package com.prupe.mcpatcher.hd;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.imageio.ImageIO;

import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureClock;
import net.minecraft.client.renderer.texture.TextureCompass;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.util.glu.GLU;

import com.prupe.mcpatcher.MCLogger;
import com.prupe.mcpatcher.MCPatcherUtils;
import com.prupe.mcpatcher.mal.resource.BlendMethod;
import com.prupe.mcpatcher.mal.resource.GLAPI;
import com.prupe.mcpatcher.mal.resource.PropertiesFile;
import com.prupe.mcpatcher.mal.resource.TexturePackAPI;
import com.prupe.mcpatcher.mal.util.InputHandler;

import mist475.mcpatcherforge.config.MCPatcherForgeConfig;

public class FancyDial {

    private static final MCLogger logger = MCLogger.getLogger(MCLogger.Category.EXTENDED_HD, "Animation");

    private static final double ANGLE_UNSET = Double.MAX_VALUE;
    private static final int NUM_SCRATCH_TEXTURES = 3;

    private static final boolean fboSupported = GLContext.getCapabilities().GL_EXT_framebuffer_object;
    private static final boolean gl13Supported = GLContext.getCapabilities().OpenGL13;
    private static final boolean enableCompass = MCPatcherForgeConfig.instance().fancyCompass;
    private static final boolean enableClock = MCPatcherForgeConfig.instance().fancyClock;
    private static final boolean useGL13 = gl13Supported && MCPatcherForgeConfig.instance().useGL13;
    private static final boolean useScratchTexture = MCPatcherForgeConfig.instance().useScratchTexture;
    private static final int glAttributes;
    private static boolean initialized;
    private static boolean active;
    private static final int drawList = GL11.glGenLists(1);

    private static final Map<TextureAtlasSprite, ResourceLocation> setupInfo = new IdentityHashMap<>();
    private static final Map<TextureAtlasSprite, FancyDial> instances = new IdentityHashMap<>();
    private static int warnCount;

    private final TextureAtlasSprite icon;
    private final String name;
    private final int x0;
    private final int y0;
    private final int width;
    private final int height;
    private final ByteBuffer scratchBuffer;
    private final FBO[] scratchFBO = new FBO[NUM_SCRATCH_TEXTURES];
    private FBO itemsFBO;
    private int scratchIndex;
    private Map<Double, ByteBuffer> itemFrames = new TreeMap<>();
    private int outputFrames;

    private boolean ok;
    private double lastAngle = ANGLE_UNSET;
    private boolean lastItemFrameRenderer;

    private final List<Layer> layers = new ArrayList<>();

    private InputHandler keyboard;
    private static final float STEP = 0.01f;
    private float scaleXDelta;
    private float scaleYDelta;
    private float offsetXDelta;
    private float offsetYDelta;

    static {
        logger.config("fbo: supported=%s", fboSupported);
        logger.config("GL13: supported=%s, enabled=%s", gl13Supported, useGL13);

        int bits = GL11.GL_VIEWPORT_BIT | GL11.GL_SCISSOR_BIT | GL11.GL_DEPTH_BITS | GL11.GL_LIGHTING_BIT;
        if (useGL13) {
            bits |= GL13.GL_MULTISAMPLE_BIT;
        }
        glAttributes = bits;

        GL11.glNewList(drawList, GL11.GL_COMPILE);
        drawBox();
        GL11.glEndList();
    }

    public static void setup(TextureAtlasSprite icon) {
        if (!fboSupported) {
            return;
        }
        String name = icon.getIconName()
            .replaceFirst("^minecraft:items/", "");
        if ("compass".equals(name)) {
            if (!enableCompass) {
                return;
            }
        } else if ("clock".equals(name)) {
            if (!enableClock) {
                return;
            }
        } else {
            logger.warning("ignoring custom animation for %s not compass or clock", name);
            return;
        }
        ResourceLocation resource = TexturePackAPI.newMCPatcherResourceLocation("dial/" + name + ".properties");
        if (TexturePackAPI.hasResource(resource)) {
            logger.fine("found custom %s (%s)", name, resource);
            setupInfo.put(icon, resource);
            active = true;
        }
    }

