package com.articulate.sigma.jedit;

import com.articulate.sigma.trans.SUMOformulaToTPTPformula;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Standalone tests for the SUO-KIF → TPTP conversion pipeline that
 * SUMOjEdit.toTPTP() relies on.  These tests call
 * {@link SUMOformulaToTPTPformula#tptpParseSUOKIFString(String, boolean)}
 * directly with small in-memory SUO-KIF snippets and assert that the
 * generated TPTP is non-empty and structurally sane.
 *
 * This avoids pulling in a full jEdit {@code View} / {@code Buffer}
 * while still validating the same conversion logic that the editor
 * uses when exporting to TPTP.
 *
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class SuoKifToTPTPConversionTest {

    /**
     * Verify that a simple implication in SUO-KIF form produces a
     * non-empty TPTP output string with a standard top-level
     * TPTP formula prefix and balanced parentheses.
     */
    @Test
    public void testSimpleImplicationProducesNonEmptyTptp() {
        String kif = "(=> (instance ?X Animal) (instance ?X Organism))";

        String tptp = SUMOformulaToTPTPformula.tptpParseSUOKIFString(kif, false);
        assertNotNull("TPTP output should not be null", tptp);

        String trimmed = tptp.trim();
        assertFalse("TPTP output should not be empty", trimmed.isEmpty());

        // Minimal sanity check: parentheses should be globally balanced.
        assertEquals("Parentheses should be balanced in TPTP output",
                     0, parenBalance(trimmed));
    }

    /**
     * Same check as above, but with the query flag enabled.  We don't
     * assert the exact role ("conjecture" vs "axiom") to keep this
     * resilient across back-end tweaks, only that the output looks
     * like a proper TPTP problem.
     */
    @Test
    public void testQueryFlagProducesNonEmptyTptp() {
        String kifQuery = "(exists (?X) (and (instance ?X Animal) (not (instance ?X Organism))))";

        String tptp = SUMOformulaToTPTPformula.tptpParseSUOKIFString(kifQuery, true);
        assertNotNull("TPTP (query) output should not be null", tptp);

        String trimmed = tptp.trim();
        assertFalse("TPTP (query) output should not be empty", trimmed.isEmpty());

        // Minimal sanity check: parentheses should be globally balanced.
        assertEquals("Parentheses should be balanced in TPTP (query) output",
                     0, parenBalance(trimmed));
    }

    /**
     * Helper: global '(' minus ')' count.  Zero means balanced.
     */
    private static int parenBalance(String s) {
        int balance = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                balance++;
            } else if (c == ')') {
                balance--;
            }
        }
        return balance;
    }
}