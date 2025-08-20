package com.articulate.sigma.jedit.fastac;

import java.util.*;

public class PrefixIndex {
    private final Map<String, List<String>> map = new HashMap<>();
    private List<String> all = List.of();

    /** Build once from all SUMO words (predicates, classes, functions, constants, keywords). */
    public void build(List<String> words) {
        map.clear();
        all = new ArrayList<>(words);
        for (String w : words) {
            if (w == null || w.isEmpty()) continue;
            String s = w.toLowerCase(Locale.ROOT);
            int max = Math.min(4, s.length());
            for (int i = 1; i <= max; i++) {
                String p = s.substring(0, i);
                map.computeIfAbsent(p, k -> new ArrayList<>()).add(w);
            }
        }
        // sort each bucket for stable, nice ordering
        for (List<String> bucket : map.values()) {
            bucket.sort(String.CASE_INSENSITIVE_ORDER);
        }
    }

    /** Fast suggestions for the current token. */
    public List<String> suggest(String typed, int limit) {
        if (typed == null || typed.isEmpty()) return List.of();
        String key = typed.toLowerCase(Locale.ROOT);
        if (key.length() > 4) key = key.substring(0, 4);
        List<String> bucket = map.getOrDefault(key, List.of());

        // If user typed >4 chars, narrow inside the bucket.
        if (typed.length() > 4) {
            String low = typed.toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String w : bucket) {
                if (w.toLowerCase(Locale.ROOT).startsWith(low)) {
                    out.add(w);
                    if (out.size() >= limit) break;
                }
            }
            return out;
        }
        // Otherwise just cap the bucket.
        return bucket.size() <= limit ? bucket : bucket.subList(0, limit);
    }

    /** Optional: small-typo fallback when bucket is empty. */
    public List<String> fuzzy(String typed, int limit) {
        if (typed == null || typed.length() < 3) return List.of();
        String low = typed.toLowerCase(Locale.ROOT);
        List<Map.Entry<String,Integer>> scored = new ArrayList<>();
        for (String w : all) {
            String lw = w.toLowerCase(Locale.ROOT);
            int d = boundedEditDistance(low, lw, 2); // only small typos
            if (d >= 0) scored.add(Map.entry(w, d));
        }
        scored.sort(Comparator.<Map.Entry<String,Integer>>comparingInt(Map.Entry::getValue)
                .thenComparing(e -> e.getKey(), String.CASE_INSENSITIVE_ORDER));
        List<String> out = new ArrayList<>(Math.min(limit, scored.size()));
        for (int i = 0; i < Math.min(limit, scored.size()); i++) out.add(scored.get(i).getKey());
        return out;
    }

    private static int boundedEditDistance(String a, String b, int bound) {
        int n=a.length(), m=b.length();
        if (Math.abs(n-m) > bound) return -1;
        int[] prev = new int[m+1], curr = new int[m+1];
        for (int j=0;j<=m;j++) prev[j]=j;
        for (int i=1;i<=n;i++){
            curr[0]=i; int rowMin=curr[0];
            char ca=a.charAt(i-1);
            for (int j=1;j<=m;j++){
                int cost=(ca==b.charAt(j-1))?0:1;
                int v=Math.min(Math.min(curr[j-1]+1, prev[j]+1), prev[j-1]+cost);
                curr[j]=v; if (v<rowMin) rowMin=v;
            }
            if (rowMin>bound) return -1;
            int[] t=prev; prev=curr; curr=t;
        }
        return prev[m] <= bound ? prev[m] : -1;
    }

    // Return up to 'limit' suggestions for the given prefix.
    public java.util.List<String> lookup(String prefix, int limit) {
        java.util.List<String> all = lookup(prefix);  // use the existing all-matches method
        if (all == null || all.isEmpty() || limit >= all.size()) return all;
        return new java.util.ArrayList<>(all.subList(0, limit));
    }

    public java.util.List<String> lookup(String prefix) {
        return lookup(prefix, Integer.MAX_VALUE);
    }
}
