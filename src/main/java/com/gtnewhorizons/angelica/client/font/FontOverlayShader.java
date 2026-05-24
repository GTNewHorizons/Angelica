package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizon.gtnhlib.client.renderer.shader.AutoShaderUpdater;
import com.gtnewhorizon.gtnhlib.client.renderer.shader.IShaderDefinesInjector;
import com.gtnewhorizon.gtnhlib.client.renderer.shader.IShaderReloadRunnable;
import com.gtnewhorizon.gtnhlib.client.renderer.shader.ShaderProgram;
import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.Minecraft;

//TODO move this + shader to gtnhlib
public final class FontOverlayShader extends ShaderProgram {

    private static FontOverlayShader INSTANCE;

    private static int mvpMatrixLocation;
    private static int uTexelSize;
    private static int uTime;
    private static int uTexBounds;

    private static long startTime;

    public FontOverlayShader(Builder defines) {
        super(
            loadShaderSource(AngelicaMod.MOD_ID, "shaders/font/fontOutline.vsh"),
            loadShaderSource(AngelicaMod.MOD_ID, "shaders/font/fontOutline.fsh", defines)
        );
        this.bindTextureSlots("textFBO", "sceneFBO");
        mvpMatrixLocation = this.getUniformLocation("u_MVPMatrix");
        uTexelSize = this.getUniformLocation("uTexelSize");
        uTime = this.getUniformLocation("uTime");
        uTexBounds = this.getUniformLocation("uTexBounds");
        AutoShaderUpdater.getInstance().registerShaderReload(
            this,
            AngelicaMod.MOD_ID,
            "shaders/font/fontOutline.vsh", "shaders/font/fontOutline.fsh",
            new IShaderReloadRunnable() {
                @Override
                public void run(ShaderProgram shader) {
                    shader.bindTextureSlots("textFBO", "sceneFBO");
                    mvpMatrixLocation = shader.getUniformLocation("u_MVPMatrix");
                    uTexelSize = shader.getUniformLocation("uTexelSize");
                    uTime = shader.getUniformLocation("uTime");
                    uTexBounds = shader.getUniformLocation("uTexBounds");

                }

                @Override
                public IShaderDefinesInjector[] getDefines() {
                    return new IShaderDefinesInjector[0];
                }
            }
        );

        startTime = System.currentTimeMillis();
    }

    public static FontOverlayShader getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new FontOverlayShader(new FontOverlayShader.Builder());
        }
        return INSTANCE;
    }


    @Override
    public void use() {
        super.use();
        GLStateManager.uploadMVPMatrix(mvpMatrixLocation);
        final Minecraft mc = Minecraft.getMinecraft();
        if (uTexelSize != -1)
            GLStateManager.glUniform2f(uTexelSize, 1f / mc.displayWidth, 1f / mc.displayHeight);

        if (uTime != -1)
            GLStateManager.glUniform1f(uTime, (System.currentTimeMillis() - startTime) / 1000f);
    }

    public void uploadBounds(float minX, float maxX, float minY, float maxY) {
        GLStateManager.glUniform4f(uTexBounds, minX, maxX, minY, maxY);
    }

    public static final class Builder implements IShaderDefinesInjector {

        @Override
        public void writeDefines(StringBuilder out) {

        }
    }


//    private static final class ShaderDrawCmd extends FontDrawCmd {
//
//        private static final CustomFramebuffer framebuffer = new CustomFramebuffer(0);
//        private static int vao;
//        private static int vbo;
//
//        private static long test;
//
//        public float xStart;
//        public float xEnd;
//        public float yStart;
//        public float yEnd;
//
//        @Override
//        public boolean equals(Object obj) {
//            return obj instanceof ShaderDrawCmd && super.equals(obj);
//        }
//
//        @Override
//        public void render() {
//            Minecraft mc = Minecraft.getMinecraft();
//            if (framebuffer.framebufferWidth != mc.displayWidth || framebuffer.framebufferHeight != mc.displayHeight) {
//                framebuffer.createBindFramebuffer(mc.displayWidth, mc.displayHeight);
//            }
//
//            framebuffer.clearBindFramebuffer();
//            super.render();
//            framebuffer.unbindFramebuffer();
//
//            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
//            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, mc.getFramebuffer().framebufferTexture);
//            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
//            framebuffer.bindFramebufferTexture();
//
//            final float padding = getPadding();
//            FontOverlayShader.getInstance().use();
//            GLStateManager.disableCull();
//
//            ByteBuffer buffer = memAlloc(32);
//            long address = memAddress0(buffer);
//            buffer.limit(32);
//            addVertex(address, xStart - padding, yStart - padding);
//            addVertex(address + 8, xEnd + padding, yStart - padding);
//            addVertex(address + 16, xStart - padding, yEnd + padding);
//            addVertex(address + 24, xEnd + padding, yEnd + padding);
//
//            Matrix4f mvp = GLStateManager.getMVPMatrix(new Matrix4f());
//            Vector4f temp = new Vector4f();
//            temp.x = xStart;
//            temp.y = yEnd;
//            temp.z = 0;
//            temp.w = 1;
//            MatrixHelper.transformVertex(mvp, temp);
//
//            float boundXStart = temp.x * 0.5f + 0.5f;
//            float boundYStart = temp.y * 0.5f + 0.5f;
//
//            temp.x = xEnd;
//            temp.y = yStart;
//            temp.z = 0;
//            temp.w = 1;
//            MatrixHelper.transformVertex(mvp, temp);
//
//            float boundXEnd = temp.x * 0.5f + 0.5f;
//            float boundYEnd = temp.y * 0.5f + 0.5f;
//
//            FontOverlayShader.getInstance().uploadBounds(boundXStart, boundXEnd, boundYStart, boundYEnd);
//
//
//            if (vao == 0) {
//                vao = GLStateManager.glGenVertexArrays();
//                GLStateManager.glBindVertexArray(vao);
//                vbo = GL15.glGenBuffers();
//
//                mainEBO.bind();
//
//                GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
//
//                // position
//                GLStateManager.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 8, 0);
//                GLStateManager.glEnableVertexAttribArray(0);
//            }
//
//            GLStateManager.glBindVertexArray(vao);
//
//            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
//            GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STREAM_DRAW);
//            GLStateManager.glDrawElements(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_SHORT, 0);
//            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
//
//            GLStateManager.glUseProgram(fontShaderId);
//            GLStateManager.glBindVertexArray(mainVAO);
//
//            nmemFree(address);
//
//        }
//
//        private void addVertex(long ptr, float x, float y) {
//            // v, v
//            memPutFloat(ptr, x);
//            memPutFloat(ptr + 4, y);
//        }
//
//        private float getPadding() {
//            return 8;
//        }
//    }
}
