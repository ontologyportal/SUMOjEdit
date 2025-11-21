package com.articulate.sigma.jedit;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;


/**
 *
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

/**
 * NOTE: The unit tests in the following Unit Test Suites ("Standalone Unit Test Suites") 
 * are standalone, fully deterministic, and non-GUI and otherwise externally dependent. 
 * 
 * They target every testable internal helper, utility, parser-adjacent 
 * method, AC-related structure, index, mode/flag system, snippet builder, 
 * KIF/TPTP message normalizer, line/offset extractor, formula locator, 
 * file-spec composer, temporary file logic, error aggregator, etc.
 * 
 * Standalone Unit Test Suites (10 in total):
 *  SUMOjEditTest.java
 *  SUOKIFErrorCheckTest.java
 *  TPTPErrorCheckTest.java
 *  FormatSuoKifAxiomsTest.java
 *  SUMOjEditHelperAdditionalNLanguageConversionTest.java
 *  AutoCompleteIndexTest.java
 *  KifTermIndexTest.java
 *  ACModeAndSignalsTest.java
 *  TopCompletionAdapterTest.java
 *  SUMOjEditResidualHelpersTest.java
 */

@RunWith(Suite.class)
@Suite.SuiteClasses({
    SUMOjEditTest.class,
    SUOKIFErrorCheckTest.class,
    TPTPErrorCheckTest.class,
    FormatSuoKifAxiomsTest.class,
    SUMOjEditHelperAdditionalNLanguageConversionTest.class,
    AutoCompleteIndexTest.class,
    KifTermIndexTest.class,
    ACModeAndSignalsTest.class,
    TopCompletionAdapterTest.class,
    SUMOjEditResidualHelpersTest.class,
    ACModeToggleGUITest.class
})
public class UnitjEditTestSuite {

}
