package com.articulate.sigma.jedit;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;


/**
 * NOTE: The unit tests in the following unit test suites are
 * either standalone or GUI-dependent, depending on the specific 
 * methods/functionalities tested by each test.
 * 
 * 
 * Standalone unit tests target every testable internal helper, utility,
 * parser-adjacent, method, AC-related structure, index, mode/flag system, 
 * snippet builder, KIF/TPTP message normalizer, line/offset extractor, 
 * formula locator, file-spec composer, temporary file logic, error aggregator, etc.
 * 
 * Standalone Unit Test Suites (12 in total):
 *  SUMOjEditTest.java
 *  SUOKIFErrorCheckTest.java
 *  TPTPErrorCheckTest.java
 *  FormatSuoKifAxiomsTest.java
 *  SUMOjEditHelperAdditionalNLanguageConversionTest.java
 *  SuoKifToTPTPConversionTest.java
 *  FormatSuoKifAxiomsEndToEndTest.java
 *  AutoCompleteIndexTest.java
 *  KifTermIndexTest.java
 *  ACModeAndSignalsTest.java
 *  TopCompletionAdapterTest.java
 *  SUMOjEditResidualHelpersTest.java
 * 
 * 
 * GUI-dependent unit tests lock in every testable visual or event-driven piece:
 * view-layer helpers, UI-state managers, widget renderers, layout calculators,
 * input dispatchers, focus/hover handlers, component lifecycles, dialog/alert
 * flows, async UI update logic, cross-thread event bridges, platform quirks,
 * and anything else the frontend can throw at us. Trust but verify.
 * 
 * GUI-Dependent Unit Test Suites (11 in total):
 *  ACModeToggleGUITest.java
 *  ErrorListDisplayGUITest.java
 *  ErrorListUpdateGUITest.java
 *  ErrorListNavigationGUITest.java
 *  GhostTextRenderingGUITest.java
 *  DropDownPopupGUITest.java
 *  StatusBarMessagesGUITest.java
 *  GhostTextKeyboardNavigationGUITest.java
 *  DropDownKeyboardNavigationGUITest.java
 *  DualModeAutoCompleteGUITest.java
 *  MultiViewErrorSourceGUITest.java
 * 
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

@RunWith(Suite.class)
@Suite.SuiteClasses({
    SUMOjEditTest.class,
    SUOKIFErrorCheckTest.class,
    TPTPErrorCheckTest.class,
    FormatSuoKifAxiomsTest.class,
    SUMOjEditHelperAdditionalNLanguageConversionTest.class,
    SuoKifToTPTPConversionTest.class,
    FormatSuoKifAxiomsEndToEndTest.class,
    AutoCompleteIndexTest.class,
    KifTermIndexTest.class,
    ACModeAndSignalsTest.class,
    TopCompletionAdapterTest.class,
    SUMOjEditResidualHelpersTest.class,
    ACModeToggleGUITest.class,
    ErrorListDisplayGUITest.class,
    ErrorListUpdateGUITest.class,
    ErrorListNavigationGUITest.class,
    GhostTextRenderingGUITest.class,
    DropDownPopupGUITest.class,
    StatusBarMessagesGUITest.class,
    GhostTextKeyboardNavigationGUITest.class,
    DropDownKeyboardNavigationGUITest.class,
    DualModeAutoCompleteGUITest.class,
    MultiViewErrorSourceGUITest.class
})
public class UnitjEditTestSuite {

}
