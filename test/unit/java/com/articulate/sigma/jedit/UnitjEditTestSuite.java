package com.articulate.sigma.jedit;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import com.articulate.sigma.SuggestionComponentsIntegrationTest;


/**
 * NOTE: The below unit test classes in this unit test suite are
 * either standalone or GUI-dependent, depending on the specific 
 * methods/functionalities tested by each test class.
 * 
 * ============================== Standalone Unit Tests ==============================
 * Standalone unit tests target every testable internal helper, utility,
 * parser-adjacent, method, AC-related structure, index, mode/flag system, 
 * snippet builder, KIF/TPTP message normalizer, line/offset extractor, 
 * formula locator, file-spec composer, temporary file logic, error aggregator, etc.
 * 
 * Standalone Unit Test Classes (21 in total):
 *  SUMOjEditTest
 * 
 *  SUOKIFErrorCheckHelpersTest
 * 
 *  TPTPErrorCheckTest
 * 
 *  FormatSUOKIFAxiomsTest
 *  FormatSUOKIFAxiomsEndToEndTest
 * 
 *  SUMOjEditHelperAdditionalANDLanguageConversionTest
 *  SUOKIFToTPTPConversionTest
 * 
 *  AutoCompleteIndexTest
 *  KifTermIndexTest
 *  ACModeAndSignalsTest
 *  TopCompletionAdapterTest
 * 
 *  ChooseProverTest
 *  ConfigureATPPropertyOverrideTest
 *  ConfigureATPCheckboxDependenciesTest
 *  QueryResultFormattingTest
 *  QueryExpLanguageFlagTest
 *  NullEngineHandlingTest
 * 
 *  SUMOjEditResidualHelpersTest
 *  TermOccurrenceHighlightingTest
 *  SafeSnippetFromFileTest
 *  SUMOjEditThreadConfigTest
 * 
 * ===================================================================================
 * =================== Unit Tests with External Dependencies (GUI) ===================
 * ===================================================================================
 * GUI-dependent unit tests lock in every testable visual or event-driven piece:
 * view-layer helpers, UI-state managers, widget renderers, layout calculators,
 * input dispatchers, focus/hover handlers, component lifecycles, dialog/alert
 * flows, async UI update logic, cross-thread event bridges, platform quirks,
 * and anything else the frontend can throw at us. Trust but verify.
 * 
 * GUI-Dependent Unit Test Classes (19 in total):
 *  ACModeToggleGUITest
 *  GhostTextRenderingGUITest
 *  GhostTextKeyboardNavigationGUITest
 *  DropDownPopupGUITest
 *  DropDownKeyboardNavigationGUITest
 *  DualModeAutoCompleteGUITest
 * 
 *  ErrorListDisplayGUITest
 *  ErrorListUpdateGUITest
 *  ErrorListNavigationGUITest
 *  MultiViewErrorSourceGUITest
 *  MultiViewErrorSourceSwitchingGUITest
 * 
 *  SUOKIFErrorCheckGUITest
 *  FormatSUOKIFAxiomsGUITest
 *  TPTPFormatBufferGUITest
 * 
 *  StatusBarMessagesGUITest
 *  MenuToggleDuringProcessingGUITest
 * 
 *  ProofViewSelectionGUITest
 *  EngineRadioButtonsGUITest
 *  PreferenceSavingAndReloadingGUITest
 * 
 * ===================================================================================
 * Total Unit Test Classes Included: 40
 * ===================================================================================
 * Revision: 1-24-2026
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

    /** =============== ask/tell and ATP Configurator (Standalone) =================== */
    
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
    /** =========================== GUI TESTS: ERROR LIST ============================ */
    /** ============================================================================== */

    ErrorListDisplayGUITest.class,
    ErrorListUpdateGUITest.class,
    ErrorListNavigationGUITest.class,
    MultiViewErrorSourceGUITest.class,
    MultiViewErrorSourceSwitchingGUITest.class,

    /** ============================================================================== */
    /** ============ GUI TESTS: SUO-KIF & TPTP Error/Formatting Pipelines ============ */
    /** ============================================================================== */

    SUOKIFErrorCheckGUITest.class,
    FormatSUOKIFAxiomsGUITest.class,
    TPTPFormatBufferGUITest.class,

    /** ============================================================================== */
    /** ======================== GUI TESTS: STATUS BAR & MENU ======================== */
    /** ============================================================================== */

    StatusBarMessagesGUITest.class,
    MenuToggleDuringProcessingGUITest.class,

    /** ============================================================================== */
    /** ================== GUI TESTS: ask/tell and ATP Configurator ================== */
    /** ============================================================================== */

    ProofViewSelectionGUITest.class,
    EngineRadioButtonsGUITest.class,
    PreferenceSavingAndReloadingGUITest.class
})
public class UnitjEditTestSuite {

}