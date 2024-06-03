package com.gtnewhorizons.angelica.glsm.states;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

public class LightState implements ISettableState<LightState> {

    public int light;

    public Vector4f ambient;
    public Vector4f diffuse;
    public Vector4f specular;
    public Vector4f position;
    public Vector3f spotDirection;
    public float spotExponent;
    public float spotCutoff;

    public LightState(int light) {
        this(
            light,
            new Vector4f(0F, 0F, 0F, 1F),
            new Vector4f(0F, 0F, 0F, 0F),
            new Vector4f(0F, 0F, 0F, 0F),
            new Vector4f(0F, 0F, 1F, 0F),
            new Vector3f(0F, 0F, -1F),
            0F,
            180F
        );
        if (light == GL11.GL_LIGHT0) {
            this.diffuse.set(1F, 1F, 1F, 1F);
            this.specular.set(1F, 1F, 1F, 1F);
        }
    }

    public LightState(int light, Vector4f ambient, Vector4f diffuse, Vector4f specular, Vector4f position, Vector3f spotDirection, float spotExponent, float spotCutoff) {
        this.light = light;
        this.ambient = ambient;
        this.diffuse = diffuse;
        this.specular = specular;
        this.position = position;
        this.spotDirection = spotDirection;
        this.spotExponent = spotExponent;
        this.spotCutoff = spotCutoff;
    }

    public void setAmbient(FloatBuffer newBuffer) {
        Vector4f newVector = new Vector4f(newBuffer);
        if (GLStateManager.shouldBypassCache() || !this.ambient.equals(newVector)) {
            this.ambient = newVector;
            GL11.glLight(this.light, GL11.GL_AMBIENT, newBuffer);
        }
    }

    public void setDiffuse(FloatBuffer newBuffer) {
        Vector4f newVector = new Vector4f(newBuffer);
        if (GLStateManager.shouldBypassCache() || !this.diffuse.equals(newVector)) {
            this.diffuse = newVector;
            GL11.glLight(this.light, GL11.GL_DIFFUSE, newBuffer);
        }
    }

    public void setSpecular(FloatBuffer newBuffer) {
        Vector4f newVector = new Vector4f(newBuffer);
        if (GLStateManager.shouldBypassCache() || !this.specular.equals(newVector)) {
            this.specular = newVector;
            GL11.glLight(this.light, GL11.GL_SPECULAR, newBuffer);
        }
    }

    public void setPosition(FloatBuffer newBuffer) {
        Vector4f newVector = new Vector4f(newBuffer);
        if (GLStateManager.shouldBypassCache() || !this.position.equals(newVector)) {
            this.position = newVector;
            GL11.glLight(this.light, GL11.GL_POSITION, newBuffer);
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
