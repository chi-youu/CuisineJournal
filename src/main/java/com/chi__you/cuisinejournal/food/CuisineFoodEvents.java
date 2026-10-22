package com.chi__you.cuisinejournal.food;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** Player lifecycle and SOL-style survival effects for the optional runtime. */
public final class CuisineFoodEvents {
    private static final UUID HEALTH_ID = UUID.fromString("80f2b58c-2b47-4a95-b33e-7fe043210dd9");
    private static final Map<ServerPlayer, FoodCombatTiming> LAST_COMBAT = new IdentityHashMap<>();
    private static final Map<ServerPlayer, Boolean> LAST_ACTIVE = new IdentityHashMap<>();
    private static final Map<ServerPlayer,Integer> LAST_CAPACITY=new IdentityHashMap<>();
    private record RegenState(long readyAt, int interval) {}
    private static final Map<ServerPlayer,RegenState> LAST_REGEN_READY=new IdentityHashMap<>();

    private CuisineFoodEvents() {
    }

    public static void tick(ServerPlayer player) {
        if (!CuisineFoodRuntime.active()) {
            LAST_COMBAT.remove(player);
            syncCombat(player, false);
            LAST_CAPACITY.remove(player);
            CuisineFoodEffects.clear(player);
            removeHealthModifier(player);
            com.chi__you.cuisinejournal.skills.VanillaSkillNutrition.reconcile(player);
            if (LAST_ACTIVE.remove(player) != null) {
                CuisineFoodSync.send(player);
            }
            return;
        }
        LAST_ACTIVE.put(player, true);
        if (player.isDeadOrDying()) {
            CuisineFoodEffects.clear(player);
            CuisineFoodState state = CuisineFoodService.state(player);
            if (!state.allFoods().isEmpty() || state.drink() != null) {
                CuisineFoodService.clear(player);
                CuisineFoodSync.send(player);
            }
            removeHealthModifier(player);
            LAST_COMBAT.remove(player);
            syncCombat(player, false);
            return;
        }

        CuisineFoodState before = CuisineFoodService.state(player);
        CuisineFoodEffects.observe(player, before);
        int foodCount = before.foods().size();
        int temporaryCount = before.temporaryFoods().size();
        boolean hadDrink = before.drink() != null;
        CuisineFoodService.tick(player);
        CuisineFoodState state = CuisineFoodService.state(player);
        CuisineFoodEffects.tick(player, state);
        CuisineFoodService.save(player, state);
        int capacityKey=CuisineFoodCapacity.ordinary(player)*65+CuisineFoodCapacity.temporary(player);
        Integer oldCapacity=LAST_CAPACITY.put(player,capacityKey);
        if (oldCapacity==null||oldCapacity!=capacityKey||player.tickCount % (state.temporaryFoods().isEmpty() ? 200 : 20) == 0 || foodCount != state.foods().size()
                || temporaryCount != state.temporaryFoods().size()
                || hadDrink != (state.drink() != null)) {
            CuisineFoodSync.send(player);
        }
        if (state.isReadOnly()) {
            removeHealthModifier(player);
            syncCombat(player, false);
            return;
        }
        synchronizeHealth(player, state);
        player.getFoodData().setSaturation(0.0F);

        long now = player.server.overworld().getGameTime();
        boolean delayElapsed = LAST_COMBAT.computeIfAbsent(player, key -> new FoodCombatTiming()).ready(now,
                CuisineFoodConfig.REGEN_DELAY_TICKS.get(), CuisineEffectAdapters.warmHeart(player));
        syncCombat(player, false);
        if (player.tickCount % CuisineFoodStats.regenIntervalTicks() == 0) {
            var recovery = CuisineEffectAdapters.recovery(player);
            float ordinary = CuisineFoodStats.healPerPulse(state, player);
            var pulse = new com.chi__you.cuisinejournal.api.FoodRegenerationEvent(
                    player, recovery == null ? ordinary : recovery.amount(ordinary, delayElapsed),
                    delayElapsed || recovery != null && recovery.combatFraction() > 0);
            if (!net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(pulse)
                    && pulse.isAllowed() && pulse.getAmount() > 0.0F) {
                player.heal(pulse.getAmount());
            }
            float temporary = CuisineFoodStats.temporaryHealPerPulse(state, player);
            if (temporary > 0) {
                var extra = new com.chi__you.cuisinejournal.api.FoodRegenerationEvent(player, temporary, true, true);
                if (!net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(extra) && extra.isAllowed() && extra.getAmount() > 0)
                    player.heal(extra.getAmount());
            }
        }
    }

    public static void damaged(ServerPlayer player) {
        enteredCombat(player);
    }

    public static void synchronizeHealth(ServerPlayer player, CuisineFoodState state) {
        if (CuisineFoodRuntime.active() && !state.isReadOnly()) {
            updateHealthModifier(player, CuisineFoodStats.maxHealth(state, player));
        }
    }

    public static void fillNewCapacity(ServerPlayer player, float oldMaximum) {
        // This is added capacity, not a healing potion or a periodic regeneration pulse.
        if (!player.isDeadOrDying()) player.setHealth(CuisineFoodMath.healthAfterCapacityIncrease(
                player.getHealth(), oldMaximum, player.getMaxHealth()));
    }

