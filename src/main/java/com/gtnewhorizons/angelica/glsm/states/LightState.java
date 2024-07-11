package com.gtnewhorizons.angelica.glsm.states;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.joml.Vector4i;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.gtnewhorizons.angelica.glsm.GLStateManager.i2f;

public class LightState implements ISettableState<LightState> {

    private static final Vector4i vector4i = new Vector4i();
    private static final Vector4f vector4f = new Vector4f();
    private static final Vector3i vector3i = new Vector3i();
    private static final Vector3f vector3f = new Vector3f();

    public int light;

    public Vector4f ambient;
    public Vector4f diffuse;
    public Vector4f specular;
    public Vector4f position;
    public Vector3f spotDirection;
    public float spotExponent;
    public float spotCutoff;
    public float constantAttenuation;
    public float linearAttenuation;
    public float quadraticAttenuation;

    public LightState(int light) {
        this(
            light,
            new Vector4f(0F, 0F, 0F, 1F),
            new Vector4f(0F, 0F, 0F, 1F),
            new Vector4f(0F, 0F, 0F, 1F),
            new Vector4f(0F, 0F, 1F, 0F),
            new Vector3f(0F, 0F, -1F),
            0F,
            180F,
            1.0F,
            0.0F,
            0.0F
        );
        if (light == GL11.GL_LIGHT0) {
            this.diffuse.set(1F, 1F, 1F, 1F);
            this.specular.set(1F, 1F, 1F, 1F);
        }
    }

    public LightState(int light, Vector4f ambient, Vector4f diffuse, Vector4f specular, Vector4f position, Vector3f spotDirection, float spotExponent, float spotCutoff, float constantAttenuation, float linearAttenuation, float quadraticAttenuation) {
        this.light = light;
        this.ambient = ambient;
        this.diffuse = diffuse;
        this.specular = specular;
        this.position = position;
        this.spotDirection = spotDirection;
        this.spotExponent = spotExponent;
        this.spotCutoff = spotCutoff;
        this.constantAttenuation = constantAttenuation;
        this.linearAttenuation = linearAttenuation;
        this.quadraticAttenuation = quadraticAttenuation;
    }

    public void setAmbient(FloatBuffer newBuffer) {
        vector4f.set(newBuffer);
        if (GLStateManager.shouldBypassCache() || !this.ambient.equals(vector4f)) {
            this.ambient.set(vector4f);
            GL11.glLight(this.light, GL11.GL_AMBIENT, newBuffer);
        }
    }

    public void setAmbient(IntBuffer newBuffer) {
        vector4i.set(newBuffer);
        vector4f.set(i2f(vector4i.x), i2f(vector4i.y), i2f(vector4i.z), i2f(vector4i.w));
        if (GLStateManager.shouldBypassCache() || !this.ambient.equals(vector4f)) {
            this.ambient.set(vector4f);
            GL11.glLight(this.light, GL11.GL_AMBIENT, newBuffer);
        }
    }

    public void setDiffuse(FloatBuffer newBuffer) {
        vector4f.set(newBuffer);
        if (GLStateManager.shouldBypassCache() || !this.diffuse.equals(vector4f)) {
            this.diffuse.set(vector4f);
            GL11.glLight(this.light, GL11.GL_DIFFUSE, newBuffer);
        }
    }

    public void setDiffuse(IntBuffer newBuffer) {
        vector4i.set(newBuffer);
        vector4f.set(i2f(vector4i.x), i2f(vector4i.y), i2f(vector4i.z), i2f(vector4i.w));
        if (GLStateManager.shouldBypassCache() || !this.diffuse.equals(vector4f)) {
            this.diffuse.set(vector4f);
            GL11.glLight(this.light, GL11.GL_DIFFUSE, newBuffer);
        }
    }

    public void setSpecular(FloatBuffer newBuffer) {
        vector4f.set(newBuffer);
        if (GLStateManager.shouldBypassCache() || !this.specular.equals(vector4f)) {
            this.specular.set(vector4f);
            GL11.glLight(this.light, GL11.GL_SPECULAR, newBuffer);
        }
    }

    public void setSpecular(IntBuffer newBuffer) {
        vector4i.set(newBuffer);
        vector4f.set(i2f(vector4i.x), i2f(vector4i.y), i2f(vector4i.z), i2f(vector4i.w));
        if (GLStateManager.shouldBypassCache() || !this.specular.equals(vector4f)) {
            this.specular.set(vector4f);
            GL11.glLight(this.light, GL11.GL_SPECULAR, newBuffer);
        }
    }

    public void setPosition(FloatBuffer newBuffer) {
        vector4f.set(newBuffer);
        if (GLStateManager.shouldBypassCache() || !this.position.equals(vector4f)) {
            // The position is stored in eye coordinates(AKA multiplied by the modelview matrix
            // OpenGL does this itself, so we are applying it to our cached version, and passing the
            // untransformed buffer through to OpenGL. It is very important that OpenGL receives
            // the untransformed position to glLight.
            vector4f.mul(GLStateManager.getModelviewMatrix());
            this.position.set(vector4f);
            GL11.glLight(this.light, GL11.GL_POSITION, newBuffer);
        }
    }

