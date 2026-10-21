package com.chi__you.cuisinejournal.network;

import com.chi__you.cuisinejournal.CuisineJournalLog;
import com.chi__you.cuisinejournal.CuisineJournalServer;
import com.chi__you.cuisinejournal.config.ConfigEnums;
import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.config.ServerConfig;
import com.chi__you.cuisinejournal.compat.KaleidoscopeUnificationCompat;
import com.chi__you.cuisinejournal.food.CuisineFoodRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/**
 * The server half of the settings screen's "服务器" tab: a summary of the rules it shows, and
 * admin-only changes. Flavor and nutrition have mode defaults; native potion effects are independent.
 */
public final class ServerSettingsHandler {
    private static long session;
    public static void stop() { session++; }
    public static boolean unificationBusy() { return com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.busy(
            com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.Kind.UNIFICATION); }
    private ServerSettingsHandler() {
    }

    public static ConfigEnums.DiscoveryMode editableDiscoveryMode(String value) {
        var mode=ConfigEnums.DiscoveryMode.valueOf(value);
        if(mode==ConfigEnums.DiscoveryMode.ALWAYS_VISIBLE)
            throw new IllegalArgumentException("Automatic discovery of all dishes is configuration-file-only");
        return mode;
    }

    private static boolean canEdit(ServerPlayer player) {
        return player.hasPermissions(ConfigManager.server().multiplayer.adminPermissionLevel);
    }

    public static Packets.SyncServerSettings view(ServerPlayer player) {
        ServerConfig config = ConfigManager.server();
        boolean valheim = CuisineFoodRuntime.active();
        int manual = (config.flavors.enabled != valheim ? 1 : 0)
                | (config.nutritionDisplay.enabled != !valheim ? 4 : 0);
        int star = !config.mastery.enabled ? 0 : config.mastery.perDish() ? 2 : 1;
        return new Packets.SyncServerSettings(canEdit(player), valheim ? "valheim" : "vanilla",
                ModList.get().isLoaded("sol_valheim"), config.flavors.enabled, config.foodEffects.enabled,
                config.nutritionDisplay.enabled, manual, star, config.discovery.mode.name(), config.discovery.shared.name(),
                config.discovery.hideUndiscoveredDetails,
                config.compatibility.unifiedMode,
                KaleidoscopeUnificationCompat.installed(), unificationBusy());
    }

    public static void send(ServerPlayer player) {
        Network.toPlayer(player, view(player));
    }

