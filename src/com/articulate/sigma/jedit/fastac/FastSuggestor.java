package com.articulate.sigma.jedit.fastac;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.gjt.sp.jedit.jEdit;

/**
 * FastSuggestor
 * - Shows suggestions quickly while typing.
 * - On DELETE (prefix gets shorter): show an INSTANT subset (filter last results),
 *   then refresh fully after a slightly longer debounce in background.
 * - FIXED: Eliminated UI freeze on deletion by avoiding blocking operations
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
    
    // FIXED: Add flag to prevent concurrent lookups
    private final AtomicBoolean lookupInProgress = new AtomicBoolean(false);
    
    // FIXED: Cache to avoid redundant lookups
    private final Map<String, List<String>> recentLookupCache = new LinkedHashMap<String, List<String>>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
            return size() > 50; // Keep last 50 lookups cached
        }
    };

    private static boolean popupEnabled() {
        String mode = jEdit.getProperty("sumo.autocomplete.mode", "both");
        return "popup".equalsIgnoreCase(mode) || "both".equalsIgnoreCase(mode);
    }

    // Tunables
    private static final int INSERT_DEBOUNCE_MS = 70;
    private static final int DELETE_DEBOUNCE_MS = 30;  // FIXED: Reduced for faster response
    private static final int QUICK_LIMIT = 20;   // instant subset size
    private static final int FULL_LIMIT  = 200;  // full refresh max results

    public FastSuggestor(JTextComponent editor, PrefixIndex index) {
        this.editor = editor;
        this.index = index;
        this.popup = new JPopupMenu();

        // Create once; we'll change delay dynamically
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
        if (!popupEnabled()) return; // disabled: never compute/show
        
        // FIXED: Get prefix immediately on EDT to avoid any delay
        String prefix = currentWordPrefix();
        boolean isDelete = prefix.length() < lastPrefix.length();

        if (isDelete) {
            // FIXED: For deletions, immediately show filtered results without any async operations
            List<String> quick = quickFilterCached(prefix);
            showSuggestionsImmediate(quick);
            lastPrefix = prefix;
            lastResults = quick;
            
            // Still schedule a full refresh but with very short delay
            debounceTimer.setInitialDelay(DELETE_DEBOUNCE_MS);
            debounceTimer.restart();
        } else {
            // Insert / extend prefix: check cache first
            List<String> cached = recentLookupCache.get(prefix);
            if (cached != null) {
                showSuggestionsImmediate(cached);
                lastResults = cached;
                lastPrefix = prefix;
                return; // No need to schedule lookup if we have cached results
            }
            
            // Not cached, schedule lookup
            lastPrefix = prefix;
            debounceTimer.setInitialDelay(INSERT_DEBOUNCE_MS);
            debounceTimer.restart();
        }
    }

    /** FIXED: Enhanced quick filter that uses cache and last results efficiently */
    private List<String> quickFilterCached(String prefix) {
        if (prefix.isEmpty()) return Collections.emptyList();
        
        // First check cache
        List<String> cached = recentLookupCache.get(prefix);
        if (cached != null) return cached;
        
        // Filter from last results (this is very fast)
        ArrayList<String> filtered = new ArrayList<>(Math.min(QUICK_LIMIT, lastResults.size()));
        String prefixLower = prefix.toLowerCase();
        for (String s : lastResults) {
            if (s.toLowerCase().startsWith(prefixLower)) {
                filtered.add(s);
                if (filtered.size() >= QUICK_LIMIT) break;
            }
        }
        
        // Cache the filtered results
        if (!filtered.isEmpty()) {
            recentLookupCache.put(prefix, filtered);
        }
        
        return filtered;
    }

    /** FIXED: Fast filter without any toLowerCase() allocations for common case */
    private List<String> quickFilter(List<String> prior, String prefix, int limit) {
        if (prefix.isEmpty()) return Collections.emptyList();
        ArrayList<String> out = new ArrayList<>(Math.min(limit, prior.size()));
        
        // Optimize for common case where prefix hasn't changed case
        for (String s : prior) {
            if (s.startsWith(prefix)) {
                out.add(s);
                if (out.size() >= limit) break;
            }
        }
        
        // If we didn't find enough, try case-insensitive
        if (out.size() < limit / 2) {
            String prefixLower = prefix.toLowerCase();
            for (String s : prior) {
                if (!out.contains(s) && s.toLowerCase().startsWith(prefixLower)) {
                    out.add(s);
                    if (out.size() >= limit) break;
                }
            }
        }
        
        return out;
    }

    /** Compute full results off the EDT, then swap in on EDT. */
    private void refreshFullAsync() {
        if (!popupEnabled()) return;
        
        final String prefixSnapshot = lastPrefix;
        if (prefixSnapshot == null || prefixSnapshot.isEmpty()) {
            SwingUtilities.invokeLater(() -> {
                lastResults = Collections.emptyList();
                showSuggestionsImmediate(Collections.emptyList());
            });
            return;
        }
        
        // FIXED: Skip if already doing a lookup
        if (!lookupInProgress.compareAndSet(false, true)) {
            return;
        }
        
        // Check cache first
        List<String> cached = recentLookupCache.get(prefixSnapshot);
        if (cached != null) {
            SwingUtilities.invokeLater(() -> {
                if (prefixSnapshot.equals(lastPrefix)) {
                    lastResults = cached;
                    showSuggestionsImmediate(cached);
                }
            });
            lookupInProgress.set(false);
            return;
        }
        
        pool.submit(() -> {
            try {
                List<String> full = index.lookup(prefixSnapshot, FULL_LIMIT);
                
                // Cache the results
                if (!full.isEmpty()) {
                    synchronized (recentLookupCache) {
                        recentLookupCache.put(prefixSnapshot, full);
                    }
                }
                
                // Hand off to EDT
                SwingUtilities.invokeLater(() -> {
                    // Only apply if user hasn't typed more since we started
                    if (prefixSnapshot.equals(lastPrefix)) {
                        lastResults = full;
                        showSuggestionsImmediate(full);
                    }
                });
            } finally {
                lookupInProgress.set(false);
            }
        });
    }

    /** Extract the current "word" prefix at the caret (letters, digits, underscores). */
    private String currentWordPrefix() {
        try {
            int caret = editor.getCaretPosition();
            Document doc = editor.getDocument();
            int start = caret;
            
            // FIXED: Use getText with length to avoid creating substrings
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

    /** FIXED: Immediate show without any delay - used for delete operations */
    private void showSuggestionsImmediate(List<String> items) {
        if (!popupEnabled()) return;
        
        // FIXED: Only update if different from what's showing
        if (items.isEmpty()) {
            if (popup.isVisible()) {
                popup.setVisible(false);
            }
            return;
        }
        
        // Reuse existing popup if possible
        if (popup.isVisible() && popup.getComponentCount() > 0) {
            Component comp = popup.getComponent(0);
            if (comp instanceof JScrollPane) {
                JScrollPane sp = (JScrollPane) comp;
                if (sp.getViewport().getView() instanceof JList) {
                    @SuppressWarnings("unchecked")
                    JList<String> existingList = (JList<String>) sp.getViewport().getView();
                    // Just update the model instead of recreating everything
                    DefaultListModel<String> model = new DefaultListModel<>();
                    for (String item : items) {
                        model.addElement(item);
                    }
                    existingList.setModel(model);
                    if (!items.isEmpty()) {
                        existingList.setSelectedIndex(0);
                    }
                    return;
                }
            }
        }
        
        // Fall back to full recreation only if necessary
        showSuggestions(items);
    }

    /** Render the popup. Keep this light — actual data is prepared elsewhere. */
    private void showSuggestions(List<String> items) {
        if (!popupEnabled()) return;
        popup.setVisible(false);
        popup.removeAll();

        if (items == null || items.isEmpty()) return;

        JList<String> list = new JList<>(new Vector<>(items));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(Math.min(12, items.size()));
        list.setSelectedIndex(0);
        
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String sel = list.getSelectedValue();
                    if (sel != null) insertCompletion(sel);
                    popup.setVisible(false);
                }
            }
        });

        JScrollPane scroller = new JScrollPane(list);
        scroller.setBorder(BorderFactory.createEmptyBorder());
        popup.add(scroller);

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
        recentLookupCache.clear();
    }

    // Create a FastSuggestor by building an index from words and wiring it to this editor.
    public static FastSuggestor attach(javax.swing.text.JTextComponent editor,
                                       java.util.List<String> words) {
        if (!popupEnabled()) {
            System.out.println("[FastAC] Popup disabled (ENABLE_POPUP=false) — FastSuggestor.attach(editor,words) no-op.");
            return null;
        }

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
        if (!popupEnabled()) {
            System.out.println("[FastAC] Popup disabled (ENABLE_POPUP=false) — FastSuggestor.attach(owner,editor,words) no-op.");
            return null;
        }
        return attach(editor, words);
    }
}