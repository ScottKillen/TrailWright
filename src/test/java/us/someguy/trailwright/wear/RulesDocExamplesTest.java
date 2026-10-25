/*
 * Purpose:      Parses every JSON example in docs/RULES.md with the real rule codec, so the
 *               player guide cannot drift from what TrailWright accepts.
 * Dependencies: JUnit 5; Gson and DataFixerUpper (Minecraft classpath); Minecraft Bootstrap
 *               (registries back the biome_tag codec).
 */
package us.someguy.trailwright.wear;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class RulesDocExamplesTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyRuleExampleParses() throws Exception {
        // Gradle runs tests from the project directory.
        String doc = Files.readString(Path.of("docs/RULES.md"));
        // \r? because a Windows checkout with core.autocrlf has CRLF line endings.
        Matcher m = Pattern.compile("```json\\r?\\n(.*?)```", Pattern.DOTALL).matcher(doc);
        List<String> failures = new ArrayList<>();
        int count = 0;
        while (m.find()) {
            count++;
            DataResult<WearRule> r = WearRule.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(m.group(1)));
            r.error().ifPresent(e -> failures.add(e.message() + "\n" + m.group(1)));
        }
        assertTrue(count >= 6, "expected at least 6 JSON examples, found " + count);
        assertTrue(failures.isEmpty(), String.join("\n---\n", failures));
    }
}
