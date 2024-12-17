package me.jellysquid.mods.sodium.client.render.chunk.shader;

import static com.gtnewhorizon.gtnhlib.client.lwjgl3.MemoryStack.stackPush;

import com.gtnewhorizon.gtnhlib.client.lwjgl3.MemoryStack;
import com.gtnewhorizons.angelica.compat.toremove.MatrixStack;
import java.util.function.Function;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gl.shader.GlProgram;
import me.jellysquid.mods.sodium.client.render.GameRendererContext;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

/**
 * A forward-rendering shader program for chunks.
 */
public class ChunkProgram extends GlProgram {
    // Uniform variable binding indexes
    private final int uModelViewProjectionMatrix;
    private final int uModelScale;
    private final int uTextureScale;
    private final int uBlockTex;
    private final int uLightTex;

    // The fog shader component used by this program in order to setup the appropriate GL state
    private final ChunkShaderFogComponent fogShader;

    protected ChunkProgram(RenderDevice owner, ResourceLocation name, int handle, Function<ChunkProgram, ChunkShaderFogComponent> fogShaderFunction) {
        super(owner, name, handle);

        this.uModelViewProjectionMatrix = this.getUniformLocation("u_ModelViewProjectionMatrix");

        this.uBlockTex = this.getUniformLocation("u_BlockTex");
        this.uLightTex = this.getUniformLocation("u_LightTex");
        this.uModelScale = this.getUniformLocation("u_ModelScale");
        this.uTextureScale = this.getUniformLocation("u_TextureScale");

        this.fogShader = fogShaderFunction.apply(this);
    }

    public void setup(MatrixStack matrixStack, float modelScale, float textureScale) {
        if(this.uBlockTex != -1) GL20.glUniform1i(this.uBlockTex, OpenGlHelper.defaultTexUnit - GL13.GL_TEXTURE0);
        if(this.uLightTex != -1) GL20.glUniform1i(this.uLightTex, OpenGlHelper.lightmapTexUnit - GL13.GL_TEXTURE0);

        if(this.uModelScale != -1) GL20.glUniform3f(this.uModelScale, modelScale, modelScale, modelScale);
        if(this.uTextureScale != -1) GL20.glUniform2f(this.uTextureScale, textureScale, textureScale);

        this.fogShader.setup();

        if (this.uModelViewProjectionMatrix == -1) return;
        try (MemoryStack stack = stackPush()) {
            GL20.glUniformMatrix4(
                this.uModelViewProjectionMatrix,
                false,
                GameRendererContext.getModelViewProjectionMatrix(matrixStack.peek(), stack));
        }
    }
}
