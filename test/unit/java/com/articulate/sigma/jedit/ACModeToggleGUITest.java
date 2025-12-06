package com.articulate.sigma.jedit;

import com.articulate.sigma.jedit.ac.ACMode;
import com.articulate.sigma.jedit.ac.ACSignals;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.edt.GuiQuery;
import org.assertj.swing.edt.GuiTask;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.junit.testcase.AssertJSwingJUnitTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * GUI-level test around a minimal Swing UI that models the AutoComplete
 * mode toggle behaviour (ghost + drop-down) using {@link ACMode} and
 * {@link ACSignals}.
 *
 * It uses a real Swing frame via AssertJ Swing, and real AC logic:
 *  - ACMode for mode semantics and enabled flags
 *  - ACSignals to propagate mode changes with dismiss/apply ordering
 *
 * The state machine for the two toggles mirrors actions.xml:
 *
 * Ghost toggle:
 *  - BOTH           -> DROPDOWN_ONLY
 *  - DROPDOWN_ONLY  -> OFF
 *  - OFF            -> GHOST_ONLY
 *  - GHOST_ONLY     -> BOTH
 *
 * Drop-down toggle:
 *  - BOTH           -> GHOST_ONLY
 *  - GHOST_ONLY     -> OFF
 *  - OFF            -> DROPDOWN_ONLY
 *  - DROPDOWN_ONLY  -> BOTH
 * 
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class ACModeToggleGUITest extends AssertJSwingJUnitTestCase {

    /**
     * Simple panel that implements the same toggle state machine as
     * the sumojedit.ac.mode ghost/drop-down actions, and listens to
     * {@link ACSignals} updates.
     */
    private static final class ModeTogglePanel extends JPanel implements ACSignals.Listener {

        private final JCheckBox ghostBox    = new JCheckBox("Ghost text");
        private final JCheckBox dropdownBox = new JCheckBox("Drop-down");

        private ACMode mode = ACMode.BOTH;

        // For verifying ACSignals dispatch
        final List<String> calls = new ArrayList<>();
        ACMode lastAppliedMode = null;

        ModeTogglePanel(ACMode initial) {
            super(new GridLayout(0, 1));
            this.mode = initial;

            ghostBox.setName("ghostToggle");
            dropdownBox.setName("dropdownToggle");

            add(ghostBox);
            add(dropdownBox);

            // Initialise checkbox states from the starting mode
            applyMode(initial);

            ghostBox.addActionListener(e -> onGhostToggle());
            dropdownBox.addActionListener(e -> onDropdownToggle());
        }

        private void onGhostToggle() {
            // This logic mirrors actions.xml for sumojedit.ac.mode.ghost:
            // boolean ghostOn = mode.ghostEnabled();
            // boolean dropOn  = mode.dropdownEnabled();
            //
            // if (ghostOn) {
            //     if (dropOn) next = DROPDOWN_ONLY;
            //     else        next = OFF;
            // } else {
            //     if (dropOn) next = BOTH;
            //     else        next = GHOST_ONLY;
            // }
            boolean ghostOn = mode.ghostEnabled();
            boolean dropOn  = mode.dropdownEnabled();

            ACMode next;
            if (ghostOn) {
                if (dropOn) {
                    next = ACMode.DROPDOWN_ONLY;
                } else {
                    next = ACMode.OFF;
                }
            } else {
                if (dropOn) {
                    next = ACMode.BOTH;
                } else {
                    next = ACMode.GHOST_ONLY;
                }
            }
            ACSignals.onModeChanged(next);
        }

        private void onDropdownToggle() {
            // This logic mirrors actions.xml for sumojedit.ac.mode.dropdown:
            // boolean dropOn  = mode.dropdownEnabled();
            // boolean ghostOn = mode.ghostEnabled();
            //
            // if (dropOn) {
            //     if (ghostOn) next = GHOST_ONLY;
            //     else         next = OFF;
            // } else {
            //     if (ghostOn) next = BOTH;
            //     else         next = DROPDOWN_ONLY;
            // }
            boolean dropOn  = mode.dropdownEnabled();
            boolean ghostOn = mode.ghostEnabled();

            ACMode next;
            if (dropOn) {
                if (ghostOn) {
                    next = ACMode.GHOST_ONLY;
                } else {
                    next = ACMode.OFF;
                }
            } else {
                if (ghostOn) {
                    next = ACMode.BOTH;
                } else {
                    next = ACMode.DROPDOWN_ONLY;
                }
            }
            ACSignals.onModeChanged(next);
        }

        ACMode currentMode() {
            return mode;
        }

        boolean ghostSelected() {
            return ghostBox.isSelected();
        }

        boolean dropdownSelected() {
            return dropdownBox.isSelected();
        }

        // ----- ACSignals.Listener -----

        @Override
        public void applyMode(ACMode mode) {
            calls.add("apply");
            this.mode = mode;
            ghostBox.setSelected(mode.ghostEnabled());
            dropdownBox.setSelected(mode.dropdownEnabled());
            lastAppliedMode = mode;
        }

        @Override
        public void dismissTransientUI() {
            calls.add("dismiss");
        }
    }

    private FrameFixture window;
    private ModeTogglePanel panel;

    @Before
    public void registerListener() {
        // no-op; listener is registered in onSetUp()
    }

    @After
    public void cleanupListener() {
        // Ensure we do not leak a listener into other tests.
        ACSignals.register(null);
    }

    @Override
    protected void onSetUp() {
        // Build the panel and frame on the EDT via GuiActionRunner
        JFrame frame = GuiActionRunner.execute(new GuiQuery<JFrame>() {
            @Override
            protected JFrame executeInEDT() {
                panel = new ModeTogglePanel(ACMode.BOTH);
                JFrame f = new JFrame("AC Mode Toggle Test");
                f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                f.getContentPane().add(panel);
                f.pack();
                f.setLocationRelativeTo(null);

                // Register the panel as the global ACSignals listener
                ACSignals.register(panel);

                return f;
            }
        });

        window = new FrameFixture(robot(), frame);
        window.show();
    }

    @Override
    protected void onTearDown() {
        if (window != null) {
            window.cleanUp();
            window = null;
        }
    }

    // ---------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------

    @Test
    public void testGhostToggleCyclesAccordingToActionsXml() {
        // Start in BOTH (default)
        assertEquals(ACMode.BOTH, panel.currentMode());
        assertTrue(panel.ghostSelected());
        assertTrue(panel.dropdownSelected());

        // From BOTH, ghost toggle -> DROPDOWN_ONLY
        window.checkBox("ghostToggle").click();
        robot().waitForIdle();
        assertEquals(ACMode.DROPDOWN_ONLY, panel.currentMode());
        assertFalse(panel.ghostSelected());
        assertTrue(panel.dropdownSelected());

        // From DROPDOWN_ONLY, ghost toggle -> BOTH
        window.checkBox("ghostToggle").click();
        robot().waitForIdle();
        assertEquals(ACMode.BOTH, panel.currentMode());
        assertTrue(panel.ghostSelected());
        assertTrue(panel.dropdownSelected());

        // Now explicitly test the OFF <-> GHOST_ONLY branch as well.
        // OFF -> GHOST_ONLY -> OFF

        // Force OFF via signal (must run on EDT)
        GuiActionRunner.execute(new GuiTask() {
            @Override
            protected void executeInEDT() {
                ACSignals.onModeChanged(ACMode.OFF);
            }
        });

        assertEquals(ACMode.OFF, panel.currentMode());
        assertFalse(panel.ghostSelected());
        assertFalse(panel.dropdownSelected());

        // From OFF, ghost toggle -> GHOST_ONLY
        window.checkBox("ghostToggle").click();
        robot().waitForIdle();
        assertEquals(ACMode.GHOST_ONLY, panel.currentMode());
        assertTrue(panel.ghostSelected());
        assertFalse(panel.dropdownSelected());

        // From GHOST_ONLY, ghost toggle -> OFF
        window.checkBox("ghostToggle").click();
        robot().waitForIdle();
        assertEquals(ACMode.OFF, panel.currentMode());
        assertFalse(panel.ghostSelected());
        assertFalse(panel.dropdownSelected());
    }

    @Test
    public void testDropdownToggleCyclesAccordingToActionsXml() {
        // Start from BOTH (run mode change on EDT)
        GuiActionRunner.execute(new GuiTask() {
            @Override
            protected void executeInEDT() {
                ACSignals.onModeChanged(ACMode.BOTH);
            }
        });

        assertEquals(ACMode.BOTH, panel.currentMode());
        assertTrue(panel.ghostSelected());
        assertTrue(panel.dropdownSelected());

        // From BOTH, dropdown toggle -> GHOST_ONLY
        window.checkBox("dropdownToggle").click();
        assertEquals(ACMode.GHOST_ONLY, panel.currentMode());
        assertTrue(panel.ghostSelected());
        assertFalse(panel.dropdownSelected());

        // From GHOST_ONLY, dropdown toggle -> BOTH
        window.checkBox("dropdownToggle").click();
        assertEquals(ACMode.BOTH, panel.currentMode());
        assertTrue(panel.ghostSelected());
        assertTrue(panel.dropdownSelected());

        // Now explicitly test the OFF <-> DROPDOWN_ONLY branch.
        // OFF -> DROPDOWN_ONLY -> OFF

        // Force OFF via signal (run on EDT)
        GuiActionRunner.execute(new GuiTask() {
            @Override
            protected void executeInEDT() {
                ACSignals.onModeChanged(ACMode.OFF);
            }
        });

        assertEquals(ACMode.OFF, panel.currentMode());
        assertFalse(panel.ghostSelected());
        assertFalse(panel.dropdownSelected());

        // From OFF, dropdown toggle -> DROPDOWN_ONLY
        window.checkBox("dropdownToggle").click();
        assertEquals(ACMode.DROPDOWN_ONLY, panel.currentMode());
        assertFalse(panel.ghostSelected());
        assertTrue(panel.dropdownSelected());

        // From DROPDOWN_ONLY, dropdown toggle -> OFF
        window.checkBox("dropdownToggle").click();
        assertEquals(ACMode.OFF, panel.currentMode());
        assertFalse(panel.ghostSelected());
        assertFalse(panel.dropdownSelected());
    }

    @Test
    public void testACSignalsDispatchOrderFromUI() {
        // Clear any previous calls
        panel.calls.clear();

        // Trigger a mode change via ghost toggle
        window.checkBox("ghostToggle").click();

        // We expect dismissTransientUI() then applyMode(...)
        assertEquals(2, panel.calls.size());
        assertEquals("dismiss", panel.calls.get(0));
        assertEquals("apply", panel.calls.get(1));
        assertNotNull(panel.lastAppliedMode);
    }
}