package net.coderbot.iris.texture.pbr;

import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;

public enum PBRType {
	NORMAL("_n", 0x7F7FFFFF),
	SPECULAR("_s", 0x00000000);

	private static final PBRType[] VALUES = values();

	private final String suffix;
	private final int defaultValue;

	PBRType(String suffix, int defaultValue) {
		this.suffix = suffix;
		this.defaultValue = defaultValue;
	}

	public String getSuffix() {
		return suffix;
	}

	public int getDefaultValue() {
		return defaultValue;
	}

    public ResourceLocation appendToFileLocation(ResourceLocation location) {
        String domain = location.getResourceDomain();
        String path = location.getResourcePath();
        String newPath;

        try {
            URI uri = new URI(null, null, path, null);
            String cleanPath = uri.getPath();

            int extensionIndex = FilenameUtils.indexOfExtension(cleanPath);
            if (extensionIndex != -1) {
                newPath = cleanPath.substring(0, extensionIndex) + suffix + cleanPath.substring(extensionIndex);
            } else {
                newPath = cleanPath + suffix;
            }

            URI newUri = new URI(null, null, newPath, null);
            newPath = newUri.getPath();

        } catch (URISyntaxException e) {
            String fallback = path.replace(':', '/');
            int extensionIndex = FilenameUtils.indexOfExtension(fallback);
            if (extensionIndex != -1) {
                newPath = fallback.substring(0, extensionIndex) + suffix + fallback.substring(extensionIndex);
            } else {
                newPath = fallback + suffix;
            }
        }

        return new ResourceLocation(domain, newPath);
    }

	/**
	 * Returns the PBR type corresponding to the suffix of the given file location.
	 *
	 * @param location The file location without an extension
	 * @return the PBR type
	 */
	@Nullable
	public static PBRType fromFileLocation(String location) {
		for (PBRType type : VALUES) {
			if (location.endsWith(type.getSuffix())) {
				return type;
			}
		}
		return null;
	}
}
