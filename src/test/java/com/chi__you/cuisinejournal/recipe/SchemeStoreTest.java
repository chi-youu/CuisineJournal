package com.chi__you.cuisinejournal.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Flavor and category schemes: save, dirty tracking, load (replace), copy, rename, delete, defaults. */
public final class SchemeStoreTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("cj-schemes");
        ResourceManager empty = (ResourceManager) java.lang.reflect.Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class[]{ResourceManager.class}, (proxy, method, arguments) -> method.getName().equals("listResources") ? Map.of() : null);
        FlavorPresets.reload(empty, directory);
        IngredientCatalogueConfig.load(directory);
        GlobalCategoryStore.setPackOverlays(List.of());
        GlobalCategoryStore.load(directory);
        SchemeStore.reload(empty, directory);
        var F = SchemeStore.Kind.FLAVOR;
        var C = SchemeStore.Kind.CATEGORY;

        check(SchemeStore.list(F).size() == 1 && SchemeStore.list(F).get(0).id().equals(SchemeStore.DEFAULT_ID), "generated default exists");
        check(SchemeStore.empty(F) && !SchemeStore.dirty(F), "fresh layer is empty and not dirty");

        // Flavor edits, then save.
        ResourceLocation spicy = ResourceLocation.parse("cuisinejournal_custom:spicy");
        FlavorPresets.putLocal(new FlavorPresets.Preset(spicy, "Spicy", "", true, List.of()));
        IngredientCatalogueConfig.setLocalFlavor(false, ResourceLocation.parse("minecraft:carrot"),
                IngredientCatalogueConfig.Flavor.of(true, List.of(spicy), null));
        check(SchemeStore.dirty(F) && !SchemeStore.empty(F), "edits make the flavor layer dirty");
        check(SchemeStore.counts(F, SchemeStore.forExport(F, SchemeStore.DEFAULT_ID).content())[0] == 1,
                "exporting the implicit current scheme includes unsaved flavor edits, not an empty default");
        check(SchemeStore.counts(F, SchemeStore.get(F, SchemeStore.DEFAULT_ID).content())[0] == 0,
                "export snapshot never mutates the empty default");
        exportUnsaved(directory, SchemeStore.forExport(F, SchemeStore.DEFAULT_ID));
        String saved = SchemeStore.save(F, null, "整合包 v1", "常规");
        check(saved != null && saved.startsWith("own:") && saved.equals(SchemeStore.currentId(F)), "save remembers the scheme as current");
        check(!SchemeStore.dirty(F), "after saving the layer matches the scheme");
        int[] counts = SchemeStore.counts(F, SchemeStore.get(F, saved).content());
        check(counts[0] == 1 && counts[1] == 1, "flavor scheme counts one flavor and one binding");
        var changed = new FlavorPresets.Preset(spicy, "Changed after saving", "", true, List.of());
        FlavorPresets.putLocal(changed);
        check(SchemeStore.forExport(F, saved).content().getAsJsonObject("flavors").getAsJsonObject(spicy.toString())
                .get("name").getAsString().equals(changed.name()), "active saved scheme exports unsaved edits");
        check(SchemeStore.get(F, saved).content().getAsJsonObject("flavors").getAsJsonObject(spicy.toString())
                .get("name").getAsString().equals("Spicy"), "export leaves saved snapshot unchanged");
        check(SchemeStore.counts(F, SchemeStore.forExport(F, SchemeStore.DEFAULT_ID).content())[0] == 0,
                "selecting a different scheme still exports that selected snapshot");
        FlavorPresets.putLocal(new FlavorPresets.Preset(spicy, "Spicy", "", true, List.of()));

        // Load the default: clears flavors and bindings; categories untouched.
        GlobalCategoryStore.apply(List.of());
        GlobalCategoryStore.createRoot("test:root", "Root", null);
        check(SchemeStore.counts(C, SchemeStore.forExport(C, SchemeStore.DEFAULT_ID).content())[0] == 1,
                "implicit category export includes current unsaved categories");
        check(SchemeStore.load(F, SchemeStore.DEFAULT_ID), "load default flavor scheme");
        check(FlavorPresets.overrides().isEmpty() && IngredientCatalogueConfig.current().localFlavors(false).isEmpty(),
                "default clears flavors and bindings");
        check(GlobalCategoryStore.hasCustomRoot("test:root"), "loading a flavor scheme leaves categories alone");

        // Load the saved one back.
        check(SchemeStore.load(F, saved) && FlavorPresets.get(spicy) != null
                && IngredientCatalogueConfig.current().flavor(ResourceLocation.parse("minecraft:carrot")) != null, "load restores the scheme");
        check(!SchemeStore.dirty(F), "loaded layer is not dirty");
        failedFlavorLoads(directory, empty, saved);
        exportTransactions(directory, SchemeStore.get(F, saved));

        // Same name saves over the existing file.
        FlavorPresets.removeLocal(spicy);
        check(SchemeStore.dirty(F), "removing a flavor is an unsaved change");
        String again = SchemeStore.save(F, null, "整合包 v1", "");
        check(saved.equals(again) && SchemeStore.list(F).size() == 2, "same name overwrites instead of duplicating");
        check(SchemeStore.counts(F, SchemeStore.get(F, saved).content())[0] == 0, "overwritten scheme holds the new layer");

        // Category scheme.
        String cat = SchemeStore.save(C, null, "按菜系", "");
        check(cat != null && !SchemeStore.dirty(C), "save category scheme");
        check(SchemeStore.counts(C, SchemeStore.get(C, cat).content())[0] == 1, "category scheme counts the new root");
        check(SchemeStore.load(C, SchemeStore.DEFAULT_ID) && !GlobalCategoryStore.hasCustomRoot("test:root"), "default category scheme clears edits");
        check(SchemeStore.dirty(C) == false && SchemeStore.DEFAULT_ID.equals(SchemeStore.currentId(C)), "default is now current");
        check(SchemeStore.load(C, cat) && GlobalCategoryStore.hasCustomRoot("test:root"), "category scheme restores the root");

        // Copy, rename, delete.
        String copy = SchemeStore.copy(C, cat, "按菜系 副本");
        check(copy != null && !copy.equals(cat), "copy makes a new own scheme");
        check(SchemeStore.rename(C, copy, "副本改名") && SchemeStore.get(C, copy).name().equals("副本改名"), "rename");
        check(!SchemeStore.rename(C, SchemeStore.DEFAULT_ID, "x") && !SchemeStore.delete(C, SchemeStore.DEFAULT_ID), "built-in is read-only");
        check(SchemeStore.delete(C, cat) && SchemeStore.get(C, cat) == null && SchemeStore.currentId(C) == null,
                "deleting the current scheme forgets it");

        // First-start default only fills an empty kind.
        check(!SchemeStore.applyDefault(C, "副本改名"), "default is skipped when the kind already has edits");
        SchemeStore.load(C, SchemeStore.DEFAULT_ID);
        Files.deleteIfExists(directory.resolve("cuisinejournal/schemes/current.json"));
        check(SchemeStore.applyDefault(C, copy.substring(4)) && GlobalCategoryStore.hasCustomRoot("test:root"),
                "default by file name loads into an empty kind");

        System.out.println("SchemeStoreTest: " + checks + " checks passed");
    }

    private static void exportUnsaved(Path directory, SchemeStore.Scheme snapshot) throws Exception {
        var writer = com.chi__you.cuisinejournal.network.FlavorEditHandler.class
                .getDeclaredMethod("writeExport", Path.class, SchemeStore.Scheme.class);
        writer.setAccessible(true);
        var root = directory.resolve("unsaved-export");
        check((int) writer.invoke(null, root, snapshot) == 2, "unsaved current export writes flavor plus binding file");
        check(Files.isRegularFile(root.resolve("data/cuisinejournal_custom/cuisinejournal/flavors/spicy.json")),
                "unsaved current preset is not lost to empty default");
        var bindings = com.google.gson.JsonParser.parseString(Files.readString(root.resolve(
                "data/cuisinejournal_custom/cuisinejournal/compat/flavor_bindings.json"))).getAsJsonObject();
        check(bindings.getAsJsonObject("item_flavor").has("minecraft:carrot"),
                "exported binding file contains actual unsaved binding, not just priority");
        check(SchemeStore.currentId(SchemeStore.Kind.FLAVOR) == null && !SchemeStore.empty(SchemeStore.Kind.FLAVOR),
                "export does not save a new scheme or clear the working edits");
    }

    private static void failedFlavorLoads(Path directory, ResourceManager resources, String saved) throws Exception {
        ResourceLocation tag = ResourceLocation.parse("forge:vegetables");
        IngredientCatalogueConfig.setLocalFlavor(true, tag, IngredientCatalogueConfig.Flavor.of(false, List.of(), null));
        var before = SchemeStore.currentContent(SchemeStore.Kind.FLAVOR);
        Path presets = directory.resolve("cuisinejournal-flavors.json");
        Path ingredients = directory.resolve("cuisinejournal-ingredients.json");
        Path state = directory.resolve("cuisinejournal/schemes/current.json");
        String presetText = Files.readString(presets), ingredientText = Files.readString(ingredients), stateText = Files.readString(state);
        // Block each atomic writer's temporary file without replacing the persisted originals.
        for (Path file : List.of(presets, ingredients, state)) {
            Path blocker = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectory(blocker);
            try {
                check(!SchemeStore.load(SchemeStore.Kind.FLAVOR, SchemeStore.DEFAULT_ID), "load fails when writing " + file.getFileName());
                check(before.equals(SchemeStore.currentContent(SchemeStore.Kind.FLAVOR)), "failed load restores both runtime layers");
                check(saved.equals(SchemeStore.currentId(SchemeStore.Kind.FLAVOR)), "failed load keeps the current scheme");
                check(presetText.equals(Files.readString(presets)) && ingredientText.equals(Files.readString(ingredients))
                        && stateText.equals(Files.readString(state)), "failed load restores persisted files");
                FlavorPresets.reload(resources, directory);
                IngredientCatalogueConfig.load(directory);
                check(before.equals(SchemeStore.currentContent(SchemeStore.Kind.FLAVOR)), "failed load stays rolled back after disk reload");
            } finally {
                Files.delete(blocker);
            }
        }
        IngredientCatalogueConfig.setLocalFlavor(true, tag, null);
    }

    private static void exportTransactions(Path directory, SchemeStore.Scheme scheme) throws Exception {
        Class<?> handler = Class.forName("com.chi__you.cuisinejournal.network.FlavorEditHandler");
        Class<?> transactionType = Class.forName(handler.getName() + "$ExportTransaction");
        var constructor = transactionType.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        var stagedField = transactionType.getDeclaredField("staged");
        var backupField = transactionType.getDeclaredField("backup");
        stagedField.setAccessible(true); backupField.setAccessible(true);
        var write = handler.getDeclaredMethod("writeExport", Path.class, SchemeStore.Scheme.class);
        write.setAccessible(true);
        Path datapacks = directory.resolve("world/datapacks");
        Path root = datapacks.resolve(FlavorPresets.CUSTOM_NAMESPACE);
        Path oldFlavor = root.resolve("data/old/cuisinejournal/flavors/old.json");
        Path categories = root.resolve("data/cuisinejournal_custom/cuisinejournal/category_overlays/edits.json");
        Files.createDirectories(oldFlavor.getParent());
        Files.createDirectories(categories.getParent());
        Files.writeString(oldFlavor, "{}");
        Files.writeString(categories, "{\"kept\":true}");
        Files.writeString(root.resolve("pack.mcmeta"), "{\"old\":true}");
        var local = SchemeStore.currentContent(SchemeStore.Kind.FLAVOR);

        Object transaction = constructor.newInstance(datapacks);
        Path staged = (Path) stagedField.get(transaction), backup = (Path) backupField.get(transaction);
        Path blocker = staged.resolve("pack.mcmeta.tmp");
        Files.createDirectory(blocker);
        try {
            write.invoke(null, staged, scheme);
            throw new AssertionError("export write must fail");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            check(expected.getCause() instanceof java.io.IOException, "staged write failure propagates");
        }
        transactionAction(transaction, "rollback");
        transactionAction(transaction, "cleanup");
        check(Files.exists(oldFlavor) && Files.readString(root.resolve("pack.mcmeta")).contains("old"), "write failure preserves old pack");
        check(!Files.exists(staged) && !Files.exists(backup), "write failure cleans staging");

        transaction = constructor.newInstance(datapacks);
        staged = (Path) stagedField.get(transaction); backup = (Path) backupField.get(transaction);
        check((int) write.invoke(null, staged, scheme) == 2, "staged export counts preset and bindings");
        check(Files.exists(oldFlavor), "staging leaves live pack untouched");
        transactionAction(transaction, "install");
        check(!Files.exists(oldFlavor) && Files.exists(backup.resolve("pack/pack.mcmeta")), "swap retains old pack as backup");
        check(!Files.exists(backup.resolve("pack.mcmeta")), "backup is not discoverable as a pack");
        check(Files.readString(categories).equals("{\"kept\":true}"), "flavor export preserves categories");
        // Exercise the same disk rollback used when the asynchronous reload fails.
        transactionAction(transaction, "rollback");
        transactionAction(transaction, "cleanup");
        check(Files.exists(oldFlavor) && Files.readString(root.resolve("pack.mcmeta")).contains("old"), "post-swap rollback restores old pack");
        check(!Files.exists(staged) && !Files.exists(backup), "rollback cleans transaction paths");

        transaction = constructor.newInstance(datapacks);
        staged = (Path) stagedField.get(transaction); backup = (Path) backupField.get(transaction);
        write.invoke(null, staged, scheme);
        transactionAction(transaction, "install");
        transactionAction(transaction, "cleanup");
        check(!Files.exists(staged) && !Files.exists(backup) && !Files.exists(oldFlavor), "success removes backup and obsolete flavors");
        check(local.equals(SchemeStore.currentContent(SchemeStore.Kind.FLAVOR)), "export preserves local edits");
        Path exportedFlavor = root.resolve("data/cuisinejournal_custom/cuisinejournal/flavors/spicy.json");
        String flavorText = Files.readString(exportedFlavor);
        transaction = constructor.newInstance(datapacks);
        staged = (Path) stagedField.get(transaction);
        var categoryContent = new com.google.gson.JsonObject();
        categoryContent.add("categories", GlobalCategoryStore.localJson());
        var category = new SchemeStore.Scheme("test:category", SchemeStore.Kind.CATEGORY, (byte) 1, "", "", "", categoryContent);
        check((int) write.invoke(null, staged, category) == 1, "category export writes one overlay");
        transactionAction(transaction, "install");
        transactionAction(transaction, "cleanup");
        check(Files.readString(exportedFlavor).equals(flavorText), "category export preserves flavor files");

        transaction = constructor.newInstance(datapacks);
        staged = (Path) stagedField.get(transaction); backup = (Path) backupField.get(transaction);
        // Force the second directory move to fail after the old pack has been backed up.
        transactionAction(transaction, "cleanup");
        try {
            transactionAction(transaction, "install");
            throw new AssertionError("install must fail without staging");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            check(expected.getCause() instanceof java.io.IOException, "second swap move failure propagates");
        }
        transactionAction(transaction, "rollback");
        transactionAction(transaction, "cleanup");
        check(Files.readString(exportedFlavor).equals(flavorText) && !Files.exists(backup), "failed swap restores backup");

        Path fresh = directory.resolve("fresh-world/datapacks");
        transaction = constructor.newInstance(fresh);
        staged = (Path) stagedField.get(transaction);
        write.invoke(null, staged, scheme);
        transactionAction(transaction, "install");
        transactionAction(transaction, "rollback");
        transactionAction(transaction, "cleanup");
        check(!Files.exists(fresh.resolve(FlavorPresets.CUSTOM_NAMESPACE)), "failed first export removes new pack");
    }

    private static void transactionAction(Object transaction, String action) throws Exception {
        var method = transaction.getClass().getDeclaredMethod(action);
        method.setAccessible(true);
        method.invoke(transaction);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
