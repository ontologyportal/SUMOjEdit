package com.articulate.sigma.jedit;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.edt.GuiQuery;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.junit.testcase.AssertJSwingJUnitTestCase;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionListener;

/**
 * GUI-dependent unit tests for the Configure ATP "Engine" radio buttons.
 *
 * <p>This test mirrors the minimal portion of {@link SUMOjEdit#configureATP()} that
 * handles engine selection.  In the real dialog, selecting the Vampire
 * engine enables the Vampire-specific sub-mode radio buttons (CASC, Avatar,
 * Custom) and the "Modus Ponens" checkbox.  Switching to either EProver
 * or LEO‑III disables those components and clears any selections.  The
 * dependent "Drop One-Premise Formulas" checkbox is governed by a
 * separate dependency and is tested elsewhere; here we simply verify
 * that it becomes disabled whenever the engine is not Vampire.
 *
 * <p>To avoid interacting with jEdit or a full SUMOjEdit instance,
 * this test constructs a minimal Swing panel containing only the
 * relevant radio buttons and checkboxes.  The same enable/disable
 * logic from {@code configureATP()} is reproduced via anonymous
 * {@link ActionListener}s so that AssertJ Swing can drive the UI and
 * verify component state.
 *
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.ProofViewSelectionGUITest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class EngineRadioButtonsGUITest extends AssertJSwingJUnitTestCase {

    private FrameFixture window;
    private EnginePanel panel;

    /**
     * Minimal harness panel that replicates the engine selection UI
     * from SUMOjEdit.configureATP().
     */
    private static final class EnginePanel extends JPanel {
        // Engine selection radio buttons
        private final JRadioButton vamButton = new JRadioButton("Vampire");
        private final JRadioButton eButton   = new JRadioButton("EProver");
        private final JRadioButton leoButton = new JRadioButton("LEO‑III");

        // Vampire sub-mode radio buttons
        private final JRadioButton cascButton   = new JRadioButton("CASC mode");
        private final JRadioButton avatarButton = new JRadioButton("Avatar mode");
        private final JRadioButton customButton = new JRadioButton("Custom mode");

        // Inference rule checkboxes
        private final JCheckBox mpCheckBox   = new JCheckBox("Modus Ponens", false);
        private final JCheckBox dropCheckBox = new JCheckBox("Drop One-Premise Formulas", false);

        EnginePanel() {
            super(new BorderLayout());

            // Name components for AssertJ Swing lookup
            vamButton.setName("engineVampire");
            eButton.setName("engineEProver");
            leoButton.setName("engineLeo");
            cascButton.setName("subModeCasc");
            avatarButton.setName("subModeAvatar");
            customButton.setName("subModeCustom");
            mpCheckBox.setName("modusPonens");
            dropCheckBox.setName("dropOnePremise");

            // Group engine buttons mutually exclusively
            ButtonGroup engineGroup = new ButtonGroup();
            engineGroup.add(vamButton);
            engineGroup.add(eButton);
            engineGroup.add(leoButton);
            // Set a default selection (doesn't matter; tests click explicitly)
            eButton.setSelected(true);

            // Group sub-mode buttons mutually exclusively
            ButtonGroup subModeGroup = new ButtonGroup();
            subModeGroup.add(cascButton);
            subModeGroup.add(avatarButton);
            subModeGroup.add(customButton);
            cascButton.setSelected(true);

            // Layout radio buttons and checkboxes
            JPanel enginesPanel = new JPanel(new GridLayout(0, 1));
            enginesPanel.add(vamButton);
            enginesPanel.add(eButton);
            enginesPanel.add(leoButton);

            JPanel subModesPanel = new JPanel(new GridLayout(0, 1));
            subModesPanel.add(cascButton);
            subModesPanel.add(avatarButton);
            subModesPanel.add(customButton);

            JPanel checkBoxesPanel = new JPanel(new GridLayout(0, 1));
            checkBoxesPanel.add(mpCheckBox);
            checkBoxesPanel.add(dropCheckBox);

            // Compose the UI
            JPanel center = new JPanel(new GridLayout(0, 3));
            center.add(enginesPanel);
            center.add(subModesPanel);
            center.add(checkBoxesPanel);
            add(center, BorderLayout.CENTER);

            wireActions();
        }

        /**
         * Attach listeners to mirror the enable/disable logic from
         * SUMOjEdit.configureATP(): selecting Vampire enables sub‑modes
         * and Modus Ponens; selecting another engine disables them and
         * clears any selections.  In addition, selecting/deselecting
         * Modus Ponens controls the Drop One-Premise checkbox.
         */
        private void wireActions() {
            // When MP toggles, enable/disable Drop accordingly
            ActionListener mpToggle = e -> {
                boolean ena = mpCheckBox.isSelected();
                dropCheckBox.setEnabled(ena);
                if (!ena) {
                    dropCheckBox.setSelected(false);
                }
            };
            mpCheckBox.addActionListener(mpToggle);

            // When engine changes, toggle Vampire-specific options
            ActionListener engToggle = e -> {
                boolean ena = vamButton.isSelected();
                cascButton.setEnabled(ena);
                avatarButton.setEnabled(ena);
                customButton.setEnabled(ena);
                mpCheckBox.setEnabled(ena);
                if (!ena) {
                    // Clear selections when disabling
                    mpCheckBox.setSelected(false);
                    dropCheckBox.setSelected(false);
                    dropCheckBox.setEnabled(false);
                }
                // Update Drop enablement based on MP after engine change
                mpToggle.actionPerformed(null);
            };
            vamButton.addActionListener(engToggle);
            eButton.addActionListener(engToggle);
            leoButton.addActionListener(engToggle);

            // Apply initial state (simulate configureATP()'s first call)
            engToggle.actionPerformed(null);
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
                panel = new EnginePanel();
                JFrame f = new JFrame("Engine Selection Test");
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

    // ---------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------

    /**
     * Selecting Vampire should enable the sub-mode radio buttons and
     * Modus Ponens checkbox, and leave Drop One-Premise disabled
     * (because MP is unchecked by default).
     */
    @org.junit.Test
    public void testVampireEnablesSubModesAndModusPonens() {
        // Click the Vampire engine
        window.radioButton("engineVampire").click();

        // Sub-modes should be enabled
        window.radioButton("subModeCasc").requireEnabled();
        window.radioButton("subModeAvatar").requireEnabled();
        window.radioButton("subModeCustom").requireEnabled();

        // Modus Ponens should be enabled but not selected
        window.checkBox("modusPonens").requireEnabled().requireNotSelected();

        // Drop One-Premise should be disabled and not selected
        window.checkBox("dropOnePremise").requireDisabled().requireNotSelected();
    }

    /**
     * Selecting EProver should disable all Vampire-specific options and
     * clear any selections.  This test first enables some options by
     * selecting Vampire and checking MP/Drop, then switches to EProver
     * and asserts that everything is cleared and disabled.
     */
    @org.junit.Test
    public void testEProverDisablesAndClearsVampireOptions() {
        // Enable Vampire and set MP and Drop to true
        window.radioButton("engineVampire").click();
        window.checkBox("modusPonens").click();
        window.checkBox("dropOnePremise").click();

        // Sanity: ensure preconditions
        window.checkBox("modusPonens").requireSelected();
        window.checkBox("dropOnePremise").requireSelected();

        // Switch to EProver
        window.radioButton("engineEProver").click();

        // Sub-modes should now be disabled
        window.radioButton("subModeCasc").requireDisabled();
        window.radioButton("subModeAvatar").requireDisabled();
        window.radioButton("subModeCustom").requireDisabled();

        // Modus Ponens and Drop should be disabled and unchecked
        window.checkBox("modusPonens").requireDisabled().requireNotSelected();
        window.checkBox("dropOnePremise").requireDisabled().requireNotSelected();
    }

    /**
     * Selecting LEO‑III should mirror EProver: all Vampire-specific options
     * are disabled and cleared.  This test follows the same pattern as
     * testEProverDisablesAndClearsVampireOptions() but switches to LEO.
     */
    @org.junit.Test
    public void testLeoDisablesAndClearsVampireOptions() {
        // Enable Vampire and set MP and Drop to true
        window.radioButton("engineVampire").click();
        window.checkBox("modusPonens").click();
        window.checkBox("dropOnePremise").click();

        // Switch to LEO
        window.radioButton("engineLeo").click();

        // Sub-modes should now be disabled
        window.radioButton("subModeCasc").requireDisabled();
        window.radioButton("subModeAvatar").requireDisabled();
        window.radioButton("subModeCustom").requireDisabled();

        // Modus Ponens and Drop should be disabled and unchecked
        window.checkBox("modusPonens").requireDisabled().requireNotSelected();
        window.checkBox("dropOnePremise").requireDisabled().requireNotSelected();
    }

    /**
     * Switching back to Vampire after selecting a different engine should
     * re-enable the Vampire-specific options.  This ensures that toggling
     * engine selection is reversible.
     */
    @org.junit.Test
    public void testToggleBackToVampireRestoresOptions() {
        // Start on EProver (default) and then switch to Vampire
        window.radioButton("engineEProver").click();
        window.radioButton("engineVampire").click();

        // Sub-modes should be enabled
        window.radioButton("subModeCasc").requireEnabled();
        window.radioButton("subModeAvatar").requireEnabled();
        window.radioButton("subModeCustom").requireEnabled();

        // Modus Ponens should be enabled (unchecked)
        window.checkBox("modusPonens").requireEnabled().requireNotSelected();
        // Drop remains disabled because MP is not selected
        window.checkBox("dropOnePremise").requireDisabled().requireNotSelected();
    }
}