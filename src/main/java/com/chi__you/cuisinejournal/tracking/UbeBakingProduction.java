package com.chi__you.cuisinejournal.tracking;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;

/** Real input ownership persists while the baking mat replaces its contents with stage models. */
public final class UbeBakingProduction {
    private static final String KEY="cuisinejournal:ube_baking";
    private record Frame(BlockEntity be,Map<Integer,ItemStack> inputs,ProductionLedger.Batch batch,boolean[] emitted){}
    private static final ProductionFrames<Deque<Frame>> FRAMES=ProductionFrames.stack();
    private UbeBakingProduction(){}
    private static boolean allowed(BlockEntity be,WorkstationDefinitions.Capability capability){
        var definition=WorkstationDefinitions.get(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()));
        return definition==null||definition.enabled()&&definition.capabilities().contains(capability);
    }
    private static String identity(ItemStack stack){return BuiltInRegistries.ITEM.getKey(stack.getItem())+"|"+Objects.toString(stack.getTag(),"");}
    public static void begin(BlockEntity be){
        if(be.getLevel()==null||be.getLevel().isClientSide)return;
        Map<Integer,ItemStack> inputs=new TreeMap<>();
        for(int i=0;i<9;i++)inputs.put(i,VerifiedWorkstations.stack(be,i).copy());
        CompoundTag data=be.getPersistentData().getCompound(KEY);
        ProductionLedger.Batch batch=data.contains("batch")?ProductionLedger.readBatch(data.getCompound("batch")):null;
        FRAMES.get().push(new Frame(be,inputs,batch,new boolean[1]));
    }
    public static void finish(BlockEntity be,boolean success){
        if(be.getLevel()==null||be.getLevel().isClientSide)return;
        var frames=FRAMES.get();var frame=ProductionFrames.take(frames,value->value.be==be);if(frames.isEmpty())FRAMES.remove();if(frame==null)return;
        if(!success)return;
        boolean processing=Boolean.TRUE.equals(be.getBlockState().getValue(
                (net.minecraft.world.level.block.state.properties.BooleanProperty)be.getBlockState().getBlock().getStateDefinition().getProperty("processing")));
        if(frame.emitted[0]||!processing){be.getPersistentData().remove(KEY);be.setChanged();return;}
        if(frame.batch!=null)return;
        var ledger=ProductionLedger.load(be.getPersistentData().getCompound(ProductionTracker.LEDGER));
        Map<Integer,Integer> used=new TreeMap<>();frame.inputs.forEach((slot,stack)->{if(!stack.isEmpty())used.put(slot,stack.getCount());});
        var batch=ledger.produce(used,"",1,"ubesdelight:baking_mat");
        CompoundTag data=new CompoundTag();data.put("batch",ProductionLedger.writeBatch(batch));be.getPersistentData().put(KEY,data);be.setChanged();
    }
    public static void results(BlockEntity be,List<ItemStack> outputs){
        if(be.getLevel()==null||be.getLevel().isClientSide||FRAMES.get().isEmpty())return;
        var frame=FRAMES.get().peek();if(frame.be!=be||!allowed(be,WorkstationDefinitions.Capability.ITEM_OUTPUT))return;
        var batch=frame.batch;
        if(batch==null){
            var ledger=ProductionLedger.load(be.getPersistentData().getCompound(ProductionTracker.LEDGER));
            Map<Integer,Integer> used=new TreeMap<>();frame.inputs.forEach((slot,stack)->{if(!stack.isEmpty())used.put(slot,stack.getCount());});
            batch=ledger.produce(used,"",1,"ubesdelight:baking_mat");
        }
        for(ItemStack output:outputs){
            if(output.isEmpty()||!allowed(be,WorkstationDefinitions.Capability.DIRECT_DROP))continue;
            var registry=com.chi__you.cuisinejournal.recipe.CulinaryRegistries.current();
            var target=BuiltInRegistries.ITEM.getKey(com.chi__you.cuisinejournal.food.PlacedFoodConsumption.identityForProduction(output).getItem());
            if(!registry.isTracked(registry.productionIdentity(target)))continue;
            ProductionTracker.pending(output,new ProductionLedger.Batch(identity(output),output.getCount(),batch.contributors(),batch.ingredients(),batch.method()));
        }
        frame.emitted[0]=true;
    }
    public static void clearOnManualRemoval(BlockEntity be){if(be.getLevel()!=null&&!be.getLevel().isClientSide&&FRAMES.get().isEmpty()){be.getPersistentData().remove(KEY);be.setChanged();}}
}
