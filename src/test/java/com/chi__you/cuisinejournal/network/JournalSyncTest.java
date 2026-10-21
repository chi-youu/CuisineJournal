package com.chi__you.cuisinejournal.network;

import com.chi__you.cuisinejournal.client.ClientJournalState;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JournalSyncTest {
    private static int checks;
    private static final ResourceLocation ITEM = id("ingredient"), ROOT = id("category");
    public static void main(String[] args) throws Exception {
        fragments(); largeDirectory(); progress(); directoryCache(); earlyExtras(); contracts();
        System.out.println("JournalSyncTest: " + checks + " checks passed");
    }
    private static void fragments() {
        var clock = new AtomicLong(); List<String> failures = new ArrayList<>();
        var receiver = new PacketFragments.Receiver<String>(clock::get, failures::add);
        for (int size : new int[]{0, 1, PacketFragments.CHUNK_BYTES, PacketFragments.CHUNK_BYTES + 1, 2_500_001}) {
            byte[] bytes = new byte[size]; new Random(size).nextBytes(bytes);
            var frames = new ArrayList<>(PacketFragments.split("packet", size + 1L, bytes));
            Collections.reverse(frames); byte[] result = null;
            for (int i = 0; i < frames.size(); i++) {
                result = receiver.accept(frames.get(i));
                if (i + 1 < frames.size()) check(result == null, "partial packet is never visible");
            }
            check(Arrays.equals(result, bytes), "empty/boundary/large packet exact reconstruction");
            check(receiver.reservedBytes() == 0, "completed transfer releases memory");
            check(receiver.accept(frames.get(0)) == null, "completed duplicate is ignored");
        }
        byte[] bytes = new byte[PacketFragments.CHUNK_BYTES + 8];
        var old = PacketFragments.split("replace", 1, bytes);
        var next = PacketFragments.split("replace", 2, bytes);
        check(receiver.accept(old.get(0)) == null, "start old transfer");
        receiver.accept(next.get(1));
        check(receiver.accept(old.get(1)) == null, "stale fragments cannot complete new transfer");
        check(Arrays.equals(receiver.accept(next.get(0)), bytes), "new transfer completes independently");
        var duplicate = PacketFragments.split("duplicate", 1, bytes);
        receiver.accept(duplicate.get(0)); receiver.accept(duplicate.get(0));
        check(Arrays.equals(receiver.accept(duplicate.get(1)), bytes), "identical duplicate is idempotent");
        var conflict = PacketFragments.split("conflict", 1, bytes);
        receiver.accept(conflict.get(0));
        byte[] changed = conflict.get(0).data().clone(); changed[0] = 1;
        rejects(() -> receiver.accept(new PacketFragments.Frame<>("conflict", 1, bytes.length, 0, changed)), "conflicting duplicate");
        receiver.accept(PacketFragments.split("timeout", 1, bytes).get(0));
        clock.set(PacketFragments.TIMEOUT_MILLIS); receiver.expire();
        check(failures.contains("timeout") && receiver.reservedBytes() == 0, "timeout frees incomplete payload");
        rejects(() -> receiver.accept(new PacketFragments.Frame<>("bad", 1, -1, 0, new byte[0])), "negative total");
        rejects(() -> receiver.accept(new PacketFragments.Frame<>("bad", 1, PacketFragments.MAX_BYTES + 1, 0, new byte[0])), "oversized total");
        rejects(() -> receiver.accept(new PacketFragments.Frame<>("bad", 1, 1, 2, new byte[1])), "invalid index");
        rejects(() -> receiver.accept(new PacketFragments.Frame<>("bad", 1, 1, 0, new byte[0])), "truncated fragment");
        receiver.accept(old.get(0)); receiver.clear();
        check(receiver.reservedBytes() == 0, "disconnect clears transfer state");
        byte[] large = new byte[PacketFragments.MAX_BYTES];
        receiver.accept(PacketFragments.split("limit1", 1, large).get(0));
        receiver.accept(PacketFragments.split("limit2", 1, large).get(0));
        receiver.accept(PacketFragments.split("limit3", 1, bytes).get(0));
        check(failures.contains("limit3") && receiver.reservedBytes() == PacketFragments.MAX_PENDING_BYTES,
                "aggregate reassembly allocation is bounded");
        receiver.clear();
    }
    private static Packets.IngredientView ingredient(List<Packets.IngredientTagUse> uses) {
        return new Packets.IngredientView(ITEM, false, 0, Long.MIN_VALUE, Long.MIN_VALUE, 1, 0,
                (byte)3, List.of(), false, null, List.of(), List.of(id("dish")), true, List.of(ITEM),
                List.of(ITEM), "Ingredient", List.of(), uses, Packets.FlavorInfo.NONE);
    }
    private static JournalSyncPackets.Directory directory(long generation) {
        return new JournalSyncPackets.Directory(generation,
                List.of(new Packets.CollectionView(ROOT, "Category", true, ITEM, 1, 0, 0)),
                List.of(ingredient(List.of())), 1);
    }
    private static void largeDirectory() {
        List<ResourceLocation> members = new ArrayList<>();
        for (int i = 0; i < 5000; i++) members.add(id("very_long_accepted_ingredient_" + i));
        List<Packets.IngredientTagUse> uses = new ArrayList<>();
        for (int i = 0; i < 12; i++) uses.add(new Packets.IngredientTagUse(id("dish_" + i), id("recipe_" + i), i, ITEM, members));
        var original = new JournalSyncPackets.Directory(1, directory(1).collections(), List.of(ingredient(uses)), 1);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            original.write(buffer); byte[] bytes = new byte[buffer.readableBytes()]; buffer.readBytes(bytes);
            check(bytes.length > 1_048_576, "real tag graph fixture exceeds Minecraft single-packet limit");
            FriendlyByteBuf delta = new FriendlyByteBuf(Unpooled.buffer());
            try {
                progress(1, 2, false, 4).write(delta);
                check(delta.readableBytes() < 256, "player delta does not repeat tag graph");
                System.out.println("JournalSync sizes: directory=" + bytes.length + " bytes; delta=" + delta.readableBytes() + " bytes");
            } finally { delta.release(); }
            var receiver = new PacketFragments.Receiver<ResourceLocation>(() -> 0L, key -> { throw new AssertionError(key); });
            byte[] complete = null;
            for (var frame : PacketFragments.split(original.id(), 1, bytes)) {
                check(frame.data().length <= 262144, "each outgoing fragment stays below payload limit");
                complete = receiver.accept(frame);
            }
            var decoded = new FriendlyByteBuf(Unpooled.wrappedBuffer(complete));
            try {
                check(original.equals(Network.decode(original.id(), decoded)), "multi-MiB directory round trip preserves graph");
                check(!decoded.isReadable(), "directory consumes all bytes");
            } finally { decoded.release(); }
        } finally { buffer.release(); }
    }
    private static void directoryCache() {
        JournalSyncServer.clear();
        var registry = com.chi__you.cuisinejournal.recipe.CulinaryRegistry.EMPTY;
        var used = new JournalSyncPackets.IngredientProgress(ITEM, true, true, 77, 1, 2, 1, List.of(id("method")));
        var use = new Packets.IngredientTagUse(id("dish"), id("recipe"), 0, ITEM, List.of(id("accepted")));
        var initial = JournalSyncServer.directory(registry, () -> new Packets.SyncJournalExtras(List.of(),
                List.of(new Packets.CollectionView(ROOT, "Category", true, ITEM, 1, 1, 1)),
                List.of(used.apply(ingredient(List.of(use)))), List.of(), List.of(), 1));
        check(!initial.ingredients().get(0).known() && initial.ingredients().get(0).timesUsed() == 0
                && initial.ingredients().get(0).methods().isEmpty(), "shared directory never leaks first player's progress");
        check(initial.collections().get(0).discovered() == 0 && initial.collections().get(0).mastered() == 0,
                "shared categories contain no player counts");
        check(initial.ingredients().get(0).tagUses().isEmpty(), "expensive tag graph omitted from eager directory");
        var details = JournalSyncServer.tagDetails(initial.generation(), ITEM);
        check(details != null && details.uses().equals(List.of(use)), "lazy lookup retains full tag recipe graph");
        check(JournalSyncServer.tagDetails(initial.generation() - 1, ITEM) == null
                && JournalSyncServer.tagDetails(initial.generation(), id("unknown")) == null,
                "stale and unknown tag requests rejected");
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
        try {
            details.write(b);
            check(details.equals(Network.decode(details.id(), b)), "tag details wire round trip");
            var request = new JournalSyncPackets.TagRequest(initial.generation(), ITEM); request.write(b);
            check(request.equals(Network.decode(request.id(), b)), "tag request wire round trip");
        } finally { b.release(); }
        var cached = JournalSyncServer.directory(registry, () -> { throw new AssertionError("unchanged graph rebuilt"); });
        check(cached == initial, "multiple players and progress updates reuse static graph");
        JournalSyncServer.clear();
        var reloaded = JournalSyncServer.directory(registry, () -> new Packets.SyncJournalExtras(List.of(),
                List.of(), List.of(), List.of(), List.of(), 0));
        check(reloaded.generation() > initial.generation() && reloaded.ingredients().isEmpty(),
                "invalidating cache produces a new generation and removes deleted entries");
        JournalSyncServer.clear();
    }
    private static JournalSyncPackets.Progress progress(long generation, long sequence, boolean full, long used) {
        return new JournalSyncPackets.Progress(generation, sequence, full,
                List.of(new JournalSyncPackets.CollectionProgress(ROOT, 1, 0)),
                List.of(new JournalSyncPackets.IngredientProgress(ITEM, true, true, used, 1, 2, 0, List.of(id("method")))),
                List.of(), List.of(), List.of());
    }
    private static void progress() {
        ClientJournalState.clear();
        check(!ClientJournalState.directoryReady(), "initial state is loading, not empty");
        ClientJournalState.acceptDirectory(directory(1));
        check(!ClientJournalState.directoryReady(), "directory waits for matching player snapshot");
        var initial = progress(1, 1, true, 3);
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
        try {
            initial.write(b);
            check(initial.equals(Network.decode(initial.id(), b)), "progress wire round trip");
        } finally { b.release(); }
        ClientJournalState.acceptProgress(initial);
        check(ClientJournalState.directoryReady() && ClientJournalState.ingredients().get(0).timesUsed() == 3, "atomic initial commit");
        ClientJournalState.acceptProgress(progress(1, 2, false, 4));
        check(ClientJournalState.ingredients().get(0).timesUsed() == 4, "compact delta applied");
        check(ClientJournalState.ingredients().get(0).members().equals(List.of(ITEM)), "delta preserves static members");
        var lazyUse = new Packets.IngredientTagUse(id("dish"), id("recipe"), 0, ITEM, List.of(ITEM));
        var details = new JournalSyncPackets.TagDetails(1, ITEM, List.of(), List.of(lazyUse));
        ClientJournalState.acceptTagDetails(details);
        check(ClientJournalState.ingredients().get(0).tagUses().equals(List.of(lazyUse))
                && ClientJournalState.ingredients().get(0).timesUsed() == 4, "lazy response preserves player progress");
        ClientJournalState.acceptProgress(progress(1, 3, false, 4));
        check(ClientJournalState.ingredients().get(0).tagUses().equals(List.of(lazyUse)), "player delta preserves lazy graph");
        ClientJournalState.acceptProgress(progress(1, 1, false, 1));
        check(ClientJournalState.ingredients().get(0).timesUsed() == 4, "stale delta ignored");
        ClientJournalState.acceptDirectory(directory(2));
        check(ClientJournalState.ingredients().get(0).timesUsed() == 4, "reload retains old view until complete");
        ClientJournalState.acceptDirectory(directory(1));
        ClientJournalState.acceptProgress(progress(1, 4, false, 55));
        check(ClientJournalState.ingredients().get(0).timesUsed() == 4, "old directory and progress cannot cancel pending reload");
        ClientJournalState.acceptProgress(progress(2, 1, true, 9));
        check(ClientJournalState.ingredients().get(0).timesUsed() == 9, "new registry atomically replaces old graph");
        ClientJournalState.acceptTagDetails(details);
        check(ClientJournalState.ingredients().get(0).tagUses().isEmpty(), "old lazy response cannot contaminate new graph");
        ClientJournalState.acceptProgress(progress(1, 999, true, 99));
        check(ClientJournalState.ingredients().get(0).timesUsed() == 9, "old registry cannot overwrite reload");
        ClientJournalState.acceptProgress(progress(2, 3, false, 20));
        check(ClientJournalState.directoryFailed() && ClientJournalState.ingredients().get(0).timesUsed() == 9,
                "missing delta requests recovery without clearing data");
        ClientJournalState.acceptDirectory(directory(2)); ClientJournalState.acceptProgress(progress(2, 1, true, 11));
        check(!ClientJournalState.directoryFailed() && ClientJournalState.ingredients().get(0).timesUsed() == 11,
                "full recovery resets delta baseline");
        ClientJournalState.clear();
        check(!ClientJournalState.directoryReady() && ClientJournalState.ingredients().isEmpty(), "disconnect clears catalogue progress");
    }
    private static void earlyExtras() throws Exception {
        JournalSyncServer.clear(); ClientJournalState.clear();
        var registry = com.chi__you.cuisinejournal.recipe.CulinaryRegistries.current();
        var graph = JournalSyncServer.directory(registry, () -> new Packets.SyncJournalExtras(
                List.of(), List.of(), List.of(), List.of(), List.of(), 0));
        var profile = new com.chi__you.cuisinejournal.culinary.CulinaryProfile(UUID.randomUUID());
        List<CuisineJournalPacket> sent = new ArrayList<>();
        List<CuisineJournalPacket> requests = new ArrayList<>();
        Network.useTransport(new Network.Transport() {
            public void toPlayer(net.minecraft.server.level.ServerPlayer player, CuisineJournalPacket packet) {
                sent.add(packet);
                if (packet instanceof Packets.SyncCatalogue value) ClientJournalState.acceptCatalogue(value);
                else if (packet instanceof JournalSyncPackets.Directory value) ClientJournalState.acceptDirectory(value);
                else if (packet instanceof JournalSyncPackets.Progress value) ClientJournalState.acceptProgress(value);
            }
            public void toServer(CuisineJournalPacket packet) { requests.add(packet); }
        });
        try {
            JournalSyncServer.send(null, profile, com.chi__you.cuisinejournal.mastery.MasteryCurve.DEFAULT,
                    graph, List.of(), List.of(), List.of(), false);
            check(sent.size() == 3 && sent.get(0) instanceof Packets.SyncCatalogue
                    && sent.get(1) instanceof JournalSyncPackets.Directory
                    && sent.get(2) instanceof JournalSyncPackets.Progress,
                    "early skill extras sends base catalogue before directory and progress");
            check(ClientJournalState.isCatalogueComplete() && ClientJournalState.directoryReady(),
                    "early extras cannot leave every page behind catalogue loading gate");
            sent.clear();
            JournalSyncServer.send(null, profile, com.chi__you.cuisinejournal.mastery.MasteryCurve.DEFAULT,
                    graph, List.of(), List.of(), List.of(), true);
            check(sent.size() == 1 && sent.get(0) instanceof JournalSyncPackets.Progress,
                    "normal journal refresh does not repeat static catalogues");
            ClientJournalState.clear();
            ClientJournalState.acceptDirectory(graph);
            ClientJournalState.acceptProgress(new JournalSyncPackets.Progress(graph.generation(), 1, true,
                    List.of(), List.of(), List.of(), List.of(), List.of()));
            ClientJournalState.beginJournalSync();
            var started = ClientJournalState.class.getDeclaredField("syncStarted");
            started.setAccessible(true);
            check(started.getLong(null) != 0, "missing base catalogue starts recovery timer even when extras are ready");
            ClientJournalState.acceptProgress(new JournalSyncPackets.Progress(graph.generation(), 2, true,
                    List.of(), List.of(), List.of(), List.of(), List.of()));
            check(started.getLong(null) != 0, "extras refresh cannot cancel missing-catalogue recovery timer");
            started.setLong(null, System.nanoTime() / 1_000_000 - 30_001);
            ClientJournalState.tickSync();
            check(ClientJournalState.directoryFailed() && requests.size() == 1
                    && requests.get(0) instanceof JournalSyncPackets.Request,
                    "missing base catalogue requests full recovery instead of loading forever");
            JournalSyncServer.forget(null); sent.clear(); ClientJournalState.clear();
            JournalSyncServer.send(null, profile, com.chi__you.cuisinejournal.mastery.MasteryCurve.DEFAULT,
                    graph, List.of(), List.of(), List.of(), false);
            check(ClientJournalState.isCatalogueComplete() && ClientJournalState.directoryReady()
                    && sent.get(0) instanceof Packets.SyncCatalogue,
                    "recovery and reconnect resend both catalogue layers");
        } finally {
            Network.useTransport(null); JournalSyncServer.clear(); ClientJournalState.clear();
        }
    }
    private static void contracts() throws Exception {
        String transport = Files.readString(Path.of("src/main/java/com/chi__you/cuisinejournal/forge/ForgeNetwork.java"));
        check(transport.contains("PacketFragments.split") && transport.contains("Optional.of(NetworkDirection.PLAY_TO_CLIENT)"),
                "all server packets use bounded client-only fragments");
        String handler = Files.readString(Path.of("src/main/java/com/chi__you/cuisinejournal/network/ServerPacketHandler.java"));
        check(handler.contains("JournalSyncServer.directory(registry, () ->") && handler.contains("JournalSyncServer.send("),
                "production path caches graph and sends progress separately");
        String server = Files.readString(Path.of("src/main/java/com/chi__you/cuisinejournal/network/JournalSyncServer.java"));
        check(server.contains("registry != current") && server.contains("ServerPacketHandler.sendCatalogue(player)")
                        && server.contains("Network.toPlayer(player, graph)"),
                "directory sent only for first connection or changed graph");
        check(server.contains("changedIngredients") && server.contains("changedCollections") && server.contains("WeakHashMap"),
                "player deltas and connection-lifetime baselines wired");
        check(server.contains("forget(player); ServerPacketHandler.sendExtras(player)")
                        && !handler.contains("if (directoryChanged) sendCatalogue(player)"),
                "resync and early extras share the single complete-directory sender");
        String screen = Files.readString(Path.of("src/main/java/com/chi__you/cuisinejournal/client/journal/JournalScreen.java"));
        check(screen.contains("ClientJournalState.directoryFailed()") && screen.contains("cuisinejournal.sync.failed"),
                "base catalogue loading gate exposes exhausted recovery instead of hiding failure");
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("test", path); }
    private static void rejects(Runnable body, String message) {
        try { body.run(); throw new AssertionError(message); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
