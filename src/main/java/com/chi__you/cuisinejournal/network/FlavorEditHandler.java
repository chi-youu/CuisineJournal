package com.chi__you.cuisinejournal.network;

import com.chi__you.cuisinejournal.CuisineJournalLog;
import com.chi__you.cuisinejournal.CuisineJournalServer;
import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.food.CuisineFlavorEffects;
import com.chi__you.cuisinejournal.recipe.CulinaryRegistries;
import com.chi__you.cuisinejournal.recipe.CulinaryRegistry;
import com.chi__you.cuisinejournal.recipe.FlavorPresets;
import com.chi__you.cuisinejournal.recipe.GlobalCategoryStore;
import com.chi__you.cuisinejournal.recipe.IngredientCatalogue;
import com.chi__you.cuisinejournal.recipe.IngredientCatalogueConfig;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server side of the flavor editor: preset/binding sync, edit requests (admin only, saved
 * atomically, broadcast to every player through the usual rebuild) and the data-pack export
 * that turns in-game edits — flavors and categories — into a pack in the world folder.
 */
public final class FlavorEditHandler {
    /** Folder name (and pack id {@code file/<name>}) of the exported data pack. */
    public static final String PACK_NAME = FlavorPresets.CUSTOM_NAMESPACE;
    private static final String PACK_ID = "file/" + PACK_NAME;
    private static long session;
    public static void stop() { session++; }
    public static boolean exporting() { return com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.busy(
            com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.Kind.EXPORT); }

    private FlavorEditHandler() {
    }

    private static boolean canEdit(ServerPlayer player) {
        return player.hasPermissions(ConfigManager.server().multiplayer.adminPermissionLevel);
    }

    // ------------------------------------------------------------------ sync

    public static void sendFlavors(ServerPlayer player) {
        Map<ResourceLocation, Integer> usage = usage();
        List<Packets.FlavorPresetView> views = new ArrayList<>();
        for (FlavorPresets.Preset preset : FlavorPresets.all().values()) {
            views.add(new Packets.FlavorPresetView(preset.id(), preset.toJson().toString(),
                    FlavorPresets.origin(preset.id()), usage.getOrDefault(preset.id(), 0)));
        }
        IngredientCatalogueConfig config = IngredientCatalogueConfig.current();
        Network.toPlayer(player, new Packets.SyncFlavors(views, canEdit(player), FlavorPresets.overrides().size(),
                config.localFlavors(false).size() + config.localFlavors(true).size(), GlobalCategoryStore.hasLocalEdits(), EditVersions.snapshot()));
    }

    /** How many ingredient entries (items and tags) bind each preset as a flavor source. */
    private static Map<ResourceLocation, Integer> usage() {
        Map<ResourceLocation, Integer> result = new HashMap<>();
        CulinaryRegistry registry = CulinaryRegistries.current();
        for (ResourceLocation item : registry.catalogueItems()) count(result, IngredientCatalogue.flavor(item));
        for (CulinaryRegistry.TagEntry tag : registry.tags().values()) count(result, IngredientCatalogue.tagFlavor(ruleIds(tag)));
        return result;
    }

    private static void count(Map<ResourceLocation, Integer> result, IngredientCatalogueConfig.Flavor flavor) {
        if (flavor == null || !flavor.source()) return;
        for (ResourceLocation id : flavor.flavors()) result.merge(id, 1, Integer::sum);
    }

    private static List<ResourceLocation> ruleIds(CulinaryRegistry.TagEntry tag) {
        List<ResourceLocation> ids = new ArrayList<>();
        ids.add(tag.key()); ids.addAll(tag.ids());
        return ids;
    }

    /** Binding shown in the ingredient journal; items also list what their tag entries pass down. */
    public static Packets.FlavorInfo infoFor(ResourceLocation id, boolean tag, CulinaryRegistry registry) {
        IngredientCatalogueConfig.Flavor flavor;
        if (tag) {
            CulinaryRegistry.TagEntry entry = registry.tags().get(id);
            flavor = IngredientCatalogue.tagFlavor(entry == null ? List.of(id) : ruleIds(entry));
        } else flavor = IngredientCatalogue.flavor(id);
        List<String> problems = new ArrayList<>();
        List<ResourceLocation> bound = flavor == null ? List.of() : flavor.flavors();
        for (ResourceLocation preset : bound) if (FlavorPresets.get(preset) == null) problems.add("unknown:" + preset);
        List<Packets.InheritedFlavor> inherited = new ArrayList<>();
        if (!tag) {
            for (ResourceLocation key : registry.tagsOf(id)) {
                CulinaryRegistry.TagEntry entry = registry.tags().get(key);
                var tagFlavor = IngredientCatalogue.tagFlavor(entry == null ? List.of(key) : ruleIds(entry));
                if (tagFlavor != null && tagFlavor.source() && !tagFlavor.flavors().isEmpty())
                    inherited.add(new Packets.InheritedFlavor(key, tagFlavor.flavors()));
            }
        }
        boolean local = IngredientCatalogueConfig.current().localFlavor(tag, id) != null;
        return new Packets.FlavorInfo(flavor != null && flavor.source(), bound, local,
                flavor != null && !flavor.stages().isEmpty(), inherited, problems, EditVersions.bindingVersion(tag, id, flavor));
    }

