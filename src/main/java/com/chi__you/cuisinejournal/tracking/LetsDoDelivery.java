package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.progression.CookingRewards;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Credit a manual station's pending output only for the count accepted by the inventory. */
public final class LetsDoDelivery {
    public record Frame(ItemStack marked,ProductionLedger.Batch batch,int count){}
    private LetsDoDelivery(){}
    public static Frame begin(Inventory inventory,ItemStack stack){
        if(!(inventory.player instanceof ServerPlayer player)||!stack.hasTag()||!stack.getTag().contains(ProductionTracker.PENDING,10))return null;
        var batch=ProductionLedger.readBatch(stack.getTag().getCompound(ProductionTracker.PENDING));
        if(!(batch.method().startsWith("farm_and_charm:")||batch.method().equals("barbequesdelight:grilling"))||!batch.belongsTo(player.getUUID()))return null;
        stack.removeTagKey(ProductionTracker.PENDING);
        CookingRewards.stampOutput(player,stack,true,false,batch.count());
        return new Frame(stack.copy(),batch,stack.getCount());
    }
    public static void end(Inventory inventory,ItemStack stack,Frame frame){
        if(frame==null||!(inventory.player instanceof ServerPlayer player))return;
        int accepted=Math.max(0,frame.count-stack.getCount());
        if(accepted>0)ProductionTracker.claimBatch(player,frame.marked.copyWithCount(accepted),frame.batch,CulinaryCredit.Source.INTERACTION);
        if(!stack.isEmpty())ProductionTracker.pending(stack,frame.batch);
    }
}
