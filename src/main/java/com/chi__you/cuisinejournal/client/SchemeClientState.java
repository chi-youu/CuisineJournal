package com.chi__you.cuisinejournal.client;

import com.chi__you.cuisinejournal.network.Packets;

import java.util.List;

/** Client copy of the flavor / category schemes and the last scheme action's result. */
public final class SchemeClientState {
    private static volatile Packets.SyncSchemes schemes;
    private static final java.util.Map<Integer,Packets.SchemeResult> RESULTS = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile long revision;

    private SchemeClientState() {
    }

    public static void accept(Packets.SyncSchemes packet) { EditClientState.accept(packet.versions()); schemes = packet; revision++; }
    public static void accept(Packets.SchemeResult result) {
        if(RESULTS.size()>=128)RESULTS.clear();RESULTS.put(result.requestId(),result);revision++;
    }

    public static long revision() { return revision; }
    public static boolean canEdit() { return schemes != null && schemes.canEdit(); }

    public static List<Packets.SchemeView> list(boolean category) {
        Packets.SyncSchemes value = schemes;
        return value == null ? List.of() : category ? value.category() : value.flavor();
    }

    public static String current(boolean category) {
        Packets.SyncSchemes value = schemes;
        return value == null ? "" : category ? value.currentCategory() : value.currentFlavor();
    }

    public static boolean dirty(boolean category) {
        Packets.SyncSchemes value = schemes;
        return value != null && (category ? value.dirtyCategory() : value.dirtyFlavor());
    }

    /** Counts of the current (unsaved) local layer, in the same order as {@link Packets.SchemeView#counts()}. */
    public static List<Integer> localCounts(boolean category) {
        Packets.SyncSchemes value = schemes;
        return value == null ? List.of() : category ? value.localCategory() : value.localFlavor();
    }

    public static Packets.SchemeResult take(int requestId) {
        return RESULTS.remove(requestId);
    }

    public static void clear() { schemes = null; RESULTS.clear(); revision++; }
}
