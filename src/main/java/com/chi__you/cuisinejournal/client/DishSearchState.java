package com.chi__you.cuisinejournal.client;

import com.chi__you.cuisinejournal.network.DishSearchPackets;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public final class DishSearchState {
    private static final Map<ResourceLocation,DishSearchPackets.Entry> ENTRIES=new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile long revision;
    private DishSearchState() {}
    public static void accept(DishSearchPackets.Sync packet){if(packet.first())ENTRIES.clear();packet.entries().forEach(entry->ENTRIES.put(entry.item(),entry));revision++;}
    public static DishSearchPackets.Entry get(ResourceLocation item){return ENTRIES.get(item);}
    public static long revision(){return revision;}
    public static void clear(){ENTRIES.clear();revision++;}
}
