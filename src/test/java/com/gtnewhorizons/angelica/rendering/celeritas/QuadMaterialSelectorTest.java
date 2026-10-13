package com.gtnewhorizons.angelica.rendering.celeritas;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevel;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.parameters.AlphaCutoffParameter;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;

class QuadMaterialSelectorTest {

    private final Material[] previous = new Material[3];
    private final Map<TextureAtlasSprite, SpriteTransparencyLevel> levels = new IdentityHashMap<>();
    private final QuadMaterialSelector selector = new QuadMaterialSelector() {
        @Override
        SpriteTransparencyLevel transparencyLevel(TextureAtlasSprite sprite) {
            return levels.get(sprite);
        }
    };

    private Material solid;
    private Material cutout;
    private Material translucent;

    @BeforeEach
    void installMaterials() {
        previous[0] = AngelicaRenderPassConfiguration.SOLID_MATERIAL;
        previous[1] = AngelicaRenderPassConfiguration.CUTOUT_MIPPED_MATERIAL;
        previous[2] = AngelicaRenderPassConfiguration.TRANSLUCENT_MATERIAL;

        solid = new Material(pass("solid", false), AlphaCutoffParameter.ZERO, true);
        cutout = new Material(pass("cutout_mipped", true), AlphaCutoffParameter.HALF, true);
        translucent = new Material(pass("translucent", false), AlphaCutoffParameter.ZERO, true);

        AngelicaRenderPassConfiguration.SOLID_MATERIAL = solid;
        AngelicaRenderPassConfiguration.CUTOUT_MIPPED_MATERIAL = cutout;
        AngelicaRenderPassConfiguration.TRANSLUCENT_MATERIAL = translucent;
    }

    @AfterEach
    void restoreMaterials() {
        AngelicaRenderPassConfiguration.SOLID_MATERIAL = previous[0];
        AngelicaRenderPassConfiguration.CUTOUT_MIPPED_MATERIAL = previous[1];
        AngelicaRenderPassConfiguration.TRANSLUCENT_MATERIAL = previous[2];
    }

    private static TerrainRenderPass pass(String name, boolean fragmentDiscard) {
        return new TerrainRenderPass(name, null, false, fragmentDiscard, false, false, ChunkMeshFormats.VANILLA_LIKE, QuadPrimitiveType.TRIANGULATED, Map.of());
    }

    private TextureAtlasSprite sprite(String name, SpriteTransparencyLevel level) {
        final TextureAtlasSprite sprite = new TextureAtlasSprite(name);
        levels.put(sprite, level);
        return sprite;
    }

    private TextureAtlasSprite animated(String name) {
        final TextureAtlasSprite sprite = sprite(name, SpriteTransparencyLevel.OPAQUE);
        sprite.animationMetadata = new AnimationMetadataSection(new ArrayList<>(), 16, 16, 1);
        return sprite;
    }

    private TextureAtlasSprite custom(String name) {
        final TextureAtlasSprite sprite = new TextureAtlasSprite(name) {};
        levels.put(sprite, SpriteTransparencyLevel.OPAQUE);
        return sprite;
    }

    private void begin(Material material) {
        selector.reset(material, false, true);
    }

    private Material select(ModelQuadFacing facing, TextureAtlasSprite sprite) {
        return selector.select(facing, sprite, false);
    }

    @Test
    void opaqueOverlayStaysWithAnimatedBaseOnTheSameFace() {
        final TextureAtlasSprite base = animated("hadesX32");
        final TextureAtlasSprite corner = sprite("metalborder", SpriteTransparencyLevel.OPAQUE);
        final TextureAtlasSprite edge = sprite("metalborder-ctm", SpriteTransparencyLevel.TRANSPARENT);

        begin(cutout);
        assertSame(cutout, select(ModelQuadFacing.POS_Z, base));
        assertSame(cutout, select(ModelQuadFacing.POS_Z, corner));
        assertSame(cutout, select(ModelQuadFacing.POS_Z, edge));
        assertSame(cutout, select(ModelQuadFacing.POS_Z, corner));
    }

    @Test
    void cachedDemotionDoesNotLeakOntoAFaceWithABase() {
        final TextureAtlasSprite base = animated("hadesX32");
        final TextureAtlasSprite corner = sprite("metalborder", SpriteTransparencyLevel.OPAQUE);

        begin(cutout);
        assertSame(solid, select(ModelQuadFacing.POS_X, corner));
        assertSame(cutout, select(ModelQuadFacing.POS_Z, base));
        assertSame(cutout, select(ModelQuadFacing.POS_Z, corner));
        assertSame(solid, select(ModelQuadFacing.POS_X, corner));
    }

    @Test
    void opaqueOnlyFacesAreStillDemoted() {
        final TextureAtlasSprite stone = sprite("stone", SpriteTransparencyLevel.OPAQUE);

        begin(cutout);
        for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
            assertSame(solid, select(facing, stone));
        }
    }

    @Test
    void vertexAlphaKeepsTranslucentAndPinsTheFace() {
        final TextureAtlasSprite frame = sprite("frame", SpriteTransparencyLevel.TRANSPARENT);

        begin(translucent);
        assertSame(cutout, select(ModelQuadFacing.NEG_Y, frame));
        assertSame(translucent, selector.select(ModelQuadFacing.POS_Y, frame, true));
        assertSame(translucent, select(ModelQuadFacing.POS_Y, frame));
        assertSame(cutout, select(ModelQuadFacing.NEG_Y, frame));
    }

    @Test
    void undemotableSpritesKeepTheBaseMaterial() {
        begin(cutout);
        assertSame(cutout, select(ModelQuadFacing.POS_X, null));
        assertSame(cutout, select(ModelQuadFacing.POS_Y, animated("animated")));
        assertSame(cutout, select(ModelQuadFacing.POS_Z, custom("custom")));
    }

    @Test
    void shaderPackOverrideAndDisabledOptimizationKeepTheBaseMaterial() {
        final TextureAtlasSprite stone = sprite("stone", SpriteTransparencyLevel.OPAQUE);

        selector.reset(cutout, true, true);
        assertSame(cutout, select(ModelQuadFacing.POS_X, stone));
        selector.reset(cutout, false, false);
        assertSame(cutout, select(ModelQuadFacing.POS_X, stone));
    }

    @Test
    void resetReleasesPinnedFacesAndTheSpriteCache() {
        final TextureAtlasSprite base = animated("hadesX32");
        final TextureAtlasSprite corner = sprite("metalborder", SpriteTransparencyLevel.OPAQUE);

        begin(cutout);
        assertSame(cutout, select(ModelQuadFacing.POS_Z, base));
        assertSame(cutout, select(ModelQuadFacing.POS_Z, corner));
        assertSame(solid, select(ModelQuadFacing.NEG_Z, corner));

        begin(translucent);
        assertSame(cutout, select(ModelQuadFacing.POS_Z, corner));
    }
}
