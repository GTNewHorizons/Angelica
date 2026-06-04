package com.gtnewhorizons.angelica.client.font.atlas;

import com.gtnewhorizon.gtnhlib.client.renderer.textures.TextureLoader;
import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.client.font.GlyphData;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAllocFloat;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutFloat;

public abstract class FontTextureArray {
    protected final int id;
    protected final int textureSize;
    protected final int[] layersLookup;
    protected final GlyphData[][] atlasGlyphs = new GlyphData[256][];

    static final boolean DUMP_ATLASES = true;


    //TODO layers + layers lookup


    FontTextureArray(int size, int layers, int[] layersLookupArray, final int filter) {
        this.layersLookup = layersLookupArray;
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

    public final GlyphData getGlyphData(final char ch) {
        final int atlasId = ch / 256;
        GlyphData[] glyphs = atlasGlyphs[atlasId];
        if (glyphs == null) {
            glyphs = addAtlas(atlasId);
        }
        return glyphs[ch & 255];
    }

    public final boolean isGlyphAvailable(char ch) {
        final int atlasId = ch / 256;
        GlyphData[] glyphs = atlasGlyphs[atlasId];
        if (glyphs == null) {
            glyphs = addAtlas(atlasId);
        }
        return glyphs[ch & 255] != null;
    }

    public int getDepth(char ch) {
        return layersLookup[ch / 256];
    }

    protected final void loadAtlas(int atlasId) {
        if (atlasGlyphs[atlasId] == null) {
            addAtlas(atlasId);
        }
    }

    public abstract ByteBuffer generateGlyphData(int atlasId, GlyphData[] glyphs);


    protected final GlyphData[] addAtlas(int atlasId) {
        final GlyphData[] glyphData = new GlyphData[256];
        try {
            final ByteBuffer pixels = generateGlyphData(atlasId, glyphData);
            if (pixels == null) {
                atlasGlyphs[atlasId] = glyphData;
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

            atlasGlyphs[atlasId] = glyphData;

            if (DUMP_ATLASES) {
                try {
                    saveLayerAsPNG(layersLookup[atlasId], size, "test" + layersLookup[atlasId] + ".png");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            AngelicaMod.LOGGER.error("Failed to initialize glyph data for atlas id " + id + "! Did this get called in another thread?", e);
            // Return the data (because it's correct), but don't initialize it (texture is still missing)
            return glyphData;

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
    }

    public void delete() {
        GLStateManager.glDeleteTextures(id);
    }



    static ByteBuffer getBoundTextureData(final int size) {
        final ByteBuffer pixels = memAlloc(size * size);
        GLStateManager.glGetTexImage(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_RED,
            GL11.GL_UNSIGNED_BYTE,
            pixels
        );
        return pixels;
    }

    static int getBoundTextureSize() {
        return GLStateManager.glGetTexLevelParameteri(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_TEXTURE_WIDTH
        );
    }

    static ByteBuffer getPixelBuffer(BufferedImage image) {
        final int size = image.getWidth();
        final ByteBuffer pixelBuffer = memAlloc(size * size);
        pixelBuffer.clear();
        final int[] pixelValues = TextureLoader.getImagePixels(image);
        for (int i : pixelValues) {
            pixelBuffer.put((byte) ((i >> 24) & 0xFF)); // Only extract alpha
        }
        pixelBuffer.position(0).limit(pixelValues.length);
        return pixelBuffer;
    }

    static ByteBuffer getTextureFromResource(ResourceLocation resource) {
        try {
            final BufferedImage image = TextureLoader.getBufferedImage(Minecraft.getMinecraft().getResourceManager()
                .getResource(resource));
            if (image.getWidth() != image.getHeight()) {
                throw new UnsupportedOperationException(resource + "has a different width and height, they cannot get rendered!");
            }
            return getPixelBuffer(image);
        } catch (IOException e) {
            System.out.println("Failed to load resource " + resource + ".");
            return null;
        }
    }
}
