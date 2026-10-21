package com.chi__you.cuisinejournal.recipe;

import com.chi__you.cuisinejournal.culinary.MealRecord;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.Function;

/** Tag usage comes from preparing its linked dishes, never from using its member items elsewhere. */
public final class TagIngredientProgress {
    private TagIngredientProgress() {}

    public static IngredientProgress collect(CulinaryRegistry registry, ResourceLocation tag,
                                             Function<ResourceLocation, MealRecord> lookup) {
        return collect(registry.tagUses(tag).stream().map(MealEntry::target).toList(),
                registry::preparationTarget, lookup);
    }

    public static IngredientProgress collect(Collection<ResourceLocation> dishes,
                                             Function<ResourceLocation, ResourceLocation> preparation,
                                             Function<ResourceLocation, MealRecord> lookup) {
        long used = 0, first = Long.MIN_VALUE, last = Long.MIN_VALUE;
        var methods = new TreeSet<ResourceLocation>();
        var counted = new HashSet<ResourceLocation>();
        for (var dish : dishes) {
            var target = preparation.apply(dish);
            if (!counted.add(target)) continue;
            var record = lookup.apply(target);
            if (record == null || record.prepared() <= 0) continue;
            long count = record.prepared();
            used = count > Long.MAX_VALUE - used ? Long.MAX_VALUE : used + count;
            if (record.firstPreparedDay() != Long.MIN_VALUE)
                first = first == Long.MIN_VALUE ? record.firstPreparedDay() : Math.min(first, record.firstPreparedDay());
            if (record.lastPreparedDay() != Long.MIN_VALUE)
                last = last == Long.MIN_VALUE ? record.lastPreparedDay() : Math.max(last, record.lastPreparedDay());
            record.preparationsByMethod().forEach((method, amount) -> { if (amount > 0) methods.add(method.id()); });
        }
        return new IngredientProgress(used > 0, used, first, last, List.copyOf(methods));
    }
}
