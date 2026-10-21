package com.chi__you.foodinteractions.bootstrap;

import com.chi__you.foodinteractions.FoodInteractionRecipes;
import net.minecraftforge.fml.common.Mod;

@Mod("food_interaction_recipes")
public final class FoodInteractionMod {
    public FoodInteractionMod() { FoodInteractionRecipes.register(true); }
}
