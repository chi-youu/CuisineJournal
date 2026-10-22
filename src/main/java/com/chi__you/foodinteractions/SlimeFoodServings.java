package com.chi__you.foodinteractions;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import com.google.gson.JsonObject;
import java.util.*;

/** Native grass-cake and stew stages, never guessed from block names in other hosts. */
public final class SlimeFoodServings {
    private SlimeFoodServings() {}
    public static int remaining(ResourceLocation id){
        return id.getNamespace().equals("slime_delight")&&id.getPath().matches("grass_block_cake_[1-7]")
                ?8-(id.getPath().charAt(id.getPath().length()-1)-'0'):0;
    }
    public static ResourceLocation whole(){return ResourceLocation.parse("slime_delight:grass_block_cake_1");}
    public static ResourceLocation slice(){return ResourceLocation.parse("slime_delight:slice_of_grass_block_cake");}
    public static int stewRemaining(ResourceLocation id){
        return id.getNamespace().equals("slime_delight")&&id.getPath().matches("nether_stew_[1-4]")
                ?5-(id.getPath().charAt(id.getPath().length()-1)-'0'):0;
    }
    public static List<InteractionDisplay> displays(Block block){
        var id=BuiltInRegistries.BLOCK.getKey(block);
        if(stewRemaining(id)>0&&block.getClass().getName().startsWith("net.mcreator.slimedelight.block.NetherStew")){
            var bowl=new JsonObject();bowl.addProperty("item","slime_delight:nether_brick_bowl");
            return List.of(new InteractionDisplay(id,ResourceLocation.parse("slime_delight:nether_stew_1"),bowl,
                    ResourceLocation.parse("slime_delight:bowl_of_nether_stew"),1,Map.of(),"serving","Slime Delight stew; four decreasing stages"));
        }
        if(remaining(id)==0||!block.getClass().getName().startsWith("net.mcreator.slimedelight.block.GrassBlockCake"))return List.of();
        var knife=new JsonObject();knife.addProperty("tag","forge:tools/knives");
        return List.of(new InteractionDisplay(id,whole(),knife,slice(),1,Map.of(),"cutting","Slime Delight grass cake procedure; seven decreasing stages"));
    }
}
