package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.compat.HostReflection;

import com.chi__you.cuisinejournal.food.IngredientProvenance;

import net.minecraft.nbt.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import java.util.*;

/** Real manual tub additions and drains retain their sources; ready drinks poured back are storage. */
public final class CosmopolitanTubProduction {
    private static final String KEY="cuisinejournal:cosmopolitan_tub";
    private static final ProductionFrames<Deque<Frame>> FRAMES=ProductionFrames.stack();
    private record Frame(BlockEntity owner,FluidStack before,ItemStack held,ItemStack copy,Player player){}
    private CosmopolitanTubProduction(){}
    public static void clear(){FRAMES.remove();}
    private static IFluidHandler tank(BlockEntity owner){var raw=HostReflection.invoke(owner,"getTank");return raw instanceof IFluidHandler value?value:null;}
    private static String id(FluidStack stack){return stack.isEmpty()?"":BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString();}
    public static void begin(BlockEntity be,ItemStack held,Player player){
        if(be.getLevel()==null||be.getLevel().isClientSide)return;var tank=tank(be);if(tank==null)return;
        FRAMES.get().push(new Frame(be,tank.getFluidInTank(0).copy(),held,held.copy(),player));
    }
    public static void added(BlockEntity owner){
        var frames=FRAMES.get();var frame=FrameStacks.finish(frames,value->value.owner==owner);if(frame==null)return;if(frames.isEmpty())FRAMES.remove();
        var handler=tank(owner);if(handler==null)return;var after=handler.getFluidInTank(0);
        int gained=after.getAmount()-(frame.before.isFluidEqual(after)?frame.before.getAmount():0);
        if(gained<=0||frame.copy.isEmpty())return;
        var saved=owner.getPersistentData().getCompound(KEY).copy();
        if(!saved.getString("fluid").equals(id(frame.before)))saved=new CompoundTag();
        Set<UUID> owners=owners(saved);if(frame.player instanceof ServerPlayer)owners.add(frame.player.getUUID());
        List<ItemStack> inputs=inputs(saved);inputs.add(frame.copy.copyWithCount(1));
        ItemStack original=ItemStack.of(saved.getCompound("original"));
        if(frame.before.isEmpty())original=frame.copy.copyWithCount(1);
        else if(!ItemStack.isSameItemSameTags(original,frame.copy))original=ItemStack.EMPTY;
        write(owner,after,saved.getBoolean("production"),owners,inputs,original);
    }
    public static void transformed(BlockEntity owner){
        transformed(owner,List.of());
    }
    public static void transformed(BlockEntity owner,List<ItemStack> extra){
        var frames=FRAMES.get();var frame=FrameStacks.finish(frames,value->value.owner==owner);if(frame==null)return;if(frames.isEmpty())FRAMES.remove();
        var handler=tank(owner);if(handler==null)return;var after=handler.getFluidInTank(0);
        if(after.isEmpty()||frame.before.isFluidEqual(after))return;
        var saved=owner.getPersistentData().getCompound(KEY);
        if(!saved.getString("fluid").equals(id(frame.before)))saved=new CompoundTag();
        var sources=inputs(saved);sources.addAll(extra);
        write(owner,after,true,owners(saved),sources,ItemStack.EMPTY);
    }
    public static ItemStack extraction(ItemStack raw){
        var frames=FRAMES.get();if(frames.isEmpty()||raw.isEmpty())return raw;var frame=frames.peek();
        var handler=tank(frame.owner);if(handler==null)return raw;var after=handler.getFluidInTank(0);
        int used=frame.before.getAmount()-(frame.before.isFluidEqual(after)?after.getAmount():0);if(used<=0)return raw;
        ItemStack result=raw.copy();var saved=frame.owner.getPersistentData().getCompound(KEY);
        if(!saved.getString("fluid").equals(id(frame.before)))return result;
        List<ItemStack> inputs=inputs(saved);IngredientProvenance.sources(result,inputs);
        ItemStack original=ItemStack.of(saved.getCompound("original"));
        if(!saved.getBoolean("production")&&ItemStack.isSameItem(original,result))result.setTag(original.getTag()==null?null:original.getTag().copy());
        else if(saved.getBoolean("production")){Set<String> ingredients=new HashSet<>();for(var input:inputs)ingredients.add(BuiltInRegistries.ITEM.getKey(input.getItem()).toString());
            var batch=new ProductionLedger.Batch(BuiltInRegistries.ITEM.getKey(result.getItem()).toString(),result.getCount(),owners(saved),ingredients,"cosmopolitan:tub_extracting");
            if(frame.player instanceof ServerPlayer player)ProductionTracker.claimBatch(player,result,batch,CulinaryCredit.Source.INTERACTION);
            else ProductionTracker.pending(result,batch);}
        if(after.isEmpty())frame.owner.getPersistentData().remove(KEY);frame.owner.setChanged();return result;
    }
    public static void end(BlockEntity owner){var frames=FRAMES.get();FrameStacks.finish(frames,value->value.owner==owner);if(frames.isEmpty())FRAMES.remove();}
    private static Set<UUID> owners(CompoundTag saved){Set<UUID> result=new HashSet<>();for(var row:saved.getList("owners",10))if(((CompoundTag)row).hasUUID("id"))result.add(((CompoundTag)row).getUUID("id"));return result;}
    private static List<ItemStack> inputs(CompoundTag saved){List<ItemStack> result=new ArrayList<>();for(var row:saved.getList("inputs",10)){var item=ItemStack.of((CompoundTag)row);if(!item.isEmpty())result.add(item);}return result;}
    private static void write(BlockEntity owner,FluidStack fluid,boolean production,Set<UUID> owners,List<ItemStack> inputs,ItemStack original){
        var saved=new CompoundTag();saved.putString("fluid",id(fluid));saved.putBoolean("production",production);var people=new ListTag();
        owners.stream().sorted().forEach(id->{var row=new CompoundTag();row.putUUID("id",id);people.add(row);});saved.put("owners",people);
        var items=new ListTag();inputs.stream().limit(64).forEach(item->items.add(item.save(new CompoundTag())));saved.put("inputs",items);
        if(!original.isEmpty())saved.put("original",original.save(new CompoundTag()));owner.getPersistentData().put(KEY,saved);owner.setChanged();
    }
}
