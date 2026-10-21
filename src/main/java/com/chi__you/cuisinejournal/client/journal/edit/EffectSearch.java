package com.chi__you.cuisinejournal.client.journal.edit;

import com.chi__you.cuisinejournal.recipe.PinyinMatch;
import java.util.Locale;

/** The picker accepts localized names, resource ids and optional JECharacters pinyin. */
public final class EffectSearch {
    private EffectSearch() {}

    public static boolean matches(String id, String name, String query) {
        String normalized = query.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return true;
        String identity = id.toLowerCase(Locale.ROOT);
        for (String token : normalized.split("\\s+")) {
            if (!identity.contains(token) && !PinyinMatch.contains(name, token)) return false;
        }
        return true;
    }
}
