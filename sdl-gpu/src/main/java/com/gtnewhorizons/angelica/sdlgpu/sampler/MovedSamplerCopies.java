package com.gtnewhorizons.angelica.sdlgpu.sampler;

import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager.FrameState;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.MovedSamplerConversion;
import com.gtnewhorizons.angelica.sdlgpu.resource.PixelOps;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREFORMAT_R16G16B16A16_FLOAT;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREFORMAT_R32_FLOAT;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREUSAGE_COLOR_TARGET;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREUSAGE_SAMPLER;

/**
 * SDL reads texture slots in layout GENERAL, but a sampleable texture sits in SHADER_READ_ONLY_OPTIMAL
 * and SDL won't give one texture both usages. So a moved sampler reads a GRAPHICS_STORAGE_READ copy of its texture,
 * refreshed after the source is written. Formats Vulkan can't storage-read are converted to what texelFetch returns:
 * depth to R32F, anything else (sRGB) to RGBA16F. D3D12 (one resource state for both usages) and Metal (no layouts)
 * bind the texture directly.
 */
public final class MovedSamplerCopies {
    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");
    private static final int UNUSED_FRAMES_BEFORE_RELEASE = 8;

    private static final class Copy {
        final long texture;
        final ResourceManager.TextureMeta meta;
        final int convertedFormat;
        FrameState readFrame;
        long readPass = -1;
        long writeVersion = -1;
        long pendingWriteVersion;
        long lastUsedFrame;

        Copy(long texture, ResourceManager.TextureMeta meta, int convertedFormat) {
            this.texture = texture;
            this.meta = meta;
            this.convertedFormat = convertedFormat;
        }
    }

    private final Device device;
    private final FrameManager frameManager;
    private final ResourceManager resourceManager;
    private final MovedSamplerConversion conversion;
    private final Long2ObjectOpenHashMap<Copy> bySource = new Long2ObjectOpenHashMap<>();
    private final LongArrayList stale = new LongArrayList();
    private final IntOpenHashSet unsupportedWarned = new IntOpenHashSet();
    private long frameNumber;

    public MovedSamplerCopies(Device device, FrameManager frameManager, ResourceManager resourceManager, MovedSamplerConversion conversion) {
        this.device = device;
        this.frameManager = frameManager;
        this.resourceManager = resourceManager;
        this.conversion = conversion;
    }

    public void prepare(ContextState st, FrameState f, boolean keepsOpenPass) {
        if (!device.isVulkan()) return;
        final ShaderManager.ProgramObject prog = st.boundProgramObj;
        if (prog == null || !prog.linked || !prog.hasReadMovedSamplers) return;
        final int[] vertexUnits = SamplerLookup.movedSamplerUnits(prog, false);
        final int[] fragmentUnits = SamplerLookup.movedSamplerUnits(prog, true);
        final long writeGen = resourceManager.textureWriteGeneration();
        if (keepsOpenPass && st.lastPreparedMovedSamplerPass == f.renderPassGeneration
            && st.lastPreparedMovedSamplerProgram == prog
            && st.lastPreparedMovedSamplerUnitsGen == prog.samplerUnitsGeneration
            && st.lastPreparedMovedSamplerWriteGen == writeGen
            && bindingsMatch(st, vertexUnits, st.lastPreparedVertexMovedSamplerTextures)
            && bindingsMatch(st, fragmentUnits, st.lastPreparedFragmentMovedSamplerTextures)) return;
        synchronized (this) {
            if (!refresh(st, f, vertexUnits, fragmentUnits, keepsOpenPass)) return;
        }
        st.lastPreparedMovedSamplerProgram = prog;
        snapshotBindings(st, vertexUnits, st.lastPreparedVertexMovedSamplerTextures);
        snapshotBindings(st, fragmentUnits, st.lastPreparedFragmentMovedSamplerTextures);
        st.lastPreparedMovedSamplerUnitsGen = prog.samplerUnitsGeneration;
        st.lastPreparedMovedSamplerWriteGen = writeGen;
        st.lastPreparedMovedSamplerPass = f.renderPassGeneration;
    }

    private static boolean bindingsMatch(ContextState st, int[] units, int[] textures) {
        for (int i = 0; i < units.length; i++) {
            if (textures[i] != st.boundTexture(units[i])) return false;
        }
        return true;
    }

    private static void snapshotBindings(ContextState st, int[] units, int[] textures) {
        for (int i = 0; i < units.length; i++) textures[i] = st.boundTexture(units[i]);
    }

