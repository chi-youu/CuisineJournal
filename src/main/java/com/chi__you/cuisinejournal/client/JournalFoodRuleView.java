package com.chi__you.cuisinejournal.client;

import com.chi__you.cuisinejournal.config.ServerConfig;
import com.chi__you.cuisinejournal.food.ClientFoodRuleView;
import net.minecraft.resources.ResourceLocation;

/** Reads the current packet on each query; reconnects never retain a previous world's rules. */
public final class JournalFoodRuleView implements ClientFoodRuleView {
    public com.chi__you.cuisinejournal.skills.SkillPackets.Sync skills() { return ClientJournalState.skills(); }
    public boolean flavors() { var value=ClientJournalState.rules();return value==null||value.flavorsEnabled(); }
    public boolean nutrition() { var value=ClientJournalState.rules();return value==null||value.nutritionDisplayEnabled(); }
    public boolean stars() { var value=ClientJournalState.rules();return value==null||value.starsEnabled(); }
    public boolean nativeEffects() { var value=ClientJournalState.rules();return value==null||value.foodEffectsEnabled(); }
    public boolean craftable(ResourceLocation item) {
        var dish = ClientJournalState.dish(item);
        if (dish == null) return false;
        if (!dish.methods().isEmpty()) return true;
        var parent = ClientJournalState.dish(dish.preparationTarget());
        return parent != null && !parent.methods().isEmpty() || !dish.preparationTarget().equals(dish.target());
    }
    public ServerConfig.Bonuses vanillaBonuses() {
        var packet = ClientJournalState.rules();
        if (packet == null || !packet.vanillaFood() || !packet.bonusesEnabled()) return null;
        var result = new ServerConfig.Bonuses();
        result.vanillaSaturationMultiplierPerStar = packet.vanillaSaturationMultiplierPerStar();
        result.vanillaMaxStarSaturation = packet.vanillaMaxStarSaturation();
        result.vanillaPerfectNutritionMultiplier = packet.vanillaPerfectNutritionMultiplier();
        return result;
    }
}
