package com.chi__you.cuisinejournal.compat;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import static com.chi__you.cuisinejournal.compat.UnificationContent.id;

/** Processing blocks and storage bags have different recipes and native yield loss. */
public final class UnificationTeaBlocks {
    private UnificationTeaBlocks() {}
    public static void recipe(ResourceLocation key,JsonObject recipe,UnificationContent.Plan plan){
        if(!plan.enabled()||!Set.of(id("herbalbrews:green_tea_leaf_block"),id("herbalbrews:dried_green_tea_leaf_block")).contains(key))return;
        if(!recipe.has("pattern")||!recipe.get("pattern").isJsonArray())return;
        var pattern=recipe.getAsJsonArray("pattern");
        if(pattern.size()==3&&pattern.get(0).getAsString().equals("XXX")&&pattern.get(1).getAsString().equals("XXX")&&pattern.get(2).getAsString().equals("XXX"))
            pattern.set(1,new JsonPrimitive("X X"));
    }
    public static int loot(Map<ResourceLocation,JsonElement> tables,UnificationContent.Plan plan){
        if(!plan.enabled()||plan.winner("cuisinejournal:tea_fresh")==null)return 0;
        int changed=0;
        for(String path:List.of("green_tea_leaf_block","mixed_tea_leaf_block")){
            var key=id("herbalbrews:blocks/"+path);var original=tables.get(key);
            if(original==null||!original.isJsonObject())continue;
            var copy=original.deepCopy();
            boolean edited=rewrite(copy,path);
            if(path.equals("mixed_tea_leaf_block"))edited|=splitMixedPools(copy.getAsJsonObject());
            if(edited){tables.put(key,copy);changed++;}
        }
        return changed;
    }
    private static boolean splitMixedPools(JsonObject table){
        if(!table.has("pools"))return false;boolean changed=false;var pools=new JsonArray();
        for(var value:table.getAsJsonArray("pools")){
            if(!value.isJsonObject()){pools.add(value);continue;}var pool=value.getAsJsonObject();
            if(!pool.has("entries")||pool.getAsJsonArray("entries").size()!=2){pools.add(pool);continue;}
            var names=new HashSet<String>();
            for(var entry:pool.getAsJsonArray("entries"))if(entry.isJsonObject()&&entry.getAsJsonObject().has("name"))names.add(entry.getAsJsonObject().get("name").getAsString());
            if(!names.equals(Set.of("herbalbrews:dried_green_tea","herbalbrews:dried_black_tea"))){pools.add(pool);continue;}
            // Two item entries in one loot pool are alternatives, not a refund of both ingredients.
            for(var entry:pool.getAsJsonArray("entries")){
                var separate=pool.deepCopy();var items=new JsonArray();items.add(entry.deepCopy());separate.add("entries",items);pools.add(separate);
            }
            changed=true;
        }
        if(changed)table.add("pools",pools);return changed;
    }
    private static boolean rewrite(JsonElement raw,String block){
        boolean changed=false;
        if(raw.isJsonArray())for(var child:raw.getAsJsonArray())changed|=rewrite(child,block);
        else if(raw.isJsonObject()){
            var object=raw.getAsJsonObject();
            if(object.has("name")&&object.get("name").isJsonPrimitive()
                    &&object.get("name").getAsString().equals("herbalbrews:green_tea_leaf")){
                if(block.equals("mixed_tea_leaf_block")){object.addProperty("name","herbalbrews:dried_green_tea");changed=true;}
                else if(object.has("functions"))for(var function:object.getAsJsonArray("functions")){
                    if(!function.isJsonObject())continue;var row=function.getAsJsonObject();
                    if(row.has("function")&&row.get("function").getAsString().equals("minecraft:set_count")
                            &&row.has("count")&&row.get("count").isJsonPrimitive()&&row.get("count").getAsInt()==9){row.addProperty("count",8);changed=true;}
                }
            }
            for(var e:object.entrySet())if(e.getValue().isJsonArray()||e.getValue().isJsonObject())changed|=rewrite(e.getValue(),block);
        }
        return changed;
    }
}
