package me.zetra.nickguard;

import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

/** Distinct nicknames in a window; reconnects of the same account count once. */
final class SimilarNickWindow {
    private final Map<String, Long> names = new HashMap<>();
    synchronized int record(String name, long now, long window) {
        expire(now, window);
        names.put(name.toLowerCase(Locale.ROOT), now);
        return names.size();
    }
    synchronized boolean expire(long now, long window) {
        names.values().removeIf(time -> now - time > window);
        return names.isEmpty();
    }
    static String normalize(String name, boolean lower, boolean numbers, boolean symbols) {
        String result = lower ? name.toLowerCase(Locale.ROOT) : name;
        if (numbers) result = result.replaceAll("[0-9]", "");
        if (symbols) result = result.replaceAll("[^a-zA-Z]", "");
        return result.isBlank() ? "exact:" + name.toLowerCase(Locale.ROOT) : result;
    }
}
