package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.compat.HostReflection;

import com.chi__you.cuisinejournal.compat.HostClasses;
import net.minecraft.world.item.*;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.effect.MobEffectInstance;
import java.util.*;

/** Pure DrinkBeer value-model inspection, without executing native consumption actions. */
public final class DrinkBeerFoodCompat {
    private DrinkBeerFoodCompat() {}

    public static FoodProperties properties(ItemStack stack,FoodProperties original){
        if(HostClasses.matches(stack.getItem(),"lekavar.lma.drinkbeer.items.MixedBeerBlockItem")){
            Object mixed=mixedBeer(stack);Object hunger=HostReflection.invoke(mixed,"getHunger");
            if(hunger instanceof Number value)return new FoodProperties.Builder().nutrition(Math.max(0,value.intValue())).saturationMod(0).alwaysEat().build();
            try{var manager=Class.forName("lekavar.lma.drinkbeer.managers.MixedBeerManager");int id=(Integer)manager.getMethod("getBeerId",ItemStack.class).invoke(null,stack);
                var beers=Class.forName("lekavar.lma.drinkbeer.utils.beer.Beers").getMethod("byId",int.class).invoke(null,id);
                Object raw=HostReflection.invoke(beers,"getBeerItem");if(raw instanceof Item item)return item.getFoodProperties(new ItemStack(item),null);
            }catch(ReflectiveOperationException|LinkageError ignored){}
        }
        return original;
    }

    /** Null means this is not a mixed-beer effect list; an empty list is a real empty result. */
    public static List<MobEffectInstance> effects(ItemStack stack) {
        Object mixed=HostReflection.invoke(mixedBeer(stack),"getStatusEffectList");
        if(!(mixed instanceof List<?> list))return null;
        List<MobEffectInstance> result=new ArrayList<>();
        for(Object pair:list){Object effect=HostReflection.invoke(pair,"getKey"),duration=HostReflection.invoke(pair,"getValue");
            if(effect instanceof net.minecraft.world.effect.MobEffect mob&&duration instanceof Number n)result.add(new MobEffectInstance(mob,n.intValue()));}
        return List.copyOf(result);
    }

    public static float healing(ItemStack stack){var value=HostReflection.invoke(mixedBeer(stack),"getHealth");return value instanceof Number n?Math.max(0,n.floatValue()):0;}

    private static Object mixedBeer(ItemStack stack){
        if(!HostClasses.matches(stack.getItem(),"lekavar.lma.drinkbeer.items.MixedBeerBlockItem"))return null;
        try{
            var manager=Class.forName("lekavar.lma.drinkbeer.managers.MixedBeerManager");
            int id=(Integer)manager.getMethod("getBeerId",ItemStack.class).invoke(null,stack);if(id<=0)return null;
            var beers=Class.forName("lekavar.lma.drinkbeer.utils.beer.Beers");
            Object beer=beers.getMethod("byId",int.class).invoke(null,id);
            var model=Class.forName("lekavar.lma.drinkbeer.utils.mixedbeer.MixedBeerOnUsing");var value=model.getConstructor().newInstance();
            model.getMethod("setBeer",beers).invoke(value,beer);
            Object raw=HostReflection.invoke(beer,"getBeerItem");
            if(!(raw instanceof Item item))return null;var food=item.getFoodProperties(new ItemStack(item),null);if(food==null)return null;
            model.getMethod("addHunger",int.class).invoke(value,food.getNutrition());
            Object spices=manager.getMethod("getSpiceList",ItemStack.class).invoke(null,stack);
            model.getMethod("setSpiceList",List.class).invoke(value,spices);
            var flavorManager=Class.forName("lekavar.lma.drinkbeer.managers.SpiceAndFlavorManager");
            Object combined=flavorManager.getMethod("getCombinedFlavor",List.class).invoke(null,spices);
            if(combined!=null)model.getMethod("addFlavor",combined.getClass()).invoke(value,combined);
            List<org.apache.commons.lang3.tuple.Pair<net.minecraft.world.effect.MobEffect,Integer>> effects=new ArrayList<>();
            for(var effect:food.getEffects())effects.add(org.apache.commons.lang3.tuple.Pair.of(effect.getFirst().getEffect(),effect.getFirst().getDuration()));
            model.getMethod("addStatusEffect",List.class).invoke(value,effects);
            // This host routine only changes a value object. World/entity actions are deliberately not called.
            flavorManager.getMethod("applyFlavorValue",model).invoke(null,value);return value;
        }catch(ReflectiveOperationException|LinkageError ignored){return null;}
    }
}

