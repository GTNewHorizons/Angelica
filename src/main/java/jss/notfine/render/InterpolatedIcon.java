package jss.notfine.render;

import com.gtnewhorizons.angelica.rendering.celeritas.SpriteExtension;
import com.gtnewhorizons.angelica.utils.SpritePadding;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Originally created by ganymedes01 for Et Futurum.
 * Ported to NotFine and cleaned up by Roadhog360.
 * @author ganymedes01
 * @author Roadhog360
 */
@SideOnly(Side.CLIENT)
public class InterpolatedIcon extends TextureAtlasSprite {

	protected int[][] interpolatedFrameData;

	public InterpolatedIcon(String name) {
		super(name);
	}

	@Override
	public void updateAnimation() {
		super.updateAnimation();
		try {
			updateAnimationInterpolated();
		} catch (Exception e) {
		}
	}

	protected void updateAnimationInterpolated() throws IllegalArgumentException, IllegalAccessException {

        final double dn = tickCounter / (double) animationMetadata.getFrameTimeSingle(frameCounter);
        final double dc = 1.0D - dn;
		final int cFrame = animationMetadata.getFrameIndex(frameCounter);
		final int endFrame = animationMetadata.getFrameCount() == 0 ? framesTextureData.size() : animationMetadata.getFrameCount();
		final int nFrame = animationMetadata.getFrameIndex((frameCounter + 1) % endFrame);

		if (cFrame != nFrame && nFrame >= 0 && nFrame < framesTextureData.size()) {
			final int[][] cARGB = framesTextureData.get(cFrame);
			final int[][] nARGB = framesTextureData.get(nFrame);

			if (interpolatedFrameData == null || interpolatedFrameData.length != cARGB.length)
				interpolatedFrameData = new int[cARGB.length][];

			for (int i = 0; i < cARGB.length; i++) {
				if (interpolatedFrameData[i] == null)
					interpolatedFrameData[i] = new int[cARGB[i].length];

				if (i < nARGB.length && nARGB[i].length == cARGB[i].length)
					for (int j = 0; j < cARGB[i].length; ++j) {
						final int c = cARGB[i][j];
						final int n = nARGB[i][j];

                        final double ca = (c >>> 24) * dc;
                        final int cr = (c >>> 16) & 0xFF;
                        final int cg = (c >>> 8) & 0xFF;
                        final int cb = c & 0xFF;

                        final double na = (n >>> 24) * dn;
                        final int nr = (n >>> 16) & 0xFF;
                        final int ng = (n >>> 8) & 0xFF;
                        final int nb = n & 0xFF;

                        final double ad = ca + na;
                        final double wc = ad == 0 ? dc : ca / ad;
                        final double wn = ad == 0 ? dn : na / ad;

                        final int a = (int) Math.round(ad);
                        final int r = (int) Math.round(cr * wc + nr * wn);
                        final int g = (int) Math.round(cg * wc + ng * wn);
                        final int b = (int) Math.round(cb * wc + nb * wn);
						interpolatedFrameData[i][j] = a << 24 | r << 16 | g << 8 | b;
					}
			}

			SpritePadding.uploadPadded(interpolatedFrameData, width, height, originX, originY,
				((SpriteExtension) this).angelica$getGutterWidth(), false, false);
		}
	}
}
