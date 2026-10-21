package com.chi__you.cuisinejournal.client.journal;

import com.chi__you.cuisinejournal.network.Packets;
import com.chi__you.cuisinejournal.recipe.PinyinMatch;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

/** A leading @ selects an item's owning mod; tags match through their members. */
public record IngredientSearch(String mod, String text) {
    public static IngredientSearch parse(String query) {
        String value = query == null ? "" : query.strip();
        if (!value.startsWith("@")) return new IngredientSearch(null, value);
        String rest = value.substring(1).stripLeading();
        if (rest.startsWith("\"")) {
            int end = rest.indexOf('"', 1);
            return end < 0 ? new IngredientSearch(rest.substring(1), "")
                    : new IngredientSearch(rest.substring(1, end), rest.substring(end + 1).strip());
        }
        int end = 0;
        while (end < rest.length() && !Character.isWhitespace(rest.charAt(end))) end++;
        return new IngredientSearch(rest.substring(0, end), rest.substring(end).strip());
    }

    public boolean matchesMod(String namespace, List<String> names) {
        return mod == null || PinyinMatch.contains(namespace, mod)
                || names.stream().anyMatch(name -> PinyinMatch.contains(name, mod));
    }

    public boolean matchesItem(ResourceLocation item, Function<ResourceLocation,String> name,
                               Predicate<String> selectedMod) {
        return (mod == null || selectedMod.test(item.getNamespace())) && matchesText(item, name.apply(item));
    }

    public boolean matches(Packets.IngredientView entry, String entryName,
                           Predicate<ResourceLocation> memberMatches, Predicate<String> selectedMod) {
        return matches(entry, entryName, memberMatches, selectedMod, List.of());
    }

    public boolean matches(Packets.IngredientView entry, String entryName,
                           Predicate<ResourceLocation> memberMatches, Predicate<String> selectedMod, List<String> flavors) {
        boolean flavorMatch = flavors.stream().anyMatch(name -> PinyinMatch.contains(name, text));
        if (!entry.tag()) return (mod == null || selectedMod.test(entry.item().getNamespace()))
                && (matchesText(entry.item(), entryName) || flavorMatch);
        if (mod != null) return entry.members().stream().anyMatch(memberMatches)
                || flavorMatch && entry.members().stream().anyMatch(item -> selectedMod.test(item.getNamespace()));
        return flavorMatch || matchesText(entry.item(), entryName)
                || entry.tagIds().stream().anyMatch(id -> id.toString().contains(text.toLowerCase(Locale.ROOT)))
                || entry.members().stream().anyMatch(memberMatches);
    }

    private boolean matchesText(ResourceLocation id, String name) {
        return text.isEmpty() || id.toString().contains(text.toLowerCase(Locale.ROOT)) || PinyinMatch.contains(name, text);
    }
}
