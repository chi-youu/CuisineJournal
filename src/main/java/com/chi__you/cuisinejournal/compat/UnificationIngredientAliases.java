package com.chi__you.cuisinejournal.compat;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.Predicate;
import static com.chi__you.cuisinejournal.compat.UnificationContent.id;

/** One-to-many recipe inputs must not collapse every colored or cut ingredient into one item. */
public final class UnificationIngredientAliases {
    public static final String BELL_TAG="forge:crops/bell_pepper";
    private UnificationIngredientAliases() {}
    static void configure(Map<String,ResourceLocation> winners,Map<ResourceLocation,ResourceLocation> replacements,
                          Set<ResourceLocation> excluded,Set<String> plants,Predicate<ResourceLocation> exists){
        if(winners.containsKey("cuisinejournal:eggplant")&&exists.test(id("culturaldelights:smoked_eggplant"))
                &&exists.test(id("culturaldelights:smoked_cut_eggplant"))&&exists.test(id("crockpot:cooked_eggplant"))){
            winners.put("cuisinejournal:smoked_eggplant",id("culturaldelights:smoked_eggplant"));
            replacements.put(id("crockpot:cooked_eggplant"),id("culturaldelights:smoked_eggplant"));
        }
        if(exists.test(id("rusticdelight:bell_pepper_green"))&&exists.test(id("veggiesdelight:bellpepper"))){
            winners.put("cuisinejournal:bell_pepper",id("rusticdelight:bell_pepper_green"));
            for(String item:List.of("bellpepper","bellpepper_seeds","bellpepper_crate"))
                if(exists.test(id("veggiesdelight:"+item)))excluded.add(id("veggiesdelight:"+item));
            replacements.put(id("veggiesdelight:bellpepper"),id("rusticdelight:bell_pepper_green"));
            if(exists.test(id("rusticdelight:bell_pepper_seeds")))
                replacements.put(id("veggiesdelight:bellpepper_seeds"),id("rusticdelight:bell_pepper_seeds"));
            plants.add("veggiesdelight:wild_bellpeppers");
        }
    }
    public static void inputs(JsonObject recipe,ResourceLocation recipeId,UnificationContent.Plan plan){
        for(String field:List.of("ingredient","ingredients","key","input","inputs","requirements"))
            if(recipe.has(field))recipe.add(field,ingredient(recipe.get(field),recipeId.getNamespace().equals("crockpot"),plan));
    }
    private static JsonElement ingredient(JsonElement raw,boolean crockpot,UnificationContent.Plan plan){
        if(raw.isJsonArray()){
            var result=new JsonArray();for(var value:raw.getAsJsonArray())result.add(ingredient(value,crockpot,plan));return result;
        }
        if(!raw.isJsonObject())return raw;var object=raw.getAsJsonObject().deepCopy();
        if(plan.winner("cuisinejournal:bell_pepper")!=null&&string(object,"item").equals("veggiesdelight:bellpepper")){
            object.remove("item");object.addProperty("tag",BELL_TAG);return object;
        }
        if(plan.winner("cuisinejournal:bell_pepper")!=null
                &&Set.of("forge:crops/bellpepper","forge:vegetables/bellpepper").contains(string(object,"tag")))object.addProperty("tag",BELL_TAG);
        if(crockpot&&plan.winner("cuisinejournal:smoked_eggplant")!=null
                &&string(object,"item").equals("crockpot:cooked_eggplant")){
            var choices=new JsonArray();
            for(String path:List.of("smoked_eggplant","smoked_cut_eggplant")){
                var copy=object.deepCopy();copy.addProperty("item","culturaldelights:"+path);choices.add(copy);
            }
            return choices;
        }
        for(var e:List.copyOf(object.entrySet()))if(!Set.of("nbt","conditions").contains(e.getKey()))
            object.add(e.getKey(),ingredient(e.getValue(),crockpot,plan));
        return object;
    }
    private static String string(JsonObject object,String name){
        var value=object.get(name);return value!=null&&value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()?value.getAsString():"";
    }
}
