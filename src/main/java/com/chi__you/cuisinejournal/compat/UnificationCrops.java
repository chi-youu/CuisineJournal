package com.chi__you.cuisinejournal.compat;

import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.Predicate;
import static com.chi__you.cuisinejournal.compat.UnificationContent.id;

/** Explicit crop-source policy; fruit trees and the retained underwater peanut route are not removed. */
public final class UnificationCrops {
    private record Crop(String group,String item,String wild) {}
    private static final List<Crop> CROPS=List.of(
            new Crop("tomato_seed","farmersdelight:tomato_seeds","farmersdelight:wild_tomatoes"),
            new Crop("tomato_seed","farm_and_charm:tomato_seeds","farm_and_charm:wild_tomatoes"),
            new Crop("tomato_seed","kaleidoscope_cookery:tomato_seed",""),
            new Crop("tomato_seed","crockpot:tomato_seeds",""),
            new Crop("lettuce_seed","kaleidoscope_cookery:lettuce_seed",""),
            new Crop("lettuce_seed","farm_and_charm:lettuce_seeds","farm_and_charm:wild_lettuce"),
            new Crop("corn","crockpot:corn",""),
            new Crop("corn","bountifulfares:maize","bountifulfares:wild_maize"),
            new Crop("coffee_raw","farmersrespite:coffee_beans","farmersrespite:wild_coffee_bush"),
            new Crop("coffee_raw","youkaishomecoming:green_coffee_bean",""),
            new Crop("coffee_raw","herbalbrews:coffee_beans","herbalbrews:wild_coffee_plant"),
            new Crop("coffee_raw","rusticdelight:coffee_beans","rusticdelight:wild_coffee"),
            new Crop("redbean","youkaishomecoming:redbean","youkaishomecoming:wild_redbean"),
            new Crop("redbean","neapolitan:adzuki_beans",""),
            new Crop("eggplant","kaleidoscope_chinesefood:eggplant",""),
            new Crop("eggplant","culturaldelights:eggplant","culturaldelights:wild_eggplants"),
            new Crop("eggplant","crockpot:eggplant",""),
            new Crop("eggplant","dumplings_delight:eggplant",""),
            new Crop("chili_red","kaleidoscope_cookery:red_chili",""),
            new Crop("chili_red","crockpot:pepper",""),
            new Crop("chili_red","muffins_thaidelight:pepper",""),
            new Crop("onion","farmersdelight:onion","farmersdelight:wild_onions"),
            new Crop("onion","farm_and_charm:onion","farm_and_charm:wild_onions"),
            new Crop("onion","kaleidoscope_grilling:onion","kaleidoscope_grilling:wild_onion"),
            new Crop("onion","crockpot:onion",""),
            new Crop("garlic","veggiesdelight:garlic","veggiesdelight:wild_garlic"),
            new Crop("garlic","crockpot:garlic",""),
            new Crop("garlic","dumplings_delight:garlic",""),
            new Crop("garlic","braziliandelight:garlic_bulb","braziliandelight:wild_garlic"),
            new Crop("garlic","ubesdelight:garlic","ubesdelight:wild_garlic"),
            new Crop("broccoli","smc:broccoli",""),
            new Crop("broccoli","veggiesdelight:broccoli","veggiesdelight:wild_broccoli"),
            new Crop("sweet_potato","kaleidoscope_grilling:sweet_potato","kaleidoscope_grilling:wild_sweet_potato"),
            new Crop("sweet_potato","veggiesdelight:sweet_potato","veggiesdelight:wild_sweet_potatoes"),
            new Crop("pineapple","pineapple_delight:pineapple","pineapple_delight:pineapple_wild_crop"),
            new Crop("pineapple","fruitsdelight:pineapple","fruitsdelight:wild_pineapple"),
            new Crop("pineapple","jellyfishing:pineapple","jellyfishing:pineapple_plant"));
    private UnificationCrops() {}

