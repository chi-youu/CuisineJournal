package com.chi__you.cuisinejournal.compat;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.nio.file.*;
import java.util.*;
import static com.chi__you.cuisinejournal.compat.UnificationContent.id;

public final class CompatFallbackTest {
    private static int checks;
    public static void main(String[] args)throws Exception{
        var json=JsonParser.parseString(Files.readString(Path.of("src/main/resources/cuisinejournal/unification.json"))).getAsJsonObject();
        var all=new HashSet<ResourceLocation>();json.entrySet().forEach(e->e.getValue().getAsJsonObject().getAsJsonArray("items").forEach(v->all.add(id(v.getAsString()))));
        var with=UnificationContent.compile(json,true,all::contains,mod->true);
        var without=UnificationContent.compile(json,true,all::contains,mod->!mod.equals("kaleidoscope_compat"));
        check(with.replace(id("farmersdelight:tomato")).equals(id("kaleidoscope_cookery:tomato")),"installed Compat's tomato winner receives exact-item fallback");
        check(without.replace(id("crockpot:tomato")).equals(id("farmersdelight:tomato")),"without Compat farmer tomato is first priority");
        var missingFarmer=new HashSet<>(all);missingFarmer.remove(id("farmersdelight:tomato"));missingFarmer.remove(id("farmersdelight:tomato_seeds"));
        var second=UnificationContent.compile(json,true,missingFarmer::contains,mod->!mod.equals("kaleidoscope_compat"));
        check(second.winner("cuisinejournal:tomato").equals(id("crockpot:tomato"))&&second.winner("cuisinejournal:tomato_seed").equals(id("crockpot:tomato_seeds")),"CrockPot tomato and cultivation are second when farmer is absent");
        check(!UnificationCrops.blocksPlanting(id("farmersdelight:tomato_seeds"),without),"winning farmer seed remains plantable");
        check(UnificationCrops.blocksPlanting(id("farmersdelight:tomato_seeds"),with),"hidden farmer seed cannot keep creating the low-priority crop");
        check(!UnificationCrops.blocksPlanting(id("kaleidoscope_cookery:tomato_seed"),with),"Compat winner tomato seed remains plantable");
        var recipes=new LinkedHashMap<ResourceLocation,JsonElement>();
        recipes.put(id("test:tomato_meal"),parse("{type:'farmersdelight:cooking',ingredients:[{item:'farmersdelight:tomato'},{item:'crockpot:tomato'},{item:'minecraft:beef'}],result:{item:'test:meal',count:2},cookingtime:240,experience:0.5}"));
        recipes.put(id("test:rice_meal"),parse("{type:'farmersdelight:cooking',ingredients:[{item:'farmersdelight:cooked_rice'},{item:'farmersdelight:fried_egg'}],result:{item:'test:rice_meal'}}"));
        recipes.put(id("test:rice_cook"),parse("{type:'farmersdelight:cooking',ingredients:[{item:'farmersdelight:rice'}],result:{item:'farmersdelight:cooked_rice'}}"));
        UnificationRecipes.apply(recipes,with);
        var meal=recipes.get(id("test:tomato_meal")).getAsJsonObject();
        check(meal.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString().equals("kaleidoscope_cookery:tomato")
                &&meal.getAsJsonArray("ingredients").get(1).getAsJsonObject().get("item").getAsString().equals("kaleidoscope_cookery:tomato"),"both forgotten exact tomato ingredients point to visible winner");
        check(meal.getAsJsonObject("result").get("count").getAsInt()==2&&meal.get("cookingtime").getAsInt()==240
                &&meal.get("experience").getAsDouble()==.5&&meal.getAsJsonArray("ingredients").get(2).toString().contains("minecraft:beef"),"fallback preserves other ingredients, amounts and processing");
        check(recipes.get(id("test:rice_meal")).toString().contains("kaleidoscope_cookery:cooked_rice")&&recipes.get(id("test:rice_meal")).toString().contains("kaleidoscope_cookery:fried_egg"),"already-equivalent Compat rice and egg omissions also recover");
        check(recipes.containsKey(id("test:rice_cook")),"required native producer retained rather than deleted as duplicate");
        var stable=recipes.toString();UnificationRecipes.apply(recipes,with);check(recipes.toString().equals(stable),"fallback is idempotent");
        var off=UnificationContent.compile(json,false,all::contains,mod->true);check(off.equals(UnificationContent.EMPTY),"compatibility mode retains all original food without unification");
        check(!with.replacements().containsKey(id("farmersdelight:cabbage_leaf")),"whole cabbage leaf and lettuce are not blindly equated");
        check(with.winner("cuisinejournal:fish_sauce").equals(id("ubesdelight:fish_sauce_bottle")),"Ube fish sauce is the confirmed representative");
        var thaiOnly=UnificationContent.compile(json,true,item->item.equals(id("muffins_thaidelight:fish_sauce_bottle")),mod->true);
        check(thaiOnly.winner("cuisinejournal:fish_sauce").equals(id("muffins_thaidelight:fish_sauce_bottle")),"missing Ube falls back to Thai sauce");
        var sauceRecipes=new LinkedHashMap<ResourceLocation,JsonElement>();
        sauceRecipes.put(id("test:sauce_meal"),parse("{type:'farmersdelight:cooking',ingredients:[{item:'muffins_thaidelight:fish_sauce_bottle'}],result:{item:'test:meal',count:2},cookingtime:200}"));
        sauceRecipes.put(id("test:lower_sauce"),parse("{type:'minecraft:crafting_shapeless',ingredients:[{item:'minecraft:cod'},{item:'minecraft:glass_bottle'}],result:{item:'muffins_thaidelight:fish_sauce_bottle'}}"));
        UnificationRecipes.apply(sauceRecipes,with);
        check(sauceRecipes.get(id("test:sauce_meal")).toString().contains("ubesdelight:fish_sauce_bottle"),"downstream sauce input uses the winner");
        check(!sauceRecipes.containsKey(id("test:lower_sauce")),"ordinary duplicate sauce producer follows existing priority policy");
        System.out.println("CompatFallbackTest: "+checks+" checks passed");
    }
    private static JsonElement parse(String json){return JsonParser.parseString(json);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
