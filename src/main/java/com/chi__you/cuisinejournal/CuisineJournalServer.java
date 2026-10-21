package com.chi__you.cuisinejournal;

import com.chi__you.cuisinejournal.challenge.ChallengeManager;
import com.chi__you.cuisinejournal.challenge.ChallengeRegistry;
import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.culinary.ProfileManager;
import com.chi__you.cuisinejournal.data.DatapackLoader;
import com.chi__you.cuisinejournal.milestone.MilestoneTracker;
import com.chi__you.cuisinejournal.platform.Services;
import com.chi__you.cuisinejournal.recipe.CulinaryRegistries;
import com.chi__you.cuisinejournal.recipe.FoodCategory;
import com.chi__you.cuisinejournal.recipe.GlobalCategoryStore;
import com.chi__you.cuisinejournal.recipe.IngredientCatalogueConfig;
import com.chi__you.cuisinejournal.recipe.IngredientCompatRules;
import com.chi__you.cuisinejournal.tracking.CookingContextTracker;
import com.chi__you.cuisinejournal.tracking.WorkstationRegistry;
import net.minecraft.server.MinecraftServer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Server lifecycle and transactional catalogue rebuilds, wired by Forge events.
 *
 * <p>The culinary registry is rebuilt from the <em>tags loaded</em> signal
 * rather than from a resource-reload listener. Recipes are available earlier,
 * but item tags are only bound once the whole reload finishes, and categories
 * are derived entirely from tags — building sooner would silently classify
 * every dish as uncategorised.</p>
 */
public final class CuisineJournalServer {
    private static volatile MinecraftServer server;
    private static boolean catalogueReady;
    public static boolean catalogueReady(){return catalogueReady;}

    private CuisineJournalServer() {
    }

    public static MinecraftServer server() {
        return server;
    }

    public static void onServerStarting(MinecraftServer startingServer) {
        server = startingServer;
        catalogueReady=false;
        ConfigManager.initialize(Services.PLATFORM.configDirectory());
        applyFoodModeDefaults();
        GlobalCategoryStore.load(Services.PLATFORM.configDirectory());
        IngredientCatalogueConfig.load(Services.PLATFORM.configDirectory());
        WorkstationRegistry.applyConfiguration(
                ConfigManager.server().compatibility.extraWorkstationBlocks);
        ProfileManager.start(startingServer);
    }

    public static void onServerStopping() {
        com.chi__you.cuisinejournal.tracking.PlacedDataCleanup.flush();
        com.chi__you.cuisinejournal.challenge.RewardDelivery.clear();
        com.chi__you.cuisinejournal.network.ServerSettingsHandler.stop();
        com.chi__you.cuisinejournal.network.FlavorEditHandler.stop();
        com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.stop();
        GlobalCategoryStore.clearEditing();
        com.chi__you.cuisinejournal.tracking.ProductionTracker.clear();
        com.chi__you.cuisinejournal.skills.SkillsIntegration.clear();
        com.chi__you.cuisinejournal.network.ServerPacketHandler.clearDiscoveryUpdates();
        ProfileManager.stop();
        CulinaryRegistries.clear();
        com.chi__you.cuisinejournal.food.CuisineFlavorEffects.clearTrace();
        IngredientCompatRules.clear();
        com.chi__you.cuisinejournal.recipe.FlavorPresets.clear();
        com.chi__you.cuisinejournal.recipe.SchemeStore.clear();
        com.chi__you.cuisinejournal.tracking.WorkstationDefinitions.clear();
        com.chi__you.cuisinejournal.tracking.RecipeWorkstationAudit.clear();
        ChallengeRegistry.clear();
        com.chi__you.cuisinejournal.culinary.CulinaryBadge.clear();
        com.chi__you.cuisinejournal.challenge.DailyRotation.reset();
        CookingContextTracker.reset();
        server = null;
        catalogueReady=false;
    }

