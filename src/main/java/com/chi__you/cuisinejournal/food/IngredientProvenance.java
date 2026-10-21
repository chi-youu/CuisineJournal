package com.chi__you.cuisinejournal.food;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Actual ingredient snapshots shared by every host, independent of quality and production state. */
public final class IngredientProvenance {
    public static final String KEY="cuisinejournal:ingredient_sources";
    private IngredientProvenance() {}

    public static List<String> sources(ItemStack stack) {
        if(!stack.hasTag()||!stack.getTag().contains(KEY,9))return null;
        List<String> result=new ArrayList<>();var list=stack.getTag().getList(KEY,8);
        for(int i=0;i<Math.min(list.size(),128);i++){
            String value=list.getString(i);if(ResourceLocation.tryParse(value.startsWith("#")?value.substring(1):value)!=null)result.add(value);
        }
        return List.copyOf(result);
    }
    public static void sources(ItemStack output,Collection<ItemStack> inputs) {
        Set<String> sources=new LinkedHashSet<>();
        for(ItemStack stack:inputs)if(!stack.isEmpty()){
            sources.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            var binding=com.chi__you.cuisinejournal.recipe.IngredientCatalogueConfig.current().flavor(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            if(binding==null||!binding.source()){
                var upstream=sources(stack);
                sources.addAll(upstream==null?CuisineFlavorEffects.sourcesFor(BuiltInRegistries.ITEM.getKey(stack.getItem())):upstream);
            }
        }
        ListTag list=new ListTag();sources.stream().limit(128).forEach(id->list.add(net.minecraft.nbt.StringTag.valueOf(id)));
        output.getOrCreateTag().put(KEY,list);
    }
}
