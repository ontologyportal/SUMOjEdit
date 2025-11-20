package com.articulate.sigma.jedit;

import errorlist.DefaultErrorSource;
import errorlist.ErrorSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import com.articulate.sigma.ErrRec;
import errorlist.DefaultErrorSource.DefaultError;

import static org.junit.Assert.*;

/**
 * Unit tests for helper methods on {@link SUMOjEdit} that live in the
 * SUMOjEdit repository.  These tests exercise private utility functions
 * such as line/offset parsing, message normalization and snippet
 * appending.  They avoid touching any of the Sigmakee classes or
 * performing end‑to‑end error checking.  Reflection is used to
 * access private methods in order to verify their behaviour.
 *
 *
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class SUOKIFErrorCheckTest {

    private SUMOjEdit sje;

    @Before
    public void setUp() {
        // initialise a new SUMOjEdit instance and register a fresh
        // ErrorSource.  Without an ErrorSource the class will throw
        // when logging parse warnings/errors.
        sje = new SUMOjEdit();
        sje.errsrc = new DefaultErrorSource(sje.getClass().getName(), null);
        ErrorSource.registerErrorSource(sje.errsrc);
    }

    @After
    public void tearDown() {
        // clean up the ErrorSource to avoid polluting other tests
        ErrorSource.unregisterErrorSource(sje.errsrc);
        sje.errsrc.clear();
        sje = null;
    }

    /**
     * Verify that {@code getLineNum()} correctly extracts the 0‑based
     * line number from a variety of warning/error formats.  When no
     * numeric line can be found the method should return 0.
     */
    @Test
    public void testGetLineNum() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod("getLineNum", String.class);
        m.setAccessible(true);

        // Format produced by the SigmaAntlr parser: "5:10: ..." should yield line 5
        assertEquals(5, ((Integer) m.invoke(sje, "5:10: mismatched input")).intValue());

        // Format with explicit text: "line: 7" should yield 7
        assertEquals(7, ((Integer) m.invoke(sje, "line: 7 unexpected token")).intValue());

        // HTML escaped colon version: "line&#58; 10" should yield 10
        assertEquals(10, ((Integer) m.invoke(sje, "line&#58; 10: something went wrong")).intValue());

        // No line number should default to 0
        assertEquals(0, ((Integer) m.invoke(sje, "no line info here")).intValue());
    }

    /**
     * Verify that {@code getOffset()} extracts the column offset from a
     * warning/error string and defaults to 0 when no offset is present.
     */
    @Test
    public void testGetOffset() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod("getOffset", String.class);
        m.setAccessible(true);

        // Standard colon separated format: second numeric is the offset
        assertEquals(10, ((Integer) m.invoke(sje, "5:10: mismatched input")).intValue());

        // When no pattern is found the offset should be 0
        assertEquals(0, ((Integer) m.invoke(sje, "no offset available")).intValue());
    }

    /**
     * Verify that {@code normalizeBaseMessage()} collapses verbose "Term
     * not below Entity" messages down to only the offending term and
     * leaves all other messages untouched.
     */
    @Test
    public void testNormalizeBaseMessage() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod(
                "normalizeBaseMessage", String.class, String.class);
        m.setAccessible(true);

        // When the message matches the special pattern the offending term
        // (here "Human") should be extracted
        String base = "Term not below Entity: (instance ?X Human)";
        String expected = "Term not below Entity: Human";
        String actual = (String) m.invoke(sje, base, "");
        assertEquals(expected, actual);

        // Unrelated messages should be returned verbatim
        String other = "Some other diagnostic";
        actual = (String) m.invoke(sje, other, "");
        assertEquals(other, actual);
    }

    /**
     * Verify that {@code appendSnippet()} appends a snippet of the
     * offending line to the base message when it is not already present.
     */
    @Test
    public void testAppendSnippetAddsSnippet() throws Exception {
        // Prepare a temporary file with known content; appendSnippet will
        // fall back to reading from disk because there is no active buffer.
        File tmp = File.createTempFile("sumojedit", ".kif");
        tmp.deleteOnExit();
        java.nio.file.Files.writeString(tmp.toPath(), "First\nSecond line of interest\nThird\n");

        Method appendMethod = SUMOjEdit.class.getDeclaredMethod(
                "appendSnippet", String.class, String.class, int.class);
        appendMethod.setAccessible(true);

        String base = "Error at";
        // Request the snippet from the second line (index 1)
        String result = (String) appendMethod.invoke(sje, base, tmp.getAbsolutePath(), 1);

        // The base message should be retained
        assertTrue(result.startsWith(base));
        // The returned message should include the truncated snippet from the file
        assertTrue(result.contains("Second line of interest"));
    }

    /**
     * Verify that {@code appendSnippet()} avoids duplicating the snippet
     * when the offending line is already included in the base message.
     */
    @Test
    public void testAppendSnippetAvoidsDuplication() throws Exception {
        File tmp = File.createTempFile("sumojedit", ".kif");
        tmp.deleteOnExit();
        String line = "(instance Human Animal)";
        java.nio.file.Files.writeString(tmp.toPath(), line + "\n");

        Method appendMethod = SUMOjEdit.class.getDeclaredMethod(
                "appendSnippet", String.class, String.class, int.class);
        appendMethod.setAccessible(true);

        String base = "Error: " + line;
        String result = (String) appendMethod.invoke(sje, base, tmp.getAbsolutePath(), 0);

        // Count occurrences of the offending line; should be exactly one
        int occurrences = result.split(java.util.regex.Pattern.quote(line), -1).length - 1;
        assertEquals(1, occurrences);
    }

    /**
     * Verify that the KIF parser reports success on a well‑formed formula and
     * fails on malformed input.  This test exercises the private
     * {@code parseKif()} method via reflection and ensures that the
     * underlying {@link com.articulate.sigma.KIF} collections are cleared
     * appropriately.
     */
    @Test
    public void testParseKifValidAndInvalid() throws Exception {
        Method parse = SUMOjEdit.class.getDeclaredMethod("parseKif", String.class);
        parse.setAccessible(true);

        // Valid SUO‑KIF should parse without exceptions
        sje.kif.filename = "valid.kif";
        sje.kif.warningSet.clear();
        sje.kif.errorSet.clear();
        boolean ok = (Boolean) parse.invoke(sje, "(instance Human Animal)");
        assertTrue("Expected parseKif to return true for valid KIF", ok);
        assertTrue("No warnings should be recorded for valid KIF", sje.kif.warningSet.isEmpty());
        assertTrue("No errors should be recorded for valid KIF", sje.kif.errorSet.isEmpty());

        // Malformed input should cause parseKif to return false and record errors
        sje.kif.filename = "invalid.kif";
        sje.kif.warningSet.clear();
        sje.kif.errorSet.clear();
        ok = (Boolean) parse.invoke(sje, "(instance Human Animal");
        assertFalse("Expected parseKif to return false for malformed KIF", ok);
        assertFalse("Errors should be recorded for malformed KIF", sje.kif.errorSet.isEmpty());
    }

    /**
     * Verify that {@code logKifWarnAndErr()} converts entries in
     * {@link com.articulate.sigma.KIF#warningSet} and
     * {@link com.articulate.sigma.KIF#errorSet} into jEdit error list
     * entries.  Warnings and errors should be distinguished and the
     * offending line snippets appended to the messages.
     */
    @Test
    public void testLogKifWarnAndErr() throws Exception {
        // Create a temporary file with at least four lines
        File tmp = File.createTempFile("logtest", ".kif");
        tmp.deleteOnExit();
        java.nio.file.Files.writeString(tmp.toPath(), "a\nb\nc\nd\n");

        // Populate the KIF diagnostic sets
        sje.kif.filename = tmp.getAbsolutePath();
        sje.kif.warningSet.clear();
        sje.kif.errorSet.clear();
        sje.kif.warningSet.add("2:1: warning message");
        sje.kif.errorSet.add("line: 4 error message");

        // Invoke the private method
        Method log = SUMOjEdit.class.getDeclaredMethod("logKifWarnAndErr");
        log.setAccessible(true);
        log.invoke(sje);

        // Allow any queued EDT tasks to complete
        Thread.sleep(100);

        // We expect exactly two diagnostics to be recorded
        assertEquals("Expected two diagnostics", 2, sje.errsrc.getErrorCount());

        errorlist.ErrorSource.Error[] errs = sje.errsrc.getAllErrors();
        assertEquals("There should be exactly two diagnostics", 2, errs.length);
        boolean foundWarn = false;
        boolean foundErr = false;
        for (errorlist.ErrorSource.Error er : errs) {
            if (er.getErrorType() == ErrorSource.WARNING) {
                foundWarn = true;
                assertTrue("Warning message must contain original text", er.getErrorMessage().contains("warning message"));
            } else if (er.getErrorType() == ErrorSource.ERROR) {
                foundErr = true;
                assertTrue("Error message must contain original text", er.getErrorMessage().contains("error message"));
            }
        }
        assertTrue("Both warning and error diagnostics must be present", foundWarn && foundErr);
    }

    /**
     * Verify that {@code addErrorsDirect()} sorts diagnostics by line and
     * attaches the offending line snippets.  Two unsorted errors are
     * provided; the method should reorder them and append appropriate
     * snippets based on the file contents.
     */
    @Test
    public void testAddErrorsDirect() throws Exception {
        // Prepare a file with two lines for snippet extraction
        File tmp = File.createTempFile("adderrors", ".kif");
        tmp.deleteOnExit();
        java.nio.file.Files.writeString(tmp.toPath(), "alpha beta gamma\nsecond line\n");

        // Clear previous errors and ensure no view is active so that file read is used.
        // The "view" field on SUMOjEdit is private, so use reflection to null it out.
        sje.errsrc.clear();
        java.lang.reflect.Field viewField = SUMOjEdit.class.getDeclaredField("view");
        viewField.setAccessible(true);
        viewField.set(sje, null);

        // Create two ErrRec instances out of order (line 1 before line 0)
        ErrRec rec2 = new ErrRec(ErrRec.WARNING, tmp.getAbsolutePath(), 1, 5, 11, "Second");
        ErrRec rec1 = new ErrRec(ErrRec.ERROR, tmp.getAbsolutePath(), 0, 6, 11, "First");
        List<ErrRec> recs = Arrays.asList(rec2, rec1);

        Method add = SUMOjEdit.class.getDeclaredMethod("addErrorsDirect", java.util.List.class);
        add.setAccessible(true);
        add.invoke(sje, recs);

        // After invocation there should be two errors
        assertEquals(2, sje.errsrc.getErrorCount());

        // Check that diagnostics are sorted by line number by inspecting the order of snippets.
        errorlist.ErrorSource.Error[] errs = sje.errsrc.getAllErrors();
        assertEquals("There should be two diagnostics for the file", 2, errs.length);

        // The first diagnostic should correspond to line 0 (alpha beta gamma) and the second to line 1 (second line).
        String msg0 = errs[0].getErrorMessage();
        String msg1 = errs[1].getErrorMessage();
        assertTrue("First diagnostic should include the first line snippet", msg0.contains("alpha beta gamma"));
        assertTrue("Second diagnostic should include the second line snippet", msg1.contains("second line"));
        // The snippets should not be swapped
        assertFalse("First diagnostic should not include the second line snippet", msg0.contains("second line"));
        assertFalse("Second diagnostic should not include the first line snippet", msg1.contains("alpha beta gamma"));
    }
} // end class file SUOKIFErrorCheckTest.java