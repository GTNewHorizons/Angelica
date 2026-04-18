package net.coderbot.iris.shaderpack.materialmap;

import lombok.Getter;
import net.coderbot.iris.Iris;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Getter
public class BlockEntry {
	private final NamespacedId id;
	private final Set<Integer> metas;
	private final Map<String, String> stateProperties;

	public BlockEntry(NamespacedId id, Set<Integer> metas) {
		this(id, metas, Collections.emptyMap());
	}

	public BlockEntry(NamespacedId id, Set<Integer> metas, Map<String, String> stateProperties) {
		this.id = id;
		this.metas = metas;
		this.stateProperties = stateProperties;
	}

	/**
	 * Parses a block ID entry.
	 *
	 * @param entry The string representation of the entry. Must not be empty.
	 */
	@NotNull
	public static BlockEntry parse(@NotNull String entry) {
		if (entry.isEmpty()) {
			throw new IllegalArgumentException("Called BlockEntry::parse with an empty string");
		}

		// We can assume that this array is of at least array length because the input string is non-empty.
		final String[] splitStates = entry.split(":");

		// Trivial case: no states, no namespace
		if (splitStates.length == 1) {
			return new BlockEntry(new NamespacedId("minecraft", entry), Collections.emptySet());
		}

        // Examples of what we accept:
        // stone
        // stone:0
        // minecraft:stone:0
        // minecraft:stone:0,1,2
        // minecraft:furnace:lit=true      (blockstate property)
        // minecraft:oak_log:axis=y         (blockstate property)

		// Less trivial case: no metas or state properties, just a namespace
		if (splitStates.length == 2 && !StringUtils.isNumeric(splitStates[1].substring(0, 1))
				&& !splitStates[1].contains("=")) {
			return new BlockEntry(new NamespacedId(splitStates[0], splitStates[1]), Collections.emptySet());
		}

		// Complex case: metas and/or state properties
		final int statesStart;
		final NamespacedId id;

		if (splitStates.length == 2) {
			// "stone:0" or "stone:lit=true"
			statesStart = 1;
			id = new NamespacedId("minecraft", splitStates[0]);
		} else if (StringUtils.isNumeric(splitStates[1].substring(0, 1)) || splitStates[1].contains("=")) {
			// "stone:0:something" or "stone:lit=true:something" — unlikely but handle it
			statesStart = 1;
			id = new NamespacedId("minecraft", splitStates[0]);
		} else {
			// "minecraft:stone:0" or "minecraft:furnace:lit=true"
			statesStart = 2;
			id = new NamespacedId(splitStates[0], splitStates[1]);
		}

		final Set<Integer> metas = new HashSet<>();
		final Map<String, String> properties = new HashMap<>();

		for (int index = statesStart; index < splitStates.length; index++) {
			String segment = splitStates[index];

			if (segment.contains("=")) {
				// Blockstate property: key=value
				for (String prop : segment.split(",")) {
					int eq = prop.indexOf('=');
					if (eq > 0 && eq < prop.length() - 1) {
						properties.put(prop.substring(0, eq), prop.substring(eq + 1));
					}
				}
			} else {
				// Metadata IDs: comma-separated integers
				for (String metaPart : segment.split(",")) {
					try {
						metas.add(Integer.parseInt(metaPart));
					} catch (NumberFormatException e) {
						Iris.logger.warn("Warning: the block ID map entry \"" + entry + "\" could not be fully parsed:");
						Iris.logger.warn("- Metadata ids must be a comma separated list of one or more integers, but " + segment + " is not of that form!");
					}
				}
			}
        }

		return new BlockEntry(id, metas, properties);
	}

    @Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		final BlockEntry that = (BlockEntry) o;
		return Objects.equals(id, that.id) && Objects.equals(metas, that.metas) && Objects.equals(stateProperties, that.stateProperties);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, metas, stateProperties);
	}
}
