package com.articulate.sigma.jedit;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Standalone-only unit test suite for CI runs that should skip
 * AssertJ Swing / GUI harnesses.
 *
 * This is basically the "top half" of UnitjEditTestSuite without
 * any *GUITest classes.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({

    /** ============================= STANDALONE TESTS ============================= */

    // ===== Error-checking / formatting core logic =====
    SUOKIFErrorCheckHelpersTest.class,
    FormatSUOKIFAxiomsTest.class,
    FormatSUOKIFAxiomsEndToEndTest.class,
    TPTPErrorCheckTest.class,
    SUOKIFToTPTPConversionTest.class,

    // ===== AutoComplete core (non-GUI) =====
    AutoCompleteIndexTest.class,
    KifTermIndexTest.class,
    ACModeAndSignalsTest.class,
    TopCompletionAdapterTest.class,

    // ===== Misc SUMOjEdit helper logic =====
    SUMOjEditResidualHelpersTest.class,
    TermOccurrenceHighlightingTest.class,
    SafeSnippetFromFileTest.class,
    SUMOjEditThreadConfigTest.class,
    SUMOjEditHelperAdditionalANDLanguageConversionTest.class,

    // ===== Non-GUI SUMOjEdit core =====
    SUMOjEditTest.class,
    SUMOjEditThreadConfigTest.class,
    SUOKIFErrorCheckHelpersTest.class,
    SUOKIFToTPTPConversionTest.class,

    // ===== Non-GUI ATP / misc =====
    KifTermIndexTest.class,
    ACModeAndSignalsTest.class
})
public class StandaloneOnlyTestSuite {
}