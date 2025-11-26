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
 * GUI-level tests for a minimal status bar harness that mimics how
 * SUMOjEdit uses jEdit's StatusBar for transient messages and
 * background colour changes.
 *
 * The harness focuses on two main behaviours:
 *
 *  - Initialisation path:
 *      * "loading..." message while SUMOjEdit is starting
 *      * a transient "ready" message which can be cleared back to the
 *        default idle text
 *
 *  - File processing path (e.g. KIF/TPTP checking):
 *      * status bar background switches to a "processing" colour while
 *        work is in progress
 *      * on success the background is restored and a transient
 *        "processing <file> complete" message is shown
 *
 * This test does NOT depend on jEdit itself. Instead it uses a small
 * Swing panel driven by AssertJ Swing to keep the tests deterministic
 * and self-contained, similar to the Error List GUI tests.
 * 
 * 
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class StatusBarMessagesGUITest extends AssertJSwingJUnitTestCase {

    private FrameFixture window;
    private StatusBarPanel panel;

    /**
     * Minimal status bar + control panel that simulates the subset of
     * behaviour we care about for SUMOjEdit:
     *
     *  - A JLabel showing the current status message
     *  - A status bar panel whose background colour can change
     *  - Buttons that trigger "init loading", "init ready",
     *    "start processing", "processing complete" and "clear" paths
     */
    private static final class StatusBarPanel extends JPanel {

        private static final String VERSION =
                "SUMOjEdit vTest (Build 0)";

        private static final String DEFAULT_MESSAGE = "Idle";
        private static final String TEST_FILE = "example.kif";

        private final JLabel statusLabel = new JLabel(DEFAULT_MESSAGE);
        private final JPanel statusBar = new JPanel(new BorderLayout());

        private final JButton initStartButton   = new JButton("Init: loading");
        private final JButton initReadyButton   = new JButton("Init: ready");
        private final JButton processStartButton = new JButton("Process: start");
        private final JButton processDoneButton  = new JButton("Process: done");
        private final JButton clearButton        = new JButton("Clear");

        private final Color defaultBackground;
        private String lastTransientMessage;

        StatusBarPanel() {
            super(new BorderLayout());

            // Name components for AssertJ Swing lookups
            statusLabel.setName("statusLabel");
            statusBar.setName("statusBar");
            initStartButton.setName("initStart");
            initReadyButton.setName("initReady");
            processStartButton.setName("processStart");
            processDoneButton.setName("processDone");
            clearButton.setName("clearStatus");

            statusBar.add(statusLabel, BorderLayout.CENTER);

            JPanel controls = new JPanel(new GridLayout(0, 1));
            controls.add(initStartButton);
            controls.add(initReadyButton);
            controls.add(processStartButton);
            controls.add(processDoneButton);
            controls.add(clearButton);

            add(controls, BorderLayout.CENTER);
            add(statusBar, BorderLayout.SOUTH);

            defaultBackground = statusBar.getBackground();

            wireActions();
        }

        private void wireActions() {
            initStartButton.addActionListener(e -> showInitLoading());
            initReadyButton.addActionListener(e -> showInitReady());
            processStartButton.addActionListener(e -> startProcessing(TEST_FILE));
            processDoneButton.addActionListener(e -> finishProcessing(TEST_FILE));
            clearButton.addActionListener(e -> clearTransient());
        }

        // -----------------------------------------------------------------
        // Behaviours that mirror SUMOjEdit's status bar usage
        // -----------------------------------------------------------------

        private void showInitLoading() {
            statusLabel.setText(VERSION + " loading...");
            // Background remains the default; only the message changes.
        }

        private void showInitReady() {
            showTransient(VERSION + " ready");
        }

        private void startProcessing(String fileName) {
            statusBar.setBackground(Color.GREEN);
            statusLabel.setText("processing " + fileName);
            lastTransientMessage = null;
        }

        private void finishProcessing(String fileName) {
            // Restore background and show a transient completion message.
            statusBar.setBackground(defaultBackground);
            showTransient("processing " + fileName + " complete");
        }

        private void showTransient(String message) {
            statusLabel.setText(message);
            lastTransientMessage = message;
        }

        private void clearTransient() {
            statusLabel.setText(DEFAULT_MESSAGE);
            statusBar.setBackground(defaultBackground);
            lastTransientMessage = null;
        }

        // -----------------------------------------------------------------
        // Accessors used by the tests
        // -----------------------------------------------------------------

        String getStatusText() {
            return statusLabel.getText();
        }

        Color getStatusBackground() {
            return statusBar.getBackground();
        }

        Color getDefaultBackground() {
            return defaultBackground;
        }

        String getLastTransientMessage() {
            return lastTransientMessage;
        }
    }

    // ---------------------------------------------------------------------
    // AssertJ Swing wiring
    // ---------------------------------------------------------------------

    @Override
    protected void onSetUp() {
        JFrame frame = GuiActionRunner.execute(new GuiQuery<JFrame>() {
            @Override
            protected JFrame executeInEDT() {
                panel = new StatusBarPanel();
                JFrame f = new JFrame("Status Bar Messages Test");
                f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                f.getContentPane().add(panel);
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

    @After
    public void afterEach() {
        // Nothing extra to clean, but keep the hook for symmetry with
        // other GUI test files if needed later.
    }

    // ---------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------

    /**
     * Verify the initialisation path:
     *
     *  - default text is "Idle"
     *  - clicking the "Init: loading" button shows a "loading..." message
     *  - clicking the "Init: ready" button shows a transient "ready" message
     *  - clicking "Clear" returns to the default text and clears transient
     *    state
     */
    @Test
    public void testInitStatusMessagesAreTransient() {
        // Default state
        assertEquals("Idle", panel.getStatusText());
        assertNull(panel.getLastTransientMessage());

        // Simulate SUMOjEdit startup: loading...
        window.button("initStart").click();
        String loadingText = panel.getStatusText();
        assertTrue("Expected a loading message", loadingText.contains("loading..."));

        // Simulate SUMOjEdit startup: ready
        window.button("initReady").click();
        String readyText = panel.getStatusText();
        assertTrue("Expected a ready message", readyText.contains("ready"));
        assertEquals(readyText, panel.getLastTransientMessage());

        // Clearing the transient should restore the default message
        window.button("clearStatus").click();
        assertEquals("Idle", panel.getStatusText());
        assertNull(panel.getLastTransientMessage());
    }

    /**
     * Verify the processing path:
     *
     *  - starting processing changes the background colour and shows a
     *    "processing <file>" message
     *  - finishing processing restores the background and shows a transient
     *    "processing <file> complete" message
     *  - clearing the transient returns to the default idle text
     */
    @Test
    public void testProcessingStatusBackgroundAndCompletionMessage() {
        Color defaultBg = panel.getDefaultBackground();

        // Start processing: background changes, status shows "processing <file>"
        window.button("processStart").click();
        assertEquals(Color.GREEN, panel.getStatusBackground());
        String processingText = panel.getStatusText();
        assertTrue("Expected a processing message",
                processingText.startsWith("processing "));

        // Finish processing: background restored, transient completion message
        window.button("processDone").click();
        assertEquals(defaultBg, panel.getStatusBackground());
        String doneText = panel.getStatusText();
        assertTrue("Expected a completion message",
                doneText.contains("complete"));
        assertEquals(doneText, panel.getLastTransientMessage());

        // Clear transient: back to idle
        window.button("clearStatus").click();
        assertEquals("Idle", panel.getStatusText());
        assertNull(panel.getLastTransientMessage());
    }
}