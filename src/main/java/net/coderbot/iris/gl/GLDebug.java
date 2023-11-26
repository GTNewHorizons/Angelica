/*
 * Copyright LWJGL. All rights reserved. Modified by IMS for use in Iris (net.coderbot.iris.gl).
 * License terms: https://www.lwjgl.org/license
 */

package net.coderbot.iris.gl;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
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
    /**
     * Sets up debug callbacks
     * @return 0 for failure, 1 for success, 2 for restart required.
     */
    public static int setupDebugMessageCallback() {
        if (Thread.currentThread() != GLStateManager.getMainThread()) {
            AngelicaTweaker.LOGGER.warn("setupDebugMessageCallback called from non-main thread!");
            return 0;
        }
        return setupDebugMessageCallback(System.err);
    }

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
    public static int setupDebugMessageCallback(PrintStream stream) {
        if (Iris.capabilities.OpenGL43 || Iris.capabilities.GL_KHR_debug) {
            Iris.logger.info("[GL] Using OpenGL 4.3 for error logging.");
            KHRDebugCallback proc = new KHRDebugCallback((source, type, id, severity, message) -> {
                stream.println("[LWJGL] OpenGL debug message");
                printDetail(stream, "ID", String.format("0x%X", id));
                printDetail(stream, "Source", getDebugSource(source));
                printDetail(stream, "Type", getDebugType(type));
                printDetail(stream, "Severity", getDebugSeverity(severity));
                printDetail(stream, "Message", message);
                printTrace(stream);
            });
            GL43.glDebugMessageControl(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_HIGH, null, true);
            GL43.glDebugMessageControl(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_MEDIUM, null, false);
            GL43.glDebugMessageControl(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_LOW, null, false);
            GL43.glDebugMessageControl(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_NOTIFICATION, null, false);
            GL43.glDebugMessageCallback(proc);

            if ((GL11.glGetInteger(GL30.GL_CONTEXT_FLAGS) & GL43.GL_CONTEXT_FLAG_DEBUG_BIT) == 0) {
                Iris.logger.warn("[GL] Warning: A non-debug context may not produce any debug output.");
                GL11.glEnable(GL43.GL_DEBUG_OUTPUT);
                return 2;
            }
            return 1;
        } else if (Iris.capabilities.GL_ARB_debug_output) {
            Iris.logger.info("[GL] Using ARB_debug_output for error logging.");
            ARBDebugOutputCallback proc = new ARBDebugOutputCallback((source, type, id, severity, message) -> {
                stream.println("[LWJGL] ARB_debug_output message");
                printDetail(stream, "ID", String.format("0x%X", id));
                printDetail(stream, "Source", getSourceARB(source));
                printDetail(stream, "Type", getTypeARB(type));
                printDetail(stream, "Severity", getSeverityARB(severity));
                printDetail(stream, "Message", message);
                printTrace(stream);
            });
            ARBDebugOutput.glDebugMessageControlARB(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_HIGH, null, true);
            ARBDebugOutput.glDebugMessageControlARB(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_MEDIUM, null, false);
            ARBDebugOutput.glDebugMessageControlARB(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_LOW, null, false);
            ARBDebugOutput.glDebugMessageControlARB(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_NOTIFICATION, null, false);
            ARBDebugOutput.glDebugMessageCallbackARB(proc);
            return 1;
        } else if (Iris.capabilities.GL_AMD_debug_output) {
            Iris.logger.info("[GL] Using AMD_debug_output for error logging.");
            AMDDebugOutputCallback proc = new AMDDebugOutputCallback((id, category, severity, message) -> {
                stream.println("[LWJGL] AMD_debug_output message");
                printDetail(stream, "ID", String.format("0x%X", id));
                printDetail(stream, "Category", getCategoryAMD(category));
                printDetail(stream, "Severity", getSeverityAMD(severity));
                printDetail(stream, "Message", message);
                printTrace(stream);
            });
            AMDDebugOutput.glDebugMessageEnableAMD(0, GL43.GL_DEBUG_SEVERITY_HIGH, null, true);
            AMDDebugOutput.glDebugMessageEnableAMD(0, GL43.GL_DEBUG_SEVERITY_MEDIUM, null, false);
            AMDDebugOutput.glDebugMessageEnableAMD(0, GL43.GL_DEBUG_SEVERITY_LOW, null, false);
            AMDDebugOutput.glDebugMessageEnableAMD(0, GL43.GL_DEBUG_SEVERITY_NOTIFICATION, null, false);
            AMDDebugOutput.glDebugMessageCallbackAMD(proc);
            return 1;
        } else {
            Iris.logger.info("[GL] No debug output implementation is available, cannot return debug info.");
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
			Iris.logger.info("[GL] No debug output implementation is available, cannot disable debug info.");
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
        // Only enable if debug is enabled, and also forcibly disable the splash screen
        if(!AngelicaMod.lwjglDebug)
            return;

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
