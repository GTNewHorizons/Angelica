package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess;
import org.lwjgl.opengl.GL20;

final class ShaderTransformChain {

    private ShaderTransformChain() {}

    static String run(String source, int glShaderType) {
        final GlslVulkanPreprocess.Result pre = GlslVulkanPreprocess.run(source, glShaderType, "test", true);
        String src = pre != null ? pre.rewrittenSource() : source;
        if (glShaderType == GL20.GL_VERTEX_SHADER) {
            src = ClipZRemap.injectGLToVulkanClipZ(src);
        }
        src = SamplerStripper.stripUnused(src);
        if (glShaderType == GL20.GL_VERTEX_SHADER || glShaderType == GL20.GL_FRAGMENT_SHADER) {
            src = PerFrameBlockInjector.inject(src, GLSMHooks.perFrameUniformBlock, GLSMHooks.perPassUniformBlock);
        }
        return src;
    }
}
