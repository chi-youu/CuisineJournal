package com.chi__you.cuisinejournal.food;

import net.minecraft.server.level.ServerPlayer;

/** Persistent capacity bonuses are integration points for the future growth system. */
public final class CuisineFoodCapacity {
    public static final int MAXIMUM=64;
    public static final int TEMPORARY_TICKS=200;
    private CuisineFoodCapacity() {}
    public static int ordinary(ServerPlayer player) {
        return total(CuisineFoodConfig.maxFoodSlots(),player.getPersistentData().getInt("cuisinejournal:ordinary_capacity_bonus")
                +(int)com.chi__you.cuisinejournal.skills.CookingSkills.value(player,"ordinary"),0);
    }
    public static int temporary(ServerPlayer player) {
        var policy=CuisineEffectAdapters.temporaryFood(player);
        return total(CuisineFoodConfig.BASE_TEMPORARY_SLOTS.get(),
                player.getPersistentData().getInt("cuisinejournal:temporary_capacity_bonus")
                        +(int)com.chi__you.cuisinejournal.skills.CookingSkills.value(player,"temporary"),policy==null?0:policy.capacity());
    }
    public static int total(int base,int growth,int effect) {
        return (int)Math.max(0L,Math.min(MAXIMUM,(long)base+Math.max(0,growth)+Math.max(0,effect)));
    }
    public static void setBonuses(ServerPlayer player,int ordinary,int temporary) {
        player.getPersistentData().putInt("cuisinejournal:ordinary_capacity_bonus",Math.max(0,Math.min(MAXIMUM,ordinary)));
        player.getPersistentData().putInt("cuisinejournal:temporary_capacity_bonus",Math.max(0,Math.min(MAXIMUM,temporary)));
        FoodCapacityReconciliation.apply(player);
        CuisineFoodSync.send(player);
    }
}
