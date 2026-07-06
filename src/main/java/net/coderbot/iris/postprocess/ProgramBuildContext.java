package net.coderbot.iris.postprocess;

import com.github.bsideup.jabel.Desugar;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.coderbot.iris.gl.image.GlImage;
import net.coderbot.iris.gl.texture.TextureAccess;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.rendertarget.RenderTargets;
import net.coderbot.iris.shadows.ShadowRenderTargets;
import net.coderbot.iris.uniforms.FrameUpdateNotifier;
import net.coderbot.iris.uniforms.custom.CustomUniforms;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Supplier;

@Desugar
public record ProgramBuildContext(
    @NotNull RenderTargets renderTargets,
    @NotNull TextureAccess noiseTexture,
    @NotNull FrameUpdateNotifier updateNotifier,
    @NotNull CenterDepthSampler centerDepthSampler,
    @NotNull Supplier<ShadowRenderTargets> shadowTargetsSupplier,
    @NotNull Object2ObjectMap<String, TextureAccess> customTextureIds,
    @NotNull CustomUniforms customUniforms,
    @Nullable Set<GlImage> customImages,
    @Nullable Object2ObjectMap<String, TextureAccess> irisCustomTextures,
    @Nullable WorldRenderingPipeline pipeline
) {}
