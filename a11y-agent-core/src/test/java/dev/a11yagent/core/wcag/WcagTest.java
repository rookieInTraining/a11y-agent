package dev.a11yagent.core.wcag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WcagTest {

    @Test
    void criteriaCountsPerVersion() {
        assertEquals(61, Wcag.forConformance(WcagVersion.V2_0, Level.AAA).size());
        assertEquals(78, Wcag.forConformance(WcagVersion.V2_1, Level.AAA).size());
        assertEquals(86, Wcag.forConformance(WcagVersion.V2_2, Level.AAA).size());
        assertEquals(87, Wcag.all().size());
    }

    @Test
    void levelCountsIn22() {
        assertEquals(32, Wcag.forConformance(WcagVersion.V2_2, Level.A).size());
        assertEquals(55, Wcag.forConformance(WcagVersion.V2_2, Level.AA).size());
        assertEquals(30, Wcag.forConformance(WcagVersion.V2_1, Level.A).size());
        assertEquals(50, Wcag.forConformance(WcagVersion.V2_1, Level.AA).size());
    }

    @Test
    void focusVisibleMovesToLevelAIn22() {
        Criterion c = Wcag.get("2.4.7");
        assertEquals(Level.AA, c.levelIn(WcagVersion.V2_1));
        assertEquals(Level.A, c.levelIn(WcagVersion.V2_2));
    }

    @Test
    void parsingRemovedIn22() {
        Criterion c = Wcag.get("4.1.1");
        assertTrue(c.existsIn(WcagVersion.V2_1));
        assertFalse(c.existsIn(WcagVersion.V2_2));
    }

    @Test
    void unknownCriterionThrows() {
        assertThrows(IllegalArgumentException.class, () -> Wcag.get("9.9.9"));
        assertTrue(Wcag.find("9.9.9").isEmpty());
    }

    @Test
    void sortKeyOrdersNumerically() {
        var list = Wcag.forConformance(WcagVersion.V2_2, Level.AAA);
        int i410 = list.indexOf(Wcag.get("2.4.10"));
        int i29 = list.indexOf(Wcag.get("2.4.9"));
        assertTrue(i29 < i410);
    }
}
