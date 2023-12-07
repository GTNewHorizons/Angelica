package net.coderbot.iris.pipeline.transform;

import net.coderbot.iris.gl.shader.ShaderType;

public enum PatchShaderType {
	VERTEX(ShaderType.VERTEX),
	GEOMETRY(ShaderType.GEOMETRY),
	FRAGMENT(ShaderType.FRAGMENT);

	public final ShaderType glShaderType;

	private PatchShaderType(ShaderType glShaderType) {
		this.glShaderType = glShaderType;
	}

	public static PatchShaderType[] fromGlShaderType(ShaderType glShaderType) {
        return switch (glShaderType) {
            case VERTEX -> new PatchShaderType[] { VERTEX };
            case GEOMETRY -> new PatchShaderType[] { GEOMETRY };
            case FRAGMENT -> new PatchShaderType[] { FRAGMENT };
            default -> throw new IllegalArgumentException("Unknown shader type: " + glShaderType);
        };
	}
}
