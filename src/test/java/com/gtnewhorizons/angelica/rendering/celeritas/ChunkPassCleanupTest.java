package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import com.gtnewhorizons.angelica.rendering.celeritas.api.IrisShaderProvider;
import com.gtnewhorizons.angelica.rendering.celeritas.api.IrisShaderProviderHolder;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import me.jellysquid.mods.sodium.client.gui.options.named.MultiDrawMode;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.parameters.AlphaCutoffParameter;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@GLCoreTest
class ChunkPassCleanupTest {
    @Test
    void failedIrisSetupRestoresThePassAndDoesNotRepeatCleanupOnAnEmptyPass() throws ReflectiveOperationException {
        final IrisShaderProvider previous = IrisShaderProviderHolder.getProvider();
        final IrisShaderProvider provider = mock(IrisShaderProvider.class);
        final ChunkShaderInterface shader = mock(ChunkShaderInterface.class);
        final GlProgram<?> program = mock(GlProgram.class);
        final TerrainRenderPass pass = spy(new TerrainRenderPass("cleanup", TerrainRenderPass.PipelineState.DEFAULT,
            false, false, false, false, ChunkMeshFormats.VANILLA_LIKE, QuadPrimitiveType.TRIANGULATED, Map.of()));
        final Material material = new Material(pass, AlphaCutoffParameter.ZERO, false);
        final RenderPassConfiguration<String> config = new RenderPassConfiguration<>(
            Map.of("solid", material), Map.of("solid", List.of(pass)), material, material, material);
        final ChunkRenderMatrices matrices = new ChunkRenderMatrices(new Matrix4f(), new Matrix4f());
        final CameraTransform camera = new CameraTransform(0, 0, 0);
        final ChunkRenderListIterable lists = mock(ChunkRenderListIterable.class);
        final Field optionsField = ClientProxy.class.getDeclaredField("CONFIG");
        optionsField.setAccessible(true);
        final Object previousOptions = optionsField.get(null);
        final SodiumGameOptions options = new SodiumGameOptions();
        options.advanced.multiDrawMode = MultiDrawMode.DIRECT;
        final RuntimeException failure = new IllegalStateException("Failed Iris setup");
        when(provider.isShadersEnabled()).thenReturn(true);
        doReturn(program).when(provider).getShaderOverride(pass);
        doReturn(shader).when(program).getInterface();
        when(lists.hasPass(pass)).thenReturn(true, false);
        doThrow(failure).when(shader).setupState(pass);
        IrisShaderProviderHolder.setProvider(provider);
        optionsField.set(null, options);
        RenderDevice.enterManagedCode();
        try {
            final CommandList commandList = RenderDevice.INSTANCE.createCommandList();
            final AngelicaChunkRenderer renderer = new AngelicaChunkRenderer(RenderDevice.INSTANCE, config);
            try {
                assertSame(failure, assertThrows(RuntimeException.class,
                    () -> renderer.render(matrices, commandList, lists, pass, camera, camera)));
                renderer.render(matrices, commandList, lists, pass, camera, camera);

                verify(shader, times(1)).restoreState();
                verify(program, times(1)).unbind();
                verify(pass, times(1)).endDrawing();
            } finally {
                renderer.delete(commandList);
            }
        } finally {
            RenderDevice.exitManagedCode();
            IrisShaderProviderHolder.setProvider(previous);
            optionsField.set(null, previousOptions);
        }
    }
}