    public static void enteredCombat(ServerPlayer player) {
        if (CuisineFoodRuntime.active()) {
            LAST_COMBAT.computeIfAbsent(player, key -> new FoodCombatTiming())
                    .enter(player.server.overworld().getGameTime());
            syncCombat(player, false);
        }
    }

    public static void saturation(ServerPlayer player) {
        if (CuisineFoodRuntime.active()) {
            LAST_COMBAT.computeIfAbsent(player, key -> new FoodCombatTiming())
                    .saturation(player.server.overworld().getGameTime());
            syncCombat(player, false);
        }
    }

    public static int combatTicksLeft(ServerPlayer player) {
        if (!CuisineFoodRuntime.active() || player.isDeadOrDying() || CuisineFoodService.state(player).isReadOnly()) return 0;
        var timing = LAST_COMBAT.get(player);
        return timing == null ? 0 : timing.remaining(player.server.overworld().getGameTime(),
                CuisineFoodConfig.REGEN_DELAY_TICKS.get(), CuisineEffectAdapters.warmHeart(player));
    }

    public static void syncCombat(ServerPlayer player, boolean force) {
        int remaining = combatTicksLeft(player);
        long readyAt = remaining == 0 ? 0 : player.server.overworld().getGameTime() + remaining;
        int interval = CuisineFoodStats.regenIntervalTicks();
        var state = new RegenState(readyAt, interval);
        var previous = LAST_REGEN_READY.put(player, state);
        if (force || !state.equals(previous))
            com.chi__you.cuisinejournal.network.Network.toPlayer(player, new CuisineRegenPackets.Combat(remaining, interval));
    }

    public static void clonePlayer(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayer original)
                || !(event.getEntity() instanceof ServerPlayer replacement)) {
            return;
        }
        CuisineSolMigration.copyHistory(original.getPersistentData(), replacement.getPersistentData());
        LAST_CAPACITY.remove(original);
        LAST_REGEN_READY.remove(original);
        for(String key:new String[]{"cuisinejournal:ordinary_capacity_bonus","cuisinejournal:temporary_capacity_bonus"})
            replacement.getPersistentData().putInt(key,original.getPersistentData().getInt(key));
        if (!CuisineFoodRuntime.active()) {
            if (event.isWasDeath()) {
                if (original.getPersistentData().contains(CuisineFoodState.ROOT)) {
                    replacement.getPersistentData().put(CuisineFoodState.ROOT, new CuisineFoodState().save());
                }
                replacement.getPersistentData().putBoolean(CuisineSolMigration.IMPORTED, true);
            } else if (original.getPersistentData().contains(CuisineFoodState.ROOT)) {
                replacement.getPersistentData().put(CuisineFoodState.ROOT,
                        original.getPersistentData().get(CuisineFoodState.ROOT).copy());
            }
            CuisineFoodService.forget(original);
            CuisineFoodService.forget(replacement);
            LAST_COMBAT.remove(original);
            LAST_ACTIVE.remove(original);
            return;
        }
        CuisineFoodService.forget(replacement);
        if (!event.isWasDeath()) {
            CuisineFoodEffects.clear(original);
            CuisineFoodService.save(original, CuisineFoodService.state(original));
            if (original.getPersistentData().contains(CuisineFoodState.ROOT)) {
                replacement.getPersistentData().put(CuisineFoodState.ROOT,
                        original.getPersistentData().get(CuisineFoodState.ROOT).copy());
            }
        } else {
            CuisineFoodEffects.clear(original);
            replacement.getPersistentData().put(CuisineFoodState.ROOT, new CuisineFoodState().save());
            replacement.getPersistentData().putBoolean(CuisineSolMigration.IMPORTED, true);
        }
        CuisineFoodService.forget(original);
        LAST_COMBAT.remove(original);
        LAST_ACTIVE.remove(original);
        CuisineFoodSync.send(replacement);
    }

    public static void leave(ServerPlayer player) {
        LAST_REGEN_READY.remove(player);
        LAST_CAPACITY.remove(player);
        CuisineFoodEffects.clear(player);
        if (CuisineFoodRuntime.active()) {
            CuisineFoodService.save(player, CuisineFoodService.state(player));
        }
        CuisineFoodService.forget(player);
        LAST_COMBAT.remove(player);
        LAST_ACTIVE.remove(player);
    }

    public static void stop() {
        LAST_REGEN_READY.clear();
        LAST_CAPACITY.clear();
        CuisineFoodEffects.clearAll();
        LAST_COMBAT.clear();
        LAST_ACTIVE.clear();
    }

    private static void updateHealthModifier(ServerPlayer player, double targetHealth) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        double amount = targetHealth - attribute.getBaseValue();
        AttributeModifier current = attribute.getModifier(HEALTH_ID);
        if (current != null && Math.abs(current.getAmount() - amount) < 0.001D) {
            return;
        }
        if (current != null) {
            attribute.removeModifier(HEALTH_ID);
        }
        if (Math.abs(amount) >= 0.001D) {
            attribute.addTransientModifier(new AttributeModifier(HEALTH_ID,
                    "cuisinejournal_food_health", amount, AttributeModifier.Operation.ADDITION));
        }
        if(player.getHealth()>player.getMaxHealth())player.setHealth(player.getMaxHealth());
    }

    private static void removeHealthModifier(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) {
            attribute.removeModifier(HEALTH_ID);
        }
    }

}
