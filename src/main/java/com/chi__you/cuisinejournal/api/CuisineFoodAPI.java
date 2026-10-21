package com.chi__you.cuisinejournal.api;

import com.chi__you.cuisinejournal.food.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Optional integration entry points. Existing CYS integrations use this public class identity. */
public final class CuisineFoodAPI {
    private CuisineFoodAPI() {}

    public record FoodEffect(ResourceLocation id, int level, float activeFraction) {}

    public static boolean isRuntimeActive() { return CuisineFoodRuntime.active(); }
    public static boolean preservesNativeEffects() { return CuisineGameplayRules.nativeEffectsEnabled(); }

    /** Flavor effects only; native food effects remain owned by their host. */
    public static List<FoodEffect> configuredEffects(ItemStack stack) {
        return CuisineFoodEffectSource.forStack(stack).stream()
                .map(effect -> new FoodEffect(effect.id(), effect.level(), effect.activeFraction())).toList();
    }

    public static boolean isActive(Player player) {
        return player instanceof ServerPlayer server && CuisineFoodRuntime.active()
                && !CuisineFoodService.state(server).isReadOnly();
    }

    public static boolean isFull(Player player) {
        return isActive(player) && CuisineFoodService.state((ServerPlayer) player).usedFoodSlots()
                >= CuisineFoodCapacity.ordinary((ServerPlayer) player);
    }

    /** Maximum ordinary/drink timeline, excluding independent temporary servings. */
    public static int remainingTimelineTicks(Player player) {
        if (!isActive(player)) return 0;
        var state = CuisineFoodService.state((ServerPlayer) player);
        int remaining = state.drink() == null ? 0 : state.drink().remainingTicks();
        for (var food : state.foods()) remaining = Math.max(remaining, food.remainingTicks());
        return remaining;
    }

    public static int ordinaryCapacity(ServerPlayer player) { return CuisineFoodCapacity.ordinary(player); }
    public static int temporaryCapacity(ServerPlayer player) { return CuisineFoodCapacity.temporary(player); }
    public static void setCapacityBonuses(ServerPlayer player, int ordinary, int temporary) {
        CuisineFoodCapacity.setBonuses(player, ordinary, temporary);
    }

    public static int consumeTimelineTicks(Player player, int ticks) {
        if (ticks <= 0 || !isActive(player)) return 0;
        var server = (ServerPlayer) player;
        int consumed = Math.min(ticks, remainingTimelineTicks(player));
        if (consumed == 0) return 0;
        var state = CuisineFoodService.state(server);
        CuisineFoodEffects.observe(server, state);
        state.advance(consumed);
        CuisineFoodService.save(server, state);
        // Reconciliation runs at the normal tick boundary, not recursively inside an effect callback.
        CuisineFoodSync.send(server);
        return consumed;
    }
}
