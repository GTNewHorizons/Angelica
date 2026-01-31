/*
 * Copyright LWJGL. All rights reserved. Modified by IMS for use in Iris (net.coderbot.iris.gl).
 * License terms: https://www.lwjgl.org/license
 */

package com.gtnewhorizons.angelica.glsm;

import org.lwjgl.opengl.AMDDebugOutput;
import org.lwjgl.opengl.AMDDebugOutputCallback;
import org.lwjgl.opengl.ARBDebugOutput;
import org.lwjgl.opengl.ARBDebugOutputCallback;
import org.lwjgl.opengl.EXTBlendColor;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.KHRDebug;
import org.lwjgl.opengl.KHRDebugCallback;

import static com.gtnewhorizons.angelica.loading.AngelicaTweaker.LOGGER;
import static org.lwjgl.opengl.ARBDebugOutput.glDebugMessageCallbackARB;

public final class GLDebug {
    /**
     * Sets up debug callbacks
     * @return 0 for failure, 1 for success, 2 for restart required.
     */
    public static int setupDebugMessageCallback() {
        if (Thread.currentThread() != GLStateManager.getMainThread()) {
            LOGGER.warn("setupDebugMessageCallback called from non-main thread!");
            return 0;
        }
        return setupDebugMessageCallbackImpl();
    }

	public static Throwable filterStackTrace(Throwable throwable, int offset) {
		StackTraceElement[] elems = throwable.getStackTrace();
		StackTraceElement[] filtered = new StackTraceElement[elems.length];
		int j = 0;
		for (int i = offset; i < elems.length; i++) {
			String className = elems[i].getClassName();
			if (className == null) {
				className = "";
			}
			filtered[j++] = elems[i];
		}
		StackTraceElement[] newElems = new StackTraceElement[j];
		System.arraycopy(filtered, 0, newElems, 0, j);
		throwable.setStackTrace(newElems);
		return throwable;
	}

	private static String buildStackTrace() {
		StackTraceElement[] elems = filterStackTrace(new Throwable(), 4).getStackTrace();
		StringBuilder sb = new StringBuilder();
		for (StackTraceElement elem : elems) {
			sb.append("\n\t").append(elem.toString());
		}
		return sb.toString();
	}

	private static void logDebugMessage(int id, String source, String type, String severity, String message) {
		String fullMessage = String.format("[GL] %s %s (0x%X) from %s: %s%s", severity, type, id, source, message, buildStackTrace());

		if ("HIGH".equals(severity)) {
			LOGGER.error(fullMessage);
		} else if ("MEDIUM".equals(severity)) {
			LOGGER.warn(fullMessage);
		} else {
			LOGGER.info(fullMessage);
		}
	}

	private static void logDebugMessageAMD(int id, String category, String severity, String message) {
		String fullMessage = String.format("[GL] %s %s (0x%X): %s%s", severity, category, id, message, buildStackTrace());

		if ("HIGH".equals(severity)) {
			LOGGER.error(fullMessage);
		} else if ("MEDIUM".equals(severity)) {
			LOGGER.warn(fullMessage);
		} else {
			LOGGER.info(fullMessage);
		}
	}

