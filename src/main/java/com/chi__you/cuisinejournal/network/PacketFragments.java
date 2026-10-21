package com.chi__you.cuisinejournal.network;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Bounded atomic reassembly for server-to-client packets. */
public final class PacketFragments {
    public static final int CHUNK_BYTES = 256 * 1024;
    public static final int MAX_BYTES = 32 * 1024 * 1024;
    public static final int MAX_PENDING_BYTES = 64 * 1024 * 1024;
    public static final long TIMEOUT_MILLIS = 30_000;
    private PacketFragments() {}

    public record Frame<K>(K key, long transfer, int totalBytes, int index, byte[] data) {
        public int count() { return Math.max(1, (totalBytes + CHUNK_BYTES - 1) / CHUNK_BYTES); }
        public void validate() {
            if (key == null || transfer <= 0 || totalBytes < 0 || totalBytes > MAX_BYTES
                    || index < 0 || index >= count() || data == null
                    || data.length != Math.min(CHUNK_BYTES, totalBytes - index * CHUNK_BYTES))
                throw new IllegalArgumentException("Invalid packet fragment");
        }
    }

    public static <K> List<Frame<K>> split(K key, long transfer, byte[] payload) {
        if (payload.length > MAX_BYTES) throw new IllegalArgumentException("Journal packet exceeds 32 MiB");
        List<Frame<K>> frames = new ArrayList<>();
        for (int offset = 0; offset < payload.length || frames.isEmpty(); offset += CHUNK_BYTES) {
            var frame = new Frame<>(key, transfer, payload.length, offset / CHUNK_BYTES,
                    Arrays.copyOfRange(payload, offset, Math.min(offset + CHUNK_BYTES, payload.length)));
            frame.validate(); frames.add(frame);
        }
        return List.copyOf(frames);
    }

    public static final class Receiver<K> {
        private final LongSupplier clock;
        private final Consumer<K> failed;
        private final Map<K, Pending> pending = new HashMap<>();
        private final Map<K, Long> latest = new LinkedHashMap<>();
        private int reserved;
        public Receiver(LongSupplier clock, Consumer<K> failed) { this.clock = clock; this.failed = failed; }

        public byte[] accept(Frame<K> frame) {
            frame.validate(); expire();
            long seen = latest.getOrDefault(frame.key(), 0L);
            Pending current = pending.get(frame.key());
            if (frame.transfer() < seen || frame.transfer() == seen && current == null) return null;
            if (frame.transfer() > seen) {
                drop(frame.key()); latest.put(frame.key(), frame.transfer());
                if (latest.size() > 256) {
                    K oldest = latest.keySet().iterator().next(); drop(oldest); latest.remove(oldest);
                }
                if (pending.size() >= 8 || frame.totalBytes() > MAX_PENDING_BYTES - reserved) {
                    failed.accept(frame.key()); return null;
                }
                current = new Pending(frame.totalBytes(), frame.count(), clock.getAsLong());
                pending.put(frame.key(), current); reserved += frame.totalBytes();
            }
            if (current.total != frame.totalBytes()) {
                drop(frame.key()); failed.accept(frame.key());
                throw new IllegalArgumentException("Fragment size changed within a transfer");
            }
            int offset = frame.index() * CHUNK_BYTES;
            if (current.received[frame.index()]) {
                for (int i = 0; i < frame.data().length; i++) if (current.bytes[offset + i] != frame.data()[i]) {
                    drop(frame.key()); failed.accept(frame.key());
                    throw new IllegalArgumentException("Conflicting duplicate fragment");
                }
                return null;
            }
            System.arraycopy(frame.data(), 0, current.bytes, offset, frame.data().length);
            current.received[frame.index()] = true;
            if (++current.parts != current.received.length) return null;
            byte[] result = current.bytes; drop(frame.key()); return result;
        }

        public void expire() {
            long now = clock.getAsLong();
            List<K> expired = new ArrayList<>();
            Iterator<Map.Entry<K, Pending>> iterator = pending.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (now - entry.getValue().started < TIMEOUT_MILLIS) continue;
                reserved -= entry.getValue().total; expired.add(entry.getKey()); iterator.remove();
            }
            expired.forEach(failed);
        }
        public void clear() { pending.clear(); latest.clear(); reserved = 0; }
        public int reservedBytes() { return reserved; }
        private void drop(K key) { Pending value = pending.remove(key); if (value != null) reserved -= value.total; }
        private static final class Pending {
            final long started;
            final int total;
            final byte[] bytes;
            final boolean[] received;
            int parts;
            Pending(int total, int count, long started) {
                this.total = total; this.started = started; bytes = new byte[total]; received = new boolean[count];
            }
        }
    }
}
