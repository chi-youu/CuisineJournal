package com.chi__you.cuisinejournal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public final class JournalFeaturePackets {
    private JournalFeaturePackets() {}
    private static ResourceLocation id(String name){return ResourceLocation.parse("cuisinejournal:"+name);}
    private static void optional(FriendlyByteBuf b,ResourceLocation id){b.writeBoolean(id!=null);if(id!=null)b.writeResourceLocation(id);}
    private static ResourceLocation optional(FriendlyByteBuf b){return b.readBoolean()?b.readResourceLocation():null;}
    public record Track(ResourceLocation dish,ResourceLocation recipe,int target) implements CuisineJournalPacket {
        public static final ResourceLocation ID=JournalFeaturePackets.id("track_recipe");public ResourceLocation id(){return ID;}
        public void write(FriendlyByteBuf b){b.writeResourceLocation(dish);optional(b,recipe);b.writeVarInt(target);}
        public static Track read(FriendlyByteBuf b){return new Track(b.readResourceLocation(),optional(b),b.readVarInt());}
    }
    public record Signature(ResourceLocation dish) implements CuisineJournalPacket {
        public static final ResourceLocation ID=JournalFeaturePackets.id("select_signature");public ResourceLocation id(){return ID;}
        public void write(FriendlyByteBuf b){optional(b,dish);}public static Signature read(FriendlyByteBuf b){return new Signature(optional(b));}
    }
    public record Slot(List<ResourceLocation> items,ResourceLocation tag,int needed) {
        public Slot {items=List.copyOf(items);}
        void write(FriendlyByteBuf b){b.writeVarInt(items.size());items.forEach(b::writeResourceLocation);optional(b,tag);b.writeVarInt(needed);}
        static Slot read(FriendlyByteBuf b){int n=b.readVarInt();if(n<1||n>8192)throw new IllegalArgumentException("Invalid alternatives");
            var items=new ArrayList<ResourceLocation>();for(int i=0;i<n;i++)items.add(b.readResourceLocation());
            var tag=optional(b);int count=b.readVarInt();if(count<1||count>1_000_000)throw new IllegalArgumentException("Invalid requirement");return new Slot(items,tag,count);}
    }
    public record Tracked(ResourceLocation dish,ResourceLocation recipe,ResourceLocation method,int target,int output,List<Slot> slots) {
        public Tracked {slots=List.copyOf(slots);}
        void write(FriendlyByteBuf b){b.writeResourceLocation(dish);b.writeResourceLocation(recipe);optional(b,method);b.writeVarInt(target);b.writeVarInt(output);b.writeVarInt(slots.size());slots.forEach(s->s.write(b));}
        static Tracked read(FriendlyByteBuf b){var dish=b.readResourceLocation();var recipe=b.readResourceLocation();var method=optional(b);int target=b.readVarInt(),output=b.readVarInt(),n=b.readVarInt();
            if(target<1||target>4096||output<1||n<0||n>64)throw new IllegalArgumentException("Invalid tracked recipe");
            var slots=new ArrayList<Slot>();for(int i=0;i<n;i++)slots.add(Slot.read(b));return new Tracked(dish,recipe,method,target,output,slots);}
    }
    /** {@code trackLimit}: the server's maximum number of tracked dishes, so the client can disable the pin when full. */
    public record Sync(ResourceLocation signature,boolean chosen,long lastChange,long day,double bonus,int cost,
                       boolean eligible,List<Tracked> tracks,int trackLimit) implements CuisineJournalPacket {
        public static final ResourceLocation ID=JournalFeaturePackets.id("journal_features");public ResourceLocation id(){return ID;}
        public Sync {tracks=List.copyOf(tracks);}
        public void write(FriendlyByteBuf b){optional(b,signature);b.writeBoolean(chosen);b.writeLong(lastChange);b.writeLong(day);b.writeDouble(bonus);b.writeVarInt(cost);b.writeBoolean(eligible);b.writeVarInt(tracks.size());tracks.forEach(t->t.write(b));b.writeVarInt(trackLimit);}
        public static Sync read(FriendlyByteBuf b){var signature=optional(b);boolean chosen=b.readBoolean();long last=b.readLong(),day=b.readLong();double bonus=b.readDouble();int cost=b.readVarInt();boolean eligible=b.readBoolean();int n=b.readVarInt();
            if(n<0||n>16)throw new IllegalArgumentException("Invalid tracks");var tracks=new ArrayList<Tracked>();for(int i=0;i<n;i++)tracks.add(Tracked.read(b));
            int limit=b.readVarInt();if(limit<1||limit>16)throw new IllegalArgumentException("Invalid track limit");
            return new Sync(signature,chosen,last,day,bonus,cost,eligible,tracks,limit);}
    }
}
