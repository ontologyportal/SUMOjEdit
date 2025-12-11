package com.articulate.sigma;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import com.articulate.sigma.jedit.StandaloneOnlyTestSuite;

/**
 * CI-only test suite that runs non-GUI unit tests.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
    StandaloneOnlyTestSuite.class,
    SuggestionComponentsIntegrationTest.class
})
public class CINonGuiTestSuite {
}