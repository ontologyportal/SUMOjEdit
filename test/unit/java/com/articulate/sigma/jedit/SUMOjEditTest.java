package com.articulate.sigma.jedit;

import com.articulate.sigma.utils.FileUtil;

import errorlist.ErrorSource;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author <a href="mailto:tdnorbra@nps.edu?subject=com.articulate.sigma.jedit.SUMOjEditTest">Terry Norbraten, NPS MOVES</a>
 */
public class SUMOjEditTest extends Assert {

    static String test;
    SUMOjEdit sje;

    @BeforeClass
    public static void beforeClass() {

        test = System.getenv("ONTOLOGYPORTAL_GIT") + "/SUMOjEdit/test/unit/java/resources/test";
    }

    @Before
    public void beforeTest() {

        // This will call KBmanager.getMgr().initializeOnce()
        sje = new SUMOjEdit(null);
    }

    @After
    public void afterTest() {
        sje = null;
    }

    @Test
    public void testDiagnostics() {

        System.out.println("============= SUMOjEditTest.testDiagnostics ==================");
        String contents = String.join("\n", FileUtil.readLines(test, false));
        sje.checkErrorsBody(contents, test);

        ErrorSource[] errsrcs = ErrorSource.getErrorSources();
        assertTrue(errsrcs.length > 0);

        ErrorSource errsrc = errsrcs[0];
        String msg = errsrc.getAllErrors()[0].getErrorMessage();
        assertTrue(msg.contains("mismatched input ')'"));
    }

} // end class file SUMOjEditTest.java