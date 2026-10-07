package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.PerFrameUniformBlock;
import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess;
import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess.Edit;
import com.gtnewhorizons.angelica.glsm.shader.ShaderIndex;
import org.lwjgl.opengl.GL20;
import org.taumc.glsl.grammar.GLSLParser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

final class ShaderTransformChain {

    private ShaderTransformChain() {}

    static String run(String source, int glShaderType) {
        final GlslVulkanPreprocess.Result pre = GlslVulkanPreprocess.run(source, glShaderType, "test", true);
        String src = pre != null ? pre.rewrittenSource() : source;
        if (glShaderType == GL20.GL_VERTEX_SHADER) {
            src = clipZ(src);
        }
        src = stripUnused(src);
        if (glShaderType == GL20.GL_VERTEX_SHADER || glShaderType == GL20.GL_FRAGMENT_SHADER) {
            src = inject(src, GLSMHooks.perFrameUniformBlock, GLSMHooks.perPassUniformBlock);
        }
        return src;
    }

    static String clipZ(String source) {
        return edit(source, (index, edits) -> ClipZRemap.collectEdits(index.root(), edits));
    }

    static String stripUnused(String source) {
        if (!source.contains("sampler")) return source;
        return edit(source, SamplerStripper::collectEdits);
    }

    static String inject(String source, PerFrameUniformBlock perFrame, PerFrameUniformBlock perPass) {
        return edit(source, (index, edits) -> PerFrameBlockInjector.collectEdits(index, perFrame, perPass, edits));
    }

    private static String edit(String source, BiConsumer<ShaderIndex, List<Edit>> collect) {
        final GLSLParser.Translation_unitContext root;
        try {
            root = GlslTransformUtils.parseFullQuiet(source);
        } catch (Exception e) {
            return source;
        }
        final List<Edit> edits = new ArrayList<>();
        collect.accept(new ShaderIndex(root, source), edits);
        return edits.isEmpty() ? source : GlslVulkanPreprocess.applyEdits(source, edits);
    }
}
