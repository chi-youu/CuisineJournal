package com.chi__you.cuisinejournal.food;

import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;

public final class FoodCapacityReconciliation {
    private FoodCapacityReconciliation() {}
    public static void apply(ServerPlayer player) {
        if (!CuisineFoodRuntime.active()) return;
        var state = CuisineFoodService.state(player); if (state.isReadOnly()) return;
        var removed = new ArrayList<CuisineFoodEntry>();
        while (state.usedFoodSlots()>CuisineFoodCapacity.ordinary(player) && !state.foods().isEmpty())
            removed.add(state.foods().remove(state.foods().size()-1));
        int temporary=state.temporaryFoods().stream().mapToInt(CuisineFoodEntry::slotCost).sum();
        while (temporary>CuisineFoodCapacity.temporary(player) && !state.temporaryFoods().isEmpty()) {
            var entry = state.temporaryFoods().remove(state.temporaryFoods().size()-1);
            temporary-=entry.slotCost(); removed.add(entry);
        }
        CuisineFoodEffects.replaceTemporary(player,state,removed);
        CuisineFoodService.save(player,state); CuisineFoodEvents.synchronizeHealth(player,state);
        CuisineFoodSync.send(player);
    }
}
