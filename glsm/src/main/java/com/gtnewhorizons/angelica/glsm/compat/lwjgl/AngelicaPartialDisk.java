package com.gtnewhorizons.angelica.glsm.compat.lwjgl;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.util.glu.PartialDisk;

import static org.lwjgl.opengl.GL11.GL_LINE_STRIP;
import static org.lwjgl.opengl.GL11.GL_POINTS;
import static org.lwjgl.opengl.GL11.GL_TRIANGLE_FAN;
import static org.lwjgl.opengl.GL11.GL_TRIANGLE_STRIP;
import static org.lwjgl.util.glu.GLU.GLU_FILL;
import static org.lwjgl.util.glu.GLU.GLU_FLAT;
import static org.lwjgl.util.glu.GLU.GLU_LINE;
import static org.lwjgl.util.glu.GLU.GLU_OUTSIDE;
import static org.lwjgl.util.glu.GLU.GLU_POINT;
import static org.lwjgl.util.glu.GLU.GLU_SILHOUETTE;
import static org.lwjgl.util.glu.GLU.GLU_SMOOTH;

/**
 * Core-profile compatible replacement for {@link PartialDisk}.
 */
@SuppressWarnings("unused")
public class AngelicaPartialDisk extends PartialDisk {

    private static final float PI = (float) Math.PI;
    private static final int CACHE_SIZE = 240;
    private final float[] sinCache = new float[CACHE_SIZE];
    private final float[] cosCache = new float[CACHE_SIZE];

