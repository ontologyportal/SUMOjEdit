package com.articulate.sigma.jedit;

import errorlist.DefaultErrorSource;
import errorlist.ErrorSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for a subset of helper methods that underpin the
 * “Format SUO‑KIF Axioms” functionality in {@link SUMOjEdit}.  The
 * formatting pipeline delegates the heavy lifting of SUO‑KIF parsing
 * and reprinting to the underlying KIF parser, but it still
 * includes a number of string and diagnostic helpers that can be
 * independently verified. These tests exercise those private
 * helpers via reflection without invoking the external parser or
 * running jEdit itself. 
 * 
 * All unit tests in this test suite are designed to be standalone 
 * and self-contained and not rely on any external state or resources. 
 * This means that they should not require jEdit to be running, and 
 * should not depend on any files or resources outside of the test code 
 * itself.
 * 
 * That being said, methods/functions that depend on the jEdit UI 
 * and the external KIF parser (formatSelect, formatBuffer, 
 * clearWarnAndErr, parseKif, clearKif, etc.), are excluded from 
 * this test suite, as they are better suited for integration 
 * testing with the jEdit environment and the KIF parser.
 *
 * <p>Note: methods whose behaviour overlaps with the SUO‑KIF error
 * checking tests are intentionally omitted to avoid duplicate
 * coverage.</p>
 *
 *
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class FormatSUOKIFAxiomsTest {

    private SUMOjEdit sje;

    /**
     * Set up a fresh {@link SUMOjEdit} instance and register an
     * {@link errorlist.ErrorSource} prior to each test.  Without an
     * ErrorSource the class logs would throw when adding diagnostics
     * during validation.
     */
    @Before
    public void setUp() {
        sje = new SUMOjEdit();
        sje.errsrc = new DefaultErrorSource(sje.getClass().getName(), null);
        ErrorSource.registerErrorSource(sje.errsrc);
    }

    /**
     * Unregister the ErrorSource and clear any accumulated state
     * after each test.  Leaving stray error messages registered can
     * interfere with other tests when they inspect the pending
     * diagnostic queue.
     */
    @After
    public void tearDown() {
        ErrorSource.unregisterErrorSource(sje.errsrc);
        sje.errsrc.clear();
        sje = null;
    }

    /**
     * Verify that the private static helper {@code truncateWithEllipsis}
     * behaves as expected.  Null inputs should produce an empty
     * string, short inputs should be returned unchanged (aside from
     * whitespace stripping) and long inputs should be truncated and
     * suffixed with a Unicode ellipsis.  The trimming uses
     * {@code String.strip()} semantics, so both leading and trailing
     * whitespace are removed prior to evaluating length.
     */
    @Test
    public void testTruncateWithEllipsis() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod("truncateWithEllipsis", String.class, int.class);
        m.setAccessible(true);

        // Null input yields empty string
        assertEquals("", m.invoke(null, (String) null, 10));

        // Short input is returned unchanged after stripping
        assertEquals("Hello", m.invoke(null, "  Hello  ", 10));

        // Input exactly equal to max should not be truncated
        assertEquals("Exact", m.invoke(null, "Exact", 5));

        // Input longer than max should be truncated and suffixed with an ellipsis
        assertEquals("Hello…", m.invoke(null, "Hello world", 5));
        // Whitespace is stripped before truncation
        assertEquals("Hello…", m.invoke(null, "  Hello world  ", 5));
    }

    /**
     * Verify that {@code normalizeBaseMessage} rewrites verbose
     * diagnostic messages into a more succinct form when they follow
     * the "Term not below Entity" pattern.  Messages that do not
     * match the special prefix are returned unchanged.  Null inputs
     * should yield an empty string.
     */
    @Test
    public void testNormalizeBaseMessage() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod("normalizeBaseMessage", String.class, String.class);
        m.setAccessible(true);

        // Null base message yields empty string
        assertEquals("", m.invoke(sje, (String) null, "irrelevant"));

        // Matching pattern with a simple instance should collapse to the offending term
        String msg1 = "Term not below Entity: (instance ?X Term)";
        assertEquals("Term not below Entity: Term", m.invoke(sje, msg1, ""));

        // Matching pattern with another term should extract that term
        String msg2 = "Term not below Entity: (instance ?X AnotherTerm)";
        assertEquals("Term not below Entity: AnotherTerm", m.invoke(sje, msg2, ""));

        // Non‑matching prefix should return the original string
        String other = "Some other diagnostic";
        assertEquals(other, m.invoke(sje, other, ""));
    }

    /**
     * Verify that {@code checkEditorContents} returns {@code false}
     * when the supplied text is null, blank or shorter than two
     * characters.  In those cases the method enqueues a single
     * diagnostic record into the internal pending error list.  Valid
     * inputs should pass the check and produce no new diagnostics.
     */
    @Test
    public void testCheckEditorContents() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod("checkEditorContents", String.class, String.class);
        m.setAccessible(true);

        // Access the private pending error queue so we can inspect diagnostics
        Field pendField = SUMOjEdit.class.getDeclaredField("_pendingErrs");
        pendField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> pending = (List<Object>) pendField.get(sje);

        // Prepare a message for diagnostics
        String warnMsg = "Please highlight something";

        // Null content should fail and queue one warning
        boolean resultNull = (Boolean) m.invoke(sje, (String) null, warnMsg);
        assertFalse(resultNull);
        assertEquals("Expected one pending diagnostic for null content", 1, pending.size());
        Object recNull = pending.get(0);
        // Extract the msg field from the diagnostic
        Field fMsg = recNull.getClass().getDeclaredField("msg");
        fMsg.setAccessible(true);
        assertEquals(warnMsg, fMsg.get(recNull));

        // Clear diagnostics before next check
        pending.clear();

        // Too short content (single character) should also fail
        boolean resultShort = (Boolean) m.invoke(sje, "a", warnMsg);
        assertFalse(resultShort);
        assertEquals("Expected one pending diagnostic for single character", 1, pending.size());
        pending.clear();

        // Valid content (two characters) should pass and add no diagnostics
        boolean resultOk = (Boolean) m.invoke(sje, "ab", warnMsg);
        assertTrue(resultOk);
        assertTrue("Expected no pending diagnostics for valid content", pending.isEmpty());
    }

    /**
     * Verify that {@code getLineNum} extracts the first line number
     * from various formatted messages.  SigmaAntlr style messages
     * separate the line from the remainder with a colon, whereas
     * KIF parser messages embed the number after the word "line" or
     * an HTML‑escaped variant.  When no line number can be found the
     * method should return 0.
     */
    @Test
    public void testGetLineNum() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod("getLineNum", String.class);
        m.setAccessible(true);

        // SigmaAntlr style: digits followed by a colon
        assertEquals(10, ((Integer) m.invoke(sje, "10: parse error")).intValue());
        assertEquals(123, ((Integer) m.invoke(sje, "123: something bad")).intValue());

        // KIF parser style: "line" followed by a number
        assertEquals(5, ((Integer) m.invoke(sje, "line 5: problem")).intValue());
        // Accept optional colon after the word line
        assertEquals(7, ((Integer) m.invoke(sje, "line: 7 unexpected")).intValue());

        // HTML escaped colon (&amp;#58; is the code for ':')
        assertEquals(8, ((Integer) m.invoke(sje, "line&#58; 8 trouble")).intValue());

        // When no number is present the method should return 0
        assertEquals(0, ((Integer) m.invoke(sje, "no line info")).intValue());
    }

    /**
     * Verify that {@code getOffset} extracts the first column offset
     * from SigmaAntlr style messages.  Offsets are denoted by a
     * colon, the digits, then another colon.  When no offset is
     * present the method should return 0.
     */
    @Test
    public void testGetOffset() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod("getOffset", String.class);
        m.setAccessible(true);

        // Standard pattern: colon, digits, colon
        assertEquals(3, ((Integer) m.invoke(sje, "err:3: something")).intValue());
        assertEquals(34, ((Integer) m.invoke(sje, "12:34: message")).intValue());
        // If no offset indicator exists return 0
        assertEquals(0, ((Integer) m.invoke(sje, "no offset here")).intValue());
    }

    /**
     * Verify that {@code appendSnippet} attaches a truncated snippet
     * from the specified file to the base message.  When the base
     * message already contains the snippet the method should not
     * duplicate it.  Messages matching the special "Term not below
     * Entity" pattern should be normalised before the snippet is
     * appended.
     */
    @Test
    public void testAppendSnippet() throws Exception {
        // Create a temporary file with a few lines
        Path tmp = Files.createTempFile("snippet", ".kif");
        try {
            String content = String.join("\n",
                    "First line of the file",
                    "Second line contains important information",
                    "Third line");
            Files.writeString(tmp, content);

            Method m = SUMOjEdit.class.getDeclaredMethod(
                    "appendSnippet", String.class, String.class, int.class);
            m.setAccessible(true);

            // Case 1: append snippet to a simple message
            String base = "Some error";
            String appended = (String) m.invoke(sje, base, tmp.toString(), 1);
            assertEquals("Some error — Second line contains important information", appended);

            // Case 2: normalisation is applied before appending
            String verbose = "Term not below Entity: (instance ?X Term)";
            String appendedNorm = (String) m.invoke(sje, verbose, tmp.toString(), 2);
            assertEquals("Term not below Entity: Term — Third line", appendedNorm);

            // Case 3: if the message already contains the snippet do not duplicate
            String already = "Some error — First line of the file";
            String noDup = (String) m.invoke(sje, already, tmp.toString(), 0);
            assertEquals(already, noDup);
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignore) {}
        }
    }
} // end class file FormatSuoKifAxiomsTest.java