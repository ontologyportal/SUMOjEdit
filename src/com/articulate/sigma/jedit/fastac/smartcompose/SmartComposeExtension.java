package com.articulate.sigma.jedit.fastac.smartcompose;

import org.gjt.sp.jedit.textarea.TextArea;
import org.gjt.sp.jedit.textarea.TextAreaExtension;
import org.gjt.sp.jedit.textarea.TextAreaPainter;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;

final class SmartComposeExtension extends TextAreaExtension {
    private final TextArea ta;
    private final SmartComposeManager mgr;

    SmartComposeExtension(TextArea ta, SmartComposeManager mgr) {
        this.ta = ta;
        this.mgr = mgr;
    }

    @Override
    public void paintValidLine(Graphics2D g, int screenLine, int physicalLine, int start, int end, int y) {
        String tail = mgr.tail();
        if (tail == null || tail.isEmpty()) return;
        if (ta.getCaretLine() != physicalLine) return;

        Point p = ta.offsetToXY(ta.getCaretPosition());
        if (p == null) return;

        TextAreaPainter painter = ta.getPainter();
        FontMetrics fm = painter.getFontMetrics();

        Composite oc = g.getComposite();
        Color ocCol = g.getColor();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
        g.setColor(Color.GRAY);

        int baseline = y + fm.getAscent();
        g.drawString(tail, p.x, baseline);

        g.setComposite(oc);
        g.setColor(ocCol);
    }
}
