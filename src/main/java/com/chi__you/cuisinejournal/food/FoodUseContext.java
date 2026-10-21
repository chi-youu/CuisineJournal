package com.chi__you.cuisinejournal.food;

import net.minecraft.world.item.ItemStack;
import java.util.ArrayDeque;
import java.util.Deque;

/** Captures the actual outer use without letting nested host actions create new admissions. */
public final class FoodUseContext {
    private static final ThreadLocal<Deque<ItemStack>> USING = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Integer> SEASONING = ThreadLocal.withInitial(() -> 0);

    private FoodUseContext() {}

    public static ItemStack current() {
        return USING.get().isEmpty() ? ItemStack.EMPTY : USING.get().peek();
    }
    public static boolean active() { return !USING.get().isEmpty(); }
    public static void begin(ItemStack food) { USING.get().push(food.copy()); }
    public static void end() {
        var frames = USING.get();
        if (!frames.isEmpty()) frames.pop();
        if (frames.isEmpty()) { USING.remove(); SEASONING.remove(); }
    }
    public static boolean seasoningActive() { return SEASONING.get() > 0; }
    public static void seasoning(boolean enter) {
        int value = SEASONING.get() + (enter ? 1 : -1);
        if (value <= 0) SEASONING.remove(); else SEASONING.set(value);
    }
}

