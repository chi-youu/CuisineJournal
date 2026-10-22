package com.chi__you.cuisinejournal.forge;

import com.chi__you.cuisinejournal.Constants;
import com.chi__you.cuisinejournal.client.ClientPacketHandler;
import com.chi__you.cuisinejournal.network.CuisineJournalPacket;
import com.chi__you.cuisinejournal.network.Network;
import com.chi__you.cuisinejournal.network.ServerPacketHandler;
import com.chi__you.cuisinejournal.network.PacketFragments;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.function.Supplier;

/**
 * Forge transport.
 *
 * <p>Forge's {@code SimpleChannel} wants a registered class per message, which
 * would duplicate the packet list. Instead one envelope carries the packet id
 * and its bytes, and the loader-neutral decoder in {@link Network} does the
 * rest. Packet codecs and fragmentation are separate from Forge transport
 * registration.</p>
 */
public final class ForgeNetwork implements Network.Transport {
    private static final String PROTOCOL = "47";
    private static final java.util.concurrent.atomic.AtomicLong TRANSFERS = new java.util.concurrent.atomic.AtomicLong();
    private static final com.chi__you.cuisinejournal.network.RequestBudget<java.util.UUID> INBOUND =
            new com.chi__you.cuisinejournal.network.RequestBudget<>();

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Constants.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private ForgeNetwork() {
    }

    /** An id plus the payload bytes, so one registration covers every packet. */
    private record Envelope(ResourceLocation id, byte[] payload) {

        static void encode(Envelope envelope, FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(envelope.id());
            buffer.writeByteArray(envelope.payload());
        }

        static Envelope decode(FriendlyByteBuf buffer) {
            return new Envelope(buffer.readResourceLocation(), buffer.readByteArray(
                    com.chi__you.cuisinejournal.network.PacketSafety.MAX_REQUEST_BYTES));
        }
    }

