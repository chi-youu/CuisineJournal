package com.chi__you.cuisinejournal.client.journal;

import com.chi__you.cuisinejournal.network.Packets;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;
import java.util.function.ToLongFunction;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Discovery and usage take priority; item ordering only breaks ties. */
public final class IngredientOrder {
    private IngredientOrder() {}

    public static Comparator<Packets.IngredientView> comparator(boolean undiscoveredFirst,
                                                               Comparator<ResourceLocation> items) {
        return comparator(undiscoveredFirst, items, Packets.IngredientView::timesUsed);
    }

    public static Comparator<Packets.IngredientView> comparator(boolean undiscoveredFirst,
            Comparator<ResourceLocation> items, ToLongFunction<Packets.IngredientView> uses) {
        Comparator<Packets.IngredientView> discovery = Comparator.comparing(Packets.IngredientView::known);
        if (!undiscoveredFirst) discovery = discovery.reversed();
        var priority = discovery.thenComparing(Comparator.comparingLong(uses).reversed());
        if (!undiscoveredFirst) priority = priority.thenComparing(
                Comparator.comparingInt(Packets.IngredientView::mealCount).reversed());
        return priority.thenComparing(relatedOrder(items));
    }

    public static Comparator<ResourceLocation> mealComparator(Predicate<ResourceLocation> discovered,
            ToIntFunction<ResourceLocation> stars, Comparator<ResourceLocation> items) {
        return Comparator.comparing((ResourceLocation meal) -> !discovered.test(meal))
                .thenComparing(Comparator.comparingInt(stars).reversed())
                .thenComparing(items);
    }

    public static Comparator<Packets.IngredientView> mealCountComparator(Comparator<ResourceLocation> items,
            ToLongFunction<Packets.IngredientView> uses) {
        return Comparator.comparingInt(Packets.IngredientView::mealCount).reversed()
                .thenComparing(comparator(false, items, uses));
    }

    /** Preparation words affect order within a tag family, not the family's position. */
    private static Comparator<Packets.IngredientView> relatedOrder(Comparator<ResourceLocation> items) {
        return Comparator.comparing(IngredientOrder::familyKey, items)
                .thenComparing(Packets.IngredientView::tag)
                .thenComparingInt(IngredientOrder::preparationOrder)
                .thenComparing(value -> value.item().toString());
    }

    private static final List<String> STATES = List.of("raw", "cooked", "roasted", "grilled", "seared", "baked");

    private static ResourceLocation familyKey(Packets.IngredientView value) {
        if (!value.tag()) return value.item();
        ResourceLocation id = value.item();
        String path = id.getPath();
        if (path.startsWith("tag/")) return id;
        for (String state : STATES) {
            if (path.startsWith(state + "_")) path = path.substring(state.length() + 1);
            else if (path.endsWith("_" + state)) path = path.substring(0, path.length() - state.length() - 1);
        }
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path);
    }

    private static int preparationOrder(Packets.IngredientView value) {
        if (!value.tag()) return 0;
        String path = value.item().getPath();
        if (path.startsWith("raw_") || path.endsWith("_raw")) return 0;
        for (String state : STATES) if (!state.equals("raw")
                && (path.startsWith(state + "_") || path.endsWith("_" + state))) return 2;
        return 1;
    }
}
