package com.chi__you.cuisinejournal.network;

import com.chi__you.cuisinejournal.recipe.FoodCategory;
import com.chi__you.cuisinejournal.recipe.GlobalCategoryStore;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.util.List;

/** Focused dependency coverage for category optimistic edit keys. */
public final class CategoryEditVersionsTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        ResourceLocation root = ResourceLocation.parse("test:root");
        ResourceLocation other = ResourceLocation.parse("test:other");
        ResourceLocation sub = ResourceLocation.parse("test:root/sub");
        ResourceLocation otherSub = ResourceLocation.parse("test:other/sub");
        var first = new FoodCategory.Subcategory(sub, "Sub", true, null, List.of(), java.util.Set.of());
        var second = new FoodCategory.Subcategory(otherSub, "Other", true, null, List.of(), java.util.Set.of());
        List<FoodCategory> base = List.of(
                new FoodCategory(root, List.of(), 0, java.util.Set.of(), null, List.of(first), "Root", true),
                new FoodCategory(other, List.of(), 1, java.util.Set.of(), null, List.of(second), "Other", true));
        var directory = Files.createTempDirectory("cj-category-versions");
        GlobalCategoryStore.setPackOverlays(List.of());
        GlobalCategoryStore.load(directory);
        GlobalCategoryStore.apply(base);
        try {
            check(CategoryEditVersions.values().containsKey("category:root:test:root"), "root semantic value exists");
            check(CategoryEditVersions.values().containsKey("category:children:test:root"), "child aggregate exists");

            var assign = new Packets.CategoryEditRequest(1,
                    Packets.CategoryEditRequest.Action.ASSIGN_FOOD, sub, null, null, null,
                    List.of(ResourceLocation.parse("test:apple")), java.util.Map.of());
            var assignKeys = CategoryEditVersions.keys(assign);
            check(assignKeys.contains("category:food:test:apple"), "assignment guards its food");
            check(!assignKeys.contains("category:food:test:carrot"), "assignment does not guard unrelated food");

            check(GlobalCategoryStore.assignFood("test:apple", sub.toString()), "seed assignment");
            var remove = new Packets.CategoryEditRequest(2,
                    Packets.CategoryEditRequest.Action.DELETE_SUB, sub, root, null, null,
                    List.of(), java.util.Map.of());
            var removeKeys = CategoryEditVersions.keys(remove);
            check(removeKeys.contains("category:sub:test:root/sub"), "sub deletion guards the sub");
            check(removeKeys.contains("category:members:test:root/sub"), "sub deletion guards members");
            check(removeKeys.contains("category:food:test:apple"), "sub deletion guards affected food");
            check(!removeKeys.contains("category:food:test:carrot"), "sub deletion ignores unrelated food");

            var rootRemove = new Packets.CategoryEditRequest(3,
                    Packets.CategoryEditRequest.Action.DELETE_ROOT, root, null, null, null,
                    List.of(), java.util.Map.of());
            check(CategoryEditVersions.keys(rootRemove).contains("category:sub:test:root/sub"),
                    "root deletion guards descendant sub");
        } finally {
            GlobalCategoryStore.reset();
            GlobalCategoryStore.setPackOverlays(List.of());
        }
        System.out.println("CategoryEditVersionsTest: " + checks + " checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
