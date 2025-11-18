package com.articulate.sigma.jedit;

import com.articulate.sigma.jedit.fastac.KifTermIndex;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link KifTermIndex#suggest(String, int)}.
 *
 * These tests avoid any file-system or jEdit Buffer dependencies by injecting
 * synthetic terms directly into the private {@code terms} index via reflection.
 * Only the in-memory suggestion behaviour is exercised.
 *
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.fastac.KifTermIndexTest">Simon Deng, NPS ORISE Intern 2025</a>
 */
public class KifTermIndexTest {

    private KifTermIndex index;
    private Field termsField;
    private ConcurrentSkipListSet<String> originalTerms;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        index = KifTermIndex.get();

        // Access the private ConcurrentSkipListSet<String> terms field.
        termsField = KifTermIndex.class.getDeclaredField("terms");
        termsField.setAccessible(true);

        ConcurrentSkipListSet<String> terms =
                (ConcurrentSkipListSet<String>) termsField.get(index);

        // Snapshot the original contents so we can restore them in tearDown.
        originalTerms = new ConcurrentSkipListSet<>(terms.comparator());
        originalTerms.addAll(terms);

        // Start each test with a clean index.
        terms.clear();
    }

    @After
    @SuppressWarnings("unchecked")
    public void tearDown() throws Exception {
        ConcurrentSkipListSet<String> terms =
                (ConcurrentSkipListSet<String>) termsField.get(index);

        // Restore whatever was in the index before the test ran.
        terms.clear();
        terms.addAll(originalTerms);
    }

    /**
     * Null or empty prefixes should simply return an empty list.
     */
    @Test
    public void testSuggestWithNullOrEmptyPrefix() {
        assertTrue(index.suggest(null, 10).isEmpty());
        assertTrue(index.suggest("", 10).isEmpty());
    }

    /**
     * Verify that {@code suggest()} returns terms that start with the given
     * prefix in a case-insensitive manner, and that the order reflects the
     * underlying case-insensitive sorted set.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testSuggestBasicMatchesCaseInsensitive() throws Exception {
        ConcurrentSkipListSet<String> terms =
                (ConcurrentSkipListSet<String>) termsField.get(index);

        // Populate synthetic terms.  Note mixed case and separators.
        terms.add("Foo");
        terms.add("foobar");
        terms.add("Bar");
        terms.add("Foo_Baz");
        terms.add("baz");

        List<String> result = index.suggest("fo", 10);

        // Expect only the "foo*" entries, ordered by CASE_INSENSITIVE_ORDER:
        // Foo, Foo_Baz, foobar
        assertEquals(3, result.size());
        assertEquals("Foo", result.get(0));
        assertEquals("Foo_Baz", result.get(1));
        assertEquals("foobar", result.get(2));

        // Case-insensitive check: "FO" should yield the same result.
        List<String> upperResult = index.suggest("FO", 10);
        assertEquals(result, upperResult);
    }

    /**
     * Verify that the {@code limit} parameter is respected and acts as a hard cap
     * on the number of suggestions returned.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testSuggestHonoursLimit() throws Exception {
        ConcurrentSkipListSet<String> terms =
                (ConcurrentSkipListSet<String>) termsField.get(index);

        terms.add("Foo");
        terms.add("FooBar");
        terms.add("Foo_Baz");
        terms.add("FooQuux");

        // Request only two suggestions even though more are available.
        List<String> limited = index.suggest("Foo", 2);

        assertEquals(2, limited.size());
        assertEquals("Foo", limited.get(0));
        assertEquals("FooBar", limited.get(1));
    }

    /**
     * When no terms match the given prefix, an empty list should be returned.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testSuggestNoMatches() throws Exception {
        ConcurrentSkipListSet<String> terms =
                (ConcurrentSkipListSet<String>) termsField.get(index);

        terms.add("Alpha");
        terms.add("Beta");
        terms.add("Gamma");

        List<String> result = index.suggest("Foo", 10);
        assertTrue("Expected no matches for prefix 'Foo'", result.isEmpty());
    }
}