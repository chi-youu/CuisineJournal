package com.chi__you.cuisinejournal.food;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Narrow exception for the two installed Exquisito multi-bite desserts. */
public final class ExquisitoParfaits {
    private ExquisitoParfaits() {}
    public static boolean matches(ResourceLocation id) {
        return id != null && id.getNamespace().equals("exquisito")
                && (id.getPath().equals("nightshade_berry_parfait") || id.getPath().equals("ether_bulb_parfait"));
    }
    public static boolean matches(ItemStack stack) {
        return stack != null && !stack.isEmpty() && matches(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }
    public static int placement(int bite, List<CuisineFoodPlacement.Slot> ordinary,
                                List<CuisineFoodPlacement.Slot> temporary, int cost, int capacity,
                                int temporaryCapacity, double fraction, boolean repeatable) {
        boolean present = ordinary.stream().anyMatch(slot -> slot.sameFood() && slot.remainingTicks() > 0);
        if (present && bite > 0) return CuisineFoodPlacement.acceptsTemporary(temporary,cost,temporaryCapacity)
                ? CuisineFoodPlacement.TEMPORARY : CuisineFoodPlacement.REJECT;
        return CuisineFoodPlacement.find(ordinary,cost,capacity,fraction,repeatable);
    }
}