    // ------------------------------------------------------------------ edits

    public static void handle(ServerPlayer player, Packets.FlavorEditRequest request) {
        var lease = com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.begin(
                com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.Kind.EDIT);
        if (lease == null) { result(player,request,false,"cuisinejournal.flavor.failed.busy",List.of(),null);return; }
        try { handleOwned(player, request); }
        finally { lease.close(); }
    }

    private static void handleOwned(ServerPlayer player, Packets.FlavorEditRequest request) {
        if (!canEdit(player)) { result(player, request, false, "cuisinejournal.flavor.failed.permission", List.of(), null); return; }
        List<String> keys = new ArrayList<>();
        if (request.target() != null) keys.add(switch (request.action()) {
            case BIND, REVERT_BINDING -> EditVersions.binding(request.tag(), request.target());
            default -> EditVersions.preset(request.target());
        });
        if (request.action() == Packets.FlavorEditRequest.Action.BIND)
            request.flavors().forEach(id -> keys.add(EditVersions.preset(id)));
        if (!EditVersions.matches(request.expectedVersions(), keys)) {
            result(player, request, false, "cuisinejournal.edit.failed.conflict", List.of(), null);
            sendFlavors(player);
            ServerPacketHandler.sendProfile(player);
            return;
        }
        ResourceLocation created = null;
        String message;
        List<String> args = List.of();
        boolean success;
        com.chi__you.cuisinejournal.data.LocalEditSnapshot snapshot = null;
        try {
            snapshot=com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.checkpoint(
                    com.chi__you.cuisinejournal.platform.Services.PLATFORM.configDirectory());
            switch (request.action()) {
                case BIND -> {
                    if (request.target() == null) throw new IllegalArgumentException("No target");
                    boolean validTarget=request.tag() ? CulinaryRegistries.current().tags().values().stream()
                            .anyMatch(t->t.key().equals(request.target())||t.ids().contains(request.target()))
                            : BuiltInRegistries.ITEM.containsKey(request.target())&&!request.target().toString().equals("minecraft:air");
                    if(!validTarget)throw new IllegalArgumentException("Unknown ingredient target");
                    if(IngredientCatalogueConfig.current().localFlavors(request.tag()).size()>=16_384
                            &&IngredientCatalogueConfig.current().localFlavor(request.tag(),request.target())==null)
                        throw new IllegalArgumentException("Too many flavor bindings");
                    for (ResourceLocation id : request.flavors())
                        if (FlavorPresets.get(id) == null) throw new IllegalArgumentException("Unknown flavor " + id);
                    var previous = request.tag() ? IngredientCatalogue.tagFlavor(List.of(request.target()))
                            : IngredientCatalogue.flavor(request.target());
                    success = IngredientCatalogueConfig.setLocalFlavor(request.tag(), request.target(),
                            IngredientCatalogueConfig.Flavor.of(request.source(), request.flavors(), previous));
                    message = "cuisinejournal.flavor.toast.bound";
                }
                case REVERT_BINDING -> {
                    if (request.target() == null) throw new IllegalArgumentException("No target");
                    success = IngredientCatalogueConfig.setLocalFlavor(request.tag(), request.target(), null);
                    message = "cuisinejournal.flavor.toast.reverted";
                }
                case SAVE_PRESET -> {
                    JsonObject json = JsonParser.parseString(request.json()).getAsJsonObject();
                    ResourceLocation id = request.target();
                    if (id == null) {
                        if(FlavorPresets.all().size()>=4096)throw new IllegalArgumentException("Too many flavor presets");
                        String name = json.has("name") ? json.get("name").getAsString() : "";
                        id = FlavorPresets.newId(name);
                        created = id;
                    } else if (FlavorPresets.get(id) == null) throw new IllegalArgumentException("Unknown flavor " + id);
                    FlavorPresets.Preset preset = FlavorPresets.read(id, json);
                    if (preset.icon() != null && !BuiltInRegistries.ITEM.containsKey(preset.icon()))
                        throw new IllegalArgumentException("Unknown icon " + preset.icon());
                    if (preset.iconEffect() != null && !BuiltInRegistries.MOB_EFFECT.containsKey(com.chi__you.cuisinejournal.food.CuisineEffectIds.resolve(preset.iconEffect())))
                        throw new IllegalArgumentException("Unknown effect icon " + preset.iconEffect());
                    success = FlavorPresets.putLocal(preset);
                    message = created != null ? "cuisinejournal.flavor.toast.created" : "cuisinejournal.flavor.toast.saved";
                    args = List.of(preset.name());
                }
                case REVERT_PRESET -> {
                    if (request.target() == null || FlavorPresets.origin(request.target()) != 1) throw new IllegalArgumentException("Not edited");
                    success = FlavorPresets.removeLocal(request.target());
                    message = "cuisinejournal.flavor.toast.reverted";
                }
                case DELETE_PRESET -> {
                    // Only in-game presets can be deleted; data-pack presets are disabled instead.
                    if (request.target() == null || FlavorPresets.origin(request.target()) != 2) throw new IllegalArgumentException("Not deletable");
                    success = FlavorPresets.removeLocal(request.target());
                    message = "cuisinejournal.flavor.toast.deleted";
                }
                default -> throw new IllegalArgumentException("Unknown action");
            }
        } catch (RuntimeException failure) {
            if (snapshot != null) com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.rollback(snapshot,failure);
            CuisineJournalLog.LOGGER.warn("Flavor edit rejected: {}", failure.getMessage());
            result(player, request, false, "cuisinejournal.flavor.failed.invalid", List.of(), null);
            return;
        }
        if (success) {
            try {CuisineJournalServer.rebuildCulinaryRegistry();EditVersions.snapshot();}
            catch(RuntimeException failure){
                com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.rollback(snapshot,failure);
                CuisineJournalLog.LOGGER.error("Flavor edit reload failed; restoring previous edits",failure);
                result(player,request,false,"cuisinejournal.flavor.failed.persistence",List.of(),null);return;
            }
        }
        result(player, request, success, success ? message : "cuisinejournal.flavor.failed.persistence", args, created);
    }

