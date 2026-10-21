package com.chi__you.cuisinejournal.network;

import com.chi__you.cuisinejournal.food.*;
import com.chi__you.cuisinejournal.recipe.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class DishSearchSync {
    private DishSearchSync() {}
    public static void send(ServerPlayer player) {
        List<DishSearchPackets.Entry> page=new ArrayList<>();boolean first=true;
        for(var dish:CulinaryRegistries.current().entries()) {
            Set<net.minecraft.resources.ResourceLocation> flavors=new LinkedHashSet<>(),effects=new LinkedHashSet<>();
            ItemStack stack=new ItemStack(dish.item());
            var properties=stack.getFoodProperties(player);
            if(properties!=null)for(var pair:properties.getEffects())if(pair.getFirst()!=null&&pair.getSecond()>0)
                effects.add(BuiltInRegistries.MOB_EFFECT.getKey(pair.getFirst().getEffect()));
            for(int stars=0;stars<=5;stars++) {
                new com.chi__you.cuisinejournal.culinary.MealStamp(stars,false,5,null,"").write(stack);
                var flavor=CuisineFlavorEffects.resolveStack(stack,CuisineFoodDefinition.isDrink(stack));
                for(var id:flavor.flavors()) {
                    var preset=FlavorPresets.get(id);
                    if(preset!=null&&preset.enabled()&&FlavorAvailability.hasContent(preset,BuiltInRegistries.MOB_EFFECT::containsKey))flavors.add(id);
                }
                for(var effect:CuisineFoodEffectSource.forStack(stack))if(effect.activeFraction()>0
                        &&BuiltInRegistries.MOB_EFFECT.containsKey(effect.id()))effects.add(effect.id());
            }
            page.add(new DishSearchPackets.Entry(dish.target(),flavors.stream().limit(256).toList(),effects.stream().limit(256).toList()));
            if(page.size()==32){Network.toPlayer(player,new DishSearchPackets.Sync(first,page));first=false;page=new ArrayList<>();}
        }
        if(first||!page.isEmpty())Network.toPlayer(player,new DishSearchPackets.Sync(first,page));
    }
}
