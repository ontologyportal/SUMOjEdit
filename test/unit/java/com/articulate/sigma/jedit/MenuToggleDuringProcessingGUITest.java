package com.articulate.sigma.jedit;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.edt.GuiQuery;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.junit.testcase.AssertJSwingJUnitTestCase;
import org.junit.After;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;

import static org.junit.Assert.*;

/**
 * GUI-dependent test for menu enable/disable behaviour during a
 * simulated “long‑running” KIF/TPTP processing operation.
 *
 * SUMOjEdit temporarily disables its plugin menu entries and the
 * editor’s context menu while background processing is in flight
 * (e.g. checkErrors(), processLoadedKifOrTptp()), then re-enables
 * them on completion – both for success and error paths. The real
 * implementation drives this through togglePluginMenus(boolean)
 * on the EDT.
 *
 * This test uses a small Swing harness that mirrors that contract:
 *
 *  • A JMenuBar with a “Plugins → SUMOjEdit” menu
 *  • A synthetic “context popup enabled” flag exposed via a label
 *  • Buttons that drive the same state machine as the real plugin:
 *      startProcessing()  -> disable menus / context
 *      finishOk()         -> re-enable on success
 *      finishError()      -> re-enable on failure
 *
 * Assertions cover three phases:
 *
 *  1. Menus are enabled initially.
 *  2. Menus become disabled while the fake processing is “in progress”.
 *  3. Menus are re-enabled after completion, for both success and
 *     error completions.
 *
 *
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.MenuToggleDuringProcessingGUITest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class MenuToggleDuringProcessingGUITest extends AssertJSwingJUnitTestCase {

    /**
     * Minimal harness that mimics SUMOjEdit's menu toggle logic
     * without requiring a real jEdit View. It exposes:
     *
     *  • A “Plugins → SUMOjEdit” JMenu that is enabled/disabled
     *  • A synthetic context-popup flag rendered as a JLabel
     *  • Explicit start/finish controls that simulate a long-running
     *    operation whose lifecycle mirrors SUMOjEdit's background
     *    KIF/TPTP checks.
     */
    private static final class MenuTogglePanel extends JPanel {

        private final JMenuBar menuBar;
        private final JMenu pluginsMenu;
        private final JMenu sumoMenu;

        private final JLabel contextStatusLabel;

        private final JButton startOkButton;
        private final JButton finishOkButton;
        private final JButton startErrorButton;
        private final JButton finishErrorButton;

        private boolean contextPopupEnabled = true;
        private boolean processing = false;

        MenuTogglePanel() {
            super(new BorderLayout());

            // ----- Menu bar with a Plugins → SUMOjEdit hierarchy -----
            menuBar = new JMenuBar();

            // Add some filler menus to reflect the idea of multiple top-level menus.
            for (int i = 0; i < 8; i++) {
                menuBar.add(new JMenu("M" + i));
            }

            pluginsMenu = new JMenu("Plugins");
            pluginsMenu.setName("pluginsMenu");
            menuBar.add(pluginsMenu);

            sumoMenu = new JMenu("SUMOjEdit");
            sumoMenu.setName("sumojEditMenu");
            sumoMenu.add(new JMenuItem("Check Errors…"));
            sumoMenu.add(new JMenuItem("Format SUO‑KIF Axioms"));
            pluginsMenu.add(sumoMenu);

            // ----- Context menu status label (synthetic stand-in) -----
            contextStatusLabel = new JLabel();
            contextStatusLabel.setName("contextStatusLabel");

            // ----- Control buttons that drive the state machine -----
            startOkButton = new JButton("Start (OK)");
            startOkButton.setName("startSuccess");
            startOkButton.addActionListener(e -> startProcessing());

            finishOkButton = new JButton("Finish (OK)");
            finishOkButton.setName("finishSuccess");
            finishOkButton.addActionListener(e -> finishProcessingSuccess());

            startErrorButton = new JButton("Start (Error)");
            startErrorButton.setName("startError");
            startErrorButton.addActionListener(e -> startProcessing());

            finishErrorButton = new JButton("Finish (Error)");
            finishErrorButton.setName("finishError");
            finishErrorButton.addActionListener(e -> finishProcessingError());

            JPanel controls = new JPanel(new GridLayout(2, 2, 4, 4));
            controls.add(startOkButton);
            controls.add(finishOkButton);
            controls.add(startErrorButton);
            controls.add(finishErrorButton);

            add(controls, BorderLayout.CENTER);
            add(contextStatusLabel, BorderLayout.SOUTH);

            // Initial state mirrors SUMOjEdit at idle: menus enabled,
            // context popup enabled, no processing in flight.
            setMenusAndContextEnabled(true);
            processing = false;
        }

        JMenuBar getMenuBar() {
            return menuBar;
        }

        boolean isProcessing() {
            return processing;
        }

        boolean isPluginMenuEnabled() {
            return sumoMenu.isEnabled();
        }

        boolean isContextPopupEnabled() {
            return contextPopupEnabled;
        }

        String getContextStatusText() {
            return contextStatusLabel.getText();
        }

        // -----------------------------------------------------------------
        // Harness behaviours mirroring SUMOjEdit.togglePluginMenus(...)
        // -----------------------------------------------------------------

        private void setMenusAndContextEnabled(boolean enabled) {
            sumoMenu.setEnabled(enabled);
            contextPopupEnabled = enabled;
            contextStatusLabel.setText("Context popup: " + (enabled ? "enabled" : "disabled"));
        }

        void startProcessing() {
            processing = true;
            // In real SUMOjEdit this happens inside background KIF/TPTP work
            // before heavy processing begins.
            setMenusAndContextEnabled(false);
        }

        void finishProcessingSuccess() {
            // Successful completion path; menus must be re-enabled.
            processing = false;
            setMenusAndContextEnabled(true);
        }

        void finishProcessingError() {
            // Error completion path; menus must still be re-enabled so the
            // user can retry or perform other actions.
            processing = false;
            setMenusAndContextEnabled(true);
        }
    }

    private FrameFixture window;
    private MenuTogglePanel panel;

    @Override
    protected void onSetUp() {
        JFrame frame = GuiActionRunner.execute(new GuiQuery<JFrame>() {
            @Override
            protected JFrame executeInEDT() {
                panel = new MenuTogglePanel();
                JFrame f = new JFrame("Menu Toggle Harness");
                f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                f.setJMenuBar(panel.getMenuBar());
                f.getContentPane().add(panel, BorderLayout.CENTER);
                f.pack();
                f.setLocationRelativeTo(null);
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

    /**
     * Menus and context popup should be enabled by default, become
     * disabled while the fake processing is “in progress”, and be
     * re-enabled after a successful completion.
     */
    @Test
    public void testMenusToggleAroundSuccessfulProcessing() {
        // Initial state: enabled, not processing.
        assertFalse("Processing flag should be false initially", panel.isProcessing());
        assertTrue("Plugin menu should start enabled", panel.isPluginMenuEnabled());
        assertTrue("Context popup should start enabled", panel.isContextPopupEnabled());
        window.menuItem("sumojEditMenu").requireEnabled();
        window.label("contextStatusLabel").requireText("Context popup: enabled");

        // Start processing (success path).
        window.button("startSuccess").click();
        assertTrue("Processing flag should be true after start", panel.isProcessing());
        assertFalse("Plugin menu must be disabled while processing", panel.isPluginMenuEnabled());
        assertFalse("Context popup must be disabled while processing", panel.isContextPopupEnabled());
        window.menuItem("sumojEditMenu").requireDisabled();
        window.label("contextStatusLabel").requireText("Context popup: disabled");

        // Finish successfully: everything should be re-enabled.
        window.button("finishSuccess").click();
        assertFalse("Processing flag should be false after successful completion", panel.isProcessing());
        assertTrue("Plugin menu must be re-enabled after success", panel.isPluginMenuEnabled());
        assertTrue("Context popup must be re-enabled after success", panel.isContextPopupEnabled());
        window.menuItem("sumojEditMenu").requireEnabled();
        window.label("contextStatusLabel").requireText("Context popup: enabled");
    }

    /**
     * The error completion path must also re-enable menus and context
     * popup so that the user can continue interacting with the plugin.
     */
    @Test
    public void testMenusReenabledAfterErrorCompletion() {
        // Start processing via the “error” button.
        window.button("startError").click();
        assertTrue("Processing flag should be true after error-start", panel.isProcessing());
        assertFalse("Plugin menu must be disabled while processing", panel.isPluginMenuEnabled());
        assertFalse("Context popup must be disabled while processing", panel.isContextPopupEnabled());
        window.menuItem("sumojEditMenu").requireDisabled();
        window.label("contextStatusLabel").requireText("Context popup: disabled");

        // Finish with an error: menus must still be re-enabled.
        window.button("finishError").click();
        assertFalse("Processing flag should be false after error completion", panel.isProcessing());
        assertTrue("Plugin menu must be re-enabled after error completion", panel.isPluginMenuEnabled());
        assertTrue("Context popup must be re-enabled after error completion", panel.isContextPopupEnabled());
        window.menuItem("sumojEditMenu").requireEnabled();
        window.label("contextStatusLabel").requireText("Context popup: enabled");
    }
}