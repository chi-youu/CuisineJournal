package com.chi__you.cuisinejournal.recipe;

import com.chi__you.cuisinejournal.compat.HostReflection;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.*;

/** Mirrors the host JEI's base-model and custom ingredient resolution without client classes. */
public final class YoukaiCuisineInputs {
    private YoukaiCuisineInputs() {}
    public static boolean supports(Object recipe) {
        for (Class<?> type=recipe.getClass();type!=null;type=type.getSuperclass())
            if (type.getName().equals("dev.xkmc.youkaishomecoming.content.pot.table.recipe.CuisineRecipe")) return true;
        return false;
    }
    public static List<Ingredient> read(Object recipe) {
        try {
            Object base=HostReflection.invoke(recipe,"base");
            List<Ingredient> inputs=new ArrayList<>();
            List<Ingredient> custom=new ArrayList<>();
            Object raw=HostReflection.invoke(recipe,"getCustomIngredients");
            if (!(raw instanceof List<?> list)) throw new IllegalStateException("Missing cuisine ingredients");
            for(Object value:list)custom.add((Ingredient)value);
            Class<?> variant=Class.forName("dev.xkmc.youkaishomecoming.content.pot.table.item.VariantTableItemBase");
            Object model=((Map<?,?>)variant.getField("MAP").get(null)).get(base);
            if(model!=null)variant.getMethod("collectIngredients",List.class,List.class).invoke(model,inputs,custom);
            else {
                Class<?> fixed=Class.forName("dev.xkmc.youkaishomecoming.content.pot.table.item.IngredientTableItem");
                model=((Map<?,?>)fixed.getField("FIXED").get(null)).get(base);
                if(model!=null)fixed.getMethod("collectIngredients",List.class).invoke(model,inputs);
                else if(base instanceof ResourceLocation id && BuiltInRegistries.ITEM.containsKey(id))
                    inputs.add(Ingredient.of(BuiltInRegistries.ITEM.get(id)));
                else throw new IllegalStateException("Unknown cuisine base: "+base);
            }
            inputs.addAll(custom);
            return inputs.stream().filter(ingredient->!ingredient.isEmpty()).toList();
        } catch(ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException("Cannot resolve cuisine base",failure);
        }
    }
}
