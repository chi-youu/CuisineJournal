package com.chi__you.cuisinejournal.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Bundled content is opt-in, survives load/export, and never becomes the empty default. */
public final class BundledSchemesTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        var root = Path.of("src/main/resources/data/cuisinejournal/cuisinejournal");
        Map<ResourceLocation, Resource> files = new LinkedHashMap<>();
        try (var paths = Files.walk(root.resolve("schemes"))) {
            for (var file : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                files.put(ResourceLocation.parse("cuisinejournal:cuisinejournal/" + root.relativize(file).toString().replace('\\', '/')),
                        resource(Files.readString(file)));
            }
        }
        ResourceManager resources = manager(files);
        var directory = Files.createTempDirectory("cj-bundled-schemes");
        IngredientCatalogueConfig.load(directory);
        IngredientCompatRules.load(resources);
        FlavorPresets.reload(resources, directory);
        GlobalCategoryStore.load(directory);
        GlobalCategoryStore.setPackOverlays(List.of());
        SchemeStore.reload(resources, directory);
        var flavor = SchemeStore.Kind.FLAVOR;
        var category = SchemeStore.Kind.CATEGORY;
        check(FlavorPresets.all().isEmpty(), "optional flavor content is not registered until selected");
        for (String folder : List.of("categories", "flavors")) {
            var path = root.resolve(folder);
            if (Files.isDirectory(path)) try (var entries = Files.walk(path)) {
                check(entries.noneMatch(file -> file.toString().endsWith(".json")), "no active built-in content in " + folder);
            }
        }
        check(datapackCategories(resources) == 0, "no automatic bundled category definitions");
        for (var kind : SchemeStore.Kind.values()) {
            check(SchemeStore.list(kind).size() == 2, "empty default plus one optional preset: " + kind);
            check(SchemeStore.empty(kind), "fresh local layer is empty: " + kind);
            check(!SchemeStore.applyDefault(kind, ""), "new configuration does not auto-select a preset: " + kind);
        }
        var flavorScheme = SchemeStore.get(flavor, "cuisinejournal:handcrafted_flavors");
        var categoryScheme = SchemeStore.get(category, "cuisinejournal:basic_categories");
        check(flavorScheme != null && categoryScheme != null, "both named presets available");
        var flavors = flavorScheme.content().getAsJsonObject("flavors");
        var bindings = flavorScheme.content().getAsJsonObject("bindings");
        check(flavors.size() == 45 && bindings.getAsJsonObject("item_flavor").size() == 41
                && bindings.getAsJsonObject("tag_flavor").size() == 26, "manual flavor snapshot is complete");
        for (String field : List.of("item_flavor", "tag_flavor")) {
            for (var binding : bindings.getAsJsonObject(field).entrySet()) {
                if (!binding.getValue().getAsJsonObject().has("flavors")) continue;
                for (var id : binding.getValue().getAsJsonObject().getAsJsonArray("flavors")) {
                    check(flavors.has(id.getAsString()), "every binding carries its flavor definition: " + id);
                }
            }
        }
        check(SchemeStore.load(flavor, flavorScheme.id()) && FlavorPresets.all().size() == 45,
                "selecting flavor preset installs definitions and bindings");
        check(!SchemeStore.dirty(flavor), "freshly loaded flavor preset is clean");
        check(SchemeStore.load(category, categoryScheme.id()), "category preset loads without optional host bootstrap");
        var categories = GlobalCategoryStore.apply(List.of());
        check(categories.size() == 4 && categories.stream().mapToInt(c -> c.subcategories().size()).sum() == 23,
                "all root/subcategory definitions restored");
        check(GlobalCategoryStore.manuallyAssignedItems().size() == 304, "all explicit assignments restored");
        check(categories.stream().flatMap(c -> c.subcategories().stream()).filter(FoodCategory.Subcategory::drinkFallback).count() == 1,
                "other-drink automatic fallback preserved");
        GlobalCategoryStore.load(directory);
        check(GlobalCategoryStore.apply(List.of()).stream().flatMap(c -> c.subcategories().stream())
                .anyMatch(FoodCategory.Subcategory::drinkFallback), "fallback survives persistence");
        check(!SchemeStore.dirty(category), "category normalization preserves loaded snapshot");
        export(directory, flavor, flavorScheme.id(), 46);
        export(directory, category, categoryScheme.id(), 1);
        FlavorPresets.reload(resources, directory);
        check(SchemeStore.load(flavor, SchemeStore.DEFAULT_ID) && FlavorPresets.all().isEmpty()
                && IngredientCatalogueConfig.current().localFlavors(false).isEmpty()
                && IngredientCatalogueConfig.current().localFlavors(true).isEmpty(), "empty flavor default restores no bundled flavor content");
        check(SchemeStore.load(category, SchemeStore.DEFAULT_ID) && GlobalCategoryStore.apply(List.of()).isEmpty(),
                "empty category default removes preset groups");
        System.out.println("BundledSchemesTest: " + checks + " checks passed");
    }

    private static int datapackCategories(ResourceManager resources) { return com.chi__you.cuisinejournal.data.DatapackLoader.load(resources).categories().size(); }

    private static void export(Path directory, SchemeStore.Kind kind, String id, int expected) throws Exception {
        var snapshot = SchemeStore.forExport(kind, id);
        var writer = com.chi__you.cuisinejournal.network.FlavorEditHandler.class
                .getDeclaredMethod("writeExport", Path.class, SchemeStore.Scheme.class);
        writer.setAccessible(true);
        var output = directory.resolve("export");
        check((int) writer.invoke(null, output, snapshot) == expected, "export writes actual content for " + kind);
        var data = new LinkedHashMap<ResourceLocation, Resource>();
        try (var paths = Files.walk(output.resolve("data"))) {
            for (var file : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                var relative = output.resolve("data").relativize(file);
                String path = relative.subpath(1, relative.getNameCount()).toString().replace('\\', '/');
                data.put(ResourceLocation.fromNamespaceAndPath(relative.getName(0).toString(), path), resource(Files.readString(file)));
            }
        }
        FlavorPresets.reload(manager(data), directory);
        check(FlavorPresets.defaults().size() == 45, "exported flavor definitions load as a standalone data pack");
        if (kind == SchemeStore.Kind.FLAVOR) {
            var json = JsonParser.parseString(Files.readString(output.resolve("data/cuisinejournal_custom/cuisinejournal/compat/flavor_bindings.json"))).getAsJsonObject();
            check(json.getAsJsonObject("item_flavor").size() == 41 && json.getAsJsonObject("tag_flavor").size() == 26,
                    "export never replaces active bindings with only priority metadata");
        }
    }

    private static Resource resource(String json) {
        var pack = (PackResources) Proxy.newProxyInstance(BundledSchemesTest.class.getClassLoader(), new Class<?>[]{PackResources.class},
                (proxy, method, args) -> method.getName().equals("packId") ? "test" : null);
        return new Resource(pack, () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    @SuppressWarnings("unchecked")
    private static ResourceManager manager(Map<ResourceLocation, Resource> files) {
        return (ResourceManager) Proxy.newProxyInstance(BundledSchemesTest.class.getClassLoader(), new Class<?>[]{ResourceManager.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("listResources")) return null;
                    Map<ResourceLocation, Resource> selected = new LinkedHashMap<>();
                    files.forEach((id, resource) -> {
                        if (id.getPath().startsWith(args[0] + "/") && ((Predicate<ResourceLocation>) args[1]).test(id)) selected.put(id, resource);
                    });
                    return selected;
                });
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