    /**
     * Sets up debug callbacks
     * @return 0 for failure, 1 for success, 2 for restart required.
     */
    private static int setupDebugMessageCallbackImpl() {
        if (GLStateManager.capabilities.OpenGL43 || GLStateManager.capabilities.GL_KHR_debug) {
            LOGGER.info("[GL] Using OpenGL 4.3 for error logging.");
            final KHRDebugCallback proc = new KHRDebugCallback((source, type, id, severity, message) -> {
                logDebugMessage(id, getDebugSource(source), getDebugType(type), getDebugSeverity(severity), message);
            });
            GL43.glDebugMessageControl(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_HIGH, null, true);
            GL43.glDebugMessageControl(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_MEDIUM, null, false);
            GL43.glDebugMessageControl(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_LOW, null, false);
            GL43.glDebugMessageControl(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_NOTIFICATION, null, false);
            GL43.glDebugMessageCallback(proc);

            // Enable synchronous debug output so errors are reported immediately with accurate stack traces
            GL11.glEnable(GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS);

            if ((GL11.glGetInteger(GL30.GL_CONTEXT_FLAGS) & GL43.GL_CONTEXT_FLAG_DEBUG_BIT) == 0) {
                LOGGER.warn("[GL] Warning: A non-debug context may not produce any debug output.");
                GL11.glEnable(GL43.GL_DEBUG_OUTPUT);
                return 2;
            }
            return 1;
        } else if (GLStateManager.capabilities.GL_ARB_debug_output) {
            LOGGER.info("[GL] Using ARB_debug_output for error logging.");
            final ARBDebugOutputCallback proc = new ARBDebugOutputCallback((source, type, id, severity, message) -> {
                logDebugMessage(id, getSourceARB(source), getTypeARB(type), getSeverityARB(severity), message);
            });
            ARBDebugOutput.glDebugMessageControlARB(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_HIGH, null, true);
            ARBDebugOutput.glDebugMessageControlARB(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_MEDIUM, null, false);
            ARBDebugOutput.glDebugMessageControlARB(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_LOW, null, false);
            ARBDebugOutput.glDebugMessageControlARB(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_NOTIFICATION, null, false);
            ARBDebugOutput.glDebugMessageCallbackARB(proc);

            // Enable synchronous debug output so errors are reported immediately with accurate stack traces
            GL11.glEnable(ARBDebugOutput.GL_DEBUG_OUTPUT_SYNCHRONOUS_ARB);
            return 1;
        } else if (GLStateManager.capabilities.GL_AMD_debug_output) {
            LOGGER.info("[GL] Using AMD_debug_output for error logging.");
            final AMDDebugOutputCallback proc = new AMDDebugOutputCallback((id, category, severity, message) -> {
                logDebugMessageAMD(id, getCategoryAMD(category), getSeverityAMD(severity), message);
            });
            AMDDebugOutput.glDebugMessageEnableAMD(0, GL43.GL_DEBUG_SEVERITY_HIGH, null, true);
            AMDDebugOutput.glDebugMessageEnableAMD(0, GL43.GL_DEBUG_SEVERITY_MEDIUM, null, false);
            AMDDebugOutput.glDebugMessageEnableAMD(0, GL43.GL_DEBUG_SEVERITY_LOW, null, false);
            AMDDebugOutput.glDebugMessageEnableAMD(0, GL43.GL_DEBUG_SEVERITY_NOTIFICATION, null, false);
            AMDDebugOutput.glDebugMessageCallbackAMD(proc);
            return 1;
        } else {
            LOGGER.info("[GL] No debug output implementation is available, cannot return debug info.");
            return 0;
        }
    }

	public static int disableDebugMessages() {
		if (GLStateManager.capabilities.OpenGL43) {
			GL43.glDebugMessageCallback(null);
			return 1;
		} else if (GLStateManager.capabilities.GL_KHR_debug) {
			KHRDebug.glDebugMessageCallback(null);
			if (GLStateManager.capabilities.OpenGL30 && (GL11.glGetInteger(GL30.GL_CONTEXT_FLAGS) & 2) == 0) {
                GL11.glDisable(GL43.GL_DEBUG_OUTPUT);
			}
			return 1;
		} else if (GLStateManager.capabilities.GL_ARB_debug_output) {
			glDebugMessageCallbackARB(null);
			return 1;
		} else if (GLStateManager.capabilities.GL_AMD_debug_output) {
			AMDDebugOutput.glDebugMessageCallbackAMD(null);
			return 1;
		} else {
			LOGGER.info("[GL] No debug output implementation is available, cannot disable debug info.");
			return 0;
		}
	}


	private static String getDebugSource(int source) {
        return switch (source) {
            case GL43.GL_DEBUG_SOURCE_API -> "API";
            case GL43.GL_DEBUG_SOURCE_WINDOW_SYSTEM -> "WINDOW SYSTEM";
            case GL43.GL_DEBUG_SOURCE_SHADER_COMPILER -> "SHADER COMPILER";
            case GL43.GL_DEBUG_SOURCE_THIRD_PARTY -> "THIRD PARTY";
            case GL43.GL_DEBUG_SOURCE_APPLICATION -> "APPLICATION";
            case GL43.GL_DEBUG_SOURCE_OTHER -> "OTHER";
            default -> String.format("Unknown [0x%X]", source);
        };
	}

	private static String getDebugType(int type) {
        return switch (type) {
            case GL43.GL_DEBUG_TYPE_ERROR -> "ERROR";
            case GL43.GL_DEBUG_TYPE_DEPRECATED_BEHAVIOR -> "DEPRECATED BEHAVIOR";
            case GL43.GL_DEBUG_TYPE_UNDEFINED_BEHAVIOR -> "UNDEFINED BEHAVIOR";
            case GL43.GL_DEBUG_TYPE_PORTABILITY -> "PORTABILITY";
            case GL43.GL_DEBUG_TYPE_PERFORMANCE -> "PERFORMANCE";
            case GL43.GL_DEBUG_TYPE_OTHER -> "OTHER";
            case GL43.GL_DEBUG_TYPE_MARKER -> "MARKER";
            default -> String.format("Unknown [0x%X]", type);
        };
	}

