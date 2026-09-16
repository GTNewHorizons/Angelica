package com.gtnewhorizons.angelica.rendering.particles;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

public final class ParticleDescriptorRegistry {

    private static final Logger LOGGER = LogManager.getLogger("Angelica/Particles");

    private static final String[] RENDER_PARTICLE_NAMES = { "renderParticle", "func_70539_a" };

    public static final ParticleDescriptor CAPTURE = (fx, partialTicks, out, state) -> false;
    public static final ParticleDescriptor VANILLA = (fx, partialTicks, out, state) -> {
        ParticleQuads.vanillaQuad(fx, partialTicks, fx.particleScale, out);
        return true;
    };

    private static final Object2ObjectOpenHashMap<String, ParticleDescriptor> BY_NAME = new Object2ObjectOpenHashMap<>();
    private static final Reference2ObjectOpenHashMap<Class<?>, ParticleDescriptor> BY_CLASS = new Reference2ObjectOpenHashMap<>();

    private static Class<?> lastClass;
    private static ParticleDescriptor lastDescriptor;

    static {
        VanillaParticleDescriptors.registerAll();
        BopParticleDescriptors.registerAll();
    }

    private ParticleDescriptorRegistry() {}

    public static void register(String className, ParticleDescriptor descriptor) {
        BY_NAME.put(className, descriptor);
        clearCache();
    }

    public static ParticleDescriptor forClass(Class<?> cls) {
        if (cls == lastClass) return lastDescriptor;
        ParticleDescriptor d = BY_CLASS.get(cls);
        if (d == null) {
            d = resolve(cls);
            BY_CLASS.put(cls, d);
        }
        lastClass = cls;
        lastDescriptor = d;
        return d;
    }

    public static void clearCache() {
        BY_CLASS.clear();
        lastClass = null;
        lastDescriptor = null;
    }

    private static ParticleDescriptor resolve(Class<?> cls) {
        final ParticleDescriptor registered = BY_NAME.get(cls.getName());
        if (registered != null) return registered;

        final Method render = renderParticleMethod(cls);
        if (render == null) {
            LOGGER.warn("Could not locate renderParticle on particle class {}, using the capture path", cls.getName());
            return CAPTURE;
        }
        return render.getDeclaringClass() == EntityFX.class ? VANILLA : CAPTURE;
    }

    private static Method renderParticleMethod(Class<?> cls) {
        for (String name : RENDER_PARTICLE_NAMES) {
            try {
                return cls.getMethod(name, Tessellator.class, float.class, float.class, float.class, float.class, float.class, float.class);
            } catch (NoSuchMethodException ignored) {}
        }
        return null;
    }
}
