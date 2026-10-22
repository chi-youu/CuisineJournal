package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.network.CuisineJournalPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Server-authoritative remaining recovery delay; does not change food or health. */
public final class CuisineRegenPackets {
    private CuisineRegenPackets() {}

    public record Combat(int ticksLeft, int intervalTicks) implements CuisineJournalPacket {
        public Combat(int ticksLeft) { this(ticksLeft, 5); }
        public static final ResourceLocation ID = ResourceLocation.parse("cuisinejournal:regen_combat");
        public Combat {
            if (ticksLeft < 0 || ticksLeft > 72000) throw new IllegalArgumentException("Invalid recovery delay");
            if (intervalTicks < 1 || intervalTicks > 500) throw new IllegalArgumentException("Invalid regeneration interval");
        }
        public ResourceLocation id() { return ID; }
        public void write(FriendlyByteBuf buffer) { buffer.writeVarInt(ticksLeft); buffer.writeVarInt(intervalTicks); }
        public static Combat read(FriendlyByteBuf buffer) { return new Combat(buffer.readVarInt(), buffer.readVarInt()); }
    }
}
