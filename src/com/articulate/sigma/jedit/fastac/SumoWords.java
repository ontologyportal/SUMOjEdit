package com.articulate.sigma.jedit.fastac;

import java.util.*;

public class SumoWords {
    /** TODO: replace with real extraction from your KB loader. */
    public static List<String> all() {
        // start small; add more terms anytime
        return List.of(
            "instance", "subclass", "and", "or", "not", "=>", "<=>", "exists", "forall",
            "Animal", "Mammal", "Human", "Gorilla", "Broccoli",
            "likes", "hates", "parent", "father", "mother",
            "equal", "Integer", "RealNumber", "List", "member",
            "part", "properPart", "overlaps", "intersects"
        );
    }
}