    static void configure(Map<String,ResourceLocation> winners,Map<ResourceLocation,ResourceLocation> replacements,
                          Set<ResourceLocation> excluded,Set<String> plants,Predicate<ResourceLocation> exists) {
        for(var crop:CROPS){
            var winner=winners.get("cuisinejournal:"+crop.group);
            if(winner!=null&&!winner.equals(id(crop.item))&&!crop.wild.isEmpty())plants.add(crop.wild);
        }
        removeSeed(winners,excluded,exists,"onion","crockpot:onion","crockpot:onion_seeds");
        removeSeed(winners,excluded,exists,"garlic","crockpot:garlic","crockpot:garlic_seeds");
        var pineapple=winners.get("cuisinejournal:pineapple");
        if(pineapple!=null&&!pineapple.equals(id("jellyfishing:pineapple"))&&exists.test(id("jellyfishing:pineapple_seeds"))){
            excluded.add(id("jellyfishing:pineapple_seeds"));
            if(pineapple.equals(id("pineapple_delight:pineapple"))&&exists.test(id("pineapple_delight:pineapple_crop")))
                replacements.put(id("jellyfishing:pineapple_seeds"),id("pineapple_delight:pineapple_crop"));
            else replacements.put(id("jellyfishing:pineapple_seeds"),pineapple);
        }
    }
    private static void removeSeed(Map<String,ResourceLocation> winners,Set<ResourceLocation> excluded,
                                   Predicate<ResourceLocation> exists,String group,String crop,String seed){
        var winner=winners.get("cuisinejournal:"+group);
        if(winner!=null&&!winner.equals(id(crop))&&exists.test(id(seed)))excluded.add(id(seed));
    }
    public static boolean blocksPlanting(ResourceLocation item,UnificationContent.Plan plan){
        if(!plan.enabled())return false;
        if(plan.excluded().contains(item))return true;
        for(var crop:CROPS)if(crop.item.equals(item.toString())){
            var winner=plan.winner("cuisinejournal:"+crop.group);return winner!=null&&!winner.equals(item);
        }
        if(item.equals(id("ltc2:pepper_red")))return !plan.replace(item).equals(item);
        if(Set.of("veggiesdelight:garlic_clove","dumplings_delight:garlic_clove","braziliandelight:garlic_clove",
                "ubesdelight:garlic_chop").contains(item.toString())){
            var whole=plan.winner("cuisinejournal:garlic");
            return whole!=null&&!whole.getNamespace().equals(item.getNamespace());
        }
        return (item.getPath().contains("seed")||item.getPath().endsWith("_pips"))&&!plan.replace(item).equals(item);
    }
    public static net.minecraft.world.InteractionResult plantPeanut(net.minecraft.world.item.context.UseOnContext context,
                                                                   net.minecraft.world.item.ItemStack held){
        var level=context.getLevel();var plan=UnificationRuntime.plan(level.getServer());
        var winner=plan.winner("cuisinejournal:peanut");var nativeSeed=id("jellyfishing:seanut");
        if(winner==null||winner.equals(nativeSeed)||!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(nativeSeed)
                ||!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).equals(winner))return null;
        var fluid=level.getFluidState(context.getClickedPos().relative(context.getClickedFace()));
        if(!fluid.is(net.minecraft.tags.FluidTags.WATER)||!fluid.isSource())return null;
        var proxy=new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(nativeSeed),held.getCount());
        var hit=new net.minecraft.world.phys.BlockHitResult(context.getClickLocation(),context.getClickedFace(),context.getClickedPos(),context.isInside());
        var placement=new net.minecraft.world.item.context.BlockPlaceContext(level,context.getPlayer(),context.getHand(),proxy,hit);
        int before=proxy.getCount();var result=proxy.getItem().useOn(placement);
        if(context.getPlayer()==null||!context.getPlayer().isCreative())held.shrink(Math.max(0,before-proxy.getCount()));
        return result;
    }
}
