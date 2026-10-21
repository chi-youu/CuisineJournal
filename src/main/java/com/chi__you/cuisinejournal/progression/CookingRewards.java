package com.chi__you.cuisinejournal.progression;

import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.config.ServerConfig;
import com.chi__you.cuisinejournal.culinary.CulinaryProfile;
import com.chi__you.cuisinejournal.culinary.MealStamp;
import com.chi__you.cuisinejournal.mastery.MasteryRank;
import com.chi__you.cuisinejournal.recipe.CookingMethod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * What a cook gets out of a preparation beyond the numbers: quality stamped
 * onto the food and the occasional perfect result.
 *
 * <p>Perfect probability scales with rank, with a configurable final minimum.</p>
 */
public final class CookingRewards {
    private CookingRewards() {
    }

    /** Legacy public entry point retained for callers; product stamping is handled separately. */
    public static void onPrepared(ServerPlayer player, CulinaryProfile profile,
                                  ResourceLocation meal, int amount, CookingMethod method) {
        // Kept as an API entry point; random tool refunds have been retired.
    }

    static int starsFor(CulinaryProfile profile, ResourceLocation meal,
                                ServerConfig config) {
        if (!config.mastery.enabled) return 2;
        var registry = com.chi__you.cuisinejournal.recipe.CulinaryRegistries.current();
        var entry = registry.progressEntry(meal);
        if(config.mastery.perDish()&&meal.getNamespace().equals("ltc2")
                &&com.chi__you.cuisinejournal.recipe.LatiaoMastery.family(meal).size()>1)
            return Math.min(config.mastery.toCurve().rankFor(profile.masteryPointsOf(meal)).ordinal(),
                    com.chi__you.cuisinejournal.skills.CookingSkills.starCap(profile));
        int stars = config.mastery.perDish() || entry == null
                ? profile.rankOf(meal, config.mastery.toCurve()).ordinal()
                : profile.categoryRank(entry.masteryGroup(), config.mastery.toCurve()).ordinal();
        return Math.min(stars,com.chi__you.cuisinejournal.skills.CookingSkills.starCap(profile));
    }

    /** Marks only the result stack supplied by a crafting or furnace output. */
    public static void stampOutput(ServerPlayer player, ItemStack stack) {
        stampOutput(player, stack, false, true);
    }

    public static void stampOutput(ServerPlayer player, ItemStack stack, boolean announce) {
        stampOutput(player, stack, false, announce);
    }

    public static void stampOutput(ServerPlayer player, ItemStack stack,
                                   boolean allowPerfect, boolean announce) {
        stampOutput(player,stack,allowPerfect,announce,stack.getCount());
    }

    public static void stampOutput(ServerPlayer player,ItemStack stack,boolean allowPerfect,boolean announce,int batchCount){
        if (stack.isEmpty() || MealStamp.isStamped(stack)) {
            return;
        }
        var registry = com.chi__you.cuisinejournal.recipe.CulinaryRegistries.current();
        ItemStack identityStack=com.chi__you.cuisinejournal.food.PlacedFoodConsumption.identityForProduction(stack);
        ResourceLocation meal = registry.productionIdentity(BuiltInRegistries.ITEM.getKey(identityStack.getItem()));
        if (meal == null || !registry.isTracked(meal)) {
            return;
        }
        var profiles = com.chi__you.cuisinejournal.culinary.ProfileManager.get();
        if (profiles == null) {
            return;
        }
        CulinaryProfile profile = profiles.of(player);
        ServerConfig config = ConfigManager.server();
        int earned = config.mastery.enabled ? starsFor(profile, meal, config) : 2;
        if(com.chi__you.cuisinejournal.food.LetsDoFood.beer(stack))earned=com.chi__you.cuisinejournal.food.LetsDoFood.stars(stack);
        boolean today=config.mastery.enabled&&TodayDishService.isToday(player,meal);
        int stars=ProductionQuality.stars(earned,today);
        // Discovery limits determine earned stars, not the identity of an already cooked item.
        int cap = MasteryRank.highestIndex();
        boolean perfect = allowPerfect && !com.chi__you.cuisinejournal.food.LetsDoFood.wine(stack) && !com.chi__you.cuisinejournal.food.KaleidoscopeFood.noPerfect(stack)
                && config.mastery.enabled && config.mastery.bonuses.enabled
                && player.getRandom().nextDouble()
                < ProductionQuality.chance(config.mastery.bonuses.perfectChanceAtMaxRank * earned / MasteryRank.highestIndex()
                        +com.chi__you.cuisinejournal.skills.CookingSkills.value(profile,"perfect")+SignatureDishService.bonus(profile,meal),today,batchCount,
                        com.chi__you.cuisinejournal.skills.CookingSkills.minimum(profile,config.mastery.bonuses.minimumPerfectChance),
                        com.chi__you.cuisinejournal.skills.CookingSkills.penalty(profile));
        MealStamp stamp = new MealStamp(stars, perfect, cap,
                player.getUUID(), player.getGameProfile().getName());
        stamp.write(stack);
        com.chi__you.cuisinejournal.food.KaleidoscopeFood.stamp(stack,stamp,today);
        com.chi__you.cuisinejournal.food.LetsDoFood.stamp(stack,stamp,today);
        if (perfect && announce) {
            player.displayClientMessage(Component.translatable("cuisinejournal.notify.perfect",
                    stack.getHoverName()), true);
        }
    }

