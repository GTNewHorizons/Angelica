package com.gtnewhorizons.angelica.glsm.states;

import static com.gtnewhorizons.angelica.glsm.GLStateManager.i2f;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.joml.Vector4i;
import org.lwjgl.opengl.GL11;

@Lwjgl3Aware
public class LightState implements ISettableState<LightState> {

    private static final Vector4i vector4i = new Vector4i();
    private static final Vector4f vector4f = new Vector4f();
    private static final Vector3i vector3i = new Vector3i();
    private static final Vector3f vector3f = new Vector3f();
    private static final Matrix3f matrix3f = new Matrix3f();

    public int light;

    public final Vector4f ambient;
    public final Vector4f diffuse;
    public final Vector4f specular;
    public final Vector4f position;
    public final Vector3f spotDirection;
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
        this.spotExponent = spotExponent;
        this.spotCutoff = spotCutoff;
        this.constantAttenuation = constantAttenuation;
        this.linearAttenuation = linearAttenuation;
        this.quadraticAttenuation = quadraticAttenuation;

        this.position = position;
        this.position.mul(GLStateManager.getModelViewMatrix());

        this.spotDirection = spotDirection;
        GLStateManager.getModelViewMatrix().get3x3(matrix3f);
        this.spotDirection.mul(matrix3f);
    }

    public void setAmbient(FloatBuffer newBuffer) {
        vector4f.set(newBuffer);
        if (GLStateManager.shouldBypassCache() || !this.ambient.equals(vector4f)) {
            this.ambient.set(vector4f);
            GL11.glLightfv(this.light, GL11.GL_AMBIENT, newBuffer);
        }
    }

    public void setAmbient(IntBuffer newBuffer) {
        vector4i.set(newBuffer);
        vector4f.set(i2f(vector4i.x), i2f(vector4i.y), i2f(vector4i.z), i2f(vector4i.w));
        if (GLStateManager.shouldBypassCache() || !this.ambient.equals(vector4f)) {
            this.ambient.set(vector4f);
            GL11.glLightiv(this.light, GL11.GL_AMBIENT, newBuffer);
        }
    }

    public void setDiffuse(FloatBuffer newBuffer) {
        vector4f.set(newBuffer);
        if (GLStateManager.shouldBypassCache() || !this.diffuse.equals(vector4f)) {
            this.diffuse.set(vector4f);
            GL11.glLightfv(this.light, GL11.GL_DIFFUSE, newBuffer);
        }
    }

    public void setDiffuse(IntBuffer newBuffer) {
        vector4i.set(newBuffer);
        vector4f.set(i2f(vector4i.x), i2f(vector4i.y), i2f(vector4i.z), i2f(vector4i.w));
        if (GLStateManager.shouldBypassCache() || !this.diffuse.equals(vector4f)) {
            this.diffuse.set(vector4f);
            GL11.glLightiv(this.light, GL11.GL_DIFFUSE, newBuffer);
        }
    }

    public void setSpecular(FloatBuffer newBuffer) {
        vector4f.set(newBuffer);
        if (GLStateManager.shouldBypassCache() || !this.specular.equals(vector4f)) {
            this.specular.set(vector4f);
            GL11.glLightfv(this.light, GL11.GL_SPECULAR, newBuffer);
        }
    }

    public void setSpecular(IntBuffer newBuffer) {
        vector4i.set(newBuffer);
        vector4f.set(i2f(vector4i.x), i2f(vector4i.y), i2f(vector4i.z), i2f(vector4i.w));
        if (GLStateManager.shouldBypassCache() || !this.specular.equals(vector4f)) {
            this.specular.set(vector4f);
            GL11.glLightiv(this.light, GL11.GL_SPECULAR, newBuffer);
        }
    }

    public void setPosition(FloatBuffer newBuffer) {
        // We are bypassing cache everytime for position, because the necessary components to enable tracking the cache
        // are big and probably more than just bypassing. We would need to track the modelview matrix it was transformed
        // with and the untransformed coordinates in addition to the final transformation.
        this.position.set(newBuffer);
        this.position.mul(GLStateManager.getModelViewMatrix());
        GL11.glLightfv(this.light, GL11.GL_POSITION, newBuffer);
    }

    public void setPosition(IntBuffer newBuffer) {
        vector4i.set(newBuffer);
        this.position.set((float) vector4i.x, (float) vector4i.y, (float) vector4i.z, (float) vector4i.w);
        this.position.mul(GLStateManager.getModelViewMatrix());
        GL11.glLightiv(this.light, GL11.GL_POSITION, newBuffer);
    }

    public void setSpotDirection(FloatBuffer newBuffer) {
        // We are bypassing cache everytime for spot direction, because the necessary components to enable tracking the cache
        // are big and probably more than just bypassing. We would need to track the modelview matrix it was transformed
        // with and the untransformed coordinates in addition to the final transformation.
        GLStateManager.getModelViewMatrix().get3x3(matrix3f);
        this.spotDirection.set(newBuffer);
        this.spotDirection.mul(matrix3f);
        GL11.glLightfv(this.light, GL11.GL_SPOT_DIRECTION, newBuffer);
    }

    public void setSpotDirection(IntBuffer newBuffer) {
        vector3i.set(newBuffer);
        GLStateManager.getModelViewMatrix().get3x3(matrix3f);
        this.spotDirection.set((float) vector3i.x, (float) vector3i.y, (float) vector3i.z);
        this.spotDirection.mul(matrix3f);
        GL11.glLightiv(this.light, GL11.GL_SPOT_DIRECTION, newBuffer);
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
            this.spotCutoff = f;
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
