package com.chi__you.cuisinejournal.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.*;

/** Reads committed server resources, not the setting being edited during an asynchronous reload. */
public final class UnificationRuntime {
    private static ResourceManager cached;
    private static UnificationContent.Plan plan = UnificationContent.EMPTY;
    private static final ThreadLocal<Deque<Level>> INTERACTIONS = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<ItemStack[]>> INVENTORY = ThreadLocal.withInitial(ArrayDeque::new);
    private UnificationRuntime() {}
    public static synchronized UnificationContent.Plan plan(MinecraftServer server) {
        if (server == null) return UnificationContent.EMPTY;
        var resources = server.getResourceManager();
        if (cached != resources) { plan = UnificationContent.read(resources); cached = resources; }
        return plan;
    }
    public static synchronized void clear() { cached = null; plan = UnificationContent.EMPTY; }
    public static ItemStack replace(ItemStack original, UnificationContent.Plan plan) {
        if (original.isEmpty()) return original;
        var target = plan.replace(BuiltInRegistries.ITEM.getKey(original.getItem()));
        if (target.equals(BuiltInRegistries.ITEM.getKey(original.getItem())) || !BuiltInRegistries.ITEM.containsKey(target)) return original;
        // Only called at fresh production/loot boundaries; never scans or converts a player's inventory.
        var result = new ItemStack(BuiltInRegistries.ITEM.get(target), original.getCount());
        if (original.hasTag()) result.setTag(original.getTag().copy());
        return result;
    }
    public static ItemStack produced(Level level, ItemStack stack) {
        return level.isClientSide ? stack : replace(stack, plan(level.getServer()));
    }
    public static void beginInteraction(Level level) { INTERACTIONS.get().push(level); }
    public static void endInteraction() {
        var stack = INTERACTIONS.get(); if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) INTERACTIONS.remove();
    }
    public static ItemStack interactionOutput(ItemStack stack) {
        var levels = INTERACTIONS.get();
        return levels.isEmpty() ? stack : produced(levels.peek(), stack);
    }
    public static ItemStack inventoryOutput(ItemStack original) {
        var output = interactionOutput(original); INVENTORY.get().push(new ItemStack[]{original, output}); return output;
    }
    public static void inventoryDone() {
        var frames = INVENTORY.get();
        if (!frames.isEmpty()) {
            var frame = frames.pop();
            if (frame[0] != frame[1]) frame[0].setCount(frame[1].getCount());
        }
        if (frames.isEmpty()) INVENTORY.remove();
    }
    public static void clearInteractions() { INTERACTIONS.remove(); INVENTORY.remove(); }
    public static boolean suppressedDrop(ResourceLocation item, ResourceLocation entity, UnificationContent.Plan plan) {
        if (!plan.enabled()) return false;
        if (entity.equals(UnificationContent.id("minecraft:squid")) || entity.equals(UnificationContent.id("minecraft:glow_squid"))) {
            String group = entity.getPath().equals("glow_squid") ? "glow_squid" : "squid";
            var whole = plan.winner("cuisinejournal:" + group);
            if (whole != null && isSquidMaterial(item)) return !item.equals(whole)
                    && !item.equals(plan.winner("cuisinejournal:cooked_squid"));
            var tentacle = plan.winner("cuisinejournal:" + (group.equals("glow_squid") ? "glow_tentacle" : "tentacle"));
            if (tentacle != null && isSquidMaterial(item)) return !item.equals(tentacle)
                    && !item.equals(plan.winner("cuisinejournal:cooked_tentacle"));
        }
        if (entity.equals(UnificationContent.id("twilightforest:naga"))) {
            var winner = plan.winner("cuisinejournal:naga");
            if (winner != null && (item.equals(UnificationContent.id("kaleidoscope_twilight:raw_naga_meat"))
                    || item.equals(UnificationContent.id("kaleidoscope_twilight:cooked_naga_meat"))))
                return !winner.getNamespace().equals("kaleidoscope_twilight");
        }
        if (entity.equals(UnificationContent.id("minecraft:ghast")) && item.getPath().equals("ghast_tentacle")) {
            var winner = plan.winner("cuisinejournal:ghast_tentacle");
            return winner != null && !winner.equals(item);
        }
        return false;
    }
    public static boolean isSquidMaterial(ResourceLocation item) {
        return Set.of("miners_delight", "culturaldelights", "rusticdelight", "oceansdelight", "oceanic_delight", "crabbersdelight", "dumplings_delight")
                .contains(item.getNamespace()) && Set.of("squid", "glow_squid", "calamari", "tentacles", "squid_tentacles",
                "glow_squid_tentacles", "raw_squid_tentacles", "raw_glow_squid_tentacles",
                "baked_squid", "cooked_squid", "cooked_calamari", "baked_tentacles",
                "grilled_squid_tentacles", "cooked_squid_tentacles", "cooked_glow_squid_tentacles").contains(item.getPath());
    }
}
