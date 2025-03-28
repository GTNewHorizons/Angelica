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

import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.ResourceLocation;

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

	/*
	#if MC_VER < MC_1_20_2
	#if MC_VER < MC_1_19_4
	@Override
	public void renderButton(PoseStack matrices, int mouseX, int mouseY, float delta)
	{
		if (this.renderBackground) // Renders the background of the button
		{
			#if MC_VER < MC_1_17_1
			Minecraft.getInstance().getTextureManager().bind(WIDGETS_LOCATION);
			RenderSystem.color4f(1.0F, 1.0F, 1.0F, this.alpha);
			#else
			RenderSystem.setShader(GameRenderer::getPositionTexShader);
			RenderSystem.setShaderTexture(0, WIDGETS_LOCATION);
			RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, this.alpha);
			#endif

			int i = this.getYImage(this.isHovered);
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.enableDepthTest();
			#if MC_VER < MC_1_19_4
			this.blit(matrices, this.x, this.y, 0, 46 + i * 20, this.width / 2, this.height);
			this.blit(matrices, this.x + this.width / 2, this.y, 200 - this.width / 2, 46 + i * 20, this.width / 2, this.height);
			#else
			this.blit(matrices, this.getX(), this.getY(), 0, 46 + i * 20, this.getWidth() / 2, this.getHeight());
			this.blit(matrices, this.getX() + this.getWidth() / 2, this.getY(), 200 - this.width / 2, 46 + i * 20, this.getWidth() / 2, this.getHeight());
			#endif
		}

		super.renderButton(matrices, mouseX, mouseY, delta);
	}

	#else
    #if MC_VER < MC_1_20_1
	@Override
    public void renderWidget(PoseStack matrices, int mouseX, int mouseY, float delta)
    {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, WIDGETS_LOCATION);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, this.alpha);
    #else
	@Override
	public void renderWidget(GuiGraphics matrices, int mouseX, int mouseY, float delta)
	{
    #endif
		if (this.renderBackground) // Renders the background of the button
		{
			int i = 1;
			if (!this.active)           i = 0;
			else if (this.isHovered)    i = 2;

            #if MC_VER < MC_1_20_1
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();

            this.blit(matrices, this.getX(), this.getY(), 0, 46 + i * 20, this.getWidth() / 2, this.getHeight());
            this.blit(matrices, this.getX() + this.getWidth() / 2, this.getY(), 200 - this.width / 2, 46 + i * 20, this.getWidth() / 2, this.getHeight());
            #else
			matrices.blit(WIDGETS_LOCATION, this.getX(), this.getY(), 0, 46 + i * 20, this.getWidth() / 2, this.getHeight());
			matrices.blit(WIDGETS_LOCATION, this.getX() + this.getWidth() / 2, this.getY(), 200 - this.width / 2, 46 + i * 20, this.getWidth() / 2, this.getHeight());
            #endif
		}

		super.renderWidget(matrices, mouseX, mouseY, delta);
	}
	#endif

	#else
	@Override
	public void renderWidget(GuiGraphics matrices, int mouseX, int mouseY, float delta)
	{
		if (this.renderBackground)
		{
			#if MC_VER < MC_1_21_3
			matrices.blitSprite(SPRITES.get(this.active, this.isHoveredOrFocused()), this.getX(), this.getY(), this.getWidth(), this.getHeight());
			#else
			matrices.blitSprite(
				RenderType::guiTextured,
				SPRITES.get(this.active, this.isHoveredOrFocused()),
				this.getX(), this.getY(),
				this.getWidth(), this.getHeight());

			#endif
		}


		// Renders the sprite
		int i = 0;
		if (!this.active)
		{
			i = 2;
		}
		else if (this.isHovered)
		{
			i = 1;
		}

		#if MC_VER < MC_1_21_3
		matrices.blit(this.textureResourceLocation, this.getX(), this.getY(), this.u, this.v + (this.hoveredVOffset * i), this.width, this.height, this.textureWidth, this.textureHeight);
		#else
		matrices.blit(
				RenderType::guiTextured,
				this.textureResourceLocation,
				this.getX(), this.getY(),
				this.u, this.v + (this.hoveredVOffset * i),
				this.width, this.height,
				this.textureWidth, this.textureHeight);

		#endif
	}
	#endif

	 */
}
