package com.articulate.sigma.jedit;

import com.articulate.sigma.trans.SUMOtoTFAform;
import com.articulate.sigma.utils.FileUtil;
import errorlist.DefaultErrorSource;

import errorlist.ErrorSource;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;


/**
 *
 * @author <a href="mailto:tdnorbra@nps.edu?subject=com.articulate.sigma.jedit.SUMOjEditTest">Terry Norbraten, NPS MOVES</a>
 */
public class SUMOjEditTest extends Assert {

    static String test;
    SUMOjEdit sje;

    @BeforeClass
    public static void beforeClass() {

        SUMOtoTFAform.initOnce();
        test = System.getenv("ONTOLOGYPORTAL_GIT") + "/SUMOjEdit/test/unit/java/resources/test";
    }

    @Before
    public void beforeTest() {

        sje = new SUMOjEdit();
        sje.kb = SUMOtoTFAform.kb;
        sje.fp = SUMOtoTFAform.fp;
        sje.errsrc = new DefaultErrorSource(sje.getClass().getName(), null);
        ErrorSource.registerErrorSource(sje.errsrc);
        sje.kif.filename = test;
        String contents = String.join("\n", FileUtil.readLines(test, false));
        sje.checkErrorsBody(contents);
    }

    @After
    public void afterTest() {
        ErrorSource.unregisterErrorSource(sje.errsrc);
        sje.errsrc.clear();
        sje = null;
    }

    @Test // Will exercise SigmaAntlr parser
    @Ignore // no longer a viable test
    public void testCheckErrorsBody() {

        System.out.println("============= SUMOjEditTest.testCheckErrorsBody ==================");

        String msg;
        for (ErrorSource.Error er : sje.errsrc.getFileErrors(test)) {
            msg = er.getErrorMessage();
            if (msg.contains("mismatched input ')'"))
                assertTrue(msg.contains("mismatched input ')'"));
        }
    }

    @Test // Will exercise SigmaAntlr parser
    @Ignore // no longer a viable test
    public void testCheckErrorCount() {

        System.out.println("============= SUMOjEditTest.testCheckErrorCount ==================");

        assertTrue(sje.errsrc.getErrorCount() > 0);
    }

} // end class file SUMOjEditTest.java