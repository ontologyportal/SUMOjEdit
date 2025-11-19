package com.articulate.sigma.jedit.fastac.smartcompose;

import org.gjt.sp.jedit.textarea.TextArea;
import com.articulate.sigma.jedit.fastac.SumoWords;

import java.util.Collection;

final class TopCompletionAdapter {

    /** Return the best full completion for prefix, or null. */
    static String bestFull(TextArea ta, String prefix) {
        if (prefix == null || prefix.isEmpty()) return null;

        // 1) Try the shared vocabulary
        try {
            Collection<String> vocab = SumoWords.all(); // same call used by FastACBootstrap
            if (vocab != null) {
                String best = null;
                for (String w : vocab) {
                    if (w != null && w.startsWith(prefix)) {
                        if (best == null || better(prefix, w, best)) best = w;
                    }
                }
                if (best != null) return best;
            }
        } catch (Throwable ignored) {
        }

        // 2) Fallback: scan current buffer tokens
        try {
            String text = ta.getBuffer().getText(0, ta.getBuffer().getLength());
            String best = null;
            int n = text.length(), i = 0;
            while (i < n) {
                while (i < n && !isWord(text.charAt(i))) i++;
                int start = i;
                while (i < n && isWord(text.charAt(i))) i++;
                int end = i;
                if (end > start) {
                    int len = end - start;
                    if (len >= prefix.length()) {
                        boolean match = true;
                        for (int k = 0; k < prefix.length(); k++) {
                            if (text.charAt(start + k) != prefix.charAt(k)) { match = false; break; }
                        }
                        if (match) {
                            String cand = text.substring(start, end);
                            if (best == null || better(prefix, cand, best)) best = cand;
                        }
                    }
                }
            }
            return best;
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static boolean isWord(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
    }

    /** Prefer candidates that match the prefix; for two matches prefer shorter, otherwise lexicographically smaller. */
    private static boolean better(String prefix, String a, String b) {
        // Defensive null handling: any non-null candidate beats null.
        if (a == null) return b != null;
        if (b == null) return false;

        // Determine whether each candidate starts with the prefix (if any).
        boolean hasPrefix = prefix != null && !prefix.isEmpty();
        boolean aMatches = hasPrefix && a.startsWith(prefix);
        boolean bMatches = hasPrefix && b.startsWith(prefix);

        // If only one candidate matches the prefix, that one is better.
        if (aMatches && !bMatches) return true;
        if (!aMatches && bMatches) return false;

        int la = a.length();
        int lb = b.length();

        // If both candidates match the prefix, prefer the shorter completion first.
        if (aMatches && bMatches && la != lb) {
            return la < lb;
        }

        // Otherwise (neither matches the prefix, or same length), fall back to
        // lexicographical comparison, case-insensitive first.
        int cmpIgnoreCase = a.compareToIgnoreCase(b);
        if (cmpIgnoreCase != 0) {
            return cmpIgnoreCase < 0;
        }

        // Tie-breaker: case-sensitive comparison.
        return a.compareTo(b) < 0;
    }
}
