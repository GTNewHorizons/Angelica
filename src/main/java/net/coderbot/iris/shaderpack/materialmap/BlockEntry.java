package net.coderbot.iris.shaderpack.materialmap;

import lombok.Getter;
import net.coderbot.iris.Iris;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Getter
public class BlockEntry {
	private final NamespacedId id;
	private final Set<Integer> metas;

	public BlockEntry(NamespacedId id,  Set<Integer> metas) {
		this.id = id;
		this.metas = metas;
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

        // Examples of what we'll accept
        // stone
        // stone:0
        // minecraft:stone:0
        // minecraft:stone:0,1,2  # Theoretically valid, but I haven't seen any examples in actual shaders

        // Examples of what we don't (Yet?) accept - Seems to be from MC 1.8+
        // minecraft:farmland:moisture=0
        // minecraft:farmland:moisture=1
        // minecraft:double_plant:half=lower
        // minecraft:double_plant:half=upper
        // minecraft:grass:snowy=true
        // minecraft:unpowered_comparator:powered=false


		// Less trivial case: no metas involved, just a namespace
		//
		// The first term MUST be a valid ResourceLocation component
		// The second term, if it is not numeric, must be a valid ResourceLocation component.
		if (splitStates.length == 2 && !StringUtils.isNumeric(splitStates[1].substring(0, 1))) {
			return new BlockEntry(new NamespacedId(splitStates[0], splitStates[1]), Collections.emptySet());
		}

		// Complex case: One or more states involved...
		final int statesStart;
		final NamespacedId id;

		if (StringUtils.isNumeric(splitStates[1].substring(0, 1))) {
			// We have an entry of the form "stone:0"
			statesStart = 1;
			id = new NamespacedId("minecraft", splitStates[0]);
		} else {
			// We have an entry of the form "minecraft:stone:0"
			statesStart = 2;
			id = new NamespacedId(splitStates[0], splitStates[1]);
		}

        final Set<Integer> metas = new HashSet<>();

		for (int index = statesStart; index < splitStates.length; index++) {
			// Parse out one or more metadata ids
			final String[] metaParts = splitStates[index].split(",");

            for (String metaPart : metaParts) {
                try {
                    metas.add(Integer.parseInt(metaPart));
                } catch (NumberFormatException e) {
                    Iris.logger.warn("Warning: the block ID map entry \"" + entry + "\" could not be fully parsed:");
                    Iris.logger.warn("- Metadata ids must be a comma separated list of one or more integers, but "+ splitStates[index] + " is not of that form!");
                }
            }
        }

		return new BlockEntry(id, metas);
	}

    @Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		final BlockEntry that = (BlockEntry) o;
		return Objects.equals(id, that.id) && Objects.equals(metas, that.metas);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, metas);
	}
}
