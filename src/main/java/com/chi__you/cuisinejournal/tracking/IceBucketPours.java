package com.chi__you.cuisinejournal.tracking;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who poured a mix into a Trail &amp; Tales ice bucket.
 *
 * <p>The bucket is not a recipe station: the mix goes into block state, ageing runs on
 * random ticks, and only the frozen bucket yields ice cream. The pour is the cooking
 * input but not a finished dish. Freezing creates a serving batch; successful serving
 * settles each output for a contributor, with no offline-cook dependency.</p>
 */
public final class IceBucketPours extends SavedData {
    /** @param mix the poured item, kept so the finished dish can list its ingredient */
    public record Pour(UUID player, String name, ResourceLocation mix, String frozen, int remaining) {
        public Pour(UUID player, String name, ResourceLocation mix) { this(player,name,mix,"",0); }
    }

    private static final String DATA_NAME = "cuisinejournal_ice_bucket_pours";
    private final Map<Long, Pour> entries = new HashMap<>();
    public static void queueRemoval(ServerLevel level,BlockPos pos){
        var data=level.getDataStorage().get(IceBucketPours::load,DATA_NAME);
        if(data!=null)PlacedDataCleanup.defer(data.entries,pos.asLong(),data);
    }

    private static IceBucketPours get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(IceBucketPours::load, IceBucketPours::new, DATA_NAME);
    }

    public static IceBucketPours load(CompoundTag tag) {
        IceBucketPours data = new IceBucketPours();
        for (Tag value : tag.getList("pours", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) value;
            if (!row.hasUUID("player")) continue;
            ResourceLocation mix = ResourceLocation.tryParse(row.getString("mix"));
            data.entries.put(row.getLong("pos"), new Pour(row.getUUID("player"), row.getString("name"), mix,
                    row.getString("frozen"), row.getInt("remaining")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        entries.forEach((pos, pour) -> {
            CompoundTag row = new CompoundTag();
            row.putLong("pos", pos);
            row.putUUID("player", pour.player());
            row.putString("name", pour.name());
            row.putString("frozen", pour.frozen());
            row.putInt("remaining", pour.remaining());
            if (pour.mix() != null) row.putString("mix", pour.mix().toString());
            list.add(row);
        });
        tag.put("pours", list);
        return tag;
    }

    public static void record(ServerLevel level, BlockPos pos, UUID player, String name, ResourceLocation mix) {
        IceBucketPours data = get(level);
        data.entries.put(pos.asLong(), new Pour(player, name == null ? "" : name, mix));
        data.setDirty();
    }

    public static void forget(ServerLevel level, BlockPos pos) {
        IceBucketPours data = get(level);
        if (data.entries.remove(pos.asLong()) != null) data.setDirty();
    }

    public static void restore(ServerLevel level, BlockPos pos, Pour pour) {
        if (pour == null) return;
        IceBucketPours data = get(level);
        data.entries.put(pos.asLong(), pour);
        data.setDirty();
    }

    public static Pour peek(ServerLevel level, BlockPos pos) { return get(level).entries.get(pos.asLong()); }

    /** Only the host's completed state transition promotes the poured mix to servings. */
    public static void frozen(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState before) {
        Pour pour = peek(level,pos);
        var after = level.getBlockState(pos);
        if (pour == null || !pour.frozen().isEmpty() || !after.getBlock().getClass().getName()
                .equals(com.chi__you.foodinteractions.TrailIceCreamRelations.FROZEN)) return;
        try {
            for (var relation : com.chi__you.foodinteractions.TrailIceCreamRelations.read(before.getBlock())) {
                if (relation.frozen() == after.getBlock() && relation.mix().equals(pour.mix())) {
                    restore(level,pos,new Pour(pour.player(),pour.name(),pour.mix(),
                            net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(after.getBlock()).toString(),relation.servings()));
                    return;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            com.chi__you.cuisinejournal.CuisineJournalLog.LOGGER.warn("Cannot track frozen ice bucket at {}",pos,failure);
        }
        forget(level,pos);
    }

    public static void removed(net.minecraft.world.level.Level level, BlockPos pos,
                               net.minecraft.world.level.block.state.BlockState before,
                               net.minecraft.world.level.block.state.BlockState after) {
        if (!(level instanceof ServerLevel server) || before.getBlock() == after.getBlock()) return;
        String old = before.getBlock().getClass().getName(), next = after.getBlock().getClass().getName();
        if (old.equals(com.chi__you.foodinteractions.TrailIceCreamRelations.EMPTY)
                && next.equals(com.chi__you.foodinteractions.TrailIceCreamRelations.FROZEN)) return;
        if (old.equals(com.chi__you.foodinteractions.TrailIceCreamRelations.EMPTY)
                || old.equals(com.chi__you.foodinteractions.TrailIceCreamRelations.FROZEN)) forget(server,pos);
    }
    public static void claimed(ServerLevel level, BlockPos pos, Pour pour, int count) {
        // The last serving resets the block and clears the old record before delivery.
        Pour current=peek(level,pos);
        if (current == null || !current.player().equals(pour.player()) || !current.frozen().equals(pour.frozen())) return;
        int rest = current.remaining() - Math.max(0,count);
        if (rest <= 0) forget(level,pos);
        else restore(level,pos,new Pour(current.player(),current.name(),current.mix(),current.frozen(),rest));
    }
}
