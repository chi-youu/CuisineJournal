package com.chi__you.cuisinejournal.recipe;

import com.chi__you.cuisinejournal.CuisineJournalLog;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

/**
 * Optional pinyin-aware substring matching.
 *
 * <p>No compile-time dependency: if Just Enough Characters (or a standalone PinIn
 * library) is installed, its matcher is looked up reflectively the first time a
 * search runs, so typing {@code tdt} or {@code tudou} finds 土豆汤. Without such a
 * mod — or if the lookup fails in any way — matching falls back to a plain
 * case-insensitive {@code contains}.</p>
 */
public final class PinyinMatch {

    private static volatile boolean resolved;
    private static Method staticContains;   // JECh: Match.contains(String haystack, CharSequence needle)
    private static Object pinInInstance;    // PinIn: new PinIn().contains(String haystack, String needle)
    private static Method pinInContains;

    private PinyinMatch() {
    }

    /** True if {@code haystack} contains {@code needle}, directly or by pinyin. Both may be any case. */
    public static boolean contains(String haystack, String needle) {
        if (haystack == null || needle == null) return false;
        if (needle.isEmpty()) return true;
        String hay = haystack.toLowerCase(Locale.ROOT);
        String query = needle.toLowerCase(Locale.ROOT);
        if (hay.contains(query)) return true;
        // Pinyin only helps when the text has non-ASCII characters and the query has Latin letters.
        if (!hasNonAscii(hay) || !hasLatin(query)) return false;
        resolve();
        try {
            if (staticContains != null) return Boolean.TRUE.equals(staticContains.invoke(null, hay, query));
            if (pinInContains != null) return Boolean.TRUE.equals(pinInContains.invoke(pinInInstance, hay, query));
        } catch (Throwable error) {
            CuisineJournalLog.LOGGER.warn("Pinyin search failed, falling back to plain search", error);
            staticContains = null;
            pinInContains = null;
        }
        return false;
    }

    private static boolean hasNonAscii(String value) {
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) > 0x7F) return true;
        return false;
    }

    private static boolean hasLatin(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 'a' && c <= 'z') return true;
        }
        return false;
    }

    private static synchronized void resolve() {
        if (resolved) return;
        resolved = true;
        // Just Enough Characters exposes a static helper that already honours its config (fuzzy, keyboard, ...).
        for (String name : new String[]{"me.towdium.jecharacters.utils.Match", "me.towdium.jecharacters.util.Match"}) {
            Method method = findStatic(name);
            if (method != null) {
                staticContains = method;
                CuisineJournalLog.LOGGER.info("Pinyin search enabled via {}", name);
                return;
            }
        }
        // Standalone PinIn library (possibly shaded under another package).
        for (String name : new String[]{"me.towdium.pinin.PinIn", "me.towdium.jecharacters.pinin.PinIn"}) {
            try {
                Class<?> type = Class.forName(name);
                Method method = type.getMethod("contains", String.class, String.class);
                pinInInstance = type.getConstructor().newInstance();
                pinInContains = method;
                CuisineJournalLog.LOGGER.info("Pinyin search enabled via {}", name);
                return;
            } catch (Throwable ignored) {
                // Not present or incompatible; try the next candidate.
            }
        }
    }

    private static Method findStatic(String className) {
        try {
            Class<?> type = Class.forName(className);
            for (Method method : type.getMethods()) {
                if (!method.getName().equals("contains") || !Modifier.isStatic(method.getModifiers())) continue;
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 2 && params[0].isAssignableFrom(String.class)
                        && params[1].isAssignableFrom(String.class)
                        && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                    return method;
                }
            }
        } catch (Throwable ignored) {
            // Mod not installed.
        }
        return null;
    }
}
