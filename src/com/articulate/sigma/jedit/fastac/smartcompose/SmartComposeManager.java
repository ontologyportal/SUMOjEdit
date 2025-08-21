package com.articulate.sigma.jedit.fastac.smartcompose;

import org.gjt.sp.jedit.textarea.TextArea;

public final class SmartComposeManager {
    private final TextArea ta;
    private String tail = "";
    private int tailStart = -1;

    SmartComposeManager(TextArea ta) { this.ta = ta; }

    String tail() { return tail; }
    int tailStart() { return tailStart; }

    void clear() {
        tail = "";
        tailStart = -1;
        ta.getPainter().repaint();
    }

    public void refresh() {
        int caret = ta.getCaretPosition();
        int line = ta.getCaretLine();
        int lineStart = ta.getLineStartOffset(line);
        int col = caret - lineStart;
        if (col <= 0) { clear(); return; }

        String whole = ta.getLineText(line);
        String prefix = whole.substring(0, Math.min(col, whole.length()));

        if (prefix.trim().isEmpty()) { clear(); return; }

        // Ask adapter for best completion
        String bestFull = TopCompletionAdapter.bestFull(ta, prefix);
        if (bestFull == null || !bestFull.startsWith(prefix)) { clear(); return; }

        String t = bestFull.substring(prefix.length());
        if (t.length() > 60) t = t.substring(0, 60);

        this.tail = t;
        this.tailStart = caret;
        ta.getPainter().repaint();
    }

    public boolean accept() {
        if (tail == null || tail.isEmpty()) return false;
        ta.getBuffer().insert(ta.getCaretPosition(), tail);
        clear();
        return true;
    }
}
