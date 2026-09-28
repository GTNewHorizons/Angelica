package com.gtnewhorizons.angelica.compat.thaumcraft;

import com.gtnewhorizons.angelica.glsm.DisplayListManager;
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
        GLStateManager.glNewList(capture.list, GL11.GL_COMPILE);
        return capture;
    }

    public static void finish(Capture capture) {
        GLStateManager.glEndList();
        pending.add(capture);
    }

    public static void abort(Capture capture) {
        if (DisplayListManager.isRecording()) GLStateManager.glEndList();
        GLStateManager.glDeleteLists(capture.list, 1);
    }

    public static void discard() {
        for (Capture capture : pending) GLStateManager.glDeleteLists(capture.list, 1);
        pending.clear();
    }

    public static void render(WorldRenderingPipeline pipeline) {
        if (pending.isEmpty()) return;
        final WorldRenderingPhase phase = pipeline.getPhase();
        final Matrix4f texture = new Matrix4f(GLStateManager.getTextures().getTextureUnitMatrix(0));
        final Matrix4f lightmap = new Matrix4f(GLStateManager.getTextures().getTextureUnitMatrix(1));
        final float brightnessX = GLStateManager.ctx().lastBrightnessX;
        final float brightnessY = GLStateManager.ctx().lastBrightnessY;
        final Boolean translucency = GbufferPrograms.beginTranslucencyDeclaration(true);
        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glPushMatrix();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glPushMatrix();
        ItemIdManager.pushItemId();
        try {
            pipeline.setPhase(WorldRenderingPhase.HAND_TRANSLUCENT);
            GbufferPrograms.setBlockEntityDefaults();
            GLStateManager.enableDepthTest();
            GLStateManager.glDepthMask(false);
            for (Capture capture : pending) capture.replay();
        } finally {
            ItemIdManager.popItemId();
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
            GLStateManager.glPopMatrix();
            GLStateManager.setTextureMatrix(0, texture);
            GLStateManager.setTextureMatrix(1, lightmap);
            GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
            GLStateManager.glPopMatrix();
            GLStateManager.glPopAttrib();
            GLStateManager.setLightmapTextureCoords(GL13.GL_TEXTURE1, brightnessX, brightnessY);
            GbufferPrograms.endTranslucencyDeclaration(translucency);
            pipeline.setPhase(phase);
            discard();
        }
    }

    public static final class Capture {
        private final int list = GLStateManager.glGenLists(1);
        private final Matrix4f modelView = new Matrix4f(GLStateManager.getModelViewMatrix());
        private final Matrix4f projection = new Matrix4f(GLStateManager.getProjectionMatrix());
        private final Matrix4f texture = new Matrix4f(GLStateManager.getTextures().getTextureUnitMatrix(0));
        private final Matrix4f lightmapMatrix = new Matrix4f(GLStateManager.getTextures().getTextureUnitMatrix(1));
        private final float brightnessX = GLStateManager.ctx().lastBrightnessX;
        private final float brightnessY = GLStateManager.ctx().lastBrightnessY;
        private final Vector3f normal = new Vector3f(ShaderManager.getCurrentNormal());
        private final AlphaState alphaState = GLStateManager.getAlphaState().readEffective(new AlphaState());
        private final int depthFunction = GLStateManager.getDepthState().getFunc();
        private final int itemId = ItemIdManager.getItemId();
        private final boolean cull = GLStateManager.getCullState().isEnabled();
        private final boolean alpha = GLStateManager.getAlphaTest().isEffectivelyEnabled();
        private final boolean lightmap = GLStateManager.getTextures().getTextureUnitStates(1).isEnabled();
        private final boolean lighting = GLStateManager.getLightingState().isEnabled();

        private void replay() {
            GLStateManager.setProjectionMatrix(projection);
            GLStateManager.setModelViewMatrix(modelView);
            GLStateManager.setTextureMatrix(0, texture);
            GLStateManager.setTextureMatrix(1, lightmapMatrix);
            GLStateManager.setLightmapTextureCoords(GL13.GL_TEXTURE1, brightnessX, brightnessY);
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
            GLStateManager.glNormal3f(normal.x, normal.y, normal.z);
            GLStateManager.glDepthFunc(depthFunction);
            GLStateManager.glAlphaFunc(alphaState.getFunction(), alphaState.getReference());
            ItemIdManager.setItemIdRaw(itemId);
            if (cull) GLStateManager.enableCull(); else GLStateManager.disableCull();
            if (alpha) GLStateManager.enableAlphaTest(); else GLStateManager.disableAlphaTest();
            if (lighting) GLStateManager.enableLighting(); else GLStateManager.disableLighting();
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            if (lightmap) GLStateManager.enableTexture(); else GLStateManager.disableTexture();
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.enableTexture();
            GLStateManager.glCallList(list);
        }
    }
}