    public void setPosition(IntBuffer newBuffer) {
        vector4i.set(newBuffer);
        vector4f.set(i2f(vector4i.x), i2f(vector4i.y), i2f(vector4i.z), i2f(vector4i.w));
        if (GLStateManager.shouldBypassCache() || !this.position.equals(vector4f)) {
            vector4f.mul(GLStateManager.getModelviewMatrix());
            this.position.set(vector4f);
            GL11.glLight(this.light, GL11.GL_POSITION, newBuffer);
        }
    }

    public void setSpotDirection(FloatBuffer newBuffer) {
        vector3f.set(newBuffer);
        if (GLStateManager.shouldBypassCache() || !this.spotDirection.equals(vector3f)) {
            this.spotDirection.set(vector3f);
            GL11.glLight(this.light, GL11.GL_SPOT_DIRECTION, newBuffer);
        }
    }

    public void setSpotDirection(IntBuffer newBuffer) {
        vector3i.set(newBuffer);
        vector3f.set((float) vector3i.x, (float) vector3i.y, (float) vector3i.z);
        if (GLStateManager.shouldBypassCache() || !this.spotDirection.equals(vector3f)) {
            this.spotDirection.set(vector3f);
            GL11.glLight(this.light, GL11.GL_SPOT_DIRECTION, newBuffer);
        }
    }

    public void setSpotExponent(FloatBuffer newBuffer) {
        setSpotExponent(newBuffer.get());
    }

    public void setSpotExponent(IntBuffer newBuffer) {
        setSpotExponent((float) newBuffer.get());
    }

    public void setSpotExponent(int i) {
        setSpotExponent((float) i);
    }

    public void setSpotExponent(float f) {
        if (GLStateManager.shouldBypassCache() || Float.compare(f, this.spotExponent) != 0) {
            this.spotExponent = f;
            GL11.glLightf(this.light, GL11.GL_SPOT_EXPONENT, f);
        }
    }

    public void setSpotCutoff(FloatBuffer newBuffer) {
        setSpotCutoff(newBuffer.get());
    }

    public void setSpotCutoff(IntBuffer newBuffer) {
        setSpotCutoff((float) newBuffer.get());
    }

    public void setSpotCutoff(int i) {
        setSpotCutoff((float) i);
    }

    public void setSpotCutoff(float f) {
        if (GLStateManager.shouldBypassCache() || Float.compare(f, this.spotCutoff) != 0) {
            this.spotExponent = f;
            GL11.glLightf(this.light, GL11.GL_SPOT_CUTOFF, f);
        }
    }

    public void setConstantAttenuation(FloatBuffer newBuffer) {
        setConstantAttenuation(newBuffer.get());
    }

    public void setConstantAttenuation(IntBuffer newBuffer) {
        setConstantAttenuation((float) newBuffer.get());
    }

    public void setConstantAttenuation(int i) {
        setConstantAttenuation((float) i);
    }

    public void setConstantAttenuation(float f) {
        if (GLStateManager.shouldBypassCache() || Float.compare(f, this.constantAttenuation) != 0) {
            this.constantAttenuation = f;
            GL11.glLightf(this.light, GL11.GL_CONSTANT_ATTENUATION, f);
        }
    }

    public void setLinearAttenuation(FloatBuffer newBuffer) {
        setLinearAttenuation(newBuffer.get());
    }

    public void setLinearAttenuation(IntBuffer newBuffer) {
        setLinearAttenuation((float) newBuffer.get());
    }

    public void setLinearAttenuation(int i) {
        setLinearAttenuation((float) i);
    }

    public void setLinearAttenuation(float f) {
        if (GLStateManager.shouldBypassCache() || Float.compare(f, this.linearAttenuation) != 0) {
            this.linearAttenuation = f;
            GL11.glLightf(this.light, GL11.GL_LINEAR_ATTENUATION, f);
        }
    }

    public void setQuadraticAttenuation(FloatBuffer newBuffer) {
        setQuadraticAttenuation(newBuffer.get());
    }

    public void setQuadraticAttenuation(IntBuffer newBuffer) {
        setQuadraticAttenuation((float) newBuffer.get());
    }

    public void setQuadraticAttenuation(int i) {
        setQuadraticAttenuation((float) i);
    }

    public void setQuadraticAttenuation(float f) {
        if (GLStateManager.shouldBypassCache() || Float.compare(f, this.quadraticAttenuation) != 0) {
            this.quadraticAttenuation = f;
            GL11.glLightf(this.light, GL11.GL_QUADRATIC_ATTENUATION, f);
        }
    }

    @Override
    public LightState set(LightState state) {
        this.ambient.set(state.ambient);
        this.diffuse.set(state.diffuse);
        this.specular.set(state.specular);
        this.position.set(state.position);
        this.spotDirection.set(state.spotDirection);
        this.spotExponent = state.spotExponent;
        this.spotCutoff = state.spotCutoff;

        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof LightState lightState)) return false;
        return this.ambient.equals(lightState.ambient) && this.diffuse.equals(lightState.diffuse) && this.specular.equals(lightState.specular) && this.position.equals(lightState.position) && this.spotDirection.equals(lightState.spotDirection) && Float.compare(this.spotCutoff, lightState.spotCutoff) == 0 && Float.compare(this.spotExponent, lightState.spotExponent) == 0;
    }

    @Override
    public LightState copy() { return new LightState(this.light).set(this); }

}
