package com.prupe.mcpatcher.hd;

import com.prupe.mcpatcher.MCLogger;
import com.prupe.mcpatcher.MCPatcherUtils;
import com.prupe.mcpatcher.mal.resource.GLAPI;
import com.prupe.mcpatcher.mal.resource.PropertiesFile;
import com.prupe.mcpatcher.mal.resource.ResourceList;
import com.prupe.mcpatcher.mal.resource.TexturePackAPI;
import com.prupe.mcpatcher.mal.resource.TexturePackChangeHandler;
import jss.notfine.config.MCPatcherForgeConfig;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.gtnewhorizons.angelica.glsm.managers.GLTextureManager.glGetTexLevelParameteri;

public class CustomAnimation implements Comparable<CustomAnimation> {

    private static final MCLogger logger = MCLogger.getLogger(MCLogger.Category.EXTENDED_HD, "Animation");

    private static final boolean enable = MCPatcherForgeConfig.ExtendedHD.animations;
    private static final Set<PropertiesFile> pending = new HashSet<>();
    private static final List<CustomAnimation> animations = new ArrayList<>();

    private final PropertiesFile properties;
    private final ResourceLocation dstName;
    private final ResourceLocation srcName;
    private final int mipmapLevel;
    private final ByteBuffer imageData;
    private final int x;
    private final int y;
    private final int w;
    private final int h;

    private int currentFrame;
    private int currentDelay;
    private int numFrames;
    private int[] tileOrder;
    private int[] tileDelay;
    private final int numTiles;
    private boolean error;

    static {
        TexturePackChangeHandler.register(new TexturePackChangeHandler(MCPatcherUtils.EXTENDED_HD, 1) {

            @Override
            public void beforeChange() {
                if (!pending.isEmpty()) {
                    logger.fine("%d animations were never registered:", pending.size());
                    for (PropertiesFile properties : pending) {
                        logger.fine("  %s", properties);
                    }
                    pending.clear();
                }
                animations.clear();
                MipmapHelper.reset();
                FancyDial.clearAll();
            }

            @Override
            public void afterChange() {
                if (enable) {
                    for (ResourceLocation resource : ResourceList.getInstance()
                        .listResources(TexturePackAPI.MCPATCHER_SUBDIR + "anim", ".properties", false)) {
                        PropertiesFile properties = PropertiesFile.get(logger, resource);
                        if (properties != null) {
                            pending.add(properties);
                        }
                    }
                }
                FancyDial.registerAnimations();
            }
        });
    }

    public static void updateAll() {
        if (!pending.isEmpty()) {
            try {
                checkPendingAnimations();
            } catch (Throwable e) {
                e.printStackTrace();
                logger.error("%d remaining animations cleared", pending.size());
                pending.clear();
            }
        }
        for (CustomAnimation animation : animations) {
            animation.update();
        }
    }

    private static void checkPendingAnimations() {
        List<PropertiesFile> done = new ArrayList<>();
        for (PropertiesFile properties : pending) {
            ResourceLocation textureName = properties.getResourceLocation("to", "");
            if (TexturePackAPI.isTextureLoaded(textureName)) {
                addStrip(properties);
                done.add(properties);
            }
        }
        if (!done.isEmpty()) {
            for (PropertiesFile name : done) {
                pending.remove(name);
            }
            Collections.sort(animations);
        }
    }

    private static void addStrip(PropertiesFile properties) {
        ResourceLocation dstName = properties.getResourceLocation("to", "");
        if (dstName == null) {
            properties.error("missing to= property");
            return;
        }
        ResourceLocation srcName = properties.getResourceLocation("from", "");
        if (srcName == null) {
            properties.error("missing from= property");
            return;
        }
        BufferedImage srcImage = TexturePackAPI.getImage(srcName);
        if (srcImage == null) {
            properties.error("image %s not found in texture pack", srcName);
            return;
        }
        int x = properties.getInt("x", 0);
        int y = properties.getInt("y", 0);
        int w = properties.getInt("w", 0);
        int h = properties.getInt("h", 0);
        if (dstName.toString()
            .startsWith("minecraft:textures/atlas/")) {
            properties.error("animations cannot have a target of %s", dstName);
            return;
        }
        if (x < 0 || y < 0 || w <= 0 || h <= 0) {
            properties.error("%s has invalid dimensions x=%d,y=%d,w=%d,h=%d", srcName, x, y, w, h);
            return;
        }
        TexturePackAPI.bindTexture(dstName);
        int dstWidth = glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int dstHeight = glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        int levels = MipmapHelper.getMipmapLevelsForCurrentTexture();
        if (x + w > dstWidth || y + h > dstHeight) {
            properties.error(
                "%s dimensions x=%d,y=%d,w=%d,h=%d exceed %s size %dx%d",
                srcName,
                x,
                y,
                w,
                h,
                dstName,
                dstWidth,
                dstHeight);
            return;
        }
        int width = srcImage.getWidth();
        int height = srcImage.getHeight();
        if (width != w) {
            srcImage = resizeImage(srcImage, w);
            width = srcImage.getWidth();
            height = srcImage.getHeight();
        }
        if (width != w || height < h) {
            properties.error("%s dimensions %dx%d do not match %dx%d", srcName, width, height, w, h);
            return;
        }
        ByteBuffer imageData = ByteBuffer.allocateDirect(4 * width * height);
        int[] argb = new int[width * height];
        byte[] rgba = new byte[4 * width * height];
        srcImage.getRGB(0, 0, width, height, argb, 0, width);
        ARGBtoRGBA(argb, rgba);
        imageData.put(rgba)
            .flip();
        for (int mipmapLevel = 0; mipmapLevel <= levels; mipmapLevel++) {
            add(new CustomAnimation(properties, srcName, dstName, mipmapLevel, x, y, w, h, imageData, height / h));
            if (((x | y | w | h) & 0x1) != 0 || w <= 0 || h <= 0) {
                break;
            }
            ByteBuffer newImage = ByteBuffer.allocateDirect(width * height);
            MipmapHelper.scaleHalf(imageData.asIntBuffer(), width, height, newImage.asIntBuffer(), 0);
            imageData = newImage;
            width >>= 1;
            height >>= 1;
            x >>= 1;
            y >>= 1;
            w >>= 1;
            h >>= 1;
        }
    }