    public static void register() {
        if(registered)return;registered=true;
        CHANNEL.registerMessage(0, Envelope.class, Envelope::encode, Envelope::decode,
                ForgeNetwork::receive);
        CHANNEL.registerMessage(1, Fragment.class, Fragment::encode, Fragment::decode,
                ForgeNetwork::receiveFragment, java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        Network.useTransport(new ForgeNetwork());
        ServerPacketHandler.install();
    }
    private static boolean registered;

    private record Fragment(PacketFragments.Frame<ResourceLocation> frame) {
        static void encode(Fragment value, FriendlyByteBuf buffer) {
            var frame = value.frame(); frame.validate();
            buffer.writeResourceLocation(frame.key()); buffer.writeLong(frame.transfer());
            buffer.writeVarInt(frame.totalBytes()); buffer.writeVarInt(frame.index());
            buffer.writeByteArray(frame.data());
        }
        static Fragment decode(FriendlyByteBuf buffer) {
            var frame = new PacketFragments.Frame<>(buffer.readResourceLocation(), buffer.readLong(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readByteArray(PacketFragments.CHUNK_BYTES));
            frame.validate(); return new Fragment(frame);
        }
    }

    private static final class ClientTransfers {
        static final PacketFragments.Receiver<ResourceLocation> RECEIVER = new PacketFragments.Receiver<>(
                () -> System.nanoTime() / 1_000_000,
                id -> {
                    com.chi__you.cuisinejournal.CuisineJournalLog.LOGGER.warn("Journal sync incomplete: {}", id);
                    com.chi__you.cuisinejournal.client.ClientJournalState.syncFailed(id);
                });
    }

    public static void clientTick() { ClientTransfers.RECEIVER.expire(); }
    public static void clearClientTransfers() { ClientTransfers.RECEIVER.clear(); }
    public static void forgetInbound(java.util.UUID player) { INBOUND.remove(player); }
    public static void clearInbound() { INBOUND.clear(); }

    private static void receiveFragment(Fragment fragment, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        if (context.getDirection() != NetworkDirection.PLAY_TO_CLIENT || FMLEnvironment.dist != Dist.CLIENT) {
            context.setPacketHandled(true); return;
        }
        context.enqueueWork(() -> {
            var frame = fragment.frame();
            if(!com.chi__you.cuisinejournal.network.PacketSafety.allowed(frame.key(),false))return;
            try {
                byte[] complete = ClientTransfers.RECEIVER.accept(frame);
                if (complete == null) return;
                FriendlyByteBuf buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(complete));
                try {
                    var packet = Network.decode(frame.key(), buffer);
                    if (packet == null || buffer.isReadable()) {
                        com.chi__you.cuisinejournal.client.ClientJournalState.syncFailed(frame.key());
                        return;
                    }
                    ClientPacketHandler.handle(packet);
                } finally { buffer.release(); }
            } catch (RuntimeException failure) {
                com.chi__you.cuisinejournal.CuisineJournalLog.LOGGER.warn("Discarding journal transfer {}", frame.key(), failure);
                com.chi__you.cuisinejournal.client.ClientJournalState.syncFailed(frame.key());
            }
        });
        context.setPacketHandled(true);
    }

    private static void receive(Envelope envelope, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        boolean toServer = context.getDirection() == NetworkDirection.PLAY_TO_SERVER;
        if ((context.getDirection() != NetworkDirection.PLAY_TO_SERVER && context.getDirection() != NetworkDirection.PLAY_TO_CLIENT)
                || !com.chi__you.cuisinejournal.network.PacketSafety.allowed(envelope.id(), toServer)
                || toServer && envelope.payload().length > com.chi__you.cuisinejournal.network.PacketSafety.MAX_REQUEST_BYTES) {
            context.getNetworkManager().disconnect(net.minecraft.network.chat.Component.literal("Invalid Cuisine Journal packet direction or size"));
            context.setPacketHandled(true); return;
        }
        if(toServer) {
            ServerPlayer sender=context.getSender();
            if(sender==null || !INBOUND.allow(sender.getUUID(),"queue",System.nanoTime()/1_000_000,0)) {
                context.setPacketHandled(true); return;
            }
        }
        context.enqueueWork(() -> {
            FriendlyByteBuf buffer = new FriendlyByteBuf(
                    io.netty.buffer.Unpooled.wrappedBuffer(envelope.payload()));
            CuisineJournalPacket packet;
            try {
                packet = Network.decode(envelope.id(), buffer);
                if (buffer.isReadable()) packet = null;
            }
            finally { buffer.release(); }
            if (packet == null) {
                context.getNetworkManager().disconnect(net.minecraft.network.chat.Component.literal("Malformed Cuisine Journal packet"));
                return;
            }
            if (context.getDirection() == NetworkDirection.PLAY_TO_SERVER) {
                ServerPlayer sender = context.getSender();
                if (sender != null) {
                    ServerPacketHandler.handle(sender, packet);
                }
            } else if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientPacketHandler.handle(packet);
            }
        });
        context.setPacketHandled(true);
    }

    private static Envelope wrap(CuisineJournalPacket packet) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            packet.write(buffer);
            byte[] payload = new byte[buffer.readableBytes()];
            buffer.readBytes(payload);
            return new Envelope(packet.id(), payload);
        } finally { buffer.release(); }
    }

    @Override
    public void toPlayer(ServerPlayer player, CuisineJournalPacket packet) {
        Envelope envelope = wrap(packet);
        if(envelope.payload().length>PacketFragments.MAX_BYTES) {
            throw new IllegalArgumentException("Cuisine Journal transfer exceeds 32 MiB: "+packet.id());
        }
        for (var frame : PacketFragments.split(envelope.id(), TRANSFERS.incrementAndGet(), envelope.payload()))
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Fragment(frame));
    }

    @Override
    public void toServer(CuisineJournalPacket packet) {
        CHANNEL.sendToServer(wrap(packet));
    }
}
