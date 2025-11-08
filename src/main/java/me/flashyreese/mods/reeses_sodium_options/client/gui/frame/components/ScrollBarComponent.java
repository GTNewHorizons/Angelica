package me.flashyreese.mods.reeses_sodium_options.client.gui.frame.components;

import lombok.Getter;
import me.jellysquid.mods.sodium.client.gui.widgets.AbstractWidget;
import me.jellysquid.mods.sodium.client.util.Dim2i;
import net.minecraft.util.MathHelper;

import java.util.function.Consumer;

public class ScrollBarComponent extends AbstractWidget {

    protected static final int SCROLL_OFFSET = 15;

    protected final Dim2i dim;

    private final Mode mode;
    private final int frameLength;
    private final int viewPortLength;
    private final int maxScrollBarOffset;
    private final Consumer<Integer> onSetOffset;
    @Getter
    private int offset = 0;
    private boolean isDragging;

    private Dim2i scrollThumb = null;
    private int scrollThumbClickOffset;

    private Dim2i extendedScrollArea = null;

    public ScrollBarComponent(Dim2i trackArea, Mode mode, int frameLength, int viewPortLength, Consumer<Integer> onSetOffset) {
        this.dim = trackArea;
        this.mode = mode;
        this.frameLength = frameLength;
        this.viewPortLength = viewPortLength;
        this.onSetOffset = onSetOffset;
        this.maxScrollBarOffset = this.frameLength - this.viewPortLength;
    }

    public ScrollBarComponent(Dim2i scrollBarArea, Mode mode, int frameLength, int viewPortLength, Consumer<Integer> onSetOffset, Dim2i extendedTrackArea) {
        this(scrollBarArea, mode, frameLength, viewPortLength, onSetOffset);
        this.extendedScrollArea = extendedTrackArea;
    }

    public void updateThumbPosition() {
        int scrollThumbLength = (this.viewPortLength * (this.mode == Mode.VERTICAL ? this.dim.getHeight() : this.dim.getWidth() - 6)) / this.frameLength;
        int maximumScrollThumbOffset = this.viewPortLength - scrollThumbLength;
        int scrollThumbOffset = this.offset * maximumScrollThumbOffset / this.maxScrollBarOffset;
        this.scrollThumb = new Dim2i(this.dim.getOriginX() + 2 + (this.mode == Mode.HORIZONTAL ? scrollThumbOffset : 0), this.dim.getOriginY() + 2 + (this.mode == Mode.VERTICAL ? scrollThumbOffset : 0), (this.mode == Mode.VERTICAL ? this.dim.getWidth() : scrollThumbLength) - 4, (this.mode == Mode.VERTICAL ? scrollThumbLength : this.dim.getHeight()) - 4);
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        this.drawRectOutline(this.dim.getOriginX(), this.dim.getOriginY(), this.dim.getLimitX(), this.dim.getLimitY(), 0xFFAAAAAA);
        this.drawRect(this.scrollThumb.getOriginX(), this.scrollThumb.getOriginY(), this.scrollThumb.getLimitX(), this.scrollThumb.getLimitY(), 0xFFAAAAAA);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.dim.containsCursor(mouseX, mouseY)) {
            if (this.scrollThumb.containsCursor(mouseX, mouseY)) {
                if (this.mode == Mode.VERTICAL) {
                    this.scrollThumbClickOffset = (int) (mouseY - this.scrollThumb.getCenterY());
                } else {
                    this.scrollThumbClickOffset = (int) (mouseX - this.scrollThumb.getCenterX());
                }
                this.isDragging = true;
            } else {
                int value;
                if (this.mode == Mode.VERTICAL) {
                    value = (int) ((mouseY - this.dim.getOriginY() - (this.scrollThumb.getHeight() / 2)) / (this.dim.getHeight() - this.scrollThumb.getHeight()) * this.maxScrollBarOffset);
                } else {
                    value = (int) ((mouseX - this.dim.getOriginX() - (this.scrollThumb.getWidth() / 2)) / (this.dim.getWidth() - this.scrollThumb.getWidth()) * this.maxScrollBarOffset);
                }
                this.setOffset(value);
                this.isDragging = false;
            }
            return true;
        }
        this.isDragging = false;
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.isDragging = false;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (this.isDragging) {
            final int value;
            if (this.mode == Mode.VERTICAL) {
                value = (int) ((mouseY - this.scrollThumbClickOffset - this.dim.getOriginY() - (this.scrollThumb.getHeight() / 2)) / (this.dim.getHeight() - this.scrollThumb.getHeight()) * this.maxScrollBarOffset);
            } else {
                value = (int) ((mouseX - this.scrollThumbClickOffset - this.dim.getOriginX() - (this.scrollThumb.getWidth() / 2)) / (this.dim.getWidth() - this.scrollThumb.getWidth()) * this.maxScrollBarOffset);
            }
            this.setOffset(value);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (this.dim.containsCursor(mouseX, mouseY) || this.extendedScrollArea != null && this.extendedScrollArea.containsCursor(mouseX, mouseY)) {
            if (this.offset <= this.maxScrollBarOffset && this.offset >= 0) {
                int value = (int) (this.offset - amount * SCROLL_OFFSET);
                this.setOffset(value);
                return true;
            }
        }
        return false;
    }

    public void setOffset(int value) {
        this.offset = MathHelper.clamp_int(value, 0, this.maxScrollBarOffset);
        this.updateThumbPosition();
        this.onSetOffset.accept(this.offset);
    }


    protected void drawRectOutline(double x, double y, double w, double h, int color) {
        final float a = (float) (color >> 24 & 255) / 255.0F;
        final float r = (float) (color >> 16 & 255) / 255.0F;
        final float g = (float) (color >> 8 & 255) / 255.0F;
        final float b = (float) (color & 255) / 255.0F;

        this.drawQuads(vertices -> {
            addQuad(vertices, x, y, w, y + 1, a, r, g, b);
            addQuad(vertices, x, h - 1, w, h, a, r, g, b);
            addQuad(vertices, x, y, x + 1, h, a, r, g, b);
            addQuad(vertices, w - 1, y, w, h, a, r, g, b);
        });
    }

    public enum Mode {
        HORIZONTAL,
        VERTICAL
    }
}
