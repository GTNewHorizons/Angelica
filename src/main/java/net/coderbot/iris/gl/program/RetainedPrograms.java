package net.coderbot.iris.gl.program;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Linked programs kept from destroyed pipelines. Turning shaders off and on, reloading an unchanged pack, or
 * travelling between two dimensions rebuilds programs from identical sources; taking them from here skips compile and
 * link.
 */
public final class RetainedPrograms {
	record Sources(@Nullable String vertex, @Nullable String geometry, @Nullable String tessControl,
				   @Nullable String tessEval, @Nullable String fragment, @Nullable String compute) {}

	private static Map<Sources, ArrayDeque<Integer>> recent = new HashMap<>();
	private static Map<Sources, ArrayDeque<Integer>> older = new HashMap<>();
	private static String configuration;

	private RetainedPrograms() {}

	public static void useConfiguration(@Nullable String settings) {
		if (settings == null || !settings.equals(configuration)) {
			afterPipelineBuilt();
			afterPipelineBuilt();
		}
		configuration = settings;
	}

	static void retain(Sources sources, int program) {
		recent.computeIfAbsent(sources, key -> new ArrayDeque<>()).add(program);
	}

	static int take(Sources sources) {
		final int program = take(recent, sources);
		return program != 0 ? program : take(older, sources);
	}

	private static int take(Map<Sources, ArrayDeque<Integer>> pool, Sources sources) {
		if (pool.isEmpty()) return 0;
		final ArrayDeque<Integer> programs = pool.get(sources);
		if (programs == null) return 0;
		final int program = programs.poll();
		if (programs.isEmpty()) pool.remove(sources);
		return program;
	}

	public static void afterPipelineBuilt() {
		for (ArrayDeque<Integer> programs : older.values()) {
			for (int program : programs) {
				GLStateManager.glDeleteProgram(program);
			}
		}
		older = recent;
		recent = new HashMap<>();
	}
}
