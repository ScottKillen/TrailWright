/*
 * Purpose:      Unit tests for rule precedence (docs/RULES.md "Which rule applies").
 * Dependencies: JUnit 5.
 */
package us.someguy.trailwright.wear;

import org.junit.jupiter.api.Test;
import us.someguy.trailwright.wear.WearRules.Rank;
import us.someguy.trailwright.wear.WearRules.Source;

import static org.junit.jupiter.api.Assertions.*;

class WearRulesRankTest {
    private static Rank pack(int priority, boolean byId, int pack) {
        return new Rank(Source.DATA_PACK, priority, byId, pack, 0);
    }

    @Test void configFolderBeatsAnyDataPackPriority() {
        Rank config = new Rank(Source.CONFIG_FOLDER, -100, false, 0, 0);
        assertTrue(config.compareTo(pack(1000, true, 99)) > 0);
    }

    @Test void higherPriorityWins() {
        assertTrue(pack(1, false, 0).compareTo(pack(0, true, 9)) > 0);
    }

    @Test void onTieIdBeatsTag() {
        assertTrue(pack(0, true, 0).compareTo(pack(0, false, 9)) > 0);
    }

    @Test void stillTiedLaterPackWins() {
        assertTrue(pack(0, true, 3).compareTo(pack(0, true, 2)) > 0);
    }

    @Test void configFilesLaterNameWins() {
        Rank a = new Rank(Source.CONFIG_FOLDER, 0, true, 0, 0);
        Rank b = new Rank(Source.CONFIG_FOLDER, 0, true, 0, 1);
        assertTrue(b.compareTo(a) > 0);
    }
}
