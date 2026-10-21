package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.compat.HostReflection;

import com.chi__you.cuisinejournal.food.IngredientProvenance;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.nbt.*;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;

/** Persistent manual-input attribution for the two audited grills. No tick acceleration. */
public final class ManualGrillProduction {
    private static final String KEY="cuisinejournal:manual_grill_owner";
    private ManualGrillProduction() {}
    public static void inserted(ItemStack stack,net.minecraft.world.entity.player.Player player){
        if(player==null||stack.isEmpty())return;
        stack.getOrCreateTag().putUUID(KEY,player.getUUID());
    }
    public static void output(BlockEntity be,ItemStack input,ItemStack result,String method){
        if(be.getLevel()==null||be.getLevel().isClientSide||result.isEmpty())return;
        IngredientProvenance.sources(result,List.of(input));
        Set<UUID> owners=input.hasTag()&&input.getTag().hasUUID(KEY)?Set.of(input.getTag().getUUID(KEY)):Set.of();
        result.removeTagKey(KEY);
        result.removeTagKey(com.chi__you.cuisinejournal.culinary.MealStamp.ROOT);
        var batch=new ProductionLedger.Batch(BuiltInRegistries.ITEM.getKey(result.getItem()).toString(),result.getCount(),owners,
                Set.of(BuiltInRegistries.ITEM.getKey(input.getItem()).toString()),method);
        ProductionTracker.pending(result,batch);
    }
    public static void bbqInserted(BlockEntity be,int slot){
        Object raw=HostReflection.invoke(be,"getStack",slot);
        if(raw instanceof ItemStack stack)inserted(stack,ProductionTracker.actor());
    }
    public static void handOutput(net.minecraft.world.entity.player.Player player,ItemStack result){
        if(!(player instanceof net.minecraft.server.level.ServerPlayer server)||!result.hasTag()
                ||!result.getTag().contains(ProductionTracker.PENDING,10))return;
        var batch=ProductionLedger.readBatch(result.getTag().getCompound(ProductionTracker.PENDING));
        if(!batch.method().equals("barbequesdelight:grilling"))return;
        result.removeTagKey(ProductionTracker.PENDING);
        if(batch.belongsTo(server.getUUID()))
            com.chi__you.cuisinejournal.progression.CookingRewards.stampOutput(server,result,true,false,batch.count());
        ProductionTracker.claimBatch(server,result,batch,CulinaryCredit.Source.INTERACTION);
    }
}
