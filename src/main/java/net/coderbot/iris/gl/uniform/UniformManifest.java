package net.coderbot.iris.gl.uniform;

import com.gtnewhorizons.angelica.glsm.shader.UniformType;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class UniformManifest {

    public record Entry(String name, UniformType type, UniformUpdateFrequency frequency) {}

    private final List<Entry> entries;
    private final Set<String> dynamicNames;
    private final Set<String> externallyManagedNames;

    UniformManifest(List<Entry> entries, Set<String> dynamicNames, Set<String> externallyManagedNames) {
        this.entries = Collections.unmodifiableList(entries);
        this.dynamicNames = Collections.unmodifiableSet(dynamicNames);
        this.externallyManagedNames = Collections.unmodifiableSet(externallyManagedNames);
    }

    public List<Entry> entries() {
        return entries;
    }

    public Set<String> dynamicNames() {
        return dynamicNames;
    }

    public Set<String> externallyManagedNames() {
        return externallyManagedNames;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
