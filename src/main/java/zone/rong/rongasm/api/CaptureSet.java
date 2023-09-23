package zone.rong.rongasm.api;

import speiger.src.collections.objects.sets.ObjectOpenHashSet;

import java.util.HashSet;
import java.util.Set;

public class CaptureSet<K> extends HashSet<K> {

    private final Set<K> backingCaptures;

    public CaptureSet() {
        super();
        this.backingCaptures = new ObjectOpenHashSet<>();
    }

    public CaptureSet(Set<K> populate) {
        this();
        addAll(populate);
    }

    public void addCapture(K capture) {
        this.backingCaptures.add(capture);
    }

    public boolean put(K k) {
        return super.add(k);
    }

    @Override
    public boolean add(K k) {
        return this.backingCaptures.contains(k) || super.add(k);
    }
}
