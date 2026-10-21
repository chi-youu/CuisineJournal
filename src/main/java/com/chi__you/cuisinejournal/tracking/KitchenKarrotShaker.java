package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.compat.HostReflection;

import com.chi__you.cuisinejournal.food.IngredientProvenance;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import java.util.*;

/** KitchenKarrot portable shaker ownership survives handoff and storage. */
public final class KitchenKarrotShaker {
    private static final String SHAKER="cuisinejournal:kitchen_karrot_shaker";
    private KitchenKarrotShaker() {}

    public static void shaken(ItemStack tool,LivingEntity user){
        if(!(user instanceof ServerPlayer player))return;
        tool.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler->{
            var saved=new CompoundTag();saved.putUUID("owner",player.getUUID());var inputs=new ListTag();
            for(int slot=0;slot<5;slot++)inputs.add(handler.getStackInSlot(slot).copyWithCount(1).save(new CompoundTag()));
            saved.put("inputs",inputs);tool.getOrCreateTag().put(SHAKER,saved);
        });
    }

    public static ItemStack shakerResult(Object menu,net.minecraft.world.entity.player.Player user,IItemHandler handler,ItemStack raw){
        if(!(user instanceof ServerPlayer player)||raw.isEmpty())return raw;
        Object held=HostReflection.field(menu,"itemStack");
        if(!(held instanceof ItemStack tool))return raw;
        var saved=tool.hasTag()?tool.getTag().getCompound(SHAKER):new CompoundTag();
        var inputs=new ArrayList<ItemStack>();boolean same=saved.getList("inputs",10).size()==5;
        for(int slot=0;slot<5;slot++){
            var input=handler.getStackInSlot(slot).copyWithCount(1);inputs.add(input);
            if(same&&!ItemStack.isSameItemSameTags(input,ItemStack.of(saved.getList("inputs",10).getCompound(slot))))same=false;
        }
        Set<UUID> owners=same&&saved.hasUUID("owner")?Set.of(saved.getUUID("owner")):Set.of();
        tool.removeTagKey(SHAKER);
        ItemStack result=raw.copy();IngredientProvenance.sources(result,inputs);
        // The host now inserts or drops this result and unconditionally consumes the five inputs.
        DirectDropProgress.finish(player.level(),result,batch(result,owners,inputs,"kitchenkarrot:cocktail"));
        return result;
    }

    private static ProductionLedger.Batch batch(ItemStack result,Set<UUID> owners,List<ItemStack> inputs,String method){
        Set<String> ids=new HashSet<>();for(var input:inputs)if(!input.isEmpty())ids.add(BuiltInRegistries.ITEM.getKey(input.getItem()).toString());
        return new ProductionLedger.Batch(BuiltInRegistries.ITEM.getKey(result.getItem()).toString(),result.getCount(),owners,ids,method);
    }
}
