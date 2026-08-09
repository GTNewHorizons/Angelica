package net.coderbot.iris.gl.uniform;

import com.gtnewhorizons.angelica.glsm.shader.UniformType;

import net.coderbot.iris.gl.state.ValueUpdateNotifier;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

public final class ManifestCollectingHolder implements DynamicLocationalUniformHolder {

    private final List<String> namesById = new ObjectArrayList<>();
    private final Map<String, UniformType> typesByName = new Object2ObjectOpenHashMap<>();
    private final List<UniformManifest.Entry> entries = new ArrayList<>();
    private final Set<String> dynamicNames = new LinkedHashSet<>();
    private final Set<String> externallyManagedNames = new LinkedHashSet<>();

    @Override
    public OptionalInt location(String name, UniformType type) {
        if (typesByName.putIfAbsent(name, type) != null) return OptionalInt.empty();
        final int id = namesById.size();
        namesById.add(name);
        return OptionalInt.of(id);
    }

    private String nameFor(int location) {
        return location >= 0 && location < namesById.size() ? namesById.get(location) : null;
    }

    @Override
    public ManifestCollectingHolder addUniform(UniformUpdateFrequency updateFrequency, Uniform uniform) {
        Objects.requireNonNull(uniform);
        final String name = nameFor(uniform.getLocation());
        if (name == null) return this;
        switch (updateFrequency) {
            case ONCE, PER_TICK, PER_FRAME -> entries.add(new UniformManifest.Entry(name, typesByName.get(name), updateFrequency));
            case CUSTOM -> { }
        }
        return this;
    }

    @Override
    public ManifestCollectingHolder addDynamicUniform(Uniform uniform, ValueUpdateNotifier notifier) {
        Objects.requireNonNull(uniform);
        final String name = nameFor(uniform.getLocation());
        if (name != null) dynamicNames.add(name);
        return this;
    }

    @Override
    public ManifestCollectingHolder externallyManagedUniform(String name, UniformType type) {
        externallyManagedNames.add(name);
        return this;
    }

    public UniformManifest build() {
        final List<UniformManifest.Entry> grouped = new ArrayList<>(entries.size());
        for (UniformUpdateFrequency freq : GROUP_ORDER) {
            for (UniformManifest.Entry e : entries) {
                if (e.frequency() == freq) grouped.add(e);
            }
        }
        return new UniformManifest(grouped, dynamicNames, externallyManagedNames);
    }

    private static final UniformUpdateFrequency[] GROUP_ORDER = {
        UniformUpdateFrequency.ONCE, UniformUpdateFrequency.PER_TICK, UniformUpdateFrequency.PER_FRAME
    };
}
