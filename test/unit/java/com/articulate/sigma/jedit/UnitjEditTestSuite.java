package com.articulate.sigma.jedit;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;


/**
 *
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
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
    TopCompletionAdapterTest.class
})
public class UnitjEditTestSuite {

}