    @Override
    public void draw(float innerRadius, float outerRadius, int slices, int loops, float startAngle, float sweepAngle) {
        final float deltaRadius, angleOffset;
        float angle, sintemp, costemp, radiusLow, radiusHigh, texLow = 0, texHigh = 0;
        final int slices2, finish;

        if (slices >= CACHE_SIZE) slices = CACHE_SIZE - 1;
        if (slices < 2 || loops < 1 || outerRadius <= 0.0f || innerRadius < 0.0f || innerRadius > outerRadius) {
            GLStateManager.LOGGER.warn("PartialDisk: GLU_INVALID_VALUE (slices={}, loops={}, innerRadius={}, outerRadius={})", slices, loops, innerRadius, outerRadius);
            return;
        }

        if (sweepAngle < -360.0f) sweepAngle = 360.0f;
        if (sweepAngle > 360.0f) sweepAngle = 360.0f;
        if (sweepAngle < 0) {
            startAngle += sweepAngle;
            sweepAngle = -sweepAngle;
        }

        slices2 = (sweepAngle == 360.0f) ? slices : slices + 1;

        /* Compute length (needed for normal calculations) */
        deltaRadius = outerRadius - innerRadius;

        angleOffset = startAngle / 180.0f * PI;
        for (int i = 0; i <= slices; i++) {
            angle = angleOffset + ((PI * sweepAngle) / 180.0f) * i / slices;
            sinCache[i] = sin(angle);
            cosCache[i] = cos(angle);
        }

        if (sweepAngle == 360.0f) {
            sinCache[slices] = sinCache[0];
            cosCache[slices] = cosCache[0];
        }

        switch (super.normals) {
            case GLU_FLAT, GLU_SMOOTH -> GLStateManager.glNormal3f(0.0f, 0.0f, (super.orientation == GLU_OUTSIDE) ? 1.0f : -1.0f);
            default -> {
            }
        }

        switch (super.drawStyle) {
            case GLU_FILL -> {
                if (innerRadius == .0f) {
                    finish = loops - 1;
                    /* Triangle strip for inner polygons */
                    GLStateManager.glBegin(GL_TRIANGLE_FAN);
                    if (super.textureFlag) {
                        GLStateManager.glTexCoord2f(0.5f, 0.5f);
                    }
                    GLStateManager.glVertex3f(0.0f, 0.0f, 0.0f);
                    radiusLow = outerRadius - deltaRadius * ((float) (loops - 1) / loops);
                    if (super.textureFlag) {
                        texLow = radiusLow / outerRadius / 2;
                    }

                    if (super.orientation == GLU_OUTSIDE) {
                        for (int i = slices; i >= 0; i--) {
                            if (super.textureFlag) {
                                GLStateManager.glTexCoord2f(texLow * sinCache[i] + 0.5f, texLow * cosCache[i] + 0.5f);
                            }
                            GLStateManager.glVertex3f(radiusLow * sinCache[i], radiusLow * cosCache[i], 0.0f);
                        }
                    } else {
                        for (int i = 0; i <= slices; i++) {
                            if (super.textureFlag) {
                                GLStateManager.glTexCoord2f(texLow * sinCache[i] + 0.5f, texLow * cosCache[i] + 0.5f);
                            }
                            GLStateManager.glVertex3f(radiusLow * sinCache[i], radiusLow * cosCache[i], 0.0f);
                        }
                    }
                    GLStateManager.glEnd();
                } else {
                    finish = loops;
                }
                for (int j = 0; j < finish; j++) {
                    radiusLow = outerRadius - deltaRadius * ((float) j / loops);
                    radiusHigh = outerRadius - deltaRadius * ((float) (j + 1) / loops);
                    if (super.textureFlag) {
                        texLow = radiusLow / outerRadius / 2;
                        texHigh = radiusHigh / outerRadius / 2;
                    }

                    GLStateManager.glBegin(GL_TRIANGLE_STRIP);
                    for (int i = 0; i <= slices; i++) {
                        if (super.orientation == GLU_OUTSIDE) {
                            if (super.textureFlag) {
                                GLStateManager.glTexCoord2f(texLow * sinCache[i] + 0.5f, texLow * cosCache[i] + 0.5f);
                            }
                            GLStateManager.glVertex3f(radiusLow * sinCache[i], radiusLow * cosCache[i], 0.0f);

                            if (super.textureFlag) {
                                GLStateManager.glTexCoord2f(texHigh * sinCache[i] + 0.5f, texHigh * cosCache[i] + 0.5f);
                            }
                            GLStateManager.glVertex3f(radiusHigh * sinCache[i], radiusHigh * cosCache[i], 0.0f);
                        } else {
                            if (super.textureFlag) {
                                GLStateManager.glTexCoord2f(texHigh * sinCache[i] + 0.5f, texHigh * cosCache[i] + 0.5f);
                            }
                            GLStateManager.glVertex3f(radiusHigh * sinCache[i], radiusHigh * cosCache[i], 0.0f);

                            if (super.textureFlag) {
                                GLStateManager.glTexCoord2f(texLow * sinCache[i] + 0.5f, texLow * cosCache[i] + 0.5f);
                            }
                            GLStateManager.glVertex3f(radiusLow * sinCache[i], radiusLow * cosCache[i], 0.0f);
                        }
                    }
                    GLStateManager.glEnd();
                }
            }
            case GLU_POINT -> {
                GLStateManager.glBegin(GL_POINTS);
                for (int i = 0; i < slices2; i++) {
                    sintemp = sinCache[i];
                    costemp = cosCache[i];
                    for (int j = 0; j <= loops; j++) {
                        radiusLow = outerRadius - deltaRadius * ((float) j / loops);

                        if (super.textureFlag) {
                            texLow = radiusLow / outerRadius / 2;

                            GLStateManager.glTexCoord2f(texLow * sinCache[i] + 0.5f, texLow * cosCache[i] + 0.5f);
                        }
                        GLStateManager.glVertex3f(radiusLow * sintemp, radiusLow * costemp, 0.0f);
                    }
                }
                GLStateManager.glEnd();
            }
            case GLU_LINE -> {
                if (innerRadius == outerRadius) {
                    GLStateManager.glBegin(GL_LINE_STRIP);

                    for (int i = 0; i <= slices; i++) {
                        if (super.textureFlag) {
                            GLStateManager.glTexCoord2f(sinCache[i] / 2 + 0.5f, cosCache[i] / 2 + 0.5f);
                        }
                        GLStateManager.glVertex3f(innerRadius * sinCache[i], innerRadius * cosCache[i], 0.0f);
                    }
                    GLStateManager.glEnd();
                    break;
                }
                for (int j = 0; j <= loops; j++) {
                    radiusLow = outerRadius - deltaRadius * ((float) j / loops);
                    if (super.textureFlag) {
                        texLow = radiusLow / outerRadius / 2;
                    }

                    GLStateManager.glBegin(GL_LINE_STRIP);
                    for (int i = 0; i <= slices; i++) {
                        if (super.textureFlag) {
                            GLStateManager.glTexCoord2f(texLow * sinCache[i] + 0.5f, texLow * cosCache[i] + 0.5f);
                        }
                        GLStateManager.glVertex3f(radiusLow * sinCache[i], radiusLow * cosCache[i], 0.0f);
                    }
                    GLStateManager.glEnd();
                }
                for (int i = 0; i < slices2; i++) {
                    sintemp = sinCache[i];
                    costemp = cosCache[i];
                    GLStateManager.glBegin(GL_LINE_STRIP);
                    for (int j = 0; j <= loops; j++) {
                        radiusLow = outerRadius - deltaRadius * ((float) j / loops);
                        if (super.textureFlag) {
                            texLow = radiusLow / outerRadius / 2;
                        }

                        if (super.textureFlag) {
                            GLStateManager.glTexCoord2f(texLow * sinCache[i] + 0.5f, texLow * cosCache[i] + 0.5f);
                        }
                        GLStateManager.glVertex3f(radiusLow * sintemp, radiusLow * costemp, 0.0f);
                    }
                    GLStateManager.glEnd();
                }
            }
            case GLU_SILHOUETTE -> {
                if (sweepAngle < 360.0f) {
                    for (int i = 0; i <= slices; i += slices) {
                        sintemp = sinCache[i];
                        costemp = cosCache[i];
                        GLStateManager.glBegin(GL_LINE_STRIP);
                        for (int j = 0; j <= loops; j++) {
                            radiusLow = outerRadius - deltaRadius * ((float) j / loops);

                            if (super.textureFlag) {
                                texLow = radiusLow / outerRadius / 2;
                                GLStateManager.glTexCoord2f(texLow * sinCache[i] + 0.5f, texLow * cosCache[i] + 0.5f);
                            }
                            GLStateManager.glVertex3f(radiusLow * sintemp, radiusLow * costemp, 0.0f);
                        }
                        GLStateManager.glEnd();
                    }
                }
                for (int j = 0; j <= loops; j += loops) {
                    radiusLow = outerRadius - deltaRadius * ((float) j / loops);
                    if (super.textureFlag) {
                        texLow = radiusLow / outerRadius / 2;
                    }

                    GLStateManager.glBegin(GL_LINE_STRIP);
                    for (int i = 0; i <= slices; i++) {
                        if (super.textureFlag) {
                            GLStateManager.glTexCoord2f(texLow * sinCache[i] + 0.5f, texLow * cosCache[i] + 0.5f);
                        }
                        GLStateManager.glVertex3f(radiusLow * sinCache[i], radiusLow * cosCache[i], 0.0f);
                    }
                    GLStateManager.glEnd();
                    if (innerRadius == outerRadius) break;
                }
            }
            default -> {
            }
        }
    }
}
