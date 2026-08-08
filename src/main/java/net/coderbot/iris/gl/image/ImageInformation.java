package net.coderbot.iris.gl.image;

import com.gtnewhorizons.angelica.glsm.texture.InternalTextureFormat;
import com.gtnewhorizons.angelica.glsm.texture.PixelFormat;
import com.gtnewhorizons.angelica.glsm.texture.PixelType;
import com.gtnewhorizons.angelica.glsm.texture.TextureType;

public record ImageInformation(String name, String samplerName, TextureType target, PixelFormat format,
							   InternalTextureFormat internalTextureFormat,
							   PixelType type, int width, int height, int depth, boolean clear, boolean isRelative,
							   float relativeWidth, float relativeHeight) {
}
