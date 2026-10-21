package com.chi__you.cuisinejournal.food;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

/** Explicit multi-block serving chains read from the installed procedures. */
public final class UnusualServingRelations {
    public record Serving(String whole,String output,int remaining,boolean direct){}
    private UnusualServingRelations(){}
    public static Serving get(Block block){
        var id=BuiltInRegistries.BLOCK.getKey(block);
        if(!id.getNamespace().equals("unusual_delight"))return null;
        return switch(id.getPath()){
            case "dragon_loaf"->new Serving("dragon_loaf","bowl_of_dragon_loaf",3,false);
            case "dragonloaf_2"->new Serving("dragon_loaf","bowl_of_dragon_loaf",2,false);
            case "dragonloaf_3"->new Serving("dragon_loaf","bowl_of_dragon_loaf",1,false);
            case "snow_head"->new Serving("snow_head","pumpkin_mousse_bowl",5,false);
            case "snowhead_2"->new Serving("snow_head","bowl_of_snow_head",4,false);
            case "snowhead_3"->new Serving("snow_head","bowl_of_snow_head",3,false);
            case "snowhead_4"->new Serving("snow_head","bowl_of_snow_head",2,false);
            case "snowhead_5"->new Serving("snow_head","bowl_of_snow_head",1,false);
            case "glow_gummy"->new Serving("glow_gummy","glow_gummy",3,true);
            case "glowgummyeaten_1"->new Serving("glow_gummy","glow_gummy",2,true);
            case "glow_gummyeaten_2"->new Serving("glow_gummy","glow_gummy",1,true);
            default->null;
        };
    }
    public static ItemStack item(String name){return BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("unusual_delight",name)).map(ItemStack::new).orElse(ItemStack.EMPTY);}
}
