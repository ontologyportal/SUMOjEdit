package com.articulate.sigma.jedit;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.jedit.textarea.Selection;
import org.gjt.sp.jedit.jEdit;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.articulate.sigma.KB;

/**
 * AutoCompleteManager provides prefix-based suggestions and a caret-anchored
 * popup for SUMOjEdit. Suggestions are non-committal until TAB (or Enter if enabled) is pressed.
 *
 * Important jEdit notes:
 * - JEditTextArea is NOT a Swing JTextComponent; use Buffer for text access.
 * - Use Selection.Range + setSelectedText(...) to replace text.
 * - Arrow/Tab handling can be swallowed by the editor; we use a KeyEventDispatcher
 *   to ensure navigation and acceptance work reliably.
 */
public class AutoCompleteManager {

    private final View view;
    private final JEditTextArea textArea;
    private final KB kb; // used to seed SUMO/SUO-KIF symbols

    private final SuggestionIndex index = new SuggestionIndex();

    private final JPopupMenu popup = new JPopupMenu();
    private final JList<String> list = new JList<>();
    private final JScrollPane scroller = new JScrollPane(list);
    private final DefaultListModel<String> listModel = new DefaultListModel<>();

    // configuration
    private int minPrefix = 2;               // show only after this many chars
    private int maxSuggestions = 20;         // cap UI list size
    private boolean acceptOnEnter = false;   // set true if you want Enter to commit as well
    private boolean caseSensitive = false;   // case-insensitive by default

    // Safe regex (no escaping issues). '-' placed at end; '.' literal within class
    private final Pattern tokenPattern = Pattern.compile("[A-Za-z0-9_.-]+");

    // Global dispatcher reliably handles UP/DOWN/TAB/ESC even if editor consumes them
    private final KeyEventDispatcher dispatcher = new KeyEventDispatcher() {
        @Override public boolean dispatchKeyEvent(KeyEvent e) {
            // FIXED: Only handle keys when popup is actually visible
            if (!popup.isVisible()) return false;
            if (e.getID() != KeyEvent.KEY_PRESSED) return false;
            switch (e.getKeyCode()) {
                case KeyEvent.VK_TAB:
                    // Do not hijack Ctrl+Tab – reserved for Ghost Text accept
                    if (e.isControlDown()) {
                        // Let this event pass through so Ghost Text can handle it
                        return false;
                    }
                    // Only consume Tab if popup is showing suggestions
                    if (popup.isVisible() && listModel.size() > 0) {
                        e.consume();
                        acceptSelection();
                        return true;
                    }
                    // Let Tab pass through for indentation
                    return false;
                case KeyEvent.VK_ESCAPE:
                    e.consume();
                    hidePopup();
                    return true;
                case KeyEvent.VK_UP:
                    e.consume();
                    moveSelection(-1);
                    return true;
                case KeyEvent.VK_DOWN:
                    e.consume();
                    moveSelection(+1);
                    return true;
            }
            return false;
        }
    };

    // returns true if the current mode includes a popup dropdown
    private static boolean popupEnabled() {
        String mode = jEdit.getProperty("sumo.autocomplete.mode", "both");
        return "popup".equalsIgnoreCase(mode) || "both".equalsIgnoreCase(mode);
    }