	private static String getDebugSeverity(int severity) {
        return switch (severity) {
            case GL43.GL_DEBUG_SEVERITY_NOTIFICATION -> "NOTIFICATION";
            case GL43.GL_DEBUG_SEVERITY_HIGH -> "HIGH";
            case GL43.GL_DEBUG_SEVERITY_MEDIUM -> "MEDIUM";
            case GL43.GL_DEBUG_SEVERITY_LOW -> "LOW";
            default -> String.format("Unknown [0x%X]", severity);
        };
	}

    private static String getSourceARB(int source) {
        return switch (source) {
            case ARBDebugOutput.GL_DEBUG_SOURCE_API_ARB -> "API";
            case ARBDebugOutput.GL_DEBUG_SOURCE_WINDOW_SYSTEM_ARB -> "WINDOW SYSTEM";
            case ARBDebugOutput.GL_DEBUG_SOURCE_SHADER_COMPILER_ARB -> "SHADER COMPILER";
            case ARBDebugOutput.GL_DEBUG_SOURCE_THIRD_PARTY_ARB -> "THIRD PARTY";
            case ARBDebugOutput.GL_DEBUG_SOURCE_APPLICATION_ARB -> "APPLICATION";
            case ARBDebugOutput.GL_DEBUG_SOURCE_OTHER_ARB -> "OTHER";
            default -> String.format("Unknown [0x%X]", source);
        };
    }

    private static String getTypeARB(int type) {
        return switch (type) {
            case ARBDebugOutput.GL_DEBUG_TYPE_ERROR_ARB -> "ERROR";
            case ARBDebugOutput.GL_DEBUG_TYPE_DEPRECATED_BEHAVIOR_ARB -> "DEPRECATED BEHAVIOR";
            case ARBDebugOutput.GL_DEBUG_TYPE_UNDEFINED_BEHAVIOR_ARB -> "UNDEFINED BEHAVIOR";
            case ARBDebugOutput.GL_DEBUG_TYPE_PORTABILITY_ARB -> "PORTABILITY";
            case ARBDebugOutput.GL_DEBUG_TYPE_PERFORMANCE_ARB -> "PERFORMANCE";
            case ARBDebugOutput.GL_DEBUG_TYPE_OTHER_ARB -> "OTHER";
            default -> String.format("Unknown [0x%X]", type);
        };
    }

    private static String getSeverityARB(int severity) {
        return switch (severity) {
            case ARBDebugOutput.GL_DEBUG_SEVERITY_HIGH_ARB -> "HIGH";
            case ARBDebugOutput.GL_DEBUG_SEVERITY_MEDIUM_ARB -> "MEDIUM";
            case ARBDebugOutput.GL_DEBUG_SEVERITY_LOW_ARB -> "LOW";
            default -> String.format("Unknown [0x%X]", severity);
        };
    }

	private static String getCategoryAMD(int category) {
        return switch (category) {
            case AMDDebugOutput.GL_DEBUG_CATEGORY_API_ERROR_AMD -> "API ERROR";
            case AMDDebugOutput.GL_DEBUG_CATEGORY_WINDOW_SYSTEM_AMD -> "WINDOW SYSTEM";
            case AMDDebugOutput.GL_DEBUG_CATEGORY_DEPRECATION_AMD -> "DEPRECATION";
            case AMDDebugOutput.GL_DEBUG_CATEGORY_UNDEFINED_BEHAVIOR_AMD -> "UNDEFINED BEHAVIOR";
            case AMDDebugOutput.GL_DEBUG_CATEGORY_PERFORMANCE_AMD -> "PERFORMANCE";
            case AMDDebugOutput.GL_DEBUG_CATEGORY_SHADER_COMPILER_AMD -> "SHADER COMPILER";
            case AMDDebugOutput.GL_DEBUG_CATEGORY_APPLICATION_AMD -> "APPLICATION";
            case AMDDebugOutput.GL_DEBUG_CATEGORY_OTHER_AMD -> "OTHER";
            default -> String.format("Unknown [0x%X]", category);
        };
	}

