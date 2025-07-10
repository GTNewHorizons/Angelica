/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.common.wrappers.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Creates a button with a texture on it (and a background) that works with all mc versions
 *
 * @author coolGi
 * @version 2023-10-03
 */
public class TexturedButtonWidget extends GuiButton {
    public final boolean renderBackground;

    private final int u;
    private final int v;
    private final int hoveredVOffset;

    private final ResourceLocation textureResourceLocation;

    private final int textureWidth;
    private final int textureHeight;


    public TexturedButtonWidget(int x, int y, int width, int height, int u, int v, int hoveredVOffset, ResourceLocation textureResourceLocation, int textureWidth, int textureHeight, int id, String text) {
        this(x, y, width, height, u, v, hoveredVOffset, textureResourceLocation, textureWidth, textureHeight, id, text, true);
    }

    public TexturedButtonWidget(int x, int y, int width, int height, int u, int v, int hoveredVOffset, ResourceLocation textureResourceLocation, int textureWidth, int textureHeight, int id, String text, boolean renderBackground) {
        super(id, x, y, width, height, text);

        this.u = u;
        this.v = v;
        this.hoveredVOffset = hoveredVOffset;

        this.textureResourceLocation = textureResourceLocation;

        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;

        this.renderBackground = renderBackground;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY)
    {
        this.field_146123_n = mouseX >= this.xPosition && mouseY >= this.yPosition && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;

        if (this.renderBackground)
        {
            int k = this.getHoverState(this.field_146123_n);
            mc.getTextureManager().bindTexture(buttonTextures);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(770, 771, 1, 0);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            this.drawTexturedModalRect(this.xPosition, this.yPosition, 0, 46 + k * 20, this.width / 2, this.height);
            this.drawTexturedModalRect(this.xPosition + this.width / 2, this.yPosition, 200 - this.width / 2, 46 + k * 20, this.width / 2, this.height);
        }

        int k = getIconHoverState(this.field_146123_n);
        mc.getTextureManager().bindTexture(textureResourceLocation);
        if (!this.renderBackground) {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(770, 771, 1, 0);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
        this.drawTexturedModalRect(this.xPosition, this.yPosition, this.u, this.v + (hoveredVOffset * k), this.textureWidth, this.textureHeight);
    }

    public int getIconHoverState(boolean mouseOver) {
        if (!this.enabled || mouseOver) // grey out if mouse over/disabled
            return 1;
        return 0; // regular button colors
    }
}
