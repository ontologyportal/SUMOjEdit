package com.articulate.sigma.jedit.fastac;

import org.gjt.sp.jedit.jEdit;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.List;

public final class FastACAutoAttach {

    private static boolean popupEnabled() {
        String mode = jEdit.getProperty("sumo.autocomplete.mode", "both");
        return "popup".equalsIgnoreCase(mode) || "both".equalsIgnoreCase(mode);
    }

    private FastACAutoAttach() {}

    /** Scans all frames/windows and attaches the popup suggestor to text editors.
     *  With ENABLE_POPUP=false, this method is a no-op. */
    public static void attachEverywhere(List<String> words) {
        if (!popupEnabled()) {
            System.out.println("[FastAC] Popup disabled by sumo.autocomplete.mode — skipping attachEverywhere().");
            return;
        }

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

    /** Recursively attach popup suggestor in a container tree. No-op if disabled. */
    private static void attachInContainer(Container c, List<String> words) {
        if (!popupEnabled()) return;

        Component[] comps = c.getComponents();
        for (Component comp : comps) {
            if (comp instanceof JTextComponent) {
                JTextComponent tc = (JTextComponent) comp;
                System.out.println("[FastAC] Found editor: " + tc.getClass().getName());
                // Keep your existing signature. This will be skipped when disabled.
                FastSuggestor.attach(SwingUtilities.getWindowAncestor(tc), tc, words);
            }
            if (comp instanceof Container) {
                attachInContainer((Container) comp, words);
            }
        }
    }
}
