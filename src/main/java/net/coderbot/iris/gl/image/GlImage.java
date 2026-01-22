package net.coderbot.iris.gl.image;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import net.coderbot.iris.gl.texture.InternalTextureFormat;
import net.coderbot.iris.gl.texture.PixelFormat;
import net.coderbot.iris.gl.texture.PixelType;
import net.coderbot.iris.gl.texture.TextureType;
import org.embeddedt.embeddium.impl.gl.GlObject;
import org.embeddedt.embeddium.impl.gl.debug.GLDebug;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

public class GlImage extends GlObject {
	protected final String name;
	protected final String samplerName;
	protected final TextureType target;
	protected final PixelFormat format;
	protected final InternalTextureFormat internalTextureFormat;
	protected final PixelType pixelType;
	private final boolean clear;

	public GlImage(String name, String samplerName, TextureType target, PixelFormat format, InternalTextureFormat internalFormat, PixelType pixelType, boolean clear, int width, int height, int depth) {
		this.setHandle(RenderSystem.createTexture(target.getGlType()));

		this.name = name;
		this.samplerName = samplerName;
		this.target = target;
		this.format = format;
		this.internalTextureFormat = internalFormat;
		this.pixelType = pixelType;
		this.clear = clear;

		GLDebug.nameObject(GL11.GL_TEXTURE, handle(), name);

		GL11.glBindTexture(target.getGlType(), handle());
		target.apply(handle(), width, height, depth, internalFormat.getGlFormat(), format.getGlFormat(), pixelType.getGlFormat(), null);

		int texture = handle();

		setup(texture, width, height, depth);

		GL11.glBindTexture(target.getGlType(), 0);
	}

	protected void setup(int texture, int width, int height, int depth) {
		boolean isInteger = internalTextureFormat.getPixelFormat().isInteger();
		RenderSystem.texParameteri(texture, target.getGlType(), GL11.GL_TEXTURE_MIN_FILTER, isInteger ? GL11.GL_NEAREST : GL11.GL_LINEAR);
		RenderSystem.texParameteri(texture, target.getGlType(), GL11.GL_TEXTURE_MAG_FILTER, isInteger ? GL11.GL_NEAREST : GL11.GL_LINEAR);
		RenderSystem.texParameteri(texture, target.getGlType(), GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);

		if (height > 0) {
			RenderSystem.texParameteri(texture, target.getGlType(), GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		}

		if (depth > 0) {
			RenderSystem.texParameteri(texture, target.getGlType(), GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
		}

		RenderSystem.texParameteri(texture, target.getGlType(), GL12.GL_TEXTURE_MAX_LEVEL, 0);
		RenderSystem.texParameteri(texture, target.getGlType(), GL12.GL_TEXTURE_MIN_LOD, 0);
		RenderSystem.texParameteri(texture, target.getGlType(), GL12.GL_TEXTURE_MAX_LOD, 0);
		RenderSystem.texParameterf(texture, target.getGlType(), GL14.GL_TEXTURE_LOD_BIAS, 0.0F);

		RenderSystem.clearTexImage(texture, target.getGlType(), 0, format.getGlFormat(), pixelType.getGlFormat());
	}

	public String getName() {
		return name;
	}

	public String getSamplerName() {
		return samplerName;
	}

	public TextureType getTarget() {
		return target;
	}

	public boolean shouldClear() {
		return clear;
	}

	public int getId() {
		return handle();
	}

	/**
	 * Clears the texture to zero. Called each frame for images that need clearing.
	 */
	public void clear() {
		RenderSystem.clearTexImage(handle(), target.getGlType(), 0, format.getGlFormat(), pixelType.getGlFormat());
	}

	/**
	 * This makes the image aware of a new render target. Depending on the image's properties, it may not follow these targets.
	 *
	 * @param width  The width of the main render target.
	 * @param height The height of the main render target.
	 */
	public void updateNewSize(int width, int height) {

	}

	@Override
	protected void destroyInternal() {
		GLStateManager.glDeleteTextures(handle());
	}

	public InternalTextureFormat getInternalFormat() {
		return internalTextureFormat;
	}

	@Override
	public String toString() {
		return "GlImage name " + name + " format " + format + "internalformat " + internalTextureFormat + " pixeltype " + pixelType;
	}

	public PixelFormat getFormat() {
		return format;
	}

	public PixelType getPixelType() {
		return pixelType;
	}

	public static class Relative extends GlImage {

		private final float relativeHeight;
		private final float relativeWidth;

		public Relative(String name, String samplerName, PixelFormat format, InternalTextureFormat internalFormat, PixelType pixelType, boolean clear, float relativeWidth, float relativeHeight, int currentWidth, int currentHeight) {
			super(name, samplerName, TextureType.TEXTURE_2D, format, internalFormat, pixelType, clear, (int) (currentWidth * relativeWidth), (int) (currentHeight * relativeHeight), 0);

			this.relativeWidth = relativeWidth;
			this.relativeHeight = relativeHeight;
		}

		@Override
		public void updateNewSize(int width, int height) {
			GL11.glBindTexture(target.getGlType(), handle());
			target.apply(handle(), (int) (width * relativeWidth), (int) (height * relativeHeight), 0, internalTextureFormat.getGlFormat(), format.getGlFormat(), pixelType.getGlFormat(), null);

			int texture = handle();

			setup(texture, width, height, 0);

			GL11.glBindTexture(target.getGlType(), 0);
		}
	}
}
