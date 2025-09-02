// src/com/articulate/sigma/jedit/SUOKifCompletionHandler.java
package com.articulate.sigma.jedit;

import com.articulate.sigma.Formula;

import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.jedit.msg.EditorStarted;
import org.gjt.sp.jedit.jEdit;

import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.jedit.textarea.TextAreaExtension;
import org.gjt.sp.jedit.textarea.TextAreaPainter;
import org.gjt.sp.jedit.buffer.JEditBuffer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SUO-KIF inline SmartCompose for SUMOjEdit.
 *
 * - Renders faint gray "ghost" suffix after caret.
 * - Accept with TAB or RIGHT; cancel with ESC (only when ghost mode is enabled).
 * - Candidates: current buffer tokens + SUO-KIF operator groups from Formula.
 *
 * NOTE (2025-08): Ghost-AC obeys sumojedit.ac.mode from SUMOjEdit.props:
 *   OFF           -> disabled
 *   GHOST_ONLY    -> enabled
 *   DROPDOWN_ONLY -> disabled
 *   BOTH          -> enabled
 *
 * IMPORTANT: To avoid interfering with the drop-down AC, we only install
 * ghost key bindings when ghost mode is enabled, and unregister them otherwise.
 */
public final class SUOKifCompletionHandler implements EBComponent {

    // static flag removed; ghost mode is controlled by sumojedit.ac.mode
    private static final int     MAX_SCAN_CHARS  = 250_000;

    private static final Map<String, List<String>> SUO_KIF_KEYWORD_GROUPS = new LinkedHashMap<>();
    static {
        SUO_KIF_KEYWORD_GROUPS.put("Logical",     Formula.LOGICAL_OPERATORS);
        SUO_KIF_KEYWORD_GROUPS.put("Definition",  Formula.DEFN_PREDICATES);
        SUO_KIF_KEYWORD_GROUPS.put("Comparison",  Formula.COMPARISON_OPERATORS);
        SUO_KIF_KEYWORD_GROUPS.put("Math",        Formula.MATH_FUNCTIONS);
        SUO_KIF_KEYWORD_GROUPS.put("Document",    Formula.DOC_PREDICATES);
    }

    /** One overlay per text area (weak to avoid leaks) */
    private final Map<JEditTextArea, GhostOverlay> overlays = new WeakHashMap<>();
    private boolean dispatcherInstalled = false;

    /** AC-mode helper: ghost text allowed only in GHOST_ONLY or BOTH */
    private static boolean ghostACEnabled() {
        String m = jEdit.getProperty("sumojedit.ac.mode", "BOTH");
        return "GHOST_ONLY".equals(m) || "BOTH".equals(m);
    }

    public SUOKifCompletionHandler() {
        EditBus.addToBus(this);
        installGlobalDispatcherOnce();
    }

    @Override
    public void handleMessage(EBMessage msg) {
        if (msg instanceof EditorStarted) {
            for (View v : jEdit.getViews()) attach(v);
        } else if (msg instanceof BufferUpdate) {
            BufferUpdate bu = (BufferUpdate) msg;
            if (bu.getWhat() == BufferUpdate.LOADED || bu.getWhat() == BufferUpdate.CREATED) {
                View v = jEdit.getActiveView();
                if (v != null) attach(v);
            }
        }
    }

    // ===== attach and wire a view's text area =====
    private void attach(View view) {
        if (view == null) return;
        final JEditTextArea ta = view.getTextArea();
        if (ta == null) return;

        // 1) Painter overlay
        GhostOverlay overlay = overlays.get(ta);
        if (overlay == null) {
            overlay = new GhostOverlay(ta);
            ta.getPainter().addExtension(TextAreaPainter.HIGHEST_LAYER, overlay);
            overlays.put(ta, overlay);
        }

        // 2) Recompute listener (typing)
        boolean hasInlineListener = false;
        for (KeyListener kl : ta.getKeyListeners()) {
            if (kl instanceof InlineRecomputeListener) { hasInlineListener = true; break; }
        }
        if (!hasInlineListener) {
            ta.addKeyListener(new InlineRecomputeListener(view, overlay));
        }

        // 3) Install or remove ghost key bindings based on current mode
        installOrRemoveGhostKeyBindings(ta, overlay);

        // Initial compute/paint (will clear if ghost is disabled)
        overlay.recompute();
        overlay.repaintNow();
    }

    // ----- component key bindings (install only when ghost enabled) -----
    private static void installComponentKeyBindings(JEditTextArea ta, GhostOverlay overlay) {
        // clear stale first (defensive)
        unregisterGhostKeys(ta);

        // FIXED: Only consume Tab when there's actually a ghost suggestion
        registerAction(ta, "smartcompose-accept-tab",
            KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0),
            () -> { 
                if (overlay.hasGhost()) {
                    overlay.acceptIfAvailable();
                    return true; // consume
                }
                return false; // don't consume - let Tab do indentation
            });

