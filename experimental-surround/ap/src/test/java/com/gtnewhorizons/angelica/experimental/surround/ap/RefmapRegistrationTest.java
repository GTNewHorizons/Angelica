package com.gtnewhorizons.angelica.experimental.surround.ap;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RefmapRegistrationTest {

    private static final Pattern ENTRY = Pattern.compile("\"([^\"]+)\": \"([^\"]+)\"");

    @ParameterizedTest
    @ValueSource(strings = {"discovered", "surround-first", "surround-last"})
    void surroundTargetsAreRemappedInTheRefmap(String variant) throws IOException {
        final Map<String, String> expected = new HashMap<>();
        expected.put("run()V", "Lprobe/RefmapTarget;func_1_a()V");
        expected.put("run(Ljava/lang/String;)V", "Lprobe/RefmapTarget;func_2_b(Ljava/lang/String;)V");
        expected.put("Lprobe/RefmapTarget;helper()V", "Lprobe/RefmapTarget;func_3_c()V");
        assertEquals(expected, mappings(variant, "probe/MixinRefmapTarget"));
    }

    private static Map<String, String> mappings(String variant, String mixin) throws IOException {
        final String json = new String(RefmapRegistrationTest.class.getResourceAsStream("/refmap-probe/" + variant + ".json").readAllBytes(), UTF_8);
        final int start = json.indexOf('{', json.indexOf("\"" + mixin + "\"", json.indexOf("\"mappings\"")));
        final Matcher m = ENTRY.matcher(json.substring(start, json.indexOf('}', start)));
        final Map<String, String> entries = new HashMap<>();
        while (m.find()) entries.put(m.group(1), m.group(2));
        return entries;
    }
}
