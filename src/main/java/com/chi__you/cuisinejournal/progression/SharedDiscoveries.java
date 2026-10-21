package com.chi__you.cuisinejournal.progression;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** World-level discovery ledger; team names are vanilla scoreboard identities. */
public final class SharedDiscoveries extends SavedData {
    private final Map<String,Set<ResourceLocation>> groups = new TreeMap<>();

    public Set<ResourceLocation> entries(String group) {
        return group == null ? Set.of() : Set.copyOf(groups.getOrDefault(group,Set.of()));
    }

    public boolean add(String group, ResourceLocation dish) {
        if (group == null || dish == null || !groups.computeIfAbsent(group,key->new TreeSet<>()).add(dish)) return false;
        setDirty();
        return true;
    }

    public static SharedDiscoveries load(CompoundTag tag) {
        var data = new SharedDiscoveries();
        for (String group : tag.getAllKeys()) {
            var items = tag.getList(group,8);
            for (int i=0;i<items.size();i++) {
                var dish=ResourceLocation.tryParse(items.getString(i));
                if(dish!=null)data.groups.computeIfAbsent(group,key->new TreeSet<>()).add(dish);
            }
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag) {
        groups.forEach((group,items)-> {
            var list=new ListTag();items.forEach(item->list.add(StringTag.valueOf(item.toString())));
            tag.put(group,list);
        });
        return tag;
    }
}
