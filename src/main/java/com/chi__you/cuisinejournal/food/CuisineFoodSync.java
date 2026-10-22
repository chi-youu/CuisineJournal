package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.network.Network;
import net.minecraft.server.level.ServerPlayer;

public final class CuisineFoodSync {
    private CuisineFoodSync() {
    }

    public static void send(ServerPlayer player) {
        boolean requested = CuisineFoodRuntime.active();
        CuisineFoodState state = requested ? CuisineFoodService.state(player) : new CuisineFoodState();
        boolean active = requested && !state.isReadOnly();
        var foods = state.foods().stream().map(CuisineFoodSync::slot).toList();
        Network.toPlayer(player, new CuisineFoodSnapshot(active,
                CuisineGameplayRules.nativeEffectsEnabled(), CuisineFoodCapacity.ordinary(player),
                CuisineFoodConfig.EAT_AGAIN_PERCENTAGE.get(),
                foods, state.drink() == null ? null : slot(state.drink()), state.temporaryFoods().stream().map(CuisineFoodSync::slot).toList(),
                active?CuisineFoodCapacity.temporary(player):0));
        CuisineFoodEvents.syncCombat(player, true);
    }

    private static CuisineFoodSnapshot.Slot slot(CuisineFoodEntry entry) {
        return new CuisineFoodSnapshot.Slot(entry.stack().copyWithCount(1),
                entry.remainingTicks(), entry.totalTicks(), entry.slotCost());
    }
}
