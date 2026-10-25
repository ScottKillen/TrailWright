/*
 * Purpose:      Checks the lang files under assets/trailwright/lang (DESIGN.md §11): every
 *               other language uses only en_us keys with the same arguments, and en_us names
 *               and explains every config setting.
 * Dependencies: JUnit 5, Gson (Minecraft classpath), TrailWrightConfig.
 */
package us.someguy.trailwright;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import us.someguy.trailwright.config.TrailWrightConfig;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class LangFilesTest {
    private static final Path LANG = Path.of("src/main/resources/assets/trailwright/lang");
    // The argument forms Minecraft's translation formatter accepts: %s, %n$s, and %% (literal).
    private static final Pattern ARG = Pattern.compile("%(?:(\\d+)\\$)?([s%])");

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    /** 1-based argument indices a value uses. A translation may reorder them with %n$s. */
    private static Set<Integer> args(String value) {
        Set<Integer> used = new TreeSet<>();
        Matcher m = ARG.matcher(value);
        int next = 1;
        while (m.find()) {
            if (m.group(2).equals("%")) continue;
            used.add(m.group(1) != null ? Integer.parseInt(m.group(1)) : next++);
        }
        return used;
    }

    @Test void otherLanguagesMatchEnUs() throws IOException {
        JsonObject en = read(LANG.resolve("en_us.json"));
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.list(LANG)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                String name = file.getFileName().toString();
                assertEquals(name.toLowerCase(), name, "lang file names are lower case");
                if (name.equals("en_us.json")) continue;
                JsonObject other = read(file);
                for (String key : other.keySet()) {
                    if (!en.has(key)) {
                        problems.add(name + ": " + key + " is not in en_us.json");
                    } else if (!args(other.get(key).getAsString()).equals(args(en.get(key).getAsString()))) {
                        problems.add(name + ": " + key + " uses different arguments than en_us.json");
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test void enUsCoversEveryConfigSetting() throws IOException {
        JsonObject en = read(LANG.resolve("en_us.json"));
        List<String> missing = new ArrayList<>();
        for (Field f : TrailWrightConfig.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            for (String key : List.of("trailwright.config." + f.getName(), "trailwright.config." + f.getName() + ".tooltip")) {
                if (!en.has(key)) missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), "missing from en_us.json: " + missing);
    }

    @Test void argumentParsing() {
        assertEquals(Set.of(1, 2), args("wear: %s / %s"));
        assertEquals(Set.of(1, 2), args("%2$s sur %1$s"));
        assertEquals(Set.of(), args("100%% done"));
    }
}
