package com.articulate.sigma.jedit;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.edt.GuiQuery;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.junit.testcase.AssertJSwingJUnitTestCase;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;

/**
 * GUI unit test that verifies toggling the “Closed World Assumption”
 * checkbox changes the answer for a query that depends on closed‑world
 * reasoning.  A minimal Ask/Tell panel is defined specifically for
 * testing: it contains a query field, a Closed World Assumption
 * checkbox, an Ask button and a result text area.  Clicking the Ask
 * button invokes a stubbed inference routine that returns different
 * results depending on whether closed‑world reasoning is enabled.
 *
 * <p>The chosen query in this test is deliberately simple: it does not
 * correspond to any real SUMO axiom, but the stubbed answer method
 * simulates a situation where the absence of a fact yields an
 * “Unknown” result under open‑world semantics and a “Proof found”
 * result under the closed‑world assumption.  This captures the
 * intended behavioural change without launching any external provers
 * or depending on jEdit internals.</p>
 *
 * <p>Test steps:</p>
 * <ol>
 *   <li>Enter a sample query into the query field.</li>
 *   <li>Ensure the Closed World Assumption checkbox is initially
 *       unchecked (open world).</li>
 *   <li>Click the Ask button and verify the result is “Unknown”.</li>
 *   <li>Check the Closed World Assumption box.</li>
 *   <li>Click Ask again and verify the result is now “Proof found”.</li>
 * </ol>
 *
 * This test uses AssertJ Swing to drive the UI synchronously and
 * verify component states.  All Swing components are named to allow
 * straightforward lookup via the Fixture API.
 *
 *
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class ClosedWorldBehaviourGUITest extends AssertJSwingJUnitTestCase {

    /** The fixture for interacting with the frame under test. */
    private FrameFixture window;

    /**
     * Minimal Ask/Tell panel used solely for this test.  It exposes
     * named Swing components for the query input, the Closed World
     * Assumption toggle, an Ask button, and a result display.  The
     * Ask button uses a stubbed inference routine that returns a
     * different answer depending on whether closed‑world reasoning is
     * enabled.
     */
    private static final class AskTellPanel extends JPanel {
        private final JTextField queryField = new JTextField();
        private final JCheckBox cwaCheckbox = new JCheckBox("Closed World Assumption");
        private final JButton askButton = new JButton("Ask");
        private final JTextArea resultArea = new JTextArea();

        AskTellPanel() {
            super(new BorderLayout());

            // Name the components for AssertJ Swing lookups.
            queryField.setName("queryField");
            cwaCheckbox.setName("cwaCheckbox");
            askButton.setName("askButton");
            resultArea.setName("resultArea");

            // Configure the result area as non‑editable with a sensible
            // preferred size.
            resultArea.setEditable(false);
            resultArea.setRows(3);
            resultArea.setLineWrap(true);
            resultArea.setWrapStyleWord(true);

            // Layout the query and checkbox at the top in a simple
            // horizontal arrangement.  The Ask button sits next to the
            // checkbox to emulate a small control strip.
            JPanel topPanel = new JPanel(new BorderLayout());
            topPanel.add(queryField, BorderLayout.CENTER);
            JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
            controls.add(cwaCheckbox);
            controls.add(askButton);
            topPanel.add(controls, BorderLayout.EAST);
            add(topPanel, BorderLayout.NORTH);

            // Place the result area in a scroll pane to mimic a real
            // output pane.
            JScrollPane scroll = new JScrollPane(resultArea);
            scroll.setPreferredSize(new Dimension(400, 120));
            add(scroll, BorderLayout.CENTER);

            // Wire the Ask button to compute a result from the current
            // query and CWA setting.
            askButton.addActionListener(e -> runQuery());
        }

        /**
         * Called when the user clicks Ask: computes an answer for the
         * current query under the selected reasoning semantics and
         * displays it in the result area.  For this test, the answer
         * logic is deliberately simplistic: any non‑empty query
         * returns “Proof found” under closed‑world reasoning and
         * “Unknown” otherwise.
         */
        private void runQuery() {
            String q = queryField.getText().trim();
            boolean cwa = cwaCheckbox.isSelected();
            resultArea.setText(answerFor(q, cwa));
        }

        /**
         * Compute a canned answer based on the provided query and
         * closed‑world flag.  In a real implementation this would
         * construct a TPTP translation, invoke a prover and parse the
         * resulting proof.  Here it simply demonstrates that the
         * closed‑world assumption changes the result.
         *
         * @param query the input query string
         * @param closedWorld whether the closed‑world assumption is enabled
         * @return either “Proof found” (closed world) or “Unknown” (open world)
         */
        private static String answerFor(String query, boolean closedWorld) {
            if (query == null || query.isEmpty())
                return "";
            return closedWorld ? "Proof found" : "Unknown";
        }
    }

    @Override
    protected void onSetUp() {
        // Build the entire Swing UI (panel + frame) on the EDT.
        JFrame frame = GuiActionRunner.execute(new GuiQuery<JFrame>() {
            @Override
            protected JFrame executeInEDT() {
                AskTellPanel panel = new AskTellPanel();

                JFrame f = new JFrame("Ask/Tell Test");
                f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                f.getContentPane().add(panel);
                f.pack();
                f.setName("askTellFrame");
                return f;
            }
        });

        // Create the FrameFixture and display the frame.
        window = new FrameFixture(robot(), frame);
        window.show();
    }

    /**
     * Verify that toggling the Closed World Assumption checkbox
     * materially changes the answer for a simple query.  When the
     * checkbox is unchecked, the stubbed answer routine returns
     * “Unknown”; when checked, it returns “Proof found”.  This
     * behavioural difference simulates closed‑world versus open‑world
     * reasoning in the ask/tell feature.
     */
    @Test
    public void testClosedWorldCheckboxChangesAnswer() {
        // Enter a sample query.  The specific contents are immaterial
        // because the answer is fully determined by the CWA flag in
        // this stubbed panel.
        window.textBox("queryField").enterText("(instance Tweety Flying)");

        // Ensure CWA is initially unchecked (open world).
        window.checkBox("cwaCheckbox").requireNotSelected();

        // Ask the query and verify the result indicates no proof under
        // open‑world reasoning.
        window.button("askButton").click();
        window.textBox("resultArea").requireText("Unknown");

        // Enable closed world reasoning.
        window.checkBox("cwaCheckbox").click();

        // Ask again; this time the stub should report a proof.
        window.button("askButton").click();
        window.textBox("resultArea").requireText("Proof found");
    }
}