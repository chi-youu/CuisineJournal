package com.chi__you.cuisinejournal.challenge;

import com.chi__you.cuisinejournal.CuisineJournalLog;
import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.config.ServerConfig;
import com.chi__you.cuisinejournal.culinary.CulinaryProfile;
import com.chi__you.cuisinejournal.event.CulinaryEvents;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Hands out what a challenge promised.
 *
 * <p>Every branch is individually guarded: a datapack that names an item or an
 * advancement that does not exist logs a warning and the rest of the reward is
 * still granted.</p>
 */
public final class ChallengeRewards {

    private ChallengeRewards() {
    }

    public static void grant(ServerPlayer player, CulinaryProfile profile, ChallengeReward reward) {
        if (reward == null || reward == ChallengeReward.NONE) {
            return;
        }
        ServerConfig config = ConfigManager.server();
        int before=profile.cookingLevel(config.progression.toCurve());

        if (config.progression.enabled && reward.cookingXp() > 0) {
            long xp = cookingXp(reward,config);
            if (xp > 0L) {
                profile.addCookingXp(xp, config.progression.toCurve());
                CulinaryEvents.COOKING_XP_GAINED.fire(new CulinaryEvents.CookingXpGained(
                        player, profile, xp, null));
            }
        }
        if (reward.vanillaXp() > 0) {
            player.giveExperiencePoints(reward.vanillaXp());
        }
        if(reward.masteryXp()>0){
            ResourceLocation dish=reward.masteryDish();
            if(dish!=null)com.chi__you.cuisinejournal.progression.ProgressionService.grantMasteryBonus(player,profile,dish,
                    reward.masteryXp(),com.chi__you.cuisinejournal.culinary.CulinaryClock.day(player.server));
            else CuisineJournalLog.LOGGER.warn("Ignoring mastery reward without an explicit mastery_dish");
        }
        if (reward.badge() != null) {
            profile.awardBadge(reward.badge());
        }
        if (reward.advancement() != null) {
            grantAdvancement(player, reward.advancement());
        }
        for (String entry : reward.items()) {
            giveItem(player, entry);
        }
        if (!reward.commands().isEmpty()) {
            if (!config.challenges.allowCommandRewards) {
                CuisineJournalLog.LOGGER.debug(
                        "Skipping {} command reward(s): challenges.allowCommandRewards is off",
                        reward.commands().size());
            } else {
                for (String command : reward.commands()) {
                    runCommand(player, command);
                }
            }
        }
        int after=profile.cookingLevel(config.progression.toCurve());
        if(after>before)CulinaryEvents.COOKING_LEVEL_CHANGED.fire(new CulinaryEvents.CookingLevelChanged(player,profile,before,after));
    }

    static void grantAdvancement(ServerPlayer player, ResourceLocation id) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(id);
        if (advancement == null) {
            CuisineJournalLog.LOGGER.warn("Challenge reward names an unknown advancement: {}", id);
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }
    public static long cookingXp(ChallengeReward reward,ServerConfig config){
        return config.progression.enabled?Math.max(0,Math.round(reward.cookingXp()
                *config.challenges.challengeXpMultiplier*config.progression.xpMultiplier)):0;
    }

    /** Accepts {@code "modid:item"} or {@code "modid:item 3"}. */
    static void giveItem(ServerPlayer player, String entry) {
        var reward = ChallengeReward.parseItem(entry);
        if (reward == null) {
            CuisineJournalLog.LOGGER.warn("Challenge item reward is invalid: '{}'", entry);
            return;
        }
        ResourceLocation id = reward.id();
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            CuisineJournalLog.LOGGER.warn("Challenge item reward names an unknown item: {}", id);
            return;
        }
        int remaining=reward.count();
        while(remaining>0){
            int amount=Math.min(remaining,Math.max(1,item.getMaxStackSize()));
            ItemStack stack = new ItemStack(item, amount);
            if (!player.getInventory().add(stack) && !stack.isEmpty()) player.drop(stack, false);
            remaining-=amount;
        }
    }

    private static void runCommand(ServerPlayer player, String command) {
        String resolved = command.replace("@s", player.getGameProfile().getName());
        try {
            player.server.getCommands().performPrefixedCommand(
                    ChallengeManager.rewardSource(player), resolved);
        } catch (RuntimeException failure) {
            CuisineJournalLog.LOGGER.error("Challenge command reward failed: {}", resolved, failure);
        }
    }
}
