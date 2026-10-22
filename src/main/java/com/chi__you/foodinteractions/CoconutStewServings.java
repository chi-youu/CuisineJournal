package com.chi__you.foodinteractions;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import java.util.*;

public final class CoconutStewServings {
    private CoconutStewServings() {}
    public static boolean known(ResourceLocation id){return id.getNamespace().equals("crabbers_delight_expand")
            &&Set.of("coconut_chicken_stew","coconut_crab_stew").contains(id.getPath());}
    public static ResourceLocation portion(ResourceLocation id){return ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"bowl_of_"+id.getPath());}
    public static ResourceLocation claws(){return ResourceLocation.parse("crabbers_delight_expand:coconut_flavored_crab_claws");}
    public static List<InteractionDisplay> displays(Block block){
        var id=BuiltInRegistries.BLOCK.getKey(block);
        if(!known(id)||!block.getClass().getName().startsWith("net.budoumiao.crabbersdelightexpand.block."))return List.of();
        var bowl=new JsonObject();bowl.addProperty("item","minecraft:bowl");
        var result=new ArrayList<InteractionDisplay>();
        for(int stage=id.getPath().equals("coconut_crab_stew")?1:0;stage<(id.getPath().equals("coconut_crab_stew")?5:4);stage++)
            result.add(new InteractionDisplay(id,id,bowl,portion(id),1,Map.of("blockstate",Integer.toString(stage)),"serving","Coconut stew native bowl procedure"));
        if(id.getPath().equals("coconut_crab_stew")){
            var hand=new JsonObject();hand.addProperty("item","minecraft:air");
            result.add(new InteractionDisplay(id,id,hand,claws(),1,Map.of("blockstate","0"),"serving","Native first serving is claws, then four stew bowls"));
        }
        return result;
    }
}
