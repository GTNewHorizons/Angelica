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

package com.seibel.distanthorizons.common.wrappers.block;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

#if MC_VER < MC_1_17_1
#elif MC_VER < MC_1_21_3
#else
import com.seibel.distanthorizons.core.util.ColorUtil;
import net.minecraft.client.renderer.texture.SpriteContents;
#endif

/**
 * For wrapping/utilizing around TextureAtlasSprite
 *
 * @author Ran
 */
public class TextureAtlasSpriteWrapper
{
	public static int getPixelRGBA(TextureAtlasSprite sprite, int frameIndex, int x, int y)
	{
        #if MC_VER < MC_1_17_1
        return sprite.mainImage[0].getPixelRGBA(
                x + sprite.framesX[frameIndex] * sprite.getWidth(),
                y + sprite.framesY[frameIndex] * sprite.getHeight());
        #elif MC_VER < MC_1_19_4
		if (sprite.animatedTexture != null)
		{
			x += sprite.animatedTexture.getFrameX(frameIndex) * sprite.width;
			y += sprite.animatedTexture.getFrameY(frameIndex) * sprite.height;
		}
		return sprite.mainImage[0].getPixelRGBA(x, y);
		#elif MC_VER < MC_1_21_3
		if (sprite.contents().animatedTexture != null)
		{
			x += sprite.contents().animatedTexture.getFrameX(frameIndex) * sprite.contents().width();
			y += sprite.contents().animatedTexture.getFrameY(frameIndex) * sprite.contents().width();
		}
		return sprite.contents().originalImage.getPixelRGBA(x, y);
        #else
		
		SpriteContents content = sprite.contents(); // don't close, otherwise MC will be corrupted and you won't be able to re-access the texture
		if (content.animatedTexture != null)
		{
			x += content.animatedTexture.getFrameX(frameIndex) * content.width();
			y += content.animatedTexture.getFrameY(frameIndex) * content.width();
		}
		
		int abgr = content.originalImage.getPixel(x, y);
		// re-pack the color so we can access it normally
		int a = (abgr & 0xFF000000) >>> 24;
		int b = (abgr & 0x00FF0000) >>> 16;
		int g = (abgr & 0x0000FF00) >>> 8;
		int r = (abgr & 0x000000FF);
		return ColorUtil.argbToInt(a, r, g, b);
        #endif
		
	}
	
}