    /**
     * Rebuilds the culinary registry and everything derived from it. Safe to
     * call repeatedly; a datapack reload mid-session goes through the same path
     * as the initial load.
     */
    public static void rebuildCulinaryRegistry() {
        MinecraftServer current = server;
        if (current == null) {
            return;
        }
        var localEdits=com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.checkpoint(Services.PLATFORM.configDirectory());
        DatapackLoader.Result data;
        try {
        applyFoodModeDefaults();
        data = DatapackLoader.load(current.getResourceManager());
        com.chi__you.cuisinejournal.food.CuisineFlavorEffects.clearTrace();
        IngredientCompatRules.load(current.getResourceManager());
        com.chi__you.cuisinejournal.recipe.FlavorPresets.reload(current.getResourceManager(),Services.PLATFORM.configDirectory());
        com.chi__you.cuisinejournal.recipe.SchemeStore.reload(current.getResourceManager(),Services.PLATFORM.configDirectory());
        // First start: a configured default scheme fills a kind that has no in-game edits yet.
        var schemes = ConfigManager.server().schemes;
        com.chi__you.cuisinejournal.recipe.SchemeStore.applyDefault(
                com.chi__you.cuisinejournal.recipe.SchemeStore.Kind.FLAVOR, schemes.defaultFlavorScheme);
        com.chi__you.cuisinejournal.recipe.SchemeStore.applyDefault(
                com.chi__you.cuisinejournal.recipe.SchemeStore.Kind.CATEGORY, schemes.defaultCategoryScheme);
        com.chi__you.cuisinejournal.recipe.DishServingVariants.reload(current.getResourceManager());
        com.chi__you.cuisinejournal.tracking.WorkstationDefinitions.load(current.getResourceManager());
        com.chi__you.cuisinejournal.food.CuisineFoodDefinitions.reload(current.getResourceManager());
        List<FoodCategory> orderedCategories = new ArrayList<>(data.categories());
        orderedCategories.sort(Comparator.comparingInt(FoodCategory::priority)
                .thenComparing(category -> category.id().toString()));
        GlobalCategoryStore.setPackOverlays(
                com.chi__you.cuisinejournal.network.FlavorEditHandler.categoryOverlays(current.getResourceManager()));
        orderedCategories = GlobalCategoryStore.apply(orderedCategories);
        CulinaryRegistries.rebuild(
                current.getRecipeManager().getRecipes(),
                current.registryAccess(),
                ConfigManager.toEligibilityRules(ConfigManager.server()),
                orderedCategories);
        } catch (RuntimeException | LinkageError failure) {
            try {localEdits.restore();} catch(RuntimeException rollbackFailure){failure.addSuppressed(rollbackFailure);}
            com.chi__you.cuisinejournal.food.CuisineFlavorEffects.clearTrace();
            throw new IllegalStateException("Cuisine Journal reload rejected; previous runtime layers restored",failure);
        }
        catalogueReady=true;
        try {net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new com.chi__you.cuisinejournal.api.FoodDefinitionsReloadedEvent(current));}
        catch(RuntimeException | LinkageError failure){CuisineJournalLog.LOGGER.error(
                "A food reload observer failed after registry commit",failure);}

        try {
            com.chi__you.cuisinejournal.tracking.RecipeWorkstationAudit.rebuild(
                    current.getRecipeManager().getRecipes(),current.registryAccess(),
                    CulinaryRegistries.current(),Services.PLATFORM.configDirectory());
            com.chi__you.cuisinejournal.tracking.RecipeWorkstationAudit.writeCatalogueOnly(
                    Services.PLATFORM.configDirectory().resolve("cuisinejournal-catalogue-only-audit.tsv"),CulinaryRegistries.current());
        } catch(RuntimeException | LinkageError | java.io.IOException failure) {
            // Diagnostic collection must never prevent production, catalogue sync or world loading.
            CuisineJournalLog.LOGGER.warn("Could not finish recipe-workstation audit",failure);
        }

