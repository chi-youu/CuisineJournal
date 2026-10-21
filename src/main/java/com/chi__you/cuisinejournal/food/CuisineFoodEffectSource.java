package com.chi__you.cuisinejournal.food;

import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Only ingredient flavors supply Cuisine Journal's managed effect reserves. */
public final class CuisineFoodEffectSource {
    private CuisineFoodEffectSource() {}

    public static List<CuisineFoodDefinitions.Effect> forStack(ItemStack stack) {
        return CuisineFlavorEffects.forStack(stack);
    }
}
