package com.articulate.sigma.jedit.fastac;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.List;

public final class FastACAutoAttach {
    private FastACAutoAttach() {}

    public static void attachEverywhere(List<String> words) {
        System.out.println("[FastAC] Scanning frames for text editors...");
        Frame[] frames = Frame.getFrames();
        for (Frame f : frames) {
            if (f != null && f.isDisplayable()) {
                attachInContainer(f, words);
                // also check owned windows (dialogs, etc.)
                Window[] owned = f.getOwnedWindows();
                for (Window w : owned) {
                    if (w != null && w.isDisplayable()) {
                        attachInContainer(w, words);
                    }
                }
            }
        }
    }

    private static void attachInContainer(Container c, List<String> words) {
        Component[] comps = c.getComponents();
        for (Component comp : comps) {
            if (comp instanceof JTextComponent) { // Java 11 style
                JTextComponent tc = (JTextComponent) comp; // explicit cast
                System.out.println("[FastAC] Found editor: " + tc.getClass().getName());
                FastSuggestor.attach(SwingUtilities.getWindowAncestor(tc), tc, words);
            }
            if (comp instanceof Container) {
                attachInContainer((Container) comp, words);
            }
        }
    }
}
