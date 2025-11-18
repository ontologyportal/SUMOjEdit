package com.articulate.sigma.jedit;

import com.articulate.sigma.jedit.fastac.PrefixIndex;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for the core AutoComplete index structures that live in the
 * SUMOjEdit repository and have no GUI or external dependencies:
 *
 *  - {@link PrefixIndex} in the fast AC pipeline
 *  - {@link SuggestionIndex} used by {@link AutoCompleteManager}
 *
 * These tests deliberately avoid touching jEdit classes, KBmanager, or
 * any external repositories so they can run as pure unit tests.
 */
/**
 *
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class AutoCompleteIndexTest {

    // ---------------------------------------------------------------------
    // PrefixIndex tests
    // ---------------------------------------------------------------------

    @Test
    public void testPrefixIndexBuildAndSuggestBasic() {
        PrefixIndex idx = new PrefixIndex();
        List<String> words = Arrays.asList("Animal", "Animate", "agent", "Zebra");
        idx.build(words);

        // typed length <= 4 → bucket-based suggestions, case-insensitive,
        // sorted using CASE_INSENSITIVE_ORDER.
        List<String> result = idx.suggest("a", 10);
        assertEquals(Arrays.asList("agent", "Animal", "Animate"), result);

        // Asking for prefix that does not exist should return empty list.
        assertEquals(Collections.emptyList(), idx.suggest("xyz", 10));
    }

    @Test
    public void testPrefixIndexSuggestLongerThanFourChars() {
        PrefixIndex idx = new PrefixIndex();
        List<String> words = Arrays.asList("Animal", "Animate", "agent");
        idx.build(words);

        // typed length > 4 → bucket narrowed by full startsWith() on the
        // lower-cased typed prefix.
        List<String> result = idx.suggest("animat", 10);
        assertEquals(Collections.singletonList("Animate"), result);
    }

    @Test
    public void testPrefixIndexSuggestRespectsLimit() {
        PrefixIndex idx = new PrefixIndex();
        List<String> words = Arrays.asList("Animal", "Animate", "agent", "alpha");
        idx.build(words);

        List<String> result = idx.suggest("a", 2);
        // Should return only the first two (case-insensitive sorted)
        assertEquals(2, result.size());
        assertEquals("agent", result.get(0));
        assertEquals("alpha", result.get(1));
    }

    @Test
    public void testPrefixIndexFuzzyBasic() {
        PrefixIndex idx = new PrefixIndex();
        List<String> words = Arrays.asList("animal", "animals", "Animate", "agent", "zebra");
        idx.build(words);

        // Small typo that should match "animal" best.
        List<String> result = idx.fuzzy("animl", 3);
        assertFalse(result.isEmpty());
        assertEquals("animal", result.get(0));
    }

    @Test
    public void testPrefixIndexFuzzyShortOrNullPrefix() {
        PrefixIndex idx = new PrefixIndex();
        idx.build(Arrays.asList("animal", "agent"));

        assertEquals(Collections.emptyList(), idx.fuzzy(null, 10));
        assertEquals(Collections.emptyList(), idx.fuzzy("", 10));
        assertEquals(Collections.emptyList(), idx.fuzzy("an", 10)); // < 3 chars
    }

    // ---------------------------------------------------------------------
    // SuggestionIndex tests
    // ---------------------------------------------------------------------

    @Test
    public void testSuggestionIndexAddAndStartsWithCaseInsensitive() {
        SuggestionIndex si = new SuggestionIndex();

        // KB layer
        si.addKB("Animal");
        si.addKB("Animate");

        // Buffer layer
        si.add("animalistic");

        List<String> out = si.startsWith("ani", 10, false);
        // Case-insensitive, KB layer first, then buffer layer, preserving insertion order.
        assertEquals(Arrays.asList("Animal", "Animate", "animalistic"), out);
    }

    @Test
    public void testSuggestionIndexCaseSensitive() {
        SuggestionIndex si = new SuggestionIndex();

        si.addKB("Animal");
        si.addKB("animate");
        si.add("Annotate");

        // Case-sensitive: only tokens that literally start with "An"
        List<String> out = si.startsWith("An", 10, true);
        assertEquals(Arrays.asList("Animal", "Annotate"), out);

        // Different case prefix should not match "Animal"
        out = si.startsWith("an", 10, true);
        assertEquals(Collections.singletonList("animate"), out);
    }

    @Test
    public void testSuggestionIndexClearBufferLayer() {
        SuggestionIndex si = new SuggestionIndex();

        si.addKB("Animal");
        si.add("animalistic");

        assertEquals(2, si.startsWith("ani", 10, false).size());

        si.clearBufferLayer();

        // After clearing buffer layer, only KB token should remain.
        List<String> out = si.startsWith("ani", 10, false);
        assertEquals(Collections.singletonList("Animal"), out);
    }

    @Test
    public void testSuggestionIndexAddAllKB() {
        SuggestionIndex si = new SuggestionIndex();

        si.addAllKB(Arrays.asList("Animal", "Animate", "agent"));

        List<String> out = si.startsWith("a", 10, false);
        assertEquals(3, out.size());
        assertTrue(out.contains("Animal"));
        assertTrue(out.contains("Animate"));
        assertTrue(out.contains("agent"));
    }

    @Test
    public void testSuggestionIndexLimitRespected() {
        SuggestionIndex si = new SuggestionIndex();

        si.addKB("Animal");
        si.addKB("Animate");
        si.addKB("agent");

        List<String> out = si.startsWith("a", 2, false);
        assertEquals(2, out.size());
    }
}