package com.gtnewhorizons.angelica.rendering.celeritas;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

class TerrainFragmentShaderDriftTest {

    private static final String UPSTREAM_FSH = "/assets/sodium/shaders/blocks/block_layer_opaque.fsh";
    private static final String ANGELICA_FSH = "/assets/angelica/shaders/blocks/block_layer_opaque.fsh";

    private static final String REPLACED_SAMPLE = "vec4 diffuseColor = texture(u_BlockTex, v_TexCoord, v_MaterialMipBias);";

    private static String resource(String path) throws IOException {
        try (InputStream in = TerrainFragmentShaderDriftTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Statements with whitespace collapsed, so line wrapping and indentation don't count as drift. */
    private static List<String> normalize(String src) {
        final List<String> out = new ArrayList<>();
        final StringBuilder statement = new StringBuilder();
        for (String line : src.split("\\R")) {
            final int comment = line.indexOf("//");
            if (comment >= 0) line = line.substring(0, comment);
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                out.add(line.replaceAll("\\s+", " "));
                continue;
            }
            for (char c : line.toCharArray()) {
                statement.append(c);
                if (c == ';' || c == '{' || c == '}') {
                    out.add(statement.toString().replaceAll("\\s+", " ").trim());
                    statement.setLength(0);
                }
            }
            statement.append(' ');
        }
        if (!statement.toString().isBlank()) out.add(statement.toString().replaceAll("\\s+", " ").trim());
        return out;
    }

    @Test
    void angelicaFragmentShaderContainsUpstream() throws IOException {
        final List<String> upstream = normalize(resource(UPSTREAM_FSH));
        final List<String> angelica = normalize(resource(ANGELICA_FSH));

        assertEquals(1, upstream.stream().filter(REPLACED_SAMPLE::equals).count(), "upstream diffuse sample line changed; update REPLACED_SAMPLE and Angelica's sampler selection");
        upstream.remove(REPLACED_SAMPLE);

        int a = 0;
        for (int u = 0; u < upstream.size(); u++) {
            final String want = upstream.get(u);
            while (a < angelica.size() && !angelica.get(a).equals(want)) a++;
            if (a == angelica.size()) {
                fail("Angelica " + ANGELICA_FSH + " is missing upstream statement " + (u + 1) + " in order: " + want);
            }
            a++;
        }
    }
}
