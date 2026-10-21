package com.chi__you.cuisinejournal.network;

import com.chi__you.cuisinejournal.recipe.CulinaryRegistries;
import com.chi__you.cuisinejournal.recipe.FlavorPresets;
import com.chi__you.cuisinejournal.recipe.GlobalCategoryStore;
import com.chi__you.cuisinejournal.recipe.IngredientCatalogue;
import com.chi__you.cuisinejournal.recipe.IngredientCatalogueConfig;
import com.chi__you.cuisinejournal.recipe.SchemeStore;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/** Real edit stores and wire preconditions, without a Minecraft server or player. */
public final class EditProtectionTest {
    private static int checks;
    private static final ResourceLocation FIRST = id("first"), SECOND = id("second");

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("cj-edit-protection");
        ResourceManager empty = (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class<?>[]{ResourceManager.class},
                (proxy, method, arguments) -> method.getName().equals("listResources") ? Map.of() : null);
        CulinaryRegistries.clear();
        FlavorPresets.reload(empty, directory);
        IngredientCatalogueConfig.load(directory);
        GlobalCategoryStore.setPackOverlays(List.of());
        GlobalCategoryStore.load(directory);
        GlobalCategoryStore.apply(List.of());
        SchemeStore.reload(empty, directory);

        presets();
        bindings();
        schemeCollections();
        packetRoundTrips();
        malformedVersionMaps();
        System.out.println("EditProtectionTest: " + checks + " checks passed");
    }

    private static void presets() {
        put(FIRST, "First A");
        put(SECOND, "Second A");
        String first = EditVersions.preset(FIRST), second = EditVersions.preset(SECOND);
        var opened = EditVersions.snapshot();
        check(opened.get(first) > 0 && opened.get(second) > 0, "real presets receive edit tokens");
        check(opened.equals(EditVersions.snapshot()), "unchanged real stores preserve all tokens");

        put(FIRST, "First B");
        check(!matches(opened, first), "same preset rejects stale save");
        check(matches(opened, second), "independent preset can still save");
        check(!matches(opened, first, second), "batch containing stale preset rejects");
        var changed = EditVersions.snapshot();
        put(FIRST, "First A");
        var restored = EditVersions.snapshot();
        check(restored.get(first) > changed.get(first) && changed.get(first) > opened.get(first),
                "observed preset A-B-A advances versions twice");
        check(!matches(opened, first), "restoring preset content does not revive stale draft");
        check(FlavorPresets.get(FIRST).name().equals("First A"), "preset content really returned to A");
        put(FIRST, "First A");
        check(restored.equals(EditVersions.snapshot()), "identical preset save is not a conflict");

        check(FlavorPresets.removeLocal(FIRST), "delete local preset");
        check(!matches(restored, first), "preset deletion rejects stale edit");
        var deleted = EditVersions.snapshot();
        check(deleted.containsKey(first) && matches(deleted, first), "deleted preset retains usable tombstone");
        put(FIRST, "First A");
        check(!matches(deleted, first), "preset recreation rejects stale tombstone");
        check(!matches(Map.of(), first), "existing preset requires expected token");
        check(matches(Map.of(), EditVersions.preset(id("never_created"))), "new preset needs no existing token");
    }

    private static void bindings() {
        // The same id deliberately exists in both namespaces: item and tag must stay independent.
        ResourceLocation target = id("ingredient"), other = id("other_ingredient");
        bind(false, target, FIRST);
        bind(true, target, FIRST);
        for (boolean tag : new boolean[]{false, true}) {
            bind(tag, other, FIRST);
            String key = EditVersions.binding(tag, target);
            String otherKey = EditVersions.binding(tag, other);
            String opposite = EditVersions.binding(!tag, target);
            var opened = EditVersions.snapshot();
            bind(tag, target, SECOND);
            check(!matches(opened, key), "same binding rejects stale save: tag=" + tag);
            check(matches(opened, otherKey, opposite, EditVersions.preset(FIRST), EditVersions.preset(SECOND)),
                    "other bindings, item/tag counterpart and presets remain editable: tag=" + tag);
            var changed = EditVersions.snapshot();
            bind(tag, target, FIRST);
            var restored = EditVersions.snapshot();
            check(restored.get(key) > changed.get(key) && changed.get(key) > opened.get(key),
                    "binding A-B-A advances versions twice: tag=" + tag);
            check(!matches(opened, key), "binding A-B-A cannot revive draft: tag=" + tag);
            check(IngredientCatalogueConfig.current().localFlavor(tag, target).flavors().equals(List.of(FIRST)),
                    "binding content really returned to A: tag=" + tag);
            bind(tag, target, FIRST);
            check(restored.equals(EditVersions.snapshot()), "identical binding write preserves tokens: tag=" + tag);
            var effective = tag ? IngredientCatalogue.tagFlavor(List.of(target)) : IngredientCatalogue.flavor(target);
            check(EditVersions.bindingVersion(tag, target, effective) == restored.get(key),
                    "per-row binding sync uses the snapshot token: tag=" + tag);
            check(restored.equals(EditVersions.snapshot()), "per-row sync does not invalidate collections: tag=" + tag);

            check(IngredientCatalogueConfig.setLocalFlavor(tag, target, null), "revert binding: tag=" + tag);
            check(!matches(restored, key), "binding removal invalidates draft: tag=" + tag);
            var deleted = EditVersions.snapshot();
            check(deleted.containsKey(key) && matches(deleted, key), "binding tombstone retained: tag=" + tag);
            bind(tag, target, FIRST);
            check(!matches(deleted, key), "binding recreation invalidates tombstone: tag=" + tag);
        }
    }

    private static void schemeCollections() {
        relatedEdit(false, () -> put(FIRST, "Scheme base"), "preset edit");
        relatedEdit(false, () -> bind(false, id("scheme_item"), FIRST), "item binding edit");
        relatedEdit(false, () -> bind(true, id("scheme_tag"), FIRST), "tag binding edit");
        relatedEdit(true, () -> check(GlobalCategoryStore.createRoot(id("root").toString(), "Root A", null),
                "create category root"), "category creation");
        relatedEdit(true, () -> check(GlobalCategoryStore.editRoot(id("root").toString(), "Root B", null),
                "edit category root"), "category edit");

        for (boolean category : new boolean[]{false, true}) {
            var kind = category ? SchemeStore.Kind.CATEGORY : SchemeStore.Kind.FLAVOR;
            var beforeSave = EditVersions.snapshot();
            String saved = SchemeStore.save(kind, null, category ? "Categories" : "Flavors", "Original");
            check(saved != null, "save real scheme: " + kind);
            assertRelatedEdit(beforeSave, category, "scheme creation: " + kind);

            var beforeRename = EditVersions.snapshot();
            check(SchemeStore.rename(kind, saved, "Renamed"), "rename real scheme: " + kind);
            assertRelatedEdit(beforeRename, category, "scheme metadata update: " + kind);
            check(matches(beforeRename, EditVersions.preset(FIRST), EditVersions.binding(false, id("scheme_item"))),
                    "scheme metadata does not invalidate individual flavor drafts: " + kind);

            // Observe the intermediate state so an actual A-B-A load cannot resurrect an old token.
            var beforeLoad = EditVersions.snapshot();
            relatedEdit(category, () -> check(SchemeStore.load(kind, SchemeStore.DEFAULT_ID),
                    "load empty scheme: " + kind), "default scheme load: " + kind);
            if (!category) {
                check(FlavorPresets.get(FIRST) == null
                                && IngredientCatalogueConfig.current().localFlavor(false, id("scheme_item")) == null,
                        "flavor load replaces presets and bindings");
                check(!matches(beforeLoad, EditVersions.preset(FIRST)), "scheme load invalidates preset draft");
                check(!matches(beforeLoad, EditVersions.binding(false, id("scheme_item"))),
                        "scheme load invalidates binding draft");
            } else check(!GlobalCategoryStore.hasCustomRoot(id("root").toString()), "category load replaces local roots");
            relatedEdit(category, () -> check(SchemeStore.load(kind, saved), "restore saved scheme: " + kind),
                    "saved scheme load: " + kind);
            check(!matches(beforeLoad, EditVersions.collection(category)), "scheme A-B-A rejects old collection: " + kind);
            if (!category) {
                check(FlavorPresets.get(FIRST).name().equals("Scheme base")
                                && IngredientCatalogueConfig.current().localFlavor(false, id("scheme_item")) != null,
                        "saved scheme restores real presets and bindings");
                check(!matches(beforeLoad, EditVersions.preset(FIRST), EditVersions.binding(false, id("scheme_item"))),
                        "restoring scheme content does not revive individual drafts");
            } else check(GlobalCategoryStore.hasCustomRoot(id("root").toString()), "saved category scheme restores root");

            var beforeOverwrite = EditVersions.snapshot();
            check(saved.equals(SchemeStore.save(kind, saved, null, "Updated description")),
                    "overwrite existing scheme: " + kind);
            assertRelatedEdit(beforeOverwrite, category, "scheme overwrite: " + kind);
            var beforeCopy = EditVersions.snapshot();
            String copy = SchemeStore.copy(kind, saved, "Copy");
            check(copy != null && !copy.equals(saved), "copy real scheme: " + kind);
            assertRelatedEdit(beforeCopy, category, "scheme copy: " + kind);
            relatedEdit(category, () -> check(SchemeStore.delete(kind, copy), "delete copied scheme: " + kind),
                    "scheme deletion: " + kind);
            var stable = EditVersions.snapshot();
            check(SchemeStore.load(kind, saved), "reload unchanged scheme: " + kind);
            check(stable.equals(EditVersions.snapshot()), "identical scheme load preserves tokens: " + kind);
        }
    }

    private static void packetRoundTrips() {
        var versions = new HashMap<>(EditVersions.snapshot());
        versions.put("flavor:preset:test:large_version", Long.MAX_VALUE);
        versions.put("category:root:test:zero_version", 0L);
        var expected = Map.copyOf(versions);
        var category = new Packets.CategoryEditRequest(17, Packets.CategoryEditRequest.Action.ASSIGN_FOODS,
                id("root"), null, "Root", id("icon"), List.of(id("one"), id("two")), versions);
        var flavor = new Packets.FlavorEditRequest(18, Packets.FlavorEditRequest.Action.BIND,
                id("scheme_tag"), true, true, List.of(FIRST, SECOND), "{}", 3, versions);
        var scheme = new Packets.SchemeRequest(19, false, Packets.SchemeRequest.Action.LOAD,
                "own:flavors", "Flavors", "Description", versions);
        var categorySync = new Packets.SyncCategoryEditor(List.of(new Packets.CategoryMetadata(id("root"),
                null, "Root", true, id("icon"), (byte) 2, false, false, "Root", true, id("icon"))),
                List.of(id("one")), versions);
        var flavorSync = new Packets.SyncFlavors(List.of(new Packets.FlavorPresetView(FIRST,
                FlavorPresets.get(FIRST).toJson().toString(), (byte) 2, 1)), true, 2, 3, true, versions);
        var schemeSync = new Packets.SyncSchemes(List.of(new Packets.SchemeView("own:flavors", (byte) 2,
                "Flavors", "Description", "2026-10-16T12:00", List.of(2, 3))),
                List.of(), "own:flavors", null, true, false, true, List.of(2, 3), List.of(1, 0, 0, 0, 0), versions);
        versions.clear();
        check(category.expectedVersions().equals(expected) && flavor.expectedVersions().equals(expected)
                        && scheme.expectedVersions().equals(expected) && categorySync.versions().equals(expected)
                        && flavorSync.versions().equals(expected) && schemeSync.versions().equals(expected),
                "requests and sync packets capture independent version map snapshots");
        check(roundTrip(category, category::write, Packets.CategoryEditRequest::read).expectedVersions().equals(expected),
                "category request expected versions survive wire");
        check(roundTrip(flavor, flavor::write, Packets.FlavorEditRequest::read).expectedVersions().equals(expected),
                "flavor request expected versions survive wire");
        check(roundTrip(scheme, scheme::write, Packets.SchemeRequest::read).expectedVersions().equals(expected),
                "scheme request expected versions survive wire");
        check(roundTrip(categorySync, categorySync::write, Packets.SyncCategoryEditor::read).versions().equals(expected),
                "category sync versions survive wire");
        check(roundTrip(flavorSync, flavorSync::write, Packets.SyncFlavors::read).versions().equals(expected),
                "flavor sync versions survive wire");
        check(roundTrip(schemeSync, schemeSync::write, Packets.SyncSchemes::read).versions().equals(expected),
                "scheme sync versions survive wire");
        var info = new Packets.FlavorInfo(true, List.of(FIRST), true, false,
                List.of(new Packets.InheritedFlavor(id("scheme_tag"), List.of(SECOND))), List.of("diagnostic"),
                EditVersions.snapshot().get(EditVersions.binding(false, id("scheme_item"))));
        roundTrip(info, info::write, Packets.FlavorInfo::read);
        var empty = new Packets.SyncCategoryEditor(List.of(), List.of(), Map.of());
        roundTrip(empty, empty::write, Packets.SyncCategoryEditor::read);
    }

    private static void malformedVersionMaps() {
        rejects(() -> new Packets.SyncCategoryEditor(List.of(), List.of(), Map.of("bad", -1L)),
                "negative version rejected by packet constructor");
        malformed(buffer -> buffer.writeVarInt(-1), "negative map size rejected on wire");
        malformed(buffer -> {
            buffer.writeVarInt(1); buffer.writeUtf("bad"); buffer.writeLong(-1);
        }, "negative version rejected on wire");
        malformed(buffer -> {
            buffer.writeVarInt(2);
            buffer.writeUtf("duplicate"); buffer.writeLong(1);
            buffer.writeUtf("duplicate"); buffer.writeLong(2);
        }, "duplicate version keys rejected on wire");
    }

    private static void malformed(Consumer<FriendlyByteBuf> mapWriter, String message) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(0); // No category metadata or manual items before the version map.
            buffer.writeVarInt(0);
            mapWriter.accept(buffer);
            rejects(() -> Packets.SyncCategoryEditor.read(buffer), message);
        } finally { buffer.release(); }
    }

    private static <T> T roundTrip(T value, Consumer<FriendlyByteBuf> writer, Function<FriendlyByteBuf, T> reader) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writer.accept(buffer);
            T decoded = reader.apply(buffer);
            check(value.equals(decoded), value.getClass().getSimpleName() + " round trip retains every field");
            check(buffer.readableBytes() == 0, value.getClass().getSimpleName() + " consumes every field");
            return decoded;
        } finally { buffer.release(); }
    }

    private static void relatedEdit(boolean category, Runnable edit, String message) {
        var before = EditVersions.snapshot();
        edit.run();
        assertRelatedEdit(before, category, message);
    }

    private static void assertRelatedEdit(Map<String, Long> before, boolean category, String message) {
        check(!matches(before, EditVersions.collection(category)), message + " invalidates related scheme collection");
        check(matches(before, EditVersions.collection(!category)), message + " leaves other scheme collection independent");
    }

    private static void put(ResourceLocation id, String name) {
        check(FlavorPresets.putLocal(new FlavorPresets.Preset(id, name, "", true, List.of())), "write preset " + id);
    }

    private static void bind(boolean tag, ResourceLocation id, ResourceLocation preset) {
        check(IngredientCatalogueConfig.setLocalFlavor(tag, id,
                IngredientCatalogueConfig.Flavor.of(true, List.of(preset), null)), "write binding " + tag + " " + id);
    }

    private static boolean matches(Map<String, Long> expected, String... keys) {
        return EditVersions.matches(expected, List.of(keys));
    }

    private static ResourceLocation id(String path) { return ResourceLocation.parse("test:" + path); }

    private static void rejects(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(message);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
