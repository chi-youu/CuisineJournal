package com.chi__you.cuisinejournal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Small, independently deliverable notification when a larger transfer cannot be sent. */
public record SyncFailure(ResourceLocation failedPacket) implements CuisineJournalPacket {
    public static final ResourceLocation ID=ResourceLocation.parse("cuisinejournal:sync_failure");
    public ResourceLocation id(){return ID;}
    public void write(FriendlyByteBuf buffer){buffer.writeResourceLocation(failedPacket);}
    public static SyncFailure read(FriendlyByteBuf buffer){return new SyncFailure(buffer.readResourceLocation());}
}
