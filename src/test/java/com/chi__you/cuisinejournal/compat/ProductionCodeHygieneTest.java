package com.chi__you.cuisinejournal.compat;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Checks production boundaries without starting Minecraft or removing useful admin diagnostics. */
public final class ProductionCodeHygieneTest {
    private static final Path MAIN = Path.of("src/main/java/com/chi__you/cuisinejournal");
    private static final Path CLASSES = Path.of(System.getProperty("cuisinejournal.productionClasses",
            "build/classes/java/main")).resolve("com/chi__you/cuisinejournal");
    private static final String ROOT = "com/chi__you/cuisinejournal/";
    private static final Pattern PACKAGE = Pattern.compile("(?m)^package ([a-zA-Z0-9_.]+);");
    private static int checks;

    public static void main(String[] args) throws Exception {
        Set<String> testTypes = new HashSet<>();
        try (var files = Files.walk(Path.of("src/test/java"))) {
            for (var file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                testTypes.add(Path.of("src/test/java").relativize(file).toString()
                        .replace('\\', '/').replace(".java", ""));
            }
        }
        try (var files = Files.walk(MAIN)) {
            for (var file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                check(!source.contains("mealmastery") && !source.contains("MealMastery")
                                && !source.contains("CuisineJournalB006"), "no pre-release identity aliases in runtime: " + file);
                var declaration = PACKAGE.matcher(source);
                check(declaration.find(), "declared package: " + file);
                String relative = MAIN.relativize(file).toString().replace('\\', '/');
                String owner = ROOT + relative.substring(0, relative.length() - 5);
                check(owner.substring(0, owner.lastIndexOf('/')).equals(declaration.group(1).replace('.', '/')),
                        "package matches directory: " + file);
                check(!testTypes.contains(owner), "production/test identities are separate: " + owner);
                var type = new ClassNode();
                try (var input = Files.newInputStream(CLASSES.resolve(relative.replace(".java", ".class")))) {
                    new ClassReader(input).accept(type, 0);
                }
                check(type.name.equals(owner), "compiled class matches source identity: " + file);
            }
        }
        try (var files = Files.walk(CLASSES)) {
            for (var file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                var type = new ClassNode();
                try (var input = Files.newInputStream(file)) { new ClassReader(input).accept(type, 0); }
                check(!type.name.equals(ROOT + "recipe/MealSearch"), "removed search implementation is not shipped");
                for (var method : type.methods) for (var instruction : method.instructions) {
                    if (instruction instanceof FieldInsnNode field) {
                        check(!(field.owner.equals("java/lang/System") && Set.of("out", "err").contains(field.name)),
                                "no temporary console logging: " + type.name + "." + method.name);
                    } else if (instruction instanceof MethodInsnNode call) {
                        check(!call.name.equals("printStackTrace"), "structured logging instead of stack dumps: " + type.name);
                        check(!testTypes.contains(call.owner) && !call.owner.startsWith("org/junit/")
                                && !call.owner.startsWith("org/mockito/"), "no test-only calls: " + type.name);
                    }
                }
            }
        }
        var food = read("food/KaleidoscopeFood.java");
        check(!food.contains("ThreadLocal"), "host adapter no longer owns a duplicate use context");
        check(!food.contains("ModFoodCompat.effects") && !food.contains("LetsDoFood.effects"),
                "cross-host effect preview dispatch belongs to ModFoodCompat");
        check(read("food/ModFoodCompat.java").contains("KaleidoscopeFood.effects(stack, true)"),
                "shared preview still delegates native tavern/tea effects");
        check(!read("forge/ForgeClientBridge.java").contains("debugTooltip"), "temporary tooltip dumps removed");
        check(!read("network/ServerPacketHandler.java").contains("ChallengeManager.start"),
                "obsolete challenge-start handler stays unreachable and removed");
        check(!Files.exists(MAIN.resolve("tracking/CulinaryInventory.java"))
                && !Files.exists(MAIN.resolve("tracking/PlayerTracking.java")), "retired inventory-difference attribution is removed");
        check(!read("food/CuisineFoodConfig.java").contains("experimentalRuntime"), "one food-mode switch, no obsolete experimental gate");
        check(!read("network/Packets.java").contains("record StartChallenge")
                && !read("network/Packets.java").contains("record SetNutritionDisplay"), "obsolete request codecs are removed");
        check(!Files.exists(MAIN.resolve("skills/SkillTreeMigration.java"))
                && !read("skills/SkillsIntegration.java").contains("skillTreeRevision"), "pre-release node migration cannot modify new skill purchases");
        System.out.println("ProductionCodeHygieneTest: " + checks + " checks passed");
    }

    private static String read(String relative) throws Exception { return Files.readString(MAIN.resolve(relative)); }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
