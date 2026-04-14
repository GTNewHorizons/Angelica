package net.coderbot.iris.shaderpack.materialmap;

import net.coderbot.iris.Iris;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public record BlockEntry(NamespacedId id, Set<Integer> metas, Map<String, PropertiesTokenizer.NbtValue> nbtProperties) {
    public BlockEntry(NamespacedId id, Set<Integer> metas) {
        this(id, metas, Collections.emptyMap());
    }

    public boolean hasNbtProperties() {
        return !nbtProperties.isEmpty();
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

        // Parse blocks
        final PropertiesTokenizer.ParsedBlockIdentifier parsed = PropertiesTokenizer.parseBlockIdentifier(entry);
        final String baseEntry = parsed.blockId();
        final Map<String, PropertiesTokenizer.NbtValue> nbtProperties = parsed.nbtProperties();

        // We can assume that this array is of at least array length because the input string is non-empty.
        final String[] splitStates = baseEntry.split(":");

        // Trivial case: no states, no namespace
        if (splitStates.length == 1) {
            return new BlockEntry(new NamespacedId("minecraft", baseEntry), Collections.emptySet(), nbtProperties);
        }

        // Examples of what we'll accept
        // stone
        // stone:0
        // minecraft:stone:0
        // minecraft:stone:0,1,2
        // flower_pot:1[Item=minecraft:red_flower,Data=0]

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
            return new BlockEntry(new NamespacedId(splitStates[0], splitStates[1]), Collections.emptySet(), nbtProperties);
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
                    Iris.logger.warn("- Metadata ids must be a comma separated list of one or more integers, but " + splitStates[index] + " is not of that form!");
                }
            }
        }

        return new BlockEntry(id, metas, nbtProperties);
    }

}
