package com.chi__you.cuisinejournal.recipe;

import com.chi__you.cuisinejournal.compat.HostReflection;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import java.util.*;

/** Fixed skewers are native data, not RecipeManager recipes. Preserve each selector and slot. */
public final class GrillingRecipes {
    public static final String RAW_TENTACLES="cuisinejournal:raw_squid_tentacles";
    private GrillingRecipes() {}
    public static void repairSquid(JsonObject root){
        if(!root.has("skewers")||!root.get("skewers").isJsonArray())return;
        for(var entry:root.getAsJsonArray("skewers"))if(entry.isJsonObject()){
            var row=entry.getAsJsonObject();
            if(!row.has("id")||!row.get("id").getAsString().equals("kaleidoscope_grilling:raw_squid_tentacle_skewer"))continue;
            if(!row.has("ingredients"))continue;
            for(var slot:row.getAsJsonArray("ingredients"))if(slot.isJsonArray()){
                var selectors=slot.getAsJsonArray();
                for(int i=0;i<selectors.size();i++)if(selectors.get(i).isJsonPrimitive()
                        &&Set.of("#kaleidoscope_grilling:ingredients/squid_tentacles","kaleidoscope_grilling:squid_tentacle").contains(selectors.get(i).getAsString()))
                    selectors.set(i,new JsonPrimitive("#"+RAW_TENTACLES));
            }
        }
    }
    public static List<RecipeEntry> preparations(){
        if(!BuiltInRegistries.ITEM.containsKey(id("kaleidoscope_grilling:raw_squid_tentacle_skewer")))return List.of();
        try{
            Object raw=Class.forName("cn.breezeth.kaleidoscope_grilling.data.GrillingDataManager").getMethod("skewersForDisplay").invoke(null);
            if(!(raw instanceof Map<?,?> values))return List.of();
            var result=new ArrayList<RecipeEntry>();
            for(var entry:values.entrySet()){
                var uncooked=ResourceLocation.tryParse(entry.getKey().toString());if(uncooked==null)continue;
                Object selectors=HostReflection.invoke(entry.getValue(),"ingredients");
                var slots=new ArrayList<RecipeEntry.IngredientSlot>();
                if(selectors instanceof List<?> list)for(var input:list){
                    if(!(input instanceof List<?> alternatives)){slots.clear();break;}
                    var slot=selectors(alternatives);if(slot.items().isEmpty()){slots.clear();break;}slots.add(slot);
                }
                if(!slots.isEmpty()){
                    slots.add(0,new RecipeEntry.IngredientSlot(List.of(BuiltInRegistries.ITEM.getKey(Items.STICK))));
                    Object threaded=HostReflection.invoke(entry.getValue(),"threadingResult");
                    var target=threaded instanceof String s&&!s.isBlank()?ResourceLocation.tryParse(s):uncooked;
                    add(result,target,"threading",slots);
                }
                Object cooked=HostReflection.invoke(entry.getValue(),"cookedResult");
                var target=cooked instanceof String s&&!s.isBlank()?ResourceLocation.tryParse(s):null;
                if(target!=null&&BuiltInRegistries.ITEM.containsKey(uncooked))
                    add(result,target,"grilling",List.of(new RecipeEntry.IngredientSlot(List.of(uncooked))));
            }
            return List.copyOf(result);
        }catch(ReflectiveOperationException|LinkageError failure){
            throw new IllegalStateException("Cannot read installed fixed skewer recipes",failure);
        }
    }
    private static RecipeEntry.IngredientSlot selectors(List<?> alternatives){
        var items=new LinkedHashSet<ResourceLocation>();var direct=new LinkedHashSet<ResourceLocation>();var tags=new LinkedHashSet<ResourceLocation>();
        for(var value:alternatives){
            String text=String.valueOf(value);boolean tag=text.startsWith("#");var id=ResourceLocation.tryParse(tag?text.substring(1):text);if(id==null)continue;
            if(tag){tags.add(id);BuiltInRegistries.ITEM.getTag(ItemTags.create(id)).ifPresent(members->members.forEach(item->items.add(BuiltInRegistries.ITEM.getKey(item.value()))));}
            else if(BuiltInRegistries.ITEM.containsKey(id)){direct.add(id);items.add(id);}
        }
        items.removeIf(HostItemAvailability::hiddenByUnification);
        return new RecipeEntry.IngredientSlot(List.copyOf(items),List.copyOf(direct),List.copyOf(tags));
    }
    private static void add(List<RecipeEntry> result,ResourceLocation output,String method,List<RecipeEntry.IngredientSlot> inputs){
        if(output==null||!BuiltInRegistries.ITEM.containsKey(output)||HostItemAvailability.hiddenByUnification(output))return;
        var type=id("kaleidoscope_grilling:"+method);
        result.add(new RecipeEntry(id("cuisinejournal:grilling/"+method+"/"+output.getNamespace()+"/"+output.getPath()),
                type,CookingMethod.of(type),output,1,List.copyOf(inputs),output.getNamespace()));
    }
    private static ResourceLocation id(String value){return ResourceLocation.parse(value);}
}