    public static void handle(ServerPlayer player, Packets.SetServerSetting request) {
        if (!canEdit(player)) { send(player); return; }
        if (com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.busy()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("cuisinejournal.settings.unification.busy"));
            send(player); return;
        }
        if (request.key() == Packets.SetServerSetting.Key.UNIFICATION) {
            if (!request.value().equals("true") && !request.value().equals("false")) { send(player); return; }
            try { changeUnification(player, Boolean.parseBoolean(request.value())); }
            catch (RuntimeException failure) {
                CuisineJournalLog.LOGGER.error("Cannot start unification reload",failure);
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("cuisinejournal.settings.unification.failed"));
                send(player);
            }
            return;
        }
        var lease = com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.begin(
                com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.Kind.EDIT);
        if (lease == null) { send(player); return; }
        try { applySetting(player, request); }
        finally { lease.close(); }
    }

    private static void applySetting(ServerPlayer player, Packets.SetServerSetting request) {
        var checkpoint=com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.checkpoint(
                com.chi__you.cuisinejournal.platform.Services.PLATFORM.configDirectory());
        ServerConfig previous = ConfigManager.server();
        boolean previousNative=com.chi__you.cuisinejournal.food.CuisineFoodConfig.PRESERVE_NATIVE_EFFECTS.get();
        ServerConfig config = com.chi__you.cuisinejournal.config.Toml.read(
                com.chi__you.cuisinejournal.config.Toml.write(previous,java.util.List.of()),ServerConfig.class);
        boolean valheim = CuisineFoodRuntime.active();
        boolean rebuild = false;
        try {
            String value = request.value();
            switch (request.key()) {
                case FOOD_MODE -> {
                    if (!value.equals("valheim") && !value.equals("vanilla")) throw new IllegalArgumentException(value);
                    if (ModList.get().isLoaded("sol_valheim") && value.equals("valheim")) throw new IllegalStateException("sol_valheim");
                    if (value.equals(valheim ? "valheim" : "vanilla")) { send(player); return; }
                    // setFoodSystem rebuilds; the rebuild applies the new mode's defaults once.
                    CuisineJournalServer.setFoodSystem(value);
                    broadcast(player);
                    return;
                }
                case FLAVORS -> { config.flavors.enabled = Boolean.parseBoolean(value); rebuild = true; }
                case POTIONS -> {
                    config.foodEffects.enabled = Boolean.parseBoolean(value);
                    if(config.foodEffects.enabled) {
                        com.chi__you.cuisinejournal.food.CuisineFoodConfig.PRESERVE_NATIVE_EFFECTS.set(true);
                        com.chi__you.cuisinejournal.food.CuisineFoodConfig.PRESERVE_NATIVE_EFFECTS.save();
                    }
                    rebuild = true;
                }
                case NUTRITION -> config.nutritionDisplay.enabled = Boolean.parseBoolean(value);
                case RESET_FLAVORS -> { config.flavors.enabled = valheim; rebuild = true; }
                case RESET_POTIONS -> {
                    config.foodEffects.enabled = true;
                    if(config.foodEffects.enabled) {
                        com.chi__you.cuisinejournal.food.CuisineFoodConfig.PRESERVE_NATIVE_EFFECTS.set(true);
                        com.chi__you.cuisinejournal.food.CuisineFoodConfig.PRESERVE_NATIVE_EFFECTS.save();
                    }
                    rebuild = true;
                }
                case RESET_NUTRITION -> config.nutritionDisplay.enabled = !valheim;
                case STAR_MODE -> {
                    int mode = Integer.parseInt(value);
                    if (mode < 0 || mode > 2) throw new IllegalArgumentException(value);
                    config.mastery.enabled = mode != 0;
                    if (mode != 0) config.mastery.scope = mode == 2 ? ConfigEnums.MasteryScope.DISH : ConfigEnums.MasteryScope.SUBCATEGORY;
                    rebuild = true;
                }
                case DISCOVERY -> config.discovery.mode = editableDiscoveryMode(value);
                case SHARING -> config.discovery.shared = ConfigEnums.SharedDiscovery.valueOf(value);
                case HIDE_UNDISCOVERED -> config.discovery.hideUndiscoveredDetails = Boolean.parseBoolean(value);
            }
        } catch (RuntimeException failure) {
            restoreNative(previousNative,failure);
            com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.rollback(checkpoint,failure);
            CuisineJournalLog.LOGGER.warn("Server setting {} rejected: {}", request.key(), failure.getMessage());
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Setting rejected: "+failure.getMessage()));
            send(player);
            return;
        }
        if (!ConfigManager.saveServerChecked(config)) {
            restoreNative(previousNative,null);
            var failure=new IllegalStateException("Cannot save settings");
            com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.rollback(checkpoint,failure);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Could not save settings; no change applied"));
            send(player); return;
        }
        try { if (rebuild) CuisineJournalServer.rebuildCulinaryRegistry(); }
        catch (RuntimeException failure) {
            restoreNative(previousNative,failure);
            com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.rollback(checkpoint,failure);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Setting reload failed; previous settings restored"));
            CuisineJournalLog.LOGGER.error("Setting reload failed",failure);send(player);return;
        }
        broadcast(player);
    }
    private static void restoreNative(boolean previous,Throwable original){
        var setting=com.chi__you.cuisinejournal.food.CuisineFoodConfig.PRESERVE_NATIVE_EFFECTS;
        if(setting.get()==previous)return;
        setting.set(previous);
        try{setting.save();}catch(RuntimeException failure){
            if(original!=null)original.addSuppressed(failure);
            CuisineJournalLog.LOGGER.error("Cannot persist restored native food effect setting",failure);
        }
    }

    private static void changeUnification(ServerPlayer player, boolean enabled) {
        var lease = com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.begin(
                com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.Kind.UNIFICATION);
        if (lease == null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("cuisinejournal.settings.unification.busy"));
            send(player); return;
        }
        var server = player.server;
        long operationSession=session;
        boolean previous = ConfigManager.server().compatibility.unifiedMode;
        String mode;
        com.chi__you.cuisinejournal.compat.UnificationReload.Transaction transaction;
        try {
            mode = KaleidoscopeUnificationCompat.installed() ? KaleidoscopeUnificationCompat.currentMode() : null;
            transaction = com.chi__you.cuisinejournal.compat.UnificationReload.capture(server);
        } catch (RuntimeException failure) { lease.close(); throw failure; }
        for (var online : server.getPlayerList().getPlayers()) send(online);
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("cuisinejournal.settings.unification.busy"));
        java.util.concurrent.CompletableFuture<Void> applying;
        try {
            KaleidoscopeUnificationCompat.apply(enabled);
            applying = transaction.apply(enabled);
        } catch (RuntimeException failure) { applying = java.util.concurrent.CompletableFuture.failedFuture(failure); }
        applying.thenRunAsync(() -> {
            if(operationSession!=session || CuisineJournalServer.server()!=server)return;
            var current = com.chi__you.cuisinejournal.config.Toml.read(
                    com.chi__you.cuisinejournal.config.Toml.write(ConfigManager.server(),java.util.List.of()),ServerConfig.class);
            current.compatibility.unifiedMode = enabled;
            if (!ConfigManager.saveServerChecked(current)) throw new IllegalStateException("Cannot save unification setting");
            CuisineJournalServer.rebuildCulinaryRegistry();
        }, server).handleAsync((ignored, failure) -> {
            if(operationSession!=session || CuisineJournalServer.server()!=server)
                return java.util.concurrent.CompletableFuture.<Throwable>completedFuture(failure);
            if (failure == null) return java.util.concurrent.CompletableFuture.<Throwable>completedFuture(null);
            CuisineJournalLog.LOGGER.error("Unification reload failed; restoring previous packs", failure);
            try { if (mode != null) KaleidoscopeUnificationCompat.applyMode(mode); }
            catch (RuntimeException restoreFailure) { failure.addSuppressed(restoreFailure); }
            var current = com.chi__you.cuisinejournal.config.Toml.read(
                    com.chi__you.cuisinejournal.config.Toml.write(ConfigManager.server(),java.util.List.of()),ServerConfig.class);
            current.compatibility.unifiedMode = previous;
            ConfigManager.restoreRuntime(current);
            if (!ConfigManager.saveServerChecked(current))
                failure.addSuppressed(new IllegalStateException("Cannot persist restored unification setting"));
            return transaction.rollback().handleAsync((restored, restoreFailure) -> {
                if (restoreFailure != null) {
                    failure.addSuppressed(restoreFailure);
                    CuisineJournalLog.LOGGER.error("Unification rollback failed", failure);
                }
                return failure;
            }, server);
        }, server).thenCompose(completion -> completion).whenCompleteAsync((failure, unexpected) -> {
            if(operationSession!=session || CuisineJournalServer.server()!=server)return;
            lease.close();
            boolean success = failure == null && unexpected == null;
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    success ? "cuisinejournal.settings.unification.applied" : "cuisinejournal.settings.unification.failed"));
            broadcast(player);
        }, server);
    }

    /** Everyone gets the new rules (and, after a rebuild, the rest follows from it). */
    private static void broadcast(ServerPlayer source) {
        for (ServerPlayer player : source.server.getPlayerList().getPlayers()) {
            ServerPacketHandler.sendRules(player);
            com.chi__you.cuisinejournal.food.CuisineFoodSync.send(player);
            send(player);
            if (player != source) continue;
            ServerPacketHandler.sendEverything(player);
        }
    }
}
