package me.flashyreese.mods.reeses_sodium_options.client.gui.frame;

import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.components.ScrollBarComponent;
import me.jellysquid.mods.sodium.client.util.Dim2i;

import java.util.concurrent.atomic.AtomicReference;

public class ScrollableFrame extends AbstractFrame {

    protected final Dim2i frameOrigin;
    protected final AbstractFrame frame;

    private boolean canScrollHorizontal;
    private boolean canScrollVertical;
    private Dim2i viewPortDimension = null;
    private ScrollBarComponent verticalScrollBar = null;
    private ScrollBarComponent horizontalScrollBar = null;

    public ScrollableFrame(Dim2i dim, AbstractFrame frame, boolean renderOutline, AtomicReference<Integer> verticalScrollBarOffset, AtomicReference<Integer> horizontalScrollBarOffset) {
        super(dim, renderOutline);
        this.frame = frame;
        this.frameOrigin = new Dim2i(frame.dim.getOriginX(), frame.dim.getOriginY(), 0, 0);
        this.setupFrame(verticalScrollBarOffset, horizontalScrollBarOffset);
        this.buildFrame();
    }

    public static Builder createBuilder() {
        return new Builder();
    }

    public void setupFrame(AtomicReference<Integer> verticalScrollBarOffset, AtomicReference<Integer> horizontalScrollBarOffset) {
        int maxWidth = 0;
        int maxHeight = 0;
        if (!this.dim.canFitDimension(this.frame.dim)) {
            if (this.dim.getLimitX() < this.frame.dim.getLimitX()) {
                int value = this.frame.dim.getOriginX() - this.dim.getOriginX() + this.frame.dim.getWidth();
                if (maxWidth < value) {
                    maxWidth = value;
                }
            }
            if (this.dim.getLimitY() < this.frame.dim.getLimitY()) {
                int value = this.frame.dim.getOriginY() - this.dim.getOriginY() + this.frame.dim.getHeight();
                if (maxHeight < value) {
                    maxHeight = value;
                }
            }
        }

        if (maxWidth > 0) {
            this.canScrollHorizontal = true;
        }
        if (maxHeight > 0) {
            this.canScrollVertical = true;
        }

        if (this.canScrollHorizontal && this.canScrollVertical) {
            this.viewPortDimension = new Dim2i(this.dim.getOriginX(), this.dim.getOriginY(), this.dim.getWidth() - 11, this.dim.getHeight() - 11);
        } else if (this.canScrollHorizontal) {
            this.viewPortDimension = new Dim2i(this.dim.getOriginX(), this.dim.getOriginY(), this.dim.getWidth(), this.dim.getHeight() - 11);
            this.frame.dim.setHeight(this.frame.dim.getHeight() - 11); // fixme: don't mutate rather
        } else if (this.canScrollVertical) {
            this.viewPortDimension = new Dim2i(this.dim.getOriginX(), this.dim.getOriginY(), this.dim.getWidth() - 11, this.dim.getHeight());
            this.frame.dim.setWidth(this.frame.dim.getWidth() - 11); // fixme: don't mutate rather
        }

        if (this.canScrollHorizontal) {
            this.horizontalScrollBar = new ScrollBarComponent(new Dim2i(this.viewPortDimension.getOriginX(), this.viewPortDimension.getLimitY() + 1, this.viewPortDimension.getWidth(), 10), ScrollBarComponent.Mode.HORIZONTAL, this.frame.dim.getWidth(), this.viewPortDimension.getWidth(), offset -> {
                this.buildFrame();
                horizontalScrollBarOffset.set(offset);
            });
            this.horizontalScrollBar.setOffset(horizontalScrollBarOffset.get());
        }
        if (this.canScrollVertical) {
            this.verticalScrollBar = new ScrollBarComponent(new Dim2i(this.viewPortDimension.getLimitX() + 1, this.viewPortDimension.getOriginY(), 10, this.viewPortDimension.getHeight()), ScrollBarComponent.Mode.VERTICAL, this.frame.dim.getHeight(), this.viewPortDimension.getHeight(), offset -> {
                this.buildFrame();
                verticalScrollBarOffset.set(offset);
            }, this.viewPortDimension);
            this.verticalScrollBar.setOffset(verticalScrollBarOffset.get());
        }
    }

