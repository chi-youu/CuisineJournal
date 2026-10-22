package com.chi__you.cuisinejournal.network;

import com.chi__you.cuisinejournal.CuisineJournalLog;
import com.chi__you.cuisinejournal.food.CuisineFoodSnapshot;
import com.chi__you.cuisinejournal.food.CuisineFoodInfoPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The loader-neutral half of networking: what a packet id decodes to, and how
 * to hand a decoded packet to whoever handles it.
 *
 * <p>Transport lives in the loader modules. Everything else — payloads,
 * decoding, dispatch and the server-side handlers — is here.</p>
 */
public final class Network {

    /** Set by the loader module at startup. */
    private static volatile Transport transport = Transport.NOOP;

    private static final Map<ResourceLocation, Function<FriendlyByteBuf, CuisineJournalPacket>> DECODERS =
            new LinkedHashMap<>();

    static {
        DECODERS.put(SyncFailure.ID,SyncFailure::read);
        DECODERS.put(JournalSyncPackets.Directory.ID, JournalSyncPackets.Directory::read);
        DECODERS.put(JournalSyncPackets.Progress.ID, JournalSyncPackets.Progress::read);
        DECODERS.put(JournalSyncPackets.Request.ID, JournalSyncPackets.Request::read);
        DECODERS.put(JournalSyncPackets.TagRequest.ID, JournalSyncPackets.TagRequest::read);
        DECODERS.put(JournalSyncPackets.TagDetails.ID, JournalSyncPackets.TagDetails::read);
        DECODERS.put(com.chi__you.cuisinejournal.skills.SkillPackets.Nutrition.ID,com.chi__you.cuisinejournal.skills.SkillPackets.Nutrition::read);
        DECODERS.put(JournalFeaturePackets.Track.ID,JournalFeaturePackets.Track::read);
        DECODERS.put(JournalFeaturePackets.Signature.ID,JournalFeaturePackets.Signature::read);
        DECODERS.put(JournalFeaturePackets.Sync.ID,JournalFeaturePackets.Sync::read);
        DECODERS.put(com.chi__you.cuisinejournal.skills.SkillPackets.Sync.ID,com.chi__you.cuisinejournal.skills.SkillPackets.Sync::read);
        DECODERS.put(com.chi__you.cuisinejournal.skills.SkillPackets.Open.ID, com.chi__you.cuisinejournal.skills.SkillPackets.Open::read);
        DECODERS.put(CuisineFoodSnapshot.ID, CuisineFoodSnapshot::read);
        DECODERS.put(com.chi__you.cuisinejournal.food.CuisineRegenPackets.Combat.ID,
                com.chi__you.cuisinejournal.food.CuisineRegenPackets.Combat::read);
        DECODERS.put(CuisineFoodInfoPackets.Batch.ID, CuisineFoodInfoPackets.Batch::read);
        DECODERS.put(CuisineFoodInfoPackets.FruitRequest.ID, CuisineFoodInfoPackets.FruitRequest::read);
        DECODERS.put(CuisineFoodInfoPackets.FruitResponse.ID, CuisineFoodInfoPackets.FruitResponse::read);
        DECODERS.put(CuisineFoodInfoPackets.HostRequest.ID,CuisineFoodInfoPackets.HostRequest::read);
        DECODERS.put(CuisineFoodInfoPackets.HostResponse.ID,CuisineFoodInfoPackets.HostResponse::read);
        DECODERS.put(Packets.SyncCatalogue.ID, Packets.SyncCatalogue::read);
        DECODERS.put(DishSearchPackets.Sync.ID,DishSearchPackets.Sync::read);
        DECODERS.put(Packets.SyncProfile.ID, Packets.SyncProfile::read);
        DECODERS.put(Packets.SyncRules.ID, Packets.SyncRules::read);
        DECODERS.put(Packets.SyncDishDetail.ID, Packets.SyncDishDetail::read);
        DECODERS.put(Packets.SyncJournalExtras.ID, Packets.SyncJournalExtras::read);
        DECODERS.put(Packets.SyncStatistics.ID, Packets.SyncStatistics::read);
        DECODERS.put(Packets.SyncFlavors.ID, Packets.SyncFlavors::read);
        DECODERS.put(Packets.SyncSchemes.ID, Packets.SyncSchemes::read);
        DECODERS.put(Packets.SchemeRequest.ID, Packets.SchemeRequest::read);
        DECODERS.put(Packets.SchemeResult.ID, Packets.SchemeResult::read);
        DECODERS.put(Packets.FlavorEditRequest.ID, Packets.FlavorEditRequest::read);
        DECODERS.put(Packets.FlavorEditResult.ID, Packets.FlavorEditResult::read);
        DECODERS.put(Packets.FlavorPreviewRequest.ID, Packets.FlavorPreviewRequest::read);
        DECODERS.put(Packets.FlavorPreview.ID, Packets.FlavorPreview::read);
        DECODERS.put(Packets.MenuContext.ID, Packets.MenuContext::read);
        DECODERS.put(Packets.Notify.ID, Packets.Notify::read);
        DECODERS.put(Packets.OpenJournal.ID, Packets.OpenJournal::read);
        DECODERS.put(Packets.RequestDishDetail.ID, Packets.RequestDishDetail::read);
        DECODERS.put(Packets.RequestProfile.ID, Packets.RequestProfile::read);
        DECODERS.put(Packets.SyncServerSettings.ID, Packets.SyncServerSettings::read);
        DECODERS.put(Packets.SetServerSetting.ID, Packets.SetServerSetting::read);
        DECODERS.put(Packets.SyncTodayDish.ID,Packets.SyncTodayDish::read);
        DECODERS.put(Packets.SetPreference.ID, Packets.SetPreference::read);
        DECODERS.put(Packets.SyncCategoryPermissions.ID, Packets.SyncCategoryPermissions::read);
        DECODERS.put(Packets.CategoryEditModeRequest.ID, Packets.CategoryEditModeRequest::read);
        DECODERS.put(Packets.SyncCategoryEditMode.ID, Packets.SyncCategoryEditMode::read);
        DECODERS.put(Packets.CategoryEditRequest.ID, Packets.CategoryEditRequest::read);
        DECODERS.put(Packets.CategoryEditResult.ID, Packets.CategoryEditResult::read);
        DECODERS.put(Packets.SyncCategoryEditor.ID, Packets.SyncCategoryEditor::read);
    }

