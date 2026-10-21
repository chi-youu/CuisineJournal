package com.chi__you.cuisinejournal.recipe;

import com.chi__you.cuisinejournal.CuisineJournalLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

/** Registered items can still be disabled by their owning mod's compatibility rules. */
public final class HostItemAvailability {
    public static final net.minecraft.resources.ResourceLocation UNITED =
            net.minecraft.resources.ResourceLocation.parse("kaleidoscope_cookery:united");
    private static volatile java.util.Set<net.minecraft.resources.ResourceLocation> unifiedHidden = java.util.Set.of();
    private static final Map<Item, Boolean> ENABLED = new IdentityHashMap<>();
    private static Method delightfulEnabled;
    private static boolean resolved;
    private static boolean warned;

    private HostItemAvailability() {}
    public static synchronized Runnable rollbackPoint() {
        var hidden=unifiedHidden; var enabled=new IdentityHashMap<>(ENABLED);
        return () -> { synchronized(HostItemAvailability.class) { unifiedHidden=hidden; ENABLED.clear(); ENABLED.putAll(enabled); } };
    }

    public static synchronized void rebuild() {
        ENABLED.clear();
        var hidden = new java.util.HashSet<net.minecraft.resources.ResourceLocation>();
        for (Item item : BuiltInRegistries.ITEM)
            if (hiddenByUnification(id -> item.builtInRegistryHolder().tags().anyMatch(tag -> tag.location().equals(id))))
                hidden.add(BuiltInRegistries.ITEM.getKey(item));
        unifiedHidden = java.util.Set.copyOf(hidden);
    }

    public static synchronized boolean enabled(Item item) {
        if (item == null) return false;
        if (hiddenByUnification(id -> item.builtInRegistryHolder().tags().anyMatch(tag -> tag.location().equals(id))))
            return false;
        var id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null || !id.getNamespace().equals("delightful")) return true;
        Boolean cached = ENABLED.get(item);
        if (cached != null) return cached;
        boolean enabled = true;
        try {
            if (!resolved) {
                delightfulEnabled = Class.forName("net.brnbrd.delightful.Util")
                        .getMethod("enabled", Item.class);
                resolved = true;
            }
            enabled = Boolean.TRUE.equals(delightfulEnabled.invoke(null, item));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            // Unknown host versions keep their items visible instead of hiding valid content.
            if (!warned) {
                CuisineJournalLog.LOGGER.warn("Cannot read Delightful item availability; keeping items visible", failure);
                warned = true;
            }
        }
        ENABLED.put(item, enabled);
        return enabled;
    }

    public static boolean hiddenByUnification(java.util.function.Predicate<net.minecraft.resources.ResourceLocation> hasTag) {
        return hasTag.test(UNITED) || hasTag.test(com.chi__you.cuisinejournal.compat.UnificationRules.HIDDEN);
    }

    public static boolean visible(net.minecraft.resources.ResourceLocation id) {
        return BuiltInRegistries.ITEM.containsKey(id) && enabled(BuiltInRegistries.ITEM.get(id));
    }

    public static boolean hiddenByUnification(net.minecraft.resources.ResourceLocation id) {
        if (id == null) return false;
        return unifiedHidden.contains(id);
    }
}
