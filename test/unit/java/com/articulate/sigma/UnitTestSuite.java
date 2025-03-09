package com.articulate.sigma;

import com.articulate.sigma.jedit.SUMOjEditTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 *
 * @author <a href="mailto:tdnorbra@nps.edu?subject=com.articulate.sigma.UnitTestSuite">Terry
 * Norbraten, NPS MOVES</a>
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
    SUMOjEditTest.class
})
public class UnitTestSuite extends UnitTestBase {

}
