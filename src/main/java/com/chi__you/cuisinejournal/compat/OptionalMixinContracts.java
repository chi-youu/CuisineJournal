package com.chi__you.cuisinejournal.compat;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import java.util.*;
import java.util.function.Function;

/** Read-only preflight for optional hosts; never initializes their classes. */
public final class OptionalMixinContracts {
    private OptionalMixinContracts() {}

    public static boolean optional(ClassNode mixin) {
        return annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations).stream()
                .anyMatch(annotation -> annotation.desc.endsWith("/Pseudo;"));
    }

    public static String incompatibility(ClassNode mixin, ClassNode target, Function<String, ClassNode> classes) {
        boolean remap = annotations(mixin.visibleAnnotations,mixin.invisibleAnnotations).stream()
                .filter(annotation->annotation.desc.endsWith("/Mixin;"))
                .noneMatch(annotation->Boolean.FALSE.equals(value(annotation,"remap")));
        if (((mixin.access ^ target.access) & Opcodes.ACC_INTERFACE) != 0) return "class/interface changed";
        for (var field : mixin.fields) for (var annotation : annotations(field.visibleAnnotations, field.invisibleAnnotations)) {
            if (!annotation.desc.endsWith("/Shadow;")) continue;
            var names = new ArrayList<>(List.of(field.name));
            names.addAll(strings(value(annotation, "aliases")));
            if (!hasField(target, names, field.desc, classes)) return "missing shadow field " + field.name;
        }
        for (var handler : mixin.methods) for (var injection : annotations(handler.visibleAnnotations, handler.invisibleAnnotations)) {
            if (!injection.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/")) continue;
            var selectors = strings(value(injection, "method"));
            if (selectors.isEmpty()) continue;
            // Vanilla selectors using a refmap are checked after remapping by Mixin itself.
            Object localRemap=value(injection,"remap");
            if (Boolean.TRUE.equals(localRemap) || localRemap==null && remap) continue;
            for (String selector : selectors) {
                var methods = target.methods.stream().filter(method -> matches(method, selector)).toList();
                if (methods.isEmpty()) return "missing target method " + selector;
                int hits = 0;
                for (var method : methods) {
                    if (injection.desc.endsWith("/Inject;") && !callbackCompatible(handler, method))
                        return "callback arguments changed for " + selector;
                    for (var at : points(value(injection, "at"))) hits += count(method, at);
                }
                if (hits < Math.max(1, number(value(injection, "require"), 1)))
                    return "missing injection point in " + selector + " (" + handler.name + ")";
            }
        }
        return null;
    }

    private static boolean callbackCompatible(MethodNode handler, MethodNode target) {
        if ((target.access & Opcodes.ACC_STATIC) != 0 && (handler.access & Opcodes.ACC_STATIC) == 0) return false;
        Type[] supplied = Type.getArgumentTypes(handler.desc), actual = Type.getArgumentTypes(target.desc);
        int callback = -1;
        for (int i = 0; i < supplied.length; i++) if (supplied[i].getDescriptor().startsWith(
                "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo")) { callback = i; break; }
        if (callback < 0) return false;
        boolean returnsValue=Type.getReturnType(target.desc).getSort()!=Type.VOID;
        if (supplied[callback].getDescriptor().endsWith("CallbackInfoReturnable;") != returnsValue) return false;
        if (callback == 0) return true;
        if (callback > actual.length) return false;
        for (int i = 0; i < callback; i++) if (!supplied[i].equals(actual[i]) && !coercedReference(handler,i,supplied[i],actual[i])) return false;
        return true;
    }

    private static boolean coercedReference(MethodNode handler,int parameter,Type supplied,Type actual) {
        if (supplied.getSort()!=Type.OBJECT || actual.getSort()!=Type.OBJECT) return false;
        var visible=handler.visibleParameterAnnotations;
        var invisible=handler.invisibleParameterAnnotations;
        return annotations(visible!=null&&parameter<visible.length?visible[parameter]:null,
                invisible!=null&&parameter<invisible.length?invisible[parameter]:null).stream()
                .anyMatch(annotation->annotation.desc.endsWith("/Coerce;"));
    }

    private static int count(MethodNode method, AnnotationNode at) {
        String kind = (String) value(at, "value");
        if ("HEAD".equals(kind)) return 1;
        if ("RETURN".equals(kind) || "TAIL".equals(kind)) {
            int returns = 0;
            for (var instruction : method.instructions) if (instruction.getOpcode() >= Opcodes.IRETURN
                    && instruction.getOpcode() <= Opcodes.RETURN) returns++;
            return "TAIL".equals(kind) ? Math.min(1, returns) : returns;
        }
        if (!("INVOKE".equals(kind) || "INVOKE_ASSIGN".equals(kind) || "FIELD".equals(kind))) return 0;
        String target = (String) value(at, "target");
        if (target == null) return 0;
        int hits = 0, ordinal = number(value(at, "ordinal"), -1), opcode = number(value(at, "opcode"), -1);
        for (var instruction : method.instructions) {
            if (opcode >= 0 && instruction.getOpcode() != opcode) continue;
            String reference = instruction instanceof MethodInsnNode call ? "L" + call.owner + ";" + call.name + call.desc
                    : instruction instanceof FieldInsnNode field ? "L" + field.owner + ";" + field.name + ":" + field.desc : "";
            if (reference.equals(target)) hits++;
        }
        return ordinal < 0 ? hits : hits > ordinal ? 1 : 0;
    }

    private static boolean hasField(ClassNode owner, List<String> names, String descriptor, Function<String, ClassNode> classes) {
        for (int depth = 0; owner != null && depth < 64; depth++) {
            for (var field : owner.fields) if (names.contains(field.name) && field.desc.equals(descriptor)) return true;
            owner = owner.superName == null ? null : classes.apply(owner.superName);
        }
        return false;
    }
    private static boolean matches(MethodNode method, String selector) {
        int index = selector.indexOf('(');
        return index < 0 ? method.name.equals(selector)
                : method.name.equals(selector.substring(0, index)) && method.desc.equals(selector.substring(index));
    }
    private static int number(Object value, int fallback) { return value instanceof Integer integer ? integer : fallback; }
    private static List<AnnotationNode> points(Object value) {
        if (value instanceof AnnotationNode annotation) return List.of(annotation);
        if (value instanceof List<?> list) return list.stream().filter(AnnotationNode.class::isInstance).map(AnnotationNode.class::cast).toList();
        return List.of();
    }
    private static List<String> strings(Object value) {
        if (value instanceof String string) return List.of(string);
        if (value instanceof List<?> list) return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        return List.of();
    }
    private static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        var values = new ArrayList<AnnotationNode>();
        if (visible != null) values.addAll(visible);
        if (invisible != null) values.addAll(invisible);
        return values;
    }
    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2)
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }
}
