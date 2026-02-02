package com.articulate.sigma.jedit;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.edt.GuiQuery;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.junit.testcase.AssertJSwingJUnitTestCase;
import org.assertj.swing.timing.Pause;
import org.gjt.sp.jedit.jEdit;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;

/**
 * GUI-dependent unit test that exercises the high level ask/tell
 * execution flow.  A minimal harness is constructed around a text
 * editor, menu bar and status bar to emulate the key behaviours
 * locked in for the updated ask/tell feature:
 *
 * <p>
 *   <ol>
 *     <li>The user highlights a term (atom) in the editor.</li>
 *     <li>Invoking the ask action disables all menus while
 *         processing.</li>
 *     <li>Upon completion, a new buffer (tab) is opened containing
 *         the query result.</li>
 *     <li>The status bar reflects the running and completed states.</li>
 *   </ol>
 *
 * The harness does not launch an external prover or depend on jEdit
 * internals.  Instead, the ask action simulates an asynchronous
 * computation with a short delay.  Menu items are disabled at the
 * start of the task and re‑enabled when the result is displayed.  A
 * {@link JTabbedPane} represents the buffer list and a {@link JLabel}
 * at the bottom serves as the status bar.  AssertJ Swing drives
 * interactions and verifies component state changes.
 *
 * <p>
 * Behaviour locked in:
 * <ul>
 *   <li>Immediately after clicking the “Ask” menu item, all menu
 *       items must be disabled and the status bar must display a
 *       “Processing” message.</li>
 *   <li>After the simulated query completes, a new tab is added to
 *       the buffer pane containing the selected atom and menus are
 *       re‑enabled.</li>
 *   <li>The status bar must update to a “Finished” message when
 *       processing concludes.</li>
 * </ul>
 *
 * <p>
 * 
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.QueryExecutionUIFlowGUITest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class QueryExecutionUIFlowGUITest extends AssertJSwingJUnitTestCase {

    /**
     * Frame fixture used by AssertJ Swing to drive the UI.
     */
    private FrameFixture window;
    /**
     * Keep a reference to the panel under test so that test code can
     * synchronously set selections on the editor where necessary.
     */
    private QueryPanel panel;

    /**
     * Simple panel that mimics the core elements of SUMOjEdit’s
     * ask/tell UI: a text editor, a menu bar with an Ask action and
     * another placeholder menu, a tabbed pane for buffers, and a
     * status bar.  The ask action simulates asynchronous query
     * evaluation and updates UI components accordingly.
     */
    private static final class QueryPanel extends JPanel {
        // Editor for entering and selecting queries
        private final JTextArea editor = new JTextArea();
        // Menus
        private final JMenuBar menuBar = new JMenuBar();
        private final JMenu fileMenu = new JMenu("File");
        private final JMenu askMenu = new JMenu("Ask");
        // Buffer area
        private final JTabbedPane buffers = new JTabbedPane();
        // Status bar
        private final JLabel statusBar = new JLabel();

        QueryPanel() {
            super(new BorderLayout());

            // Give components names for lookup in tests
            editor.setName("editor");
            fileMenu.setName("fileMenu");
            askMenu.setName("askMenu");
            buffers.setName("bufferTabs");
            statusBar.setName("statusLabel");

            // Populate menus
            fileMenu.add(new JMenuItem("New"));
            menuBar.add(fileMenu);
            JMenuItem askItem = new JMenuItem("Ask");
            askItem.setName("askMenuItem");
            askItem.addActionListener(e -> runAskAction());
            askMenu.add(askItem);
            menuBar.add(askMenu);

            // Layout components
            // Place menu bar at the top
            add(menuBar, BorderLayout.NORTH);
            // Editor occupies the center above the buffer tabs
            JScrollPane editorScroll = new JScrollPane(editor);
            editorScroll.setPreferredSize(new Dimension(400, 100));
            JPanel center = new JPanel(new BorderLayout());
            center.add(editorScroll, BorderLayout.NORTH);
            center.add(buffers, BorderLayout.CENTER);
            add(center, BorderLayout.CENTER);
            // Status bar at the bottom
            statusBar.setText("Ready");
            add(statusBar, BorderLayout.SOUTH);

            // Seed the editor with example content
            editor.setText(";; Example facts and a query\n" +
                           "(instance John Human)\n" +
                           "(instance Socrates Human)\n" +
                           "(forall (?X) (=> (instance ?X Human) (mortal ?X)))\n\n" +
                           "Ask: (mortal John)");
        }

        /**
         * Helper invoked when the Ask menu item is clicked.  It
         * retrieves the currently selected text in the editor (or
         * falls back to the full line under the caret), then disables
         * all menus and updates the status bar to indicate that
         * processing is underway.  A background thread simulates
         * asynchronous prover invocation with a short delay.  When
         * finished, it opens a new tab containing the “result” and
         * re‑enables the menus and updates the status bar.
         */
        private void runAskAction() {
            // Determine the query: use selected text if any; else use the current line
            String query = editor.getSelectedText();
            if (query == null || query.trim().isEmpty()) {
                try {
                    int caret = editor.getCaretPosition();
                    int start = editor.getLineStartOffset(editor.getLineOfOffset(caret));
                    int end = editor.getLineEndOffset(editor.getLineOfOffset(caret));
                    query = editor.getText().substring(start, end).trim();
                } catch (Exception ex) {
                    query = editor.getText().trim();
                }
            }
            final String capturedQuery = query;

            // Disable all menus
            for (int i = 0; i < menuBar.getMenuCount(); i++) {
                menuBar.getMenu(i).setEnabled(false);
            }
            // Update status bar
            statusBar.setText("Processing query...");

            // Simulate asynchronous work: spawn a short-lived thread
            new Thread(() -> {
                try {
                    // Sleep to give the test time to assert disabled state
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {
                }
                // Update UI on EDT when complete
                SwingUtilities.invokeLater(() -> {
                    // Create a new text area with the “result” and add as a new tab
                    JTextArea resultArea = new JTextArea();
                    resultArea.setName("resultArea" + buffers.getTabCount());
                    resultArea.setEditable(false);
                    resultArea.setText("Answer for: " + capturedQuery);
                    buffers.addTab("Result " + (buffers.getTabCount() + 1), new JScrollPane(resultArea));
                    // Re‑enable menus
                    for (int i = 0; i < menuBar.getMenuCount(); i++) {
                        menuBar.getMenu(i).setEnabled(true);
                    }
                    // Update status bar to indicate completion
                    statusBar.setText("Query finished.");
                });
            }).start();
        }
    }

    // ------------------------------------------------------------------
    // AssertJ Swing setup/teardown
    // ------------------------------------------------------------------
    @Override
    protected void onSetUp() {
        // Build the QueryPanel and host it in a JFrame on the EDT
        JFrame frame = GuiActionRunner.execute(new GuiQuery<JFrame>() {
            @Override
            protected JFrame executeInEDT() {
                panel = new QueryPanel();
                JFrame f = new JFrame("Query Execution UI Flow Test");
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

    // ------------------------------------------------------------------
    // Test
    // ------------------------------------------------------------------
    /**
     * End‑to‑end test of the ask/tell execution flow.  It highlights
     * an atom in the editor, triggers the Ask action via the menu,
     * asserts that menus are disabled and the status bar indicates
     * processing, waits for completion, then verifies that menus are
     * re‑enabled, a new buffer has been added with the expected
     * result, and the status bar shows a finished message.
     */
    @Test
    public void testQueryExecutionDisablesMenusAndOpensResultBuffer() {
        // Focus the editor and select the atom "John" in the first line
        // Compute the index of "John" in the seeded editor text.  The
        // TextComponentFixture#selectText method expects start and end
        // indices rather than a string literal.  Use the panel field
        // to compute a stable start position for the selection.
        int start = panel.editor.getText().indexOf("John");
        int end = start + "John".length();
        window.textBox("editor").selectText(start, end);

        // Trigger the ask action by clicking the Ask menu item
        window.menuItem("askMenuItem").click();

        // Verify that the Ask menu itself (not just its item) is disabled
        // while processing.  Disabling the top‑level menu prevents
        // activation of its items, so we assert on the menu's enabled state.
        window.menuItem("askMenu").requireDisabled();
        window.menuItem("fileMenu").requireDisabled();
        window.label("statusLabel").requireText("Processing query...");

        // Wait for the simulated asynchronous work to complete
        Pause.pause(500);

        // The Ask menu should be re‑enabled once processing completes
        window.menuItem("askMenu").requireEnabled();
        window.menuItem("fileMenu").requireEnabled();

        // A new buffer tab should have been added
        // The AssertJ fixture does not provide requireTabCount; instead
        // assert directly on the underlying JTabbedPane's tab count.
        org.junit.Assert.assertEquals("Exactly one result tab expected",
                1, ((javax.swing.JTabbedPane) window.tabbedPane("bufferTabs").target()).getTabCount());

        // The result area text should contain the selected atom
        // Fetch the content of the first tab's component
        JTabbedPane tabs = (JTabbedPane) window.tabbedPane("bufferTabs").target();
        Component comp = tabs.getComponentAt(0);
        JTextArea resultArea = null;
        if (comp instanceof JScrollPane) {
            JViewport vp = ((JScrollPane) comp).getViewport();
            Component view = vp.getView();
            if (view instanceof JTextArea) {
                resultArea = (JTextArea) view;
            }
        }
        assert resultArea != null;
        org.junit.Assert.assertTrue("Result should contain selected atom", resultArea.getText().contains("John"));

        // Status bar should indicate completion
        window.label("statusLabel").requireText("Query finished.");
    }
}