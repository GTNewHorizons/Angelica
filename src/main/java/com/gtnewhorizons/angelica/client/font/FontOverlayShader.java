package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizon.gtnhlib.client.renderer.shader.AutoShaderUpdater;
import com.gtnewhorizon.gtnhlib.client.renderer.shader.ShaderProgram;
import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.Minecraft;

public final class FontOverlayShader extends ShaderProgram {

    private static FontOverlayShader INSTANCE;

    private static int mvpMatrixLocation;
    private static int uTexelSize;
    private static int uTime;

    private static long startTime;

    public FontOverlayShader() {
        super(AngelicaMod.MOD_ID, "shaders/font/fontOutline.vsh", "shaders/font/fontOutline.fsh");
        super.use();
        this.bindTextureSlot("textFBO", 0);
        this.bindTextureSlot("sceneFBO", 1);
        clear();
        mvpMatrixLocation = this.getUniformLocation("u_MVPMatrix");
        uTexelSize = this.getUniformLocation("uTexelSize");
        uTime = this.getUniformLocation("uTime");
        AutoShaderUpdater.getInstance().registerShaderReload(
            this,
            AngelicaMod.MOD_ID,
            "shaders/font/fontOutline.vsh", "shaders/font/fontOutline.fsh",
            (program, vert, frag) -> {

                mvpMatrixLocation = program.getUniformLocation("u_MVPMatrix");
                uTexelSize = this.getUniformLocation("uTexelSize");
                uTime = this.getUniformLocation("uTime");
                program.use();
                this.bindTextureSlot("textFBO", 0);
                this.bindTextureSlot("sceneFBO", 1);
                clear();
            }
            );

        startTime = System.currentTimeMillis();
    }

    public static FontOverlayShader getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new FontOverlayShader();
        }
        return INSTANCE;
    }


    @Override
    public void use() {
        super.use();
        GLStateManager.uploadMVPMatrix(mvpMatrixLocation);
        final Minecraft mc = Minecraft.getMinecraft();
        GLStateManager.glUniform2f(uTexelSize, 1f / mc.displayWidth, 1f / mc.displayHeight);

        GLStateManager.glUniform1f(uTime, (System.currentTimeMillis() - startTime) / 1000f);
    }
}
