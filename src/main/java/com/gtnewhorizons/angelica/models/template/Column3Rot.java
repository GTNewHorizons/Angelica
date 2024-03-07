package com.gtnewhorizons.angelica.models.template;

import org.joml.Matrix4f;

import static org.joml.Math.toRadians;

/**
 * Use this to create a full cube column rotatable in 3 directions - vertical, x, and z
 */
public class Column3Rot {

    public final ColumnModel[] models = new ColumnModel[3];
    private static final Matrix4f ROT_X = new Matrix4f()
        .translation(-.5f, -.5f, -.5f).rotateLocalX(toRadians(90)).translateLocal(.5f, .5f, .5f);
    private static final Matrix4f ROT_Y = new Matrix4f()
        .translation(-.5f, -.5f, -.5f).rotateLocalX(toRadians(90)).rotateLocalY(toRadians(90)).translateLocal(.5f, .5f, .5f);

    public Column3Rot(String topTex, String sideTex) {

        this.models[0] = new ColumnModel(topTex, sideTex);
        this.models[1] = new ColumnModel(topTex, sideTex, ROT_Y);
        this.models[2] = new ColumnModel(topTex, sideTex, ROT_X);
    }

    public ColumnModel updown() { return this.models[0]; }
    public ColumnModel eastwest() { return this.models[1]; }
    public ColumnModel northsouth() { return this.models[2]; }
}
