package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.api.tesr.TesrMeshProvider;
import com.gtnewhorizons.angelica.api.tesr.TesrMeshSink;
import com.gtnewhorizons.angelica.common.TileTesrTranslucentTest;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.utils.WorkaroundUtils;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.opengl.GL11;

public final class TesrTranslucentTestRenderers {

    private static final double HALF = 0.4375;

    private TesrTranslucentTestRenderers() {}

    private static void pane(Tessellator t, double half) {
        rect(t, -half, -half, half, half);
    }

    private static void rect(Tessellator t, double x0, double y0, double x1, double y1) {
        t.setNormal(0f, 0f, -1f);
        t.addVertexWithUV(0.5 + x0, 0.5 + y0, 0.5, 0, 1);
        t.addVertexWithUV(0.5 + x0, 0.5 + y1, 0.5, 0, 0);
        t.addVertexWithUV(0.5 + x1, 0.5 + y1, 0.5, 1, 0);
        t.addVertexWithUV(0.5 + x1, 0.5 + y0, 0.5, 1, 1);
    }

    private static void drawPane(double half) {
        final Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        pane(t, half);
        t.draw();
    }

    private static void apply(TesrMaterial material) {
        if (material.transparency() == TesrMaterial.Transparency.ADDITIVE) {
            GLSMBlend.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
        } else {
            GLSMBlend.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
        GLSMCurrentAttribs.glColor4f(material.colorRed(), material.colorGreen(), material.colorBlue(), material.colorAlpha());
    }

    private abstract static class PaneRenderer extends TileEntitySpecialRenderer {

        void begin(double x, double y, double z) {
            bindTexture(WorkaroundUtils.GL_TEXTURE_2D_WORKAROUND);
            GLSMMatrices.glPushMatrix();
            GLSMMatrices.glTranslated(x, y, z);
            GLSMEnable.glDisable(GL11.GL_CULL_FACE);
            GLSMEnable.glEnable(GL11.GL_BLEND);
        }

        static void end() {
            GLSMCurrentAttribs.glColor4f(1f, 1f, 1f, 1f);
            GLSMBlend.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GLSMEnable.glDisable(GL11.GL_BLEND);
            GLSMEnable.glEnable(GL11.GL_CULL_FACE);
            GLSMMatrices.glPopMatrix();
        }
    }

    public static final class Provider extends PaneRenderer implements TesrMeshProvider {

        private static final TesrMaterial[] MATERIALS = {
            TesrMaterial.builder().color(1f, 0f, 0f, 0.5f).translucent().noCull().build(),
            TesrMaterial.builder().color(0.5f, 0f, 0.5f, 1f).additive().noCull().build() };
        private static final Object[] KEYS = { new Object(), new Object() };

        private static int kind(TileEntity te) {
            return ((TileTesrTranslucentTest) te).variant() == 1 ? 1 : 0;
        }

        @Override
        public Object angelica$meshKey(TileEntity te) {
            return KEYS[kind(te)];
        }

        @Override
        public void angelica$build(TesrMeshSink sink, TileEntity te) {
            pane(sink.angelica$bucket(DefaultVertexFormat.POSITION_TEXTURE_NORMAL, WorkaroundUtils.GL_TEXTURE_2D_WORKAROUND, MATERIALS[kind(te)]), HALF);
        }

        @Override
        public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
            begin(x, y, z);
            apply(MATERIALS[kind(te)]);
            drawPane(HALF);
            end();
        }
    }

    public static final class Model extends PaneRenderer {

        private static final TesrMaterial TRANSLUCENT_GREEN = TesrMaterial.builder().color(0f, 1f, 0f, 0.5f).translucent().build();
        private static final TesrMaterial TRANSLUCENT_YELLOW = TesrMaterial.builder().color(1f, 1f, 0f, 0.5f).translucent().build();
        private static final TesrMaterial TRANSLUCENT_MAGENTA = TesrMaterial.builder().color(1f, 0f, 1f, 0.5f).translucent().build();
        private static final TesrMaterial ADDITIVE_CYAN = TesrMaterial.builder().color(0f, 0.6f, 0.6f, 1f).additive().build();

