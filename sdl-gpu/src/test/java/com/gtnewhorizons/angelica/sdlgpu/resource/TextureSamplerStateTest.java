package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextureSamplerStateTest {

    private static Arguments setter(String name, Predicate<TextureSamplerState> set, Function<TextureSamplerState, Number> field, Number expected) {
        return Arguments.of(name, set, field, expected);
    }

    static Stream<Arguments> setters() {
        return Stream.of(
            setter("seti on an int pname", ss -> ss.seti(GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR), ss -> ss.minFilter, GL11.GL_LINEAR),
            setter("setf on a float pname", ss -> ss.setf(GL14.GL_TEXTURE_LOD_BIAS, 0.5f), ss -> ss.lodBias, 0.5f),
            setter("setf falls through to an int pname", ss -> ss.setf(GL11.GL_TEXTURE_MIN_FILTER, (float) GL11.GL_LINEAR), ss -> ss.minFilter, GL11.GL_LINEAR),
            setter("seti routes LOD to the float field", ss -> ss.seti(GL12.GL_TEXTURE_MIN_LOD, 2), ss -> ss.minLod, 2.0f));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("setters")
    void setReportsChangeOnlyWhenTheValueChanges(String name, Predicate<TextureSamplerState> set, Function<TextureSamplerState, Number> field, Number expected) {
        final TextureSamplerState ss = new TextureSamplerState();
        assertTrue(set.test(ss), "first set changes the value");
        assertEquals(expected, field.apply(ss));
        assertFalse(set.test(ss), "setting the same value again is not a change");
    }

    @Test
    void maxLevelIsStoredButNeverAffectsTheSampler() {
        final TextureSamplerState ss = new TextureSamplerState();
        assertFalse(ss.seti(GL12.GL_TEXTURE_MAX_LEVEL, 3));
        assertEquals(3, ss.maxLevel);
    }
}
