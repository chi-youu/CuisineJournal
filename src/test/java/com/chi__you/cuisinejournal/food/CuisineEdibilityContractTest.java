package com.chi__you.cuisinejournal.food;

import net.minecraft.world.food.FoodProperties;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;

/** The optional host sentinel is compared by identity, without requiring its JAR at compile time. */
public final class CuisineEdibilityContractTest {
    public static final class Host {
        public static final FoodProperties NONE = new FoodProperties.Builder().build();
    }
    public static final class NoSentinel {}

    public static void main(String[] args) throws Exception {
        check(CuisineEdibility.readSentinel(Host.class.getName()) == Host.NONE, "exact native singleton identity retained");
        check(CuisineEdibility.readSentinel(NoSentinel.class.getName()) == null, "missing field fails open");
        check(CuisineEdibility.readSentinel("missing.optional.Host") == null, "missing optional class fails open");
        check(new FoodProperties.Builder().build() != Host.NONE, "same-value food is not treated as native sentinel");
        try (var input = CuisineEdibility.class.getClassLoader().getResourceAsStream(
                "com/chi__you/cuisinejournal/food/CuisineEdibility$YoukaiFood.class")) {
            var node = new ClassNode();
            new ClassReader(java.util.Objects.requireNonNull(input)).accept(node, 0);
            for (var method : node.methods) for (var instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode field) {
                    check(!field.owner.startsWith("dev/xkmc/"), "no direct host field linkage remains");
                }
            }
        }
        System.out.println("CuisineEdibilityContractTest: optional singleton, missing host and linkage checks passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
