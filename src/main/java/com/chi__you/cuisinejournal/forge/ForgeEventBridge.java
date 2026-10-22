package com.chi__you.cuisinejournal.forge;

import com.chi__you.cuisinejournal.CuisineJournalServer;
import com.chi__you.cuisinejournal.event.CulinaryHooks;
import com.chi__you.cuisinejournal.food.CuisineFoodOverrides;
import com.chi__you.cuisinejournal.food.CuisineFoodRuntime;
import com.chi__you.cuisinejournal.food.CuisineFoodService;
import com.chi__you.cuisinejournal.food.CuisineFoodEvents;
import com.chi__you.cuisinejournal.food.CuisineFoodSync;
import com.chi__you.cuisinejournal.food.CuisineFoodEffects;
import com.chi__you.cuisinejournal.food.CuisineFoodCatalogueSync;
import com.chi__you.cuisinejournal.food.CuisineFoodConfig;
import com.chi__you.cuisinejournal.CuisineJournal;
import net.minecraftforge.fml.ModList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge lifecycle, player events and tick wiring. Host calls that Forge does
 * not expose are handled separately by the registered Mixins.
 */
@Mod.EventBusSubscriber(modid = com.chi__you.cuisinejournal.Constants.MOD_ID)
public final class ForgeEventBridge {

    private ForgeEventBridge() {
    }

