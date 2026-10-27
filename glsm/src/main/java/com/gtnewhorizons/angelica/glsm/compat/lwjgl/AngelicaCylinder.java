package com.gtnewhorizons.angelica.glsm.compat.lwjgl;

import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import org.lwjgl.util.glu.Cylinder;

import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.GL_LINE_LOOP;
import static org.lwjgl.opengl.GL11.GL_POINTS;
import static org.lwjgl.opengl.GL11.GL_TRIANGLE_STRIP;
import static org.lwjgl.util.glu.GLU.GLU_FILL;
import static org.lwjgl.util.glu.GLU.GLU_INSIDE;
import static org.lwjgl.util.glu.GLU.GLU_LINE;
import static org.lwjgl.util.glu.GLU.GLU_POINT;
import static org.lwjgl.util.glu.GLU.GLU_SILHOUETTE;

/**
 * Core-profile compatible replacement for {@link Cylinder}.
 */
@SuppressWarnings("unused")
public class AngelicaCylinder extends Cylinder {

    private static final float PI = (float) Math.PI;

    private void emitNormal(float x, float y, float z) {
        final float mag = (float) Math.sqrt(x * x + y * y + z * z);
        if (mag > 0.00001F) {
            x /= mag;
            y /= mag;
            z /= mag;
        }
        GLSMCurrentAttribs.glNormal3f(x, y, z);
    }

    private void emitTexCoord(float x, float y) {
        if (super.textureFlag) GLSMCurrentAttribs.glTexCoord2f(x, y);
    }

    @Override
    public void draw(float baseRadius, float topRadius, float height, int slices, int stacks) {
        final float da, dr, dz, nz, nsign;
        float r, x, y, z;

        nsign = (super.orientation == GLU_INSIDE) ? -1.0f : 1.0f;

        da = 2.0f * PI / slices;
        dr = (topRadius - baseRadius) / stacks;
        dz = height / stacks;
        nz = (baseRadius - topRadius) / height;

        if (super.drawStyle == GLU_POINT) {
            GLSMDraws.glBegin(GL_POINTS);
            for (int i = 0; i < slices; i++) {
                x = cos(i * da);
                y = sin(i * da);
                emitNormal(x * nsign, y * nsign, nz * nsign);

                z = 0.0f;
                r = baseRadius;
                for (int j = 0; j <= stacks; j++) {
                    GLSMDraws.glVertex3f(x * r, y * r, z);
                    z += dz;
                    r += dr;
                }
            }
            GLSMDraws.glEnd();
        } else if (super.drawStyle == GLU_LINE || super.drawStyle == GLU_SILHOUETTE) {
            // Draw rings
            if (super.drawStyle == GLU_LINE) {
                z = 0.0f;
                r = baseRadius;
                for (int j = 0; j <= stacks; j++) {
                    GLSMDraws.glBegin(GL_LINE_LOOP);
                    for (int i = 0; i < slices; i++) {
                        x = cos(i * da);
                        y = sin(i * da);
                        emitNormal(x * nsign, y * nsign, nz * nsign);
                        GLSMDraws.glVertex3f(x * r, y * r, z);
                    }
                    GLSMDraws.glEnd();
                    z += dz;
                    r += dr;
                }
            } else {
                // draw one ring at each end
                if (baseRadius != 0.0) {
                    GLSMDraws.glBegin(GL_LINE_LOOP);
                    for (int i = 0; i < slices; i++) {
                        x = cos(i * da);
                        y = sin(i * da);
                        emitNormal(x * nsign, y * nsign, nz * nsign);
                        GLSMDraws.glVertex3f(x * baseRadius, y * baseRadius, 0.0f);
                    }
                    GLSMDraws.glEnd();
                    GLSMDraws.glBegin(GL_LINE_LOOP);
                    for (int i = 0; i < slices; i++) {
                        x = cos(i * da);
                        y = sin(i * da);
                        emitNormal(x * nsign, y * nsign, nz * nsign);
                        GLSMDraws.glVertex3f(x * topRadius, y * topRadius, height);
                    }
                    GLSMDraws.glEnd();
                }
            }
            // draw length lines
            GLSMDraws.glBegin(GL_LINES);
            for (int i = 0; i < slices; i++) {
                x = cos(i * da);
                y = sin(i * da);
                emitNormal(x * nsign, y * nsign, nz * nsign);
                GLSMDraws.glVertex3f(x * baseRadius, y * baseRadius, 0.0f);
                GLSMDraws.glVertex3f(x * topRadius, y * topRadius, height);
            }
            GLSMDraws.glEnd();
        } else if (super.drawStyle == GLU_FILL) {
            final float ds = 1.0f / slices;
            final float dt = 1.0f / stacks;
            float t = 0.0f;
            z = 0.0f;
            r = baseRadius;
            for (int j = 0; j < stacks; j++) {
                float s = 0.0f;
                GLSMDraws.glBegin(GL_TRIANGLE_STRIP);
                for (int i = 0; i <= slices; i++) {
                    final float angle = (i == slices) ? 0.0f : i * da;
                    x = sin(angle);
                    y = cos(angle);
                    emitNormal(x * nsign, y * nsign, nz * nsign);
                    emitTexCoord(s, t);
                    GLSMDraws.glVertex3f(x * r, y * r, z);
                    emitNormal(x * nsign, y * nsign, nz * nsign);
                    emitTexCoord(s, t + dt);
                    GLSMDraws.glVertex3f(x * (r + dr), y * (r + dr), z + dz);
                    s += ds;
                }
                GLSMDraws.glEnd();
                r += dr;
                t += dt;
                z += dz;
            }
        }
    }
}
