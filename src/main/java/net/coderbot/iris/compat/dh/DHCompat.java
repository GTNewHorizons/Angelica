package net.coderbot.iris.compat.dh;

import com.gtnewhorizons.angelica.rendering.RenderingState;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;

public class DHCompat {
    private static boolean dhPresent;
    private static boolean lastIncompatible;
    private DHCompatInternal compatInternalInstance;
    private final static Matrix4f tempProj = new Matrix4f();

    public DHCompat(DeferredWorldRenderingPipeline pipeline, boolean renderDHShadow) {
        if (pipeline == null) {
            return;
        }
        try {
            if (dhPresent) {
                compatInternalInstance = new DHCompatInternal(pipeline, renderDHShadow);
                lastIncompatible = compatInternalInstance.incompatiblePack();
            }
        } catch (Throwable e) {
            lastIncompatible = false;
            if (e instanceof InvocationTargetException ite) {
                throw new RuntimeException("Unknown error loading Distant Horizons compatibility.", ite.getCause());
            } else {
                throw new RuntimeException("Unknown error loading Distant Horizons compatibility.", e);
            }
        }

    }

    public static Matrix4fc getProjection() {
        Matrix4f projection = RenderingState.INSTANCE.getProjectionMatrix();
        if (!dhPresent) {
            return projection;
        }

        return tempProj.setPerspective(projection.perspectiveFov(), projection.m11() / projection.m00(), DHCompat.getNearPlane(), DHCompat.getFarPlane());
    }

    public static void run() {
        try {
            Class.forName("com.seibel.distanthorizons.DistantHorizonsTweaker");
            dhPresent = true;
        }
        catch (Exception e) {
            dhPresent = false;
        }
        try {
            if (dhPresent) {
                LodRendererEvents.setupEventHandlers();
            }
        } catch (Throwable e) {
            dhPresent = false;
            if (e instanceof ExceptionInInitializerError eiie) {
                throw new RuntimeException("Failure loading DH compat.", eiie.getCause());
            } else {
                throw new RuntimeException("DH found, but one or more API methods are missing. Iris requires DH [2.0.4] or DH API version [1.1.0] or newer. Please make sure you are on the latest version of DH and Iris.", e);
            }
        }
    }

    public static boolean lastPackIncompatible() {
        return dhPresent && hasRenderingEnabled() && lastIncompatible;
    }

    public static float getFarPlane() {
        if (!dhPresent) return 0.01f;
        return DHCompatInternal.getFarPlane();
    }

    public static float getNearPlane() {
        if (!dhPresent) return 0.01f;
        return DHCompatInternal.getNearPlane();
    }

    public static int getRenderDistance() {
        if (!dhPresent) return Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16;

        return DHCompatInternal.getRenderDistance();
    }

    public static boolean checkFrame() {
        if (!dhPresent) {
            return false;
        }

        return DHCompatInternal.checkFrame();
    }

    public static boolean hasRenderingEnabled() {
        if (!dhPresent) {
            return false;
        }

        return checkFrame();
    }

    public void clearPipeline() {
        if (compatInternalInstance == null) return;

        compatInternalInstance.clear();
    }

    public int getDepthTex() {
        if (compatInternalInstance == null) return -1;

        return compatInternalInstance.getStoredDepthTex();
    }

    public int getDepthTexNoTranslucent() {
        if (compatInternalInstance == null) return -1;

        return compatInternalInstance.getDepthTexNoTranslucent();
    }

    public DHCompatInternal getInstance() {
        return compatInternalInstance;
    }
}
