/*
 * Copyright LWJGL. All rights reserved. Modified by IMS for use in Iris (net.coderbot.iris.gl).
 * License terms: https://www.lwjgl.org/license
 */

package net.coderbot.iris.gl;

import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import net.coderbot.iris.Iris;
import org.lwjgl.opengl.AMDDebugOutput;
import org.lwjgl.opengl.AMDDebugOutputCallback;
import org.lwjgl.opengl.ARBDebugOutput;
import org.lwjgl.opengl.ARBDebugOutputCallback;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.KHRDebug;
import org.lwjgl.opengl.KHRDebugCallback;

import java.io.PrintStream;
import java.util.function.Consumer;

import static org.lwjgl.opengl.ARBDebugOutput.glDebugMessageCallbackARB;

public final class GLDebug {

	private static void trace(Consumer<String> output) {
		/*
		 * We can not just use a fixed stacktrace element offset, because some methods
		 * are intercepted and some are not. So, check the package name.
		 */
		StackTraceElement[] elems = filterStackTrace(new Throwable(), 4).getStackTrace();
		for (StackTraceElement ste : elems) {
			output.accept(ste.toString());
		}
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

	private static void printTrace(PrintStream stream) {
		trace(new Consumer<String>() {
			boolean first = true;

			public void accept(String str) {
				if (first) {
					printDetail(stream, "Stacktrace", str);
					first = false;
				} else {
					printDetailLine(stream, "Stacktrace", str);
				}
			}
		});
	}

    /**
     * Sets up debug callbacks
     * @return 0 for failure, 1 for success, 2 for restart required.
     */
	public static int setupDebugMessageCallback() {
        if (Iris.capabilities.OpenGL43 || Iris.capabilities.GL_KHR_debug) {
			AngelicaTweaker.LOGGER.info("[GL] Using KHR_debug for error logging.");
            KHRDebug.glDebugMessageControl(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_HIGH, null, true);
            KHRDebug.glDebugMessageControl(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_MEDIUM, null, false);
            KHRDebug.glDebugMessageControl(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_LOW, null, false);
            KHRDebug.glDebugMessageControl(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_NOTIFICATION, null, false);
            KHRDebug.glDebugMessageCallback(new KHRDebugCallback());

			if (Iris.capabilities.OpenGL30 && (GL11.glGetInteger(GL30.GL_CONTEXT_FLAGS) & 2) == 0) {
				AngelicaTweaker.LOGGER.warn("[GL] Warning: A non-debug context may not produce any debug output.");
                GL11.glDisable(GL43.GL_DEBUG_OUTPUT);
				return 2;
			}
			return 1;
		} else if (Iris.capabilities.GL_ARB_debug_output) {
			AngelicaTweaker.LOGGER.info("[GL] Using ARB_debug_output for error logging.");

			ARBDebugOutput.glDebugMessageControlARB(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_HIGH, null, true);
			ARBDebugOutput.glDebugMessageControlARB(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_MEDIUM, null, false);
			ARBDebugOutput.glDebugMessageControlARB(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_LOW, null, false);
			ARBDebugOutput.glDebugMessageControlARB(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_NOTIFICATION, null, false);
			glDebugMessageCallbackARB(new ARBDebugOutputCallback());
			return 1;
		} else if (Iris.capabilities.GL_AMD_debug_output) {
			AngelicaTweaker.LOGGER.info("[GL] Using AMD_debug_output for error logging.");

			AMDDebugOutput.glDebugMessageEnableAMD(0, GL43.GL_DEBUG_SEVERITY_HIGH, null, true);
			AMDDebugOutput.glDebugMessageEnableAMD(0, GL43.GL_DEBUG_SEVERITY_MEDIUM, null, false);
			AMDDebugOutput.glDebugMessageEnableAMD(0, GL43.GL_DEBUG_SEVERITY_LOW, null, false);
			AMDDebugOutput.glDebugMessageEnableAMD(0, GL43.GL_DEBUG_SEVERITY_NOTIFICATION, null, false);
			AMDDebugOutput.glDebugMessageCallbackAMD(new AMDDebugOutputCallback());
			return 1;
		} else {
			AngelicaTweaker.LOGGER.info("[GL] No debug output implementation is available, cannot return debug info.");
			return 0;
		}
	}

	public static int disableDebugMessages() {
		if (Iris.capabilities.OpenGL43) {
			GL43.glDebugMessageCallback(null);
			return 1;
		} else if (Iris.capabilities.GL_KHR_debug) {
			KHRDebug.glDebugMessageCallback(null);
			if (Iris.capabilities.OpenGL30 && (GL11.glGetInteger(GL30.GL_CONTEXT_FLAGS) & 2) == 0) {
                GL11.glDisable(GL43.GL_DEBUG_OUTPUT);
			}
			return 1;
		} else if (Iris.capabilities.GL_ARB_debug_output) {
			glDebugMessageCallbackARB(null);
			return 1;
		} else if (Iris.capabilities.GL_AMD_debug_output) {
			AMDDebugOutput.glDebugMessageCallbackAMD(null);
			return 1;
		} else {
			AngelicaTweaker.LOGGER.info("[GL] No debug output implementation is available, cannot disable debug info.");
			return 0;
		}
	}

	private static void printDetail(PrintStream stream, String type, String message) {
		stream.printf("\t%s: %s\n", type, message);
	}

	private static void printDetailLine(PrintStream stream, String type, String message) {
		stream.append("    ");
		for (int i = 0; i < type.length(); i++) {
			stream.append(" ");
		}
		stream.append(message).append("\n");
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

	private static DebugState debugState;

	private static interface DebugState {
		void nameObject(int id, int object, String name);
		void pushGroup(int id, String name);
		void popGroup();
	}

	private static class KHRDebugState implements DebugState {
		private boolean hasGroup;

		@Override
		public void nameObject(int id, int object, String name) {
			KHRDebug.glObjectLabel(id, object, name);
		}

		@Override
		public void pushGroup(int id, String name) {
			KHRDebug.glPushDebugGroup(KHRDebug.GL_DEBUG_SOURCE_APPLICATION, id, name);
			hasGroup = true;
		}

		@Override
		public void popGroup() {
			if (hasGroup) {
				KHRDebug.glPopDebugGroup();
				hasGroup = false;
			}
		}
	}

	private static class UnsupportedDebugState implements DebugState {
		@Override
		public void nameObject(int id, int object, String name) {
		}

		@Override
		public void pushGroup(int id, String name) {
		}

		@Override
		public void popGroup() {
		}
	}

	public static void initRenderer() {
		if (Iris.capabilities.GL_KHR_debug || Iris.capabilities.OpenGL43) {
			debugState = new KHRDebugState();
		} else {
			debugState = new UnsupportedDebugState();
		}
	}

    public static void nameObject(int id, int object, String name) {
		debugState.nameObject(id, object, name);
    }
}
