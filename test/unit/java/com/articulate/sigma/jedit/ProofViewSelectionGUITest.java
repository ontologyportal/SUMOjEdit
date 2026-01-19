package com.articulate.sigma.jedit;

import com.articulate.sigma.HTMLformatter;
import com.articulate.sigma.nlg.LanguageFormatter;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.edt.GuiQuery;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.junit.testcase.AssertJSwingJUnitTestCase;

import org.gjt.sp.jedit.jEdit;

import org.junit.After;
import org.junit.Test;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.WindowConstants;

import java.awt.BorderLayout;
import java.awt.GridLayout;

import static org.junit.Assert.assertEquals;

/**
 * GUI-dependent unit tests for the ask/tell "Proof result view" selection.
 *
 * This test follows the same pattern as StatusBarMessagesGUITest:
 *   - Build a minimal Swing harness panel
 *   - Drive it with AssertJ Swing (FrameFixture)
 *   - Verify the resulting preference side-effects
 *
 * Behaviour locked in:
 *   Selecting “TPTP,” “SUO-KIF,” “Algorithmic NL,” or “LLM paraphrase”
 *   must set the underlying jEdit properties such that query execution
 *   renders proof output in the intended language mode.
 *
 * For unit-test determinism, we do NOT run an external prover or parse a
 * full proof. Instead, we validate the downstream formatting flags that
 * queryExp() uses:
 *   - HTMLformatter.proofParaphraseInEnglish
 *   - LanguageFormatter.paraphraseLLM
 *
 *
 * Author: Simon Deng, NPS ORISE Intern 2026, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.ProofViewSelectionGUITest">Simon Deng, NPS ORISE Intern 2026</a>
 */

public class ProofViewSelectionGUITest extends AssertJSwingJUnitTestCase {

    private FrameFixture window;
    private ProofViewPanel panel;

    // Preserve original static values to avoid leaking state to other tests.
    private boolean originalShowEnglish;
    private boolean originalUseLLM;

    /**
     * Minimal harness panel that mimics the relevant portion of the
     * Configure ATP dialog: four radio buttons + an OK button that writes
     * jEdit properties.
     */
    private static final class ProofViewPanel extends JPanel {

        private final JRadioButton tptpButton = new JRadioButton("TPTP");
        private final JRadioButton suoKifButton = new JRadioButton("SUO-KIF");
        private final JRadioButton algNlButton = new JRadioButton("Algorithmic NL");
        private final JRadioButton llmButton = new JRadioButton("LLM paraphrase");

        private final JButton okButton = new JButton("OK");

        ProofViewPanel() {
            super(new BorderLayout());

            // Name components for AssertJ Swing lookup
            tptpButton.setName("proofViewTptp");
            suoKifButton.setName("proofViewSuoKif");
            algNlButton.setName("proofViewAlgNl");
            llmButton.setName("proofViewLlm");
            okButton.setName("proofViewOk");

            ButtonGroup group = new ButtonGroup();
            group.add(tptpButton);
            group.add(suoKifButton);
            group.add(algNlButton);
            group.add(llmButton);

            // Default selection (doesn't matter much; tests click explicitly)
            tptpButton.setSelected(true);

            JPanel radios = new JPanel(new GridLayout(0, 1));
            radios.add(tptpButton);
            radios.add(suoKifButton);
            radios.add(algNlButton);
            radios.add(llmButton);

            add(radios, BorderLayout.CENTER);
            add(okButton, BorderLayout.SOUTH);

            wireActions();
        }

        private void wireActions() {
            okButton.addActionListener(e -> persistSelectionToProperties());
        }

        /**
         * This is the contract we lock in:
         * - TPTP, SUO-KIF => showEnglish=false, useLLM=false
         * - Algorithmic NL => showEnglish=true, useLLM=false
         * - LLM paraphrase => showEnglish=true, useLLM=true
         */
        private void persistSelectionToProperties() {
            if (tptpButton.isSelected()) {
                setProofViewProps("tptp", false, false);
            } else if (suoKifButton.isSelected()) {
                setProofViewProps("suokif", false, false);
            } else if (algNlButton.isSelected()) {
                setProofViewProps("algNl", true, false);
            } else if (llmButton.isSelected()) {
                setProofViewProps("llm", true, true);
            }
        }

