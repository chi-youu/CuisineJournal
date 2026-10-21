package com.chi__you.cuisinejournal.skills;

import com.chi__you.cuisinejournal.network.CuisineJournalPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public final class SkillPackets {
    private SkillPackets() {}
    public record Nutrition(int food,float saturation) implements CuisineJournalPacket {
        public static final ResourceLocation ID=ResourceLocation.parse("cuisinejournal:skill_nutrition");
        public ResourceLocation id(){return ID;}
        public void write(FriendlyByteBuf b){b.writeVarInt(food);b.writeFloat(saturation);}
        public static Nutrition read(FriendlyByteBuf b){return new Nutrition(b.readVarInt(),b.readFloat());}
    }
    /** The journal's "技能树" button; the server opens the Pufferfish's Skills screen. */
    public record Open() implements CuisineJournalPacket {
        public static final ResourceLocation ID = ResourceLocation.parse("cuisinejournal:open_skills");
        public ResourceLocation id() { return ID; }
        public void write(FriendlyByteBuf b) { }
        public static Open read(FriendlyByteBuf b) { return new Open(); }
    }

    public record Sync(boolean active, int starCap, double eat, double perfect, double floor,
                       double batch, float shield, double ordinaryDuration, int hungerCap, int saturationCap) implements CuisineJournalPacket {
        public Sync(boolean active,int starCap,double eat,double perfect,double floor,double batch,float shield,double ordinaryDuration) {
            this(active,starCap,eat,perfect,floor,batch,shield,ordinaryDuration,20,20);
        }
        public Sync(boolean active,int starCap,double eat,double perfect,double floor,double batch,float shield) {
            this(active,starCap,eat,perfect,floor,batch,shield,1);
        }
        public static final ResourceLocation ID = ResourceLocation.parse("cuisinejournal:skills");
        public ResourceLocation id() { return ID; }
        public void write(FriendlyByteBuf b) {
            b.writeBoolean(active); b.writeVarInt(starCap); b.writeDouble(eat); b.writeDouble(perfect);
            b.writeDouble(floor); b.writeDouble(batch); b.writeFloat(shield);b.writeDouble(ordinaryDuration);
            b.writeVarInt(hungerCap);b.writeVarInt(saturationCap);
        }
        public static Sync read(FriendlyByteBuf b) {
            return new Sync(b.readBoolean(),b.readVarInt(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readFloat(),b.readDouble(),b.readVarInt(),b.readVarInt());
        }
    }
}
