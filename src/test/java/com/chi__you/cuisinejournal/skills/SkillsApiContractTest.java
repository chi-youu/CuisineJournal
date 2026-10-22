package com.chi__you.cuisinejournal.skills;

import net.puffish.skillsmod.api.SkillsAPI;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** Checks every production reference against both mapped hosts, including lambda handles. */
public final class SkillsApiContractTest {
    private static final String HOST = "net/puffish/skillsmod/";
    private static int checks;

    public static void main(String[] args) throws Exception {
        var metadata = Files.readString(Path.of("src/main/resources/META-INF/mods.toml"));
        var block = metadata.substring(metadata.indexOf("modId=\"puffish_skills\""));
        var matcher = java.util.regex.Pattern.compile("versionRange=\"([^\"]+)\"").matcher(block);
        check(matcher.find(), "Skills dependency range is explicit");
        var range = org.apache.maven.artifact.versioning.VersionRange.createFromVersionSpec(matcher.group(1));
        for (String version : List.of("0.18.3", "0.19.0", "0.19.2"))
            check(range.containsVersion(new org.apache.maven.artifact.versioning.DefaultArtifactVersion(version)), "verified line admitted: " + version);
        for (String version : List.of("0.18.2", "0.20", "0.20.1"))
            check(!range.containsVersion(new org.apache.maven.artifact.versioning.DefaultArtifactVersion(version)), "unverified line excluded: " + version);
        Path current = Path.of(SkillsAPI.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path legacy = Path.of(Objects.requireNonNull(System.getProperty("cuisinejournal.skillsLegacyJar")));
        for (Path path : List.of(legacy, current)) try (var jar = new JarFile(path.toFile())) {
            int before = checks;
            try (var files = Files.walk(Path.of("build/classes/java/main/com/chi__you/cuisinejournal"))) {
                for (var file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                    var type = new ClassNode();
                    new ClassReader(Files.readAllBytes(file)).accept(type, 0);
                    for (String face : type.interfaces) if (face.startsWith(HOST)) {
                        var api = host(jar, face);
                        check((api.access & Opcodes.ACC_INTERFACE) != 0, "reward interface retained: " + face);
                        for (var method : api.methods) if ((method.access & Opcodes.ACC_ABSTRACT) != 0)
                            check(type.methods.stream().anyMatch(m -> m.name.equals(method.name) && m.desc.equals(method.desc)),
                                    "callback implemented: " + face + "." + method.name + method.desc);
                    }
                    for (var field : type.fields) descriptor(jar, field.desc);
                    for (var method : type.methods) {
                        descriptor(jar, method.desc);
                        for (var instruction : method.instructions) {
                            if (instruction instanceof MethodInsnNode call) {
                                descriptor(jar, call.desc);
                                if (call.owner.startsWith(HOST)) member(jar, call.owner, call.name, call.desc,
                                        call.getOpcode() == Opcodes.INVOKESTATIC, false);
                            } else if (instruction instanceof FieldInsnNode field) {
                                descriptor(jar, field.desc);
                                if (field.owner.startsWith(HOST)) member(jar, field.owner, field.name, field.desc,
                                        field.getOpcode() == Opcodes.GETSTATIC || field.getOpcode() == Opcodes.PUTSTATIC, true);
                            } else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                                descriptor(jar, dynamic.desc);
                                for (Object argument : dynamic.bsmArgs) if (argument instanceof Handle handle) {
                                    descriptor(jar, handle.getDesc());
                                    if (handle.getOwner().startsWith(HOST)) member(jar, handle.getOwner(), handle.getName(),
                                            handle.getDesc(), handle.getTag() == Opcodes.H_INVOKESTATIC, false);
                                } else if (argument instanceof Type value) descriptor(jar, value.getDescriptor());
                            }
                        }
                    }
                }
            }
            check(jar.getJarEntry(HOST + "client/gui/SkillsScreen.class") != null, "journal return screen identity retained");
            System.out.println("SkillsApiContractTest: " + (checks - before) + " production API contracts passed: " + path.getFileName());
        }
    }

    private static void descriptor(JarFile jar, String descriptor) throws Exception {
        var type = Type.getType(descriptor);
        if (type.getSort() == Type.METHOD) {
            descriptor(jar, type.getReturnType().getDescriptor());
            for (var argument : type.getArgumentTypes()) descriptor(jar, argument.getDescriptor());
        } else {
            while (type.getSort() == Type.ARRAY) type = type.getElementType();
            if (type.getSort() == Type.OBJECT && type.getInternalName().startsWith(HOST)) host(jar, type.getInternalName());
        }
    }
    private static void member(JarFile jar, String owner, String name, String desc, boolean isStatic, boolean field) throws Exception {
        var type = host(jar, owner);
        Integer access = field ? type.fields.stream().filter(f -> f.name.equals(name) && f.desc.equals(desc)).map(f -> f.access).findFirst().orElse(null)
                : type.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).map(m -> m.access).findFirst().orElse(null);
        check(access != null, "host member exists: " + owner + "." + name + desc);
        check((access & Opcodes.ACC_PUBLIC) != 0 && ((access & Opcodes.ACC_STATIC) != 0) == isStatic,
                "host access/static contract: " + owner + "." + name);
    }
    private static ClassNode host(JarFile jar, String name) throws Exception {
        var entry = jar.getJarEntry(name + ".class");
        check(entry != null, "host class exists: " + name);
        var type = new ClassNode();
        try (var input = jar.getInputStream(entry)) { new ClassReader(input).accept(type, ClassReader.SKIP_CODE); }
        return type;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
}
