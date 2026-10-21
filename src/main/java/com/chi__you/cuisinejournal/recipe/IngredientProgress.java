package com.chi__you.cuisinejournal.recipe;

import com.chi__you.cuisinejournal.culinary.IngredientRecord;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.Function;

/** Read-only aggregation: no alias rule migrates or deletes a player's original records. */
public record IngredientProgress(boolean known, long timesUsed, long firstUsedDay,
                                 long lastUsedDay, List<ResourceLocation> methods) {
    public IngredientProgress { methods = List.copyOf(methods); }
    public static IngredientProgress collect(Collection<ResourceLocation> sources,
                                             Function<ResourceLocation, IngredientRecord> lookup) {
        boolean known = false; long used = 0, first = Long.MIN_VALUE, last = Long.MIN_VALUE;
        Set<ResourceLocation> methods = new TreeSet<>();
        for (ResourceLocation source : new HashSet<>(sources)) {
            IngredientRecord record = lookup.apply(source); if (record == null) continue;
            known |= record.known() || record.timesUsed() > 0;
            long count = Math.max(0, record.timesUsed());
            used = count > Long.MAX_VALUE - used ? Long.MAX_VALUE : used + count;
            if (record.firstUsedDay() != Long.MIN_VALUE)
                first = first == Long.MIN_VALUE ? record.firstUsedDay() : Math.min(first, record.firstUsedDay());
            if (record.lastUsedDay() != Long.MIN_VALUE)
                last = last == Long.MIN_VALUE ? record.lastUsedDay() : Math.max(last, record.lastUsedDay());
            record.methods().forEach(method -> methods.add(method.id()));
        }
        return new IngredientProgress(known, used, first, last, List.copyOf(methods));
    }
}
