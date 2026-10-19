package net.coderbot.iris.uniforms;

public final class CapturedStateScope implements AutoCloseable {

    public CapturedStateScope() {
        final CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        state.pushCurrentEntityAndItem();
        state.pushCurrentBlockEntity();
        state.pushCurrentEntityColor();
    }

    @Override
    public void close() {
        final CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        state.popCurrentEntityColor();
        state.popCurrentBlockEntity();
        state.popCurrentEntityAndItem();
    }
}
