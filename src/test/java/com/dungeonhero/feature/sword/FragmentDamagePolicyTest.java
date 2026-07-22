package com.dungeonhero.feature.sword;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FragmentDamagePolicyTest {

    private final FragmentDamagePolicy policy = FragmentDamagePolicy.defaults();

    @Test
    void rankFourCapsActiveDamageAndKeepsOverflow() {
        assertEquals(55, policy.effective(114, 4));
        assertEquals(59, policy.overflow(114, 4));
        assertEquals(114, policy.sanitizeTotal(114));
    }

    @Test
    void malformedAndExcessiveValuesAreBounded() {
        assertEquals(0, policy.sanitizeTotal(Double.NaN));
        assertEquals(0, policy.sanitizeTotal(Double.POSITIVE_INFINITY));
        assertEquals(280, policy.effective(1000000, 10));
    }
}
