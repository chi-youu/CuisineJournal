package com.chi__you.cuisinejournal.event;

import com.chi__you.cuisinejournal.tracking.CookingContextTracker;
import com.chi__you.cuisinejournal.tracking.EatingTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-side orchestration for player lifecycle, food consumption and tick events.
 * Production-specific host hooks delegate directly to their tracking adapters.
 */
public final class CulinaryHooks {
    private CulinaryHooks() {
    }

    /**
     * Fired server-side once a player has finished consuming an item.
     *
     * @param player the eater; always a server player
     * @param stack  a copy of the stack as it was before consumption
     */
    public static void onItemEaten(ServerPlayer player, ItemStack stack) {
        com.chi__you.cuisinejournal.food.CakeSliceHealing.onEaten(player, stack);
        EatingTracker.onItemEaten(player, stack);
    }

    /** Fired when a player right-clicks a block, before the block reacts. */
    public static void onBlockUsed(ServerPlayer player, BlockPos pos, BlockState state) {
        CookingContextTracker.onWorkstationUsed(player, pos, state);
    }

    /** Fired at the end of every server tick. */
    public static void onServerTick(MinecraftServer server) {
        for(var player:server.getPlayerList().getPlayers())com.chi__you.cuisinejournal.skills.SkillsIntegration.tick(player);
        CookingContextTracker.onServerTick(server);
        com.chi__you.cuisinejournal.progression.TodayDishService.tick(server);
        com.chi__you.cuisinejournal.progression.DiscoveryService.tick(server);
        com.chi__you.cuisinejournal.challenge.DailyRotation.tick(server);
        if(server.getTickCount()%20==0&&com.chi__you.cuisinejournal.culinary.ProfileManager.get()!=null)
            for(var player:server.getPlayerList().getPlayers())com.chi__you.cuisinejournal.challenge.ChallengeManager.prepare(player,
                    com.chi__you.cuisinejournal.culinary.ProfileManager.get().of(player));
        com.chi__you.cuisinejournal.network.ServerPacketHandler.flushDiscoveryUpdates();
        com.chi__you.cuisinejournal.culinary.ProfileManager manager =
                com.chi__you.cuisinejournal.culinary.ProfileManager.get();
        if (manager != null) {
            manager.tickAutosave();
        }
    }

    public static void onPlayerJoin(ServerPlayer player) {
        com.chi__you.cuisinejournal.culinary.ProfileManager manager =
                com.chi__you.cuisinejournal.culinary.ProfileManager.get();
        if (manager != null) {
            manager.onJoin(player);
            com.chi__you.cuisinejournal.challenge.DailyChallengeHistory.get(player.server).include(manager.of(player));
            com.chi__you.cuisinejournal.progression.TodayDishService.visit(player);
            com.chi__you.cuisinejournal.milestone.MilestoneTracker.evaluate(player,manager.of(player));
        }
        // Push the catalogue, profile and rules straight away. Without this the
        // client has nothing to draw until something happens to ask for it, so
        // the journal and the stats panel stayed blank until the player ran a
        // command.
        com.chi__you.cuisinejournal.skills.SkillsIntegration.tick(player);
        com.chi__you.cuisinejournal.network.ServerPacketHandler.sendEverything(player);
    }

    public static void onPlayerQuit(ServerPlayer player) {
        com.chi__you.cuisinejournal.network.ServerPacketHandler.forgetJournalSync(player);
        com.chi__you.cuisinejournal.skills.SkillsIntegration.forget(player);
        CookingContextTracker.forget(player);
        com.chi__you.cuisinejournal.culinary.ProfileManager manager =
                com.chi__you.cuisinejournal.culinary.ProfileManager.get();
        if (manager != null) {
            manager.onQuit(player);
        }
    }
}
