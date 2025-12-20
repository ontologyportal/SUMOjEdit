package com.articulate.sigma.jedit;

import org.gjt.sp.jedit.jEdit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Verify that ATP configuration settings in jEdit's property manager
 * can be overridden when a user revisits the configuration dialog.
 *
 * <p>The {@link SUMOjEdit#configureATP()} method writes a handful of
 * string-valued keys into jEdit properties when the user clicks
 * "Save Preferences".  Realistically users may change their choices
 * multiple times across sessions, so subsequent calls should simply
 * overwrite the prior values.  This test simulates that behaviour
 * without engaging the GUI by directly setting properties twice and
 * ensuring that the most recent values persist.</p>
 *
 * <p>We exercise a second set of representative values to avoid
 * duplicating the initial persistence test.  The initial assignment
 * uses one combination of settings; the second assignment uses a
 * different combination.  After the second setProperty call the
 * retrievals must reflect the second values, confirming that
 * previously saved selections don't interfere with new selections.</p>
 * 
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class ConfigureATPPropertyOverrideTest {

    private String origEngine;
    private String origMaxAnswers;
    private String origTimeLimit;
    private String origClosedWorld;
    private String origModusPonens;

    @Before
    public void saveOriginalProperties() {
        origEngine = jEdit.getProperty("sumojedit.atp.engine");
        origMaxAnswers = jEdit.getProperty("sumojedit.atp.maxAnswers");
        origTimeLimit = jEdit.getProperty("sumojedit.atp.timeLimitSec");
        origClosedWorld = jEdit.getProperty("sumojedit.atp.closedWorld");
        origModusPonens = jEdit.getProperty("sumojedit.atp.ModusPonens");
    }

    @After
    public void restoreOriginalProperties() {
        restoreProperty("sumojedit.atp.engine", origEngine);
        restoreProperty("sumojedit.atp.maxAnswers", origMaxAnswers);
        restoreProperty("sumojedit.atp.timeLimitSec", origTimeLimit);
        restoreProperty("sumojedit.atp.closedWorld", origClosedWorld);
        restoreProperty("sumojedit.atp.ModusPonens", origModusPonens);
    }

    /**
     * Helper to reset or unset a property. jEdit treats null as the
     * absence of a property, so we unset when the saved value is null;
     * otherwise we restore the original string.
     */
    private static void restoreProperty(String key, String value) {
        if (value != null) {
            jEdit.setProperty(key, value);
        } else {
            jEdit.unsetProperty(key);
        }
    }

    @Test
    public void testATPPropertiesOverride() {
        // First assignment: simulate an initial configuration choice
        jEdit.setProperty("sumojedit.atp.engine", "leo3");
        jEdit.setProperty("sumojedit.atp.maxAnswers", "5");
        jEdit.setProperty("sumojedit.atp.timeLimitSec", "123");
        jEdit.setProperty("sumojedit.atp.closedWorld", "true");
        jEdit.setProperty("sumojedit.atp.ModusPonens", "true");

        // Second assignment: user revisits preferences and chooses
        // entirely different values.  This should cleanly override
        // the first set without any residual effect.
        jEdit.setProperty("sumojedit.atp.engine", "eprover");
        jEdit.setProperty("sumojedit.atp.maxAnswers", "10");
        jEdit.setProperty("sumojedit.atp.timeLimitSec", "45");
        jEdit.setProperty("sumojedit.atp.closedWorld", "false");
        jEdit.setProperty("sumojedit.atp.ModusPonens", "false");

        // Verify that the last saved values are the ones returned
        assertEquals("ATP engine should reflect overridden value", "eprover",
                jEdit.getProperty("sumojedit.atp.engine"));
        assertEquals("maxAnswers should reflect overridden value", "10",
                jEdit.getProperty("sumojedit.atp.maxAnswers"));
        assertEquals("timeLimitSec should reflect overridden value", "45",
                jEdit.getProperty("sumojedit.atp.timeLimitSec"));
        assertEquals("closedWorld should reflect overridden value", "false",
                jEdit.getProperty("sumojedit.atp.closedWorld"));
        assertEquals("ModusPonens should reflect overridden value", "false",
                jEdit.getProperty("sumojedit.atp.ModusPonens"));
    }
}