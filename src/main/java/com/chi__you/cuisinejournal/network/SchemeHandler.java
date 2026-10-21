package com.chi__you.cuisinejournal.network;

import com.chi__you.cuisinejournal.CuisineJournalLog;
import com.chi__you.cuisinejournal.CuisineJournalServer;
import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.recipe.SchemeStore;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Server side of the flavor / category scheme editor (admins only). */
public final class SchemeHandler {
    private SchemeHandler() {
    }

    private static boolean canEdit(ServerPlayer player) {
        return player.hasPermissions(ConfigManager.server().multiplayer.adminPermissionLevel);
    }

    public static void sendSchemes(ServerPlayer player) {
        Network.toPlayer(player, new Packets.SyncSchemes(
                views(SchemeStore.Kind.FLAVOR), views(SchemeStore.Kind.CATEGORY),
                SchemeStore.currentId(SchemeStore.Kind.FLAVOR), SchemeStore.currentId(SchemeStore.Kind.CATEGORY),
                SchemeStore.dirty(SchemeStore.Kind.FLAVOR), SchemeStore.dirty(SchemeStore.Kind.CATEGORY), canEdit(player),
                counts(SchemeStore.Kind.FLAVOR, SchemeStore.currentContent(SchemeStore.Kind.FLAVOR)),
                counts(SchemeStore.Kind.CATEGORY, SchemeStore.currentContent(SchemeStore.Kind.CATEGORY)), EditVersions.snapshot()));
    }

    private static List<Packets.SchemeView> views(SchemeStore.Kind kind) {
        List<Packets.SchemeView> result = new ArrayList<>();
        for (SchemeStore.Scheme scheme : SchemeStore.list(kind)) {
            result.add(new Packets.SchemeView(scheme.id(), scheme.source(), scheme.name(), scheme.description(),
                    scheme.saved(), counts(kind, scheme.content())));
        }
        return result;
    }

    private static List<Integer> counts(SchemeStore.Kind kind, com.google.gson.JsonObject content) {
        return Arrays.stream(SchemeStore.counts(kind, content)).boxed().toList();
    }

    private static void broadcastSchemes() {
        var server = CuisineJournalServer.server();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sendSchemes(player);
    }

    public static void handle(ServerPlayer player, Packets.SchemeRequest request) {
        if (!canEdit(player)) { result(player, request, false, "cuisinejournal.scheme.failed.permission", List.of(), null); return; }
        SchemeStore.Kind kind = request.category() ? SchemeStore.Kind.CATEGORY : SchemeStore.Kind.FLAVOR;
        String id = request.scheme().isEmpty() ? null : request.scheme();
        if (!EditVersions.matches(request.expectedVersions(), List.of(EditVersions.collection(request.category())))) {
            result(player, request, false, "cuisinejournal.edit.failed.conflict", List.of(), null);
            sendSchemes(player);
            return;
        }
        var lease = com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.begin(
                com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.Kind.EDIT);
        if (lease == null) { result(player, request, false, "cuisinejournal.edit.failed.busy", List.of(), null); return; }
        boolean asynchronous = false;
        try {
            switch (request.action()) {
                case SAVE_NEW -> {
                    String saved = SchemeStore.save(kind, null, request.name(), request.description());
                    finish(player, request, saved != null, "cuisinejournal.scheme.toast.saved", request.name(), saved, false);
                }
                case OVERWRITE -> {
                    SchemeStore.Scheme scheme = SchemeStore.get(kind, id);
                    String saved = scheme == null ? null : SchemeStore.save(kind, id, null, null);
                    finish(player, request, saved != null, "cuisinejournal.scheme.toast.overwritten", scheme == null ? "" : scheme.name(), saved, false);
                }
                case LOAD -> {
                    SchemeStore.Scheme scheme = SchemeStore.get(kind, id);
                    if (scheme == null) { result(player,request,false,"cuisinejournal.scheme.failed.invalid",List.of(),null);return; }
                    com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.apply(
                            com.chi__you.cuisinejournal.platform.Services.PLATFORM.configDirectory(),
                            () -> { if (!SchemeStore.load(kind,id)) throw new IllegalStateException("Cannot load scheme"); },
                            CuisineJournalServer::rebuildCulinaryRegistry,
                            () -> finish(player,request,true,"cuisinejournal.scheme.toast.loaded",displayName(scheme),id,false));
                }
                case RENAME -> {
                    boolean ok = SchemeStore.rename(kind, id, request.name());
                    finish(player, request, ok, "cuisinejournal.scheme.toast.renamed", request.name(), id, false);
                }
                case COPY -> {
                    String saved = SchemeStore.copy(kind, id, request.name());
                    finish(player, request, saved != null, "cuisinejournal.scheme.toast.saved", request.name(), saved, false);
                }
                case DELETE -> {
                    SchemeStore.Scheme scheme = SchemeStore.get(kind, id);
                    boolean ok = SchemeStore.delete(kind, id);
                    finish(player, request, ok, "cuisinejournal.scheme.toast.deleted", scheme == null ? "" : scheme.name(), null, false);
                }
                case EXPORT -> {
                    SchemeStore.Scheme scheme = SchemeStore.forExport(kind, id);
                    if (scheme == null) { result(player, request, false, "cuisinejournal.scheme.failed.invalid", List.of(), null); return; }
                    String name = displayName(scheme);
                    lease.phase(com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.Kind.EXPORT);
                    var export = FlavorEditHandler.export(player.server, scheme, lease);
                    asynchronous = true;
                    export.whenCompleteAsync((written, failure) -> {
                        if(CuisineJournalServer.server()!=player.server||!lease.current())return;
                        lease.close();
                        EditVersions.snapshot();
                        if (failure != null) CuisineJournalLog.LOGGER.error("Cuisine Journal scheme export failed", failure);
                        result(player, request, failure == null, failure == null ? "cuisinejournal.scheme.toast.exported"
                                : "cuisinejournal.scheme.failed.export", failure == null ? List.of(name, FlavorEditHandler.PACK_NAME) : List.of(), id);
                        broadcastSchemes();
                    }, player.server);
                }
            }
        } catch (RuntimeException failure) {
            CuisineJournalLog.LOGGER.warn("Scheme request rejected: {}", failure.getMessage());
            result(player, request, false, "cuisinejournal.scheme.failed.invalid", List.of(), null);
        } finally {
            if (!asynchronous) { lease.close(); EditVersions.snapshot(); }
        }
    }

    /** Generated defaults have no stored name; the client translates "" into "百味纪默认". */
    private static String displayName(SchemeStore.Scheme scheme) {
        return scheme.name();
    }

    private static void finish(ServerPlayer player, Packets.SchemeRequest request, boolean ok, String key, String name,
                               String id, boolean rebuild) {
        if (ok && rebuild) CuisineJournalServer.rebuildCulinaryRegistry();
        result(player, request, ok, ok ? key : "cuisinejournal.scheme.failed.persistence", ok ? List.of(name) : List.of(), id);
        broadcastSchemes();
    }

    private static void result(ServerPlayer player, Packets.SchemeRequest request, boolean success, String key,
                               List<String> args, String id) {
        Network.toPlayer(player, new Packets.SchemeResult(request.requestId(), success, key, args, id));
    }
}