        if (!data.problems().isEmpty()) {
            CuisineJournalLog.LOGGER.warn("{} Cuisine Journal data file(s) were skipped; see above",
                    data.problems().size());
        }
        ChallengeRegistry.replace(data.challenges());
        for(var player:current.getPlayerList().getPlayers())com.chi__you.cuisinejournal.progression.TodayDishService.visit(player);
        com.chi__you.cuisinejournal.progression.TodayDishService.refresh(current,true);
        com.chi__you.cuisinejournal.challenge.DailyRotation.invalidate();
        com.chi__you.cuisinejournal.challenge.DailyRotation.tick(current);
        MilestoneTracker.replace(data.milestones());
        com.chi__you.cuisinejournal.culinary.CulinaryBadge.replace(data.badges());

        ChallengeManager.install();
        MilestoneTracker.install();
        com.chi__you.cuisinejournal.progression.CookingBonuses.install();

        // A reload can add or remove dishes, so every connected client needs the
        // new catalogue rather than the one it cached at join.
        for (net.minecraft.server.level.ServerPlayer player
                : current.getPlayerList().getPlayers()) {
            var profile=ProfileManager.get().of(player);
            com.chi__you.cuisinejournal.food.FoodCapacityReconciliation.apply(player);
            MilestoneTracker.evaluate(player,profile);
            com.chi__you.cuisinejournal.network.ServerPacketHandler.sendEverything(player);
            com.chi__you.cuisinejournal.food.CuisineFoodSync.send(player);
            com.chi__you.cuisinejournal.food.CuisineFoodCatalogueSync.send(player);
        }
    }

    /** Re-reads configuration and rebuilds everything derived from it. */
    public static void reload() {
        var lease=com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.begin(
                com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.Kind.EDIT);
        if(lease==null)throw new IllegalStateException("Catalogue edit/reload already running");
        try { reloadOwned(); } finally { lease.close(); }
    }

    private static void reloadOwned() {
        var previous=com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.checkpoint(Services.PLATFORM.configDirectory());
        try {
        com.chi__you.cuisinejournal.food.CuisineFoodOverrides.reload();
        ConfigManager.reloadServer();
        IngredientCatalogueConfig.load(Services.PLATFORM.configDirectory());
        WorkstationRegistry.applyConfiguration(
                ConfigManager.server().compatibility.extraWorkstationBlocks);
        rebuildCulinaryRegistry();
        } catch(RuntimeException failure) {
            try {previous.restore();}catch(RuntimeException rollbackFailure){failure.addSuppressed(rollbackFailure);}
            WorkstationRegistry.applyConfiguration(ConfigManager.server().compatibility.extraWorkstationBlocks);
            throw failure;
        }
    }

    private static void applyFoodModeDefaults() {
        var config = ConfigManager.server();
        if (com.chi__you.cuisinejournal.config.FoodModeDefaults.apply(config,
                com.chi__you.cuisinejournal.food.CuisineFoodRuntime.active() ? "valheim" : "vanilla"))
            ConfigManager.saveServer(config);
    }

    /** Settings changes use one entry point so food mode and defaults are rebuilt together. */
    public static void setFoodSystem(String mode) {
        if (!mode.equals("valheim") && !mode.equals("vanilla")) throw new IllegalArgumentException("Unknown food mode");
        var previous=com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.checkpoint(Services.PLATFORM.configDirectory());
        String oldMode=com.chi__you.cuisinejournal.food.CuisineFoodConfig.FOOD_SYSTEM.get();
        try {
        com.chi__you.cuisinejournal.food.CuisineFoodConfig.FOOD_SYSTEM.set(mode);
        com.chi__you.cuisinejournal.food.CuisineFoodConfig.FOOD_SYSTEM.save();
        rebuildCulinaryRegistry();
        }catch(RuntimeException failure){
            com.chi__you.cuisinejournal.food.CuisineFoodConfig.FOOD_SYSTEM.set(oldMode);
            try {com.chi__you.cuisinejournal.food.CuisineFoodConfig.FOOD_SYSTEM.save();previous.restore();}
            catch(RuntimeException rollbackFailure){failure.addSuppressed(rollbackFailure);}
            throw failure;
        }
    }
}