    @SubscribeEvent
    public static void onServerStarting(net.minecraftforge.event.server.ServerAboutToStartEvent event) {
        if (CuisineFoodConfig.cuisineFoodSelected() && ModList.get().isLoaded("sol_valheim")) {
            CuisineJournal.LOGGER.warn("foodSystem = valheim is ignored because sol_valheim is installed; using vanilla food");
        }
        CuisineFoodOverrides.reload();
        CuisineJournalServer.onServerStarting(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        CuisineFoodEvents.stop();
        CuisineFoodService.forgetAll();
        CuisineJournalServer.onServerStopping();
    }

    /**
     * Tags bind at the very end of a datapack reload, which is the earliest
     * point the category system can classify anything. Rebuilding again once
     * the server is up costs nothing and covers any ordering surprise.
     */
    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            rebuildSafely();
        }
    }

    @SubscribeEvent
    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        if(!CuisineJournalServer.catalogueReady())rebuildSafely();
    }
    private static void rebuildSafely(){
        var server = CuisineJournalServer.server();
        if (server == null) return;
        Runnable request = () -> {
            if (CuisineJournalServer.server() != server) return;
            com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.requestRebuild(() -> {
                if (CuisineJournalServer.server() != server) return;
                try { CuisineJournalServer.rebuildCulinaryRegistry(); }
                catch (RuntimeException | LinkageError failure) { com.chi__you.cuisinejournal.CuisineJournalLog.LOGGER.error(
                        "Culinary data rebuild rejected; retaining previous runtime layers", failure); }
            });
        };
        if (server.isSameThread()) request.run(); else server.execute(request);
    }

    /**
     * Farmers buy prepared meals at master level. The offer is built from the
     * dishes this server actually has, so no dish is named in code and an
     * addon's food can turn up here too.
     */
    @SubscribeEvent
    public static void onVillagerTrades(
            net.minecraftforge.event.village.VillagerTradesEvent event) {
        if (!event.getType().equals(net.minecraft.world.entity.npc.VillagerProfession.FARMER)) {
            return;
        }
        event.getTrades().get(5).add(
                com.chi__you.cuisinejournal.compat.VillagerFoodTrades.preparedMealListing());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        com.chi__you.cuisinejournal.command.CuisineJournalCommands.register(
                event.getDispatcher(), event.getBuildContext());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            com.chi__you.cuisinejournal.tracking.ProductionTracker.expireFrames();
            CulinaryHooks.onServerTick(event.getServer());
            com.chi__you.cuisinejournal.tracking.PlacedDataCleanup.flush();
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CulinaryHooks.onBlockUsed(player, event.getPos(),
                    event.getLevel().getBlockState(event.getPos()));
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof ItemEntity item) {
            com.chi__you.cuisinejournal.food.PlacedFoodConsumption.itemEntitySpawned(item);
        }
    }

    @SubscribeEvent
    public static void onFoodBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        var snapshot=event.getBlockSnapshot();
        if(event.getLevel() instanceof net.minecraft.world.level.Level level){
            com.chi__you.cuisinejournal.tracking.CheeseMoldProduction.forget(level,snapshot.getPos());
            com.chi__you.cuisinejournal.tracking.BrazilPotProduction.forget(level,snapshot.getPos());
            com.chi__you.cuisinejournal.tracking.MilkshakeCauldronProduction.forget(level,snapshot.getPos());
            com.chi__you.cuisinejournal.tracking.PlacedMealProduction.remove(level,snapshot.getPos());
        }
        for(var hand:net.minecraft.world.InteractionHand.values()) {
            var stack=player.getItemInHand(hand);
            if(stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem
                    &&blockItem.getBlock()==event.getPlacedBlock().getBlock()) {
                if(event.getLevel() instanceof net.minecraft.world.level.Level level)
                    com.chi__you.cuisinejournal.food.PlacedFoodConsumption.placed(
                            level,snapshot.getPos(),event.getPlacedBlock(),stack);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onFoodBlockBroken(BlockEvent.BreakEvent event){
        if(event.getLevel() instanceof net.minecraft.world.level.Level level){
            com.chi__you.cuisinejournal.tracking.CheeseMoldProduction.forget(level,event.getPos());
            com.chi__you.cuisinejournal.tracking.BrazilPotProduction.forget(level,event.getPos());
            com.chi__you.cuisinejournal.tracking.MilkshakeCauldronProduction.forget(level,event.getPos());
            // Native destruction callbacks/loot still need this stamp; actual removal queues cleanup.
            if(!com.chi__you.cuisinejournal.tracking.PlacedFoodDrops.supports(event.getState()))
                com.chi__you.cuisinejournal.tracking.PlacedMealProduction.remove(level,event.getPos());
        }
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        // Result-slot extraction handles attribution before transfer and commits once.
    }

    @SubscribeEvent
    public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        // Taking someone else's furnace result must not fabricate a cook.
    }

    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if(com.chi__you.cuisinejournal.food.LetsDoFood.roasting(event.getItem()))return;
        if(event.getEntity() instanceof net.minecraft.world.entity.player.Player eater
                && (event.getItem().isEdible() || CuisineFoodService.definition(event.getItem()) != null)) {
            double multiplier=1;
            if(eater instanceof ServerPlayer serverPlayer && com.chi__you.cuisinejournal.culinary.ProfileManager.get()!=null)
                multiplier=com.chi__you.cuisinejournal.skills.CookingSkills.eatMultiplier(com.chi__you.cuisinejournal.culinary.ProfileManager.get().of(serverPlayer));
            else {
                var skill=com.chi__you.cuisinejournal.food.ClientFoodRuleView.current().skills();
                if(skill!=null&&skill.active())multiplier=skill.eat();
            }
            event.setDuration(Math.max(1,(int)Math.round(event.getDuration()*multiplier)));
        }
        if(event.getEntity() instanceof ServerPlayer player)com.chi__you.cuisinejournal.food.CuisineDigestionUse.cancel(player);
        if (CuisineFoodRuntime.active() && event.getEntity() instanceof ServerPlayer player
                && CuisineFoodService.definition(event.getItem()) != null
                && !CuisineFoodService.canConsume(player, event.getItem())) {
            event.setCanceled(true);
        }
        if(!event.isCanceled())event.setDuration(com.chi__you.cuisinejournal.food.CuisineDigestionUse.start(
                event.getEntity(),event.getItem(),event.getDuration()));
    }

    @SubscribeEvent
    public static void onUseItemTick(LivingEntityUseItemEvent.Tick event) {
        if(com.chi__you.cuisinejournal.food.LetsDoFood.roasting(event.getItem()))return;
        if (CuisineFoodRuntime.active() && event.getEntity() instanceof ServerPlayer player
                && CuisineFoodService.definition(event.getItem()) != null
                && !CuisineFoodService.canConsume(player, event.getItem())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseItemStop(LivingEntityUseItemEvent.Stop event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.chi__you.cuisinejournal.food.CuisineDigestionUse.cancel(player);
        }
    }

    @SubscribeEvent
    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        if(com.chi__you.cuisinejournal.food.GrillingFoodCompat.nested())return;
        if (event.getEntity() instanceof ServerPlayer player) {
            var stack = event.getItem().copy();
            if(com.chi__you.cuisinejournal.food.LetsDoFood.roasting(stack))return;
            if(com.chi__you.cuisinejournal.food.KaleidoscopeFood.rejected(player,stack)){
                event.setResultStack(stack);return;
            }
            if(com.chi__you.cuisinejournal.food.PlacedFoodConsumption.finishEvent(player,stack))return;
            if (!CuisineFoodRuntime.active() || CuisineFoodService.definition(stack) == null) {
                CulinaryHooks.onItemEaten(player, stack);
                com.chi__you.cuisinejournal.food.CuisineFlavorEffects.applyVanilla(player, stack);
                return;
            }
            if (CuisineFoodService.consume(player, stack)) {
                CulinaryHooks.onItemEaten(player, stack);
                CuisineFoodEffects.onConsumed(player);
                CuisineFoodSync.send(player);
            } else {
                // Finish fires after the item's own effect but before Forge
                // places the result in the hand. Restoring the original stack
                // prevents a rejected meal from being consumed anyway.
                event.setResultStack(stack);
            }
            com.chi__you.cuisinejournal.food.CuisineDigestionUse.cancel(player);
        }
    }

    @SubscribeEvent
    public static void onFoodPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            com.chi__you.cuisinejournal.food.LetsDoFood.tick(player);
            CuisineFoodEvents.tick(player);
        }
    }

    @SubscribeEvent
    public static void onFoodEffectAdded(net.minecraftforge.event.entity.living.MobEffectEvent.Added event) {
        CuisineFoodEffects.effectAdded(event);
    }

    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onFoodAttack(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CuisineFoodEvents.enteredCombat(player);
        }
    }

    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onFoodDamage(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (event.getAmount() <= 0) return;
        if (event.getEntity() instanceof ServerPlayer player && event.getAmount() > 0) {
            CuisineFoodEvents.damaged(player);
        }
        // DamageSource resolves the player owner of arrows and other player-owned attacks.
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            CuisineFoodEvents.enteredCombat(attacker);
        }
    }

    @SubscribeEvent
    public static void onFoodClone(PlayerEvent.Clone event) {
        if(event.getOriginal() instanceof ServerPlayer old)com.chi__you.cuisinejournal.skills.SkillsIntegration.forget(old);
        CuisineFoodEvents.clonePlayer(event);
    }

    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onSkillShield(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if(event.getEntity() instanceof ServerPlayer player && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))
            event.setAmount(com.chi__you.cuisinejournal.skills.SkillsIntegration.absorb(player,event.getAmount()));
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CulinaryHooks.onPlayerJoin(player);
            CuisineFoodSync.send(player);
            CuisineFoodCatalogueSync.send(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CuisineFoodEvents.leave(player);
            CulinaryHooks.onPlayerQuit(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event){
        if(event.getEntity() instanceof ServerPlayer player)com.chi__you.cuisinejournal.progression.TodayDishService.visit(player);
    }
}
