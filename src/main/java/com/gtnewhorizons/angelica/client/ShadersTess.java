package com.gtnewhorizons.angelica.client;

import static org.lwjgl.opengl.ARBVertexShader.glDisableVertexAttribArrayARB;
import static org.lwjgl.opengl.ARBVertexShader.glEnableVertexAttribArrayARB;
import static org.lwjgl.opengl.ARBVertexShader.glVertexAttribPointerARB;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import com.gtnewhorizons.angelica.mixins.interfaces.TessellatorAccessor;

public class ShadersTess {

    public static final int vertexStride = 16;

    public static int draw(Tessellator tess) {
        final TessellatorAccessor tessa = (TessellatorAccessor) tess;
        if (!tess.isDrawing) {
            throw new IllegalStateException("Not tesselating!");
        } else {
            tess.isDrawing = false;
            if (tess.drawMode == GL11.GL_QUADS && tess.vertexCount % 4 != 0) {
                AngelicaTweaker.LOGGER.warn("%s", "bad vertexCount");
            }
            int voffset = 0;
            int realDrawMode = tess.drawMode;
            while (voffset < tess.vertexCount) {
                int vcount;
                vcount = Math.min(
                        tess.vertexCount - voffset,
                        tessa.angelica$getByteBuffer().capacity() / (vertexStride * 4));
                if (realDrawMode == GL11.GL_QUADS) vcount = vcount / 4 * 4;
                tessa.angelica$getFloatBuffer().clear();
                tessa.angelica$getShortBuffer().clear();
                tessa.angelica$getIntBuffer().clear();
                tessa.angelica$getIntBuffer().put(tess.rawBuffer, voffset * vertexStride, vcount * vertexStride);
                tessa.angelica$getByteBuffer().position(0);
                tessa.angelica$getByteBuffer().limit(vcount * (vertexStride * 4));
                voffset += vcount;

                if (tess.hasTexture) {
                    tessa.angelica$getFloatBuffer().position(3);
                    GL11.glTexCoordPointer(2, (vertexStride * 4), tessa.angelica$getFloatBuffer());
                    GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                }
                if (tess.hasBrightness) {
                    OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
                    tessa.angelica$getShortBuffer().position(6 * 2);
                    GL11.glTexCoordPointer(2, (vertexStride * 4), tessa.angelica$getShortBuffer());
                    GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                    OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
                }
                if (tess.hasColor) {
                    tessa.angelica$getByteBuffer().position(5 * 4);
                    GL11.glColorPointer(4, true, (vertexStride * 4), tessa.angelica$getByteBuffer());
                    GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
                }
                if (tess.hasNormals) {
                    tessa.angelica$getFloatBuffer().position(9);
                    GL11.glNormalPointer((vertexStride * 4), tessa.angelica$getFloatBuffer());
                    GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
                }
                tessa.angelica$getFloatBuffer().position(0);
                GL11.glVertexPointer(3, (vertexStride * 4), tessa.angelica$getFloatBuffer());
                ShadersTess.preDrawArray(tess);

                GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
                GL11.glDrawArrays(realDrawMode, 0, vcount);
            }
            // end loop

            GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            ShadersTess.postDrawArray(tess);
            if (tess.hasTexture) {
                GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            }
            if (tess.hasBrightness) {
                OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
                GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
            }
            if (tess.hasColor) {
                GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
            }
            if (tess.hasNormals) {
                GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
            }

            int n = tess.rawBufferIndex * 4;
            tess.reset();
            return n;
        }
    }

    public static void preDrawArray(Tessellator tess) {
        final TessellatorAccessor tessa = (TessellatorAccessor) tess;
        // Shaders.checkGLError("preDrawArray");
        if (Shaders.useMultiTexCoord3Attrib && tess.hasTexture) {
            GL13.glClientActiveTexture(GL13.GL_TEXTURE3);
            GL11.glTexCoordPointer(2, (vertexStride * 4), (FloatBuffer) tessa.angelica$getFloatBuffer().position(12));
            GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
        }
        if (Shaders.useMidTexCoordAttrib && tess.hasTexture) {
            glVertexAttribPointerARB(
                    Shaders.midTexCoordAttrib,
                    2,
                    false,
                    vertexStride * 4,
                    (FloatBuffer) tessa.angelica$getFloatBuffer().position(12));
            glEnableVertexAttribArrayARB(Shaders.midTexCoordAttrib);
        }
        if (Shaders.useEntityAttrib) {
            glVertexAttribPointerARB(
                    Shaders.entityAttrib,
                    3,
                    false,
                    false,
                    vertexStride * 4,
                    (ShortBuffer) tessa.angelica$getShortBuffer().position(7 * 2));
            glEnableVertexAttribArrayARB(Shaders.entityAttrib);
        }
        // Shaders.checkGLError("preDrawArray");
    }

