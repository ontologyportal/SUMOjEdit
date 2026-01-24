package com.articulate.sigma.jedit;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.edt.GuiQuery;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.junit.testcase.AssertJSwingJUnitTestCase;
import org.gjt.sp.jedit.jEdit;

import javax.swing.*;
import java.awt.*;

import static org.junit.Assert.*;

/**
 * GUI-dependent unit test that verifies the ask/tell configurator saves
 * user preferences and reloads them on subsequent openings.  The real
 * {@link SUMOjEdit#configureATP()} dialog allows users to set the
 * number of answers, time limit, translation mode (FOF/TFF/THF) and
 * prover engine.  Clicking “Save Preferences” should persist these
 * selections into jEdit properties, and reopening the configurator
 * should honour the saved values as the defaults.
 *
 * <p>
 * This test mirrors only the relevant portion of the configurator UI
 * using a lightweight Swing panel.  It does not launch external
 * provers or depend on jEdit internals beyond the {@code jEdit}
 * property store.  The panel contains two text fields for the
 * numeric settings, two groups of radio buttons for the mode and
 * engine, and a Save button that writes the current selections to
 * properties.  On construction, the panel reads existing property
 * values to initialise the fields, replicating the behaviour of
 * {@code configureATP()}.  AssertJ Swing drives the UI to select
 * custom values, click Save, and then verifies on a fresh panel
 * instance that the saved values are preselected.
 *
 * <p>
 * Behaviour locked in:
 * <ul>
 *   <li>Setting Max Answers to 5, Time Limit to 60 seconds, selecting
 *       TFF mode and the LEO‑III engine, then saving should persist
 *       those values in jEdit properties.</li>
 *   <li>Creating a new configurator instance afterwards should read
 *       the saved properties and preselect Max Answers=5,
 *       Time Limit=60, TFF mode and LEO‑III engine.</li>
 * </ul>
 *
 * <p>
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * {@literal @author} <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.PreferenceSavingAndReloadingGUITest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class PreferenceSavingAndReloadingGUITest extends AssertJSwingJUnitTestCase {

    private FrameFixture window;
    private PreferencePanel panel;

    /**
     * Minimal configurator panel used by this test.  It exposes text
     * fields for the numeric settings, radio buttons for mode and
     * engine selection, and a Save button that persists the
     * selections into the {@code jEdit} property store.  On
     * construction the current property values are read and used to
     * initialise the controls.  Each component is given a name for
     * lookup via AssertJ Swing.
     */
    private static final class PreferencePanel extends JPanel {
        // Numeric settings
        private final JTextField maxAnswersField = new JTextField();
        private final JTextField timeLimitField = new JTextField();

        // Mode selection (FOF/TFF/THF)
        private final JRadioButton fofRadio = new JRadioButton("FOF");
        private final JRadioButton tffRadio = new JRadioButton("TFF");
        private final JRadioButton thfRadio = new JRadioButton("THF");

        // Engine selection (Vampire/EProver/LEO‑III)
        private final JRadioButton engineVampire = new JRadioButton("Vampire");
        private final JRadioButton engineEProver = new JRadioButton("EProver");
        private final JRadioButton engineLeo = new JRadioButton("LEO‑III");

        // Save button
        private final JButton saveButton = new JButton("Save Preferences");

        PreferencePanel() {
            super(new BorderLayout());

            // Name components for AssertJ Swing lookup
            maxAnswersField.setName("maxAnswersField");
            timeLimitField.setName("timeLimitField");
            fofRadio.setName("modeFof");
            tffRadio.setName("modeTff");
            thfRadio.setName("modeThf");
            engineVampire.setName("engineVampire");
            engineEProver.setName("engineEProver");
            engineLeo.setName("engineLeo");
            saveButton.setName("savePreferences");

            // Group radio buttons
            ButtonGroup modeGroup = new ButtonGroup();
            modeGroup.add(fofRadio);
            modeGroup.add(tffRadio);
            modeGroup.add(thfRadio);

            ButtonGroup engineGroup = new ButtonGroup();
            engineGroup.add(engineVampire);
            engineGroup.add(engineEProver);
            engineGroup.add(engineLeo);

            // Load saved preferences into the UI
            loadFromProperties();

            // Layout numeric fields with labels
            JPanel numbers = new JPanel(new GridLayout(0, 2));
            numbers.add(new JLabel("Max Answers"));
            numbers.add(maxAnswersField);
            numbers.add(new JLabel("Time Limit"));
            numbers.add(timeLimitField);

            // Layout mode radio buttons
            JPanel modes = new JPanel(new GridLayout(0, 1));
            modes.add(fofRadio);
            modes.add(tffRadio);
            modes.add(thfRadio);

            // Layout engine radio buttons
            JPanel engines = new JPanel(new GridLayout(0, 1));
            engines.add(engineVampire);
            engines.add(engineEProver);
            engines.add(engineLeo);

            // Compose into a three‑column layout
            JPanel center = new JPanel(new GridLayout(0, 3));
            center.add(numbers);
            center.add(modes);
            center.add(engines);
            add(center, BorderLayout.CENTER);
            add(saveButton, BorderLayout.SOUTH);

            // Wire the Save button to persist preferences
            saveButton.addActionListener(e -> saveToProperties());
        }

        /**
         * Initialise the UI from stored properties.  Defaults mirror
         * those used in SUMOjEdit.configureATP(): Max Answers = 1,
         * Time Limit = 5, mode = fof, engine = eprover.
         */
        private void loadFromProperties() {
            String ans = jEdit.getProperty("sumojedit.atp.maxAnswers", "1");
            maxAnswersField.setText(ans);
            String tl = jEdit.getProperty("sumojedit.atp.timeLimit", "5");
            timeLimitField.setText(tl);

            String mode = jEdit.getProperty("sumojedit.atp.mode", "fof");
            if ("tff".equalsIgnoreCase(mode)) {
                tffRadio.setSelected(true);
            } else if ("thf".equalsIgnoreCase(mode)) {
                thfRadio.setSelected(true);
            } else {
                fofRadio.setSelected(true);
            }

            String engine = jEdit.getProperty("sumojedit.atp.engine", "eprover");
            if ("vampire".equalsIgnoreCase(engine)) {
                engineVampire.setSelected(true);
            } else if (engine != null && engine.toLowerCase().startsWith("leo")) {
                // Accept "leo"/"leo3"/"leo‑iii" interchangeably
                engineLeo.setSelected(true);
            } else {
                engineEProver.setSelected(true);
            }
        }

        /**
         * Persist the current selections into the jEdit property store.
         */
        private void saveToProperties() {
            jEdit.setProperty("sumojedit.atp.maxAnswers", maxAnswersField.getText().trim());
            jEdit.setProperty("sumojedit.atp.timeLimit", timeLimitField.getText().trim());
            String mode;
            if (tffRadio.isSelected()) {
                mode = "tff";
            } else if (thfRadio.isSelected()) {
                mode = "thf";
            } else {
                mode = "fof";
            }
            jEdit.setProperty("sumojedit.atp.mode", mode);
            String eng;
            if (engineVampire.isSelected()) {
                eng = "vampire";
            } else if (engineLeo.isSelected()) {
                eng = "leo3";
            } else {
                eng = "eprover";
            }
            jEdit.setProperty("sumojedit.atp.engine", eng);
        }
    }

    // ------------------------------------------------------------------
    // AssertJ Swing wiring
    // ------------------------------------------------------------------

    @Override
    protected void onSetUp() {
        // Ensure a clean slate for properties at the start of each test
        jEdit.setProperty("sumojedit.atp.maxAnswers", "1");
        jEdit.setProperty("sumojedit.atp.timeLimit", "5");
        jEdit.setProperty("sumojedit.atp.mode", "fof");
        jEdit.setProperty("sumojedit.atp.engine", "eprover");

        JFrame frame = GuiActionRunner.execute(new GuiQuery<JFrame>() {
            @Override
            protected JFrame executeInEDT() {
                panel = new PreferencePanel();
                JFrame f = new JFrame("Preference Saving Test");
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
     * Simulate selecting custom preferences and saving them, then
     * creating a fresh configurator instance to ensure the values are
     * reloaded.  This locks in the behaviour that the preferences are
     * persisted via jEdit properties.
     */
    @org.junit.Test
    public void testPreferencesAreSavedAndReloaded() {
        // Choose non‑default values in the UI
        window.textBox("maxAnswersField").setText("5");
        window.textBox("timeLimitField").setText("60");
        window.radioButton("modeTff").click();
        window.radioButton("engineLeo").click();

        // Click Save Preferences
        window.button("savePreferences").click();

        // Dispose of the current frame to simulate closing the dialog
        window.cleanUp();

        // Create a new panel/frame to simulate reopening the configurator
        JFrame newFrame = GuiActionRunner.execute(new GuiQuery<JFrame>() {
            @Override
            protected JFrame executeInEDT() {
                PreferencePanel newPanel = new PreferencePanel();
                JFrame f = new JFrame("Preference Saving Test 2");
                f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                f.getContentPane().add(newPanel);
                f.pack();
                f.setLocationRelativeTo(null);
                return f;
            }
        });
        window = new FrameFixture(robot(), newFrame);
        window.show();

        // Assert that the saved values are now preselected
        window.textBox("maxAnswersField").requireText("5");
        window.textBox("timeLimitField").requireText("60");
        window.radioButton("modeTff").requireSelected();
        window.radioButton("engineLeo").requireSelected();
    }
}