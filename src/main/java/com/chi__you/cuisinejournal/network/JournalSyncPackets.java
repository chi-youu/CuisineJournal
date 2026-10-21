package com.chi__you.cuisinejournal.network;

import com.chi__you.cuisinejournal.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

/** Static graph and compact player deltas share a directory generation, not a large combined packet. */
public final class JournalSyncPackets {
    private JournalSyncPackets() {}
    private static ResourceLocation id(String path) { return new ResourceLocation(Constants.MOD_ID, path); }
    private static int count(FriendlyByteBuf buffer) {
        return PacketSafety.count(buffer,100_000);
    }

    public record IngredientKey(ResourceLocation id, boolean tag) {}
    public record IngredientProgress(ResourceLocation id, boolean tag, boolean known, long used,
                                     long first, long last, int discovered, List<ResourceLocation> methods) {
        public IngredientProgress { methods = List.copyOf(methods); }
        public IngredientKey key() { return new IngredientKey(id, tag); }
        public static IngredientProgress of(Packets.IngredientView value) {
            return new IngredientProgress(value.item(), value.tag(), value.known(), value.timesUsed(),
                    value.firstUsedDay(), value.lastUsedDay(), value.discoveredCount(), value.methods());
        }
        public Packets.IngredientView apply(Packets.IngredientView value) {
            return new Packets.IngredientView(value.item(), known, used, first, last, value.mealCount(), discovered,
                    value.kind(), value.types(), value.flavor(), value.effect(), methods, value.meals(), value.tag(),
                    value.members(), value.tagIds(), value.displayName(), value.tagSources(), value.tagUses(), value.flavorInfo());
        }
        void write(FriendlyByteBuf b) {
            b.writeResourceLocation(id); b.writeBoolean(tag); b.writeBoolean(known); b.writeVarLong(used);
            b.writeLong(first); b.writeLong(last); b.writeVarInt(discovered);
            b.writeVarInt(methods.size()); methods.forEach(b::writeResourceLocation);
        }
        static IngredientProgress read(FriendlyByteBuf b) {
            var id = b.readResourceLocation(); boolean tag = b.readBoolean(), known = b.readBoolean();
            long used = b.readVarLong(), first = b.readLong(), last = b.readLong(); int discovered = b.readVarInt();
            List<ResourceLocation> methods = new ArrayList<>();
            for (int i = count(b); i > 0; i--) methods.add(b.readResourceLocation());
            return new IngredientProgress(id, tag, known, used, first, last, discovered, methods);
        }
    }

    public record CollectionProgress(ResourceLocation id, int discovered, int mastered) {
        public Packets.CollectionView apply(Packets.CollectionView value) {
            return new Packets.CollectionView(value.id(), value.titleKey(), value.literalTitle(), value.icon(),
                    value.total(), discovered, mastered, value.subcategories(), value.isModCategory());
        }
        void write(FriendlyByteBuf b) { b.writeResourceLocation(id); b.writeVarInt(discovered); b.writeVarInt(mastered); }
        static CollectionProgress read(FriendlyByteBuf b) {
            return new CollectionProgress(b.readResourceLocation(), b.readVarInt(), b.readVarInt());
        }
    }

    public record Directory(long generation, List<Packets.CollectionView> collections,
                            List<Packets.IngredientView> ingredients, int totalIngredients) implements CuisineJournalPacket {
        public static final ResourceLocation ID = JournalSyncPackets.id("journal_directory");
        public Directory { collections = List.copyOf(collections); ingredients = List.copyOf(ingredients); }
        public ResourceLocation id() { return ID; }
        public void write(FriendlyByteBuf b) {
            b.writeLong(generation); b.writeVarInt(collections.size()); collections.forEach(v -> v.write(b));
            b.writeVarInt(ingredients.size()); ingredients.forEach(v -> v.write(b)); b.writeVarInt(totalIngredients);
        }
        public static Directory read(FriendlyByteBuf b) {
            long generation = b.readLong(); List<Packets.CollectionView> collections = new ArrayList<>();
            for (int i = count(b); i > 0; i--) collections.add(Packets.CollectionView.read(b));
            List<Packets.IngredientView> ingredients = new ArrayList<>();
            for (int i = count(b); i > 0; i--) ingredients.add(Packets.IngredientView.read(b));
            return new Directory(generation, collections, ingredients, b.readVarInt());
        }
    }

