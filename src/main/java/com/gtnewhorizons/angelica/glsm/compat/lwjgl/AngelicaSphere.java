package com.gtnewhorizons.angelica.glsm.compat.lwjgl;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.util.glu.Sphere;

import static org.lwjgl.opengl.GL11.GL_LINE_LOOP;
import static org.lwjgl.opengl.GL11.GL_LINE_STRIP;
import static org.lwjgl.opengl.GL11.GL_POINTS;
import static org.lwjgl.opengl.GL11.GL_TRIANGLE_FAN;
import static org.lwjgl.opengl.GL11.GL_TRIANGLE_STRIP;
import static org.lwjgl.util.glu.GLU.GLU_FILL;
import static org.lwjgl.util.glu.GLU.GLU_INSIDE;
import static org.lwjgl.util.glu.GLU.GLU_LINE;
import static org.lwjgl.util.glu.GLU.GLU_NONE;
import static org.lwjgl.util.glu.GLU.GLU_POINT;
import static org.lwjgl.util.glu.GLU.GLU_SILHOUETTE;

/**
 * Core-profile compatible replacement for {@link Sphere}.
 */
@SuppressWarnings("unused")
public class AngelicaSphere extends Sphere {

    private static final float PI = (float) Math.PI;

    private void emitNormal(float x, float y, float z) {
        final float mag = (float) Math.sqrt(x * x + y * y + z * z);
        if (mag > 0.00001F) {
            x /= mag;
            y /= mag;
            z /= mag;
        }
        GLStateManager.glNormal3f(x, y, z);
    }

    private void emitTexCoord(float x, float y) {
        if (super.textureFlag) GLStateManager.glTexCoord2f(x, y);
    }

    @Override
    public void draw(float radius, int slices, int stacks) {
        final float drho, dtheta, ds, dt, nsign;
        float rho, theta, x, y, z, s, t;

        final int imin, imax;
        final boolean normals;

        normals = super.normals != GLU_NONE;
        nsign = (super.orientation == GLU_INSIDE) ? -1.0f : 1.0f;

        drho = PI / stacks;
        dtheta = 2.0f * PI / slices;

        if (super.drawStyle == GLU_FILL) {
            if (!super.textureFlag) {
                // draw +Z end as a triangle fan
                GLStateManager.glBegin(GL_TRIANGLE_FAN);
                GLStateManager.glNormal3f(0.0f, 0.0f, 1.0f);
                GLStateManager.glVertex3f(0.0f, 0.0f, nsign * radius);
                for (int j = 0; j <= slices; j++) {
                    theta = (j == slices) ? 0.0f : j * dtheta;
                    x = -sin(theta) * sin(drho);
                    y = cos(theta) * sin(drho);
                    z = nsign * cos(drho);
                    if (normals) {
                        emitNormal(x * nsign, y * nsign, z * nsign);
                    }
                    GLStateManager.glVertex3f(x * radius, y * radius, z * radius);
                }
                GLStateManager.glEnd();
            }

            ds = 1.0f / slices;
            dt = 1.0f / stacks;
            t = 1.0f;
            if (super.textureFlag) {
                imin = 0;
                imax = stacks;
            } else {
                imin = 1;
                imax = stacks - 1;
            }

            // draw intermediate stacks as triangle strips (GL_QUAD_STRIP removed in core profile)
            for (int i = imin; i < imax; i++) {
                rho = i * drho;
                GLStateManager.glBegin(GL_TRIANGLE_STRIP);
                s = 0.0f;
                for (int j = 0; j <= slices; j++) {
                    theta = (j == slices) ? 0.0f : j * dtheta;
                    x = -sin(theta) * sin(rho);
                    y = cos(theta) * sin(rho);
                    z = nsign * cos(rho);
                    if (normals) {
                        emitNormal(x * nsign, y * nsign, z * nsign);
                    }
                    emitTexCoord(s, t);
                    GLStateManager.glVertex3f(x * radius, y * radius, z * radius);
                    x = -sin(theta) * sin(rho + drho);
                    y = cos(theta) * sin(rho + drho);
                    z = nsign * cos(rho + drho);
                    if (normals) {
                        emitNormal(x * nsign, y * nsign, z * nsign);
                    }
                    emitTexCoord(s, t - dt);
                    s += ds;
                    GLStateManager.glVertex3f(x * radius, y * radius, z * radius);
                }
                GLStateManager.glEnd();
                t -= dt;
            }

            if (!super.textureFlag) {
                // draw -Z end as a triangle fan
                GLStateManager.glBegin(GL_TRIANGLE_FAN);
                GLStateManager.glNormal3f(0.0f, 0.0f, -1.0f);
                GLStateManager.glVertex3f(0.0f, 0.0f, -radius * nsign);
                rho = PI - drho;
                s = 1.0f;
                for (int j = slices; j >= 0; j--) {
                    theta = (j == slices) ? 0.0f : j * dtheta;
                    x = -sin(theta) * sin(rho);
                    y = cos(theta) * sin(rho);
                    z = nsign * cos(rho);
                    if (normals) emitNormal(x * nsign, y * nsign, z * nsign);
                    s -= ds;
                    GLStateManager.glVertex3f(x * radius, y * radius, z * radius);
                }
                GLStateManager.glEnd();
            }
        } else if (super.drawStyle == GLU_LINE || super.drawStyle == GLU_SILHOUETTE) {
            // draw stack lines
            for (int i = 1; i < stacks; i++) {
                rho = i * drho;
                GLStateManager.glBegin(GL_LINE_LOOP);
                for (int j = 0; j < slices; j++) {
                    theta = j * dtheta;
                    x = cos(theta) * sin(rho);
                    y = sin(theta) * sin(rho);
                    z = cos(rho);
                    if (normals) emitNormal(x * nsign, y * nsign, z * nsign);
                    GLStateManager.glVertex3f(x * radius, y * radius, z * radius);
                }
                GLStateManager.glEnd();
            }
            // draw slice lines
            for (int j = 0; j < slices; j++) {
                theta = j * dtheta;
                GLStateManager.glBegin(GL_LINE_STRIP);
                for (int i = 0; i <= stacks; i++) {
                    rho = i * drho;
                    x = cos(theta) * sin(rho);
                    y = sin(theta) * sin(rho);
                    z = cos(rho);
                    if (normals) emitNormal(x * nsign, y * nsign, z * nsign);
                    GLStateManager.glVertex3f(x * radius, y * radius, z * radius);
                }
                GLStateManager.glEnd();
            }
        } else if (super.drawStyle == GLU_POINT) {
            // top and bottom-most points
            GLStateManager.glBegin(GL_POINTS);
            if (normals) GLStateManager.glNormal3f(0.0f, 0.0f, nsign);
            GLStateManager.glVertex3f(0.0f, 0.0f, radius);
            if (normals) GLStateManager.glNormal3f(0.0f, 0.0f, -nsign);
            GLStateManager.glVertex3f(0.0f, 0.0f, -radius);

            // loop over stacks
            for (int i = 1; i < stacks - 1; i++) {
                rho = i * drho;
                for (int j = 0; j < slices; j++) {
                    theta = j * dtheta;
                    x = cos(theta) * sin(rho);
                    y = sin(theta) * sin(rho);
                    z = cos(rho);
                    if (normals) emitNormal(x * nsign, y * nsign, z * nsign);
                    GLStateManager.glVertex3f(x * radius, y * radius, z * radius);
                }
            }
            GLStateManager.glEnd();
        }
    }
}
