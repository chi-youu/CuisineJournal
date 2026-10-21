package com.chi__you.cuisinejournal.food;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** One food or drink currently tracked for a player. */
public final class CuisineFoodEntry {
    public static final String ITEM_ID = "item_id";
    public static final String ITEM_DATA = "item_data";
    public static final String REMAINING_TICKS = "remaining_ticks";
    public static final String SLOT_COST = "slot_cost";
    public static final String ENTRY_ID = "entry_id";
    public static final String TOTAL_TICKS = "total_ticks";
    public static final String TRIGGERED_EFFECTS = "triggered_effects";
    public static final String OWNED_EFFECTS = "owned_effects";

    private ItemStack stack;
    private int remainingTicks;
    private int slotCost;
    private UUID entryId;
    private int totalTicks;
    private final Set<String> triggeredEffects = new HashSet<>();
    private final Set<String> ownedEffects = new HashSet<>();
    private boolean effectsInitialized;
    private final java.util.Map<ResourceLocation, FoodEffectReserve> effectReserves = new java.util.LinkedHashMap<>();

    public CuisineFoodEntry(ItemStack stack, int remainingTicks, int slotCost) {
        this(stack, remainingTicks, slotCost, UUID.randomUUID(), remainingTicks);
    }

    private CuisineFoodEntry(ItemStack stack, int remainingTicks, int slotCost,
                             UUID entryId, int totalTicks) {
        this.stack = stack.copyWithCount(1);
        GrillingFoodCompat.snapshotHot(this.stack);
        this.remainingTicks = Math.max(1, remainingTicks);
        this.slotCost = Math.max(1, slotCost);
        this.entryId = entryId;
        this.totalTicks = Math.max(this.remainingTicks, totalTicks);
    }

    public ItemStack stack() {
        return stack;
    }

    public int remainingTicks() {
        return remainingTicks;
    }

    public int slotCost() {
        return slotCost;
    }

    public UUID entryId() {
        return entryId;
    }

    public int totalTicks() {
        return totalTicks;
    }
    public boolean effectsInitialized() { return effectsInitialized; }
    public void initializeEffects() { effectsInitialized = true; }
    public java.util.Map<ResourceLocation, FoodEffectReserve> effectReserves() { return effectReserves; }

    public boolean markTriggered(ResourceLocation effect) {
        return triggeredEffects.add(effect.toString());
    }

    public boolean wasTriggered(ResourceLocation effect) {
        return triggeredEffects.contains(effect.toString());
    }

    public boolean ownsEffect(ResourceLocation effect) {
        return ownedEffects.contains(effect.toString());
    }

    public void markOwned(ResourceLocation effect) {
        ownedEffects.add(effect.toString());
    }

    public void clearOwned(ResourceLocation effect) {
        ownedEffects.remove(effect.toString());
    }

    public void clearOwnedEffects() {
        ownedEffects.clear();
    }

    /** Transfers live-effect ownership when a slot is replaced by a new entry. */
    public void adoptOwnedEffectsFrom(CuisineFoodEntry previous) {
        ownedEffects.addAll(previous.ownedEffects);
    }

    public boolean isExpired() {
        return remainingTicks <= 0;
    }

    public void tick() {
        advance(1);
    }

    public void advance(int ticks) {
        remainingTicks = CuisineFoodMath.remainingAfterAdvance(remainingTicks, ticks);
        effectReserves.values().forEach(effect -> effect.advance(ticks));
    }

    public void skipNight(long passedTicks) {
        int before=remainingTicks;
        remainingTicks = CuisineFoodMath.remainingAfterSleep(remainingTicks,passedTicks);
        effectReserves.values().forEach(effect -> effect.advance(Math.max(0,before-remainingTicks)));
    }

    public void refresh(ItemStack newStack, int ticks, int newSlotCost) {
        stack = newStack.copyWithCount(1);
        GrillingFoodCompat.snapshotHot(stack);
        remainingTicks = Math.max(1, ticks);
        slotCost = Math.max(1, newSlotCost);
        totalTicks = remainingTicks;
        entryId = UUID.randomUUID();
        triggeredEffects.clear();
        effectReserves.clear(); effectsInitialized = false;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return tag;
        }
        tag.putString(ITEM_ID, id.toString());
        if (stack.hasTag()) {
            tag.put(ITEM_DATA, stack.getTag().copy());
        }
        tag.putInt(REMAINING_TICKS, remainingTicks);
        tag.putInt(SLOT_COST, slotCost);
        tag.putUUID(ENTRY_ID, entryId);
        tag.putInt(TOTAL_TICKS, totalTicks);
        ListTag triggered = new ListTag();
        triggeredEffects.stream().sorted().map(StringTag::valueOf).forEach(triggered::add);
        tag.put(TRIGGERED_EFFECTS, triggered);
        ListTag owned = new ListTag();
        ownedEffects.stream().sorted().map(StringTag::valueOf).forEach(owned::add);
        tag.put(OWNED_EFFECTS, owned);
        tag.putBoolean("effects_initialized", effectsInitialized);
        ListTag reserves = new ListTag();
        effectReserves.forEach((effect, reserve) -> { var row = reserve.save(); row.putString("id", effect.toString()); reserves.add(row); });
        tag.put("effect_reserves", reserves);
        return tag;
    }

    public static CuisineFoodEntry read(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(ITEM_ID));
        if (id == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)
                || net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id) == Items.AIR) {
            return null;
        }
        ItemStack stack = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id));
        if (tag.contains(ITEM_DATA)) {
            stack.setTag(tag.getCompound(ITEM_DATA).copy());
        }
        int ticks = tag.getInt(REMAINING_TICKS);
        int slots = tag.getInt(SLOT_COST);
        if (ticks <= 0 || slots <= 0) {
            return null;
        }
        UUID entryId = tag.hasUUID(ENTRY_ID) ? tag.getUUID(ENTRY_ID) : UUID.randomUUID();
        int totalTicks = tag.contains(TOTAL_TICKS) ? tag.getInt(TOTAL_TICKS) : ticks;
        CuisineFoodEntry entry = new CuisineFoodEntry(stack, ticks, slots, entryId, totalTicks);
        entry.effectsInitialized = tag.getBoolean("effects_initialized");
        for (Tag value : tag.getList("effect_reserves", Tag.TAG_COMPOUND)) {
            if (entry.effectReserves.size() >= 64) break;
            CompoundTag row = (CompoundTag)value;
            ResourceLocation effect = ResourceLocation.tryParse(row.getString("id"));
            if (effect != null) {
                var reserve = FoodEffectReserve.read(row);
                reserve.advance(Math.max(0, reserve.remaining() - ticks));
                entry.effectReserves.put(effect, reserve);
            }
        }
        ListTag triggered = tag.getList(TRIGGERED_EFFECTS, Tag.TAG_STRING);
        for (int i = 0; i < triggered.size(); i++) {
            if (ResourceLocation.tryParse(triggered.getString(i)) != null) {
                entry.triggeredEffects.add(triggered.getString(i));
            }
        }
        ListTag owned = tag.getList(OWNED_EFFECTS, Tag.TAG_STRING);
        for (int i = 0; i < owned.size(); i++) {
            if (ResourceLocation.tryParse(owned.getString(i)) != null) {
                entry.ownedEffects.add(owned.getString(i));
            }
        }
        return entry;
    }
}