    public static void preDrawArrayVBO(Tessellator tess) {
        // Shaders.checkGLError("preDrawArray");
        if (Shaders.useMultiTexCoord3Attrib && tess.hasTexture) {
            GL13.glClientActiveTexture(GL13.GL_TEXTURE3);
            GL11.glTexCoordPointer(2, GL11.GL_FLOAT, (vertexStride * 4), 12 * 4L);
            GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
        }
        if (Shaders.useMidTexCoordAttrib && tess.hasTexture) {
            glVertexAttribPointerARB(Shaders.midTexCoordAttrib, 2, GL11.GL_FLOAT, false, vertexStride * 4, 12 * 4L);
            glEnableVertexAttribArrayARB(Shaders.midTexCoordAttrib);
        }
        if (Shaders.useEntityAttrib) {
            glVertexAttribPointerARB(Shaders.entityAttrib, 3, GL11.GL_SHORT, false, vertexStride * 4, 7 * 4L);
            glEnableVertexAttribArrayARB(Shaders.entityAttrib);
        }
        // Shaders.checkGLError("preDrawArray");
    }

    public static void postDrawArray(Tessellator tess) {
        // Shaders.checkGLError("postDrawArray");
        if (Shaders.useEntityAttrib) {
            glDisableVertexAttribArrayARB(Shaders.entityAttrib);
        }
        if (Shaders.useMidTexCoordAttrib && tess.hasTexture) {
            glDisableVertexAttribArrayARB(Shaders.midTexCoordAttrib);
        }
        if (Shaders.useMultiTexCoord3Attrib && tess.hasTexture) {
            GL13.glClientActiveTexture(GL13.GL_TEXTURE3);
            GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
        }
        // Shaders.checkGLError("postDrawArray");
    }

