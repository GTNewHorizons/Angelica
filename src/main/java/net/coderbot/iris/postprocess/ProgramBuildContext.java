package net.coderbot.iris.postprocess;

import com.github.bsideup.jabel.Desugar;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.coderbot.iris.rendertarget.RenderTargets;
import net.coderbot.iris.shadows.ShadowRenderTargets;
import net.coderbot.iris.uniforms.FrameUpdateNotifier;
import net.coderbot.iris.uniforms.custom.CustomUniforms;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

@Desugar
public record ProgramBuildContext(
    @NotNull RenderTargets renderTargets,
    @NotNull IntSupplier noiseTexture,
    @NotNull FrameUpdateNotifier updateNotifier,
    @NotNull CenterDepthSampler centerDepthSampler,
    @NotNull Supplier<ShadowRenderTargets> shadowTargetsSupplier,
    @NotNull Object2ObjectMap<String, IntSupplier> customTextureIds,
    @NotNull CustomUniforms customUniforms
) {}
