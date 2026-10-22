package com.chi__you.foodinteractions;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.registries.BuiltInRegistries;
import com.google.gson.JsonObject;
import java.util.*;

/** The installed procedures replace four successive blocks, each emitting one portion. */
public final class UndergardenServings {
    public record Relation(ResourceLocation whole,ResourceLocation portion,int remaining,boolean pie) {}
    private UndergardenServings() {}
    public static Relation relation(ResourceLocation id) {
        if(!id.getNamespace().equals("undergardendelight"))return null;
        String path=id.getPath();int stage=0;
        if(path.matches(".+_[123]")){stage=path.charAt(path.length()-1)-'0';path=path.substring(0,path.length()-2);}
        boolean pie=Set.of("blisterberry_pie","droopfruit_pie","gloomgourd_pie").contains(path);
        if(!pie&&!path.equals("stuffed_gloomgourd"))return null;
        return new Relation(ResourceLocation.fromNamespaceAndPath(id.getNamespace(),path),
                ResourceLocation.fromNamespaceAndPath(id.getNamespace(),pie?path+"_slice":path+"_bowl"),4-stage,pie);
    }
    public static List<InteractionDisplay> displays(Block block) {
        if(!block.getClass().getName().startsWith("net.mcreator.undergardendelight.block."))return List.of();
        var id=BuiltInRegistries.BLOCK.getKey(block);var rule=relation(id);if(rule==null)return List.of();
        var input=new JsonObject();input.addProperty(rule.pie()?"tag":"item",rule.pie()?"farmersdelight:tools/knives":"minecraft:bowl");
        return List.of(new InteractionDisplay(id,rule.whole(),input,rule.portion(),1,Map.of(),rule.pie()?"cutting":"serving",
                "Undergarden Delight procedure: one portion and next registered block stage"));
    }
}
