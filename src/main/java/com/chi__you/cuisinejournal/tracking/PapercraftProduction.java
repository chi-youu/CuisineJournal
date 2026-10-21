package com.chi__you.cuisinejournal.tracking;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;
public final class PapercraftProduction {
    private PapercraftProduction(){}
    public static void produce(BlockEntity be,ItemStack result){
        var station=ProductionTracker.station(be);if(station==null||result.isEmpty()||result.hasTag()&&result.getTag().contains(ProductionTracker.PENDING))return;
        Map<Integer,Integer> used=new TreeMap<>();
        for(int slot:station.inputs()){var input=VerifiedWorkstations.stack(be,slot);if(!input.isEmpty())used.put(slot,1);}
        ProductionTracker.generated(be,result,used);
    }
    public static void settled(BlockEntity be,ItemStack result){
        if(be.getLevel()==null||be.getLevel().isClientSide||result.isEmpty()||!result.hasTag()||!result.getTag().contains(ProductionTracker.PENDING))return;
        var batch=ProductionLedger.readBatch(result.getTag().getCompound(ProductionTracker.PENDING));
        if(!batch.method().startsWith("papercraft_magic_decoration:"))return;
        DirectDropProgress.finish(be.getLevel(),result,batch);
    }
}
