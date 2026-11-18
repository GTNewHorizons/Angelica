package com.gtnewhorizons.angelica.glsm.compat.lwjgl;

import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
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
        if (super.textureFlag) GLSMCurrentAttribs.glTexCoord2f(x, y);
    }

    @Override
    public void draw(float innerRadius, float outerRadius, int slices, int loops) {
        final float da, dr;

        if (super.normals != GLU_NONE) {
            GLSMCurrentAttribs.glNormal3f(0.0f, 0.0f, (super.orientation == GLU_OUTSIDE) ? 1.0f : -1.0f);
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
                        GLSMDraws.glBegin(GL_TRIANGLE_STRIP);
                        for (int s = 0; s <= slices; s++) {
                            final float a = (s == slices) ? 0.0f : s * da;
                            sa = sin(a);
                            ca = cos(a);
                            emitTexCoord(0.5f + sa * r2 / dtc, 0.5f + ca * r2 / dtc);
                            GLSMDraws.glVertex2f(r2 * sa, r2 * ca);
                            emitTexCoord(0.5f + sa * r1 / dtc, 0.5f + ca * r1 / dtc);
                            GLSMDraws.glVertex2f(r1 * sa, r1 * ca);
                        }
                        GLSMDraws.glEnd();
                    } else {
                        GLSMDraws.glBegin(GL_TRIANGLE_STRIP);
                        for (int s = slices; s >= 0; s--) {
                            final float a = (s == slices) ? 0.0f : s * da;
                            sa = sin(a);
                            ca = cos(a);
                            emitTexCoord(0.5f - sa * r2 / dtc, 0.5f + ca * r2 / dtc);
                            GLSMDraws.glVertex2f(r2 * sa, r2 * ca);
                            emitTexCoord(0.5f - sa * r1 / dtc, 0.5f + ca * r1 / dtc);
                            GLSMDraws.glVertex2f(r1 * sa, r1 * ca);
                        }
                        GLSMDraws.glEnd();
                    }
                    r1 = r2;
                }
            }
            case GLU_LINE -> {
                /* draw loops */
                for (int l = 0; l <= loops; l++) {
                    final float r = innerRadius + l * dr;
                    GLSMDraws.glBegin(GL_LINE_LOOP);
                    for (int s = 0; s < slices; s++) {
                        final float a = s * da;
                        GLSMDraws.glVertex2f(r * sin(a), r * cos(a));
                    }
                    GLSMDraws.glEnd();
                }
                /* draw spokes */
                for (int s = 0; s < slices; s++) {
                    final float a = s * da;
                    final float x = sin(a);
                    final float y = cos(a);
                    GLSMDraws.glBegin(GL_LINE_STRIP);
                    for (int l = 0; l <= loops; l++) {
                        final float r = innerRadius + l * dr;
                        GLSMDraws.glVertex2f(r * x, r * y);
                    }
                    GLSMDraws.glEnd();
                }
            }
            case GLU_POINT -> {
                GLSMDraws.glBegin(GL_POINTS);
                for (int s = 0; s < slices; s++) {
                    final float a = s * da;
                    final float x = sin(a);
                    final float y = cos(a);
                    for (int l = 0; l <= loops; l++) {
                        final float r = innerRadius * l * dr;
                        GLSMDraws.glVertex2f(r * x, r * y);
                    }
                }
                GLSMDraws.glEnd();
            }
            case GLU_SILHOUETTE -> {
                if (innerRadius != 0.0) {
                    GLSMDraws.glBegin(GL_LINE_LOOP);
                    for (float a = 0.0f; a < 2.0 * PI; a += da) {
                        final float x = innerRadius * sin(a);
                        final float y = innerRadius * cos(a);
                        GLSMDraws.glVertex2f(x, y);
                    }
                    GLSMDraws.glEnd();
                }
                GLSMDraws.glBegin(GL_LINE_LOOP);
                for (float a = 0; a < 2.0f * PI; a += da) {
                    final float x = outerRadius * sin(a);
                    final float y = outerRadius * cos(a);
                    GLSMDraws.glVertex2f(x, y);
                }
                GLSMDraws.glEnd();
            }
            default -> {
            }
        }
    }
}
