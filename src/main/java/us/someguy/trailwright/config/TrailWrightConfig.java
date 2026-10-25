/*
 * Purpose:      Server-wide settings read from config/trailwright/trailwright.json. See DESIGN.md §7.
 * Dependencies: Gson (bundled with Minecraft). Config dir is resolved through a small
 *               loader seam so this class stays loader-agnostic. The client settings screen
 *               edits a copy() and applies it with save().
 */
package us.someguy.trailwright.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import us.someguy.trailwright.TrailWright;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TrailWrightConfig {
    public static final String FILE_NAME = "trailwright.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Defaults mirror DESIGN.md §7. Keep the two in sync.
    // Gson instantiates through the implicit no-arg constructor, so keys missing from the
    // file keep these initializers; unknown keys are ignored.
    // There is no sneak multiplier: a sneaking step is not a footfall (DESIGN.md §3.1).
    public boolean enabled = true;
    public float wearMultiplier = 1.0f;
    public float sprintMultiplier = 1.5f;
    public boolean countMounts = true;
    public boolean countMobs = true;
    public float mobMultiplier = 0.5f;
    public float decayPerDay = 2.0f;
    public int pruneIntervalTicks = 6000;
    public boolean respectMobGriefing = true;   // gates mob wear only

    // Volatile: the settings screen swaps the instance on the render thread while the
    // integrated server reads it on the server thread. Readers take one get() per operation,
    // so each sees a consistent set of values.
    private static volatile TrailWrightConfig instance = new TrailWrightConfig();
    // Directory the instance was loaded from; save() writes back there. Null until load().
    private static volatile Path loadedDir;

    public static TrailWrightConfig get() { return instance; }

    /**
     * Loads {@code <dir>/trailwright.json} into the shared instance and returns it. The loader
     * entry point passes {@code config/trailwright}; the directory is created if missing.
     * Missing file: defaults are written. Unreadable or malformed file: a warning is logged,
     * defaults are used, and the file is left as is so the owner can fix it.
     */
    public static TrailWrightConfig load(Path configDir) {
        Path file = configDir.resolve(FILE_NAME);
        TrailWrightConfig loaded;
        if (Files.notExists(file)) {
            loaded = new TrailWrightConfig();
            write(file, loaded);
        } else {
            loaded = read(file);
        }
        loaded.validate();
        instance = loaded;
        loadedDir = configDir;
        return loaded;
    }

    /**
     * Makes {@code updated} the live config and writes it to the file it was loaded from,
     * replacing that file even if it was malformed. Out-of-range values are reset first, as
     * on load. Before any load() the instance still changes, and nothing is written.
     */
    public static void save(TrailWrightConfig updated) {
        updated.validate();
        instance = updated;
        Path dir = loadedDir;
        if (dir == null) return;
        Path file = dir.resolve(FILE_NAME);
        try {
            Files.createDirectories(dir);
            Files.writeString(file, GSON.toJson(updated));
            TrailWright.LOG.info("Saved config to {}", file);
        } catch (IOException e) {
            TrailWright.LOG.warn("Could not save config to {}: {}", file, e.getMessage());
        }
    }

    /** An independent copy, for editing without touching the live instance. */
    public TrailWrightConfig copy() {
        return GSON.fromJson(GSON.toJson(this), TrailWrightConfig.class);
    }

    // Range rules shared by validate() and the settings screen.
    // `v >= 0` is false for NaN, which Gson accepts from the literal NaN.
    public static boolean isValidMultiplier(float v) { return v >= 0f; }
    public static boolean isValidPruneInterval(int v) { return v > 0; }

    private static TrailWrightConfig read(Path file) {
        try {
            TrailWrightConfig parsed = GSON.fromJson(Files.readString(file), TrailWrightConfig.class);
            if (parsed != null) return parsed;
            TrailWright.LOG.warn("{} is empty; using defaults", file);
        } catch (IOException | JsonParseException e) {
            TrailWright.LOG.warn("Could not read {}; using defaults. Cause: {}", file, e.getMessage());
        }
        return new TrailWrightConfig();
    }

    private static void write(Path file, TrailWrightConfig config) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(config));
            TrailWright.LOG.info("Wrote default config to {}", file);
        } catch (IOException e) {
            TrailWright.LOG.warn("Could not write default config to {}: {}", file, e.getMessage());
        }
    }

    /** Resets any out-of-range value to its default, with a warning naming the key. */
    private void validate() {
        TrailWrightConfig d = new TrailWrightConfig();
        if (!isValidMultiplier(wearMultiplier))     { warnReset("wearMultiplier", wearMultiplier);     wearMultiplier = d.wearMultiplier; }
        if (!isValidMultiplier(mobMultiplier))      { warnReset("mobMultiplier", mobMultiplier);       mobMultiplier = d.mobMultiplier; }
        if (!isValidMultiplier(sprintMultiplier))   { warnReset("sprintMultiplier", sprintMultiplier); sprintMultiplier = d.sprintMultiplier; }
        if (!isValidMultiplier(decayPerDay))        { warnReset("decayPerDay", decayPerDay);           decayPerDay = d.decayPerDay; }
        if (!isValidPruneInterval(pruneIntervalTicks)) { warnReset("pruneIntervalTicks", pruneIntervalTicks); pruneIntervalTicks = d.pruneIntervalTicks; }
    }

    private static void warnReset(String key, Object value) {
        TrailWright.LOG.warn("Config {} = {} is out of range; using the default", key, value);
    }
}
