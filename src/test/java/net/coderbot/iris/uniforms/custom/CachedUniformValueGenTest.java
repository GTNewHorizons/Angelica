package net.coderbot.iris.uniforms.custom;

import net.coderbot.iris.gl.uniform.UniformUpdateFrequency;
import net.coderbot.iris.uniforms.custom.cached.Float3VectorCachedUniform;
import net.coderbot.iris.uniforms.custom.cached.FloatCachedUniform;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CachedUniformValueGenTest {

    @Test
    void floatGenAdvancesOnlyOnChange() {
        final float[] value = {5.0f};
        final FloatCachedUniform u = new FloatCachedUniform("test", UniformUpdateFrequency.CUSTOM, () -> value[0]);

        assertEquals(0, u.valueGen());
        u.update();
        assertEquals(1, u.valueGen(), "initial value differs from the 0-default cache");
        u.update();
        u.update();
        assertEquals(1, u.valueGen(), "unchanged value must not advance the generation");
        value[0] = 6.0f;
        u.update();
        assertEquals(2, u.valueGen());
        u.update();
        assertEquals(2, u.valueGen());
    }

    @Test
    void vectorGenAdvancesOnlyOnChange() {
        final Vector3f value = new Vector3f(1, 2, 3);
        final Float3VectorCachedUniform u = new Float3VectorCachedUniform("test", UniformUpdateFrequency.CUSTOM, () -> value);

        u.update();
        final int afterFirst = u.valueGen();
        u.update();
        assertEquals(afterFirst, u.valueGen(), "same vector content must not advance");
        value.set(1, 2, 4);
        u.update();
        assertEquals(afterFirst + 1, u.valueGen());
    }

    @Test
    void perPassGenComparisonSkipsAndCatchesUp() {
        final float[] value = {1.0f};
        final FloatCachedUniform u = new FloatCachedUniform("test", UniformUpdateFrequency.CUSTOM, () -> value[0]);

        int passA = -1;
        int passB = -1;

        u.update();
        assertNotEquals(passA, u.valueGen(), "fresh pass must push even for gen 1");
        passA = u.valueGen();

        u.update();
        assertEquals(passA, u.valueGen(), "unchanged next frame skips for the caught-up pass");
        assertNotEquals(passB, u.valueGen(), "a pass that never pushed still pushes despite no change this frame");
        passB = u.valueGen();

        value[0] = 2.0f;
        u.update();
        assertNotEquals(passA, u.valueGen());
        assertNotEquals(passB, u.valueGen());
    }
}
