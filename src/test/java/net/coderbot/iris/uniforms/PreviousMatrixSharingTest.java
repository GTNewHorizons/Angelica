package net.coderbot.iris.uniforms;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PreviousMatrixSharingTest {

    private final Matrix4f source = new Matrix4f();

    @BeforeEach
    void resetFrameCounter() {
        SystemTimeUniforms.COUNTER.reset();
    }

    @Test
    void sameNameSharesOneInstance() {
        final MatrixUniforms.Previous a = MatrixUniforms.Previous.shared("testShared", () -> source);
        final MatrixUniforms.Previous b = MatrixUniforms.Previous.shared("testShared", () -> source);
        assertSame(a, b);
        assertNotSame(a, MatrixUniforms.Previous.shared("testSharedOther", () -> source));
    }

    @Test
    void advancesOncePerFrameAndReturnsLastFrameValue() {
        final MatrixUniforms.Previous prev = new MatrixUniforms.Previous(() -> source);

        source.translation(1f, 0f, 0f);
        assertEquals(new Matrix4f(), new Matrix4f(prev.get()), "first frame previous is identity");

        SystemTimeUniforms.COUNTER.beginFrame();
        source.translation(2f, 0f, 0f);
        final Matrix4fc frame2First = new Matrix4f(prev.get());
        assertEquals(new Matrix4f().translation(1f, 0f, 0f), frame2First, "frame 2 sees frame 1 value");

        source.translation(3f, 0f, 0f);
        assertEquals(frame2First, new Matrix4f(prev.get()));

        SystemTimeUniforms.COUNTER.beginFrame();
        assertEquals(new Matrix4f().translation(2f, 0f, 0f), new Matrix4f(prev.get()), "frame 3 sees the value captured at frame 2's first read, not later mid-frame mutations");
    }
}