    public static void addVertex(Tessellator tess, double parx, double pary, double parz) {
        final TessellatorAccessor tessa = (TessellatorAccessor) (Object) tess;
        int[] rawBuffer = tess.rawBuffer;
        int rbi = tess.rawBufferIndex;
        float fx = (float) (parx + tess.xOffset);
        float fy = (float) (pary + tess.yOffset);
        float fz = (float) (parz + tess.zOffset);
        // Check if buffer is nearly full
        if (rbi >= tess.bufferSize - vertexStride * 4) {
            if (tess.bufferSize >= 0x1000000) {
                // Max size reached. Just draw.
                if (tess.addedVertices % 4 == 0) {
                    tess.draw();
                    tess.isDrawing = true;
                }
            } else if (tess.bufferSize > 0) {
                // Expand
                tess.bufferSize *= 2;
                tess.rawBuffer = rawBuffer = Arrays.copyOf(tess.rawBuffer, tess.bufferSize);
                AngelicaTweaker.LOGGER.trace("Expand tesselator buffer {}", tess.bufferSize);
            } else {
                // Initialize
                tess.bufferSize = 0x10000;
                tess.rawBuffer = rawBuffer = new int[tess.bufferSize];
            }
        }

        // shaders mod calculate normal and mid UV
        if (tess.drawMode == 7) {
            int i = tess.addedVertices % 4;
            float[] vertexPos = tessa.angelica$getVertexPos();
            vertexPos[i * 4 + 0] = fx;
            vertexPos[i * 4 + 1] = fy;
            vertexPos[i * 4 + 2] = fz;
            if (i == 3) {
                // calculate normal
                float x1 = vertexPos[8 + 0] - vertexPos[+0];
                float y1 = vertexPos[8 + 1] - vertexPos[+1];
                float z1 = vertexPos[8 + 2] - vertexPos[+2];
                float x2 = vertexPos[12 + 0] - vertexPos[4 + 0];
                float y2 = vertexPos[12 + 1] - vertexPos[4 + 1];
                float z2 = vertexPos[12 + 2] - vertexPos[4 + 2];
                float vnx = y1 * z2 - y2 * z1;
                float vny = z1 * x2 - z2 * x1;
                float vnz = x1 * y2 - x2 * y1;
                float lensq = vnx * vnx + vny * vny + vnz * vnz;
                float mult = (lensq != 0.0) ? (float) (1f / Math.sqrt(lensq)) : 1f;
                float normalX = vnx * mult;
                float normalY = vny * mult;
                float normalZ = vnz * mult;
                tessa.angelica$setNormalX(normalX);
                tessa.angelica$setNormalY(normalY);
                tessa.angelica$setNormalZ(normalZ);
                rawBuffer[rbi + (9 - vertexStride * 3)] = rawBuffer[rbi + (9 - vertexStride * 2)] = rawBuffer[rbi
                        + (9 - vertexStride * 1)] = Float.floatToRawIntBits(normalX);
                rawBuffer[rbi + (10 - vertexStride * 3)] = rawBuffer[rbi + (10 - vertexStride * 2)] = rawBuffer[rbi
                        + (10 - vertexStride * 1)] = Float.floatToRawIntBits(normalY);
                rawBuffer[rbi + (11 - vertexStride * 3)] = rawBuffer[rbi + (11 - vertexStride * 2)] = rawBuffer[rbi
                        + (11 - vertexStride * 1)] = Float.floatToRawIntBits(normalZ);
                tess.hasNormals = true;
                // mid UV
                tessa.angelica$setMidTextureU(
                        (Float.intBitsToFloat(rawBuffer[rbi + (3 - vertexStride * 3)])
                                + Float.intBitsToFloat(rawBuffer[rbi + (3 - vertexStride * 2)])
                                + Float.intBitsToFloat(rawBuffer[rbi + (3 - vertexStride * 1)])
                                + (float) tess.textureU) / 4);
                tessa.angelica$setMidTextureV(
                        (Float.intBitsToFloat(rawBuffer[rbi + (4 - vertexStride * 3)])
                                + Float.intBitsToFloat(rawBuffer[rbi + (4 - vertexStride * 2)])
                                + Float.intBitsToFloat(rawBuffer[rbi + (4 - vertexStride * 1)])
                                + (float) tess.textureV) / 4);
                rawBuffer[rbi + (12 - vertexStride * 3)] = rawBuffer[rbi + (12 - vertexStride * 2)] = rawBuffer[rbi
                        + (12 - vertexStride * 1)] = Float.floatToRawIntBits(tessa.angelica$getMidTextureU());
                rawBuffer[rbi + (13 - vertexStride * 3)] = rawBuffer[rbi + (13 - vertexStride * 2)] = rawBuffer[rbi
                        + (13 - vertexStride * 1)] = Float.floatToRawIntBits(tessa.angelica$getMidTextureV());
            }
        }
        // end normal and mid UV calculation

        ++tess.addedVertices;
        rawBuffer[rbi + 0] = Float.floatToRawIntBits((float) fx);
        rawBuffer[rbi + 1] = Float.floatToRawIntBits((float) fy);
        rawBuffer[rbi + 2] = Float.floatToRawIntBits((float) fz);
        rawBuffer[rbi + 3] = Float.floatToRawIntBits((float) tess.textureU);
        rawBuffer[rbi + 4] = Float.floatToRawIntBits((float) tess.textureV);
        rawBuffer[rbi + 5] = tess.color;
        rawBuffer[rbi + 6] = tess.brightness;
        rawBuffer[rbi + 7] = Shaders.getEntityData();
        rawBuffer[rbi + 8] = Shaders.getEntityData2();
        rawBuffer[rbi + 9] = Float.floatToRawIntBits((float) tessa.angelica$getNormalX());
        rawBuffer[rbi + 10] = Float.floatToRawIntBits((float) tessa.angelica$getNormalY());
        rawBuffer[rbi + 11] = Float.floatToRawIntBits((float) tessa.angelica$getNormalZ());
        rawBuffer[rbi + 12] = Float.floatToRawIntBits((float) tessa.angelica$getMidTextureU());
        rawBuffer[rbi + 13] = Float.floatToRawIntBits((float) ((TessellatorAccessor) tess).angelica$getMidTextureV());

        tess.rawBufferIndex = rbi += vertexStride;
        ++tess.vertexCount;
    }
}