    public static boolean update(TextureAtlasSprite icon, boolean itemFrameRenderer) {
        if (!initialized) {
            logger.finer("deferring %s update until initialization finishes", icon.getIconName());
            return false;
        }
        if (!active) {
            return false;
        }
        int oldFB = GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);
        if (oldFB != 0 && warnCount < 10) {
            logger.finer("rendering %s while non-default framebuffer %d is active", icon.getIconName(), oldFB);
            warnCount++;
        }
        int oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        try {
            FancyDial instance = getInstance(icon);
            return instance != null && instance.render(itemFrameRenderer);
        } finally {
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, oldFB);
            GLAPI.glBindTexture(oldTexture);
        }
    }

    static void clearAll() {
        logger.finer("FancyDial.clearAll");
        if (initialized) {
            active = false;
            setupInfo.clear();
        }
        for (FancyDial instance : instances.values()) {
            if (instance != null) {
                instance.finish();
            }
        }
        instances.clear();
        initialized = true;
    }

    static void registerAnimations() {
        ITextureObject texture = TexturePackAPI.getTextureObject(TexturePackAPI.ITEMS_PNG);
        if (texture instanceof TextureMap map) {
            List<TextureAtlasSprite> animations = map.listAnimatedSprites;
            for (FancyDial instance : instances.values()) {
                instance.registerAnimation(animations);
            }
        }
    }

    void registerAnimation(List<TextureAtlasSprite> animations) {
        if (animations.contains(icon)) {
            return;
        }
        animations.add(icon);
        if (icon.framesTextureData == null) {
            icon.framesTextureData = new ArrayList<>();
        }
        if (icon.framesTextureData.isEmpty()) {
            /*
             * TODO: figure out how to handle the code assuming int[][] <-> int[] due to 1.6 -> 1.7
             * int[] dummyRGB = new int[width * height];
             * Arrays.fill(dummyRGB, 0xffff00ff);
             */
            int[][] dummyRGB = new int[width * height][];
            Arrays.fill(dummyRGB[0], 0xffff00ff);
            icon.framesTextureData.add(dummyRGB);
        }
        logger.fine("registered %s animation", name);
    }

    private static FancyDial getInstance(TextureAtlasSprite icon) {
        if (instances.containsKey(icon)) {
            return instances.get(icon);
        }
        ResourceLocation resource = setupInfo.remove(icon);
        instances.put(icon, null);
        if (resource == null) {
            return null;
        }
        PropertiesFile properties = PropertiesFile.get(logger, resource);
        if (properties == null) {
            return null;
        }
        try {
            FancyDial instance = new FancyDial(icon, properties);
            if (instance.ok) {
                instances.put(icon, instance);
                return instance;
            }
            instance.finish();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    private FancyDial(TextureAtlasSprite icon, PropertiesFile properties) {
        this.icon = icon;
        name = icon.getIconName();
        x0 = icon.getOriginX();
        y0 = icon.getOriginY();
        width = icon.getIconWidth();
        height = icon.getIconHeight();
        scratchBuffer = ByteBuffer.allocateDirect(4 * width * height);

        int itemsTexture = TexturePackAPI.getTextureIfLoaded(TexturePackAPI.ITEMS_PNG);
        if (itemsTexture < 0) {
            logger.severe("could not get items texture");
            return;
        }
        itemsFBO = new FBO(itemsTexture, x0, y0, width, height);

        if (useScratchTexture) {
            logger.fine("rendering %s to %dx%d scratch texture", name, width, height);
            for (int i = 0; i < scratchFBO.length; i++) {
                scratchFBO[i] = new FBO(width, height);
            }
        } else {
            logger.fine("rendering %s directly to atlas", name);
        }

        boolean debug = false;
        for (int i = 0;; i++) {
            Layer layer = newLayer(properties, "." + i);
            if (layer == null) {
                if (i > 0) {
                    break;
                }
                continue;
            }
            layers.add(layer);
            debug |= layer.debug;
            logger.fine("  new %s", layer);
        }
        keyboard = new InputHandler(name, debug);
        if (layers.size() < 2) {
            logger.error("custom %s needs at least two layers defined", name);
            return;
        }

        outputFrames = properties.getInt("outputFrames", 0);

        int glError = GL11.glGetError();
        if (glError != 0) {
            logger.severe("%s during %s setup", GLU.gluErrorString(glError), name);
            return;
        }
        ok = true;
    }

    private boolean render(boolean itemFrameRenderer) {
        if (!ok) {
            return false;
        }

        if (!itemFrameRenderer) {
            boolean changed = true;
            if (!keyboard.isEnabled()) {
                changed = false;
            } else if (keyboard.isKeyPressed(Keyboard.KEY_NUMPAD2)) {
                scaleYDelta -= STEP;
            } else if (keyboard.isKeyPressed(Keyboard.KEY_NUMPAD8)) {
                scaleYDelta += STEP;
            } else if (keyboard.isKeyPressed(Keyboard.KEY_NUMPAD4)) {
                scaleXDelta -= STEP;
            } else if (keyboard.isKeyPressed(Keyboard.KEY_NUMPAD6)) {
                scaleXDelta += STEP;
            } else if (keyboard.isKeyPressed(Keyboard.KEY_DOWN)) {
                offsetYDelta += STEP;
            } else if (keyboard.isKeyPressed(Keyboard.KEY_UP)) {
                offsetYDelta -= STEP;
            } else if (keyboard.isKeyPressed(Keyboard.KEY_LEFT)) {
                offsetXDelta -= STEP;
            } else if (keyboard.isKeyPressed(Keyboard.KEY_RIGHT)) {
                offsetXDelta += STEP;
            } else if (keyboard.isKeyPressed(Keyboard.KEY_MULTIPLY)) {
                scaleXDelta = scaleYDelta = offsetXDelta = offsetYDelta = 0.0f;
            } else {
                changed = false;
            }
            if (changed) {
                logger.info("");
                logger.info("scaleX  %+f", scaleXDelta);
                logger.info("scaleY  %+f", scaleYDelta);
                logger.info("offsetX %+f", offsetXDelta);
                logger.info("offsetY %+f", offsetYDelta);
                lastAngle = ANGLE_UNSET;
            }

            if (outputFrames > 0) {
                writeCustomImage();
                outputFrames = 0;
            }
        }

        double angle = getAngle(icon);
        if (!useScratchTexture) {
            // render directly to items.png
            if (angle != lastAngle) {
                renderToItems(angle);
                lastAngle = angle;
            }
        } else if (itemFrameRenderer) {
            // look in itemFrames cache first
            ByteBuffer buffer = itemFrames.get(angle);
            if (buffer == null) {
                logger.fine("rendering %s at angle %f for item frame", name, angle);
                buffer = ByteBuffer.allocateDirect(width * height * 4);
                renderToItems(angle);
                itemsFBO.read(buffer);
                itemFrames.put(angle, buffer);
            } else {
                itemsFBO.write(buffer);
            }
            lastItemFrameRenderer = true;
        } else if (lastAngle == ANGLE_UNSET) {
            // first time rendering - render all N copies
            for (FBO fbo : scratchFBO) {
                renderToFB(angle, fbo);
            }
            scratchFBO[0].read(scratchBuffer);
            itemsFBO.write(scratchBuffer);
            lastAngle = angle;
            scratchIndex = 0;
        } else if (lastItemFrameRenderer || angle != lastAngle) {
            // render to buffer i + 1
            // update items.png from buffer i
            int nextIndex = (scratchIndex + 1) % NUM_SCRATCH_TEXTURES;
            if (angle != lastAngle) {
                renderToFB(angle, scratchFBO[nextIndex]);
                scratchFBO[scratchIndex].read(scratchBuffer);
            }
            itemsFBO.write(scratchBuffer);
            lastAngle = angle;
            scratchIndex = nextIndex;
            lastItemFrameRenderer = false;
        }

        int glError = GL11.glGetError();
        if (glError != 0) {
            logger.severe("%s during %s update", GLU.gluErrorString(glError), name);
            ok = false;
        }
        return ok;
    }

    private void writeCustomImage() {
        try {
            BufferedImage image = new BufferedImage(width, outputFrames * height, BufferedImage.TYPE_INT_ARGB);
            IntBuffer intBuffer = scratchBuffer.asIntBuffer();
            int[] argb = new int[width * height];
            File path = MCPatcherUtils.getGamePath("custom_" + name + ".png");
            logger.info("generating %d %s frames", outputFrames, name);
            for (int i = 0; i < outputFrames; i++) {
                renderToItems(i * (360.0 / outputFrames));
                itemsFBO.read(scratchBuffer);
                intBuffer.position(0);
                for (int j = 0; j < argb.length; j++) {
                    switch (MipmapHelper.TEX_FORMAT) {
                        case GL12.GL_BGRA:
                            int bgra = intBuffer.get(j);
                            argb[j] = (bgra << 24) | ((bgra & 0xff00) << 8) | ((bgra & 0xff0000) >> 8) | (bgra >>> 24);
                            break;

                        default:
                            if (i == 0 && j == 0) {
                                logger.warning(
                                    "unhandled texture format %d, color channels may be incorrect",
                                    MipmapHelper.TEX_FORMAT);
                            }
                            // fall through

                        case GL11.GL_RGBA:
                            argb[j] = Integer.rotateRight(intBuffer.get(j), 8);
                            break;
                    }
                }
                image.setRGB(0, i * height, width, height, argb, 0, width);
            }
            ImageIO.write(image, "png", path);
            logger.info("wrote %dx%d %s", image.getWidth(), image.getHeight(), path.getPath());
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void renderToItems(double angle) {
        renderToFB(angle, itemsFBO);
    }

    private void renderToFB(double angle, FBO fbo) {
        if (fbo != null) {
            fbo.bind();
            renderImpl(angle);
            fbo.unbind();
        }
    }

    private void renderImpl(double angle) {
        for (Layer layer : layers) {
            layer.blendMethod.applyBlending();
            GL11.glPushMatrix();
            TexturePackAPI.bindTexture(layer.textureName);
            float offsetX = layer.offsetX;
            float offsetY = layer.offsetY;
            float scaleX = layer.scaleX;
            float scaleY = layer.scaleY;
            if (layer.debug) {
                offsetX += offsetXDelta;
                offsetY += offsetYDelta;
                scaleX += scaleXDelta;
                scaleY += scaleYDelta;
            }
            GL11.glTranslatef(offsetX, offsetY, 0.0f);
            GL11.glScalef(scaleX, scaleY, 1.0f);
            float layerAngle = (float) (angle * layer.rotationMultiplier + layer.rotationOffset);
            GL11.glRotatef(layerAngle, 0.0f, 0.0f, 1.0f);
            GL11.glCallList(drawList);
            GL11.glPopMatrix();
        }
    }

    private static void drawBox() {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0f, 0.0f);
        GL11.glVertex3f(-1.0f, -1.0f, 0.0f);
        GL11.glTexCoord2f(1.0f, 0.0f);
        GL11.glVertex3f(1.0f, -1.0f, 0.0f);
        GL11.glTexCoord2f(1.0f, 1.0f);
        GL11.glVertex3f(1.0f, 1.0f, 0.0f);
        GL11.glTexCoord2f(0.0f, 1.0f);
        GL11.glVertex3f(-1.0f, 1.0f, 0.0f);
        GL11.glEnd();
    }

    private void finish() {
        for (int i = 0; i < scratchFBO.length; i++) {
            if (scratchFBO[i] != null) {
                scratchFBO[i].delete();
                scratchFBO[i] = null;
            }
        }
        if (itemsFBO != null) {
            itemsFBO.delete();
            itemsFBO = null;
        }
        itemFrames.clear();
        layers.clear();
        ok = false;
    }

    @Override
    public String toString() {
        return String.format("FancyDial{%s, %dx%d @ %d,%d}", name, width, height, x0, y0);
    }

    @Override
    protected void finalize() throws Throwable {
        finish();
        super.finalize();
    }

    private static double getAngle(IIcon icon) {
        if (icon instanceof TextureCompass) {
            return ((TextureCompass) icon).currentAngle * 180.0 / Math.PI;
        } else if (icon instanceof TextureClock) {
            return ((TextureClock) icon).field_94239_h * 360.0; // currentAngle
        } else {
            return 0.0;
        }
    }

    Layer newLayer(PropertiesFile properties, String suffix) {
        ResourceLocation textureResource = properties.getResourceLocation("source" + suffix, "");
        if (textureResource == null) {
            return null;
        }
        if (!TexturePackAPI.hasResource(textureResource)) {
            properties.error("could not read %s", textureResource);
            return null;
        }
        float scaleX = properties.getFloat("scaleX" + suffix, 1.0f);
        float scaleY = properties.getFloat("scaleY" + suffix, 1.0f);
        float offsetX = properties.getFloat("offsetX" + suffix, 0.0f);
        float offsetY = properties.getFloat("offsetY" + suffix, 0.0f);
        float angleMultiplier = properties.getFloat("rotationSpeed" + suffix, 0.0f);
        float angleOffset = properties.getFloat("rotationOffset" + suffix, 0.0f);
        String blend = properties.getString("blend" + suffix, "alpha");
        BlendMethod blendMethod = BlendMethod.parse(blend);
        if (blendMethod == null) {
            properties.error("unknown blend method %s", blend);
            return null;
        }
        boolean debug = properties.getBoolean("debug" + suffix, false);
        return new Layer(
            textureResource,
            scaleX,
            scaleY,
            offsetX,
            offsetY,
            angleMultiplier,
            angleOffset,
            blendMethod,
            debug);
    }

    private class Layer {

        final ResourceLocation textureName;
        final float scaleX;
        final float scaleY;
        final float offsetX;
        final float offsetY;
        final float rotationMultiplier;
        final float rotationOffset;
        final BlendMethod blendMethod;
        final boolean debug;

        Layer(ResourceLocation textureName, float scaleX, float scaleY, float offsetX, float offsetY,
            float rotationMultiplier, float rotationOffset, BlendMethod blendMethod, boolean debug) {
            this.textureName = textureName;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.rotationMultiplier = rotationMultiplier;
            this.rotationOffset = rotationOffset;
            this.blendMethod = blendMethod;
            this.debug = debug;
        }

        @Override
        public String toString() {
            return String.format(
                "Layer{%s %f %f %+f %+f x%f}",
                textureName,
                scaleX,
                scaleY,
                offsetX,
                offsetY,
                rotationMultiplier);
        }
    }

    private static class FBO {

        private final int texture;
        private final boolean ownTexture;
        private final int x0;
        private final int y0;
        private final int width;
        private final int height;
        private final int frameBuffer;

        private boolean lightmapEnabled;
        private boolean deleted;

        FBO(int width, int height) {
            this(blankTexture(width, height), true, 0, 0, width, height);
        }

        FBO(int texture, int x0, int y0, int width, int height) {
            this(texture, false, x0, y0, width, height);
        }

        private FBO(int texture, boolean ownTexture, int x0, int y0, int width, int height) {
            this.texture = texture;
            this.ownTexture = ownTexture;
            this.x0 = x0;
            this.y0 = y0;
            this.width = width;
            this.height = height;

            frameBuffer = EXTFramebufferObject.glGenFramebuffersEXT();
            if (frameBuffer < 0) {
                throw new RuntimeException("could not get framebuffer object");
            }
            GLAPI.glBindTexture(texture);
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, frameBuffer);
            EXTFramebufferObject.glFramebufferTexture2DEXT(
                EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT,
                GL11.GL_TEXTURE_2D,
                texture,
                0);
        }

        void bind() {
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, frameBuffer);

            GL11.glPushAttrib(glAttributes);
            GL11.glViewport(x0, y0, width, height);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(x0, y0, width, height);

            lightmapEnabled = false;
            if (gl13Supported) {
                GL13.glActiveTexture(GL13.GL_TEXTURE1);
                lightmapEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
                if (lightmapEnabled) {
                    GL11.glDisable(GL11.GL_TEXTURE_2D);
                }
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
            }
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.01f);
            if (useGL13) {
                GL11.glDisable(GL13.GL_MULTISAMPLE);
            }

            GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(-1.0f, 1.0f, -1.0f, 1.0f, -1.0f, 1.0f);

            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
        }

        void unbind() {
            GL11.glPopAttrib();

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();

            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();

            if (lightmapEnabled) {
                GL13.glActiveTexture(GL13.GL_TEXTURE1);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
            }
            GL11.glEnable(GL11.GL_BLEND);
            GLAPI.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, 0);
        }

        void read(ByteBuffer buffer) {
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, frameBuffer);
            buffer.position(0);
            GL11.glReadPixels(x0, y0, width, height, MipmapHelper.TEX_FORMAT, MipmapHelper.TEX_DATA_TYPE, buffer);
        }

        void write(ByteBuffer buffer) {
            GLAPI.glBindTexture(texture);
            buffer.position(0);
            GL11.glTexSubImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                x0,
                y0,
                width,
                height,
                MipmapHelper.TEX_FORMAT,
                MipmapHelper.TEX_DATA_TYPE,
                buffer);
        }

        void delete() {
            if (!deleted) {
                deleted = true;
                if (ownTexture) {
                    GL11.glDeleteTextures(texture);
                }
                EXTFramebufferObject.glDeleteFramebuffersEXT(frameBuffer);
            }
        }

        @Override
        protected void finalize() throws Throwable {
            delete();
            super.finalize();
        }

        private static int blankTexture(int width, int height) {
            int texture = GL11.glGenTextures();
            MipmapHelper.setupTexture(texture, width, height, "scratch");
            return texture;
        }
    }
}
