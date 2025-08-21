package com.articulate.sigma.jedit.fastac.smartcompose;

import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.msg.ViewUpdate;
import org.gjt.sp.jedit.textarea.*;

import java.awt.event.*;

public final class SmartComposeBootstrap implements EBComponent {

    public static void start() { EditBus.addToBus(new SmartComposeBootstrap()); }
    public static void stop()  { EditBus.removeFromBus(new SmartComposeBootstrap()); }

    @Override
    public void handleMessage(EBMessage msg) {
        if (msg instanceof ViewUpdate) {
            ViewUpdate vu = (ViewUpdate) msg;
            if (vu.getWhat() == ViewUpdate.CREATED) {
                install(vu.getView().getTextArea());
            }
        }
    }

    private void install(TextArea ta) {
        SmartComposeManager mgr = new SmartComposeManager(ta);
        ta.getPainter().addExtension(TextAreaPainter.LOWEST_LAYER,
                                     new SmartComposeExtension(ta, mgr));

        ta.addKeyListener(new KeyAdapter() {
            @Override public void keyTyped(KeyEvent e) { mgr.refresh(); }
            @Override public void keyReleased(KeyEvent e) { mgr.refresh(); }
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_TAB) {
                    if (mgr.accept()) e.consume();
                }
            }
        });

        ta.addCaretListener(e -> mgr.refresh());
        ta.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) { mgr.clear(); }
        });
    }
}
