package com.gtnewhorizons.angelica.dynamiclights;

import lombok.Getter;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DynamicLights {
    private static DynamicLights instance;

    private static final double MAX_RADIUS = 7.75;
    private static final double MAX_RADIUS_SQUARED = MAX_RADIUS * MAX_RADIUS;
    private final Set<IDynamicLightSource> dynamicLightSources = new HashSet<>();
    private final ReentrantReadWriteLock lightSourcesLock = new ReentrantReadWriteLock();
    private long lastUpdate = System.currentTimeMillis();
    @Getter private int lastUpdateCount = 0;

    public static DynamicLights get() {
        if (instance == null)
            instance = new DynamicLights();
        return instance;
    }

    public static boolean isEnabled() {
        //return !Objects.equals(DynamicLightsConfig.Quality.get(), "OFF");
        return true;
    }

    public void addLightSource(IDynamicLightSource lightSource) {
        this.lightSourcesLock.writeLock().lock();
        this.dynamicLightSources.add(lightSource);
        this.lightSourcesLock.writeLock().unlock();
    }

    /**
     * Removes the light source from the tracked light sources.
     *
     * @param lightSource the light source to remove
     */
    public void removeLightSource(@NotNull IDynamicLightSource lightSource) {
        this.lightSourcesLock.writeLock().lock();

        var dynamicLightSources = this.dynamicLightSources.iterator();
        IDynamicLightSource it;
        while (dynamicLightSources.hasNext()) {
            it = dynamicLightSources.next();
            if (it.equals(lightSource)) {
                dynamicLightSources.remove();
                if (SodiumWorldRenderer.getInstance() != null)
                    lightSource.angelica$scheduleTrackedChunksRebuild(SodiumWorldRenderer.getInstance());
                break;
            }
        }

        this.lightSourcesLock.writeLock().unlock();
    }

    /**
     * Returns whether the light source is tracked or not.
     *
     * @param lightSource the light source to check
     * @return {@code true} if the light source is tracked, else {@code false}
     */
    public boolean containsLightSource(@NotNull IDynamicLightSource lightSource) {
        //if (!lightSource.angelica$getDynamicLightWorld().isClientSide())
            //return false;

        boolean result;
        this.lightSourcesLock.readLock().lock();
        result = this.dynamicLightSources.contains(lightSource);
        this.lightSourcesLock.readLock().unlock();
        return result;
    }

    /**
     * Returns the number of dynamic light sources that currently emit lights.
     *
     * @return the number of dynamic light sources emitting light
     */
    public int getLightSourcesCount() {
        int result;

        this.lightSourcesLock.readLock().lock();
        result = this.dynamicLightSources.size();
        this.lightSourcesLock.readLock().unlock();

        return result;
    }
}
