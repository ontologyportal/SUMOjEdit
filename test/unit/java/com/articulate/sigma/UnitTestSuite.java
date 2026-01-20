package com.articulate.sigma;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import com.articulate.sigma.jedit.UnitjEditTestSuite;

/**
 *
 * @author <a href="mailto:tdnorbra@nps.edu?subject=com.articulate.sigma.UnitTestSuite">Terry Norbraten, NPS MOVES</a>
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
    UnitjEditTestSuite.class
    // SuggestionComponentsIntegrationTest.class
})
public class UnitTestSuite {

}
