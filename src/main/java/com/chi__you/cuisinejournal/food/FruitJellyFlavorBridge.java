package com.chi__you.cuisinejournal.food;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;

/** Reuses the host's existing fruit list; never adds a new ingredient stamp. */
public final class FruitJellyFlavorBridge {
    public static final String KEY = "JellyEffectRoot";
    private static final class HostTags {
        private static final TagKey<Item> ALLOWED = TagKey.create(Registries.ITEM, ResourceLocation.parse("fruitsdelight:allow_jelly"));
    }
    private FruitJellyFlavorBridge() {}

    public static List<String> fruits(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(KEY, Tag.TAG_LIST) && stack.is(HostTags.ALLOWED)
                ? fruits(stack.getTag()) : List.of();
    }

    public static List<String> fruits(CompoundTag tag) {
        if (tag == null || !tag.contains(KEY, Tag.TAG_LIST)) return List.of();
        var values = tag.getList(KEY, Tag.TAG_STRING);
        Set<String> result = new TreeSet<>();
        for (int i = 0; i < Math.min(22, values.size()); i++) {
            String name = values.getString(i);
            if (name.matches("[A-Z_]{1,32}")) result.add(name);
        }
        return List.copyOf(result);
    }

    public static List<ResourceLocation> jellies(List<String> fruits) {
        if (fruits.isEmpty()) return List.of();
        try {
            Class<?> type = Class.forName("dev.xkmc.fruitsdelight.init.food.FruitType");
            List<ResourceLocation> result = new ArrayList<>();
            for (Object fruit : type.getEnumConstants()) if (fruits.contains(((Enum<?>) fruit).name())) {
                Object item = type.getMethod("getJelly").invoke(fruit);
                if (item instanceof Item jelly) result.add(BuiltInRegistries.ITEM.getKey(jelly));
            }
            return List.copyOf(result);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) { return List.of(); }
    }
}