    private static void result(ServerPlayer player, Packets.FlavorEditRequest request, boolean success, String key,
                               List<String> args, ResourceLocation created) {
        Network.toPlayer(player, new Packets.FlavorEditResult(request.requestId(), success, key, args, created));
    }

    // ------------------------------------------------------------------ preview

    public static void preview(ServerPlayer player, Packets.FlavorPreviewRequest request) {
        var item = BuiltInRegistries.ITEM.getOptional(request.dish()).orElse(null);
        if (item == null) return;
        ItemStack stack = new ItemStack(item);
        new com.chi__you.cuisinejournal.culinary.MealStamp(request.stars(), false, 5, null, "").write(stack);
        boolean values = com.chi__you.cuisinejournal.food.CuisineFoodRuntime.active();
        var after = values ? com.chi__you.cuisinejournal.food.CuisineFoodService.definition(stack) : null;
        var before = values ? CuisineFlavorEffects.withoutFlavors(() -> com.chi__you.cuisinejournal.food.CuisineFoodService.definition(stack)) : null;
        values = after != null && before != null;
        var resolved = CuisineFlavorEffects.resolveStack(stack, after != null && after.drink());
        List<ResourceLocation> effects = new ArrayList<>();
        List<Integer> levels = new ArrayList<>(), seconds = new ArrayList<>();
        for (var effect : com.chi__you.cuisinejournal.food.CuisineFoodEffectSource.forStack(stack)) {
            effects.add(effect.id()); levels.add(effect.level());
            int ticks = values ? effect.activeTicks(after.durationTicks()) : effect.durationTicks();
            seconds.add(Math.max(0, ticks / 20));
        }
        Network.toPlayer(player, new Packets.FlavorPreview(request.dish(), request.stars(), values,
                values ? before.healthPoints() : 0, values ? after.healthPoints() : 0,
                values ? com.chi__you.cuisinejournal.food.CuisineFoodStats.displayedRegen(before) : 0,
                values ? com.chi__you.cuisinejournal.food.CuisineFoodStats.displayedRegen(after) : 0,
                values ? before.durationTicks() / 20 : 0, values ? after.durationTicks() / 20 : 0,
                effects, levels, seconds, resolved.flavors()));
    }