    private Network() {
    }

    /** Implemented once per loader. */
    public interface Transport {
        Transport NOOP = new Transport() {
            @Override
            public void toPlayer(ServerPlayer player, CuisineJournalPacket packet) {
            }

            @Override
            public void toServer(CuisineJournalPacket packet) {
            }
        };

        void toPlayer(ServerPlayer player, CuisineJournalPacket packet);

        void toServer(CuisineJournalPacket packet);
    }

    public static void useTransport(Transport implementation) {
        transport = implementation == null ? Transport.NOOP : implementation;
    }

    public static void toPlayer(ServerPlayer player, CuisineJournalPacket packet) {
        try {
            transport.toPlayer(player, packet);
        } catch (RuntimeException failure) {
            CuisineJournalLog.LOGGER.error("Failed to send {} to {}", packet.id(),
                    player.getGameProfile().getName(), failure);
            if(!(packet instanceof SyncFailure))try {
                transport.toPlayer(player,new SyncFailure(packet.id()));
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("cuisinejournal.sync.too_large",packet.id().toString()));
            }catch(RuntimeException notificationFailure){failure.addSuppressed(notificationFailure);}
        }
    }

    public static void toServer(CuisineJournalPacket packet) {
        try {
            transport.toServer(packet);
        } catch (RuntimeException failure) {
            CuisineJournalLog.LOGGER.error("Failed to send {} to the server", packet.id(), failure);
        }
    }

    public static Iterable<ResourceLocation> packetIds() {
        return DECODERS.keySet();
    }

    /**
     * @return the decoded packet, or {@code null} for an unknown id — which is
     *         normal when a client and server run different builds and must not
     *         be treated as an error
     */
    public static CuisineJournalPacket decode(ResourceLocation id, FriendlyByteBuf buffer) {
        Function<FriendlyByteBuf, CuisineJournalPacket> decoder = DECODERS.get(id);
        if (decoder == null) {
            return null;
        }
        try {
            return decoder.apply(buffer);
        } catch (RuntimeException failure) {
            CuisineJournalLog.LOGGER.warn("Discarding malformed {} packet", id, failure);
            return null;
        }
    }
}
