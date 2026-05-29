package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizon.gtnhlib.client.renderer.textures.TextureLoader;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;

public final class FontTextureArray {
    private final int id;
    private final int layers;
    private final int[] layersLookup;
    private final GlyphData[][] atlasGlyphs;
    private final boolean isMinecraft;
    private byte[] glyphWidth;
    //private final boolean[] initializedLayers = new boolean[256];
    //TODO layers + layers lookup


    public FontTextureArray(int size, int layers, int[] layersLookupArray, boolean isMinecraft) {
        this.layers = layers;
        this.layersLookup = layersLookupArray;
        this.isMinecraft = isMinecraft;
        id = GLStateManager.glGenTextures();
        this.atlasGlyphs = new GlyphData[256][];

        bind();
        final int filter = isMinecraft ? GL11.GL_NEAREST : GL11.GL_LINEAR;
        GLStateManager.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GLStateManager.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GLStateManager.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_BORDER);
        GLStateManager.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_BORDER);
        final FloatBuffer buffer = BufferUtils.createFloatBuffer(4);
        buffer.put(1);
        buffer.put(1);
        buffer.put(1);
        buffer.put(1);
        buffer.position(0).limit(4);
        GLStateManager.glTexParameter(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_BORDER_COLOR, buffer);
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
        unbind();
    }

    public GlyphData[] addAtlasFromBoundTexture(int depth) {
        int size = GLStateManager.glGetTexLevelParameteri(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_TEXTURE_WIDTH
        );
        ByteBuffer pixels = BufferUtils.createByteBuffer(size * size); //TODO make faster
        GLStateManager.glGetTexImage(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_RED,
            GL11.GL_UNSIGNED_BYTE,
            pixels
        );


        return addAtlas(depth, size, pixels);
    }

    public GlyphData[] addAtlas(int depth, BufferedImage image) {
        return addAtlas(depth, image.getWidth(), getPixelBuffer(image));
    }

    public GlyphData[] addAtlas(int depth, ResourceLocation resource) {
        try {
            InputStream inputstream = Minecraft.getMinecraft().getResourceManager()
                .getResource(resource).getInputStream();
            final GlyphData[] data = addAtlas(depth, ImageIO.read(inputstream));
            inputstream.close();
            return data;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to initialize resource " + resource + ".");
            //initializedLayers[depth] = true;
            final GlyphData[] glyphData = new GlyphData[256];
            atlasGlyphs[depth] = glyphData;
            return glyphData;
        }
    }

    private static ByteBuffer getPixelBuffer(BufferedImage image) {
        final ByteBuffer pixelBuffer = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight());
        pixelBuffer.clear();
        final int[] pixelValues = TextureLoader.getImagePixels(image);
        for (int i : pixelValues) {
//            System.out.println(i);
            pixelBuffer.put((byte) ((i >> 24) & 0xFF));
        }
        pixelBuffer.position(0).limit(pixelValues.length);
        return pixelBuffer;
    }


    public GlyphData getGlyphData(char ch) {
        GlyphData[] glyphs = atlasGlyphs[ch / 256];
        if (glyphs == null) {
            //System.out.println("testttttttttttttt");
            if (ch < 256) {
                glyphs = addAtlasFromBoundTexture(0);
            } else {
                glyphs = addAtlas(ch / 256, getUnicodePage(ch / 256));
            }
        }
        if (glyphs[ch & 255] == null) glyphs[ch & 255] = new GlyphData(0, 0, 0, 0, 0, 0);
        return glyphs[ch & 255];
    }

    public int getDepth(char ch) {
        return layersLookup[ch / 256];
    }


    public GlyphData[] addAtlas(int atlasId, int size, ByteBuffer pixels) {
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
        unbind();
        final GlyphData[] glyphData = new GlyphData[256];
        atlasGlyphs[atlasId] = glyphData;
        if (isMinecraft) {
            if (atlasId == 0) {
                System.out.println("Initializing glyphs");
                for (int i = 0; i < 256; i++) {
                    final int[] charWidth = FontProviderMC.getDefault().charWidth;
                    int lutIndex = BatchingFontRenderer.lookupMcFontPosition((char) i);
                    final float uStart = ((lutIndex & 15) * 8) / 256.0F;
                    final float vStart = ((lutIndex >> 4) * 8) / 256.0F;
                    final float xAdvance = charWidth[lutIndex];
                    final float glyphW = charWidth[lutIndex] - 0.01F;
                    final float uSize =  (charWidth[lutIndex] - 1.01F) / 256.0F;
                    final float vSize = 7.99F / 256.0F;
//                    final FontProviderMC provider = FontProviderMC.getDefault();
//                    int lutIndex = BatchingFontRenderer.lookupMcFontPosition((char) i);
//                    final float uStart = provider.getUStart(chr);
//                    final float vStart = provider.getVStart(chr);
//                    final float xAdvance = provider.getXAdvance(chr);
//                    final float glyphW = provider.getGlyphW(chr);
//                    final float uSize = provider.getUSize(chr);
//                    final float vSize = provider.getVSize(chr);

                    glyphData[i] = new GlyphData(
                        uStart, vStart,
                        xAdvance,
                        glyphW,
                        uSize,
                        vSize
                    );
                }
            } else {
                if (glyphWidth == null) {
                    glyphWidth = new byte[65536];
                    try {
                        InputStream inputstream = Minecraft.getMinecraft().getResourceManager()
                            .getResource(new ResourceLocation("font/glyph_sizes.bin")).getInputStream();
                        //noinspection ResultOfMethodCallIgnored
                        inputstream.read(this.glyphWidth);
                    }
                    catch (IOException ioexception) {
                        throw new RuntimeException(ioexception);
                    }
                }
                for (int i = 0; i < 256; i++) {
                    final char chr = (char) ((atlasId * 256) | i);
                    final int startColumn = ((this.glyphWidth[chr] >>> 4) & 15);
                    final int endColumn = this.glyphWidth[chr] & 15;
                    final float chrWidth = endColumn - startColumn - 0.02F;
                    final float uStart = ((chr % 16 * 16) + startColumn + 0.21f) / 256.0f;
                    final float vStart = ((float) ((chr & 255) / 16 * 16) + 0.21f) / 256.0f;
                    final float xAdvance = (endColumn - startColumn) / 2.0F + 1.0F;
                    final float glyphW = chrWidth / 2.0f + 1.0f;
                    final float uSize =  (chrWidth - 0.42f) / 256.0f;
                    final float vSize = (16.0f - 0.42f) / 256.0f;
                    glyphData[i] = new GlyphData(
                        uStart, vStart,
                        xAdvance,
                        glyphW,
                        uSize,
                        vSize
                    );
                }
            }
        } else {
            //TODO
        }
        try {
            saveLayerAsPNG(layersLookup[atlasId], 256, "test" + atlasId + ".png");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return glyphData;
    }

    public void saveLayerAsPNG(int layer, int size, String fileName) {
        bind();

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
        // Unbind
        unbind();
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

    private static int getAtlasSize(Font font, FontMetrics fontMetrics, int separator) {
        int size = 256;

        loop:
        while (true) {
            for (int atlasId = 0; atlasId < 256; atlasId++) {
                final int lineHeight = fontMetrics.getHeight();
                final int charHeight = (lineHeight + separator);
                int imgX = 0;
                int imgY = 0; //TODO idk
                for (int i = 0; i < 256; i++) {
                    final char ch = (char) (i + 256 * atlasId);
                    if (!font.canDisplay(ch)) continue;

                    final int charWidth = fontMetrics.charWidth(ch);

                    if (imgX + charWidth >= size) {
                        imgX = 0;
                        imgY += charHeight;
                    }
                    imgX += (charWidth + separator);

                }
                //if (imgX == 0 && imgY == 0) return 0; TODO integrate this somehow
                // Total used height includes final row
                int usedHeight = imgY + charHeight;

                if (usedHeight > size) {
                    size *= 2;
                    continue loop;
                }

            }
        }
    }

    public static ResourceLocation getUnicodePage(int layer) {
        return new ResourceLocation(String.format("textures/font/unicode_page_%02x.png", layer));
    }
}
