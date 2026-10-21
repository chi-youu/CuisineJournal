package com.chi__you.cuisinejournal.recipe;

import net.minecraft.resources.ResourceLocation;
import java.util.function.Predicate;

/** Availability spans every star stage, so locked flavors remain discoverable. */
public final class FlavorAvailability {
    private FlavorAvailability() {}
    public static boolean hasContent(FlavorPresets.Preset preset, Predicate<ResourceLocation> effectExists) {
        return preset != null && preset.stages().stream().anyMatch(stage ->
                !stage.modifiers().equals(FlavorModifiers.NONE)
                || !stage.foodModifiers().equals(FlavorModifiers.NONE)
                || !stage.drinkModifiers().equals(FlavorModifiers.NONE)
                || stage.effectLevelBonuses().stream().anyMatch(bonus->effectExists.test(com.chi__you.cuisinejournal.food.CuisineEffectIds.resolve(bonus.id())))
                || stage.effects().stream().anyMatch(effect -> effect.activeFraction() > 0
                    && effectExists.test(com.chi__you.cuisinejournal.food.CuisineEffectIds.resolve(effect.id()))));
    }
}
