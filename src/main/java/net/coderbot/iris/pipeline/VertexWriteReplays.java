package net.coderbot.iris.pipeline;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.backend.VertexWriteReplaySetup;
import com.gtnewhorizons.angelica.glsm.texture.InternalTextureFormat;
import com.gtnewhorizons.angelica.glsm.texture.TextureType;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.image.ImageHolder;
import net.coderbot.iris.gl.program.ComputeProgram;
import net.coderbot.iris.gl.program.Program;
import net.coderbot.iris.gl.program.ProgramBuilder;
import net.coderbot.iris.gl.sampler.GlSampler;
import net.coderbot.iris.gl.sampler.SamplerHolder;
import net.coderbot.iris.gl.state.ValueUpdateNotifier;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import net.coderbot.iris.pipeline.transform.RwImageStoreExtractor;
import net.coderbot.iris.pipeline.transform.RwImageStoreExtractor.VertexInput;
import net.coderbot.iris.samplers.IrisSamplers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;

/**
 * SDL-GPU only allows image writes from compute, so a gbuffers/shadow vertex shader that writes images gets a compute
 * twin that the backend runs over every draw made with the program.
 */
final class VertexWriteReplays {

    private final List<ComputeProgram> twins = new ArrayList<>();
    private final IntArrayList attachedPrograms = new IntArrayList();

    void attach(String name, Program program, Map<PatchShaderType, String> transformed, BiConsumer<SamplerHolder, ImageHolder> wireResources) {
        BackendManager.RENDER_BACKEND.setVertexWriteReplay(program.getProgramId(), null);
        final String source = transformed.get(PatchShaderType.COMPUTE);
        if (RwImageStoreExtractor.parseSentinel(source) != RwImageStoreExtractor.RwExtractMode.VERTEX_REPLAY) return;

        final ComputeProgram twin;
        try {
            twin = ProgramBuilder.beginCompute(name + "_vertex_replay", source, IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS).buildCompute();
        } catch (RuntimeException e) {
            Iris.logger.error("Vertex image write replay for {} failed to build; its image writes are dropped", name, e);
            return;
        }

        final List<String> writtenImages = RwImageStoreExtractor.parseVertexReplayImages(source);
        final List<String> images = new ArrayList<>(writtenImages);
        images.addAll(RwImageStoreExtractor.parseVertexReplayReadImages(source));
        final List<String> samplers = new ArrayList<>();
        for (String sampler : RwImageStoreExtractor.parseVertexReplaySamplers(source)) {
            if (RenderSystem.getUniformLocation(twin.getProgramId(), sampler) != -1) samplers.add(sampler);
        }
        final ResourceCollector resources = new ResourceCollector(images, samplers);
        wireResources.accept(resources, resources);
        for (int i = 0; i < resources.images.length; i++) {
            if (resources.images[i] == null) {
                Iris.logger.warn("Vertex image writes of {} use image {}, which the pipeline does not provide; they are dropped", name, resources.imageNames.get(i));
                twin.destroy();
                return;
            }
        }

        final List<VertexInput> inputs = RwImageStoreExtractor.parseVertexReplayInputs(source);
        final String[] inputNames = new String[inputs.size()];
        final int[] inputLocations = new int[inputs.size()];
        for (int i = 0; i < inputs.size(); i++) {
            inputNames[i] = inputs.get(i).name();
            inputLocations[i] = inputs.get(i).location();
        }
        BackendManager.RENDER_BACKEND.setVertexWriteReplay(program.getProgramId(), new VertexWriteReplaySetup(twin.getProgramId(),
            inputNames, inputLocations, resources.images, writtenImages.size(),
            resources.samplerNames.toArray(new String[0]), resources.samplerUnits, resources.samplerTextures, resources.samplerObjects,
            RwImageStoreExtractor.VG_REPLAY_VERTEX_BUFFER_BINDING, RwImageStoreExtractor.VG_REPLAY_VERTEX_BUFFER_COUNT, RwImageStoreExtractor.VG_REPLAY_INDEX_BUFFER_BINDING));
        attachedPrograms.add(program.getProgramId());
        twins.add(twin);
    }

    void destroy() {
        for (int i = 0; i < attachedPrograms.size(); i++) BackendManager.RENDER_BACKEND.setVertexWriteReplay(attachedPrograms.getInt(i), null);
        attachedPrograms.clear();
        for (ComputeProgram twin : twins) twin.destroy();
        twins.clear();
    }

    private static final class ResourceCollector implements SamplerHolder, ImageHolder {
        final List<String> imageNames;
        final IntSupplier[] images;
        final List<String> samplerNames;
        final int[] samplerUnits;
        final IntSupplier[] samplerTextures;
        final int[] samplerObjects;

        ResourceCollector(List<String> imageNames, List<String> samplerNames) {
            this.imageNames = imageNames;
            this.images = new IntSupplier[imageNames.size()];
            this.samplerNames = samplerNames;
            this.samplerUnits = new int[samplerNames.size()];
            Arrays.fill(samplerUnits, -1);
            this.samplerTextures = new IntSupplier[samplerNames.size()];
            this.samplerObjects = new int[samplerNames.size()];
        }

        @Override
        public boolean hasImage(String name) {
            return imageNames.contains(name);
        }

        @Override
        public void addTextureImage(IntSupplier textureID, InternalTextureFormat internalFormat, String name) {
            final int index = imageNames.indexOf(name);
            if (index >= 0 && images[index] == null) images[index] = textureID;
        }

        @Override
        public boolean hasSampler(String name) {
            return samplerNames.contains(name) || imageNames.contains(name);
        }

        @Override
        public void addExternalSampler(int textureUnit, String... names) {
            for (String name : names) {
                final int index = samplerNames.indexOf(name);
                if (index >= 0 && unassigned(index)) samplerUnits[index] = textureUnit;
            }
        }

        @Override
        public boolean addDefaultSampler(TextureType type, IntSupplier texture, ValueUpdateNotifier notifier, GlSampler sampler, String... names) {
            return addDynamicSampler(type, texture, notifier, sampler, names);
        }

        @Override
        public boolean addDynamicSampler(TextureType type, IntSupplier texture, GlSampler sampler, String... names) {
            return addDynamicSampler(type, texture, null, sampler, names);
        }

        @Override
        public boolean addDynamicSampler(TextureType type, IntSupplier texture, ValueUpdateNotifier notifier, GlSampler sampler, String... names) {
            boolean used = false;
            for (String name : names) {
                final int index = samplerNames.indexOf(name);
                if (index < 0) continue;
                used = true;
                if (unassigned(index)) {
                    samplerTextures[index] = texture;
                    samplerObjects[index] = sampler != null ? sampler.getId() : 0;
                }
            }
            return used;
        }

        private boolean unassigned(int index) {
            return samplerUnits[index] < 0 && samplerTextures[index] == null;
        }
    }
}