    // ------------------------------------------------------------------ export

    /**
     * Writes one scheme into {@code <world>/datapacks/cuisinejournal_custom}, replacing whatever
     * that pack held for the scheme's kind (flavor: presets + bindings; category: the overlay),
     * then enables the pack and reloads. Local edits are left alone.
     *
     * @return number of files written once the reload has completed
     */
    public static java.util.concurrent.CompletableFuture<Integer> export(MinecraftServer server,
            com.chi__you.cuisinejournal.recipe.SchemeStore.Scheme scheme,
            com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.Lease lease) {
        if (scheme == null) return failedExport();
        if (lease == null || !lease.current())
            return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Export has no active edit lease"));
        List<String> previousSelected = new ArrayList<>(server.getPackRepository().getSelectedIds());
        long operationSession=session;
        ExportTransaction transaction = null;
        try {
            transaction = new ExportTransaction(server.getWorldPath(LevelResource.DATAPACK_DIR));
            int written = writeExport(transaction.staged, scheme);
            transaction.install();
            var repository = server.getPackRepository();
            repository.reload();
            if (!repository.isAvailable(PACK_ID)) throw new java.io.IOException("Exported pack unavailable");
            List<String> selected = new ArrayList<>(previousSelected);
            if (!selected.contains(PACK_ID)) selected.add(PACK_ID);
            ExportTransaction installed = transaction;
            var completion = ExportCompletion.afterReload(server.reloadResources(selected), server, written, () -> {
                if(operationSession!=session || CuisineJournalServer.server()!=server)return false;
                CuisineJournalServer.rebuildCulinaryRegistry();
                installed.cleanup();
                return true;
            });
            return completion.handleAsync((value, failure) -> {
                if(operationSession!=session || CuisineJournalServer.server()!=server)
                    return java.util.concurrent.CompletableFuture.<Integer>failedFuture(new IllegalStateException("Server stopped during export"));
                if (failure != null) return rollbackExport(server, installed, previousSelected, failure);
                return java.util.concurrent.CompletableFuture.completedFuture(value);
            }, server).thenCompose(result -> result);
        } catch (Exception failure) {
            if (transaction == null) {
                return java.util.concurrent.CompletableFuture.failedFuture(failure);
            }
            return rollbackExport(server, transaction, previousSelected, failure);
        }
    }

    private static int writeExport(Path root, com.chi__you.cuisinejournal.recipe.SchemeStore.Scheme scheme)
            throws java.io.IOException {
        var gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        int written = 0;
        JsonObject meta = new JsonObject(), pack = new JsonObject();
        pack.addProperty("pack_format", 15);
        pack.addProperty("description", "Cuisine Journal - in-game edits");
        meta.add("pack", pack);
        writeExportFile(root.resolve("pack.mcmeta"), gson.toJson(meta));
        JsonObject content = scheme.content();
        if (scheme.kind() == com.chi__you.cuisinejournal.recipe.SchemeStore.Kind.FLAVOR) {
            // Replace: drop every flavor file this pack wrote before, then write the scheme's.
            Path data = root.resolve("data");
            if (Files.isDirectory(data)) try (var namespaces = Files.list(data)) {
                for (Path namespace : namespaces.toList()) deleteTree(namespace.resolve("cuisinejournal/flavors"));
            }
            JsonObject flavors = content.has("flavors") ? content.getAsJsonObject("flavors") : new JsonObject();
            for (var entry : flavors.entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                if (id == null) continue;
                Path flavorsRoot = root.resolve("data").resolve(id.getNamespace()).resolve("cuisinejournal/flavors");
                Path file = flavorsRoot.resolve(id.getPath() + ".json").normalize();
                if (!file.startsWith(flavorsRoot)) throw new java.io.IOException("Flavor path escapes export directory");
                writeExportFile(file, gson.toJson(entry.getValue()));
                written++;
            }
            JsonObject bindings = content.has("bindings") ? content.getAsJsonObject("bindings").deepCopy() : new JsonObject();
            bindings.addProperty("priority", 1000);
            Path file = root.resolve("data/" + PACK_NAME + "/cuisinejournal/compat/flavor_bindings.json");
            writeExportFile(file, gson.toJson(bindings));
            written++;
        } else {
            JsonObject categories = content.has("categories") ? content.getAsJsonObject("categories") : new JsonObject();
            Path file = root.resolve("data/" + PACK_NAME + "/cuisinejournal/category_overlays/edits.json");
            writeExportFile(file, gson.toJson(GlobalCategoryStore.normalisedJson(categories)));
            written++;
        }
        return written;
    }

