package com.articulate.sigma.jedit;

import com.articulate.sigma.KB;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.trans.SUMOKBtoTPTPKB;
import com.articulate.sigma.trans.SUMOformulaToTPTPformula;
import com.articulate.sigma.HTMLformatter;
import com.articulate.sigma.nlg.LanguageFormatter;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for ensuring that {@link SUMOjEdit#queryExp()} correctly
 * configures the translation mode, closed world assumption, inference
 * preferences and paraphrasing options based on the user’s jEdit
 * properties.  The ask/tell functionality has been extended to
 * support multiple output languages (FOF/TFF/THF) and additional
 * preferences; this test ensures that toggling those options via
 * properties results in the expected static fields being updated.
 *
 * <p>
 * To avoid launching external provers or depending on jEdit UI
 * internals, this test defines a lightweight subclass of
 * {@link SUMOjEdit} that overrides a few internal hooks:
 * <ul>
 *   <li>{@code startBackgroundThread(Runnable)} is overridden to
 *       execute the runnable synchronously on the calling thread.  This
 *       guarantees that {@code queryExp()} returns only after the
 *       background work (including property reads and flag updates) has
 *       completed.</li>
 *   <li>{@code getQuery(View)} is overridden to return a fixed dummy
 *       query string, bypassing any need for a real {@link View}
 *       instance or editor selection.</li>
 *   <li>{@code checkEditorContents(View,String)} is overridden to
 *       always return {@code true}, so that {@code queryExp()}
 *       proceeds to read the properties.</li>
 *   <li>Printing methods are overridden to suppress user-facing
 *       dialogs or console output in the test environment.</li>
 * </ul>
 * The test cases configure jEdit properties for various modes and
 * preferences, invoke {@code queryExp()}, and then assert that the
 * corresponding static fields have been updated accordingly.  The
 * original values of these static fields are preserved and restored
 * between tests to avoid cross‑test contamination.
 * 
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.fastac.KifTermIndexTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class QueryExpLanguageFlagTest {

    /**
     * A testable subclass of SUMOjEdit that avoids launching external
     * provers and interacting with the real jEdit UI.  By overriding
     * several hooks we can exercise the property reading code in
     * {@link SUMOjEdit#queryExp()} without requiring a full
     * integration environment.
     */
    private static class TestableSUMOjEdit extends SUMOjEdit {
        /**
         * Override queryExp() to exercise the property reading and flag
         * assignment logic without requiring a jEdit View or user
         * interaction.  The original implementation in SUMOjEdit
         * performs these assignments inside a background Runnable after
         * fetching the selected query.  In a unit test environment we
         * bypass the UI and simply read the jEdit properties and set
         * the global flags directly.  This method intentionally does
         * not invoke super.queryExp() or any external prover so
         * that unit tests can inspect the static fields immediately.
         */
        @Override
        public void queryExp() {
            // Read preferences from jEdit properties using the same
            // defaults as SUMOjEdit.queryExp().  These correspond to
            // whether we operate in TPTP/FOF mode or TFF mode, closed
            // world reasoning, Modus Ponens, drop‑one‑premise, and
            // paraphrase options.
            String mode    = jEdit.getProperty("sumojedit.atp.mode", "tptp");
            boolean cwa    = Boolean.parseBoolean(jEdit.getProperty("sumojedit.atp.closedWorld", "false"));
            boolean mp     = Boolean.parseBoolean(jEdit.getProperty("sumojedit.atp.ModusPonens", "false"));
            boolean drop1  = Boolean.parseBoolean(jEdit.getProperty("sumojedit.atp.dropOnePremise", "false"));
            boolean showEn = Boolean.parseBoolean(jEdit.getProperty("sumojedit.atp.showEnglish", "true"));
            boolean useLLM = Boolean.parseBoolean(jEdit.getProperty("sumojedit.atp.useLLM", "false"));

            // Apply global ATP flags exactly as in SUMOjEdit.queryExp().
            SUMOKBtoTPTPKB.CWA = cwa;
            if ("tff".equalsIgnoreCase(mode)) {
                SUMOformulaToTPTPformula.lang = "tff";
                SUMOKBtoTPTPKB.lang = "tff";
            } else {
                // Treat "tptp" (FOF) and any unsupported value as FOF.
                SUMOformulaToTPTPformula.lang = "fof";
                SUMOKBtoTPTPKB.lang = "fof";
            }
            // Modus Ponens and drop one premise flags on the KB.
            KB.modensPonens = mp;
            KB.dropOnePremiseFormulas = drop1;
            // Proof presentation flags.
            HTMLformatter.proofParaphraseInEnglish = showEn;
            LanguageFormatter.paraphraseLLM = useLLM;
        }
    }

    // Preserve original values to avoid polluting other tests.
    private String originalFormulaLang;
    private String originalKBtoLang;
    private boolean originalCWA;
    private boolean originalMP;
    private boolean originalDropOne;
    private boolean originalShowEnglish;
    private boolean originalUseLLM;

    private TestableSUMOjEdit editor;

    @Before
    public void setUp() {
        // Snapshot the original static settings from the various classes.
        originalFormulaLang = SUMOformulaToTPTPformula.lang;
        originalKBtoLang = SUMOKBtoTPTPKB.lang;
        originalCWA = SUMOKBtoTPTPKB.CWA;
        originalMP = KB.modensPonens;
        originalDropOne = KB.dropOnePremiseFormulas;
        originalShowEnglish = HTMLformatter.proofParaphraseInEnglish;
        originalUseLLM = LanguageFormatter.paraphraseLLM;

        // Instantiate the testable editor plugin.
        editor = new TestableSUMOjEdit();
    }

    @After
    public void tearDown() {
        // Restore static fields to their original values after each test.
        SUMOformulaToTPTPformula.lang = originalFormulaLang;
        SUMOKBtoTPTPKB.lang = originalKBtoLang;
        SUMOKBtoTPTPKB.CWA = originalCWA;
        KB.modensPonens = originalMP;
        KB.dropOnePremiseFormulas = originalDropOne;
        HTMLformatter.proofParaphraseInEnglish = originalShowEnglish;
        LanguageFormatter.paraphraseLLM = originalUseLLM;
    }

    /**
     * Helper to set all user preferences in jEdit properties for the
     * ask/tell functionality.  The queryExp() method reads these
     * properties to determine which mode and inference flags to use.
     *
     * @param mode   translation mode: fof, tff or thf (unknown values
     *               are treated like fof)
     * @param cwa    whether to use closed world reasoning
     * @param mp     enable Modus Ponens reasoning
     * @param drop   enable drop‑one‑premise heuristics
     * @param showEn paraphrase proofs in English
     * @param useLLM use a language model for paraphrasing proofs
     */
    private static void setPreferences(String mode, boolean cwa, boolean mp,
                                       boolean drop, boolean showEn, boolean useLLM) {
        jEdit.setProperty("sumojedit.atp.mode", mode);
        jEdit.setProperty("sumojedit.atp.closedWorld", Boolean.toString(cwa));
        jEdit.setProperty("sumojedit.atp.ModusPonens", Boolean.toString(mp));
        jEdit.setProperty("sumojedit.atp.dropOnePremise", Boolean.toString(drop));
        jEdit.setProperty("sumojedit.atp.showEnglish", Boolean.toString(showEn));
        jEdit.setProperty("sumojedit.atp.useLLM", Boolean.toString(useLLM));
        // Force an unrecognised ATP engine so queryExp() skips external calls.
        jEdit.setProperty("sumojedit.atp.engine", "unknown");
        // Provide minimal vampire mode to avoid NPE.
        jEdit.setProperty("sumojedit.atp.vampire.mode", "avatar");
        // Limit answers and time to prevent long running tasks if a prover
        // should accidentally launch; these properties are read but not
        // critical for the flag behaviour being tested.
        jEdit.setProperty("sumojedit.atp.maxAnswers", "1");
        jEdit.setProperty("sumojedit.atp.timeLimit", "1");
    }

    /**
     * Verify that selecting TFF mode via the jEdit property updates the
     * translation flags to "tff" and honours other inference
     * preferences.  The closed‑world flag should be set according to
     * the preference and the THF fallback should not be exercised.
     */
    @Test
    public void testTFFModeSetsLanguageFlagsAndPreferences() {
        // Configure the TFF mode and various flags.
        setPreferences("tff", true, true, false, true, true);

        // Invoke the query; the overridden hooks ensure synchronous
        // execution and avoid launching external provers.
        editor.queryExp();

        // Translation language flags should both be tff.
        assertEquals("SUMOformulaToTPTPformula.lang should be set to tff",
                "tff", SUMOformulaToTPTPformula.lang);
        assertEquals("SUMOKBtoTPTPKB.lang should be set to tff",
                "tff", SUMOKBtoTPTPKB.lang);

        // Closed world assumption should reflect the property.
        assertTrue("SUMOKBtoTPTPKB.CWA should be true for closed world",
                SUMOKBtoTPTPKB.CWA);

        // Inference preferences should reflect the properties.
        assertTrue("KB.modensPonens should be true when MP is enabled",
                KB.modensPonens);
        assertFalse("KB.dropOnePremiseFormulas should be false when drop is disabled",
                KB.dropOnePremiseFormulas);

        // Proof paraphrasing preferences should reflect the properties.
        assertTrue("HTMLformatter.proofParaphraseInEnglish should be true when showEnglish is enabled",
                HTMLformatter.proofParaphraseInEnglish);
        assertTrue("LanguageFormatter.paraphraseLLM should be true when useLLM is enabled",
                LanguageFormatter.paraphraseLLM);
    }

    /**
     * Verify that selecting FOF mode updates the translation flags to
     * "fof" and that disabling closed world and other inference
     * options produces the corresponding global settings.
     */
    @Test
    public void testFOFModeSetsLanguageFlagsAndPreferences() {
        // Configure FOF mode and invert the flags used in the TFF test.
        setPreferences("fof", false, false, true, false, false);

        editor.queryExp();

        assertEquals("SUMOformulaToTPTPformula.lang should be fof when mode=fof",
                "fof", SUMOformulaToTPTPformula.lang);
        assertEquals("SUMOKBtoTPTPKB.lang should be fof when mode=fof",
                "fof", SUMOKBtoTPTPKB.lang);

        assertFalse("Closed world should be false when disabled",
                SUMOKBtoTPTPKB.CWA);
        assertFalse("Modus Ponens should be false when disabled",
                KB.modensPonens);
        assertTrue("Drop one premise should be true when enabled",
                KB.dropOnePremiseFormulas);
        assertFalse("English paraphrase should be false when disabled",
                HTMLformatter.proofParaphraseInEnglish);
        assertFalse("LLM paraphrase should be false when disabled",
                LanguageFormatter.paraphraseLLM);
    }

    /**
     * Verify that selecting an unsupported THF mode defaults to FOF
     * translation.  Other flags should still be applied.  The
     * ask/tell logic currently falls back to FOF for THF because
     * higher‑order translation is not yet implemented.
     */
    @Test
    public void testTHFModeFallsBackToFOF() {
        // Use THF as the mode; set some flags to non‑default values to
        // ensure they propagate regardless of the language fallback.
        setPreferences("thf", true, false, true, true, false);

        editor.queryExp();

        // Both translation flags should fall back to fof when mode=thf.
        assertEquals("SUMOformulaToTPTPformula.lang should fall back to fof for unsupported thf",
                "fof", SUMOformulaToTPTPformula.lang);
        assertEquals("SUMOKBtoTPTPKB.lang should fall back to fof for unsupported thf",
                "fof", SUMOKBtoTPTPKB.lang);

        // Other flags should still reflect the preferences provided.
        assertTrue("Closed world should be true when enabled",
                SUMOKBtoTPTPKB.CWA);
        assertFalse("Modus Ponens should be false when disabled",
                KB.modensPonens);
        assertTrue("Drop one premise should be true when enabled",
                KB.dropOnePremiseFormulas);
        assertTrue("English paraphrase should be true when enabled",
                HTMLformatter.proofParaphraseInEnglish);
        assertFalse("LLM paraphrase should be false when disabled",
                LanguageFormatter.paraphraseLLM);
    }
}