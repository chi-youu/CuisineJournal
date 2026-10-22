package com.chi__you.cuisinejournal.compat;

import org.objectweb.asm.tree.*;
import java.util.*;

/** Conflicts between selected adapters, before any of them changes the host class. */
public final class OptionalMixinConflicts {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/";
    private OptionalMixinConflicts() {}

    public static boolean protectedTarget(String target) {
        String name = target.replace('/', '.');
        return name.startsWith("net.minecraft.") || name.startsWith("net.minecraftforge.");
    }

    public static String incompatibility(ClassNode host, List<ClassNode> own, List<ClassNode> others) {
        if (protectedTarget(host.name) || own.isEmpty() || own.stream().anyMatch(m -> !OptionalMixinContracts.optional(m))) return null;
        for (var adapter : own) for (var handler : adapter.methods) for (var injection : annotations(handler)) {
            if (!injection.desc.startsWith(MIXIN + "injection/")) continue;
            for (String selector : strings(value(injection, "method"))) for (var target : host.methods) {
                if (!matches(target, selector)) continue;
                for (var merged : annotations(target)) if (merged.desc.equals(MIXIN + "transformer/meta/MixinMerged;")) {
                    Object owner = value(merged, "mixin");
                    if (owner instanceof String name && !name.replace('/', '.').startsWith("com.chi__you.cuisinejournal.mixin."))
                        return name + " already replaced " + target.name + target.desc + " used by " + adapter.name;
                }
                for (var other : others) for (var foreign : other.methods) {
                    if (foreign.name.equals(target.name) && foreign.desc.equals(target.desc)
                            && annotations(foreign).stream().anyMatch(a -> a.desc.equals(MIXIN + "Overwrite;")))
                        return other.name + " overwrites " + target.name + target.desc + " used by " + adapter.name;
                    for (var redirect : annotations(foreign)) {
                        if (!redirect.desc.equals(MIXIN + "injection/Redirect;")) continue;
                        if (strings(value(redirect, "method")).stream().noneMatch(s -> matches(target, s))) continue;
                        for (var ourPoint : points(value(injection, "at"))) {
                            var ours = references(target, ourPoint);
                            if (ours.isEmpty()) continue;
                            for (var theirPoint : points(value(redirect, "at"))) {
                                var theirs = references(target, theirPoint);
                                if (theirs.stream().anyMatch(ours::contains))
                                    return other.name + " redirects an instruction used by " + adapter.name
                                            + " in " + target.name + target.desc;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private static Set<AbstractInsnNode> references(MethodNode method, AnnotationNode at) {
        String kind = (String) value(at, "value");
        if (!Set.of("INVOKE", "INVOKE_ASSIGN", "FIELD").contains(kind == null ? "" : kind)) return Set.of();
        String target = (String) value(at, "target");
        if (target == null) return Set.of();
        int opcode = number(value(at, "opcode"), -1), ordinal = number(value(at, "ordinal"), -1), index = 0;
        var result = new HashSet<AbstractInsnNode>();
        for (var instruction : method.instructions) {
            if (opcode >= 0 && instruction.getOpcode() != opcode) continue;
            String reference = instruction instanceof MethodInsnNode call ? "L" + call.owner + ";" + call.name + call.desc
                    : instruction instanceof FieldInsnNode field ? "L" + field.owner + ";" + field.name + ":" + field.desc : "";
            if (target.equals(reference) && (index++ == ordinal || ordinal < 0)) result.add(instruction);
        }
        return result;
    }
    private static boolean matches(MethodNode method, String selector) {
        int descriptor = selector.indexOf('(');
        return descriptor < 0 ? method.name.equals(selector)
                : method.name.equals(selector.substring(0, descriptor)) && method.desc.equals(selector.substring(descriptor));
    }
    private static int number(Object value, int fallback) { return value instanceof Integer n ? n : fallback; }
    private static List<AnnotationNode> annotations(MethodNode method) {
        var result = new ArrayList<AnnotationNode>();
        if (method.visibleAnnotations != null) result.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) result.addAll(method.invisibleAnnotations);
        return result;
    }
    private static List<String> strings(Object value) {
        if (value instanceof String string) return List.of(string);
        if (value instanceof List<?> list) return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        return List.of();
    }
    private static List<AnnotationNode> points(Object value) {
        if (value instanceof AnnotationNode point) return List.of(point);
        if (value instanceof List<?> list) return list.stream().filter(AnnotationNode.class::isInstance).map(AnnotationNode.class::cast).toList();
        return List.of();
    }
    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2)
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }
}
