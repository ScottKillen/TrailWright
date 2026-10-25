/*
 * Purpose:      Unit tests for PlayerGuide: rendering of the shipped guide and when the
 *               config copy is (re)written.
 * Dependencies: JUnit 5. The install tests read the bundled resource, which Gradle's
 *               processResources copies from docs/RULES.md.
 */
package us.someguy.trailwright.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.*;

class PlayerGuideTest {
    @TempDir Path dir;

    private static final String SOURCE = """
            <!--
              Purpose:      dev notes
            -->

            # Writing your own wear rules

            Body text.

            ---
            <sub>Artifact ID: `x` · Last updated: 2026-10-24</sub>
            """;

    @Test void renderDropsDevHeaderAndFooterAndAddsNote() {
        String out = PlayerGuide.render(SOURCE, "1.2.3");
        assertTrue(out.startsWith("> Written by TrailWright 1.2.3."), out);
        assertTrue(out.contains("# Writing your own wear rules\n\nBody text.\n"), out);
        assertFalse(out.contains("Purpose:"), out);
        assertFalse(out.contains("Artifact ID"), out);
    }

    @Test void renderNormalizesCrlf() {
        String out = PlayerGuide.render(SOURCE.replace("\n", "\r\n"), "1");
        assertFalse(out.contains("\r"));
        assertFalse(out.contains("Artifact ID"));
    }

    @Test void installWritesThenLeavesUnchangedThenRefreshes() throws Exception {
        Path file = dir.resolve(PlayerGuide.FILE_NAME);
        PlayerGuide.install(dir, "1.0.0");
        assertTrue(Files.exists(file), "guide not written");
        String first = Files.readString(file);
        assertTrue(first.contains("# Writing your own wear rules"), "not the real guide");
        assertFalse(first.contains("Artifact ID"));

        // Same version: not rewritten (timestamp kept).
        FileTime old = FileTime.fromMillis(1_000_000L);
        Files.setLastModifiedTime(file, old);
        PlayerGuide.install(dir, "1.0.0");
        assertEquals(old, Files.getLastModifiedTime(file));

        // Edited by a player: restored.
        Files.writeString(file, "my notes");
        PlayerGuide.install(dir, "1.0.0");
        assertEquals(first, Files.readString(file));

        // New version: rewritten with the new note.
        PlayerGuide.install(dir, "1.0.1");
        assertTrue(Files.readString(file).startsWith("> Written by TrailWright 1.0.1."));
    }
}