    @Override
    public void buildFrame() {
        this.children.clear();
        this.drawable.clear();
        this.controlElements.clear();

        if (this.canScrollHorizontal) {
            this.horizontalScrollBar.updateThumbPosition();
        }

        if (this.canScrollVertical) {
            this.verticalScrollBar.updateThumbPosition();
        }

        if (this.canScrollHorizontal) {
            this.frame.dim.setX(this.frameOrigin.getOriginX() - this.horizontalScrollBar.getOffset());
        }

        if (this.canScrollVertical) {
            this.frame.dim.setY(this.frameOrigin.getOriginY() - this.verticalScrollBar.getOffset());
        }

        this.frame.buildFrame();
        this.children.add(this.frame);
        super.buildFrame();
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        if (this.canScrollHorizontal || this.canScrollVertical) {
            if (this.renderOutline) {
                this.drawRectOutline(this.dim.getOriginX(), this.dim.getOriginY(), this.dim.getLimitX(), this.dim.getLimitY(), 0xFFAAAAAA);
            }
            this.applyScissor(this.viewPortDimension.getOriginX(), this.viewPortDimension.getOriginY(), this.viewPortDimension.getWidth(), this.viewPortDimension.getHeight(), () -> super.render(mouseX, mouseY, delta));
        } else {
            super.render(mouseX, mouseY, delta);
        }

        if (this.canScrollHorizontal) {
            this.horizontalScrollBar.render(mouseX, mouseY, delta);
        }

        if (this.canScrollVertical) {
            this.verticalScrollBar.render(mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button) || (this.canScrollHorizontal && this.horizontalScrollBar.mouseClicked(mouseX, mouseY, button)) || (this.canScrollVertical && this.verticalScrollBar.mouseClicked(mouseX, mouseY, button));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        return super.mouseDragged(mouseX, mouseY, button) || (this.canScrollHorizontal && this.horizontalScrollBar.mouseDragged(mouseX, mouseY, button)) || (this.canScrollVertical && this.verticalScrollBar.mouseDragged(mouseX, mouseY, button));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY, button) || (this.canScrollHorizontal && this.horizontalScrollBar.mouseReleased(mouseX, mouseY, button)) || (this.canScrollVertical && this.verticalScrollBar.mouseReleased(mouseX, mouseY, button));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return super.mouseScrolled(mouseX, mouseY, amount) || (this.canScrollHorizontal && this.horizontalScrollBar.mouseScrolled(mouseX, mouseY, amount)) || (this.canScrollVertical && this.verticalScrollBar.mouseScrolled(mouseX, mouseY, amount));
    }

    public static class Builder {
        private boolean renderOutline = false;
        private Dim2i dim = null;
        private AbstractFrame frame = null;
        private AtomicReference<Integer> verticalScrollBarOffset = new AtomicReference<>(0);
        private AtomicReference<Integer> horizontalScrollBarOffset = new AtomicReference<>(0);

        public Builder setDimension(Dim2i dim) {
            this.dim = dim;
            return this;
        }

        public Builder shouldRenderOutline(boolean state) {
            this.renderOutline = state;
            return this;
        }

        public Builder setVerticalScrollBarOffset(AtomicReference<Integer> verticalScrollBarOffset) {
            this.verticalScrollBarOffset = verticalScrollBarOffset;
            return this;
        }

        public Builder setHorizontalScrollBarOffset(AtomicReference<Integer> horizontalScrollBarOffset) {
            this.horizontalScrollBarOffset = horizontalScrollBarOffset;
            return this;
        }

        public Builder setFrame(AbstractFrame frame) {
            this.frame = frame;
            return this;
        }

        public ScrollableFrame build() {
            return new ScrollableFrame(this.dim, this.frame, this.renderOutline, this.verticalScrollBarOffset, this.horizontalScrollBarOffset);
        }
    }
}
