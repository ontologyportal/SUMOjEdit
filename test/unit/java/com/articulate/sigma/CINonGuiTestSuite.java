package com.articulate.sigma;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import com.articulate.sigma.jedit.StandaloneOnlyTestSuite;

/**
 * This is a Github continuous integration (CI)-only test suite that runs non-GUI unit tests only.
 * 
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

@RunWith(Suite.class)
@Suite.SuiteClasses({
    StandaloneOnlyTestSuite.class
    // SuggestionComponentsIntegrationTest.class
})
public class CINonGuiTestSuite {
}