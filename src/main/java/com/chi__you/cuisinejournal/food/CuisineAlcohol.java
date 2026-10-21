package com.chi__you.cuisinejournal.food;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;

import java.util.Set;

/** Recognizes configured alcohol, including filled Youkai sake bottles. */
public final class CuisineAlcohol {
    private static final ResourceLocation SAKE_BOTTLE = new ResourceLocation("youkaishomecoming", "sake_bottle");
    private static final Set<String> DEFAULT_ITEMS = Set.of(
            "brewinandchewin:beer", "brewinandchewin:bloody_mary",
            "brewinandchewin:egg_grog", "brewinandchewin:glittering_grenadine",
            "brewinandchewin:mead", "brewinandchewin:pale_jane",
            "brewinandchewin:rice_wine", "brewinandchewin:saccharine_rum",
            "brewinandchewin:salty_folly", "brewinandchewin:strongroot_ale",
            "brewinandchewin:vodka", "youkaishomecoming:burgundy",
            "youkaishomecoming:burgundy_bottle", "youkaishomecoming:champagne",
            "youkaishomecoming:champagne_bottle", "youkaishomecoming:hakutsuru",
            "youkaishomecoming:kappa_village", "youkaishomecoming:kiku",
            "youkaishomecoming:mead", "youkaishomecoming:mio",
            "youkaishomecoming:red_wine", "youkaishomecoming:red_wine_bottle",
            "youkaishomecoming:sparrow_sake", "youkaishomecoming:suigei",
            "youkaishomecoming:van_allen", "youkaishomecoming:van_allen_bottle",
            "youkaishomecoming:white_wine", "youkaishomecoming:white_wine_bottle",
            "youkaishomecoming:wind_priestesses");

    private CuisineAlcohol() {
    }

    public static ResourceLocation sourceId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (SAKE_BOTTLE.equals(itemId)) {
            return filledSakeSource(stack);
        }
        return listed(itemId) ? itemId : null;
    }

    private static ResourceLocation filledSakeSource(ItemStack stack) {
        if (!stack.hasTag()) {
            return null;
        }
        CompoundTag tag = stack.getTag();
        if (!tag.contains("SakeFluid", 10)) {
            return null;
        }
        CompoundTag fluid = tag.getCompound("SakeFluid");
        if (fluid.getInt("Amount") <= 0) {
            return null;
        }
        for (String key : new String[] {"FluidName", "fluid", "id"}) {
            if (fluid.contains(key, 8)) {
                ResourceLocation id = ResourceLocation.tryParse(fluid.getString(key));
                return listed(id) ? id : null;
            }
        }
        return null;
    }

    public static boolean isAlcohol(ItemStack stack) {
        return sourceId(stack) != null;
    }

    public static boolean isListedItem(Item item) {
        return !SAKE_BOTTLE.equals(BuiltInRegistries.ITEM.getKey(item))
                && listed(BuiltInRegistries.ITEM.getKey(item));
    }

    private static boolean listed(ResourceLocation id) {
        return id != null && (DEFAULT_ITEMS.contains(id.toString())
                || CuisineFoodConfig.ALCOHOL_ITEMS.get().contains(id.toString()));
    }
}
