package com.gtnewhorizons.angelica.client.font.atlas;

import com.gtnewhorizons.angelica.client.font.GlyphData;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAllocFloat;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutFloat;

public final class FontTextureArray {
    private final int id;
    private final int textureSize;
    private final int[] layersLookup;
    private final GlyphData[][] atlasGlyphs = new GlyphData[256][];
    private final AtlasProvider atlasProvider;

    //TODO layers + layers lookup


    public FontTextureArray(int size, int layers, int[] layersLookupArray, final int filter, AtlasProvider atlasProvider) {
        this.layersLookup = layersLookupArray;
        this.atlasProvider = atlasProvider;
        this.id = GLStateManager.glGenTextures();
        this.textureSize = size;

        bind();
        GLStateManager.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GLStateManager.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GLStateManager.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_BORDER);
        GLStateManager.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_BORDER);
        final FloatBuffer buffer = memAllocFloat(4);
        final long ptr = memAddress0(buffer);
        memPutFloat(ptr, 1);
        memPutFloat(ptr + 4, 1);
        memPutFloat(ptr + 8, 1);
        memPutFloat(ptr + 12, 1);
        GLStateManager.glTexParameter(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_BORDER_COLOR, buffer);
        memFree(buffer);
        GLStateManager.glTexImage3D(
            GL30.GL_TEXTURE_2D_ARRAY,
            0,
            GL30.GL_R8,
            size, size, layers,
            0,
            GL11.GL_RED,
            GL11.GL_UNSIGNED_BYTE,
            (ByteBuffer) null
        );

//        if (FontProviderCustom.DUMP_ATLASES) {
//            long totalTime = 0;
//            for (int atlasId = 0; atlasId < 255; atlasId++) {
//                try {
//                    final long nanos = System.nanoTime();
//
//                    getGlyphData((char) (atlasId * 256));
//
//                    final long diff = System.nanoTime() - nanos;
//                    totalTime += diff;
//                    saveLayerAsPNG(layersLookup[atlasId], 256, "test" + atlasId + ".png");
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//            final double diff = totalTime / 1_000_000d;
//            System.out.println("Loading all texture atlases took " + diff + "ms.");
//        }
    }
//
//    public GlyphData[] addAtlas(int depth, ResourceLocation resource) {
//        try {
//            InputStream inputstream = Minecraft.getMinecraft().getResourceManager()
//                .getResource(resource).getInputStream();
//            final GlyphData[] data = addAtlas(depth, ImageIO.read(inputstream));
//            inputstream.close();
//            return data;
//        } catch (Exception e) {
//            e.printStackTrace();
//            System.out.println("Failed to initialize resource " + resource + ".");
//            //initializedLayers[depth] = true;
//            final GlyphData[] glyphData = new GlyphData[256];
//            atlasGlyphs[depth] = glyphData;
//            return glyphData;
//        }
//    }

    public GlyphData getGlyphData(final char ch) {
        final int atlasId = ch / 256;
        GlyphData[] glyphs = atlasGlyphs[atlasId];
        if (glyphs == null) {
            glyphs = addAtlas(atlasId);
        }
        if (glyphs[ch & 255] == null) { //TODO make it null
            glyphs[ch & 255] = new GlyphData(-1, -1, -1, -1, -1, -1);
        }
        return glyphs[ch & 255];
    }

    public boolean isGlyphAvailable(char ch) {
        final int atlasId = ch / 256;
        GlyphData[] glyphs = atlasGlyphs[atlasId];
        if (glyphs == null) {
            glyphs = addAtlas(atlasId);
        }
        return glyphs[ch & 255] != null && glyphs[ch & 255].glyphW != -1;
    }


    public int getDepth(char ch) {
        return layersLookup[ch / 256];
    }


    public GlyphData[] addAtlas(int atlasId) {
        final GlyphData[] glyphData = new GlyphData[256];
        atlasGlyphs[atlasId] = glyphData;
        final ByteBuffer pixels = atlasProvider.generateGlyphData(atlasId, this.textureSize, glyphData);
        if (pixels == null) {
//            throw new UnsupportedOperationException(
//                "Failed to initialize atlas for chars " + (atlasId * 256) + " to " + ((atlasId + 1) * 256) + "!"
//            );
            return glyphData;
        }
        final int size = (int) Math.sqrt(pixels.capacity());
        bind();
        GLStateManager.glTexSubImage3D(
            GL30.GL_TEXTURE_2D_ARRAY,
            0,
            0, 0, layersLookup[atlasId],
            size, size,
            1,
            GL11.GL_RED,
            GL11.GL_UNSIGNED_BYTE,
            pixels
        );
        memFree(pixels);

        try {
            saveLayerAsPNG(layersLookup[atlasId], size, "test" + layersLookup[atlasId] + ".png");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return glyphData;
    }

    public void saveLayerAsPNG(int layer, int size, String fileName) {
        ByteBuffer pixels = memAlloc(size * size);

        GLStateManager.glGetTextureSubImage(
            id,
            0,
            0, 0, layer,
            size, size, 1,
            GL11.GL_RED,
            GL11.GL_UNSIGNED_BYTE,
            size * size,
            memAddress(pixels)
        );

        saveBufferAsPNG(size, pixels, fileName);
    }

    private void saveBufferAsPNG(int size, ByteBuffer pixels, String fileName) {
        File dir = new File(Minecraft.getMinecraft().mcDataDir, "debug/texturearray");
        if (!dir.exists()) dir.mkdirs();
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

        int[] row = new int[size];

        for (int y = 0; y < size; y++) {

            int rowOffset = y * size;

            for (int x = 0; x < size; x++) {

                int i = rowOffset + x;

                int c = pixels.get(i) & 0xFF;

                row[x] = 0xFF000000 | (c << 16) | (c << 8) | c;
            }

            // Flip vertically because OpenGL origin is bottom-left
            image.setRGB(0, size - 1 - y, size, 1, row, 0, size);
        }

        // Save PNG
        try {
            ImageIO.write(image, "PNG", new File(dir, fileName));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void bind() {
        GLStateManager.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, id);
    }

    public void unbind() {
        GLStateManager.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, 0);
        //GLStateManager.bindTextureBypass(GL30.GL_TEXTURE_2D_ARRAY, 0);
    }

    public void delete() {
        GLStateManager.glDeleteTextures(id);
    }
}