    public record Progress(long generation, long sequence, boolean full, List<CollectionProgress> collections,
                           List<IngredientProgress> ingredients, List<Packets.ChallengeView> challenges,
                           List<Packets.MethodView> methods, List<Packets.ActivityView> activity) implements CuisineJournalPacket {
        public static final ResourceLocation ID = JournalSyncPackets.id("journal_progress");
        public Progress {
            collections = List.copyOf(collections); ingredients = List.copyOf(ingredients);
            challenges = List.copyOf(challenges); methods = List.copyOf(methods); activity = List.copyOf(activity);
        }
        public ResourceLocation id() { return ID; }
        public void write(FriendlyByteBuf b) {
            b.writeLong(generation); b.writeLong(sequence); b.writeBoolean(full);
            b.writeVarInt(collections.size()); collections.forEach(v -> v.write(b));
            b.writeVarInt(ingredients.size()); ingredients.forEach(v -> v.write(b));
            b.writeVarInt(challenges.size()); challenges.forEach(v -> v.write(b));
            b.writeVarInt(methods.size()); methods.forEach(v -> v.write(b));
            b.writeVarInt(activity.size()); activity.forEach(v -> v.write(b));
        }
        public static Progress read(FriendlyByteBuf b) {
            long generation = b.readLong(), sequence = b.readLong(); boolean full = b.readBoolean();
            List<CollectionProgress> collections = new ArrayList<>();
            for (int i = count(b); i > 0; i--) collections.add(CollectionProgress.read(b));
            List<IngredientProgress> ingredients = new ArrayList<>();
            for (int i = count(b); i > 0; i--) ingredients.add(IngredientProgress.read(b));
            List<Packets.ChallengeView> challenges = new ArrayList<>();
            for (int i = count(b); i > 0; i--) challenges.add(Packets.ChallengeView.read(b));
            List<Packets.MethodView> methods = new ArrayList<>();
            for (int i = count(b); i > 0; i--) methods.add(Packets.MethodView.read(b));
            List<Packets.ActivityView> activity = new ArrayList<>();
            for (int i = count(b); i > 0; i--) activity.add(Packets.ActivityView.read(b));
            return new Progress(generation, sequence, full, collections, ingredients, challenges, methods, activity);
        }
    }

    public record Request() implements CuisineJournalPacket {
        public static final ResourceLocation ID = JournalSyncPackets.id("request_journal_directory");
        public ResourceLocation id() { return ID; }
        public void write(FriendlyByteBuf b) {}
        public static Request read(FriendlyByteBuf b) { return new Request(); }
    }

    public record TagRequest(long generation, ResourceLocation tag) implements CuisineJournalPacket {
        public static final ResourceLocation ID = JournalSyncPackets.id("request_journal_tag");
        public ResourceLocation id() { return ID; }
        public void write(FriendlyByteBuf b) { b.writeLong(generation); b.writeResourceLocation(tag); }
        public static TagRequest read(FriendlyByteBuf b) { return new TagRequest(b.readLong(), b.readResourceLocation()); }
    }

    public record TagDetails(long generation, ResourceLocation tag, List<Packets.IngredientTagSource> sources,
                             List<Packets.IngredientTagUse> uses) implements CuisineJournalPacket {
        public static final ResourceLocation ID = JournalSyncPackets.id("journal_tag_details");
        public TagDetails { sources = List.copyOf(sources); uses = List.copyOf(uses); }
        public ResourceLocation id() { return ID; }
        public void write(FriendlyByteBuf b) {
            b.writeLong(generation); b.writeResourceLocation(tag);
            b.writeVarInt(sources.size()); sources.forEach(v -> v.write(b));
            b.writeVarInt(uses.size()); uses.forEach(v -> v.write(b));
        }
        public static TagDetails read(FriendlyByteBuf b) {
            long generation = b.readLong(); var tag = b.readResourceLocation();
            List<Packets.IngredientTagSource> sources = new ArrayList<>();
            for (int i = count(b); i > 0; i--) sources.add(Packets.IngredientTagSource.read(b));
            List<Packets.IngredientTagUse> uses = new ArrayList<>();
            for (int i = count(b); i > 0; i--) uses.add(Packets.IngredientTagUse.read(b));
            return new TagDetails(generation, tag, sources, uses);
        }
        public Packets.IngredientView apply(Packets.IngredientView value) {
            return new Packets.IngredientView(value.item(), value.known(), value.timesUsed(), value.firstUsedDay(),
                    value.lastUsedDay(), value.mealCount(), value.discoveredCount(), value.kind(), value.types(),
                    value.flavor(), value.effect(), value.methods(), value.meals(), value.tag(), value.members(),
                    value.tagIds(), value.displayName(), sources, uses, value.flavorInfo());
        }
    }
}
