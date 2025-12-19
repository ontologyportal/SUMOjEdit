package com.articulate.sigma.jedit;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Standalone-only unit test suite for CI runs that should skip
 * AssertJ Swing / GUI harnesses.
 *
 * This is the "standalone" subset of UnitjEditTestSuite:
 * - includes ONLY non-GUI tests
 * - each test class appears exactly once
 * 
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

@RunWith(Suite.class)
@Suite.SuiteClasses({

    /** ======================= SUMOjEdit Core (Standalone) ========================== */

    SUMOjEditTest.class,

    /** ================= SUO-KIF Error Checking Helpers (Standalone) ================ */

    SUOKIFErrorCheckHelpersTest.class,

    /** ================== TPTP Error Checking (Standalone) ========================== */

    TPTPErrorCheckTest.class,

    /** ==================== SUO-KIF Formatting (Standalone) ========================= */

    FormatSUOKIFAxiomsTest.class,
    FormatSUOKIFAxiomsEndToEndTest.class,

    /** === SUMOjEdit Helpers + Language Conversion (SUO-KIF ↔ TPTP) (Standalone) ==== */

    SUMOjEditHelperAdditionalANDLanguageConversionTest.class,
    SUOKIFToTPTPConversionTest.class,

    /** ======================= AutoComplete Core (Standalone) ======================= */

    AutoCompleteIndexTest.class,
    KifTermIndexTest.class,
    ACModeAndSignalsTest.class,
    TopCompletionAdapterTest.class,

    /** ================ ask/tell and ATP Configurator (Standalone) ================== */

    ChooseProverTest.class,

    /** =============== Miscellaneous SUMOjEdit Helpers (Standalone) ================= */

    SUMOjEditResidualHelpersTest.class,
    TermOccurrenceHighlightingTest.class,
    SafeSnippetFromFileTest.class,
    SUMOjEditThreadConfigTest.class
})
public class StandaloneOnlyTestSuite {
}