    private static void add(CustomAnimation animation) {
        if (animation != null) {
            animations.add(animation);
            if (animation.mipmapLevel == 0) {
                logger.fine("new %s", animation);
            }
        }
    }

    private CustomAnimation(PropertiesFile properties, ResourceLocation srcName, ResourceLocation dstName,
        int mipmapLevel, int x, int y, int w, int h, ByteBuffer imageData, int numFrames) {
        this.properties = properties;
        this.srcName = srcName;
        this.dstName = dstName;
        this.mipmapLevel = mipmapLevel;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.imageData = imageData;
        this.numFrames = numFrames;
        currentFrame = -1;
        numTiles = numFrames;
        loadProperties(properties);
    }

    void update() {
        if (error) {
            return;
        }
        int texture = TexturePackAPI.getTextureIfLoaded(dstName);
        if (texture < 0) {
            return;
        }
        if (--currentDelay > 0) {
            return;
        }
        if (++currentFrame >= numFrames) {
            currentFrame = 0;
        }
        GLAPI.glBindTexture(texture);
        update(texture, 0, 0);
        int glError = GL11.glGetError();
        if (glError != 0) {
            logger.severe("%s: %s", this, GLU.gluErrorString(glError));
            error = true;
            return;
        }
        currentDelay = getDelay();
    }

    public int compareTo(CustomAnimation o) {
        return dstName.toString()
            .compareTo(o.dstName.toString());
    }

    @Override
    public String toString() {
        return String.format(
            "CustomAnimation{%s %s %dx%d -> %s%s @ %d,%d (%d frames)}",
            properties,
            srcName,
            w,
            h,
            dstName,
            (mipmapLevel > 0 ? "#" + mipmapLevel : ""),
            x,
            y,
            numFrames);
    }

    private static void ARGBtoRGBA(int[] src, byte[] dest) {
        for (int i = 0; i < src.length; i++) {
            int v = src[i];
            dest[(i * 4) + 3] = (byte) ((v >> 24) & 0xff);
            dest[(i * 4) + 0] = (byte) ((v >> 16) & 0xff);
            dest[(i * 4) + 1] = (byte) ((v >> 8) & 0xff);
            dest[(i * 4) + 2] = (byte) ((v >> 0) & 0xff);
        }
    }

    private static BufferedImage resizeImage(BufferedImage image, int width) {
        if (width == image.getWidth()) {
            return image;
        }
        int height = image.getHeight() * width / image.getWidth();
        logger.finer("resizing to %dx%d", width, height);
        BufferedImage newImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics2D = newImage.createGraphics();
        graphics2D.drawImage(image, 0, 0, width, height, null);
        return newImage;
    }

    private void loadProperties(PropertiesFile properties) {
        loadTileOrder(properties);
        if (tileOrder == null) {
            tileOrder = new int[numFrames];
            for (int i = 0; i < numFrames; i++) {
                tileOrder[i] = i % numTiles;
            }
        }
        tileDelay = new int[numFrames];
        loadTileDelay(properties);
        for (int i = 0; i < numFrames; i++) {
            tileDelay[i] = Math.max(tileDelay[i], 1);
        }
    }

    private void loadTileOrder(PropertiesFile properties) {
        if (properties == null) {
            return;
        }
        int i = 0;
        for (; getIntValue(properties, "tile.", i) != null; i++) {}
        if (i > 0) {
            numFrames = i;
            tileOrder = new int[numFrames];
            for (i = 0; i < numFrames; i++) {
                tileOrder[i] = Math.abs(getIntValue(properties, "tile.", i)) % numTiles;
            }
        }
    }

    private void loadTileDelay(PropertiesFile properties) {
        if (properties == null) {
            return;
        }
        Integer defaultValue = getIntValue(properties, "duration");
        for (int i = 0; i < numFrames; i++) {
            Integer value = getIntValue(properties, "duration.", i);
            if (value != null) {
                tileDelay[i] = value;
            } else if (defaultValue != null) {
                tileDelay[i] = defaultValue;
            }
        }
    }

    private static Integer getIntValue(PropertiesFile properties, String key) {
        try {
            String value = properties.getString(key, "");
            if (value != null && value.matches("^\\d+$")) {
                return Integer.parseInt(value);
            }
        } catch (NumberFormatException ignore) {}
        return null;
    }

    private static Integer getIntValue(PropertiesFile properties, String prefix, int index) {
        return getIntValue(properties, prefix + index);
    }

    // Without the cast the code won't compile
    @SuppressWarnings("RedundantCast")
    private void update(int texture, int dx, int dy) {
        GL11.glTexSubImage2D(
            GL11.GL_TEXTURE_2D,
            mipmapLevel,
            x + dx,
            y + dy,
            w,
            h,
            GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE,
            (ByteBuffer) imageData.position(4 * w * h * tileOrder[currentFrame]));
    }

    private int getDelay() {
        return tileDelay[currentFrame];
    }
}
