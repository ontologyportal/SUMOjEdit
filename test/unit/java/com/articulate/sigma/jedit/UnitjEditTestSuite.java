package com.articulate.sigma.jedit;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import com.articulate.sigma.SuggestionComponentsIntegrationTest;


/**
 * NOTE: The unit tests in the following unit test suites are
 * either standalone or GUI-dependent, depending on the specific 
 * methods/functionalities tested by each test.
 * 
 * ===================================================================================
 * 
 * Standalone unit tests target every testable internal helper, utility,
 * parser-adjacent, method, AC-related structure, index, mode/flag system, 
 * snippet builder, KIF/TPTP message normalizer, line/offset extractor, 
 * formula locator, file-spec composer, temporary file logic, error aggregator, etc.
 * 
 * Standalone Unit Test Suites (17 in total):
 *  SUMOjEditTest.java
 * 
 *  SUOKIFErrorCheckHelpersTest.java
 * 
 *  TPTPErrorCheckTest.java
 * 
 *  FormatSUOKIFAxiomsTest.java
 *  FormatSUOKIFAxiomsEndToEndTest.java
 * 
 *  SUMOjEditHelperAdditionalANDLanguageConversionTest.java
 *  SUOKIFToTPTPConversionTest.java
 * 
 *  AutoCompleteIndexTest.java
 *  KifTermIndexTest.java
 *  ACModeAndSignalsTest.java
 *  TopCompletionAdapterTest.java
 * 
 *  ChooseProverTest.java
 *  ConfigureATPPropertyOverrideTest.java
 * 
 *  SUMOjEditResidualHelpersTest.java
 *  TermOccurrenceHighlightingTest.java
 *  SafeSnippetFromFileTest.java
 *  SUMOjEditThreadConfigTest.java
 * 
 * ===================================================================================
 * 
 * GUI-dependent unit tests lock in every testable visual or event-driven piece:
 * view-layer helpers, UI-state managers, widget renderers, layout calculators,
 * input dispatchers, focus/hover handlers, component lifecycles, dialog/alert
 * flows, async UI update logic, cross-thread event bridges, platform quirks,
 * and anything else the frontend can throw at us. Trust but verify.
 * 
 * GUI-Dependent Unit Test Suites (16 in total):
 *  ACModeToggleGUITest.java
 *  GhostTextRenderingGUITest.java
 *  GhostTextKeyboardNavigationGUITest.java
 *  DropDownPopupGUITest.java
 *  DropDownKeyboardNavigationGUITest.java
 *  DualModeAutoCompleteGUITest.java
 * 
 *  ErrorListDisplayGUITest.java
 *  ErrorListUpdateGUITest.java
 *  ErrorListNavigationGUITest.java
 *  MultiViewErrorSourceGUITest.java
 *  MultiViewErrorSourceSwitchingGUITest.java
 * 
 *  SUOKIFErrorCheckGUITest.java
 *  FormatSUOKIFAxiomsGUITest.java
 *  TPTPFormatBufferGUITest.java
 * 
 *  StatusBarMessagesGUITest.java
 *  MenuToggleDuringProcessingGUITest.java
 * 
 * ===================================================================================
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

    /** =============== ask/tell and ATP Configurator (Standalone) =================== */
    
    ChooseProverTest.class,
    ConfigureATPPropertyOverrideTest.class,

    /** =============== Miscellaneous SUMOjEdit Helpers (Standalone) ================= */

    SUMOjEditResidualHelpersTest.class,
    TermOccurrenceHighlightingTest.class,
    SafeSnippetFromFileTest.class,
    SUMOjEditThreadConfigTest.class,

    /** ============================================================================== */
    /** ========================== GUI TESTS: AUTOCOMPLETE =========================== */
    /** ============================================================================== */

    ACModeToggleGUITest.class,
    GhostTextRenderingGUITest.class,
    GhostTextKeyboardNavigationGUITest.class,
    DropDownPopupGUITest.class,
    DropDownKeyboardNavigationGUITest.class,
    DualModeAutoCompleteGUITest.class,

    /** ============================================================================== */
    /** ========================== GUI TESTS: ERROR LIST ============================= */
    /** ============================================================================== */

    ErrorListDisplayGUITest.class,
    ErrorListUpdateGUITest.class,
    ErrorListNavigationGUITest.class,
    MultiViewErrorSourceGUITest.class,
    MultiViewErrorSourceSwitchingGUITest.class,

    /** ============================================================================== */
    /** ========== GUI TESTS: SUO-KIF & TPTP Error/Formatting Pipelines ============== */
    /** ============================================================================== */

    SUOKIFErrorCheckGUITest.class,
    FormatSUOKIFAxiomsGUITest.class,
    TPTPFormatBufferGUITest.class,

    /** ============================================================================== */
    /** ======================= GUI TESTS: STATUS BAR & MENU ========================= */
    /** ============================================================================== */

    StatusBarMessagesGUITest.class,
    MenuToggleDuringProcessingGUITest.class
})
public class UnitjEditTestSuite {

}