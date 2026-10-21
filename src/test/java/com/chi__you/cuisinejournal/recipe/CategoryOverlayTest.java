package com.chi__you.cuisinejournal.recipe;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** In-game edits that are exported into a data pack overlay keep working, and can still be undone locally. */
public final class CategoryOverlayTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("cj-overlay");
        var main = ResourceLocation.parse("test:main");
        var sub = new FoodCategory.Subcategory(ResourceLocation.parse("test:main/a"), "A", true, null, List.of(), Set.of());
        List<FoodCategory> base = List.of(new FoodCategory(main, List.of(), 0, Set.of(), null, List.of(sub), "Main", true));

        GlobalCategoryStore.setPackOverlays(List.of());
        GlobalCategoryStore.load(directory);
        GlobalCategoryStore.apply(base);
        check(!GlobalCategoryStore.hasLocalEdits(), "fresh store has no local edits");
        check(GlobalCategoryStore.createRoot("test:custom", "Custom", null), "create custom root");
        check(GlobalCategoryStore.createSubcategory("test:custom/x", "test:custom", "X", null), "create custom subcategory");
        GlobalCategoryStore.apply(base);
        check(GlobalCategoryStore.assignFood("test:apple", "test:custom/x"), "assign food");
        check(GlobalCategoryStore.blockSubcategory("test:main/a"), "block pack subcategory");
        check(GlobalCategoryStore.hasLocalEdits(), "edits are local");

        JsonObject overlay = GlobalCategoryStore.exportOverlay(null);
        GlobalCategoryStore.reset();
        GlobalCategoryStore.apply(base);
        check(meta("test:custom") == null, "reset drops the custom root");

        GlobalCategoryStore.setPackOverlays(List.of(overlay));
        GlobalCategoryStore.apply(base);
        check(!GlobalCategoryStore.hasLocalEdits(), "overlay is not a local edit");
        check(meta("test:custom") != null && meta("test:custom").source() == 0, "overlay root is present and counts as data pack");
        check(meta("test:main/a").hidden(), "overlay keeps the block");
        check(GlobalCategoryStore.manuallyAssignedItems().contains("test:apple"), "overlay keeps the assignment");

        check(GlobalCategoryStore.restoreSubcategory("test:main/a"), "restore a subcategory blocked by the overlay");
        GlobalCategoryStore.apply(base);
        check(!meta("test:main/a").hidden(), "local unblock wins over the overlay");
        check(GlobalCategoryStore.editRoot("test:custom", "Renamed", null), "edit an overlay root locally");
        GlobalCategoryStore.apply(base);
        check("Renamed".equals(meta("test:custom").titleKey()) && meta("test:custom").source() == 1, "local copy of the overlay root");
        check(GlobalCategoryStore.unassignFood("test:apple"), "unassign an overlay assignment");
        check(!GlobalCategoryStore.manuallyAssignedItems().contains("test:apple"), "unassign wins over the overlay");
        check(GlobalCategoryStore.deleteRoot("test:custom"), "delete an overlay root");
        GlobalCategoryStore.apply(base);
        check(meta("test:custom") == null && meta("test:custom/x") == null, "deleted overlay root and its child are gone");

        // The negations survive a restart.
        GlobalCategoryStore.load(directory);
        GlobalCategoryStore.apply(base);
        check(meta("test:custom") == null && !meta("test:main/a").hidden(), "negations persist in the local file");

        // A second export merges over the first.
        JsonObject second = GlobalCategoryStore.exportOverlay(overlay);
        GlobalCategoryStore.reset();
        GlobalCategoryStore.setPackOverlays(List.of(second));
        GlobalCategoryStore.apply(base);
        check(meta("test:custom") == null && !meta("test:main/a").hidden(), "re-export carries the negations");
        GlobalCategoryStore.setPackOverlays(List.of());
        System.out.println("CategoryOverlayTest: " + checks + " checks passed");
    }

    private static GlobalCategoryStore.CategoryMetadata meta(String id) {
        return GlobalCategoryStore.metadata().stream().filter(value -> value.id().toString().equals(id)).findFirst().orElse(null);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
