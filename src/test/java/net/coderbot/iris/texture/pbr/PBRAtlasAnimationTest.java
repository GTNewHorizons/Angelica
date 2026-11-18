package net.coderbot.iris.texture.pbr;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.profiling.DebugCounters;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.mixins.interfaces.IPatchedTextureAtlasSprite;
import com.gtnewhorizons.angelica.mixins.interfaces.ISpriteExt;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import net.coderbot.iris.texture.pbr.loader.AtlasPBRLoader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@GLCoreTest
class PBRAtlasAnimationTest {
    static Stream<Arguments> cases() {
        return Stream.of(
            Arguments.of("visible tracked sprite updates", true, true, true, true, true, 1, 0, 1, 0),
            Arguments.of("hidden tracked sprite dry-runs", true, true, true, true, false, 1, 0, 0, 1),
            Arguments.of("only-visible off reaches the gate", true, true, true, false, true, 1, 0, 1, 0),
            Arguments.of("base without animation metadata is always animated", true, false, true, true, false, 0, 1, 1, 0),
            Arguments.of("sprite without the animation mixin is always animated", false, true, true, true, false, 0, 1, 1, 0),
            Arguments.of("non-animated pbr sprite is never touched", true, true, false, true, false, 0, 0, 0, 0)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void cycleRoutesAnimationAndCountsUpdates(String name, boolean patchedSprite, boolean baseAnimated, boolean pbrAnimated,
                                              boolean onlyVisible, boolean tickReturns, int expectedTicks, int expectedUpdateCalls,
                                              long expectedUpdates, long expectedDryRuns) {
        final SodiumGameOptions previousOptions = Reflect.getStatic(ClientProxy.class, "CONFIG");
        final SodiumGameOptions options = new SodiumGameOptions();
        options.performance.animateOnlyVisibleTextures = onlyVisible;

        final TextureMap textureMap = mock(TextureMap.class, withSettings().extraInterfaces(TextureAtlasExtension.class));
        when(textureMap.completeResourceLocation(any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(0));
        final TextureAtlasSprite base = mock(TextureAtlasSprite.class);
        when(base.hasAnimationMetadata()).thenReturn(baseAnimated);
        final AtlasPBRLoader.PBRTextureAtlasSprite sprite = mock(AtlasPBRLoader.PBRTextureAtlasSprite.class, patchedSprite
            ? withSettings().extraInterfaces(IPatchedTextureAtlasSprite.class, ISpriteExt.class)
            : withSettings().extraInterfaces(ISpriteExt.class));
        when(sprite.getIconName()).thenReturn("minecraft:stone_n");
        when(sprite.getBaseSprite()).thenReturn(base);
        when(((ISpriteExt) sprite).isAnimation()).thenReturn(pbrAnimated);
        if (patchedSprite) {
            when(((IPatchedTextureAtlasSprite) sprite).angelica$tickAnimation(anyBoolean())).thenReturn(tickReturns);
        }

        Reflect.setStatic(ClientProxy.class, "CONFIG", options);
        final PBRAtlasTexture atlas = new PBRAtlasTexture(textureMap, PBRType.NORMAL);
        final int texture = GLSMTextures.glGenTextures();
        Reflect.set(atlas, "glTextureId", texture);
        try {
            atlas.addSprite(sprite);
            final long updatesBefore = DebugCounters.PBR_ANIM_UPDATES.sum();
            final long dryRunsBefore = DebugCounters.PBR_ANIM_DRY_RUNS.sum();

            atlas.cycleAnimationFrames();

            if (patchedSprite) {
                verify((IPatchedTextureAtlasSprite) sprite, times(expectedTicks)).angelica$tickAnimation(anyBoolean());
                verify((IPatchedTextureAtlasSprite) sprite, times(expectedTicks)).angelica$tickAnimation(onlyVisible);
            }
            verify(sprite, times(expectedUpdateCalls)).updateAnimation();
            assertEquals(expectedUpdates, DebugCounters.PBR_ANIM_UPDATES.sum() - updatesBefore);
            assertEquals(expectedDryRuns, DebugCounters.PBR_ANIM_DRY_RUNS.sum() - dryRunsBefore);
        } finally {
            GLSMTextures.glDeleteTextures(texture);
            Reflect.setStatic(ClientProxy.class, "CONFIG", previousOptions);
        }
    }
}
