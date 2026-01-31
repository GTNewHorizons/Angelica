package net.coderbot.iris.pipeline.transform;

import net.coderbot.iris.gl.shader.ShaderType;

public enum PatchShaderType {
	VERTEX(ShaderType.VERTEX),
	GEOMETRY(ShaderType.GEOMETRY),
	TESS_CONTROL(ShaderType.TESSELATION_CONTROL),
	TESS_EVAL(ShaderType.TESSELATION_EVAL),
	FRAGMENT(ShaderType.FRAGMENT),
	COMPUTE(ShaderType.COMPUTE);

	public static final PatchShaderType[] VALUES = values();

	public final ShaderType glShaderType;

	private PatchShaderType(ShaderType glShaderType) {
		this.glShaderType = glShaderType;
	}

	public static PatchShaderType[] fromGlShaderType(ShaderType glShaderType) {
        return switch (glShaderType) {
            case VERTEX -> new PatchShaderType[] { VERTEX };
            case GEOMETRY -> new PatchShaderType[] { GEOMETRY };
            case TESSELATION_CONTROL -> new PatchShaderType[] { TESS_CONTROL };
            case TESSELATION_EVAL -> new PatchShaderType[] { TESS_EVAL };
            case FRAGMENT -> new PatchShaderType[] { FRAGMENT };
            case COMPUTE -> new PatchShaderType[] { COMPUTE };
        };
	}
}
