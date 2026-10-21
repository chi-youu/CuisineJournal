package com.chi__you.cuisinejournal.tracking;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Allow the last serving to settle before discarding the destroyed block's record. */
public final class PlacedDataCleanup {
    private static final List<Runnable> PENDING=new ArrayList<>();
    private PlacedDataCleanup() {}
    public static <T> void defer(Map<Long,T> records,long pos,SavedData data) {
        T old=records.get(pos);if(old==null)return;
        PENDING.add(()->{if(records.get(pos)==old){records.remove(pos);data.setDirty();}});
    }
    public static void changed(ServerLevel level,BlockPos pos) {
        PlacedMealProduction.queueRemoval(level,pos);PlacedFoodVariants.queueRemoval(level,pos);
        CheeseMoldProduction.queueRemoval(level,pos);BrazilPotProduction.queueRemoval(level,pos);
        MilkshakeCauldronProduction.queueRemoval(level,pos);IceBucketPours.queueRemoval(level,pos);
    }
    public static void flush(){var tasks=List.copyOf(PENDING);PENDING.clear();tasks.forEach(Runnable::run);}
    public static void clear(){PENDING.clear();}
}