	private static String getSeverityAMD(int severity) {
        return switch (severity) {
            case AMDDebugOutput.GL_DEBUG_SEVERITY_HIGH_AMD -> "HIGH";
            case AMDDebugOutput.GL_DEBUG_SEVERITY_MEDIUM_AMD -> "MEDIUM";
            case AMDDebugOutput.GL_DEBUG_SEVERITY_LOW_AMD -> "LOW";
            default -> String.format("Unknown [0x%X]", severity);
        };
	}

	public static String getMatrixModeName(int mode) {
        return switch (mode) {
            case GL11.GL_MODELVIEW -> "MODELVIEW";
            case GL11.GL_PROJECTION -> "PROJECTION";
            case GL11.GL_TEXTURE -> "TEXTURE";
            case GL11.GL_COLOR -> "COLOR";
            default -> String.format("0x%X", mode);
        };
	}

	public static String getCapabilityName(int cap) {
        return switch (cap) {
            case GL11.GL_ALPHA_TEST -> "ALPHA_TEST";
            case GL11.GL_BLEND -> "BLEND";
            case GL11.GL_COLOR_MATERIAL -> "COLOR_MATERIAL";
            case GL11.GL_CULL_FACE -> "CULL_FACE";
            case GL11.GL_DEPTH_TEST -> "DEPTH_TEST";
            case GL11.GL_FOG -> "FOG";
            case GL11.GL_LIGHTING -> "LIGHTING";
            case GL11.GL_LINE_SMOOTH -> "LINE_SMOOTH";
            case GL11.GL_NORMALIZE -> "NORMALIZE";
            case GL11.GL_POINT_SMOOTH -> "POINT_SMOOTH";
            case GL11.GL_POLYGON_OFFSET_FILL -> "POLYGON_OFFSET_FILL";
            case GL11.GL_POLYGON_OFFSET_LINE -> "POLYGON_OFFSET_LINE";
            case GL11.GL_POLYGON_SMOOTH -> "POLYGON_SMOOTH";
            case GL12.GL_RESCALE_NORMAL -> "RESCALE_NORMAL";
            case GL11.GL_SCISSOR_TEST -> "SCISSOR_TEST";
            case GL11.GL_STENCIL_TEST -> "STENCIL_TEST";
            case GL11.GL_TEXTURE_1D -> "TEXTURE_1D";
            case GL11.GL_TEXTURE_2D -> "TEXTURE_2D";
            case GL11.GL_LIGHT0 -> "LIGHT0";
            case GL11.GL_LIGHT1 -> "LIGHT1";
            case GL11.GL_LIGHT2 -> "LIGHT2";
            case GL11.GL_LIGHT3 -> "LIGHT3";
            case GL11.GL_LIGHT4 -> "LIGHT4";
            case GL11.GL_LIGHT5 -> "LIGHT5";
            case GL11.GL_LIGHT6 -> "LIGHT6";
            case GL11.GL_LIGHT7 -> "LIGHT7";
            default -> String.format("0x%X", cap);
        };
	}

	public static String getComparisonFuncName(int func) {
        return switch (func) {
            case GL11.GL_NEVER -> "NEVER";
            case GL11.GL_LESS -> "LESS";
            case GL11.GL_EQUAL -> "EQUAL";
            case GL11.GL_LEQUAL -> "LEQUAL";
            case GL11.GL_GREATER -> "GREATER";
            case GL11.GL_NOTEQUAL -> "NOTEQUAL";
            case GL11.GL_GEQUAL -> "GEQUAL";
            case GL11.GL_ALWAYS -> "ALWAYS";
            default -> String.format("0x%X", func);
        };
	}

	public static String getBlendFactorName(int factor) {
        return switch (factor) {
            case GL11.GL_ZERO -> "ZERO";
            case GL11.GL_ONE -> "ONE";
            case GL11.GL_SRC_COLOR -> "SRC_COLOR";
            case GL11.GL_ONE_MINUS_SRC_COLOR -> "ONE_MINUS_SRC_COLOR";
            case GL11.GL_DST_COLOR -> "DST_COLOR";
            case GL11.GL_ONE_MINUS_DST_COLOR -> "ONE_MINUS_DST_COLOR";
            case GL11.GL_SRC_ALPHA -> "SRC_ALPHA";
            case GL11.GL_ONE_MINUS_SRC_ALPHA -> "ONE_MINUS_SRC_ALPHA";
            case GL11.GL_DST_ALPHA -> "DST_ALPHA";
            case GL11.GL_ONE_MINUS_DST_ALPHA -> "ONE_MINUS_DST_ALPHA";
            case EXTBlendColor.GL_CONSTANT_COLOR_EXT -> "CONSTANT_COLOR";
            case EXTBlendColor.GL_ONE_MINUS_CONSTANT_COLOR_EXT -> "ONE_MINUS_CONSTANT_COLOR";
            case EXTBlendColor.GL_CONSTANT_ALPHA_EXT -> "CONSTANT_ALPHA";
            case EXTBlendColor.GL_ONE_MINUS_CONSTANT_ALPHA_EXT -> "ONE_MINUS_CONSTANT_ALPHA";
            case GL11.GL_SRC_ALPHA_SATURATE -> "SRC_ALPHA_SATURATE";
            default -> String.format("0x%X", factor);
        };
	}

