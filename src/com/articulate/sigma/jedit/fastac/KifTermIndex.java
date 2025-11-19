// src/com/articulate/sigma/jedit/fastac/KifTermIndex.java
package com.articulate.sigma.jedit.fastac;

import org.gjt.sp.jedit.Buffer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lightweight index of SUMO terms from .kif files.
 * - Lazy singleton
 * - Fast regex tokenization ([A-Za-z][A-Za-z0-9_-]+)
 * - De-duped, case-preserving
 * - Thread-safe, read-mostly
 *
 * Roots:
 *  1) The current buffer's directory (walks up to project root) and its subfolders
 *  2) Optional env var SUMO_KIF_PATHS (':' separated absolute paths)
 */
public final class KifTermIndex {

    private static final KifTermIndex INSTANCE = new KifTermIndex();

    // Token pattern for SUMO symbol names (predicates, classes, constants)
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z][A-Za-z0-9_-]+");

    // Index storage: case-preserving; HashSet for O(1) lookups
    private final ConcurrentSkipListSet<String> terms =
            new ConcurrentSkipListSet<>(String.CASE_INSENSITIVE_ORDER);

    // Guard to avoid re-indexing too often
    private final AtomicBoolean builtOnce = new AtomicBoolean(false);
    private volatile long lastBuildMs = 0L;
    private static final long REBUILD_MIN_INTERVAL_MS = 30_000; // throttle

    private KifTermIndex() {}

    public static KifTermIndex get() {
        return INSTANCE;
    }

    /** Suggest up to 'limit' terms that start with prefix (case-insensitive). */
    public List<String> suggest(String prefix, int limit) {
        if (prefix == null || prefix.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }

        String prefLower = prefix.toLowerCase(Locale.ROOT);

        // Partition matches into three groups:
        //  1) exact (case-insensitive) matches
        //  2) longer matches that continue with an alphanumeric character
        //  3) longer matches that continue with a non-alphanumeric character
        List<String> exact = new ArrayList<>();
        List<String> alnum = new ArrayList<>();
        List<String> other = new ArrayList<>();

        int prefLen = prefix.length();

        for (String t : terms) {
            if (!t.regionMatches(true, 0, prefLower, 0, prefLen)) {
                continue;
            }

            String tLower = t.toLowerCase(Locale.ROOT);

            if (tLower.length() == prefLen) {
                exact.add(t);
            } else if (tLower.length() > prefLen) {
                char next = tLower.charAt(prefLen);
                if (Character.isLetterOrDigit(next)) {
                    alnum.add(t);
                } else {
                    other.add(t);
                }
            } else {
                // Should not happen if regionMatches above is true, but ignore defensively.
            }
        }

        exact.sort(String.CASE_INSENSITIVE_ORDER);
        alnum.sort(String.CASE_INSENSITIVE_ORDER);
        other.sort(String.CASE_INSENSITIVE_ORDER);

        List<String> out = new ArrayList<>(Math.min(limit, exact.size() + alnum.size() + other.size()));
        for (String t : exact) {
            out.add(t);
            if (out.size() >= limit) return out;
        }
        for (String t : alnum) {
            out.add(t);
            if (out.size() >= limit) return out;
        }
        for (String t : other) {
            out.add(t);
            if (out.size() >= limit) return out;
        }

        return out;
    }

    /** Ensure the index exists; cheap if already built recently. */
    public void ensureIndexed(Buffer context) {
        long now = System.currentTimeMillis();
        if (!builtOnce.get() || now - lastBuildMs > REBUILD_MIN_INTERVAL_MS) {
            synchronized (this) {
                if (!builtOnce.get() || now - lastBuildMs > REBUILD_MIN_INTERVAL_MS) {
                    Set<File> roots = discoverRoots(context);
                    rebuild(roots);
                    builtOnce.set(true);
                    lastBuildMs = System.currentTimeMillis();
                }
            }
        }
    }

    // ---- internals ----

    private Set<File> discoverRoots(Buffer context) {
        LinkedHashSet<File> roots = new LinkedHashSet<>();

        // 1) From current buffer’s folder upward (two levels) and children
        try {
            if (context != null && context.getPath() != null) {
                File f = new File(context.getPath()).getParentFile();
                if (f != null) {
                    // up to 2 parents as "project roots"
                    for (int i = 0; i < 2 && f != null; i++, f = f.getParentFile()) {
                        roots.add(f);
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2) From env var (absolute paths, colon separated)
        String env = System.getenv("SUMO_KIF_PATHS");
        if (env != null && !env.trim().isEmpty()) {
            for (String p : env.split(":")) {
                File r = new File(p.trim());
                if (r.isDirectory()) roots.add(r);
            }
        }

        return roots;
    }

    private void rebuild(Set<File> roots) {
        ConcurrentSkipListSet<String> fresh =
                new ConcurrentSkipListSet<>(String.CASE_INSENSITIVE_ORDER);

        for (File root : roots) {
            walk(root, fresh, 40_000 /* per-file char cap */, 100_000 /* file cap */);
        }

        // swap
        terms.clear();
        terms.addAll(fresh);
    }

    private static void walk(File root, Set<String> out, int perFileChars, int maxFiles) {
        if (root == null || !root.exists()) return;
        Deque<File> dq = new ArrayDeque<>();
        dq.add(root);
        int files = 0;

        while (!dq.isEmpty() && files < maxFiles) {
            File f = dq.removeFirst();
            if (f.isDirectory()) {
                File[] kids = f.listFiles();
                if (kids != null) {
                    for (File k : kids) dq.addLast(k);
                }
                continue;
            }
            if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".kif")) continue;

            files++;
            extractTokens(f, out, perFileChars);
        }
    }

    private static void extractTokens(File file, Set<String> out, int limitChars) {
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buf = in.readNBytes(limitChars);
            String text = new String(buf, StandardCharsets.UTF_8);

            // Remove comments starting with ';' to end-of-line (KIF comment style)
            text = text.replaceAll("(?m);.*$", "");

            var m = TOKEN.matcher(text);
            while (m.find()) {
                String tok = m.group();
                // Simple pruning: skip all-digit tokens
                if (Character.isLetter(tok.charAt(0))) {
                    out.add(tok);
                }
            }
        } catch (Throwable ignored) {}
    }
}
