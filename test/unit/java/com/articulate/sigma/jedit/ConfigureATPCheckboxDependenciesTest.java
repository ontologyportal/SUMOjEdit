package com.articulate.sigma.jedit;

import org.junit.Test;

import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;
import java.awt.event.ActionListener;

import static org.junit.Assert.*;

/**
 * Standalone unit test for Configure ATP checkbox dependency:
 *
 * - Enabling "Modus Ponens" enables "Drop One-Premise Formulas"
 * - Disabling "Modus Ponens" clears and disables "Drop One-Premise Formulas"
 *
 * This test does NOT open a UI and does NOT rely on AssertJ Swing.
 * It simply recreates the same checkbox wiring used inside
 * SUMOjEdit.configureATP().
 *
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.ConfigureATPCheckboxDependenciesTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class ConfigureATPCheckboxDependenciesTest {

    @Test
    public void testModusPonensEnablesAndDisablesDropOnePremise() throws Exception {

        SwingUtilities.invokeAndWait(() -> {

            // Mirror the two checkboxes created in SUMOjEdit.configureATP():
            final JCheckBox cbMP   = new JCheckBox("Modus Ponens", false);
            final JCheckBox cbDrop = new JCheckBox("Drop One-Premise Formulas", false);

            // Mirror the exact dependency logic in SUMOjEdit.configureATP():
            // java.awt.event.ActionListener mpToggle = e -> {
            //     boolean ena = cbMP.isSelected();
            //     cbDrop.setEnabled(ena);
            //     if (!ena) cbDrop.setSelected(false);
            // };
            ActionListener mpToggle = e -> {
                boolean ena = cbMP.isSelected();
                cbDrop.setEnabled(ena);
                if (!ena) cbDrop.setSelected(false);
            };
            cbMP.addActionListener(mpToggle);

            // Initial apply (configureATP() calls mpToggle.actionPerformed(null))
            mpToggle.actionPerformed(null);

            // Initial state: MP off -> Drop disabled and unchecked
            assertFalse(cbMP.isSelected());
            assertFalse(cbDrop.isEnabled());
            assertFalse(cbDrop.isSelected());

            // Turn MP on -> Drop becomes enabled (selection stays false until user sets it)
            cbMP.setSelected(true);
            mpToggle.actionPerformed(null);

            assertTrue(cbMP.isSelected());
            assertTrue(cbDrop.isEnabled());
            assertFalse(cbDrop.isSelected());

            // User checks Drop
            cbDrop.setSelected(true);
            assertTrue(cbDrop.isSelected());

            // Turn MP off -> Drop becomes disabled AND is cleared
            cbMP.setSelected(false);
            mpToggle.actionPerformed(null);

            assertFalse(cbMP.isSelected());
            assertFalse(cbDrop.isEnabled());
            assertFalse(cbDrop.isSelected());
        });
    }
}