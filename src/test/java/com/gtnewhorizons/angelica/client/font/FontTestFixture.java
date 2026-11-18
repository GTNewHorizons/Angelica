package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizon.gtnhlib.util.font.FontRendering;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.FontConfig;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.glsm.testutil.TestPaths;
import com.gtnewhorizons.angelica.mixins.interfaces.FontRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.DefaultResourcePack;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class FontTestFixture implements AutoCloseable {

    public static final class TestFont extends FontRenderer implements FontRendererAccessor {

        public BatchingFontRenderer batcher;
        int texture;

        public TestFont() {
            super(null, null, null, false);
        }

        @Override
        public int angelica$drawStringBatched(String text, int x, int y, int argb, boolean dropShadow) {
            return 0;
        }

        @Override
        public BatchingFontRenderer angelica$getBatcher() {
            return batcher;
        }

        @Override
        public void angelica$bindTexture(ResourceLocation location) {
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        }
    }

    public static final class HexResets implements FontRendering.TextPreprocessor {

        @Override
        public String apply(String s) {
            return s;
        }

        public boolean hexColorResetsStyles() {
            return true;
        }
    }

    public static final ResourceLocation ASCII = new ResourceLocation("textures/font/ascii.png");

    public final TestFont font = Reflect.allocate(TestFont.class);
    public final Minecraft minecraft = mock(Minecraft.class);
    private final List<Field> configFields = new ArrayList<>();
    private final List<Object> configValues = new ArrayList<>();
    private final Object preprocessor = Reflect.getStatic(FontRendering.class, "textPreprocessor");
    private final Minecraft savedMinecraft = Reflect.getStatic(Minecraft.class, "theMinecraft");

    public FontTestFixture(Path dir) throws IOException {
        for (Class<?> config : new Class<?>[] { FontConfig.class, AngelicaConfig.class }) {
            for (Field field : config.getFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) continue;
                configFields.add(field);
                configValues.add(Reflect.getStatic(config, field.getName()));
            }
        }
        FontConfig.enableCustomFont = false;
        FontConfig.enableModernFont = false;
        FontConfig.enableGlyphReplacements = false;
        FontConfig.fontAAMode = 0;
        FontConfig.glyphScale = 1.0f;
        FontConfig.glyphAspect = 0.0f;
        AngelicaConfig.enableRGBColors = true;
        FontRendering.setTextPreprocessor(null);

        final byte[] glyphSizes = new byte[65536];
        for (int i = 0; i < glyphSizes.length; i++) glyphSizes[i] = (byte) (0x07 + (i % 5) * 0x11);
        final IReloadableResourceManager resources = mock(IReloadableResourceManager.class);
        when(resources.getResourceDomains()).thenReturn(Set.of("angelica"));
        when(resources.getResource(any(ResourceLocation.class))).thenAnswer(invocation -> {
            final String path = invocation.<ResourceLocation>getArgument(0).getResourcePath();
            final byte[] bytes = path.equals("font/glyph_sizes.bin") ? glyphSizes : Files.readAllBytes(TestPaths.resolve("src/main/resources/assets/angelica/" + path));
            final IResource resource = mock(IResource.class);
            when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(bytes));
            return resource;
        });
        Reflect.set(minecraft, "mcDataDir", Files.createDirectories(dir.resolve("mc")).toFile());
        Reflect.set(minecraft, "mcDefaultResourcePack", mock(DefaultResourcePack.class));
        when(minecraft.getResourceManager()).thenReturn(resources);
        Reflect.setStatic(Minecraft.class, "theMinecraft", minecraft);
        font.FONT_HEIGHT = 9;
    }

    public static int[] charWidths() {
        final int[] widths = new int[256];
        for (int i = 0; i < widths.length; i++) widths[i] = 4 + i % 5;
        return widths;
    }

    public BatchingFontRenderer batcher() {
        if (font.batcher == null) font.batcher = new BatchingFontRenderer(font, charWidths(), new int[32], ASCII);
        if (font.texture == 0) {
            font.texture = GL11.glGenTextures();
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, font.texture);
            final ByteBuffer pixels = BufferUtils.createByteBuffer(4 * 4 * 4);
            while (pixels.hasRemaining()) pixels.put((byte) 0xFF);
            pixels.flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 4, 4, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }
        return font.batcher;
    }

    @Override
    public void close() {
        if (font.batcher != null) font.batcher.resetAfterCrash();
        if (font.texture != 0) GL11.glDeleteTextures(font.texture);
        Reflect.setStatic(Minecraft.class, "theMinecraft", savedMinecraft);
        for (int i = 0; i < configFields.size(); i++) Reflect.setStatic(configFields.get(i).getDeclaringClass(), configFields.get(i).getName(), configValues.get(i));
        Reflect.setStatic(FontRendering.class, "textPreprocessor", preprocessor);
    }
}
