package net.coderbot.iris.texture.pbr;

import net.coderbot.iris.Iris;
import net.minecraft.util.ResourceLocation;
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

        try {
            URI uri = new URI(null, null, path, null);
            String cleanPath = uri.getPath();
            String newPath;

            int extensionIndex = safeIndexOfExtension(cleanPath);
            if (extensionIndex != -1) {
                newPath = cleanPath.substring(0, extensionIndex) + suffix + cleanPath.substring(extensionIndex);
            } else {
                newPath = cleanPath + suffix;
            }

            URI newUri = new URI(null, null, newPath, null);
            newPath = newUri.getPath();

            return new ResourceLocation(domain, newPath);
        } catch (URISyntaxException e) {
            Iris.logger.error("Failed to append PBR suffix to resource location for " + path, e);
            return location;
        }
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

    // Helper method to safely find the index of the file extension
    // Using this to avoid issues with ':' in paths (any block with meta).
    private static int safeIndexOfExtension(String input) {
        if (input == null) return -1;

        // Avoid dots in directory names or leading/trailing dots
        int lastSlash = Math.max(input.lastIndexOf('/'), input.lastIndexOf('\\'));
        int nameStart = lastSlash + 1;
        int lastDot = input.lastIndexOf('.');
        if (lastDot == -1 || lastDot <= nameStart || lastDot == input.length() - 1) {
            return -1;
        }
        return lastDot;
    }
}
