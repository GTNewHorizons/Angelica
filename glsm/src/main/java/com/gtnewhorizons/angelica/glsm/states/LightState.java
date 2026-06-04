package com.gtnewhorizons.angelica.glsm.states;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.joml.Matrix3f;
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
    private static final Vector3f vector3f = new Vector3f();
    private static final Vector3i vector3i = new Vector3i();
    private static final Matrix3f matrix3f = new Matrix3f();

    public int light;

    public final Vector4f ambient;
    public final Vector4f diffuse;
    public final Vector4f specular;
    public final Vector4f position;
    public final Vector3f spotDirection;
    private final Vector4f rawPosition;
    private int posMvGeneration = -1;
    private final Vector3f rawSpotDirection;
    private int spotDirLinearGeneration = -1;
    public float spotExponent;
    public float spotCutoff;
    public float spotCosCutoff;
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
        this.spotExponent = spotExponent;
        this.spotCutoff = spotCutoff;
        this.spotCosCutoff = (float) Math.cos(Math.toRadians(spotCutoff));
        this.constantAttenuation = constantAttenuation;
        this.linearAttenuation = linearAttenuation;
        this.quadraticAttenuation = quadraticAttenuation;

        this.rawPosition = new Vector4f(position);
        this.position = position;
        this.position.mul(GLStateManager.getModelViewMatrix());
        this.posMvGeneration = GLStateManager.mvGeneration;

        this.rawSpotDirection = new Vector3f(spotDirection);
        this.spotDirection = spotDirection;
        GLStateManager.getModelViewMatrix().get3x3(matrix3f);
        this.spotDirection.mul(matrix3f);
        this.spotDirLinearGeneration = GLStateManager.mvLinearGeneration;
    }

    public boolean setAmbient(FloatBuffer newBuffer) {
        vector4f.set(newBuffer);
        if (GLStateManager.shouldBypassCache() || !this.ambient.equals(vector4f)) {
            this.ambient.set(vector4f);
            return true;
        }
        return false;
    }

    public boolean setAmbient(IntBuffer newBuffer) {
        vector4i.set(newBuffer);
        vector4f.set(i2f(vector4i.x), i2f(vector4i.y), i2f(vector4i.z), i2f(vector4i.w));
        if (GLStateManager.shouldBypassCache() || !this.ambient.equals(vector4f)) {
            this.ambient.set(vector4f);
            return true;
        }
        return false;
    }

    public boolean setDiffuse(FloatBuffer newBuffer) {
        vector4f.set(newBuffer);
        if (GLStateManager.shouldBypassCache() || !this.diffuse.equals(vector4f)) {
            this.diffuse.set(vector4f);
            return true;
        }
        return false;
    }

    public boolean setDiffuse(IntBuffer newBuffer) {
        vector4i.set(newBuffer);
        vector4f.set(i2f(vector4i.x), i2f(vector4i.y), i2f(vector4i.z), i2f(vector4i.w));
        if (GLStateManager.shouldBypassCache() || !this.diffuse.equals(vector4f)) {
            this.diffuse.set(vector4f);
            return true;
        }
        return false;
    }

    public boolean setSpecular(FloatBuffer newBuffer) {
        vector4f.set(newBuffer);
        if (GLStateManager.shouldBypassCache() || !this.specular.equals(vector4f)) {
            this.specular.set(vector4f);
            return true;
        }
        return false;
    }

    public boolean setSpecular(IntBuffer newBuffer) {
        vector4i.set(newBuffer);
        vector4f.set(i2f(vector4i.x), i2f(vector4i.y), i2f(vector4i.z), i2f(vector4i.w));
        if (GLStateManager.shouldBypassCache() || !this.specular.equals(vector4f)) {
            this.specular.set(vector4f);
            return true;
        }
        return false;
    }

    public boolean setPosition(FloatBuffer newBuffer) {
        vector4f.set(newBuffer);
        return applyPosition(vector4f);
    }

    public boolean setPosition(IntBuffer newBuffer) {
        vector4i.set(newBuffer);
        vector4f.set((float) vector4i.x, (float) vector4i.y, (float) vector4i.z, (float) vector4i.w);
        return applyPosition(vector4f);
    }

    private boolean applyPosition(Vector4f raw) {
        final int mvGen = GLStateManager.mvGeneration;
        if (GLStateManager.shouldBypassCache() || this.posMvGeneration != mvGen || !this.rawPosition.equals(raw)) {
            this.rawPosition.set(raw);
            this.posMvGeneration = mvGen;
            this.position.set(raw);
            this.position.mul(GLStateManager.getModelViewMatrix());
            return true;
        }
        return false;
    }

    public boolean setSpotDirection(FloatBuffer newBuffer) {
        vector3f.set(newBuffer);
        return applySpotDirection(vector3f);
    }

    public boolean setSpotDirection(IntBuffer newBuffer) {
        vector3i.set(newBuffer);
        vector3f.set((float) vector3i.x, (float) vector3i.y, (float) vector3i.z);
        return applySpotDirection(vector3f);
    }

    private boolean applySpotDirection(Vector3f raw) {
        final int mvLinearGen = GLStateManager.mvLinearGeneration;
        if (GLStateManager.shouldBypassCache() || this.spotDirLinearGeneration != mvLinearGen || !this.rawSpotDirection.equals(raw)) {
            this.rawSpotDirection.set(raw);
            this.spotDirLinearGeneration = mvLinearGen;
            GLStateManager.getModelViewMatrix().get3x3(matrix3f);
            this.spotDirection.set(raw);
            this.spotDirection.mul(matrix3f);
            return true;
        }
        return false;
    }

    public boolean setSpotExponent(FloatBuffer newBuffer) {
        return setSpotExponent(newBuffer.get());
    }

    public boolean setSpotExponent(IntBuffer newBuffer) {
        return setSpotExponent((float) newBuffer.get());
    }

    public boolean setSpotExponent(int i) {
        return setSpotExponent((float) i);
    }

    public boolean setSpotExponent(float f) {
        if (GLStateManager.shouldBypassCache() || Float.compare(f, this.spotExponent) != 0) {
            this.spotExponent = f;
            return true;
        }
        return false;
    }

    public boolean setSpotCutoff(FloatBuffer newBuffer) {
        return setSpotCutoff(newBuffer.get());
    }

    public boolean setSpotCutoff(IntBuffer newBuffer) {
        return setSpotCutoff((float) newBuffer.get());
    }

    public boolean setSpotCutoff(int i) {
        return setSpotCutoff((float) i);
    }

    public boolean setSpotCutoff(float f) {
        if (GLStateManager.shouldBypassCache() || Float.compare(f, this.spotCutoff) != 0) {
            this.spotCutoff = f;
            this.spotCosCutoff = (float) Math.cos(Math.toRadians(f));
            return true;
        }
        return false;
    }

    public boolean setConstantAttenuation(FloatBuffer newBuffer) {
        return setConstantAttenuation(newBuffer.get());
    }

    public boolean setConstantAttenuation(IntBuffer newBuffer) {
        return setConstantAttenuation((float) newBuffer.get());
    }

    public boolean setConstantAttenuation(int i) {
        return setConstantAttenuation((float) i);
    }

    public boolean setConstantAttenuation(float f) {
        if (GLStateManager.shouldBypassCache() || Float.compare(f, this.constantAttenuation) != 0) {
            this.constantAttenuation = f;
            return true;
        }
        return false;
    }

    public boolean setLinearAttenuation(FloatBuffer newBuffer) {
        return setLinearAttenuation(newBuffer.get());
    }

    public boolean setLinearAttenuation(IntBuffer newBuffer) {
        return setLinearAttenuation((float) newBuffer.get());
    }

    public boolean setLinearAttenuation(int i) {
        return setLinearAttenuation((float) i);
    }

    public boolean setLinearAttenuation(float f) {
        if (GLStateManager.shouldBypassCache() || Float.compare(f, this.linearAttenuation) != 0) {
            this.linearAttenuation = f;
            return true;
        }
        return false;
    }

    public boolean setQuadraticAttenuation(FloatBuffer newBuffer) {
        return setQuadraticAttenuation(newBuffer.get());
    }

    public boolean setQuadraticAttenuation(IntBuffer newBuffer) {
        return setQuadraticAttenuation((float) newBuffer.get());
    }

    public boolean setQuadraticAttenuation(int i) {
        return setQuadraticAttenuation((float) i);
    }

    public boolean setQuadraticAttenuation(float f) {
        if (GLStateManager.shouldBypassCache() || Float.compare(f, this.quadraticAttenuation) != 0) {
            this.quadraticAttenuation = f;
            return true;
        }
        return false;
    }

    @Override
    public LightState set(LightState state) {
        this.ambient.set(state.ambient);
        this.diffuse.set(state.diffuse);
        this.specular.set(state.specular);
        this.position.set(state.position);
        this.rawPosition.set(state.rawPosition);
        this.posMvGeneration = state.posMvGeneration;
        this.spotDirection.set(state.spotDirection);
        this.rawSpotDirection.set(state.rawSpotDirection);
        this.spotDirLinearGeneration = state.spotDirLinearGeneration;
        this.spotExponent = state.spotExponent;
        this.spotCutoff = state.spotCutoff;
        this.spotCosCutoff = state.spotCosCutoff;
        this.constantAttenuation = state.constantAttenuation;
        this.linearAttenuation = state.linearAttenuation;
        this.quadraticAttenuation = state.quadraticAttenuation;

        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof LightState lightState)) return false;
        return this.ambient.equals(lightState.ambient)
            && this.diffuse.equals(lightState.diffuse)
            && this.specular.equals(lightState.specular)
            && this.position.equals(lightState.position)
            && this.spotDirection.equals(lightState.spotDirection)
            && Float.compare(this.spotCutoff, lightState.spotCutoff) == 0
            && Float.compare(this.spotExponent, lightState.spotExponent) == 0
            && Float.compare(this.linearAttenuation, lightState.linearAttenuation) == 0
            && Float.compare(this.constantAttenuation, lightState.constantAttenuation) == 0
            && Float.compare(this.quadraticAttenuation, lightState.quadraticAttenuation) == 0;
    }

    @Override
    public LightState copy() { return new LightState(this.light).set(this); }

}
