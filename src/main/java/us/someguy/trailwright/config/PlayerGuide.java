/*
 * Purpose:      Writes the player guide (docs/RULES.md, bundled in the jar at
 *               /trailwright/RULES.md) to config/trailwright/RULES.md, so players can read it
 *               without a public repository. Rewritten whenever the bundled copy changes.
 * Dependencies: TrailWright.LOG. Loader-agnostic; the entry point passes the folder and version.
 */
package us.someguy.trailwright.config;

import us.someguy.trailwright.TrailWright;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PlayerGuide {
    public static final String FILE_NAME = "RULES.md";
    /** Where the build copies docs/RULES.md inside the jar. */
    static final String RESOURCE = "/trailwright/RULES.md";

    private static final String FOOTER_START = "\n---\n<sub>Artifact ID";

    private PlayerGuide() {}

    /** Writes or refreshes {@code <trailwrightDir>/RULES.md}. Failures are logged, never thrown. */
    public static void install(Path trailwrightDir, String version) {
        String source;
        try (InputStream in = PlayerGuide.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                TrailWright.LOG.warn("Player guide {} is missing from the jar; not written", RESOURCE);
                return;
            }
            source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            TrailWright.LOG.warn("Could not read the bundled player guide: {}", e.getMessage());
            return;
        }

        String content = render(source, version);
        Path file = trailwrightDir.resolve(FILE_NAME);
        try {
            // Only write when it differs, so an unchanged guide keeps its timestamp.
            if (Files.exists(file) && Files.readString(file, StandardCharsets.UTF_8).equals(content)) return;
            Files.createDirectories(trailwrightDir);
            Files.writeString(file, content, StandardCharsets.UTF_8);
            TrailWright.LOG.info("Wrote player guide to {}", file);
        } catch (IOException e) {
            TrailWright.LOG.warn("Could not write player guide to {}: {}", file, e.getMessage());
        }
    }

    /**
     * The guide as players see it: the developer header comment and the artifact footer
     * removed, line endings normalized, and a note that the file is replaced on updates.
     */
    static String render(String source, String version) {
        String body = source.replace("\r\n", "\n");
        if (body.startsWith("<!--")) {
            int end = body.indexOf("-->");
            if (end >= 0) body = body.substring(end + 3);
        }
        int footer = body.indexOf(FOOTER_START);
        if (footer >= 0) body = body.substring(0, footer);
        body = body.strip();
        return "> Written by TrailWright " + version + ". TrailWright replaces this file whenever its guide"
                + " changes, so keep your own notes in another file.\n\n" + body + "\n";
    }
}
