package com.chi__you.cuisinejournal.recipe;

import com.chi__you.cuisinejournal.compat.HostReflection;

import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import java.util.*;

public final class LetsDoRecipes {
    private LetsDoRecipes(){}
    public static List<Ingredient> inputs(Recipe<?> recipe){
        var result=new ArrayList<>(recipe.getIngredients());
        if(recipe.getClass().getName().equals("net.satisfy.vinery.core.recipe.FermentationBarrelRecipe")){
            Object type=HostReflection.invoke(recipe,"getJuiceType");List<ItemStack> juice=new ArrayList<>();
            java.lang.reflect.Method query=null;try{query=Class.forName("net.satisfy.vinery.core.util.JuiceUtil").getMethod("getJuiceType",ItemStack.class);}
            catch(ReflectiveOperationException|LinkageError ignored){}
            if(type instanceof String wanted&&query!=null)for(Item item:BuiltInRegistries.ITEM){
                ItemStack stack=new ItemStack(item);
                Object actual=null;try{actual=query.invoke(null,stack);}
                catch(ReflectiveOperationException|LinkageError ignored){}
                if(wanted.equals(actual))juice.add(stack);
            }
            if(!juice.isEmpty())result.add(Ingredient.of(juice.stream()));
        }
        return List.copyOf(result);
    }
    public static List<RecipeEntry> preparations(){
        List<RecipeEntry> result=new ArrayList<>();
        for(String[] pair:List.of(new String[]{"green_tea_leaf_block","dried_out_green_tea_leaf_block"},
                new String[]{"mixed_tea_leaf_block","oolong_tea_leaf_block"},new String[]{"dried_green_tea_leaf_block","black_tea_leaf_block"})){
            var from=ResourceLocation.fromNamespaceAndPath("herbalbrews",pair[0]);var to=ResourceLocation.fromNamespaceAndPath("herbalbrews",pair[1]);
            if(BuiltInRegistries.ITEM.containsKey(from)&&BuiltInRegistries.ITEM.containsKey(to))result.add(new RecipeEntry(
                    ResourceLocation.fromNamespaceAndPath("cuisinejournal","letsdo/tea_drying/"+pair[1]),ResourceLocation.parse("herbalbrews:leaf_drying"),
                    CookingMethod.of("herbalbrews","leaf_drying"),to,1,List.of(new RecipeEntry.IngredientSlot(List.of(from))),"herbalbrews"));
        }
        if(!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("meadow:cheese_stick")))return result;
        var bread=members("meadow:bread");var cheese=members("meadow:cheese");
        if(!bread.isEmpty()&&!cheese.isEmpty())result.add(new RecipeEntry(ResourceLocation.parse("cuisinejournal:letsdo/fondue"),
                ResourceLocation.parse("meadow:fondue"),CookingMethod.of("meadow","fondue"),ResourceLocation.parse("meadow:cheese_stick"),1,
                List.of(new RecipeEntry.IngredientSlot(bread,List.of(),List.of(ResourceLocation.parse("meadow:bread"))),
                        new RecipeEntry.IngredientSlot(cheese,List.of(),List.of(ResourceLocation.parse("meadow:cheese")))),"meadow"));
        return result;
    }
    private static List<ResourceLocation> members(String id){return BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM,ResourceLocation.parse(id)))
            .map(values->values.stream().map(holder->BuiltInRegistries.ITEM.getKey(holder.value())).toList()).orElse(List.of());}
}
