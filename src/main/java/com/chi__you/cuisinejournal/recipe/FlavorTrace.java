package com.chi__you.cuisinejournal.recipe;

import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.*;

/** Traverses declared branches and unambiguous single-member tags. */
public final class FlavorTrace {
    public record Result(List<String> sources, boolean truncated) {
        public Result { sources = List.copyOf(sources); }
    }
    private FlavorTrace() {}

    public static Result trace(ResourceLocation dish,
                               Function<ResourceLocation,List<RecipeEntry.IngredientSlot>> inputs,
                               Function<ResourceLocation,List<ResourceLocation>> tags,
                               Function<ResourceLocation,List<ResourceLocation>> tagMembers,
                               Predicate<String> stop,
                               Function<ResourceLocation,List<String>> explicitSources) {
        List<String> rootOverride = explicitSources.apply(dish);
        if (rootOverride != null) return new Result(rootOverride,false);
        Deque<String> pending = new ArrayDeque<>();
        pending.add(dish.toString());
        Set<String> visited = new HashSet<>();
        Set<String> result = new LinkedHashSet<>();
        while (!pending.isEmpty() && visited.size() < 4096) {
            String reference = pending.removeFirst();
            if (!visited.add(reference)) continue;
            boolean tag = reference.startsWith("#");
            ResourceLocation id = ResourceLocation.parse(tag ? reference.substring(1) : reference);
            if (tag) {
                result.add(reference);
                // Keep tag flavors, but only follow item flavors when there is no alternative.
                List<ResourceLocation> members = tagMembers.apply(id);
                if (members.size() == 1) pending.addLast(members.get(0).toString());
                continue;
            }
            // The finished food itself is not an ingredient unless explicitly marked as a source.
            if (!id.equals(dish) || stop.test(reference)) result.add(reference);
            tags.apply(id).stream().sorted().forEach(value -> pending.addLast("#"+value));
            if (stop.test(reference)) continue;
            List<String> override = explicitSources.apply(id);
            if (override != null) { override.forEach(pending::addLast); continue; }
            for (var slot : inputs.apply(id)) {
                slot.explicitItems().stream().sorted().forEach(value -> pending.addLast(value.toString()));
                slot.tags().stream().sorted().forEach(value -> pending.addLast("#"+value));
            }
        }
        return new Result(new ArrayList<>(result),!pending.isEmpty());
    }
}
