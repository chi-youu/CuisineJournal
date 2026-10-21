package com.chi__you.cuisinejournal.client.journal;

import com.chi__you.cuisinejournal.network.Packets;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Local editor filtering; never changes tag membership or player discovery. */
public enum TagTranslationFilter {
    NORMAL("normal"), FIRST("first"), ONLY("only");

    public final String key;
    TagTranslationFilter(String key) { this.key = key; }

    public boolean accepts(boolean untranslated) { return this != ONLY || untranslated; }

    public static String translationKey(ResourceLocation key, List<ResourceLocation> aliases, Predicate<String> available) {
        return Stream.concat(Stream.of(key), aliases.stream()).distinct()
                .map(id -> "tag.item." + id.getNamespace() + "." + id.getPath().replace('/', '.'))
                .filter(available).findFirst().orElse(null);
    }

    public static boolean untranslated(Packets.IngredientView entry, Predicate<String> available) {
        return entry.tag() && (entry.displayName() == null || entry.displayName().isBlank())
                && translationKey(entry.item(), entry.tagIds(), available) == null;
    }
}
