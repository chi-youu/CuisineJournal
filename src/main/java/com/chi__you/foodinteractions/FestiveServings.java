package com.chi__you.foodinteractions;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import java.util.*;

/** The native event serves four bowls, then leaves an inedible carcass. */
public final class FestiveServings {
    private FestiveServings() {}
    public static ResourceLocation whole(){return ResourceLocation.parse("festive_delight:festive_chicken_block");}
    public static ResourceLocation portion(){return ResourceLocation.parse("festive_delight:festive_chiken");}
    public static int remaining(ResourceLocation id){
        return id.getNamespace().equals("festive_delight")&&id.getPath().matches("festive_chicken_stage_[0-3]")
                ?4-(id.getPath().charAt(id.getPath().length()-1)-'0'):0;
    }
    public static List<InteractionDisplay> displays(Block block){
        var id=BuiltInRegistries.BLOCK.getKey(block);
        if(remaining(id)==0||!block.getClass().getName().startsWith("net.toopa.festivedelight.block.FestiveChickenStage"))return List.of();
        var bowl=new JsonObject();bowl.addProperty("item","minecraft:bowl");
        return List.of(new InteractionDisplay(id,whole(),bowl,portion(),1,Map.of(),"serving","Festive Delight native bowl-serving event"));
    }
}
