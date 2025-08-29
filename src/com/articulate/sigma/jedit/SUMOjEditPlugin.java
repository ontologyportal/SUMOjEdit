package com.articulate.sigma.jedit;

import org.gjt.sp.jedit.EBComponent;
import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.EditPlugin;
import org.gjt.sp.jedit.jEdit;

import com.articulate.sigma.jedit.fastac.FastACBootstrap;

/**
 * The SUMOjEdit plugin launcher.
 *
 * This class initializes the SUMOjEdit plugin and conditionally
 * starts autocomplete features based on the sumo.autocomplete.mode property.
 *
 * Acceptable values for sumo.autocomplete.mode are:
 *   - "off"          : disable both popup and ghost completions
 *   - "popup"        : enable drop‑down popup completion only
 *   - "ghost_only"   : enable inline ghost completion only
 *   - "both"         : enable both popup and ghost completions
 *
 * If the property is not set, the plugin defaults to "both".
 */
public class SUMOjEditPlugin extends EditPlugin {
    /**
     * The plugin name and option prefix used by jEdit.
     */
    public static final String NAME = "sumojedit";
    public static final String OPTION_PREFIX = "options." + NAME + ".";

    /**
     * jEdit property key controlling autocomplete mode.
     */
    public static final String PROP_AC_MODE = "sumo.autocomplete.mode";

    private EBComponent sje;
    private EBComponent sjech;

    @Override
    public void start() {
        // Ensure a default mode is set if none exists.
        if (jEdit.getProperty(PROP_AC_MODE) == null) {
            jEdit.setProperty(PROP_AC_MODE, "both");
        }

        // Start the main SUMOjEdit component and allow KBs to load asynchronously.
        sje = new SUMOjEdit();
        ((SUMOjEdit) sje).startThread(((SUMOjEdit) sje));
        EditBus.addToBus(sje);

        // Read the autocomplete mode and normalize to lower case.
        String mode = jEdit.getProperty(PROP_AC_MODE, "both").toLowerCase();

        // Start drop‑down popup only when mode is "popup" or "both".
        if ("popup".equals(mode) || "both".equals(mode)) {
            FastACBootstrap.runOnce();
        }

        // Start inline ghost completion only when mode is "ghost_only" or "both".
        if ("ghost_only".equals(mode) || "both".equals(mode)) {
            sjech = new SUOKifCompletionHandler();
            EditBus.addToBus(sjech);
        }
    }

    @Override
    public void stop() {
        // Remove the main SUMOjEdit component from the bus.
        EditBus.removeFromBus(sje);

        // Remove the inline completion handler if it was started.
        if (sjech != null) {
            EditBus.removeFromBus(sjech);
            sjech = null;
        }
    }

    /** JavaBean accessor for the plugin component. */
    public EBComponent getSje() {
        return sje;
    }

    // Uncomment if you need to expose the inline handler.
    // public EBComponent getSjech() { return sjech; }
}