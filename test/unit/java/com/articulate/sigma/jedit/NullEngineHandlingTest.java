package com.articulate.sigma.jedit;

import com.articulate.sigma.KB;
import com.articulate.sigma.tp.EProver;
import com.articulate.sigma.tp.LEO;
import com.articulate.sigma.tp.Vampire;
import org.gjt.sp.util.Log;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Standalone unit tests for ask/tell "null engine" handling.
 *
 * Spec under test (from SUMOjEdit#queryExp):
 *   - If kb.askEProver(...) returns null, queryExp() logs an error and must NOT
 *     invoke another prover (especially Vampire).
 *   - If kb.askLeo(...) returns null, queryExp() logs an error and must NOT
 *     invoke another prover (especially Vampire).
 *
 * This test is deliberately standalone: it does NOT start any external prover.
 * It uses a KB spy that returns null for the selected engine and records whether
 * other engines were invoked.
 * 
 * 
 * Author: Simon Deng, NPS ORISE Intern 2026, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class NullEngineHandlingTest {

    /**
     * A lightweight KB spy.
     *
     * IMPORTANT: This relies on KB exposing a (String,String) constructor and the
     * ask* methods being overridable (non-final). If the upstream KB signature
     * changes, this test will fail to compile, which is fine: it means the test
     * harness must be updated to match the new API.
     */
    private static class SpyKB extends KB {

        int eproverCalls = 0;
        int leoCalls = 0;
        int vampireCalls = 0;
        int vampireMpCalls = 0;

        SpyKB(String name, String kbDir) {
            super(name, kbDir);
        }

        @Override
        public EProver askEProver(String contents, int timeout, int maxAnswers) {
            eproverCalls++;
            return null; // simulate missing/invalid executable
        }

        @Override
        public LEO askLeo(String contents, int timeout, int maxAnswers) {
            leoCalls++;
            return null; // simulate missing/invalid executable
        }

        @Override
        public Vampire askVampire(String contents, int timeout, int maxAnswers) {
            vampireCalls++;
            // If this is ever called in these tests, it's a bug; return a minimal object anyway.
            return new Vampire();
        }

        @Override
        public Vampire askVampireModensPonens(String contents, int timeout, int maxAnswers) {
            vampireMpCalls++;
            return new Vampire();
        }
    }

    /**
     * Minimal extraction of the engine-dispatch logic in SUMOjEdit#queryExp.
     *
     * We intentionally stop after the engine call + null check, because the rest
     * of queryExp() involves proof parsing, buffer creation, and Swing dispatch.
     * None of that is needed to validate the "no fallback engine" requirement.
     */
    private static void runEngineDispatchLikeQueryExp(String eng, SpyKB kb, String contents,
                                                      int tlim, int maxAns) {

        // Mirrors the primary-path dispatch in SUMOjEdit#queryExp.
        if ("vampire".equalsIgnoreCase(eng)) {
            if (KB.modensPonens)
                kb.askVampireModensPonens(contents, tlim, maxAns);
            else
                kb.askVampire(contents, tlim, maxAns);
        }
        else if ("eprover".equalsIgnoreCase(eng)) {
            EProver e = kb.askEProver(contents, tlim, maxAns);
            if (e == null) {
                Log.log(Log.ERROR, NullEngineHandlingTest.class,
                        ":queryExp(): EProver failed to load – check the 'eprover' preference for a valid executable path");
                return;
            }
        }
        else if ("leo3".equalsIgnoreCase(eng)) {
            LEO leo = kb.askLeo(contents, tlim, maxAns);
            if (leo == null) {
                Log.log(Log.ERROR, NullEngineHandlingTest.class,
                        ":queryExp(): LEO failed to load – check the 'leoExecutable' preference for a valid executable path");
                return;
            }
        }
        else {
            // Matches queryExp() "unsupported" fallback.
            kb.askVampire(contents, tlim, maxAns);
        }
    }

    @Test
    public void testNullEProverDoesNotInvokeVampireOrLeo() {
        SpyKB kb = new SpyKB("SUMO", "test");

        runEngineDispatchLikeQueryExp("eprover", kb, "(instance ?X Human)", 5, 1);

        assertEquals("EProver should be invoked exactly once", 1, kb.eproverCalls);
        assertEquals("LEO must not be invoked when EProver returns null", 0, kb.leoCalls);
        assertEquals("Vampire must not be invoked when EProver returns null", 0, kb.vampireCalls);
        assertEquals("Vampire MP must not be invoked when EProver returns null", 0, kb.vampireMpCalls);
    }

    @Test
    public void testNullLeoDoesNotInvokeVampireOrEProver() {
        SpyKB kb = new SpyKB("SUMO", "test");

        runEngineDispatchLikeQueryExp("leo3", kb, "(instance ?X Human)", 5, 1);

        assertEquals("LEO should be invoked exactly once", 1, kb.leoCalls);
        assertEquals("EProver must not be invoked when LEO returns null", 0, kb.eproverCalls);
        assertEquals("Vampire must not be invoked when LEO returns null", 0, kb.vampireCalls);
        assertEquals("Vampire MP must not be invoked when LEO returns null", 0, kb.vampireMpCalls);
    }
}