    private static void writeExportFile(Path file, String json) throws java.io.IOException {
        if (!FlavorPresets.writeAtomically(file, json)) throw new java.io.IOException("Could not write export file " + file);
    }

    private static java.util.concurrent.CompletableFuture<Integer> rollbackExport(MinecraftServer server,
            ExportTransaction transaction, List<String> selected, Throwable failure) {
        try {
            boolean reload = transaction.installed;
            transaction.rollback();
            transaction.cleanup();
            if (reload) {
                var repository = server.getPackRepository();
                repository.reload();
                repository.setSelected(selected);
                // Reload may have applied listeners before failing; restore runtime state too.
                return server.reloadResources(selected).handleAsync((ignored, rollbackFailure) -> {
                    if (rollbackFailure != null) failure.addSuppressed(rollbackFailure);
                    throw new java.util.concurrent.CompletionException(failure);
                }, server);
            }
        } catch (Exception rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
            CuisineJournalLog.LOGGER.error("Could not roll back scheme export; backup retained at {}", transaction.backup, rollbackFailure);
        }
        return java.util.concurrent.CompletableFuture.failedFuture(failure);
    }

    private static final class ExportTransaction {
        private final Path root, staged, backup;
        private boolean backedUp, installed;

        private ExportTransaction(Path datapacks) throws java.io.IOException {
            Files.createDirectories(datapacks);
            Path parent = datapacks.toRealPath();
            root = parent.resolve(PACK_NAME);
            staged = Files.createTempDirectory(parent, "." + PACK_NAME + "-stage-");
            backup = staged.resolveSibling(staged.getFileName() + "-backup");
            try {
                if (Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) copyPack(root, staged);
            } catch (java.io.IOException | RuntimeException failure) {
                cleanup();
                throw failure;
            }
        }

        private void install() throws java.io.IOException {
            if (Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                // The backup container has no pack.mcmeta, so repository scans cannot enable it.
                Files.createDirectory(backup);
                Files.move(root, backup.resolve("pack"));
                backedUp = true;
            }
            Files.move(staged, root);
            installed = true;
        }

        private void rollback() throws java.io.IOException {
            if (installed) {
                Files.move(root, staged);
                installed = false;
            }
            if (backedUp) {
                Files.move(backup.resolve("pack"), root);
                backedUp = false;
            }
        }

        private void cleanup() {
            try {
                deleteTree(staged);
                deleteTree(backup);
            } catch (java.io.IOException failure) {
                CuisineJournalLog.LOGGER.warn("Could not clean export staging/backup {}", staged, failure);
            }
        }
    }

    private static void copyPack(Path source, Path target) throws java.io.IOException {
        try (var walk = Files.walk(source)) {
            for (Path path : walk.toList()) {
                // Reject links (including redirected directories) before copying or pruning.
                if (Files.isSymbolicLink(path) || !path.toRealPath().equals(path.toAbsolutePath().normalize()))
                    throw new java.io.IOException("Linked export path: " + path);
                Path destination = target.resolve(source.relativize(path));
                if (Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(destination);
                else if (Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) Files.copy(path, destination);
                else throw new java.io.IOException("Unsupported export path: " + path);
            }
        }
    }

    private static void deleteTree(Path directory) throws java.io.IOException {
        if (!Files.exists(directory, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        try (var walk = Files.walk(directory)) {
            for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static java.util.concurrent.CompletableFuture<Integer> failedExport() {
        return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Export could not be written/enabled"));
    }

    private static JsonObject read(Path file) {
        try {
            if (Files.isRegularFile(file)) return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception failure) {
            CuisineJournalLog.LOGGER.warn("Ignoring unreadable {}: {}", file, failure.getMessage());
        }
        return new JsonObject();
    }

    /** Category overlays shipped by data packs, merged below this server's local edits. */
    public static List<JsonObject> categoryOverlays(net.minecraft.server.packs.resources.ResourceManager resources) {
        Map<ResourceLocation, JsonObject> found = new LinkedHashMap<>();
        resources.listResources("cuisinejournal/category_overlays", path -> path.getPath().endsWith(".json"))
                .forEach((path, resource) -> {
                    try (var reader = resource.openAsReader()) {
                        found.put(path, JsonParser.parseReader(reader).getAsJsonObject());
                    } catch (Exception failure) {
                        CuisineJournalLog.LOGGER.warn("Skipping category overlay {}: {}", path, failure.getMessage());
                    }
                });
        return new ArrayList<>(found.values());
    }
}