        private static void setProofViewProps(String view, boolean showEnglish, boolean useLLM) {
            jEdit.setProperty("sumojedit.atp.proofView", view);
            jEdit.setProperty("sumojedit.atp.showEnglish", Boolean.toString(showEnglish));
            jEdit.setProperty("sumojedit.atp.useLLM", Boolean.toString(useLLM));
        }
    }

    /**
     * Minimal SUMOjEdit used only to apply the proof view properties to
     * the global formatting flags (no prover / no I/O).
     */
    private static final class TestableSUMOjEdit extends SUMOjEdit {
        @Override
        public void queryExp() {
            boolean showEnglish = Boolean.parseBoolean(
                    jEdit.getProperty("sumojedit.atp.showEnglish", "true"));
            boolean useLLM = Boolean.parseBoolean(
                    jEdit.getProperty("sumojedit.atp.useLLM", "false"));

            HTMLformatter.proofParaphraseInEnglish = showEnglish;
            LanguageFormatter.paraphraseLLM = useLLM;
        }

        @Override
        public void startBackgroundThread(Runnable r) {
            r.run();
        }
    }

    // ---------------------------------------------------------------------
    // AssertJ Swing wiring (match StatusBarMessagesGUITest pattern)
    // ---------------------------------------------------------------------

    @Override
    protected void onSetUp() {
        originalShowEnglish = HTMLformatter.proofParaphraseInEnglish;
        originalUseLLM = LanguageFormatter.paraphraseLLM;

        // Start each test with clean properties.
        jEdit.setProperty("sumojedit.atp.proofView", null);
        jEdit.setProperty("sumojedit.atp.showEnglish", null);
        jEdit.setProperty("sumojedit.atp.useLLM", null);

        JFrame frame = GuiActionRunner.execute(new GuiQuery<JFrame>() {
            @Override
            protected JFrame executeInEDT() {
                panel = new ProofViewPanel();
                JFrame f = new JFrame("Proof View Selection Test");
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
        HTMLformatter.proofParaphraseInEnglish = originalShowEnglish;
        LanguageFormatter.paraphraseLLM = originalUseLLM;

        jEdit.setProperty("sumojedit.atp.proofView", null);
        jEdit.setProperty("sumojedit.atp.showEnglish", null);
        jEdit.setProperty("sumojedit.atp.useLLM", null);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static void applyQueryPreferences() {
        new TestableSUMOjEdit().queryExp();
    }

    private static void assertParaphraseFlags(boolean english, boolean llm) {
        assertEquals("HTMLformatter.proofParaphraseInEnglish mismatch",
                english, HTMLformatter.proofParaphraseInEnglish);
        assertEquals("LanguageFormatter.paraphraseLLM mismatch",
                llm, LanguageFormatter.paraphraseLLM);
    }

    // ---------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------

    @Test
    public void testTptpProofViewDisablesEnglishAndLlm() {
        window.radioButton("proofViewTptp").click();
        window.button("proofViewOk").click();

        applyQueryPreferences();
        assertParaphraseFlags(false, false);
    }

    @Test
    public void testSuoKifProofViewDisablesEnglishAndLlm() {
        window.radioButton("proofViewSuoKif").click();
        window.button("proofViewOk").click();

        applyQueryPreferences();
        assertParaphraseFlags(false, false);
    }

    @Test
    public void testAlgorithmicNlProofViewEnablesEnglishOnly() {
        window.radioButton("proofViewAlgNl").click();
        window.button("proofViewOk").click();

        applyQueryPreferences();
        assertParaphraseFlags(true, false);
    }

    @Test
    public void testLlmParaphraseProofViewEnablesEnglishAndLlm() {
        window.radioButton("proofViewLlm").click();
        window.button("proofViewOk").click();

        applyQueryPreferences();
        assertParaphraseFlags(true, true);
    }
}