        private final ModelBase model = new ModelBase() {};
        private final ModelRenderer base = new ModelRenderer(model, 0, 0);
        private final ModelRenderer pane = paneAt(0f);
        private final ModelRenderer paneFar = paneAt(0.16f);
        private final ModelRenderer paneNear = paneAt(-0.16f);

        public Model() {
            base.addBox(-1f, 7f, -1f, 2, 1, 2);
        }

        private ModelRenderer paneAt(float z) {
            final ModelRenderer renderer = new ModelRenderer(model, 0, 0);
            renderer.addBox(-7f, -7f, z, 14, 14, 0);
            return renderer;
        }

        @Override
        public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
            final float scale = 1f / 16f;
            bindTexture(WorkaroundUtils.GL_TEXTURE_2D_WORKAROUND);
            GLSMMatrices.glPushMatrix();
            GLSMMatrices.glTranslated(x + 0.5, y + 0.5, z + 0.5);
            GLSMCurrentAttribs.glColor4f(1f, 1f, 1f, 1f);
            base.render(scale);
            GLSMEnable.glEnable(GL11.GL_BLEND);
            switch (((TileTesrTranslucentTest) te).variant()) {
                case 0 -> {
                    apply(TRANSLUCENT_GREEN);
                    pane.render(scale);
                }
                case 1 -> {
                    apply(TRANSLUCENT_YELLOW);
                    GLSMMatrices.glTranslated(-0.5, -0.5, -0.5);
                    drawPane(HALF);
                }
                default -> {
                    apply(TRANSLUCENT_MAGENTA);
                    paneFar.render(scale);
                    apply(ADDITIVE_CYAN);
                    pane.render(scale);
                    apply(TRANSLUCENT_GREEN);
                    paneNear.render(scale);
                }
            }
            end();
        }
    }

    public static final class Decal extends PaneRenderer implements TesrMeshProvider {

        private static final TesrMaterial WRITER = TesrMaterial.builder().color(1f, 1f, 1f, 0.5f).translucent().noCull().build();
        private static final TesrMaterial DECAL = TesrMaterial.builder().color(0f, 0f, 0f, 0.8f).translucent().depthEqual().noCull().build();
        private static final Object KEY = new Object();
        private static final double INNER = HALF / 2;

        private static void writer(Tessellator t) {
            rect(t, -HALF, INNER, HALF, HALF);
            rect(t, -HALF, -HALF, HALF, -INNER);
            rect(t, -HALF, -INNER, -INNER, INNER);
            rect(t, INNER, -INNER, HALF, INNER);
            pane(t, INNER);
        }

        @Override
        public Object angelica$meshKey(TileEntity te) {
            return KEY;
        }

        @Override
        public void angelica$build(TesrMeshSink sink, TileEntity te) {
            writer(sink.angelica$bucket(DefaultVertexFormat.POSITION_TEXTURE_NORMAL, WorkaroundUtils.GL_TEXTURE_2D_WORKAROUND, WRITER));
            pane(sink.angelica$bucket(DefaultVertexFormat.POSITION_TEXTURE_NORMAL, WorkaroundUtils.GL_TEXTURE_2D_WORKAROUND, DECAL), INNER);
        }

        @Override
        public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
            begin(x, y, z);
            apply(WRITER);
            final Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            writer(t);
            t.draw();
            GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_EQUAL);
            apply(DECAL);
            drawPane(INNER);
            GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LEQUAL);
            end();
        }
    }

    public static final class Glow extends PaneRenderer {

        private static final TesrMaterial TRANSLUCENT_ORANGE = TesrMaterial.builder().color(1f, 0.5f, 0f, 0.6f).translucent().build();

        @Override
        public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
            begin(x, y, z);
            apply(TRANSLUCENT_ORANGE);
            GLSMDepthStencilAlpha.glDepthMask(false);
            drawPane(HALF);
            GLSMDepthStencilAlpha.glDepthMask(true);
            end();
        }
    }
}
