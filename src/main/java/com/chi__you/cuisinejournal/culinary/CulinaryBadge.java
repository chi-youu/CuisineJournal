package com.chi__you.cuisinejournal.culinary;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Cosmetic honor definitions have no separate unlocking conditions. */
public record CulinaryBadge(ResourceLocation id, ResourceLocation icon) {
    private static volatile Map<ResourceLocation,CulinaryBadge> definitions=Map.of();
    public String translationKey(){return "cuisinejournal.badge."+id.getNamespace()+"."+id.getPath();}
    public static CulinaryBadge get(ResourceLocation id){return id == null ? null : definitions.get(id);}
    public static ResourceLocation icon(ResourceLocation id){var badge=get(id);return badge==null
            ||!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(badge.icon())
            ?ResourceLocation.parse("minecraft:gold_nugget"):badge.icon();}
    public static Collection<CulinaryBadge> all(){return definitions.values();}
    public static void replace(Collection<CulinaryBadge> values){
        var map=new LinkedHashMap<ResourceLocation,CulinaryBadge>();
        values.stream().sorted(java.util.Comparator.comparing(v->v.id().toString()))
                .forEach(value -> map.put(value.id(), value));
        definitions=Map.copyOf(map);
    }
    public static void clear(){definitions=Map.of();}
    public static CulinaryBadge fromJson(ResourceLocation id,JsonObject json){
        return new CulinaryBadge(id,ResourceLocation.parse(json.has("icon")?json.get("icon").getAsString():"minecraft:gold_nugget"));
    }
}
