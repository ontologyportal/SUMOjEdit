package com.articulate.sigma.jedit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * Standalone unit tests for the private helper
 * {@code safeSnippetFromFile(String filePath, int zeroBasedLine)} in
 * {@link SUMOjEdit}.
 *
 * This helper is responsible for returning a single-line "snippet"
 * suitable for status-bar / ErrorList display, with the following
 * behaviour:
 *
 *  - Reads all lines from the given file.
 *  - If the requested line index is out of range, returns the empty
 *    string.
 *  - If the line is {@code null}, returns the empty string.
 *  - Otherwise, strips leading and trailing whitespace.
 *  - If the stripped line length is {@code <= SNIPPET_MAX}, returns
 *    it unchanged.
 *  - If the stripped line length is greater than {@code SNIPPET_MAX},
 *    returns the first {@code SNIPPET_MAX} characters with NO
 *    ellipsis.
 *
 * These tests drive that contract directly via reflection, using
 * temporary files so there are no external dependencies.
 *
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SafeSnippetFromFileTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class SafeSnippetFromFileTest {

    private SUMOjEdit sje;
    private Method safeSnippetMethod;
    private int snippetMax;

    private Path tempFile;

    @Before
    public void setUp() throws Exception {
        sje = new SUMOjEdit();

        // Reflectively obtain the private helper:
        //   private static String safeSnippetFromFile(String filePath, int zeroBasedLine)
        safeSnippetMethod = SUMOjEdit.class.getDeclaredMethod(
                "safeSnippetFromFile", String.class, int.class);
        safeSnippetMethod.setAccessible(true);

        // Also obtain SNIPPET_MAX so tests stay aligned with the constant.
        Field maxField = SUMOjEdit.class.getDeclaredField("SNIPPET_MAX");
        maxField.setAccessible(true);
        snippetMax = maxField.getInt(null);

        // Prepare a reusable temporary file for most tests.
        tempFile = Files.createTempFile("SafeSnippetFromFileTest", ".kif");
        tempFile.toFile().deleteOnExit();
    }

    @After
    public void tearDown() throws Exception {
        if (tempFile != null) {
            Files.deleteIfExists(tempFile);
            tempFile = null;
        }
        sje = null;
    }

    // ---------------------------------------------------------------------
    // Helper to invoke the private method
    // ---------------------------------------------------------------------

    private String invokeSafeSnippet(String filePath, int zeroBasedLine) throws Exception {
        return (String) safeSnippetMethod.invoke(null, filePath, zeroBasedLine);
    }

    // ---------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------

    /**
     * Basic happy-path behaviour:
     *
     *  - leading/trailing whitespace is stripped
     *  - a short line (length <= SNIPPET_MAX) is returned as-is after strip
     */
    @Test
    public void testShortLineIsReturnedTrimmedWithoutTruncation() throws Exception {
        String[] lines = {
                "   short line with spaces   ",
                "second line"
        };
        Files.write(tempFile, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));

        String result0 = invokeSafeSnippet(tempFile.toString(), 0);
        String result1 = invokeSafeSnippet(tempFile.toString(), 1);

        assertEquals("short line with spaces", result0);
        assertEquals("second line", result1);
    }

    /**
     * When the line exceeds SNIPPET_MAX characters, the helper should
     * return exactly SNIPPET_MAX characters with no ellipsis.
     */
    @Test
    public void testLongLineIsHardTruncatedToSnippetMax() throws Exception {
        // Construct a line longer than SNIPPET_MAX.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < snippetMax + 20; i++) {
            sb.append('X');
        }
        String longLine = sb.toString();

        Files.write(tempFile, longLine.getBytes(StandardCharsets.UTF_8));

        String result = invokeSafeSnippet(tempFile.toString(), 0);

        assertEquals("Truncated snippet must have length == SNIPPET_MAX",
                snippetMax, result.length());

        // The snippet should be exactly the prefix of the original line.
        String expectedPrefix = longLine.substring(0, snippetMax);
        assertEquals(expectedPrefix, result);
    }

    /**
     * Negative line indices are treated as out-of-range and must return
     * the empty string.
     */
    @Test
    public void testNegativeLineIndexReturnsEmptyString() throws Exception {
        Files.write(tempFile, "only one line".getBytes(StandardCharsets.UTF_8));

        String result = invokeSafeSnippet(tempFile.toString(), -1);
        assertEquals("", result);
    }

    /**
     * Requesting a line index beyond the last line of the file should
     * also yield the empty string.
     */
    @Test
    public void testOutOfRangeLineIndexReturnsEmptyString() throws Exception {
        String[] lines = { "line 0", "line 1" };
        Files.write(tempFile, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));

        // Index 2 is out of range for a 2-line file.
        String result = invokeSafeSnippet(tempFile.toString(), 2);
        assertEquals("", result);
    }

    /**
     * If the file cannot be read at all (e.g. it does not exist), the
     * helper should catch the exception and return the empty string
     * rather than propagating an error.
     */
    @Test
    public void testNonExistentFileReturnsEmptyString() throws Exception {
        File nonExistent = new File("SafeSnippetFromFileTest-does-not-exist-" + System.nanoTime() + ".kif");
        assertFalse("Test precondition: file should not exist", nonExistent.exists());

        String result = invokeSafeSnippet(nonExistent.getAbsolutePath(), 0);
        assertEquals("", result);
    }
}