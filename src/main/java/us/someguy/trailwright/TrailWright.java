/*
 * Purpose:      Shared constants and logger for TrailWright. Loader-agnostic.
 * Dependencies: SLF4J (bundled with Minecraft).
 */
package us.someguy.trailwright;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TrailWright {
    public static final String MOD_ID = "trailwright";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    /** Enables per-footfall debug logging: -Dtrailwright.debugFootfalls=true */
    public static final boolean DEBUG_FOOTFALLS = Boolean.getBoolean("trailwright.debugFootfalls");

    private TrailWright() {}
}
