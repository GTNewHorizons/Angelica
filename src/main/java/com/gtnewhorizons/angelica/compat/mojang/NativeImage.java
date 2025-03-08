package com.gtnewhorizons.angelica.compat.mojang;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import lombok.Getter;
import net.coderbot.iris.Iris;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

// TBD
public class NativeImage extends BufferedImage {
    @Getter
    private final Format format;
    @Getter
    private final int width;
    @Getter
    private final int height;
    @Getter
    private final int size;

    public NativeImage(int width, int height, boolean useStb) {
        this(NativeImage.Format.RGBA, width, height, useStb);
    }

    public NativeImage(Format format, int width, int height, boolean useStb) {
        super(width, height, BufferedImage.TYPE_INT_ARGB);
        this.format = format;
        this.width = width;
        this.height = height;
        this.size = width * height * format.components;
    }
    public NativeImage(Format format, int width, int height, BufferedImage image) {
        super(image.getColorModel(), image.getRaster(), image.isAlphaPremultiplied(), null);
        this.format = format;
        this.width = width;
        this.height = height;
        this.size = width * height * format.components;
    }

    public static NativeImage read(ByteBuffer buf) throws IOException {
        return read(new ByteBufferBackedInputStream(buf));
    }
    public static NativeImage read(InputStream inputStream)  throws IOException {
        BufferedImage image = ImageIO.read(inputStream);
        return new NativeImage(Format.RGBA, image.getWidth(), image.getHeight(), image);
    }

    public void downloadTexture(int level, boolean bl) {
//        this.checkAllocated();
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, format.components);
//        GlStateManager._getTexImage(3553, level, format.glFormat, GL11.GL_UNSIGNED_BYTE, this.pixels);

//        final int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_WIDTH);
//        final int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_HEIGHT);

        final IntBuffer buffer = MemoryUtilities.memAllocInt(size);

        try {
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, level, format.glFormat, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);

            int[] data = new int[size];
            buffer.get(data);
            setRGB(0, 0, width, height, data, 0, width);
        } finally {
            MemoryUtilities.memFree(buffer);
        }
    }

    public void writeToFile(File file) throws IOException{
        try {
            ImageIO.write(this, "png", file);
        } catch(IOException ioexception) {
            Iris.logger.info("[TextureDump] Unable to write: ", ioexception);
        }
    }
    public static int combine(int i, int j, int k, int l) {
        return (i & 255) << 24 | (j & 255) << 16 | (k & 255) << 8 | (l & 255);
    }

    public static int getA(int i) {
        return i >> 24 & 255;
    }

    public static int getR(int i) {
        return i >> 0 & 255;
    }

    public static int getG(int i) {
        return i >> 8 & 255;
    }

    public static int getB(int i) {
        return i >> 16 & 255;
    }

    public int getPixelRGBA(int x, int y) {
        return getRGB(x, y);
    }

    public void setPixelRGBA(int x, int y, int rgb) {
        setRGB(x, y, rgb);
    }


    public enum Format {
        RGBA(4, GL11.GL_RGBA, BufferedImage.TYPE_INT_ARGB),
        RGB(3, GL11.GL_RGB, BufferedImage.TYPE_INT_RGB);

        private final int components;
        private final int glFormat;
        private final int imageType;


        Format(int components, int glFormat, int imageType) {
            this.components = components;
            this.glFormat = glFormat;
            this.imageType = imageType;
        }
    }

}
