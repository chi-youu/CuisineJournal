package com.chi__you.cuisinejournal.recipe;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Shared method-to-item display identities; candidates permit missing optional hosts. */
public final class WorkstationDisplay {
    private static final Map<ResourceLocation,List<ResourceLocation>> ICONS=load();
    private WorkstationDisplay() {}
    private static Map<ResourceLocation,List<ResourceLocation>> load(){
        try(var input=WorkstationDisplay.class.getResourceAsStream("/cuisinejournal/workstation_display.json")){
            if(input==null)throw new IllegalStateException("Missing workstation display map");
            var result=new LinkedHashMap<ResourceLocation,List<ResourceLocation>>();
            var json=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            for(var entry:json.entrySet()){
                var items=new ArrayList<ResourceLocation>();entry.getValue().getAsJsonArray().forEach(raw->items.add(ResourceLocation.parse(raw.getAsString())));
                result.put(ResourceLocation.parse(entry.getKey()),List.copyOf(items));
            }
            return Map.copyOf(result);
        }catch(java.io.IOException failure){throw new IllegalStateException("Cannot read workstation display map",failure);}
    }
    public static List<ResourceLocation> candidates(ResourceLocation method){return ICONS.getOrDefault(method,List.of(method));}
}
