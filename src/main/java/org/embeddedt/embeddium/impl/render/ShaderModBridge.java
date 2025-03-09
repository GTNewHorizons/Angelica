package org.embeddedt.embeddium.impl.render;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

public class ShaderModBridge {
    private static final MethodHandle SHADERS_ENABLED;
    private static final MethodHandle SHADERS_OPEN_SCREEN;
    private static final MethodHandle NVIDIUM_ENABLED;

    static {
        MethodHandle shadersEnabled = null, shaderOpenScreen = null;
        try {
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Method instanceGetter = irisApiClass.getDeclaredMethod("getInstance");
            Object irisApiInstance = instanceGetter.invoke(null);
            shadersEnabled = MethodHandles.lookup().unreflect(irisApiClass.getDeclaredMethod("isShaderPackInUse")).bindTo(irisApiInstance);
            shaderOpenScreen =  MethodHandles.lookup().unreflect(irisApiClass.getDeclaredMethod("openMainIrisScreenObj", Object.class)).bindTo(irisApiInstance);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (Throwable ignored) {
        }
        SHADERS_ENABLED = shadersEnabled;
        SHADERS_OPEN_SCREEN = shaderOpenScreen;
        MethodHandle nvidiumEnabled = null;
        try {
            Class<?> nvidiumClass = Class.forName("me.cortex.nvidium.Nvidium");
            nvidiumEnabled = MethodHandles.lookup().findStaticGetter(nvidiumClass, "IS_ENABLED", boolean.class);
        } catch (Throwable ignored) {
        }
        NVIDIUM_ENABLED = nvidiumEnabled;
    }

    public static boolean isNvidiumEnabled() {
        if(NVIDIUM_ENABLED != null) {
            try {
                return (boolean)NVIDIUM_ENABLED.invokeExact();
            } catch(Throwable e) {
                return false;
            }
        } else {
            return false;
        }
    }

    public static boolean areShadersEnabled() {
        if(SHADERS_ENABLED != null) {
            try {
                return (boolean)SHADERS_ENABLED.invokeExact();
            } catch (Throwable e) {
                return false;
            }
        } else {
            return false;
        }
    }

    public static boolean isShaderModPresent() {
        return SHADERS_ENABLED != null;
    }

    public static boolean emulateLegacyColorBrightnessFormat() {
        return areShadersEnabled() || isNvidiumEnabled();
    }

    public static Object openShaderScreen(Object parentScreen) {
        if(SHADERS_OPEN_SCREEN != null) {
            try {
                return SHADERS_OPEN_SCREEN.invoke(parentScreen);
            } catch(Throwable e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
