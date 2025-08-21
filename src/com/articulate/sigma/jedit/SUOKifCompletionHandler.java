// src/com/articulate/sigma/jedit/SUOKifCompletionHandler.java
package com.articulate.sigma.jedit;

import com.articulate.sigma.Formula;

import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.jedit.msg.EditorStarted;

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
 * - Accept with TAB or RIGHT; cancel with ESC.
 * - Candidates: current buffer tokens + SUO-KIF operator groups from Formula.
 *
 * Tab acceptance reliability:
 * - Installs component-level key bindings on JEditTextArea (registerKeyboardAction).
 * - Also installs a global KeyEventDispatcher as a fallback.
 * - Disables focus traversal on the text area so TAB reaches us.
 */
public final class SUOKifCompletionHandler implements EBComponent {

    private static final boolean ENABLE_INLINE   = true;
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

        // 3) Component-level key bindings to guarantee Tab/Right/Esc
        installComponentKeyBindings(ta, overlay);

        // 4) Ensure Tab reaches us (not eaten by focus traversal)
        ta.setFocusTraversalKeysEnabled(false);

        // Initial compute/paint
        overlay.recompute();
        overlay.repaintNow();
    }

    // ----- component key bindings (highest reliability) -----
    private static void installComponentKeyBindings(JEditTextArea ta, GhostOverlay overlay) {
        // Clear any stale bindings we previously added (defensive)
        unregisterAction(ta, "smartcompose-accept-tab");
        unregisterAction(ta, "smartcompose-accept-right");
        unregisterAction(ta, "smartcompose-cancel-esc");

        registerAction(ta, "smartcompose-accept-tab",
            KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0),
            () -> { if (overlay.hasGhost()) overlay.acceptIfAvailable(); });

        registerAction(ta, "smartcompose-accept-right",
            KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0),
            () -> { if (overlay.hasGhost()) overlay.acceptIfAvailable(); });

        registerAction(ta, "smartcompose-cancel-esc",
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            () -> { if (overlay.hasGhost()) { overlay.clear(); overlay.repaintNow(); } });
    }

    private static void registerAction(JEditTextArea ta, String name, KeyStroke ks, Runnable r) {
        Action action = new AbstractAction(name) {
            @Override public void actionPerformed(ActionEvent e) { r.run(); }
        };
        // Bind both WHEN_FOCUSED and WHEN_ANCESTOR_OF_FOCUSED to be extra safe
        ta.registerKeyboardAction(action, name, ks, JComponent.WHEN_FOCUSED);
        ta.registerKeyboardAction(action, name, ks, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private static void unregisterAction(JEditTextArea ta, String name) {
        // There’s no direct unregister by name; we reset the InputMap/ActionMap pairs for our keystrokes in registerAction,
        // so here we do nothing. Left as a stub for clarity.
    }

    // ----- global dispatcher (fallback) -----
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
            if (!ENABLE_INLINE || !isKif(view)) return;
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

    /** Fallback: pre-empt keys before jEdit if needed. */
    private final class GhostKeyDispatcher implements KeyEventDispatcher {
        @Override
        public boolean dispatchKeyEvent(KeyEvent e) {
            if (!ENABLE_INLINE) return false;
            if (e.getID() != KeyEvent.KEY_PRESSED && e.getID() != KeyEvent.KEY_TYPED) return false;

            // Focused component -> nearest JEditTextArea ancestor
            Component fo = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (fo == null) return false;
            JEditTextArea ta = (JEditTextArea) SwingUtilities.getAncestorOfClass(JEditTextArea.class, fo);
            if (ta == null) return false;

            GhostOverlay overlay = overlays.get(ta);
            if (overlay == null || !overlay.hasGhost()) return false;

            // Only in .kif buffers
            View view = jEdit.getActiveView();
            if (view == null || !isKif(view)) return false;

            int code = e.getKeyCode();
            char ch = e.getKeyChar();

            // Accept on VK_TAB or on typed '\t' (some platforms map Tab to keyChar)
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
            if (!hasGhost()) return false;
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
            if (!hasGhost()) return;
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
