package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReservedWordRenameTest {

    private static final List<Pattern> LEGACY_ALWAYS = List.of(Pattern.compile("\\bsample\\b"), Pattern.compile("\\bnew\\b"));
    private static final Pattern LEGACY_400 = Pattern.compile("\\bsampler\\b(?!\\d)");
    private static final String[] WORDS = { "sample", "sampler", "new" };

    private static String legacyRename(String source, int targetVersion) {
        String out = source;
        for (Pattern p : LEGACY_ALWAYS) {
            final String word = p.pattern().substring(2, p.pattern().length() - 2);
            out = p.matcher(out).replaceAll("angelica_renamed_" + word);
        }
        if (targetVersion >= 400) out = LEGACY_400.matcher(out).replaceAll("angelica_renamed_sampler");
        return out;
    }

    private static void assertSameAsLegacy(String source) {
        for (int version : new int[] { 120, 330, 400, 460 }) {
            assertEquals(legacyRename(source, version), GlslTransformUtils.renameReservedWords(source, version), "version " + version + " source: " + source);
        }
    }

    @Test
    void edgeCasesMatchLegacy() {
        final String[] cases = {
            "", "sample", "new", "sampler", " sample ", "sample;", "(sample)", "_sample", "sample_", "sample2", "2sample",
            "samples", "resample", "sampler2D", "samplerX", "sampler_", "sampler;", "uniform sampler s;", "sampler2DShadow",
            "new_", "renew", "news", "new;new new", "sample sample sample", "samplesampler", "sampler sample new",
            "ésample", "sampleé", "ßnewß", "中sampler", "sampler中", "samplé",
            "// sample in a comment\nfloat sample = 1.0;\n", "#define new sample\n", "vec4 c = texture(sampler, uv) * sample;",
            "float x = sample.x + new_value + newValue + sample1;", "sample\nnew\nsampler\n", "\tsample\t", "sample-new+sampler*",
        };
        for (String c : cases) assertSameAsLegacy(c);
    }

    @Test
    void randomizedInputsMatchLegacy() {
        final Random random = new Random(0x5A3D1E);
        final String[] fragments = { "sample", "sampler", "new", "s", "a", "_", "1", "2", "D", "x", " ", "\n", ";", "(", ")", ".", "é", "中", "́", "//", "#" };
        for (int i = 0; i < 5000; i++) {
            final StringBuilder sb = new StringBuilder();
            final int n = 1 + random.nextInt(24);
            for (int j = 0; j < n; j++) sb.append(fragments[random.nextInt(fragments.length)]);
            assertSameAsLegacy(sb.toString());
        }
    }

    @Test
    void literalWordsAreTheOnlyCandidates() {
        for (String w : WORDS) {
            assertEquals("angelica_renamed_" + w, GlslTransformUtils.renameReservedWords(w, 460));
        }
    }
}
