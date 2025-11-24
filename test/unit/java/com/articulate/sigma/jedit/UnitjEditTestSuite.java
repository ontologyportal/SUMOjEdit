package com.articulate.sigma.jedit;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;


/**
 * NOTE: The unit tests in the following Unit Test Suites are
 * either standalone or GUI-dependent, depending on the specific 
 * methods/functionalities tested by each test.
 * 
 * 
 * Standalone Unit Tests target every testable internal helper, utility,
 * parser-adjacent, method, AC-related structure, index, mode/flag system, 
 * snippet builder, KIF/TPTP message normalizer, line/offset extractor, 
 * formula locator, file-spec composer, temporary file logic, error aggregator, etc.
 * 
 * Standalone Unit Tests (all in the following Test Suites, 10 Suites in total):
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
 * 
 * 
 * GUI-Dependent Unit Tests lock in every testable visual or event-driven piece:
 * view-layer helpers, UI-state managers, widget renderers, layout calculators,
 * input dispatchers, focus/hover handlers, component lifecycles, dialog/alert
 * flows, async UI update logic, cross-thread event bridges, platform quirks,
 * and anything else the frontend can throw at us. Trust but verify.
 * 
 * GUI-Dependent Unit Tests (all in the following Test Suites, 3 Suites in total):
 *  ACModeToggleGUITest.java
 *  ErrorListDisplayGUITest.java
 *  ErrorListUpdateGUITest.java
 * 
 * 
 *  @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
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
    ACModeToggleGUITest.class,
    ErrorListDisplayGUITest.class,
    ErrorListUpdateGUITest.class,
    ErrorListNavigationGUITest.class
})
public class UnitjEditTestSuite {

}
