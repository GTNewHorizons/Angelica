package com.gtnewhorizons.angelica.client.gui.widgets;

import com.gtnewhorizons.angelica.compat.mojang.Drawable;
import lombok.Setter;
import me.jellysquid.mods.sodium.client.util.Dim2i;

public class FlatButtonWidget extends AbstractWidget implements Drawable {
    private final Dim2i dim;
    private final String label;
    private final Runnable action;

    @Setter private boolean selected;
    @Setter private boolean enabled = true;
    @Setter private boolean visible = true;
    @Setter private boolean leftAligned;

    public FlatButtonWidget(Dim2i dim, String label, Runnable action) {
        this.dim = dim;
        this.label = label;
        this.action = action;
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        if (!this.visible) {
            return;
        }

        final boolean hovered = this.dim.containsCursor(mouseX, mouseY);

        final int backgroundColor = this.enabled ? (hovered ? 0xE0000000 : 0x90000000) : 0x60000000;
        final int textColor = this.enabled ? 0xFFFFFFFF : 0x90FFFFFF;

        final int strWidth = this.font.getStringWidth(this.label);

        this.drawRect(this.dim.getOriginX(), this.dim.getOriginY(), this.dim.getLimitX(), this.dim.getLimitY(), backgroundColor);
        final int x = leftAligned ? this.dim.getOriginX() + 10 : this.dim.getCenterX() - (strWidth / 2);
        this.drawString(this.label, x, this.dim.getCenterY() - 4, textColor);

        if (this.enabled && this.selected) {
            this.drawRect(this.dim.getOriginX(), leftAligned ? this.dim.getOriginY() : (this.dim.getLimitY() - 1),
                this.leftAligned ? (this.dim.getOriginX() + 1) : this.dim.getLimitX(), this.dim.getLimitY(), 0xFF94E4D3);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.enabled || !this.visible) {
            return false;
        }

        if (button == 0 && this.dim.containsCursor(mouseX, mouseY)) {
            this.action.run();
            this.playClickSound();

            return true;
        }

        return false;
    }

}