    // Key handler to recompute suggestions after typing
    private final KeyAdapter keyHandler = new KeyAdapter() {
        @Override public void keyReleased(KeyEvent e) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_TAB:
                    // FIXED: Don't interfere with Tab when no popup is showing
                    if (!popup.isVisible()) {
                        // Let Tab do its normal indentation
                        return;
                    }
                    break;
                case KeyEvent.VK_ESCAPE:
                case KeyEvent.VK_UP:
                case KeyEvent.VK_DOWN:
                case KeyEvent.VK_ENTER:
                case KeyEvent.VK_SHIFT:
                case KeyEvent.VK_CONTROL:
                case KeyEvent.VK_ALT:
                case KeyEvent.VK_META:
                    return;
                default:
                    SwingUtilities.invokeLater(AutoCompleteManager.this::maybeShow);
            }
        }
        
        @Override public void keyPressed(KeyEvent e) {
            // FIXED: Only block Tab if popup is actually showing with suggestions
            if (e.getKeyCode() == KeyEvent.VK_TAB) {
                if (!popup.isVisible() || listModel.size() == 0) {
                    // No suggestions - let Tab do indentation
                    return;
                }
                // Otherwise the dispatcher will handle it
            }
        }
    };

    public AutoCompleteManager(View view, KB kb) {
        this.view = view;
        this.textArea = view.getEditPane().getTextArea();
        this.kb = kb;

        // ⛔ disable this dropdown manager entirely if popup is not enabled
        // if (!popupEnabled()) return;

        list.setModel(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(Math.min(maxSuggestions, 12));
        // Single-click to accept
        list.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                int idx = list.locationToIndex(e.getPoint());
                if (idx >= 0) {
                    list.setSelectedIndex(idx);
                    acceptSelection();
                }
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) acceptSelection();
            }
        });
        popup.setFocusable(false);
        popup.add(scroller);

        // hook listeners
        textArea.addKeyListener(keyHandler);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher);

        // prime index from KB and current buffer
        rebuildIndexFromKB();
        rebuildIndexFromBuffer();
    }

    /** Rebuild suggestions from the loaded KB (classes, relations, constants). */
    public void rebuildIndexFromKB() {
        if (kb == null) return;
        for (String t : kb.terms) {
            if (t != null && !t.isBlank()) index.addKB(t);
        }
    }

    /** Scan current buffer and add tokens to the index. */
    public void rebuildIndexFromBuffer() {
        Buffer buffer = view.getBuffer();
        if (buffer == null) return;
        String text = buffer.getText(0, buffer.getLength());
        Matcher m = tokenPattern.matcher(text);
        while (m.find()) {
            String tok = m.group();
            if (tok.length() >= minPrefix) index.add(tok);
        }
    }

    /** Optional: add a user dictionary file, one token per line. */
    public void loadUserDictionary(File f) {
        if (f == null || !f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) index.add(line);
            }
        } catch (Exception ignored) {}
    }

    /** Call when switching buffers to quickly refresh the buffer words. */
    public void refreshIndexOnBufferChange() {
        index.clearBufferLayer();
        rebuildIndexFromBuffer();
        hidePopup();
    }

    /** Dispose listeners when the plugin unloads. */
    public void dispose() {
        try { textArea.removeKeyListener(keyHandler); } catch (Exception ignored) {}
        try { KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher); } catch (Exception ignored) {}
        hidePopup();
    }

    // === Core UI logic ===

    private void maybeShow() {
        // return immediately if popup is not enabled
        if (!popupEnabled()) return;

        String prefix = currentWordPrefix();
        if (prefix.length() < minPrefix) { hidePopup(); return; }
        List<String> sugg = index.startsWith(prefix, maxSuggestions, caseSensitive);
        if (sugg.isEmpty()) { hidePopup(); return; }

        listModel.clear();
        for (String s : sugg) listModel.addElement(s);
        list.setSelectedIndex(0);

        Point p = caretAnchorPoint();
        if (p == null) { hidePopup(); return; }
        popup.show(textArea, p.x, p.y + textArea.getPainter().getFontMetrics().getHeight());
    }

    private void hidePopup() { popup.setVisible(false); }

    private void moveSelection(int delta) {
        int i = list.getSelectedIndex();
        int n = listModel.size();
        if (n == 0) return;
        int j = Math.max(0, Math.min(n - 1, i + delta));
        list.setSelectedIndex(j);
        list.ensureIndexIsVisible(j);
    }

    private void acceptSelection() {
        if (!popup.isVisible()) return;
        String pick = list.getSelectedValue();
        if (pick == null) return;
        replaceCurrentPrefixWith(pick);
        hidePopup();
    }

    private String currentWordPrefix() {
        Buffer buffer = view.getBuffer();
        if (buffer == null) return "";
        int caret = textArea.getCaretPosition();
        if (caret <= 0) return "";
        int line = textArea.getCaretLine();
        int lineStart = textArea.getLineStartOffset(line);
        int i = caret - 1;
        while (i >= lineStart) {
            char c = charAt(buffer, i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-' && c != '.') break;
            i--;
        }
        int prefixStart = i + 1;
        if (prefixStart >= caret) return "";
        return buffer.getText(prefixStart, caret - prefixStart);
    }

    private void replaceCurrentPrefixWith(String fullToken) {
        Buffer buffer = view.getBuffer();
        if (buffer == null) return;
        int caret = textArea.getCaretPosition();
        int line = textArea.getCaretLine();
        int lineStart = textArea.getLineStartOffset(line);
        int i = caret - 1;
        while (i >= lineStart) {
            char c = charAt(buffer, i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-' && c != '.') break;
            i--;
        }
        int prefixStart = i + 1;
        Selection.Range sel = new Selection.Range(prefixStart, caret);
        textArea.setSelection(sel);
        textArea.setSelectedText(fullToken);
    }

    private char charAt(Buffer buffer, int offset) {
        if (offset < 0 || offset >= buffer.getLength()) return '\0';
        String s = buffer.getText(offset, 1);
        return s.isEmpty() ? '\0' : s.charAt(0);
    }

    private Point caretAnchorPoint() {
        try {
            int caret = textArea.getCaretPosition();
            Point p = textArea.offsetToXY(caret);
            if (p == null) return new Point(0, textArea.getPainter().getFontMetrics().getHeight());
            return p;
        } catch (Exception e) {
            return new Point(0, textArea.getPainter().getFontMetrics().getHeight());
        }
    }
}