package com.articulate.sigma.jedit;

import java.util.*;

/**
 * A lightweight prefix index (trie-like) with two layers:
 *  - kbLayer: stable symbols from the KB
 *  - bufLayer: volatile tokens from the current buffer
 */
class SuggestionIndex {

    private static final int MAX_BUCKET = 256; // light cap per prefix bucket

    private final Map<String, Set<String>> kbLayer = new HashMap<>();
    private final Map<String, Set<String>> bufLayer = new HashMap<>();

    void add(String token) {
        if (token == null || token.isBlank()) return;
        // Heuristic: add short prefixes to buckets for quick startsWith queries
        String lower = token.toLowerCase(Locale.ROOT);
        String key = lower.length() >= 2 ? lower.substring(0, 2) : lower;
        bufLayer.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(token);
        // do not cap aggressively here; cap when retrieving
    }

    void addKB(String token) {
        if (token == null || token.isBlank()) return;
        String lower = token.toLowerCase(Locale.ROOT);
        String key = lower.length() >= 2 ? lower.substring(0, 2) : lower;
        kbLayer.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(token);
    }

    /** Clear only buffer-derived tokens. */
    void clearBufferLayer() { bufLayer.clear(); }

    /** Retrieve suggestions that start with the given prefix. */
    List<String> startsWith(String prefix, int limit, boolean caseSensitive) {
        if (prefix == null) return Collections.emptyList();
        String lower = prefix.toLowerCase(Locale.ROOT);
        String key = lower.length() >= 2 ? lower.substring(0, 2) : lower;

        LinkedHashSet<String> results = new LinkedHashSet<>();
        Set<String> b1 = kbLayer.getOrDefault(key, Collections.emptySet());
        Set<String> b2 = bufLayer.getOrDefault(key, Collections.emptySet());

        // Prefer exact case match first if requested
        if (caseSensitive) {
            for (String s : b1) if (s.startsWith(prefix)) { results.add(s); if (results.size() >= limit) return new ArrayList<>(results); }
            for (String s : b2) if (s.startsWith(prefix)) { results.add(s); if (results.size() >= limit) return new ArrayList<>(results); }
        } else {
            for (String s : b1) if (s.toLowerCase(Locale.ROOT).startsWith(lower)) { results.add(s); if (results.size() >= limit) return new ArrayList<>(results); }
            for (String s : b2) if (s.toLowerCase(Locale.ROOT).startsWith(lower)) { results.add(s); if (results.size() >= limit) return new ArrayList<>(results); }
        }
        return new ArrayList<>(results);
    }

    // Convenience for KB bulk adds
    void addAllKB(Collection<String> tokens) { if (tokens == null) return; for (String t : tokens) addKB(t); }
}