	public static String getShadeModelName(int mode) {
        return switch (mode) {
            case GL11.GL_FLAT -> "FLAT";
            case GL11.GL_SMOOTH -> "SMOOTH";
            default -> String.format("0x%X", mode);
        };
	}

	public static String getTextureTargetName(int target) {
        return switch (target) {
            case GL11.GL_TEXTURE_1D -> "TEXTURE_1D";
            case GL11.GL_TEXTURE_2D -> "TEXTURE_2D";
            case GL12.GL_TEXTURE_3D -> "TEXTURE_3D";
            case GL13.GL_TEXTURE_CUBE_MAP -> "TEXTURE_CUBE_MAP";
            default -> String.format("0x%X", target);
        };
	}

	public static String getTexturePnameName(int pname) {
        return switch (pname) {
            case GL11.GL_TEXTURE_MIN_FILTER -> "TEXTURE_MIN_FILTER";
            case GL11.GL_TEXTURE_MAG_FILTER -> "TEXTURE_MAG_FILTER";
            case GL11.GL_TEXTURE_WRAP_S -> "TEXTURE_WRAP_S";
            case GL11.GL_TEXTURE_WRAP_T -> "TEXTURE_WRAP_T";
            case GL12.GL_TEXTURE_WRAP_R -> "TEXTURE_WRAP_R";
            case GL12.GL_TEXTURE_MIN_LOD -> "TEXTURE_MIN_LOD";
            case GL12.GL_TEXTURE_MAX_LOD -> "TEXTURE_MAX_LOD";
            case GL12.GL_TEXTURE_BASE_LEVEL -> "TEXTURE_BASE_LEVEL";
            case GL12.GL_TEXTURE_MAX_LEVEL -> "TEXTURE_MAX_LEVEL";
            case GL11.GL_TEXTURE_BORDER_COLOR -> "TEXTURE_BORDER_COLOR";
            case GL11.GL_TEXTURE_PRIORITY -> "TEXTURE_PRIORITY";
            case GL14.GL_TEXTURE_LOD_BIAS -> "TEXTURE_LOD_BIAS";
            case GL14.GL_GENERATE_MIPMAP -> "GENERATE_MIPMAP";
            default -> String.format("0x%X", pname);
        };
	}

	public static String getTextureFormatName(int format) {
        return switch (format) {
            case GL11.GL_RED -> "RED";
            case GL11.GL_GREEN -> "GREEN";
            case GL11.GL_BLUE -> "BLUE";
            case GL11.GL_ALPHA -> "ALPHA";
            case GL11.GL_RGB -> "RGB";
            case GL11.GL_RGBA -> "RGBA";
            case GL11.GL_LUMINANCE -> "LUMINANCE";
            case GL11.GL_LUMINANCE_ALPHA -> "LUMINANCE_ALPHA";
            case GL12.GL_BGR -> "BGR";
            case GL12.GL_BGRA -> "BGRA";
            case GL11.GL_DEPTH_COMPONENT -> "DEPTH_COMPONENT";
            case GL11.GL_STENCIL_INDEX -> "STENCIL_INDEX";
            case GL30.GL_DEPTH_STENCIL -> "DEPTH_STENCIL";
            // Internal formats
            case GL11.GL_RGB8 -> "RGB8";
            case GL11.GL_RGBA8 -> "RGBA8";
            case GL11.GL_ALPHA8 -> "ALPHA8";
            case GL11.GL_LUMINANCE8 -> "LUMINANCE8";
            case GL11.GL_LUMINANCE8_ALPHA8 -> "LUMINANCE8_ALPHA8";
            default -> String.format("0x%X", format);
        };
	}

