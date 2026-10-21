package com.chi__you.cuisinejournal.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import java.util.*;

/** Recorded when the host actually enables its cauldron interactions, including Collector's Reap. */
public final class NeapolitanCauldrons {
    public record Relation(Item bottle,Block cauldron,Item iceCream){}
    private static final Map<Block,Relation> RELATIONS=new IdentityHashMap<>();
    private NeapolitanCauldrons(){}
    public static void register(Item bottle,Block cauldron,Item iceCream){RELATIONS.put(cauldron,new Relation(bottle,cauldron,iceCream));}
    public static Relation get(Block block){return RELATIONS.get(block);}
    public static List<RecipeEntry> recipes(){
        List<RecipeEntry> result=new ArrayList<>();
        var milk=ResourceLocation.parse("neapolitan:milk_bottle");
        var ordered=RELATIONS.values().stream().sorted(Comparator.comparing(value->BuiltInRegistries.ITEM.getKey(value.bottle()).toString())).toList();
        for(var relation:ordered){
            var output=BuiltInRegistries.ITEM.getKey(relation.bottle());
            for(int servings=1;servings<=3;servings++){
                List<RecipeEntry.IngredientSlot> inputs=new ArrayList<>();
                for(int i=0;i<servings;i++)inputs.add(new RecipeEntry.IngredientSlot(List.of(milk)));
                inputs.add(new RecipeEntry.IngredientSlot(List.of(BuiltInRegistries.ITEM.getKey(relation.iceCream()))));
                result.add(new RecipeEntry(ResourceLocation.fromNamespaceAndPath("cuisinejournal","milkshake_cauldron/"+output.getNamespace()+"/"+output.getPath()+"/"+servings),
                        ResourceLocation.parse("neapolitan:milkshake_cauldron"),CookingMethod.of("neapolitan","milkshake_cauldron"),output,servings,inputs,output.getNamespace()));
            }
        }
        return List.copyOf(result);
    }
}
