package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizon.gtnhlib.client.renderer.shader.AutoShaderUpdater;
import com.gtnewhorizon.gtnhlib.client.renderer.shader.IShaderDefinesInjector;
import com.gtnewhorizon.gtnhlib.client.renderer.shader.IShaderReloadRunnable;
import com.gtnewhorizon.gtnhlib.client.renderer.shader.ShaderProgram;
import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.Minecraft;

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
}
