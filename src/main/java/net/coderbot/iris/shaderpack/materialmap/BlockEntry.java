package net.coderbot.iris.shaderpack.materialmap;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public record BlockEntry(
    NamespacedId id,
    Set<Integer> metas,
    Map<String, String> stateProperties,
    Map<String, PropertiesTokenizer.NbtValue> nbtProperties
) {
    public BlockEntry(NamespacedId id, Set<Integer> metas) {
        this(id, metas, Collections.emptyMap(), Collections.emptyMap());
    }

    public BlockEntry(NamespacedId id, Set<Integer> metas, Map<String, String> stateProperties) {
        this(id, metas, stateProperties, Collections.emptyMap());
    }

    public boolean hasNbtProperties() {
        return !nbtProperties.isEmpty();
    }

    public boolean hasStateProperties() {
        return !stateProperties.isEmpty();
    }

    /**
     * Parses a block ID entry. All tokenization is delegated to {@link PropertiesTokenizer}.
     *
     * @param entry The string representation of the entry. Must not be empty.
     */
    @NotNull
    public static BlockEntry parse(@NotNull String entry) {
        if (entry.isEmpty()) {
            throw new IllegalArgumentException("Called BlockEntry::parse with an empty string");
        }

        final PropertiesTokenizer.ParsedBlockIdentifier parsed = PropertiesTokenizer.parseBlockIdentifier(entry);
        return new BlockEntry(parsed.id(), parsed.metas(), parsed.stateProperties(), parsed.nbtProperties());
    }
}