	public static String getDataTypeName(int type) {
        return switch (type) {
            case GL11.GL_UNSIGNED_BYTE -> "UNSIGNED_BYTE";
            case GL11.GL_BYTE -> "BYTE";
            case GL11.GL_UNSIGNED_SHORT -> "UNSIGNED_SHORT";
            case GL11.GL_SHORT -> "SHORT";
            case GL11.GL_UNSIGNED_INT -> "UNSIGNED_INT";
            case GL11.GL_INT -> "INT";
            case GL11.GL_FLOAT -> "FLOAT";
            case GL11.GL_DOUBLE -> "DOUBLE";
            case GL12.GL_UNSIGNED_BYTE_3_3_2 -> "UNSIGNED_BYTE_3_3_2";
            case GL12.GL_UNSIGNED_SHORT_4_4_4_4 -> "UNSIGNED_SHORT_4_4_4_4";
            case GL12.GL_UNSIGNED_SHORT_5_5_5_1 -> "UNSIGNED_SHORT_5_5_5_1";
            case GL12.GL_UNSIGNED_INT_8_8_8_8 -> "UNSIGNED_INT_8_8_8_8";
            case GL12.GL_UNSIGNED_INT_10_10_10_2 -> "UNSIGNED_INT_10_10_10_2";
            default -> String.format("0x%X", type);
        };
	}

	public static String getLightName(int light) {
        return switch (light) {
            case GL11.GL_LIGHT0 -> "LIGHT0";
            case GL11.GL_LIGHT1 -> "LIGHT1";
            case GL11.GL_LIGHT2 -> "LIGHT2";
            case GL11.GL_LIGHT3 -> "LIGHT3";
            case GL11.GL_LIGHT4 -> "LIGHT4";
            case GL11.GL_LIGHT5 -> "LIGHT5";
            case GL11.GL_LIGHT6 -> "LIGHT6";
            case GL11.GL_LIGHT7 -> "LIGHT7";
            default -> String.format("0x%X", light);
        };
	}

	public static String getLightPnameName(int pname) {
        return switch (pname) {
            case GL11.GL_AMBIENT -> "AMBIENT";
            case GL11.GL_DIFFUSE -> "DIFFUSE";
            case GL11.GL_SPECULAR -> "SPECULAR";
            case GL11.GL_POSITION -> "POSITION";
            case GL11.GL_SPOT_DIRECTION -> "SPOT_DIRECTION";
            case GL11.GL_SPOT_EXPONENT -> "SPOT_EXPONENT";
            case GL11.GL_SPOT_CUTOFF -> "SPOT_CUTOFF";
            case GL11.GL_CONSTANT_ATTENUATION -> "CONSTANT_ATTENUATION";
            case GL11.GL_LINEAR_ATTENUATION -> "LINEAR_ATTENUATION";
            case GL11.GL_QUADRATIC_ATTENUATION -> "QUADRATIC_ATTENUATION";
            default -> String.format("0x%X", pname);
        };
	}

	public static String getMaterialPnameName(int pname) {
        return switch (pname) {
            case GL11.GL_AMBIENT -> "AMBIENT";
            case GL11.GL_DIFFUSE -> "DIFFUSE";
            case GL11.GL_SPECULAR -> "SPECULAR";
            case GL11.GL_EMISSION -> "EMISSION";
            case GL11.GL_SHININESS -> "SHININESS";
            case GL11.GL_AMBIENT_AND_DIFFUSE -> "AMBIENT_AND_DIFFUSE";
            case GL11.GL_COLOR_INDEXES -> "COLOR_INDEXES";
            default -> String.format("0x%X", pname);
        };
	}

	public static String getFaceName(int face) {
        return switch (face) {
            case GL11.GL_FRONT -> "FRONT";
            case GL11.GL_BACK -> "BACK";
            case GL11.GL_FRONT_AND_BACK -> "FRONT_AND_BACK";
            default -> String.format("0x%X", face);
        };
	}

	public static String getColorMaterialModeName(int mode) {
        return switch (mode) {
            case GL11.GL_AMBIENT -> "AMBIENT";
            case GL11.GL_DIFFUSE -> "DIFFUSE";
            case GL11.GL_SPECULAR -> "SPECULAR";
            case GL11.GL_EMISSION -> "EMISSION";
            case GL11.GL_AMBIENT_AND_DIFFUSE -> "AMBIENT_AND_DIFFUSE";
            default -> String.format("0x%X", mode);
        };
	}

