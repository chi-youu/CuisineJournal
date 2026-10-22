package com.chi__you.cuisinejournal.compat;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/** Selected-overwrite/redirect policies, real plugin target removal, and the pinned Mixin lifecycle. */
public final class OptionalMixinConflictsTest {
    private static final String ROOT = "com/chi__you/cuisinejournal/mixin/";
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/";
    private static int checks;

    public static void main(String[] args) throws Exception {
        var host = type("test/Host", false);
        var tick = method("tick", "()V");host.methods.add(tick);
        tick.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, "test/Source", "craft", "()V", false));
        var adapter = adapter("production/First", "Inject", "tick", at("HEAD", null));
        var foreign = type("unrelated/Replacement", false);
        var overwrite = method("tick", "()V");
        overwrite.invisibleAnnotations = List.of(new AnnotationNode(MIXIN + "Overwrite;"));foreign.methods.add(overwrite);
        check(conflict(host, adapter, foreign) != null, "arbitrary foreign overwrite is detected before application");
        overwrite.desc = "(I)V";
        check(conflict(host, adapter, foreign) == null, "different overload does not disable a valid hook");
        overwrite.name = "unrelated";overwrite.desc = "()V";
        check(conflict(host, adapter, foreign) == null, "unrelated method overwrite is allowed");
        overwrite.name = "tick";
        adapter.invisibleAnnotations = null;
        check(conflict(host, adapter, foreign) == null, "required adapter is never silently downgraded");
        adapter.invisibleAnnotations = List.of(new AnnotationNode(MIXIN + "Pseudo;"));
        host.name = "net/minecraft/TestHost";
        check(conflict(host, adapter, foreign) == null, "Minecraft core remains strict");
        host.name = "net/minecraftforge/TestHost";
        check(conflict(host, adapter, foreign) == null, "Forge core remains strict");host.name = "test/Host";
        var additive = adapter("third/Listener", "Inject", "tick", at("HEAD", null));
        check(conflict(host, adapter, additive) == null, "ordinary additive injection coexists");
        var merged = new AnnotationNode(MIXIN + "transformer/meta/MixinMerged;");
        merged.values = List.of("mixin", "another.mod.Replacement", "priority", 1000);
        tick.visibleAnnotations = List.of(merged);
        check(conflict(host, adapter, additive) != null, "already merged foreign replacement is also detected");
        tick.visibleAnnotations = null;

        String invoke = "Ltest/Source;craft()V";
        var ours = adapter("production/Call", "ModifyArg", "tick", at("INVOKE", invoke));
        var redirect = adapter("third/Redirect", "Redirect", "tick", at("INVOKE", invoke));
        check(conflict(host, ours, redirect) != null, "foreign redirect consuming our exact invocation is unsafe");
        tick.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, "test/Source", "craft", "()V", false));
        point(ours).values.addAll(List.of("ordinal", 0));point(redirect).values.addAll(List.of("ordinal", 1));
        check(conflict(host, ours, redirect) == null, "separate invocation ordinals coexist");
        point(redirect).values.set(point(redirect).values.indexOf("ordinal") + 1, 0);
        check(conflict(host, ours, redirect) != null, "matching ordinal still conflicts");
        var field = adapter("speed/Field", "Redirect", "tick", at("FIELD", "Ltest/Host;progress:I"));
        var fieldOther = adapter("third/Field", "Redirect", "tick", at("FIELD", "Ltest/Host;progress:I"));
        tick.instructions.insert(new FieldInsnNode(Opcodes.PUTFIELD, host.name, "progress", "I"));
        tick.instructions.insert(new FieldInsnNode(Opcodes.GETFIELD, host.name, "progress", "I"));
        point(field).values.addAll(List.of("opcode", Opcodes.PUTFIELD));
        point(fieldOther).values.addAll(List.of("opcode", Opcodes.GETFIELD));
        check(conflict(host, field, fieldOther) == null, "field reads and writes are distinct instruction targets");
        point(fieldOther).values.set(point(fieldOther).values.indexOf("opcode") + 1, Opcodes.PUTFIELD);
        check(conflict(host, field, fieldOther) != null, "competing field writes are detected");

        targetRemoval(host, adapter, foreign);
        previouslyMerged();
        libraryContracts();
        realInstalledConflict(System.getenv("CUISINEJOURNAL_CONFLICT_MODS"));
        System.out.println("OptionalMixinConflictsTest: " + checks + " checks passed");
    }
    private static void previouslyMerged() {
        var host = type("test/PreviouslyMerged", false);
        var extract = method("extractOneOutput", "(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;");
        extract.instructions.clear();extract.instructions.add(new InsnNode(Opcodes.ACONST_NULL));extract.instructions.add(new InsnNode(Opcodes.ARETURN));
        var marker = new AnnotationNode(MIXIN + "transformer/meta/MixinMerged;");
        marker.values = List.of("mixin", "earlier.phase.Replacement");extract.visibleAnnotations = List.of(marker);host.methods.add(extract);
        var placement = method("placeBottleResult", "()V");
        placement.instructions.insert(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "com/github/ysbbbbbb/kaleidoscopetavern/blockentity/brew/DrinkBlockEntity", "addItem", "(Lnet/minecraft/world/item/ItemStack;)Z", false));
        host.methods.add(placement);
        var plugin = new CuisineMixinPlugin(name -> { throw new AssertionError("not in acceptTargets"); }, name -> host);
        check(!plugin.shouldApplyMixin(host.name.replace('/', '.'),
                "com.chi__you.cuisinejournal.mixin.production.KaleidoscopeBarrelOutputMixin"),
                "earlier-phase merged methods rejected even without a current shared config");
    }

    @SuppressWarnings("unchecked")
    private static void targetRemoval(ClassNode host, ClassNode adapter, ClassNode foreign) throws Exception {
        var companion = adapter("production/Companion", "Inject", "tick", at("RETURN", null));
        var selected = List.of(info(adapter), info(companion), info(foreign));
        var plugin = new CuisineMixinPlugin(name -> selected, name -> host);
        var accepted = CuisineMixinPlugin.class.getDeclaredField("accepted");accepted.setAccessible(true);
        var acceptedMap = (Map<String, Map<String, ClassNode>>) accepted.get(plugin);
        String target = host.name.replace('/', '.');
        acceptedMap.put(target, Map.of(adapter.name.replace('/', '.'), adapter, companion.name.replace('/', '.'), companion));
        var targets = new HashMap<String, List<String>>();
        targets.put(target, new ArrayList<>(List.of("begin", "end", "output", "speed")));
        targets.put("unrelated.Host", List.of("other"));
        targets.put("net.minecraft.Core", List.of("strict"));
        plugin.acceptTargets(targets.keySet(), Set.of(target, "net.minecraft.Core"));
        check(!targets.containsKey(target), "live target-map removal drops all paired adapters, not one handler");
        check(targets.containsKey("unrelated.Host") && targets.containsKey("net.minecraft.Core"), "unrelated and core targets retained");
        check(acceptedMap.isEmpty(), "preparation cache released after target selection");
        check(foreign.methods.size() == 1 && foreign.methods.get(0).name.equals("tick"), "foreign mixin never modified");
        var unavailable = new CuisineMixinPlugin(name -> { throw new IllegalStateException("unsupported registry"); }, name -> host);
        ((Map<String, Map<String, ClassNode>>) accepted.get(unavailable)).put(target, Map.of(adapter.name.replace('/', '.'), adapter));
        var fallback = new HashSet<>(Set.of(target));unavailable.acceptTargets(fallback, Set.of(target));
        check(fallback.isEmpty(), "uninspectable shared optional host degrades rather than pretending conflict safety");
        var compatible = new CuisineMixinPlugin(name -> List.of(info(adapter), info(companion)), name -> host);
        ((Map<String, Map<String, ClassNode>>) accepted.get(compatible)).put(target, Map.of(adapter.name.replace('/', '.'), adapter));
        var retained = new HashSet<>(Set.of(target));compatible.acceptTargets(retained, Set.of(target));
        check(retained.contains(target), "shared host without a destructive foreign injection remains enabled");
        var rejected = new CuisineMixinPlugin(name -> { throw new AssertionError("no third-party scan needed"); }, name -> host);
        ((Map<String, Map<String, ClassNode>>) accepted.get(rejected)).put(target, Map.of(adapter.name.replace('/', '.'), adapter));
        var failures = CuisineMixinPlugin.class.getDeclaredField("rejectedTargets");failures.setAccessible(true);
        ((Map<String, String>) failures.get(rejected)).put(target, "paired output contract changed");
        var partial = new HashSet<>(Set.of(target));rejected.acceptTargets(partial, Set.of());
        check(partial.isEmpty(), "one rejected adapter also disables accepted companions on the same host");
        var strict = new CuisineMixinPlugin(name -> { throw new AssertionError("strict target is not downgraded"); }, name -> host);
        ((Map<String, Map<String, ClassNode>>) accepted.get(strict)).put(target, Map.of(adapter.name.replace('/', '.'), adapter));
        var strictField = CuisineMixinPlugin.class.getDeclaredField("strictTargets");strictField.setAccessible(true);
        ((Set<String>) strictField.get(strict)).add(target);
        var required = new HashSet<>(Set.of(target));strict.acceptTargets(required, Set.of(target));
        check(required.contains(target), "required companion prevents silent group downgrade");
    }

    private static void libraryContracts() throws Exception {
        var reader = own("com/chi__you/cuisinejournal/compat/SelectedTargetMixins");
        check(calls(reader, "getAppliedMixins").isEmpty() && calls(reader, "getMixinsForClass").isEmpty(),
                "guard never confuses already-applied mixins with selected mixins");
        var classInfo = own("org/spongepowered/asm/mixin/transformer/ClassInfo");
        var getMixins = classInfo.methods.stream().filter(m -> m.name.equals("getMixins") && m.desc.equals("()Ljava/util/Set;")).findFirst().orElseThrow();
        check(fields(getMixins).contains("mixins") && !fields(getMixins).contains("appliedMixins"), "pinned registry exposes selected target adapters");
        var config = own("org/spongepowered/asm/mixin/transformer/MixinConfig");
        var live = config.methods.stream().filter(m -> m.name.equals("getTargetsSet")).findFirst().orElseThrow();
        check(fields(live).contains("mixinMapping") && calls(live, "keySet").size() == 1, "acceptTargets receives the live configuration mapping keys");
        var processor = own("org/spongepowered/asm/mixin/transformer/MixinProcessor");
        var prepare = processor.methods.stream().filter(m -> m.name.equals("prepareConfigs")).findFirst().orElseThrow();
        check(index(prepare, "prepare") < index(prepare, "acceptTargets")
                && index(prepare, "acceptTargets") < index(prepare, "postInitialise"), "conflict pass runs after selection and before validation/application");
        var forClass = config.methods.stream().filter(m -> m.name.equals("getMixinsFor")).findFirst().orElseThrow();
        check(fields(forClass).contains("mixinMapping") || calls(forClass, "mixinsFor").size() == 1,
                "application retrieves adapters from the filtered mapping");
    }

    private static void realInstalledConflict(String directory) throws Exception {
        if (directory == null) { System.out.println("SKIPPED: installed overwrite conflict; set CUISINEJOURNAL_CONFLICT_MODS");return; }
        ClassNode replacement = null, host = null;
        try (var files = Files.list(Path.of(directory))) {
            for (var file : files.filter(p -> p.toString().endsWith(".jar")).toList()) try (var jar = new JarFile(file.toFile())) {
                var direct = jar.getJarEntry("com/github/ysbbbbbb/kaleidoscopetavern/blockentity/brew/BarrelBlockEntity.class");
                if (direct != null) try (var input = jar.getInputStream(direct)) { host = read(input); }
                for (var nested : Collections.list(jar.entries())) if (nested.getName().endsWith(".jar"))
                    try (var input = new JarInputStream(jar.getInputStream(nested))) {
                        JarEntry entry;while ((entry = input.getNextJarEntry()) != null)
                            if (entry.getName().equals("com/farmingtales/taverndaybrewing/mixin/BarrelBlockEntityMixin.class")) replacement = read(input);
                    }
            }
        }
        check(host != null && replacement != null, "actual failing host and nested replacement found");
        var speed = own(ROOT + "speed/KaleidoscopeBarrelSpeedMixin");
        check(OptionalMixinContracts.incompatibility(speed, host, name -> null) == null, "original-host preflight alone reproduces the missed conflict");
        String failure = conflict(host, speed, replacement);
        check(failure != null && failure.contains("overwrites tick"), "generic guard catches the actual farmingtales overwrite");
        System.out.println("Installed overwrite conflict: " + failure);
    }
    private static IMixinInfo info(ClassNode node) {
        return new IMixinInfo() {
            public IMixinConfig getConfig() { return null; }
            public String getName() { return node.name; }
            public String getClassName() { return node.name.replace('/', '.'); }
            public String getClassRef() { return node.name; }
            public byte[] getClassBytes() { throw new UnsupportedOperationException(); }
            public boolean isDetachedSuper() { return false; }
            public ClassNode getClassNode(int flags) { return node; }
            public List<String> getTargetClasses() { return List.of("test.Host"); }
            public int getPriority() { return 1000; }
            public MixinEnvironment.Phase getPhase() { return null; }
        };
    }
    private static String conflict(ClassNode host, ClassNode ours, ClassNode theirs) {
        return OptionalMixinConflicts.incompatibility(host, List.of(ours), List.of(theirs));
    }
    private static ClassNode type(String name, boolean optional) {
        var type = new ClassNode();type.name = name;type.superName = "java/lang/Object";
        if (optional) type.invisibleAnnotations = List.of(new AnnotationNode(MIXIN + "Pseudo;"));return type;
    }
    private static MethodNode method(String name, String desc) {
        var method = new MethodNode(0, name, desc, null, null);method.instructions.add(new InsnNode(Opcodes.RETURN));return method;
    }
    private static ClassNode adapter(String name, String kind, String selector, AnnotationNode point) {
        var type = type(name.startsWith("third/") ? name : ROOT + name, true);
        var handler = method("handler", "()V");
        var annotation = new AnnotationNode(MIXIN + "injection/" + kind + ";");
        annotation.values = new ArrayList<>(List.of("method", List.of(selector), "at", point));
        handler.visibleAnnotations = List.of(annotation);type.methods.add(handler);return type;
    }
    private static AnnotationNode at(String kind, String target) {
        var point = new AnnotationNode(MIXIN + "injection/At;");
        point.values = new ArrayList<>(List.of("value", kind));
        if (target != null) point.values.addAll(List.of("target", target));return point;
    }
    private static AnnotationNode point(ClassNode type) { return (AnnotationNode) type.methods.get(0).visibleAnnotations.get(0).values.get(3); }
    private static ClassNode own(String name) throws Exception {
        try (var input = OptionalMixinConflictsTest.class.getClassLoader().getResourceAsStream(name + ".class")) { return read(Objects.requireNonNull(input, name)); }
    }
    private static ClassNode read(java.io.InputStream input) throws Exception { var node = new ClassNode();new ClassReader(input).accept(node, 0);return node; }
    private static List<MethodInsnNode> calls(ClassNode type, String name) {
        var result = new ArrayList<MethodInsnNode>();for (var method : type.methods) result.addAll(calls(method, name));return result;
    }
    private static List<MethodInsnNode> calls(MethodNode method, String name) {
        var result = new ArrayList<MethodInsnNode>();for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.name.equals(name)) result.add(call);return result;
    }
    private static Set<String> fields(MethodNode method) {
        var result = new HashSet<String>();for (var instruction : method.instructions)
            if (instruction instanceof FieldInsnNode field) result.add(field.name);return result;
    }
    private static int index(MethodNode method, String callName) {
        int index = 0;for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals(callName)) return index;index++;
        }return -1;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message);checks++; }
}
