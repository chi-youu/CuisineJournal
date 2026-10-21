package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.compat.HostReflection;

import com.chi__you.cuisinejournal.compat.HostClasses;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import net.minecraft.world.effect.MobEffectInstance;
import java.util.*;

/** Kaleidoscope Grilling heat, seasoning and actual secret-skewer ingredients. */
public final class GrillingFoodCompat {
    private GrillingFoodCompat() {}

    public static boolean nested(){return FoodUseContext.active()
            &&HostClasses.matches(FoodUseContext.current().getItem(),"cn.breezeth.kaleidoscope_grilling.skewer.SecretSkewerItem");}

    public static void finishHot(ItemStack stack,net.minecraft.world.entity.LivingEntity consumer){
        if(!CuisineFoodRuntime.activeForCurrentThread()||!stack.hasTag()||!stack.getTag().contains("HotUntil"))return;
        try{Class.forName("cn.breezeth.kaleidoscope_grilling.food.HotFoodHandler")
                .getMethod("finishEarly",ItemStack.class,net.minecraft.world.entity.LivingEntity.class).invoke(null,stack,consumer);}
        catch(ReflectiveOperationException|LinkageError failure){com.chi__you.cuisinejournal.CuisineJournalLog.LOGGER.warn("Cannot finish native hot-food seasoning",failure);}
    }

    public static boolean hot(ItemStack stack){
        if(!stack.hasTag()||!stack.getTag().contains("HotUntil"))return false;
        var server=com.chi__you.cuisinejournal.CuisineJournalServer.server();
        if(CuisineFoodRuntime.isClientThread()){var level=net.minecraft.client.Minecraft.getInstance().level;return level!=null&&stack.getTag().getLong("HotUntil")>level.getGameTime();}
        return server!=null&&stack.getTag().getLong("HotUntil")>server.overworld().getGameTime();
    }

    public static float saturation(ItemStack stack,float base){
        if(CuisineFoodRuntime.activeForCurrentThread()&&stack.hasTag()&&stack.getTag().contains("cuisinejournal:grilling_heat_multiplier",5))
            return base*stack.getTag().getFloat("cuisinejournal:grilling_heat_multiplier");
        if(!CuisineFoodRuntime.activeForCurrentThread()||!hot(stack))return base;
        try{var config=Class.forName("cn.breezeth.kaleidoscope_grilling.food.HotFoodConfig").getField("HOT_SATURATION_PERCENT").get(null);
            Object value=HostReflection.invoke(config,"get");if(value instanceof Number n)return base*n.floatValue()/100F;
        }catch(ReflectiveOperationException|LinkageError ignored){}
        return base;
    }

    public static void snapshotHot(ItemStack stack){
        if(hot(stack)&&CuisineFoodRuntime.activeForCurrentThread())stack.getOrCreateTag().putFloat("cuisinejournal:grilling_heat_multiplier",saturation(stack,1));
    }

    public static MobEffectInstance nativeDuration(MobEffectInstance effect){
        if(FoodUseContext.seasoningActive()||!FoodUseContext.active()||!CuisineFoodRuntime.activeForCurrentThread()||!hot(FoodUseContext.current())
                ||effect.isInfiniteDuration()||effect.getEffect().isInstantenous())return effect;
        var id=BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect());
        if(id!=null&&id.toString().equals("kaleidoscope_grilling:invincible"))return effect;
        return new MobEffectInstance(effect.getEffect(),(int)Math.min(Integer.MAX_VALUE,2L*effect.getDuration()),effect.getAmplifier(),effect.isAmbient(),effect.isVisible(),effect.showIcon());
    }

    public static List<String> actualSources(ItemStack stack){
        if(!HostClasses.matches(stack.getItem(),"cn.breezeth.kaleidoscope_grilling.skewer.SecretSkewerItem"))return null;
        try{Object raw=Class.forName("cn.breezeth.kaleidoscope_grilling.skewer.SkeweringHandler")
                .getMethod("readIngredientStacks",ItemStack.class).invoke(null,stack);
            if(raw instanceof List<?> list){var inputs=list.stream().filter(ItemStack.class::isInstance).map(ItemStack.class::cast).toList();
                ItemStack copy=stack.copy();IngredientProvenance.sources(copy,inputs);return IngredientProvenance.sources(copy);}
        }catch(ReflectiveOperationException|LinkageError ignored){}
        return null;
    }
}

