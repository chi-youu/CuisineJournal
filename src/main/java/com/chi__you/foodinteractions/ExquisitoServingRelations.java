package com.chi__you.foodinteractions;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import java.util.*;

/** Exact five cake/slice pairs verified in ExquisitoCakeBlock's own knife interaction. */
public final class ExquisitoServingRelations {
    private static final Set<String> CAKES=Set.of("chorus_cake","ether_bulb_cake","jelly_ring_cake","nightshade_berry_cake","zure_berry_cake");
    private ExquisitoServingRelations(){}
    public static ItemStack slice(Block block){
        var id=BuiltInRegistries.BLOCK.getKey(block);
        if(!id.getNamespace().equals("exquisito")||!CAKES.contains(id.getPath()))return ItemStack.EMPTY;
        var item=ResourceLocation.fromNamespaceAndPath(id.getNamespace(),id.getPath()+"_slice");
        return BuiltInRegistries.ITEM.getOptional(item).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }
    public static List<InteractionDisplay> displays(Block block){
        var slice=slice(block);if(slice.isEmpty())return List.of();
        var knife=new com.google.gson.JsonObject();knife.addProperty("tag","farmersdelight:tools/knives");
        return List.of(new InteractionDisplay(BuiltInRegistries.BLOCK.getKey(block),BuiltInRegistries.ITEM.getKey(block.asItem()),knife,
                BuiltInRegistries.ITEM.getKey(slice.getItem()),1,Map.of("bites","0"),"cutting","ExquisitoCakeBlock: exact native knife/slice map; seven bites"));
    }
}
