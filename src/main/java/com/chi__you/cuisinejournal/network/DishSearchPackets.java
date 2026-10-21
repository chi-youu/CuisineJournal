package com.chi__you.cuisinejournal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Search-only dish metadata, independent of details and the selected survival mode. */
public final class DishSearchPackets {
    public record Entry(ResourceLocation item,List<ResourceLocation> flavors,List<ResourceLocation> effects) {
        public Entry { flavors=List.copyOf(flavors);effects=List.copyOf(effects); }
    }
    public record Sync(boolean first,List<Entry> entries) implements CuisineJournalPacket {
        public static final ResourceLocation ID=ResourceLocation.parse("cuisinejournal:dish_search");
        public Sync { entries=List.copyOf(entries); }
        @Override public ResourceLocation id(){return ID;}
        @Override public void write(FriendlyByteBuf buffer) {
            buffer.writeBoolean(first);buffer.writeVarInt(entries.size());
            for(var entry:entries){buffer.writeResourceLocation(entry.item());writeIds(buffer,entry.flavors());writeIds(buffer,entry.effects());}
        }
        public static Sync read(FriendlyByteBuf buffer) {
            boolean first=buffer.readBoolean();int count=buffer.readVarInt();
            if(count<0||count>64)throw new IllegalArgumentException("Invalid dish search batch");
            List<Entry> entries=new ArrayList<>();
            for(int i=0;i<count;i++)entries.add(new Entry(buffer.readResourceLocation(),readIds(buffer),readIds(buffer)));
            return new Sync(first,entries);
        }
    }
    private static void writeIds(FriendlyByteBuf buffer,List<ResourceLocation> ids){buffer.writeVarInt(ids.size());ids.forEach(buffer::writeResourceLocation);}
    private static List<ResourceLocation> readIds(FriendlyByteBuf buffer) {
        int count=buffer.readVarInt();if(count<0||count>256)throw new IllegalArgumentException("Invalid search metadata count");
        List<ResourceLocation> result=new ArrayList<>();for(int i=0;i<count;i++)result.add(buffer.readResourceLocation());return result;
    }
}