	public static String getFogPnameName(int pname) {
        return switch (pname) {
            case GL11.GL_FOG_MODE -> "FOG_MODE";
            case GL11.GL_FOG_DENSITY -> "FOG_DENSITY";
            case GL11.GL_FOG_START -> "FOG_START";
            case GL11.GL_FOG_END -> "FOG_END";
            case GL11.GL_FOG_INDEX -> "FOG_INDEX";
            case GL11.GL_FOG_COLOR -> "FOG_COLOR";
            default -> String.format("0x%X", pname);
        };
	}

	public static String getLightModelPnameName(int pname) {
        return switch (pname) {
            case GL11.GL_LIGHT_MODEL_AMBIENT -> "LIGHT_MODEL_AMBIENT";
            case GL11.GL_LIGHT_MODEL_LOCAL_VIEWER -> "LIGHT_MODEL_LOCAL_VIEWER";
            case GL11.GL_LIGHT_MODEL_TWO_SIDE -> "LIGHT_MODEL_TWO_SIDE";
            case GL12.GL_LIGHT_MODEL_COLOR_CONTROL -> "LIGHT_MODEL_COLOR_CONTROL";
            default -> String.format("0x%X", pname);
        };
	}

	public static String getClearMaskString(int mask) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        if ((mask & GL11.GL_COLOR_BUFFER_BIT) != 0) {
            sb.append("COLOR");
            first = false;
        }
        if ((mask & GL11.GL_DEPTH_BUFFER_BIT) != 0) {
            if (!first) sb.append("|");
            sb.append("DEPTH");
            first = false;
        }
        if ((mask & GL11.GL_STENCIL_BUFFER_BIT) != 0) {
            if (!first) sb.append("|");
            sb.append("STENCIL");
            first = false;
        }
        if ((mask & GL11.GL_ACCUM_BUFFER_BIT) != 0) {
            if (!first) sb.append("|");
            sb.append("ACCUM");
        }
        return sb.toString();
	}

	public static String getCullFaceName(int mode) {
        return switch (mode) {
            case GL11.GL_FRONT -> "FRONT";
            case GL11.GL_BACK -> "BACK";
            case GL11.GL_FRONT_AND_BACK -> "FRONT_AND_BACK";
            default -> String.format("0x%X", mode);
        };
	}

	public static String getLogicOpName(int opcode) {
        return switch (opcode) {
            case GL11.GL_CLEAR -> "CLEAR";
            case GL11.GL_AND -> "AND";
            case GL11.GL_AND_REVERSE -> "AND_REVERSE";
            case GL11.GL_COPY -> "COPY";
            case GL11.GL_AND_INVERTED -> "AND_INVERTED";
            case GL11.GL_NOOP -> "NOOP";
            case GL11.GL_XOR -> "XOR";
            case GL11.GL_OR -> "OR";
            case GL11.GL_NOR -> "NOR";
            case GL11.GL_EQUIV -> "EQUIV";
            case GL11.GL_INVERT -> "INVERT";
            case GL11.GL_OR_REVERSE -> "OR_REVERSE";
            case GL11.GL_COPY_INVERTED -> "COPY_INVERTED";
            case GL11.GL_OR_INVERTED -> "OR_INVERTED";
            case GL11.GL_NAND -> "NAND";
            case GL11.GL_SET -> "SET";
            default -> String.format("0x%X", opcode);
        };
	}

	public static String getFramebufferStatusName(int status) {
        return switch (status) {
            case GL30.GL_FRAMEBUFFER_COMPLETE -> "COMPLETE";
            case GL30.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT -> "INCOMPLETE_ATTACHMENT";
            case GL30.GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT -> "INCOMPLETE_MISSING_ATTACHMENT";
            case GL30.GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER -> "INCOMPLETE_DRAW_BUFFER";
            case GL30.GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER -> "INCOMPLETE_READ_BUFFER";
            case GL30.GL_FRAMEBUFFER_UNSUPPORTED -> "UNSUPPORTED";
            case GL30.GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE -> "INCOMPLETE_MULTISAMPLE";
            case 0 -> "NO_FRAMEBUFFER_BOUND";
            default -> String.format("UNKNOWN(0x%X)", status);
        };
	}

	public static String getInternalFormatName(int format) {
        return switch (format) {
            // Basic
            case GL11.GL_RGBA -> "RGBA";
            case GL11.GL_RGB -> "RGB";
            // 8-bit normalized
            case GL30.GL_R8 -> "R8";
            case GL30.GL_RG8 -> "RG8";
            case GL11.GL_RGB8 -> "RGB8";
            case GL11.GL_RGBA8 -> "RGBA8";
            // 8-bit signed normalized
            case 0x8F94 -> "R8_SNORM";
            case 0x8F95 -> "RG8_SNORM";
            case 0x8F96 -> "RGB8_SNORM";
            case 0x8F97 -> "RGBA8_SNORM";
            // 16-bit normalized
            case GL30.GL_R16 -> "R16";
            case GL30.GL_RG16 -> "RG16";
            case GL11.GL_RGB16 -> "RGB16";
            case GL11.GL_RGBA16 -> "RGBA16";
            // 16-bit float
            case GL30.GL_R16F -> "R16F";
            case GL30.GL_RG16F -> "RG16F";
            case GL30.GL_RGB16F -> "RGB16F";
            case GL30.GL_RGBA16F -> "RGBA16F";
            // 32-bit float
            case GL30.GL_R32F -> "R32F";
            case GL30.GL_RG32F -> "RG32F";
            case GL30.GL_RGB32F -> "RGB32F";
            case GL30.GL_RGBA32F -> "RGBA32F";
            // Packed
            case GL30.GL_R11F_G11F_B10F -> "R11F_G11F_B10F";
            case GL11.GL_RGB10_A2 -> "RGB10_A2";
            default -> String.format("0x%X", format);
        };
	}

	private static DebugState debugState;

    private interface DebugState {
		void nameObject(int id, int object, String name);
		void pushGroup(String name);
		void popGroup();
        void debugMessage(String name);

        String getObjectLabel(int glProgram, int program);
    }

	private static class KHRDebugState implements DebugState {
        private static final int ID = 0;
		private int depth = 0;
        private static final int maxDepth = GL11.glGetInteger(KHRDebug.GL_MAX_DEBUG_GROUP_STACK_DEPTH);
        private static final int maxNameLength = GL11.glGetInteger(KHRDebug.GL_MAX_LABEL_LENGTH);

		@Override
		public void nameObject(int id, int object, String name) {
			KHRDebug.glObjectLabel(id, object, name);
		}

		@Override
		public void pushGroup(String name) {
            depth++;
            if (depth > maxDepth) {
                throw new RuntimeException("Stack overflow");
            }
			KHRDebug.glPushDebugGroup(KHRDebug.GL_DEBUG_SOURCE_APPLICATION, ID, name);
		}

		@Override
		public void popGroup() {
            depth--;
            if (depth < 0) {
                throw new RuntimeException("Stack underflow");
            }
            KHRDebug.glPopDebugGroup();
		}

        @Override
        public void debugMessage(String message) {
            KHRDebug.glDebugMessageInsert(KHRDebug.GL_DEBUG_SOURCE_APPLICATION, KHRDebug.GL_DEBUG_TYPE_MARKER, ID, KHRDebug.GL_DEBUG_SEVERITY_NOTIFICATION, message);
        }

        @Override
        public String getObjectLabel(int glProgram, int program) {
            if(program == 0)
                return "";

            return KHRDebug.glGetObjectLabel(glProgram, program, maxNameLength);
        }
    }

	private static class UnsupportedDebugState implements DebugState {
		@Override
		public void nameObject(int id, int object, String name) {
		}

		@Override
		public void pushGroup(String name) {
		}

		@Override
		public void popGroup() {
		}

        @Override
        public void debugMessage(String name) {

        }

        @Override
        public String getObjectLabel(int glProgram, int program) {
            return "";
        }
    }

	public static void initDebugState() {
		if (GLStateManager.capabilities.GL_KHR_debug || GLStateManager.capabilities.OpenGL43) {
			debugState = new KHRDebugState();
		} else {
			debugState = new UnsupportedDebugState();
		}
	}

    public static void nameObject(int id, int object, String name) {
        if(debugState != null && Thread.currentThread() == GLStateManager.getMainThread()) {
            debugState.nameObject(id, object, name);
        }
    }
    public static void pushGroup(String group) {
        if(debugState != null && Thread.currentThread() == GLStateManager.getMainThread()) {
            debugState.pushGroup(group);
        }
    }
    public static void popGroup() {
        if(debugState != null && Thread.currentThread() == GLStateManager.getMainThread()) {
            debugState.popGroup();
        }
    }
    public static void debugMessage(String message) {
        if(debugState != null && Thread.currentThread() == GLStateManager.getMainThread()) {
            debugState.debugMessage(message);
        }
    }
    public static String getObjectLabel(int glProgram, int program) {
        if(debugState != null && Thread.currentThread() == GLStateManager.getMainThread()) {
            return debugState.getObjectLabel(glProgram, program);
        }
        return "";
    }


}
