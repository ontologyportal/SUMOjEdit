package com.articulate.sigma.jedit.fastac;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FastSuggestor
 * - Shows suggestions quickly while typing.
 * - On DELETE (prefix gets shorter): show an INSTANT subset (filter last results),
 *   then refresh fully after a slightly longer debounce in background.
 */
public class FastSuggestor {

    private final JTextComponent editor;
    private final PrefixIndex index;
    private final JPopupMenu popup;

    // IMPORTANT: use Swing Timer explicitly to avoid ambiguity with java.util.Timer
    private final javax.swing.Timer debounceTimer;

    // Background pool for heavy lookups (never block EDT)
    private final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FastSuggestor-lookup");
        t.setDaemon(true);
        return t;
    });

    // State we keep between keystrokes
    private String lastPrefix = "";
    private List<String> lastResults = Collections.emptyList();

    // Tunables
    private static final int INSERT_DEBOUNCE_MS = 70;
    private static final int DELETE_DEBOUNCE_MS = 120;
    private static final int QUICK_LIMIT = 20;   // instant subset size
    private static final int FULL_LIMIT  = 200;  // full refresh max results

    public FastSuggestor(JTextComponent editor, PrefixIndex index) {
        this.editor = editor;
        this.index = index;
        this.popup = new JPopupMenu();

        // Create once; we’ll change delay dynamically
        debounceTimer = new javax.swing.Timer(INSERT_DEBOUNCE_MS, e -> refreshFullAsync());
        debounceTimer.setRepeats(false);

        // Listen to edits
        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onChange(e); }
            @Override public void removeUpdate(DocumentEvent e) { onChange(e); }
            @Override public void changedUpdate(DocumentEvent e) { /* attrib changes */ }
        });
    }

    private void onChange(DocumentEvent e) {
        String prefix = currentWordPrefix();
        boolean isDelete = prefix.length() < lastPrefix.length();

        if (isDelete) {
            // 1) INSTANT subset: filter previous results down to the new, shorter prefix
            List<String> quick = quickFilter(lastResults, prefix, QUICK_LIMIT);
            showSuggestions(quick);

            // 2) Debounce a full background recompute with a slightly longer delay
            debounceTimer.setInitialDelay(DELETE_DEBOUNCE_MS);
        } else {
            // Insert / extend prefix: normal shorter debounce
            debounceTimer.setInitialDelay(INSERT_DEBOUNCE_MS);
        }

        lastPrefix = prefix;
        // Restart the debounce to schedule a full recompute
        if (debounceTimer.isRunning()) debounceTimer.stop();
        debounceTimer.start();
    }

    /** Fast filter over lastResults to get valid-but-incomplete suggestions immediately. */
    private List<String> quickFilter(List<String> prior, String prefix, int limit) {
        if (prefix.isEmpty()) return Collections.emptyList();
        ArrayList<String> out = new ArrayList<>(Math.min(limit, prior.size()));
        for (String s : prior) {
            if (s.startsWith(prefix)) {
                out.add(s);
                if (out.size() >= limit) break;
            }
        }
        return out;
    }

    /** Compute full results off the EDT, then swap in on EDT. */
    private void refreshFullAsync() {
        final String prefixSnapshot = lastPrefix;
        if (prefixSnapshot == null || prefixSnapshot.isEmpty()) {
            SwingUtilities.invokeLater(() -> showSuggestions(Collections.emptyList()));
            lastResults = Collections.emptyList();
            return;
        }
        pool.submit(() -> {
            List<String> full = index.lookup(prefixSnapshot, FULL_LIMIT);
            // Hand off to EDT
            SwingUtilities.invokeLater(() -> {
                // Only apply if user hasn’t typed more since we started
                if (prefixSnapshot.equals(lastPrefix)) {
                    lastResults = full;
                    showSuggestions(full);
                }
            });
        });
    }

    /** Extract the current "word" prefix at the caret (letters, digits, underscores). */
    private String currentWordPrefix() {
        try {
            int caret = editor.getCaretPosition();
            Document doc = editor.getDocument();
            int start = caret;
            while (start > 0) {
                String ch = doc.getText(start - 1, 1);
                if (!isWordChar(ch)) break;
                start--;
            }
            int len = caret - start;
            if (len <= 0) return "";
            return doc.getText(start, len);
        } catch (BadLocationException ex) {
            return "";
        }
    }

    private boolean isWordChar(String s) {
        if (s == null || s.isEmpty()) return false;
        char c = s.charAt(0);
        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
    }

    /** Render the popup. Keep this light — actual data is prepared elsewhere. */
    private void showSuggestions(List<String> items) {
        popup.setVisible(false);
        popup.removeAll();

        if (items == null || items.isEmpty()) return;

        JList<String> list = new JList<>(new Vector<>(items));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(Math.min(12, items.size()));
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String sel = list.getSelectedValue();
                    if (sel != null) insertCompletion(sel);
                    popup.setVisible(false);
                }
            }
        });

        popup.add(new JScrollPane(list));

        try {
            Rectangle r = editor.modelToView(editor.getCaretPosition());
            if (r != null) {
                popup.show(editor, r.x, r.y + r.height);
            }
        } catch (BadLocationException ignored) { }
    }

    /** Replace current word with the chosen completion. */
    private void insertCompletion(String completion) {
        try {
            int caret = editor.getCaretPosition();
            Document doc = editor.getDocument();
            // Remove current word
            int start = caret;
            while (start > 0) {
                String ch = doc.getText(start - 1, 1);
                if (!isWordChar(ch)) break;
                start--;
            }
            doc.remove(start, caret - start);
            // Insert completion
            doc.insertString(start, completion, null);
        } catch (BadLocationException ignored) { }
    }

    /** Call when disposing the plugin. */
    public void shutdown() {
        debounceTimer.stop();
        pool.shutdownNow();
        popup.setVisible(false);
        popup.removeAll();
    }

    // Create a FastSuggestor by building an index from words and wiring it to this editor.
    public static FastSuggestor attach(javax.swing.text.JTextComponent editor,
                                       java.util.List<String> words) {
        PrefixIndex idx = new PrefixIndex();
        idx.build(words);
        
        FastSuggestor fs = new FastSuggestor(editor, idx);

        // Optional: hide popup on focus loss so it doesn't linger
        editor.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                fs.popup.setVisible(false);
            }
        });

        return fs;
    }

    // Overload used by FastACAutoAttach: accepts an owner Window and delegates.
    public static FastSuggestor attach(java.awt.Window owner,
                                       javax.swing.text.JTextComponent editor,
                                       java.util.List<String> words) {
        return attach(editor, words);
    }
}
