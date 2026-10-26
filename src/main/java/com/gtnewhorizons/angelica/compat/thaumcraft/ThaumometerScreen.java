package com.gtnewhorizons.angelica.compat.thaumcraft;

import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMLighting;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.states.AlphaState;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.uniforms.ItemIdManager;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.ArrayList;
import java.util.List;

/** The held scanner's pane, icons and labels must follow translucent world geometry. */
public final class ThaumometerScreen {
    private static final List<Capture> pending = new ArrayList<>();

    private ThaumometerScreen() {}

    public static Capture begin() {
        if (DisplayListManager.isRecording()) return null;
        final Capture capture = new Capture();
        GLSMDraws.glNewList(capture.list, GL11.GL_COMPILE);
        return capture;
    }

    public static void finish(Capture capture) {
        GLSMDraws.glEndList();
        pending.add(capture);
    }

    public static void abort(Capture capture) {
        if (DisplayListManager.isRecording()) GLSMDraws.glEndList();
        GLSMDraws.glDeleteLists(capture.list, 1);
    }

    public static void discard() {
        for (Capture capture : pending) GLSMDraws.glDeleteLists(capture.list, 1);
        pending.clear();
    }

    public static void render(WorldRenderingPipeline pipeline) {
        if (pending.isEmpty()) return;
        final WorldRenderingPhase phase = pipeline.getPhase();
        final Matrix4f texture = new Matrix4f(GLSMTextures.getTextures().getTextureUnitMatrix(0));
        final Matrix4f lightmap = new Matrix4f(GLSMTextures.getTextures().getTextureUnitMatrix(1));
        final float brightnessX = GLStateManager.ctx().lastBrightnessX;
        final float brightnessY = GLStateManager.ctx().lastBrightnessY;
        final Boolean translucency = GbufferPrograms.beginTranslucencyDeclaration(true);
        GLSMAttribStack.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glPushMatrix();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glPushMatrix();
        ItemIdManager.pushItemId();
        try {
            pipeline.setPhase(WorldRenderingPhase.HAND_TRANSLUCENT);
            GbufferPrograms.setBlockEntityDefaults();
            GLSMDepthStencilAlpha.enableDepthTest();
            GLSMDepthStencilAlpha.glDepthMask(false);
            for (Capture capture : pending) capture.replay();
        } finally {
            ItemIdManager.popItemId();
            GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
            GLSMMatrices.glPopMatrix();
            GLSMMatrices.setTextureMatrix(0, texture);
            GLSMMatrices.setTextureMatrix(1, lightmap);
            GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
            GLSMMatrices.glPopMatrix();
            GLSMAttribStack.glPopAttrib();
            GLSMCurrentAttribs.setLightmapTextureCoords(GL13.GL_TEXTURE1, brightnessX, brightnessY);
            GbufferPrograms.endTranslucencyDeclaration(translucency);
            pipeline.setPhase(phase);
            discard();
        }
    }

    public static final class Capture {
        private final int list = GLSMDraws.glGenLists(1);
        private final Matrix4f modelView = new Matrix4f(GLSMMatrices.getModelViewMatrix());
        private final Matrix4f projection = new Matrix4f(GLSMMatrices.getProjectionMatrix());
        private final Matrix4f texture = new Matrix4f(GLSMTextures.getTextures().getTextureUnitMatrix(0));
        private final Matrix4f lightmapMatrix = new Matrix4f(GLSMTextures.getTextures().getTextureUnitMatrix(1));
        private final float brightnessX = GLStateManager.ctx().lastBrightnessX;
        private final float brightnessY = GLStateManager.ctx().lastBrightnessY;
        private final Vector3f normal = new Vector3f(ShaderManager.getCurrentNormal());
        private final AlphaState alphaState = GLSMDepthStencilAlpha.getAlphaState().readEffective(new AlphaState());
        private final int depthFunction = GLSMDepthStencilAlpha.getDepthState().getFunc();
        private final int itemId = ItemIdManager.getItemId();
        private final boolean cull = GLSMRasterizer.getCullState().isEnabled();
        private final boolean alpha = GLSMDepthStencilAlpha.getAlphaTest().isEffectivelyEnabled();
        private final boolean lightmap = GLSMTextures.getTextures().getTextureUnitStates(1).isEnabled();
        private final boolean lighting = GLSMLighting.getLightingState().isEnabled();

        private void replay() {
            GLSMMatrices.setProjectionMatrix(projection);
            GLSMMatrices.setModelViewMatrix(modelView);
            GLSMMatrices.setTextureMatrix(0, texture);
            GLSMMatrices.setTextureMatrix(1, lightmapMatrix);
            GLSMCurrentAttribs.setLightmapTextureCoords(GL13.GL_TEXTURE1, brightnessX, brightnessY);
            GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
            GLSMCurrentAttribs.glNormal3f(normal.x, normal.y, normal.z);
            GLSMDepthStencilAlpha.glDepthFunc(depthFunction);
            GLSMDepthStencilAlpha.glAlphaFunc(alphaState.getFunction(), alphaState.getReference());
            ItemIdManager.setItemIdRaw(itemId);
            if (cull) GLSMRasterizer.enableCull(); else GLSMRasterizer.disableCull();
            if (alpha) GLSMDepthStencilAlpha.enableAlphaTest(); else GLSMDepthStencilAlpha.disableAlphaTest();
            if (lighting) GLSMLighting.enableLighting(); else GLSMLighting.disableLighting();
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
            if (lightmap) GLSMTextures.enableTexture(); else GLSMTextures.disableTexture();
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            GLSMTextures.enableTexture();
            GLSMDraws.glCallList(list);
        }
    }
}
