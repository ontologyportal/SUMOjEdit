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

    /** Prefer shorter completion, then lexicographically smaller. */
    private static boolean better(String prefix, String a, String b) {
        int la = a.length(), lb = b.length();
        if (la != lb) return la < lb;
        return a.compareTo(b) < 0;
    }
}
