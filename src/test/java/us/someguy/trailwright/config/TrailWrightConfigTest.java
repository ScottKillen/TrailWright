/*
 * Purpose:      Unit tests for TrailWrightConfig loading and saving (TESTING.md T1.1, T1.2
 *               logic, T12.1).
 * Dependencies: JUnit 5, Gson (on the Minecraft classpath).
 */
package us.someguy.trailwright.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TrailWrightConfigTest {
    @TempDir Path dir;

    private Path file() { return dir.resolve(TrailWrightConfig.FILE_NAME); }

    @Test void missingFileWritesDefaults() throws Exception {
        TrailWrightConfig c = TrailWrightConfig.load(dir);
        assertTrue(Files.exists(file()));
        assertEquals(1.0f, c.wearMultiplier);
        // The written file must load back to the same values.
        TrailWrightConfig again = TrailWrightConfig.load(dir);
        assertEquals(c.mobMultiplier, again.mobMultiplier);
        assertEquals(c.pruneIntervalTicks, again.pruneIntervalTicks);
    }

    @Test void malformedFileFallsBackAndIsNotOverwritten() throws Exception {
        String broken = "{ \"wearMultiplier\": 20.0 ";
        Files.writeString(file(), broken);
        TrailWrightConfig c = TrailWrightConfig.load(dir);
        assertEquals(1.0f, c.wearMultiplier);
        assertEquals(broken, Files.readString(file()));
    }

    @Test void emptyFileFallsBack() throws Exception {
        Files.writeString(file(), "");
        assertEquals(1.0f, TrailWrightConfig.load(dir).wearMultiplier);
    }

    @Test void unknownKeysIgnoredAndMissingKeysKeepDefaults() throws Exception {
        Files.writeString(file(), "{ \"wearMultiplier\": 20.0, \"someFutureKey\": true }");
        TrailWrightConfig c = TrailWrightConfig.load(dir);
        assertEquals(20.0f, c.wearMultiplier);
        assertEquals(0.5f, c.mobMultiplier);
        assertTrue(c.countMounts);
        assertTrue(c.countMobs);
        assertTrue(c.respectMobGriefing);
        assertTrue(c.enabled);
    }

    @Test void outOfRangeValuesResetToDefaults() throws Exception {
        Files.writeString(file(), "{ \"sprintMultiplier\": -1, \"pruneIntervalTicks\": 0, \"decayPerDay\": NaN }");
        TrailWrightConfig c = TrailWrightConfig.load(dir);
        assertEquals(1.5f, c.sprintMultiplier);
        assertEquals(6000, c.pruneIntervalTicks);
        assertEquals(2.0f, c.decayPerDay);
    }

    @Test void loadReplacesSharedInstance() throws Exception {
        Files.writeString(file(), "{ \"enabled\": false }");
        TrailWrightConfig c = TrailWrightConfig.load(dir);
        assertSame(c, TrailWrightConfig.get());
        assertFalse(TrailWrightConfig.get().enabled);
    }

    @Test void copyIsIndependent() throws Exception {
        TrailWrightConfig live = TrailWrightConfig.load(dir);
        TrailWrightConfig edit = live.copy();
        edit.wearMultiplier = 7.0f;
        edit.countMobs = false;
        assertEquals(1.0f, live.wearMultiplier);
        assertTrue(live.countMobs);
        assertNotSame(live, edit);
    }

    @Test void saveSwapsInstanceAndWritesFile() throws Exception {
        TrailWrightConfig edit = TrailWrightConfig.load(dir).copy();
        edit.wearMultiplier = 3.5f;
        edit.pruneIntervalTicks = 1200;
        edit.respectMobGriefing = false;
        TrailWrightConfig.save(edit);
        assertSame(edit, TrailWrightConfig.get());
        TrailWrightConfig reloaded = TrailWrightConfig.load(dir);
        assertEquals(3.5f, reloaded.wearMultiplier);
        assertEquals(1200, reloaded.pruneIntervalTicks);
        assertFalse(reloaded.respectMobGriefing);
    }

    @Test void saveReplacesMalformedFile() throws Exception {
        Files.writeString(file(), "{ broken");
        TrailWrightConfig edit = TrailWrightConfig.load(dir).copy();
        edit.decayPerDay = 4.0f;
        TrailWrightConfig.save(edit);
        assertEquals(4.0f, TrailWrightConfig.load(dir).decayPerDay);
    }

    @Test void saveResetsOutOfRangeValues() throws Exception {
        TrailWrightConfig edit = TrailWrightConfig.load(dir).copy();
        edit.mobMultiplier = Float.NaN;
        edit.pruneIntervalTicks = -5;
        TrailWrightConfig.save(edit);
        assertEquals(0.5f, TrailWrightConfig.get().mobMultiplier);
        assertEquals(6000, TrailWrightConfig.load(dir).pruneIntervalTicks);
    }

    @Test void rangeRules() {
        assertTrue(TrailWrightConfig.isValidMultiplier(0f));
        assertFalse(TrailWrightConfig.isValidMultiplier(-0.1f));
        assertFalse(TrailWrightConfig.isValidMultiplier(Float.NaN));
        assertTrue(TrailWrightConfig.isValidPruneInterval(1));
        assertFalse(TrailWrightConfig.isValidPruneInterval(0));
    }
}
