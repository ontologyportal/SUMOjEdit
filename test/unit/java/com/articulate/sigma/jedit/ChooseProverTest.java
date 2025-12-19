package com.articulate.sigma.jedit;

import com.articulate.sigma.KBmanager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for SUMOjEdit#chooseE() and SUMOjEdit#chooseLeo().
 *
 * These tests verify that the chooseE() and chooseLeo() helpers properly update
 * the KBmanager's global prover enum.  They preserve and restore the original
 * prover to avoid cross-test interference with other test classes.
 *
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class ChooseProverTest {

    private KBmanager.Prover originalProver;
    private SUMOjEdit editor;

    @Before
    public void setUp() {
        // Save the current prover so it can be restored later.  SUMOjEdit
        // static helpers modify KBmanager.getMgr().prover directly.
        originalProver = KBmanager.getMgr().prover;
        editor = new SUMOjEdit();
    }

    @After
    public void tearDown() {
        // Restore the original prover to ensure other tests are unaffected.
        KBmanager.getMgr().prover = originalProver;
    }

    /**
     * Ensure that invoking chooseE() sets KBmanager.prover to EPROVER.
     */
    @Test
    public void testChooseE() {
        // Force a non-EProver value to validate that chooseE() does change it.
        KBmanager.getMgr().prover = KBmanager.Prover.VAMPIRE;
        editor.chooseE();
        assertEquals("chooseE() should set prover to EPROVER",
                KBmanager.Prover.EPROVER, KBmanager.getMgr().prover);
    }

    /**
     * Ensure that invoking chooseLeo() sets KBmanager.prover to LEO.
     */
    @Test
    public void testChooseLeo() {
        // Force a non-LEO value to validate that chooseLeo() does change it.
        KBmanager.getMgr().prover = KBmanager.Prover.VAMPIRE;
        editor.chooseLeo();
        assertEquals("chooseLeo() should set prover to LEO",
                KBmanager.Prover.LEO, KBmanager.getMgr().prover);
    }
}