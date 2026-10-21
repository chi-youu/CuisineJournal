package com.chi__you.cuisinejournal.food;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

/** Limits MCreator procedure suppression to the current Aqua food consumer. */
public final class CuisineAquaEffectWindow {
    private record Window(LivingEntity consumer) {
    }

    private static final ThreadLocal<Deque<Window>> WINDOWS = ThreadLocal.withInitial(ArrayDeque::new);

    private CuisineAquaEffectWindow() {
    }

    public static void begin(ItemStack stack, LivingEntity consumer) {
        Deque<Window> windows = WINDOWS.get();
        LivingEntity active = windows.isEmpty() ? null : windows.peek().consumer();
        if (!CuisineGameplayRules.nativeEffectsEnabled()
                && !stack.isEmpty() && consumer != null && !consumer.level().isClientSide) {
            var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null && id.getNamespace().equals("aquaculturedelight")
                    && stack.getFoodProperties(consumer) != null) {
                active = consumer;
            }
        }
        windows.push(new Window(active));
    }

    public static void end() {
        Deque<Window> windows = WINDOWS.get();
        if (!windows.isEmpty()) {
            windows.pop();
        }
        if (windows.isEmpty()) {
            WINDOWS.remove();
        }
    }

    public static boolean blocks(LivingEntity target) {
        Deque<Window> windows = WINDOWS.get();
        return !windows.isEmpty() && windows.peek().consumer() == target;
    }
}
