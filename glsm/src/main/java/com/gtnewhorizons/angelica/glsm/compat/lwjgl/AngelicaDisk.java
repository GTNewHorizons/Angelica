package com.gtnewhorizons.angelica.glsm.compat.lwjgl;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.util.glu.Disk;

import static org.lwjgl.opengl.GL11.GL_LINE_LOOP;
import static org.lwjgl.opengl.GL11.GL_LINE_STRIP;
import static org.lwjgl.opengl.GL11.GL_POINTS;
import static org.lwjgl.opengl.GL11.GL_TRIANGLE_STRIP;
import static org.lwjgl.util.glu.GLU.GLU_FILL;
import static org.lwjgl.util.glu.GLU.GLU_LINE;
import static org.lwjgl.util.glu.GLU.GLU_NONE;
import static org.lwjgl.util.glu.GLU.GLU_OUTSIDE;
import static org.lwjgl.util.glu.GLU.GLU_POINT;
import static org.lwjgl.util.glu.GLU.GLU_SILHOUETTE;

/**
 * Core-profile compatible replacement for {@link Disk}.
 */
@SuppressWarnings("unused")
public class AngelicaDisk extends Disk {

    private static final float PI = (float) Math.PI;

    private void emitTexCoord(float x, float y) {
        if (super.textureFlag) GLStateManager.glTexCoord2f(x, y);
    }

    @Override
    public void draw(float innerRadius, float outerRadius, int slices, int loops) {
        final float da, dr;

        if (super.normals != GLU_NONE) {
            GLStateManager.glNormal3f(0.0f, 0.0f, (super.orientation == GLU_OUTSIDE) ? 1.0f : -1.0f);
        }

        da = 2.0f * PI / slices;
        dr = (outerRadius - innerRadius) / loops;

        switch (super.drawStyle) {
            case GLU_FILL -> {
                final float dtc = 2.0f * outerRadius;
                float sa, ca;
                float r1 = innerRadius;
                for (int l = 0; l < loops; l++) {
                    final float r2 = r1 + dr;
                    if (super.orientation == GLU_OUTSIDE) {
                        GLStateManager.glBegin(GL_TRIANGLE_STRIP);
                        for (int s = 0; s <= slices; s++) {
                            final float a = (s == slices) ? 0.0f : s * da;
                            sa = sin(a);
                            ca = cos(a);
                            emitTexCoord(0.5f + sa * r2 / dtc, 0.5f + ca * r2 / dtc);
                            GLStateManager.glVertex2f(r2 * sa, r2 * ca);
                            emitTexCoord(0.5f + sa * r1 / dtc, 0.5f + ca * r1 / dtc);
                            GLStateManager.glVertex2f(r1 * sa, r1 * ca);
                        }
                        GLStateManager.glEnd();
                    } else {
                        GLStateManager.glBegin(GL_TRIANGLE_STRIP);
                        for (int s = slices; s >= 0; s--) {
                            final float a = (s == slices) ? 0.0f : s * da;
                            sa = sin(a);
                            ca = cos(a);
                            emitTexCoord(0.5f - sa * r2 / dtc, 0.5f + ca * r2 / dtc);
                            GLStateManager.glVertex2f(r2 * sa, r2 * ca);
                            emitTexCoord(0.5f - sa * r1 / dtc, 0.5f + ca * r1 / dtc);
                            GLStateManager.glVertex2f(r1 * sa, r1 * ca);
                        }
                        GLStateManager.glEnd();
                    }
                    r1 = r2;
                }
            }
            case GLU_LINE -> {
                /* draw loops */
                for (int l = 0; l <= loops; l++) {
                    final float r = innerRadius + l * dr;
                    GLStateManager.glBegin(GL_LINE_LOOP);
                    for (int s = 0; s < slices; s++) {
                        final float a = s * da;
                        GLStateManager.glVertex2f(r * sin(a), r * cos(a));
                    }
                    GLStateManager.glEnd();
                }
                /* draw spokes */
                for (int s = 0; s < slices; s++) {
                    final float a = s * da;
                    final float x = sin(a);
                    final float y = cos(a);
                    GLStateManager.glBegin(GL_LINE_STRIP);
                    for (int l = 0; l <= loops; l++) {
                        final float r = innerRadius + l * dr;
                        GLStateManager.glVertex2f(r * x, r * y);
                    }
                    GLStateManager.glEnd();
                }
            }
            case GLU_POINT -> {
                GLStateManager.glBegin(GL_POINTS);
                for (int s = 0; s < slices; s++) {
                    final float a = s * da;
                    final float x = sin(a);
                    final float y = cos(a);
                    for (int l = 0; l <= loops; l++) {
                        final float r = innerRadius * l * dr;
                        GLStateManager.glVertex2f(r * x, r * y);
                    }
                }
                GLStateManager.glEnd();
            }
            case GLU_SILHOUETTE -> {
                if (innerRadius != 0.0) {
                    GLStateManager.glBegin(GL_LINE_LOOP);
                    for (float a = 0.0f; a < 2.0 * PI; a += da) {
                        final float x = innerRadius * sin(a);
                        final float y = innerRadius * cos(a);
                        GLStateManager.glVertex2f(x, y);
                    }
                    GLStateManager.glEnd();
                }
                GLStateManager.glBegin(GL_LINE_LOOP);
                for (float a = 0; a < 2.0f * PI; a += da) {
                    final float x = outerRadius * sin(a);
                    final float y = outerRadius * cos(a);
                    GLStateManager.glVertex2f(x, y);
                }
                GLStateManager.glEnd();
            }
            default -> {
            }
        }
    }
}
