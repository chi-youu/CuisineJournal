package com.chi__you.cuisinejournal.config;

import com.chi__you.cuisinejournal.culinary.ActivityEntry;
import com.chi__you.cuisinejournal.culinary.CulinaryProfile;
import java.util.List;
import java.util.UUID;

/** Removed legacy fields must not discard active tuning or invalidate an existing config. */
public final class ConfigCleanupTest {
    private static int checks;
    public static void main(String[] args) {
        ServerConfig server=Toml.read("""
            [mastery]
            target = "RECIPE"
            scope = "DISH"
            dishThresholds = [2, 6, 18, 35, 70]
            [mastery.bonuses]
            extraPortionChanceAtMaxRank = 0.5
            starsEnabled = false
            effects = []
            effectChanceAtMaxRank = 0.9
            toolRefundChanceAtMaxTier = 0.9
            saturationPerRank = 10
            perfectEffectMultiplier = 7
            vanillaNutritionPerStar = 5
            accelerateSmokers = true
            [automation]
            attributionRadius = 12.0
            unattendedWindowTicks = 8000
            reducedCreditMultiplier = 0.4
            [antiFarming]
            perRecipe = false
            trackingPeriodDays = 9
            fullXpPreparations = 7
            """,ServerConfig.class);
        server.validate();
        check(server.mastery.perDish()&&server.mastery.dishThresholds.equals(List.of(2,6,18,35,70)),"active mastery tuning survives old target");
        check(server.antiFarming.fullXpPreparations==7&&server.automation.reducedCreditMultiplier==0.4,"active XP and automation settings survive cleanup");
        String text=Toml.write(server,List.of());
        for(String field:List.of("saturationPerRank","perfectEffectMultiplier","vanillaNutritionPerStar","accelerateSmokers","effects","effectChanceAtMaxRank","effectDurationSeconds","effectAmplifierAtMaxRank","toolRefundChanceAtMaxTier","extraPortionChanceAtMaxRank","starsEnabled","attributionRadius","unattendedWindowTicks","trackingPeriodDays","perRecipe","teamStatisticsEnabled","debugLogging"))
            check(!text.contains(field+" ="),"removed field not written: "+field);
        check(Toml.read(text,ServerConfig.class).mastery.perDish(),"cleaned server TOML remains readable");
        ClientConfig client=Toml.read("""
            [journal]
            scale = 3
            unknownDisplay = "HIDDEN"
            completionistMode = true
            favoriteSort = "mastery"
            defaultPage = "recipes"
            recentActivityLines = 4
            [accessibility]
            highContrast = true
            [hud]
            enabled = true
            """,ClientConfig.class);
        client.validate();
        check(client.journal.defaultPage.equals("collections")&&client.journal.recentActivityLines==4&&client.cookingHud.enabled,"old initial page migrates without losing active preferences");
        check(ClientConfig.normalisePage("methods").equals("statistics")&&ClientConfig.normalisePage("records").equals("statistics"),"removed statistics page ids migrate");
        check(ClientConfig.normalisePage(null).equals("overview")&&ClientConfig.normalisePage("missing").equals("overview"),"invalid initial page safely falls back");
        String clientText=Toml.write(client,List.of());
        for(String field:List.of("cozyMode","showNewIndicator","showStars","showMastery","showPreparedCount","showNutrition"))
            check(!clientText.contains(field+" ="),"retired display preference is not written: "+field);
        check(clientText.contains("[tooltips]")&&client.tooltips.requireModifier,"Shift gating remains enabled by default");
        var always=Toml.read("[tooltips]\nrequireModifier = false\n",ClientConfig.class);
        check(!Toml.read(Toml.write(always,List.of()),ClientConfig.class).tooltips.requireModifier,
                "tooltip expansion preference survives saving and loading");
        boolean oldModifier=ConfigManager.client().tooltips.requireModifier;
        try {
            ConfigManager.client().tooltips.requireModifier=true;
            check(!com.chi__you.cuisinejournal.client.tooltip.MasteryTooltip.showDetails(false)
                    &&com.chi__you.cuisinejournal.client.tooltip.MasteryTooltip.showDetails(true),"Shift reveals gated details");
            ConfigManager.client().tooltips.requireModifier=false;
            check(com.chi__you.cuisinejournal.client.tooltip.MasteryTooltip.showDetails(false),"explicit preference can always show details");
        } finally { ConfigManager.client().tooltips.requireModifier=oldModifier; }
        check(new ServerConfig().discovery.discoverOnObtain&&new ServerConfig().discovery.shared==ConfigEnums.SharedDiscovery.PERSONAL,
                "obtaining defaults on and sharing defaults off");
        check(!text.contains("useRealTimeSchedule =")&&!text.contains("countObtainingAsDiscovery ="),"retired reserved keys disappear");
        check(!clientText.contains("unknownDisplay")&&!clientText.contains("accessibility")&&!clientText.contains("favoriteSort"),"obsolete client sections disappear");
        ClientConfig migrated=Toml.read("""
            [hud]
            enabled = true
            useLargeFoodIcons = false
            offsetX = 27
            visibleTicks = 120
            [cookingHud]
            enabled = false
            [foodHud]
            useLargeFoodIcons = true
            """,ClientConfig.class);
        check(!migrated.cookingHud.enabled&&migrated.foodHud.useLargeFoodIcons,"explicit new settings override old HUD keys");
        check(migrated.cookingHud.offsetX==27&&migrated.cookingHud.visibleTicks==120,"old HUD position and timing migrate");
        ClientConfig legacyHud=Toml.read("[hud]\nenabled = true\nuseLargeFoodIcons = false\n",ClientConfig.class);
        check(legacyHud.cookingHud.enabled&&!legacyHud.foodHud.useLargeFoodIcons,"old HUD is split without losing icon or XP preferences");
        String migratedText=Toml.write(legacyHud,List.of());
        check(migratedText.contains("[foodHud]")&&migratedText.contains("[cookingHud]")&&!migratedText.contains("[hud]"),"writer emits only new HUD groups");
        ClientConfig roundTrip=Toml.read(migratedText,ClientConfig.class);
        check(roundTrip.cookingHud.enabled&&!roundTrip.foodHud.useLargeFoodIcons,"new HUD sections round-trip");
        check(!new ClientConfig().cookingHud.enabled,"cooking XP HUD stays off by default");
        int previous=ConfigManager.server().advanced.activityHistorySize;
        try {
            ConfigManager.server().advanced.activityHistorySize=2;
            CulinaryProfile profile=new CulinaryProfile(new UUID(0,1));
            for(int i=0;i<4;i++)profile.pushActivity(new ActivityEntry(ActivityEntry.Type.LEVEL_UP,null,i,1));
            check(profile.activity().size()==2&&profile.activity().get(0).detail()==3,"configured history cap retains most recent entries");
        }finally{ConfigManager.server().advanced.activityHistorySize=previous;}
        for(var preset:ConfigEnums.Preset.values()){ConfigPresets.applyServer(preset);ConfigPresets.applyClient(preset);}
        check(!com.chi__you.cuisinejournal.Constants.COMMAND.equals(com.chi__you.cuisinejournal.Constants.COMMAND_ALIAS_SHORT),"command alias never redirects root to itself");
        System.out.println("ConfigCleanupTest: "+checks+" checks passed");
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
}
