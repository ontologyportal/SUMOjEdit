package com.articulate.sigma.jedit.fastac;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.gjt.sp.jedit.jEdit;

public final class FastACBootstrap {

    private static final AtomicBoolean didRun = new AtomicBoolean(false);

    private static boolean popupEnabled() {
        String mode = jEdit.getProperty("sumo.autocomplete.mode", "both");
        return "popup".equalsIgnoreCase(mode) || "both".equalsIgnoreCase(mode);
    }

    private FastACBootstrap() {}

    /** Safe to call from anywhere, many times; it will only attach once. */
    public static void runOnce() {
        // Disable the old dropdown if the flag is off
        if (!popupEnabled()) return;

        if (!didRun.compareAndSet(false, true)) return; // already ran

        // Defer until the UI is up to ensure windows/components exist
        SwingUtilities.invokeLater(() -> {
            System.out.println("[FastAC] Bootstrapping autocomplete…");
            // Note: keep your existing attach call as-is.
            // If your class is actually FastACAutoattach (lower 'a'), use that name here.
            FastACAutoAttach.attachEverywhere(SumoWords.all());
        });
    }
}
