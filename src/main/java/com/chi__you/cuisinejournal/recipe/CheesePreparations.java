package com.chi__you.cuisinejournal.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Confirmed non-RecipeManager preparation paths, read only when their host exists. */
final class CheesePreparations {
    private CheesePreparations(){}
    static List<RecipeEntry> read(){
        List<RecipeEntry> result=new ArrayList<>();
        add(result,"braziliandelight:milk_mixing","braziliandelight:heavy_cream_bucket",List.of(new RecipeEntry.IngredientSlot(List.of(ResourceLocation.parse("minecraft:milk_bucket")))));
        var milk=new RecipeEntry.IngredientSlot(List.of(ResourceLocation.parse("braziliandelight:heavy_cream_bucket")));
        var coag=tag("braziliandelight:is_cheese_coagulant");
        var salt=tag("forge:salt");
        if(salt.items().isEmpty())salt=tag("c:salt");
        if(!coag.items().isEmpty()&&!salt.items().isEmpty())add(result,"braziliandelight:cheese_making","braziliandelight:minas_cheese",List.of(milk,salt,coag));
        var output=ResourceLocation.parse("vintagedelight:cheese_wheel");
        var input=ResourceLocation.parse("vintagedelight:cheese_curds");
        if(!BuiltInRegistries.ITEM.containsKey(output)||!BuiltInRegistries.ITEM.containsKey(input)
                ||!BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("vintagedelight:cheese_mold")))return List.copyOf(result);
        var slot=new RecipeEntry.IngredientSlot(List.of(input));
        result.add(new RecipeEntry(ResourceLocation.parse("cuisinejournal:cheese_mold/vintagedelight/cheese_wheel"),
                ResourceLocation.parse("vintagedelight:cheese_mold"),CookingMethod.of("vintagedelight","cheese_mold"),output,1,List.of(slot,slot,slot,slot),"vintagedelight"));
        return List.copyOf(result);
    }
    private static RecipeEntry.IngredientSlot tag(String id){
        var key=ResourceLocation.parse(id);var tag=net.minecraft.tags.ItemTags.create(key);
        var values=BuiltInRegistries.ITEM.getTag(tag).map(items->items.stream().map(item->BuiltInRegistries.ITEM.getKey(item.value())).toList()).orElse(List.of());
        return new RecipeEntry.IngredientSlot(values,List.of(),List.of(key));
    }
    private static void add(List<RecipeEntry> result,String method,String output,List<RecipeEntry.IngredientSlot> inputs){
        var id=ResourceLocation.parse(output);if(!BuiltInRegistries.ITEM.containsKey(id)||inputs.stream().anyMatch(slot->slot.items().isEmpty()))return;
        result.add(new RecipeEntry(ResourceLocation.fromNamespaceAndPath("cuisinejournal","preparation/"+id.getNamespace()+"/"+id.getPath()),ResourceLocation.parse(method),CookingMethod.of(ResourceLocation.parse(method)),id,1,inputs,id.getNamespace()));
    }
}
