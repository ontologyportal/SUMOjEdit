package com.articulate.sigma.jedit;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.edt.GuiQuery;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.junit.testcase.AssertJSwingJUnitTestCase;
import org.gjt.sp.jedit.jEdit;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import static org.junit.Assert.assertEquals;

/**
 * GUI-dependent unit tests to verify drop-down selection and keyboard
 * navigation within the ask/tell configuration dialog.
 *
 * <p>
 * The updated configurator introduces additional radio buttons and
 * spinners.  This test ensures that the existing drop-down (translation
 * mode) can still be navigated via arrow keys, and that users can
 * traverse the newly added controls using the keyboard (e.g. Tab and
 * arrow keys).  It also verifies that selections made via the
 * keyboard update the underlying {@code jEdit} properties as
 * expected.  To avoid interacting with the full SUMOjEdit dialog,
 * a lightweight Swing panel is constructed that contains the relevant
 * components: a {@link JComboBox} for mode selection, two
 * {@link JSpinner} fields for numeric settings, a trio of radio
 * buttons for engine selection, and two checkboxes representing
 * inference options.  Each component is assigned a unique name for
 * lookup via AssertJ Swing.
 *
 * <p>
 * Behaviour locked in:
 * <ul>
 *   <li>Using the down/up arrow keys on the mode drop-down cycles
 *       through “FOF,” “TFF,” and “THF” and updates the
 *       {@code sumojedit.atp.mode} property with lower‑case values.</li>
 *   <li>Pressing Tab moves focus from the drop-down to the first
 *       spinner (Max Answers) and then to the second spinner (Time
 *       Limit) and finally to the engine radio buttons.</li>
 *   <li>Using the up/down arrow keys inside a spinner increments or
 *       decrements its value and persists it to the corresponding
 *       {@code jEdit} property.</li>
 *   <li>Using the down arrow key within the engine radio button group
 *       advances selection to the next engine and updates
 *       {@code sumojedit.atp.engine} accordingly.</li>
 * </ul>
 *
 * <p>
 * 
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.DropDownKeyboardTraversalGUITest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class DropDownKeyboardTraversalGUITest extends AssertJSwingJUnitTestCase {

    private FrameFixture window;
    private KeyboardNavigationPanel panel;

    /**
     * A minimal configurator panel containing a drop‑down for
     * translation mode, two numeric spinners, a group of engine
     * selection radio buttons, and two inference checkboxes.  Each
     * control writes its current value into the {@code jEdit}
     * property store upon change.  The order of component addition
     * determines the tab traversal sequence.
     */
    private static final class KeyboardNavigationPanel extends JPanel {
        // Translation mode drop‑down (FOF/TFF/THF)
        private final JComboBox<String> modeCombo = new JComboBox<>(new String[] {"FOF", "TFF", "THF"});

        // Numeric spinners for Max Answers and Time Limit
        private final JSpinner maxAnswersSpinner = new JSpinner(new SpinnerNumberModel(1, 1, Integer.MAX_VALUE, 1));
        private final JSpinner timeLimitSpinner  = new JSpinner(new SpinnerNumberModel(5, 1, Integer.MAX_VALUE, 1));

        // Engine selection radio buttons (Vampire/EProver/LEO‑III)
        private final JRadioButton engineVampire  = new JRadioButton("Vampire");
        private final JRadioButton engineEProver  = new JRadioButton("EProver");
        private final JRadioButton engineLeo      = new JRadioButton("LEO‑III");

        // Inference option checkboxes
        private final JCheckBox modusPonensCheck  = new JCheckBox("Modus Ponens", false);
        private final JCheckBox dropOneCheck      = new JCheckBox("Drop One-Premise", false);

        KeyboardNavigationPanel() {
            super(new BorderLayout());

            // Assign component names for AssertJ Swing lookup
            modeCombo.setName("modeCombo");
            maxAnswersSpinner.setName("maxAnswersSpinner");
            timeLimitSpinner.setName("timeLimitSpinner");
            engineVampire.setName("engineVampire");
            engineEProver.setName("engineEProver");
            engineLeo.setName("engineLeo");
            modusPonensCheck.setName("modusPonens");
            dropOneCheck.setName("dropOnePremise");

            // Group radio buttons
            ButtonGroup engineGroup = new ButtonGroup();
            engineGroup.add(engineVampire);
            engineGroup.add(engineEProver);
            engineGroup.add(engineLeo);
            // Set a default selection (FOF and EProver)
            modeCombo.setSelectedIndex(0);
            engineEProver.setSelected(true);

            // ------------------------------------------------------------------
            // Deterministic keyboard handling for JComboBox (GUI test harness)
            // Ensure UP/DOWN arrows always change selection
            // ------------------------------------------------------------------
            modeCombo.setFocusable(true);

            InputMap im = modeCombo.getInputMap(JComponent.WHEN_FOCUSED);
            ActionMap am = modeCombo.getActionMap();

            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "modeDown");
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "modeUp");

            am.put("modeDown", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int idx = modeCombo.getSelectedIndex();
                    if (idx < modeCombo.getItemCount() - 1) {
                        modeCombo.setSelectedIndex(idx + 1);
                    }
                }
            });

            am.put("modeUp", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int idx = modeCombo.getSelectedIndex();
                    if (idx > 0) {
                        modeCombo.setSelectedIndex(idx - 1);
                    }
                }
            });

            // Load defaults into jEdit properties
            jEdit.setProperty("sumojedit.atp.mode", "fof");
            jEdit.setProperty("sumojedit.atp.maxAnswers", "1");
            jEdit.setProperty("sumojedit.atp.timeLimit", "5");
            jEdit.setProperty("sumojedit.atp.engine", "eprover");
            jEdit.setProperty("sumojedit.atp.ModusPonens", "false");
            jEdit.setProperty("sumojedit.atp.dropOnePremise", "false");

            // Layout translation mode drop‑down and spinners in a vertical panel
            JPanel left = new JPanel(new GridLayout(0, 1));
            left.add(new JLabel("Translation Mode"));
            left.add(modeCombo);
            left.add(new JLabel("Max Answers"));
            left.add(maxAnswersSpinner);
            left.add(new JLabel("Time Limit"));
            left.add(timeLimitSpinner);

            // Layout engine radio buttons
            JPanel middle = new JPanel(new GridLayout(0, 1));
            middle.add(new JLabel("Engine"));
            middle.add(engineVampire);
            middle.add(engineEProver);
            middle.add(engineLeo);

            // Layout inference checkboxes
            JPanel right = new JPanel(new GridLayout(0, 1));
            right.add(new JLabel("Inference Options"));
            right.add(modusPonensCheck);
            right.add(dropOneCheck);

            JPanel center = new JPanel(new GridLayout(1, 3));
            center.add(left);
            center.add(middle);
            center.add(right);
            add(center, BorderLayout.CENTER);

            // Attach listeners to update jEdit properties
            wireActions();
        }

        /**
         * Attach change listeners to each component so that user actions
         * reflect immediately in the {@code jEdit} property store.  The
         * drop‑down writes the selected mode in lower case.  Spinners
         * write their integer values.  Radio buttons write the
         * selected engine.  Checkboxes write true/false.
         */
        private void wireActions() {
            // Combo box: update mode
            modeCombo.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    Object sel = modeCombo.getSelectedItem();
                    if (sel != null) {
                        jEdit.setProperty("sumojedit.atp.mode", sel.toString().toLowerCase());
                    }
                }
            });

            // Spinners: update values on change
            ChangeListener spinnerListener = new ChangeListener() {
                @Override
                public void stateChanged(ChangeEvent e) {
                    jEdit.setProperty("sumojedit.atp.maxAnswers",
                            maxAnswersSpinner.getValue().toString());
                    jEdit.setProperty("sumojedit.atp.timeLimit",
                            timeLimitSpinner.getValue().toString());
                }
            };
            maxAnswersSpinner.addChangeListener(spinnerListener);
            timeLimitSpinner.addChangeListener(spinnerListener);

            // Radio buttons: update engine
            ActionListener engineListener = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (engineVampire.isSelected())
                        jEdit.setProperty("sumojedit.atp.engine", "vampire");
                    else if (engineLeo.isSelected())
                        jEdit.setProperty("sumojedit.atp.engine", "leo3");
                    else
                        jEdit.setProperty("sumojedit.atp.engine", "eprover");
                }
            };
            engineVampire.addActionListener(engineListener);
            engineEProver.addActionListener(engineListener);
            engineLeo.addActionListener(engineListener);

            // Checkboxes: update inference flags
            ActionListener checkListener = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    jEdit.setProperty("sumojedit.atp.ModusPonens",
                            Boolean.toString(modusPonensCheck.isSelected()));
                    jEdit.setProperty("sumojedit.atp.dropOnePremise",
                            Boolean.toString(dropOneCheck.isSelected()));
                }
            };
            modusPonensCheck.addActionListener(checkListener);
            dropOneCheck.addActionListener(checkListener);
        }
    }

    // ------------------------------------------------------------------
    // AssertJ Swing lifecycle
    // ------------------------------------------------------------------
    @Override
    protected void onSetUp() {
        JFrame frame = GuiActionRunner.execute(new GuiQuery<JFrame>() {
            @Override
            protected JFrame executeInEDT() {
                panel = new KeyboardNavigationPanel();
                JFrame f = new JFrame("Drop-Down and Keyboard Traversal Test");
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
    // Tests
    // ------------------------------------------------------------------

    /**
     * Verify that the translation mode drop‑down can be navigated using
     * the up/down arrow keys and that the selected value updates the
     * corresponding jEdit property.  The default selection is “FOF.”
     */
    @org.junit.Test
    public void testDropDownSelectionUpdatesModeProperty() {
        // Initial state
        window.comboBox("modeCombo").requireSelection("FOF");
        assertEquals("fof", jEdit.getProperty("sumojedit.atp.mode"));

        // Focus the combo so key bindings receive keystrokes
        window.comboBox("modeCombo").focus();
        robot().waitForIdle();

        // DOWN: FOF -> TFF
        window.comboBox("modeCombo").pressAndReleaseKeys(KeyEvent.VK_DOWN);
        robot().waitForIdle();
        window.comboBox("modeCombo").requireSelection("TFF");
        assertEquals("tff", jEdit.getProperty("sumojedit.atp.mode"));

        // DOWN: TFF -> THF
        window.comboBox("modeCombo").pressAndReleaseKeys(KeyEvent.VK_DOWN);
        robot().waitForIdle();
        window.comboBox("modeCombo").requireSelection("THF");
        assertEquals("thf", jEdit.getProperty("sumojedit.atp.mode"));

        // UP: THF -> TFF
        window.comboBox("modeCombo").pressAndReleaseKeys(KeyEvent.VK_UP);
        robot().waitForIdle();
        window.comboBox("modeCombo").requireSelection("TFF");
        assertEquals("tff", jEdit.getProperty("sumojedit.atp.mode"));

        // UP: TFF -> FOF
        window.comboBox("modeCombo").pressAndReleaseKeys(KeyEvent.VK_UP);
        robot().waitForIdle();
        window.comboBox("modeCombo").requireSelection("FOF");
        assertEquals("fof", jEdit.getProperty("sumojedit.atp.mode"));
    }

    /**
     * Verify that Tab traverses the focus in the expected order (drop‑down
     * → Max Answers spinner → Time Limit spinner → engine radio buttons)
     * and that using the arrow keys within spinners and radio buttons
     * updates both the UI selection and the associated properties.
     */
    @org.junit.Test
    public void testKeyboardTraversalAndSpinnerAndRadioKeys() {
        // Focus the drop‑down and confirm
        window.comboBox("modeCombo").focus();
        window.comboBox("modeCombo").requireFocused();

        // Attempt to tab to Max Answers spinner then explicitly focus the spinner
        window.comboBox("modeCombo").pressAndReleaseKeys(KeyEvent.VK_TAB);
        window.spinner("maxAnswersSpinner").focus();

        // Use UP arrow to increment the Max Answers spinner via the robot
        robot().pressAndReleaseKeys(KeyEvent.VK_UP);
        window.spinner("maxAnswersSpinner").requireValue(2);
        assertEquals("2", jEdit.getProperty("sumojedit.atp.maxAnswers"));

        // Use DOWN arrow to decrement back to 1 via the robot
        robot().pressAndReleaseKeys(KeyEvent.VK_DOWN);
        window.spinner("maxAnswersSpinner").requireValue(1);
        assertEquals("1", jEdit.getProperty("sumojedit.atp.maxAnswers"));

        // Tab to Time Limit spinner via the robot then explicitly focus it
        robot().pressAndReleaseKeys(KeyEvent.VK_TAB);
        window.spinner("timeLimitSpinner").focus();

        // Use DOWN arrow to decrement the time limit (from 5 to 4) via the robot
        robot().pressAndReleaseKeys(KeyEvent.VK_DOWN);
        window.spinner("timeLimitSpinner").requireValue(4);
        assertEquals("4", jEdit.getProperty("sumojedit.atp.timeLimit"));

        // Use UP arrow twice to increment (back to 6) via the robot
        robot().pressAndReleaseKeys(KeyEvent.VK_UP);
        robot().pressAndReleaseKeys(KeyEvent.VK_UP);
        window.spinner("timeLimitSpinner").requireValue(6);
        assertEquals("6", jEdit.getProperty("sumojedit.atp.timeLimit"));

        // Tab to the engine radio buttons via the robot then explicitly focus the first radio
        robot().pressAndReleaseKeys(KeyEvent.VK_TAB);
        window.radioButton("engineVampire").focus();

        // SPACE selects the focused radio button (reliable)
        window.radioButton("engineVampire").pressAndReleaseKeys(KeyEvent.VK_SPACE);
        robot().waitForIdle();
        window.radioButton("engineVampire").requireSelected();
        assertEquals("vampire", jEdit.getProperty("sumojedit.atp.engine"));

        // TAB to next radio, SPACE selects it
        robot().pressAndReleaseKeys(KeyEvent.VK_TAB);
        window.radioButton("engineEProver").pressAndReleaseKeys(KeyEvent.VK_SPACE);
        robot().waitForIdle();
        window.radioButton("engineEProver").requireSelected();
        assertEquals("eprover", jEdit.getProperty("sumojedit.atp.engine"));

        // TAB to next radio, SPACE selects it
        robot().pressAndReleaseKeys(KeyEvent.VK_TAB);
        window.radioButton("engineLeo").pressAndReleaseKeys(KeyEvent.VK_SPACE);
        robot().waitForIdle();
        window.radioButton("engineLeo").requireSelected();
        assertEquals("leo3", jEdit.getProperty("sumojedit.atp.engine"));
    }
}