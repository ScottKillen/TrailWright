/*
 * Purpose:      Reload listener that parses wear rules from data packs
 *               (data/<ns>/wear_rules/*.json) and then from config/trailwright/rules/*.json,
 *               and hands them to WearRules. Also creates the config folder with a disabled
 *               example on first launch. Format: docs/RULES.md.
 * Dependencies: fabric-resource-loader-v0 (SimpleSynchronousResourceReloadListener), Gson,
 *               DataFixerUpper JsonOps.
 */
package us.someguy.trailwright.wear;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import us.someguy.trailwright.TrailWright;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class WearRuleReloader implements SimpleSynchronousResourceReloadListener {
    public static final String DIRECTORY = "wear_rules";
    private static final ResourceLocation ID = new ResourceLocation(TrailWright.MOD_ID, DIRECTORY);
    private static final String EXAMPLE_NAME = "example.json.disabled";

    private final Path rulesDir;

    /** @param rulesDir {@code config/trailwright/rules}, supplied by the loader entry point */
    public WearRuleReloader(Path rulesDir) {
        this.rulesDir = rulesDir;
    }

    @Override
    public ResourceLocation getFabricId() { return ID; }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        List<WearRules.Loaded> rules = new ArrayList<>();

        // Pack order, lowest to highest: a later pack wins a priority tie (docs/RULES.md).
        Map<String, Integer> packIndex = new HashMap<>();
        List<PackResources> packs = manager.listPacks().toList();
        for (int i = 0; i < packs.size(); i++) packIndex.put(packs.get(i).packId(), i);

        Map<ResourceLocation, Resource> found =
                manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json"));
        List<ResourceLocation> files = new ArrayList<>(found.keySet());
        files.sort(null);
        for (ResourceLocation file : files) {
            Resource res = found.get(file);
            try (Reader reader = res.openAsReader()) {
                parse(JsonParser.parseReader(reader), file).ifPresent(rule -> rules.add(new WearRules.Loaded(
                        rule, file, WearRules.Source.DATA_PACK,
                        packIndex.getOrDefault(res.sourcePackId(), -1), 0)));
            } catch (IOException | RuntimeException e) {
                TrailWright.LOG.warn("Wear rule {} could not be read: {}", file, e.getMessage());
            }
        }

        loadConfigFolder(rules);
        WearRules.setLoaded(rules);
    }

    private void loadConfigFolder(List<WearRules.Loaded> rules) {
        if (!Files.isDirectory(rulesDir)) return;
        List<Path> files;
        try (Stream<Path> s = Files.list(rulesDir)) {
            files = s.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            TrailWright.LOG.warn("Could not list {}: {}", rulesDir, e.getMessage());
            return;
        }
        for (int i = 0; i < files.size(); i++) {
            Path p = files.get(i);
            ResourceLocation label = new ResourceLocation(TrailWright.MOD_ID, "config/" + safeName(p));
            int fileIndex = i;
            try (Reader reader = Files.newBufferedReader(p)) {
                parse(JsonParser.parseReader(reader), label).ifPresent(rule -> rules.add(new WearRules.Loaded(
                        rule, label, WearRules.Source.CONFIG_FOLDER, 0, fileIndex)));
            } catch (IOException | RuntimeException e) {
                TrailWright.LOG.warn("Wear rule {} could not be read: {}", p, e.getMessage());
            }
        }
    }

    /** An invalid file is skipped whole. resultOrPartial would keep a partial rule, which can
     *  silently apply out-of-range values (for example an advance_chance of 2). */
    private static Optional<WearRule> parse(JsonElement json, ResourceLocation file) {
        DataResult<WearRule> result = WearRule.CODEC.parse(JsonOps.INSTANCE, json);
        Optional<DataResult.PartialResult<WearRule>> error = result.error();
        if (error.isPresent()) {
            TrailWright.LOG.warn("Wear rule {} is invalid and was skipped: {}", file, error.get().message());
            return Optional.empty();
        }
        return result.result();
    }

    /** Only used to label log lines; ResourceLocation paths allow [a-z0-9/._-]. */
    private static String safeName(Path p) {
        return p.getFileName().toString().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
    }

    /** Creates {@code config/trailwright/rules/} with a disabled example the first time. */
    public static void ensureConfigFolder(Path rulesDir) {
        if (Files.exists(rulesDir)) return;
        try {
            Files.createDirectories(rulesDir);
            Files.writeString(rulesDir.resolve(EXAMPLE_NAME), EXAMPLE);
            TrailWright.LOG.info("Created {} with {}", rulesDir, EXAMPLE_NAME);
        } catch (IOException e) {
            TrailWright.LOG.warn("Could not create {}: {}", rulesDir, e.getMessage());
        }
    }

    // Rename the file to end in .json to enable it. Unknown keys such as _comment are ignored.
    private static final String EXAMPLE = """
            {
              "_comment": "Rename to example.json and run /reload to enable. This rule makes worn coarse dirt become a dirt path. Guide: config/trailwright/RULES.md.",
              "block": "minecraft:coarse_dirt",
              "priority": 0,
              "base_threshold": 20,
              "jitter": 0.35,
              "advance_chance": 0.25,
              "next": [
                { "when": { "type": "always" }, "to": "minecraft:dirt_path" }
              ]
            }
            """;
}
