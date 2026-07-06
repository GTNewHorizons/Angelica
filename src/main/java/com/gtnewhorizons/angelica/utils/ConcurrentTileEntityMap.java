package com.gtnewhorizons.angelica.utils;

import com.gtnewhorizons.angelica.rendering.RenderThreadContext;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkPosition;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * Thread-safe wrapper around a tile entity map for chunkTileEntityMap.
 * May wrap either a plain Object2ObjectOpenHashMap (vanilla) or a ColumnTileEntityMap (cubic chunks).
 */
public class ConcurrentTileEntityMap implements Map<ChunkPosition, TileEntity> {
    private final Object2ObjectOpenHashMap<ChunkPosition, TileEntity> flatDelegate;
    private final Map<ChunkPosition, TileEntity> delegate;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ConcurrentLinkedQueue<ChunkPosition> invalidationQueue = new ConcurrentLinkedQueue<>();

    /** Vanilla path: backs the map with a fresh flat map. */
    public ConcurrentTileEntityMap() {
        this.flatDelegate = new Object2ObjectOpenHashMap<>();
        this.delegate = this.flatDelegate;
    }

    /** cubic chunks path: wraps an existing map (e.g. ColumnTileEntityMap). */
    public ConcurrentTileEntityMap(Map<ChunkPosition, TileEntity> toWrap) {
        this.flatDelegate = null;
        this.delegate = toWrap;
    }

    public void queueInvalidation(ChunkPosition pos) {
        invalidationQueue.add(pos);
    }

    private void processInvalidationQueue() {
        ChunkPosition pos;
        while ((pos = invalidationQueue.poll()) != null) {
            final TileEntity te = delegate.get(pos);
            if (te != null && te.isInvalid()) {
                delegate.remove(pos);
            }
        }
    }

    public void withReadLock(Runnable action) {
        lock.readLock().lock();
        try {
            action.run();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void readLock() {
        lock.readLock().lock();
    }

    public void readUnlock() {
        lock.readLock().unlock();
    }

    public void withWriteLock(Runnable action) {
        lock.writeLock().lock();
        try {
            action.run();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public TileEntity putDirect(ChunkPosition key, TileEntity value) {
        return delegate.put(key, value);
    }

    /**
     * Iterates all non-invalid tile entities whose Y coordinate falls within [minY, maxY].
     * Caller must hold the read lock.
     */
    public void forEachInYRange(int minY, int maxY, Consumer<Map.Entry<ChunkPosition, TileEntity>> action) {
        final Iterable<? extends Map.Entry<ChunkPosition, TileEntity>> entries =
            flatDelegate != null ? Object2ObjectMaps.fastIterable(flatDelegate) : delegate.entrySet();
        for (Map.Entry<ChunkPosition, TileEntity> entry : entries) {
            final ChunkPosition tePos = entry.getKey();
            if (tePos.chunkPosY < minY || tePos.chunkPosY > maxY) continue;
            final TileEntity te = entry.getValue();
            if (te != null && !te.isInvalid()) {
                action.accept(entry);
            }
        }
    }

    private static boolean isRenderWorkerThread() {
        return RenderThreadContext.hasWorldSlice();
    }


    @Override
    public TileEntity get(Object key) {
        if (!isRenderWorkerThread()) return delegate.get(key);

        lock.readLock().lock();
        try {
            final TileEntity te = delegate.get(key);
            if (te != null && te.isInvalid()) {
                if (key instanceof ChunkPosition pos) {
                    invalidationQueue.add(pos);
                }
                return null;
            }
            return te;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public TileEntity put(ChunkPosition key, TileEntity value) {
        lock.writeLock().lock();
        try {
            processInvalidationQueue();
            return delegate.put(key, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public TileEntity remove(Object key) {
        if (isRenderWorkerThread()) {
            if (key instanceof ChunkPosition pos) {
                invalidationQueue.add(pos);
            }
            return null;
        }
        lock.writeLock().lock();
        try {
            processInvalidationQueue();
            return delegate.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void putAll(Map<? extends ChunkPosition, ? extends TileEntity> m) {
        lock.writeLock().lock();
        try {
            processInvalidationQueue();
            delegate.putAll(m);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            invalidationQueue.clear();
            if (flatDelegate != null) {
                flatDelegate.clear();
            }
            // wrapped delegate manages its own TE lifetime; skip clear()
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int size() {
        if (!isRenderWorkerThread()) return delegate.size();
        lock.readLock().lock();
        try {
            return delegate.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        if (!isRenderWorkerThread()) return delegate.isEmpty();
        lock.readLock().lock();
        try {
            return delegate.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean containsKey(Object key) {
        if (!isRenderWorkerThread()) return delegate.containsKey(key);
        lock.readLock().lock();
        try {
            return delegate.containsKey(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean containsValue(Object value) {
        if (!isRenderWorkerThread()) return delegate.containsValue(value);
        lock.readLock().lock();
        try {
            return delegate.containsValue(value);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Set<ChunkPosition> keySet() {
        if (isRenderWorkerThread()) {
            throw new IllegalStateException("keySet() called from render worker. Use withReadLock() for iteration.");
        }
        return delegate.keySet();
    }

    @Override
    public Collection<TileEntity> values() {
        if (isRenderWorkerThread()) {
            throw new IllegalStateException("values() called from render worker. Use withReadLock() for iteration.");
        }
        return delegate.values();
    }

    @Override
    public Set<Entry<ChunkPosition, TileEntity>> entrySet() {
        if (isRenderWorkerThread()) {
            throw new IllegalStateException("entrySet() called from render worker. Use withReadLock() for iteration.");
        }
        return delegate.entrySet();
    }
}
