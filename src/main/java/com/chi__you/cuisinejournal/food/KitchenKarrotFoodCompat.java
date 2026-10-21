package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.compat.HostReflection;

import com.chi__you.cuisinejournal.compat.HostClasses;
import net.minecraft.world.item.*;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.effect.MobEffectInstance;
import java.util.*;

/** Reads the KitchenKarrot recipe selected by the actual cocktail NBT. */
public final class KitchenKarrotFoodCompat {
    private KitchenKarrotFoodCompat() {}

    private static Object cocktail(ItemStack stack){
        if(!HostClasses.matches(stack.getItem(),"io.github.tt432.kitchenkarrot.item.CocktailItem")||!stack.hasTag())return null;
        var id=net.minecraft.resources.ResourceLocation.tryParse(stack.getTag().getString("cocktail"));
        if(id==null)return null;var server=com.chi__you.cuisinejournal.CuisineJournalServer.server();
        if(CuisineFoodRuntime.isClientThread()){var level=net.minecraft.client.Minecraft.getInstance().level;return level==null?null:level.getRecipeManager().byKey(id).orElse(null);}
        return server==null?null:server.getRecipeManager().byKey(id).orElse(null);
    }

    public static FoodProperties properties(ItemStack stack, FoodProperties original) {
        Object content=HostReflection.field(cocktail(stack),"content");
        if(content!=null){Object n=HostReflection.field(content,"hunger"),s=HostReflection.field(content,"saturation");
            if(n instanceof Number nutrition&&s instanceof Number saturation)return new FoodProperties.Builder().nutrition(nutrition.intValue())
                    .saturationMod(nutrition.intValue()>0?saturation.floatValue()/(2F*nutrition.intValue()):0).alwaysEat().build();}
        return original;
    }

    public static List<MobEffectInstance> effects(ItemStack stack) {
        List<MobEffectInstance> result=new ArrayList<>();
        var content=HostReflection.field(cocktail(stack),"content");
        Object raw=HostReflection.invoke(content,"getEffect");
        if(raw instanceof List<?> list)for(var entry:list){Object effect=HostReflection.invoke(entry,"get");if(effect instanceof MobEffectInstance value)result.add(new MobEffectInstance(value));}
        return List.copyOf(result);
    }
}

