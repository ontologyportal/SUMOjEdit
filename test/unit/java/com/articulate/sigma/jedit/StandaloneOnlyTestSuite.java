package com.articulate.sigma.jedit;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * This is the "standalone" subset of the UnitjEditTestSuite test suite:
 * - includes ONLY non-GUI unit tests
 * - skips AssertJ Swing / GUI harnesses
 * - each standalone test class appears exactly once
 * 
 * This test suite contains 21 standalone unit test classes in total.
 * 
 * ===================================================================================
 * Revision: 1-20-2026
 * ===================================================================================
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
    ConfigureATPPropertyOverrideTest.class,
    ConfigureATPCheckboxDependenciesTest.class,
    QueryResultFormattingTest.class,
    QueryExpLanguageFlagTest.class,
    NullEngineHandlingTest.class,

    /** =============== Miscellaneous SUMOjEdit Helpers (Standalone) ================= */

    SUMOjEditResidualHelpersTest.class,
    TermOccurrenceHighlightingTest.class,
    SafeSnippetFromFileTest.class,
    SUMOjEditThreadConfigTest.class
})
public class StandaloneOnlyTestSuite {
}