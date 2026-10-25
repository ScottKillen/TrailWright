/*
 * Purpose:      Mod Menu integration: gives TrailWright's entry a settings button that opens
 *               TrailWrightConfigScreen.
 * Dependencies: Mod Menu API (compile-time only). Fabric Loader instantiates this class only
 *               through the "modmenu" entrypoint, which only Mod Menu requests, so nothing
 *               else may reference it: without Mod Menu installed, loading it would fail.
 *               Client source set only.
 */
package us.someguy.trailwright.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import us.someguy.trailwright.client.config.TrailWrightConfigScreen;

public final class ModMenuCompat implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return TrailWrightConfigScreen::new;
    }
}