        registerAction(ta, "smartcompose-accept-right",
            KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0),
            () -> { 
                if (overlay.hasGhost()) {
                    overlay.acceptIfAvailable();
                    return true;
                }
                return false;
            });

        registerAction(ta, "smartcompose-cancel-esc",
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            () -> { 
                if (overlay.hasGhost()) { 
                    overlay.clear(); 
                    overlay.repaintNow();
                    return true;
                }
                return false;
            });

        // Only disable focus traversal when we have a ghost suggestion
        ta.setFocusTraversalKeysEnabled(false);
    }

    private static void installOrRemoveGhostKeyBindings(JEditTextArea ta, GhostOverlay overlay) {
        if (ghostACEnabled()) {
            installComponentKeyBindings(ta, overlay);
        } else {
            unregisterGhostKeys(ta);
            // restore default traversal so Tab behaves normally for other features/popups
            ta.setFocusTraversalKeysEnabled(true);
        }
    }

    private static void unregisterGhostKeys(JEditTextArea ta) {
        ta.unregisterKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));
        ta.unregisterKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0));
        ta.unregisterKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
    }

    // FIXED: Updated to handle boolean return values
    private static void registerAction(JEditTextArea ta, String name, KeyStroke ks, java.util.function.BooleanSupplier r) {
        Action action = new AbstractAction(name) {
            @Override 
            public void actionPerformed(ActionEvent e) { 
                boolean consumed = r.getAsBoolean();
                if (!consumed && e.getSource() instanceof JComponent) {
                    // If not consumed, let the event continue to other handlers
                    ((JComponent)e.getSource()).getInputMap().remove(ks);
                    KeyboardFocusManager.getCurrentKeyboardFocusManager()
                        .redispatchEvent(ta, new KeyEvent(ta, 
                            KeyEvent.KEY_PRESSED, 
                            System.currentTimeMillis(), 
                            0, 
                            ks.getKeyCode(), 
                            KeyEvent.CHAR_UNDEFINED));
                    ((JComponent)e.getSource()).getInputMap().put(ks, name);
                }
            }
        };
        // Bind both WHEN_FOCUSED and WHEN_ANCESTOR_OF_FOCUSED to be extra safe
        ta.registerKeyboardAction(action, name, ks, JComponent.WHEN_FOCUSED);
        ta.registerKeyboardAction(action, name, ks, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    // ----- global dispatcher (fallback; respects ghostACEnabled at runtime) -----
    private void installGlobalDispatcherOnce() {
        if (dispatcherInstalled) return;
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .addKeyEventDispatcher(new GhostKeyDispatcher());
        dispatcherInstalled = true;
    }

    private static boolean isKif(View view) {
        if (view == null) return false;
        Buffer buf = view.getBuffer();
        String path = (buf != null ? buf.getPath() : null);
        return (path != null && path.toLowerCase().endsWith(".kif"));
    }

    /** Recompute after typing; accept/cancel handled by bindings/dispatcher */
    private static final class InlineRecomputeListener extends KeyAdapter {
        private final View view;
        private final GhostOverlay overlay;
        InlineRecomputeListener(View view, GhostOverlay overlay) { this.view = view; this.overlay = overlay; }

        @Override
        public void keyReleased(KeyEvent e) {
            // Keep key bindings in sync with current mode on every keystroke (cheap & robust)
            JEditTextArea ta = view.getTextArea();
            if (ta != null) installOrRemoveGhostKeyBindings(ta, overlay);

            // do nothing if not a .kif file or ghost mode disabled
            if (!isKif(view) || !ghostACEnabled()) {
                // If disabled, clear any lingering ghost
                if (!ghostACEnabled()) { overlay.clear(); overlay.repaintNow(); }
                return;
            }
            int kc = e.getKeyCode();
            if (kc == KeyEvent.VK_SHIFT || kc == KeyEvent.VK_CONTROL || kc == KeyEvent.VK_ALT ||
                kc == KeyEvent.VK_META  || kc == KeyEvent.VK_ESCAPE  || kc == KeyEvent.VK_TAB ||
                kc == KeyEvent.VK_RIGHT) {
                return;
            }
            overlay.recompute();
            overlay.repaintNow();
        }
    }

    /** Fallback: pre-empt keys before jEdit if needed (does nothing when ghost disabled). */
    private final class GhostKeyDispatcher implements KeyEventDispatcher {
        @Override
        public boolean dispatchKeyEvent(KeyEvent e) {
            // ghost mode disabled? then ignore
            if (!ghostACEnabled()) return false;
            if (e.getID() != KeyEvent.KEY_PRESSED && e.getID() != KeyEvent.KEY_TYPED) return false;

            // Focused component -> nearest JEditTextArea ancestor
            Component fo = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (fo == null) return false;
            JEditTextArea ta = (JEditTextArea) SwingUtilities.getAncestorOfClass(JEditTextArea.class, fo);
            if (ta == null) return false;

            GhostOverlay overlay = overlays.get(ta);
            // FIXED: Only intercept Tab if there's actually a ghost suggestion
            if (overlay == null || !overlay.hasGhost()) return false;

            // Only in .kif buffers
            View view = jEdit.getActiveView();
            if (view == null || !isKif(view)) return false;

            int code = e.getKeyCode();
            char ch = e.getKeyChar();

            // Accept on VK_TAB or on typed '\t' - ONLY if there's a ghost
            boolean isTab = (code == KeyEvent.VK_TAB) || (code == KeyEvent.VK_UNDEFINED && ch == '\t');

            if (e.getID() == KeyEvent.KEY_PRESSED && (isTab || code == KeyEvent.VK_RIGHT)) {
                if (overlay.acceptIfAvailable()) {
                    e.consume();
                    return true;
                }
                return false;
            }

            if (e.getID() == KeyEvent.KEY_PRESSED && code == KeyEvent.VK_ESCAPE) {
                overlay.clear();
                overlay.repaintNow();
                e.consume();
                return true;
            }

            return false;
        }
    }

    // ===== overlay painter =====
    private static final class GhostOverlay extends TextAreaExtension {
        private final JEditTextArea ta;
        private String ghost = "";
        private int anchorCaret = -1;

        GhostOverlay(JEditTextArea ta) { this.ta = ta; }

        boolean hasGhost() { return ghost != null && !ghost.isEmpty(); }

        void clear() { ghost = ""; anchorCaret = -1; }

        void repaintNow() { ta.getPainter().repaint(); }

        boolean acceptIfAvailable() {
            if (!ghostACEnabled() || !hasGhost()) return false;
            final JEditBuffer buffer = ta.getBuffer();
            if (buffer == null) return false;
            final int caret = ta.getCaretPosition();
            buffer.beginCompoundEdit();
            try {
                buffer.insert(caret, ghost);
            } finally {
                buffer.endCompoundEdit();
            }
            ta.setCaretPosition(caret + ghost.length());
            clear();
            return true;
        }

        void recompute() {
            if (!ghostACEnabled()) { clear(); return; }

            final JEditBuffer buf = ta.getBuffer();
            if (buf == null) { clear(); return; }

            final int caret = ta.getCaretPosition();
            final String prefix = getCurrentPrefix(ta);
            if (prefix.isEmpty()) { clear(); return; }

            final Set<String> candidates = new LinkedHashSet<>();
            collectBufferTokens(buf, candidates, MAX_SCAN_CHARS);
            SUO_KIF_KEYWORD_GROUPS.values().forEach(candidates::addAll);

            String best = null; int bestExtra = Integer.MAX_VALUE;
            for (String cand : candidates) {
                if (cand == null || cand.length() <= prefix.length()) continue;
                if (cand.regionMatches(true, 0, prefix, 0, prefix.length())) {
                    int extra = cand.length() - prefix.length();
                    if (extra < bestExtra || (extra == bestExtra && (best == null || cand.compareTo(best) < 0))) {
                        best = cand; bestExtra = extra;
                        if (bestExtra == 1) break;
                    }
                }
            }
            if (best != null) {
                ghost = best.substring(prefix.length());
                anchorCaret = caret;
            } else {
                clear();
            }
        }

        private static void collectBufferTokens(JEditBuffer buf, Set<String> out, int maxChars) {
            try {
                int len = Math.min(buf.getLength(), Math.max(64_000, maxChars));
                if (len <= 0) return;
                String text = buf.getText(0, len);
                int n = text.length();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < n; i++) {
                    char c = text.charAt(i);
                    if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                        sb.append(c);
                    } else {
                        if (sb.length() > 0) { out.add(sb.toString()); sb.setLength(0); }
                    }
                }
                if (sb.length() > 0) out.add(sb.toString());
            } catch (Throwable ignore) {}
        }

        @Override
        public void paintValidLine(Graphics2D g, int screenLine, int physicalLine,
                                   int start, int end, int y) {
            if (!ghostACEnabled() || !hasGhost()) return;
            int caret = ta.getCaretPosition();
            if (anchorCaret != caret) return;
            if (physicalLine != ta.getCaretLine()) return;

            Point p = ta.offsetToXY(caret);
            if (p == null) return;

            TextAreaPainter painter = ta.getPainter();
            Font font = painter.getFont();
            if (font == null) font = g.getFont();
            g.setFont(font);

            Color old = g.getColor();
            g.setColor(new Color(128, 128, 128, 160));
            int baseline = y + painter.getFontMetrics().getAscent();
            g.drawString(ghost, p.x, baseline);
            g.setColor(old);
        }
    }

    // ===== prefix extraction =====
    private static String getCurrentPrefix(JEditTextArea textArea) {
        final JEditBuffer buf = textArea.getBuffer();
        if (buf == null) return "";
        int caret = textArea.getCaretPosition();
        int start = caret;
        while (start > 0) {
            String ch = buf.getText(start - 1, 1);
            if (ch == null || ch.isEmpty()) break;
            char c = ch.charAt(0);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-')) break;
            start--;
        }
        int len = caret - start;
        if (len <= 0) return "";
        return buf.getText(start, len);
    }
}