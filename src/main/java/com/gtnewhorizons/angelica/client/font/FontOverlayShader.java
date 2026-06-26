package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizon.gtnhlib.client.renderer.MatrixHelper;
import com.gtnewhorizon.gtnhlib.client.renderer.postprocessing.CustomFramebuffer;
import com.gtnewhorizon.gtnhlib.client.renderer.shader.IShaderDefinesInjector;
import com.gtnewhorizon.gtnhlib.client.renderer.shader.ShaderProgram;
import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.utils.InstancedHelper;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.ClientProxy.mc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutFloat;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.nmemFree;

//TODO move this + shader to gtnhlib
public final class FontOverlayShader extends ShaderProgram {

    private int mvpMatrixLocation;
    private int uTexelSize;
    private int uScale;
    private int uTime;
    private int uTexBounds;
    private final int padding;

    public float xStart;
    public float xEnd;
    public float yStart;
    public float yEnd;

    private static CustomFramebuffer framebuffer;
    private static int vao;
    private static int vbo;
    private static ByteBuffer buffer;

//    public static FontOverlayShader TEMPLATE = new FontOverlayShader(
//        new ResourceLocation(AngelicaMod.MOD_ID, "shaders/font/fontShaderTemplate.fsh")
//    );

    public FontOverlayShader(ResourceLocation fragShader, IShaderDefinesInjector... defines) {
        this(fragShader, 4, defines);
    }

    public FontOverlayShader(ResourceLocation fragShader, int padding, IShaderDefinesInjector... defines) {
        super(
            loadShaderSource(getVertexShader()),
            loadShaderSource(fragShader, defines)
        );
        this.padding = padding;
        this.bindTextureSlots("textFBO", "sceneFBO");
        mvpMatrixLocation = this.getUniformLocation("u_MVPMatrix");
        uTexelSize = this.getUniformLocation("uTexelSize");
        uScale = this.getUniformLocation("uScale");
        uTime = this.getUniformLocation("uTime");
        uTexBounds = this.getUniformLocation("uTexBounds");
    }

    public static ResourceLocation getVertexShader() {
        return new ResourceLocation(AngelicaMod.MOD_ID, "shaders/font/fontShader.vsh");
    }

    public boolean begin(BatchingFontRenderer fontRenderer) {
        fontRenderer.flushBatch();
        xStart = -1;
        return true;
    }

    public void updateBounds(float x, float y, float width, float height) {
        if (xStart == -1) {
            xStart = x;
            yStart = y;
        }
        xEnd = x + width;
        yEnd = y + height;
    }

    public boolean end(BatchingFontRenderer fontRenderer) {
        if (framebuffer == null) {
            framebuffer = new CustomFramebuffer(0);
        }
        if (framebuffer.framebufferWidth != mc.displayWidth || framebuffer.framebufferHeight != mc.displayHeight) {
            framebuffer.createBindFramebuffer(mc.displayWidth, mc.displayHeight);
        }
        framebuffer.clearBindFramebuffer();
        fontRenderer.flushBatch();
        framebuffer.unbindFramebuffer();

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, mc.getFramebuffer().framebufferTexture);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        framebuffer.bindFramebufferTexture();

        final int padding = this.padding;
        this.use();

        if (vao == 0) {
            buffer = memAlloc(32);
            vao = GLStateManager.glGenVertexArrays();
            GLStateManager.glBindVertexArray(vao);
            vbo = GLStateManager.glGenBuffers();

            GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, InstancedHelper.getQuadEBO());

            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

            // position
            GLStateManager.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 8, 0);
            GLStateManager.glEnableVertexAttribArray(0);
        }

        long address = memAddress0(buffer);
        buffer.limit(32);
        addVertex(address, xStart - padding, yStart - padding);
        addVertex(address + 8, xEnd + padding, yStart - padding);
        addVertex(address + 16, xStart - padding, yEnd + padding);
        addVertex(address + 24, xEnd + padding, yEnd + padding);

        Matrix4f mvp = GLStateManager.getMVPMatrix(new Matrix4f());
        Vector4f temp = new Vector4f();
        temp.x = xStart;
        temp.y = yEnd;
        temp.z = 0;
        temp.w = 1;
        MatrixHelper.transformVertex(mvp, temp);

        float boundXStart = temp.x * 0.5f + 0.5f;
        float boundYStart = temp.y * 0.5f + 0.5f;

        temp.x = xEnd;
        temp.y = yStart;
        temp.z = 0;
        temp.w = 1;
        MatrixHelper.transformVertex(mvp, temp);

        float boundXEnd = temp.x * 0.5f + 0.5f;
        float boundYEnd = temp.y * 0.5f + 0.5f;

        this.uploadBounds(boundXStart, boundXEnd, boundYStart, boundYEnd);

        GLStateManager.glBindVertexArray(vao);

        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STREAM_DRAW);
        GLStateManager.glDrawElements(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_SHORT, 0);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        GLStateManager.glUseProgram(0);
        GLStateManager.glBindVertexArray(0);

        nmemFree(address);

        return false;
    }

    private void addVertex(long ptr, float x, float y) {
        // v, v
        memPutFloat(ptr, x);
        memPutFloat(ptr + 4, y);
    }


    @Override
    public void use() {
        super.use();
        GLStateManager.uploadMVPMatrix(mvpMatrixLocation);
        if (uTexelSize != -1) {
            GLStateManager.glUniform2f(uTexelSize, 1f / mc.displayWidth, 1f / mc.displayHeight);
        }

        this.uploadTime(uTime);

        if (uScale != -1) {
            final ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            GLStateManager.glUniform1i(uScale, res.getScaleFactor());
        }
    }

    private void uploadBounds(float minX, float maxX, float minY, float maxY) {
        GLStateManager.glUniform4f(uTexBounds, minX, maxX, minY, maxY);
    }

    public static final class Builder implements IShaderDefinesInjector {

        @Override
        public void writeDefines(StringBuilder out) {

        }
    }
}
