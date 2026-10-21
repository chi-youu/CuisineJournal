package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.CuisineJournal;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** One-way import of SOL's sol_food_data; leaves the old tag untouched. */
public final class CuisineSolMigration {
    private static final int MAX_ENTRIES = 64;
    public static final String BACKUP = "cuisinejournal:sol_import_backup";
    public static final String IMPORTED = "cuisinejournal:sol_imported";

    private CuisineSolMigration() {
    }

    public static void capture(CompoundTag playerTag, CompoundTag persistentData) {
        boolean missingBackup = !persistentData.contains(BACKUP)
                || persistentData.contains(BACKUP, 10) && persistentData.getCompound(BACKUP).isEmpty();
        boolean notAdopted = !persistentData.contains(CuisineFoodState.ROOT)
                && !persistentData.getBoolean(IMPORTED);
        if (playerTag.contains("sol_food_data", 10) && (missingBackup || notAdopted)) {
            persistentData.put(BACKUP, playerTag.getCompound("sol_food_data").copy());
        }
    }

    public static CompoundTag pendingLegacy(CompoundTag persistentData) {
        if (persistentData.contains(CuisineFoodState.ROOT) || persistentData.getBoolean(IMPORTED)) {
            return null;
        }
        if (persistentData.contains(BACKUP, 10)
                && !persistentData.getCompound(BACKUP).isEmpty()) {
            return persistentData.getCompound(BACKUP);
        }
        return persistentData.contains("sol_food_data", 10)
                ? persistentData.getCompound("sol_food_data") : null;
    }

    public static void copyHistory(CompoundTag original, CompoundTag replacement) {
        if (original.contains(BACKUP, 10) && !original.getCompound(BACKUP).isEmpty()) {
            replacement.put(BACKUP, original.getCompound(BACKUP).copy());
        } else if (original.contains("sol_food_data", 10)) {
            replacement.put(BACKUP, original.getCompound("sol_food_data").copy());
        }
        if (original.contains(IMPORTED)) {
            replacement.putBoolean(IMPORTED, original.getBoolean(IMPORTED));
        }
    }

    public static CuisineFoodState importLegacy(CompoundTag old) {
        CuisineFoodState state = new CuisineFoodState();
        int count = Math.max(0, Math.min(MAX_ENTRIES, old.getInt("count")));
        if (old.getInt("count") > MAX_ENTRIES) {
            CuisineJournal.LOGGER.warn("SOL food import limited to {} entries; full legacy data remains in backup",
                    MAX_ENTRIES);
        }
        for (int i = 0; i < count; i++) {
            try {
                ItemStack stack = stack(old.getString("id" + i), old.getCompound("data" + i));
                int remaining = old.getInt("ticks" + i);
                if (stack.isEmpty() || remaining <= 0) {
                    continue;
                }
                CuisineFoodDefinition definition = CuisineFoodService.definition(stack);
                if (definition == null) {
                    CuisineJournal.LOGGER.warn("Skipping unsupported SOL food {}", old.getString("id" + i));
                    continue;
                }
                // Preserve the old placement even when classification or capacity has changed.
                state.foods().add(importEntry(stack, remaining, definition));
            } catch (RuntimeException failure) {
                CuisineJournal.LOGGER.warn("Skipping invalid SOL food entry {} ({})", i,
                        old.getString("id" + i), failure);
            }
        }
        try {
            ItemStack drink = stack(old.getString("drink"), old.getCompound("drinkData"));
            int drinkTicks = old.getInt("drinkticks");
            if (!drink.isEmpty() && drinkTicks > 0) {
                CuisineFoodDefinition definition = CuisineFoodService.definition(drink);
                if (definition != null) {
                    state.setDrink(importEntry(drink, drinkTicks, definition));
                } else {
                    CuisineJournal.LOGGER.warn("Skipping unsupported SOL drink {}", old.getString("drink"));
                }
            }
        } catch (RuntimeException failure) {
            CuisineJournal.LOGGER.warn("Skipping invalid SOL drink {}", old.getString("drink"), failure);
        }
        return state;
    }

    private static CuisineFoodEntry importEntry(ItemStack stack, int remaining,
                                                CuisineFoodDefinition definition) {
        CompoundTag saved = new CuisineFoodEntry(stack, remaining, definition.slotCost()).save();
        saved.putInt(CuisineFoodEntry.TOTAL_TICKS, Math.max(remaining, definition.durationTicks()));
        CuisineFoodEntry entry = CuisineFoodEntry.read(saved);
        // Legacy state cannot prove which once-only effects have already fired.
        for (var setting : CuisineFoodEffectSource.forStack(stack)) {
            var effect = BuiltInRegistries.MOB_EFFECT.getOptional(setting.id()).orElse(null);
            if (effect != null && (effect.isInstantenous()
                    || effect == net.minecraft.world.effect.MobEffects.ABSORPTION)) {
                entry.markTriggered(setting.id());
            }
        }
        return entry;
    }

    private static ItemStack stack(String rawId, CompoundTag data) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)
                || BuiltInRegistries.ITEM.get(id) == Items.AIR) {
            if (rawId != null && !rawId.isBlank()) {
                CuisineJournal.LOGGER.warn("Skipping unknown SOL food item {}", rawId);
            }
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
        if (data != null && !data.isEmpty()) {
            stack.setTag(data.copy());
        }
        return stack;
    }
}
