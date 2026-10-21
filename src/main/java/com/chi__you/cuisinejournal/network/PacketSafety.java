package com.chi__you.cuisinejournal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;

/** Validate direction before decoding any nested client-controlled payload. */
public final class PacketSafety {
    public static final int MAX_REQUEST_BYTES = 1_048_576;
    private PacketSafety() {}
    public static boolean serverbound(ResourceLocation id) {
        return CLIENT_REQUESTS.contains(id);
    }
    private static final Set<ResourceLocation> CLIENT_REQUESTS = Set.of(
            JournalSyncPackets.Request.ID, JournalSyncPackets.TagRequest.ID,
            JournalFeaturePackets.Track.ID, JournalFeaturePackets.Signature.ID,
            com.chi__you.cuisinejournal.skills.SkillPackets.Open.ID,
            com.chi__you.cuisinejournal.food.CuisineFoodInfoPackets.FruitRequest.ID,
            com.chi__you.cuisinejournal.food.CuisineFoodInfoPackets.HostRequest.ID,
            Packets.RequestProfile.ID, Packets.RequestDishDetail.ID,
            Packets.SetPreference.ID, Packets.SetServerSetting.ID,
            Packets.CategoryEditModeRequest.ID, Packets.CategoryEditRequest.ID,
            Packets.FlavorEditRequest.ID, Packets.FlavorPreviewRequest.ID, Packets.SchemeRequest.ID);
    public static boolean allowed(ResourceLocation id, boolean toServer) {
        return toServer ? serverbound(id) : !serverbound(id) && known(id);
    }
    private static boolean known(ResourceLocation id) {
        for (var candidate : Network.packetIds()) if (candidate.equals(id)) return true;
        return false;
    }
    public static int count(FriendlyByteBuf buffer, int maximum) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum || count > buffer.readableBytes())
            throw new IllegalArgumentException("Invalid collection count: " + count);
        return count;
    }
    public static int size(int count,int maximum){
        if(count<0||count>maximum)throw new IllegalArgumentException("Outgoing collection exceeds supported count: "+count+" > "+maximum);
        return count;
    }
}