    public static void announcePerfect(ServerPlayer player, ItemStack stack) {
        MealStamp stamp = MealStamp.read(stack);
        if (stamp != null && stamp.perfect()) {
            player.displayClientMessage(Component.translatable("cuisinejournal.notify.perfect",
                    stack.getHoverName()), true);
        }
    }

    /** A single known contributor's direct production fixes quality even while that player is offline. */
    public static void stampProduced(net.minecraft.server.MinecraftServer server,ItemStack stack,java.util.UUID cook,int count){
        if(stack.isEmpty()||MealStamp.isStamped(stack))return;
        var profiles=com.chi__you.cuisinejournal.culinary.ProfileManager.get();if(profiles==null)return;
        var registry=com.chi__you.cuisinejournal.recipe.CulinaryRegistries.current();
        var meal=registry.productionIdentity(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        if(meal==null||!registry.isTracked(meal))return;
        var profile=profiles.peekOffline(cook);var config=ConfigManager.server();
        int earned=config.mastery.enabled?starsFor(profile,meal,config):2;
        if(com.chi__you.cuisinejournal.food.LetsDoFood.beer(stack))earned=com.chi__you.cuisinejournal.food.LetsDoFood.stars(stack);
        var selected=TodayDishService.current(server);
        boolean today=config.mastery.enabled&&selected!=null&&registry.preparationTarget(selected).equals(registry.preparationTarget(meal));
        boolean perfect=!com.chi__you.cuisinejournal.food.LetsDoFood.wine(stack)&&!com.chi__you.cuisinejournal.food.KaleidoscopeFood.noPerfect(stack)&&config.mastery.enabled&&config.mastery.bonuses.enabled
                &&server.overworld().random.nextDouble()<ProductionQuality.chance(
                        config.mastery.bonuses.perfectChanceAtMaxRank*earned/5+com.chi__you.cuisinejournal.skills.CookingSkills.value(profile,"perfect")
                                +SignatureDishService.bonus(profile,meal),today,count,
                        com.chi__you.cuisinejournal.skills.CookingSkills.minimum(profile,config.mastery.bonuses.minimumPerfectChance),
                        com.chi__you.cuisinejournal.skills.CookingSkills.penalty(profile));
        var name=server.getProfileCache()==null?"":server.getProfileCache().get(cook).map(com.mojang.authlib.GameProfile::getName).orElse("");
        var stamp=new MealStamp(ProductionQuality.stars(earned,today),perfect,5,cook,name);stamp.write(stack);
        com.chi__you.cuisinejournal.food.KaleidoscopeFood.stamp(stack,stamp,today);
        com.chi__you.cuisinejournal.food.LetsDoFood.stamp(stack,stamp,today);
    }

}
