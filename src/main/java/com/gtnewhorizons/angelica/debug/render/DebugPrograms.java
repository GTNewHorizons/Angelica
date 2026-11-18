package com.gtnewhorizons.angelica.debug.render;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;

public final class DebugPrograms {

    private static DebugProgram color;
    private static DebugProgram textured;
    private static DebugProgram lines;

    private DebugPrograms() {}

    public static DebugProgram color() {
        if (color == null) color = link("angelica:debug_color", "angelica:debug.vert", ShaderConstants.EMPTY, false, false);
        return color;
    }

    public static DebugProgram textured() {
        if (textured == null) textured = link("angelica:debug_textured", "angelica:debug.vert", ShaderConstants.builder().add("TEXTURED").build(), true, false);
        return textured;
    }

    public static DebugProgram lines() {
        if (lines == null) lines = link("angelica:debug_lines", "angelica:debug_lines.vert", ShaderConstants.EMPTY, false, true);
        return lines;
    }

    private static DebugProgram link(String name, String vertexSource, ShaderConstants constants, boolean texCoord, boolean otherEnd) {
        final int count = 2 + (texCoord ? 1 : 0) + (otherEnd ? 1 : 0);
        final String[] attributes = new String[count];
        final int[] locations = new int[count];
        attributes[0] = "a_Position";
        locations[0] = VertexFormatElement.Usage.POSITION.getAttributeLocation();
        attributes[1] = "a_Color";
        locations[1] = VertexFormatElement.Usage.COLOR.getAttributeLocation();
        int next = 2;
        if (texCoord) {
            attributes[next] = "a_TexCoord0";
            locations[next++] = VertexFormatElement.Usage.PRIMARY_UV.getAttributeLocation();
        }
        if (otherEnd) {
            attributes[next] = "a_LineOtherEnd";
            locations[next] = VertexFormatElement.Usage.NORMAL.getAttributeLocation();
        }
        return new DebugProgram(link(name, vertexSource, "angelica:debug.frag", constants, attributes, locations));
    }

    public static int link(String name, String vertexSource, String fragmentSource, ShaderConstants constants, String[] attributes, int[] locations) {
        final GlShader vert = ShaderLoader.loadShader(ShaderType.VERTEX, vertexSource, constants);
        final GlShader frag = ShaderLoader.loadShader(ShaderType.FRAGMENT, fragmentSource, constants);
        try {
            final GlProgram.Builder builder = GlProgram.builder(name).attachShader(vert).attachShader(frag);
            for (int i = 0; i < attributes.length; i++) builder.bindAttribute(attributes[i], locations[i]);
            final GlProgram<Void> program = builder.link(ctx -> null);
            return program.handle();
        } finally {
            vert.delete();
            frag.delete();
        }
    }
}
