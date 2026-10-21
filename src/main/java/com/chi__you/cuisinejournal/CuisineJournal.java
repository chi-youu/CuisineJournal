package com.chi__you.cuisinejournal;

import com.chi__you.cuisinejournal.forge.ForgeNetwork;
import org.slf4j.Logger;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import com.chi__you.cuisinejournal.food.CuisineFoodConfig;

@Mod(CuisineJournal.MOD_ID)
public final class CuisineJournal {
    public static final String MOD_ID = Constants.MOD_ID;
    public static final Logger LOGGER = CuisineJournalLog.LOGGER;

    public CuisineJournal() {
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(
                com.chi__you.cuisinejournal.compat.UnificationRulePack::register);
        com.chi__you.cuisinejournal.food.CuisineEffects.register(
                net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        com.chi__you.foodinteractions.FoodInteractionRecipes.register(false);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CuisineFoodConfig.SPEC);
        CuisineJournalBootstrap.initialize();
        com.chi__you.cuisinejournal.skills.SkillsIntegration.install();
        ForgeNetwork.register();
        if (net.minecraftforge.fml.ModList.get().isLoaded("jeed")) {
            com.chi__you.cuisinejournal.compat.jeed.CuisineJeedCompat.register();
        }
    }
}
