package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizon.gtnhlib.client.renderer.textures.TextureLoader;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
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
import java.nio.IntBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress;

public final class FontTextureArray {
    private final int id;
    private final int layers;
    private final int[] layersLookupArray = new int[256];
    private final boolean[] initializedLayers = new boolean[256];
    //TODO layers + layers lookup


    public FontTextureArray(int size, boolean linear) {
        this.layers = 256;
        id = GLStateManager.glGenTextures();

        bind();
        //TODO convert to 1 channel only
        final int filter = linear ? GL11.GL_LINEAR : GL11.GL_NEAREST;
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
            GL11.GL_RGBA,
            size, size, layers,
            0,
            GL12.GL_BGRA,
            GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
            (IntBuffer) null

        );
        unbind();
    }

    public void addAtlasFromBoundTexture(int depth) {
        int size = GLStateManager.glGetTexLevelParameteri(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_TEXTURE_WIDTH
        );
        IntBuffer pixels = BufferUtils.createIntBuffer(size * size); //TODO make faster
        GLStateManager.glGetTexImage(
            GL11.GL_TEXTURE_2D,
            0,
            GL12.GL_BGRA,
            GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
            pixels
        );


        addAtlas(depth, size, pixels);
    }

    public void addAtlas(int depth, BufferedImage image) {
        addAtlas(depth, image.getWidth(), getPixelBuffer(image));
    }

    public void addAtlas(int depth, ResourceLocation resource) {
        try {
            InputStream inputstream = Minecraft.getMinecraft().getResourceManager()
                .getResource(resource).getInputStream();
            addAtlas(depth, ImageIO.read(inputstream));
            inputstream.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to initialize resource " + resource + ".");
            initializedLayers[depth] = true;
        }
    }

    private static IntBuffer getPixelBuffer(BufferedImage image) {
        final IntBuffer pixelBuffer = TextureLoader.dataBuffer;
        pixelBuffer.clear();
        final int[] pixelValues = TextureLoader.getImagePixels(image);
        pixelBuffer.put(pixelValues);
        pixelBuffer.position(0).limit(pixelValues.length);
        return pixelBuffer;
    }



    public void addAtlas(int depth, int size, IntBuffer pixels) {
        System.out.println("adding atlas with depth " + depth);
        bind();
        GLStateManager.glTexSubImage3D(
            GL30.GL_TEXTURE_2D_ARRAY,
            0,
            0, 0, depth,
            size, size,
            1,
            GL12.GL_BGRA,
            GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
            pixels
        );
        unbind();
        initializedLayers[depth] = true;
        try {
            saveLayerAsPNG(depth, 256, 256, new File("test" + depth + ".png"));
        } catch (Exception ignored) {}
    }

    public boolean hasLayer(int atlasId) {
        return initializedLayers[atlasId];
    }

    public void saveLayerAsPNG(
        int layer,
        int width,
        int height,
        File outputFile
    ) throws IOException {

        // Bind texture array
        bind();

        // Allocate buffer
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);

        // Read one layer from the texture array
        GLStateManager.glGetTextureSubImage(
            id,
            0,
            0, 0, layer,
            width, height, 1,
            GL12.GL_BGRA,
            GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
            width * height * 4,
            memAddress(pixels)
        );


        // Create image
        BufferedImage image =
            new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        int[] row = new int[width];

        for (int y = 0; y < height; y++) {

            int rowOffset = y * width * 4;

            for (int x = 0; x < width; x++) {

                int i = rowOffset + x * 4;

                int b = pixels.get(i) & 0xFF;
                int g = pixels.get(i + 1) & 0xFF;
                int r = pixels.get(i + 2) & 0xFF;
                int a = pixels.get(i + 3) & 0xFF;

                row[x] =
                    (a << 24) |
                        (r << 16) |
                        (g << 8)  |
                        b;
            }

            // Flip vertically because OpenGL origin is bottom-left
            image.setRGB(0, height - 1 - y, width, 1, row, 0, width);
        }

        // Save PNG
        ImageIO.write(image, "PNG", outputFile);

        // Unbind
        unbind();
    }

    public void bind() {
        GLStateManager.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, id);
    }

    public void unbind() {
        GLStateManager.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, 0);
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
}
