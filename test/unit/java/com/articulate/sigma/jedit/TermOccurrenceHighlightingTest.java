package com.articulate.sigma.jedit;

import com.articulate.sigma.ErrRec;
import com.articulate.sigma.jedit.SUMOjEdit;

import errorlist.DefaultErrorSource;
import errorlist.ErrorSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Standalone unit tests for the private helper
 * {@code reportAllOccurrencesInBuffer(...)} in {@link SUMOjEdit}.
 *
 * The helper scans the current buffer for a given term, splits the
 * work into chunks for a thread pool, and then batches the results
 * into the ErrorList via {@link errorlist.ErrorSource}. This test
 * invokes the helper via reflection, drains the EDT so the batched
 * errors are fully published, and then verifies:
 *
 *  - Every word-bounded occurrence of the term is reported.
 *  - Substring / partial matches (e.g. "SuperHuman", "Human2") are
 *    ignored according to {@code findTermInLine(...)} semantics.
 *  - Each ErrorSource entry has the expected file path, line, and
 *    start/end offsets for the term.
 * 
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class TermOccurrenceHighlightingTest {

    private SUMOjEdit sje;

    @Before
    public void setUp() {
        sje = new SUMOjEdit();

        // Keep batched ErrRec entries in _pendingErrs for this test instead
        // of flushing them immediately onto the EDT / ErrorList.
        sje.testKeepPendingErrs = true;

        sje.errsrc = new DefaultErrorSource(sje.getClass().getName(), null);
        ErrorSource.registerErrorSource(sje.errsrc);
    }

    @After
    public void tearDown() {
        ErrorSource.unregisterErrorSource(sje.errsrc);
        sje.errsrc.clear();
        sje = null;
    }

    /**
     * Verify that {@code reportAllOccurrencesInBuffer(...)} finds all
     * word-bounded occurrences of "Human" across the buffer, ignores
     * embedded and suffix matches like "SuperHuman" and "Human2", and
     * produces one ErrRec per true hit with the correct 0-based line
     * and offset range.
     *
     * Because the helper batches into ErrorList via addErrorsBatch(...)
     * on the EDT, this test first inspects the internal _pendingErrs
     * buffer and, if it has already been flushed, falls back to reading
     * from the DefaultErrorSource.
     */
    @Test
    public void testReportAllOccurrencesInBufferMarksAllWordBoundedHits() throws Exception {
        final String filePath = "TermOccurrenceHighlightingTest.kif";
        final String term = "Human";
        final String errorMessage = "highlight term";

        // Lines chosen to exercise:
        //  - valid standalone terms
        //  - term embedded in a larger identifier (SuperHuman)
        //  - term with a term-character suffix (Human2)
        //  - occurrences across multiple lines
        final String[] bufferLines = new String[] {
                "Human SuperHuman Human2 Human.",
                "(instance Human Human)",
                "(subclass Human Animal)",
                "(=> (instance ?X Human) (instance ?X Animal))"
        };

        // Private helper:
        //   void reportAllOccurrencesInBuffer(
        //       String filePath,
        //       String term,
        //       String errorMessage,
        //       String[] bufferLines,
        //       int errorType
        //   )
        Method m = SUMOjEdit.class.getDeclaredMethod(
                "reportAllOccurrencesInBuffer",
                String.class,
                String.class,
                String.class,
                String[].class,
                int.class
        );
        m.setAccessible(true);

        // Invoke the helper under test.
        m.invoke(sje, filePath, term, errorMessage, bufferLines, ErrorSource.ERROR);

        // Collect results either from the internal pending batch (before the
        // EDT flush) or, if already applied, from the DefaultErrorSource.
        List<ErrRec> results = new ArrayList<>();

        Field pendingField = SUMOjEdit.class.getDeclaredField("_pendingErrs");
        pendingField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ErrRec> pending = (List<ErrRec>) pendingField.get(sje);
        synchronized (pending) {
            if (!pending.isEmpty()) {
                results.addAll(pending);
            }
        }

        if (results.isEmpty()) {
            ErrorSource.Error[] errors = sje.errsrc.getFileErrors(filePath);
            assertNotNull("Expected errors to be registered for file " + filePath, errors);
            for (ErrorSource.Error e : errors) {
                results.add(new ErrRec(
                        e.getErrorType(),
                        e.getFilePath(),
                        e.getLineNumber(),
                        e.getStartOffset(),
                        e.getEndOffset(),
                        e.getErrorMessage()
                ));
            }
        }

        assertEquals("Unexpected number of term occurrences reported", 6, results.size());

        // Expected positions as produced by findTermInLine(...) with word
        // boundary checks:
        //
        // Line 0: "Human SuperHuman Human2 Human."
        //   -> "Human" at col 0
        //   -> "Human" at col 24 (trailing '.' is not a term char)
        //
        // Line 1: "(instance Human Human)"
        //   -> "Human" at col 10
        //   -> "Human" at col 16
        //
        // Line 2: "(subclass Human Animal)"
        //   -> "Human" at col 10
        //
        // Line 3: "(=> (instance ?X Human) (instance ?X Animal))"
        //   -> "Human" at col 17
        //
        final int[] expectedLines  = {0, 0, 1, 1, 2, 3};
        final int[] expectedStarts = {0, 24, 10, 16, 10, 17};
        final int[] expectedEnds   = {5, 29, 15, 21, 15, 22};

        for (int i = 0; i < results.size(); i++) {
            ErrRec e = results.get(i);

            assertEquals("Line mismatch at index " + i,
                    expectedLines[i], e.line);

            assertEquals("Start offset mismatch at index " + i,
                    expectedStarts[i], e.start);

            assertEquals("End offset mismatch at index " + i,
                    expectedEnds[i], e.end);

            assertEquals("File path mismatch at index " + i,
                    filePath, e.file);

            // The message is wrapped with optional snippets, but the base
            // error text should always be present.
            assertTrue("Error message should contain the base message at index " + i,
                    e.msg.contains(errorMessage));
        }
    }
}