    private boolean refresh(ContextState st, FrameState f, int[] vertexUnits, int[] fragmentUnits, boolean keepsOpenPass) {
        stale.clear();
        collectStale(st, f, vertexUnits, keepsOpenPass);
        collectStale(st, f, fragmentUnits, keepsOpenPass);
        for (int i = 0; i < stale.size(); i++) {
            final long source = stale.getLong(i);
            final Copy copy = bySource.get(source);
            if (copy == null) continue;
            final ResourceManager.TextureMeta m = copy.meta;
            final boolean refreshed = copy.convertedFormat == 0
                ? resourceManager.copyAllMips(source, copy.texture, m.glTarget(), m.width(), m.height(), m.depth(), m.levels())
                : conversion.convert(st, source, copy.texture, copy.convertedFormat, m.width(), m.height(), m.levels());
            if (!refreshed) return false;
            copy.writeVersion = copy.pendingWriteVersion;
        }
        return true;
    }

    private void collectStale(ContextState st, FrameState f, int[] units, boolean keepsOpenPass) {
        for (int unit : units) {
            final int glTexId = st.boundTexture(unit);
            if (glTexId == 0) continue;
            final ResourceManager.TextureMeta meta = resourceManager.getTextureMeta(glTexId);
            if (!readThroughCopy(meta)) continue;
            final long source = resourceManager.getTextureHandle(glTexId);
            if (source == 0 || stale.contains(source)) continue;
            final Copy copy = copyFor(source, meta);
            if (copy == null) continue;
            copy.lastUsedFrame = frameNumber;
            final boolean readByOpenPass = keepsOpenPass && f.renderPass != 0 && copy.readFrame == f && copy.readPass == f.renderPassGeneration;
            if (!readByOpenPass) {
                final long writeVersion = resourceManager.textureWriteVersion(source);
                if (copy.writeVersion != writeVersion) {
                    copy.pendingWriteVersion = writeVersion;
                    stale.add(source);
                }
            }
        }
    }

    private static boolean readThroughCopy(ResourceManager.TextureMeta meta) {
        return meta != null && (meta.usage() & SDL_GPU_TEXTUREUSAGE_SAMPLER) != 0;
    }

    private Copy copyFor(long source, ResourceManager.TextureMeta meta) {
        Copy copy = bySource.get(source);
        if (copy != null && copy.meta.equals(meta)) return copy;
        if (copy != null) resourceManager.releaseTextureDeferred(copy.texture);
        int convertedFormat = 0;
        long texture = resourceManager.createStorageReadTexture(meta, meta.sdlFormat(), 0);
        if (texture == 0 && meta.glTarget() == GL11.GL_TEXTURE_2D) {
            convertedFormat = PixelOps.isDepthFormat(meta.sdlFormat()) ? SDL_GPU_TEXTUREFORMAT_R32_FLOAT : SDL_GPU_TEXTUREFORMAT_R16G16B16A16_FLOAT;
            texture = resourceManager.createStorageReadTexture(meta, convertedFormat, SDL_GPU_TEXTUREUSAGE_COLOR_TARGET);
        }
        if (texture == 0) {
            bySource.remove(source);
            resourceManager.stopTrackingTextureWrites(source);
            if (unsupportedWarned.add(meta.sdlFormat())) {
                LOG.error("SDL_GPU cannot make a read-only texture-slot copy of format 0x{} (GL target 0x{}); samplers moved off the 16 sampler slots read black from it",
                    Integer.toHexString(meta.sdlFormat()), Integer.toHexString(meta.glTarget()));
            }
            return null;
        }
        copy = new Copy(texture, meta, convertedFormat);
        bySource.put(source, copy);
        resourceManager.trackTextureWrites(source);
        return copy;
    }

    public long textureFor(long source, int glTexId) {
        if (!device.isVulkan()) return source;
        synchronized (this) {
            final Copy copy = bySource.get(source);
            if (copy == null) return readThroughCopy(resourceManager.getTextureMeta(glTexId)) ? 0 : source;
            if (copy.writeVersion == -1) return 0;
            final FrameState f = frameManager.frame();
            copy.readFrame = f;
            copy.readPass = f.renderPassGeneration;
            return copy.texture;
        }
    }

    public synchronized void releaseCopyOf(long source) {
        final Copy copy = bySource.remove(source);
        if (copy != null) resourceManager.releaseTextureDeferred(copy.texture);
        resourceManager.stopTrackingTextureWrites(source);
    }

    public synchronized void releaseUnused() {
        frameNumber++;
        if (bySource.isEmpty()) return;
        for (var it = bySource.long2ObjectEntrySet().fastIterator(); it.hasNext(); ) {
            final Long2ObjectMap.Entry<Copy> e = it.next();
            if (frameNumber - e.getValue().lastUsedFrame > UNUSED_FRAMES_BEFORE_RELEASE) {
                resourceManager.stopTrackingTextureWrites(e.getLongKey());
                resourceManager.releaseTextureDeferred(e.getValue().texture);
                it.remove();
            }
        }
    }

    public synchronized void releaseAll() {
        for (var it = bySource.long2ObjectEntrySet().fastIterator(); it.hasNext(); ) {
            final Long2ObjectMap.Entry<Copy> e = it.next();
            resourceManager.stopTrackingTextureWrites(e.getLongKey());
            resourceManager.releaseTextureHandle(e.getValue().texture);
        }
        bySource.clear();
    }
}
