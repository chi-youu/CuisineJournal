package com.chi__you.cuisinejournal.recipe;

import com.google.gson.JsonParser;
import com.chi__you.cuisinejournal.CuisineJournalLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.Reader;
import java.util.*;

/** Copper-cup identities follow the host mod's datapack conversions. */
public final class DishServingVariants {
    private static volatile Map<ResourceLocation,ResourceLocation> current=Map.of();
    public static Runnable rollbackPoint() { var previous=current; return () -> current=previous; }
    private DishServingVariants() {}
    public static void reload(ResourceManager resources) {
        Map<ResourceLocation,ResourceLocation> loaded=new LinkedHashMap<>();
        resources.listResources("cup_conversion",id->id.getNamespace().equals("miners_delight")&&id.getPath().endsWith(".json"))
                .entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(file->{
            try(Reader reader=file.getValue().openAsReader()) {
                var json=JsonParser.parseReader(reader).getAsJsonObject();
                for(var raw:json.getAsJsonArray("entries")) {
                    var row=raw.getAsJsonObject();var bowl=ResourceLocation.parse(row.get("bowl").getAsString());
                    var cup=ResourceLocation.parse(row.get("cup").getAsString());if(!cup.equals(bowl))loaded.put(cup,bowl);
                }
            } catch(Exception failure){CuisineJournalLog.LOGGER.warn("Skipping cup conversions {}: {}",file.getKey(),failure.getMessage());}
        });
        current=Map.copyOf(loaded);
    }
    public static ResourceLocation target(ResourceLocation item) {
        if(item==null)return null;
        ResourceLocation original=current.get(item);
        return original!=null&&CulinaryRegistries.current().targets().contains(original)?original:item;
    }
    public static ResourceLocation target(ResourceLocation item,Set<ResourceLocation> tracked) {
        if (item == null) return null;
        ResourceLocation original=current.get(item);return original!=null&&tracked.contains(original)?original:item;
    }
    public static Map<ResourceLocation,ResourceLocation> all(){return current;}
}
