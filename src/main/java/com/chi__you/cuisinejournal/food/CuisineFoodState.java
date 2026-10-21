package com.chi__you.cuisinejournal.food;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Server-authoritative food and drink state for one player. */
public final class CuisineFoodState {
    public static final String ROOT = "cuisinejournal:food_state";
    public static final String VERSION = "version";
    public static final String FOOD_COUNT = "food_count";
    public static final String FOOD_PREFIX = "food_";
    public static final String DRINK = "drink";
    public static final int CURRENT_VERSION = 3;
    private static final int MAX_SAVED_ENTRIES = 64;

    private final List<CuisineFoodEntry> foods = new ArrayList<>();
    private final List<CuisineFoodEntry> temporaryFoods = new ArrayList<>();
    private CuisineFoodEntry drink;
    private boolean readOnly;

    public boolean isReadOnly() {
        return readOnly;
    }

    public static CuisineFoodState unsupported() {
        CuisineFoodState state = new CuisineFoodState();
        state.readOnly = true;
        return state;
    }

    public List<CuisineFoodEntry> foods() {
        return foods;
    }

    public List<CuisineFoodEntry> temporaryFoods() { return temporaryFoods; }

    public List<CuisineFoodEntry> allFoods() {
        List<CuisineFoodEntry> result = new ArrayList<>(foods);
        result.addAll(temporaryFoods);
        return result;
    }

    public CuisineFoodEntry drink() {
        return drink;
    }

    public void setDrink(CuisineFoodEntry drink) {
        this.drink = drink;
    }

    public int usedFoodSlots() {
        return foods.stream().mapToInt(CuisineFoodEntry::slotCost).sum();
    }

    public CuisineFoodEntry findSame(ItemStack stack) {
        for (CuisineFoodEntry entry : foods) {
            if (CuisineFoodService.sameFood(entry.stack(), stack)) {
                return entry;
            }
        }
        return null;
    }

    public void tick() {
        advance(1);
    }

    public void advance(int ticks) {
        advanceOrdinary(ticks);
        if (readOnly || ticks <= 0) return;
        temporaryFoods.forEach(entry -> entry.advance(ticks));
        temporaryFoods.removeIf(CuisineFoodEntry::isExpired);
    }

    public void advanceOrdinary(int ticks) {
        if (readOnly || ticks <= 0) return;
        for (CuisineFoodEntry entry : foods) {
            entry.advance(ticks);
        }
        if (drink != null) {
            drink.advance(ticks);
        }
        foods.removeIf(CuisineFoodEntry::isExpired);
        if (drink != null && drink.isExpired()) {
            drink = null;
        }
    }

    public void skipNight(long passedTicks) {
        temporaryFoods.forEach(food -> food.advance((int)Math.min(Integer.MAX_VALUE, passedTicks)));
        temporaryFoods.removeIf(CuisineFoodEntry::isExpired);
        foods.forEach(food -> food.skipNight(passedTicks));
        if (drink != null) {
            drink.skipNight(passedTicks);
        }
    }

    public void clear() {
        foods.clear();
        temporaryFoods.clear();
        drink = null;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION, CURRENT_VERSION);
        tag.putInt(FOOD_COUNT, foods.size());
        for (int i = 0; i < foods.size(); i++) {
            tag.put(FOOD_PREFIX + i, foods.get(i).save());
        }
        tag.putInt("temporary_count", temporaryFoods.size());
        for (int i = 0; i < temporaryFoods.size(); i++) tag.put("temporary_" + i, temporaryFoods.get(i).save());
        if (drink != null) {
            tag.put(DRINK, drink.save());
        }
        return tag;
    }

    public static CuisineFoodState read(CompoundTag tag) {
        CuisineFoodState state = new CuisineFoodState();
        if (tag == null || tag.isEmpty()) {
            return state;
        }
        if (tag.getInt(VERSION) < 1 || tag.getInt(VERSION) > CURRENT_VERSION) {
            state.readOnly = true;
            return state;
        }
        int count = Math.max(0, Math.min(tag.getInt(FOOD_COUNT), MAX_SAVED_ENTRIES));
        for (int i = 0; i < count; i++) {
            CuisineFoodEntry entry = CuisineFoodEntry.read(tag.getCompound(FOOD_PREFIX + i));
            if (entry != null && entry.slotCost() <= 3) {
                state.foods.add(entry);
            }
        }
        if (tag.contains(DRINK)) {
            state.drink = CuisineFoodEntry.read(tag.getCompound(DRINK));
        }
        int temporaryCount = Math.max(0, Math.min(CuisineFoodCapacity.MAXIMUM, tag.getInt("temporary_count")));
        for (int i = 0; i < temporaryCount; i++) {
            CuisineFoodEntry entry = CuisineFoodEntry.read(tag.getCompound("temporary_" + i));
            if (entry != null && entry.totalTicks() <= 12000 && entry.slotCost() <= 3
                    && state.temporaryFoods.stream().mapToInt(CuisineFoodEntry::slotCost).sum() + entry.slotCost() <= CuisineFoodCapacity.MAXIMUM
                    && state.temporaryFoods.stream().noneMatch(old -> CuisineFoodService.sameFood(old.stack(), entry.stack())))
                state.temporaryFoods.add(entry);
        }
        return state;
    }
}
