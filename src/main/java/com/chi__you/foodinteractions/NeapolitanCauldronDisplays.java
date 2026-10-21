package com.chi__you.foodinteractions;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import java.util.*;

/** Exact bottle/cauldron families declared by Neapolitan and Collector's Reap, display only. */
final class NeapolitanCauldronDisplays {
    private static final Map<String,List<String>> FLAVORS=Map.of(
            "neapolitan",List.of("vanilla","chocolate","strawberry","banana","mint","adzuki"),
            "collectorsreap",List.of("lime","pomegranate","pink_dragon_fruit","lucuma"));
    private NeapolitanCauldronDisplays(){}
    static List<InteractionDisplay> read(Block block){
        var id=BuiltInRegistries.BLOCK.getKey(block);var flavors=FLAVORS.getOrDefault(id.getNamespace(),List.of());
        for(String flavor:flavors)if(id.getPath().equals(flavor+"_milkshake_cauldron")){
            var output=ResourceLocation.fromNamespaceAndPath(id.getNamespace(),flavor+"_milkshake");
            if(!BuiltInRegistries.ITEM.containsKey(output))return List.of();
            var input=new com.google.gson.JsonObject();input.addProperty("item","minecraft:glass_bottle");
            return List.of(new InteractionDisplay(id,null,input,output,1,Map.of("level","1"),"serving",
                    "NeapolitanCauldronInteractions.addMilkInteractions: bottle removes one level; no source block item"));
        }
        return List.of();
    }
}
