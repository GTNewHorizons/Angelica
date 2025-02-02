package com.prupe.mcpatcher.mob;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.prupe.mcpatcher.MCLogger;
import com.prupe.mcpatcher.mal.resource.PropertiesFile;
import com.prupe.mcpatcher.mal.resource.TexturePackAPI;
import com.prupe.mcpatcher.mal.util.InputHandler;

import jss.notfine.config.MCPatcherForgeConfig;

@Lwjgl3Aware
public class LineRenderer {

    private static final MCLogger logger = MCLogger.getLogger(MCLogger.Category.RANDOM_MOBS);

    private static final double D_WIDTH = 1.0 / 1024.0;
    private static final double D_POS = 1.0 / 256.0;

    private static final boolean enable = MCPatcherForgeConfig.RandomMobs.leashLine;
    private static final LineRenderer[] renderers = new LineRenderer[2];

    private final ResourceLocation texture;
    private final double width;
    private final double a;
    private final double b;
    private final double sx;
    private final double sy;
    private final double sz;
    private final int segments;
    private final double tileFactor;
    private final boolean active;
    private final InputHandler keyboard;

    private double plusWidth;
    private double plusTile;
    private double plusSX;
    private double plusSY;
    private double plusSZ;

    public static boolean renderLine(int type, double x, double y, double z, double dx, double dy, double dz) {
        LineRenderer renderer = renderers[type];
        return renderer != null && renderer.render(x, y, z, dx, dy, dz);
    }

    static void reset() {
        if (enable) {
            setup(0, "fishingline", 0.0075, 0.0, 0.25, 16);
            setup(1, "lead", 0.025, 4.0 / 3.0, 0.125, 24);
        }
    }

    private static void setup(int type, String name, double defaultWidth, double a, double b, int segments) {
        LineRenderer renderer = new LineRenderer(name, defaultWidth, a, b, segments);
        if (renderer.active) {
            logger.fine("using %s", renderer);
            renderers[type] = renderer;
        } else {
            logger.fine("%s not found", renderer);
            renderers[type] = null;
        }
    }

    private LineRenderer(String name, double width, double a, double b, int segments) {
        texture = TexturePackAPI.newMCPatcherResourceLocation("line/" + name + ".png");
        active = TexturePackAPI.hasResource(texture);
        PropertiesFile properties = PropertiesFile
            .getNonNull(logger, TexturePackAPI.transformResourceLocation(texture, ".png", ".properties"));
        this.width = properties.getDouble("width", width);
        this.a = properties.getDouble("a", a);
        this.b = properties.getDouble("b", b);
        this.sx = properties.getDouble("sx", 0.0);
        this.sy = properties.getDouble("sy", 0.0);
        this.sz = properties.getDouble("sz", 0.0);
        this.segments = properties.getInt("segments", segments);
        this.tileFactor = properties.getDouble("tileFactor", 24.0);
        keyboard = new InputHandler(name, properties.getBoolean("debug", false));
    }

    private boolean render(double x, double y, double z, double dx, double dy, double dz) {
        if (keyboard.isKeyDown(Keyboard.KEY_MULTIPLY)) {
            return false;
        }
        boolean changed = false;
        if (!keyboard.isEnabled()) {
            // nothing
        } else if (keyboard.isKeyPressed(Keyboard.KEY_ADD)) {
            changed = true;
            plusWidth += D_WIDTH;
        } else if (keyboard.isKeyPressed(Keyboard.KEY_SUBTRACT)) {
            changed = true;
            plusWidth -= D_WIDTH;
        } else if (keyboard.isKeyPressed(Keyboard.KEY_DIVIDE)) {
            changed = true;
            plusWidth = plusTile = plusSX = plusSY = plusSZ = 0.0;
        } else if (keyboard.isKeyPressed(Keyboard.KEY_NUMPAD3)) {
            changed = true;
            plusTile--;
        } else if (keyboard.isKeyPressed(Keyboard.KEY_NUMPAD9)) {
            changed = true;
            plusTile++;
        } else if (keyboard.isKeyDown(Keyboard.KEY_NUMPAD4)) {
            changed = true;
            plusSX -= D_POS;
        } else if (keyboard.isKeyDown(Keyboard.KEY_NUMPAD6)) {
            changed = true;
            plusSX += D_POS;
        } else if (keyboard.isKeyDown(Keyboard.KEY_NUMPAD1)) {
            changed = true;
            plusSY -= D_POS;
        } else if (keyboard.isKeyDown(Keyboard.KEY_NUMPAD7)) {
            changed = true;
            plusSY += D_POS;
        } else if (keyboard.isKeyDown(Keyboard.KEY_NUMPAD2)) {
            changed = true;
            plusSZ += D_POS;
        } else if (keyboard.isKeyDown(Keyboard.KEY_NUMPAD8)) {
            changed = true;
            plusSZ -= D_POS;
        }
        TexturePackAPI.bindTexture(texture);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        dx += sx + plusSX;
        dy += sy + plusSY;
        dz += sz + plusSZ;
        double x0 = x;
        double y0 = y + a + b;
        double z0 = z;
        double u0 = 0.0;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double t = tileFactor + plusTile;
        double w = width + plusWidth;
        if (changed) {
            logger.info(
                "%s: dx=%f, dy=%f, dz=%f, len=%f(*%d=%f), slen=%f",
                this,
                dx,
                dy,
                dz,
                len,
                (int) t,
                len * t,
                len * t / segments);
            System.out.printf("width=%f\n", w);
            System.out.printf("tileFactor=%f\n", t);
            System.out.printf("sx=%f\n", sx + plusSX);
            System.out.printf("sy=%f\n", sy + plusSY);
            System.out.printf("sz=%f\n", sz + plusSZ);
        }
        len *= t / segments;
        for (int i = 1; i <= segments; i++) {
            double s = i / (double) segments;
            double x1 = x + s * dx;
            double y1 = y + (s * s + s) * 0.5 * dy + a * (1.0 - s) + b;
            double z1 = z + s * dz;
            double u1 = (segments - i) * len;

            tessellator.addVertexWithUV(x0, y0, z0, u0, 1.0);
            tessellator.addVertexWithUV(x1, y1, z1, u1, 1.0);
            tessellator.addVertexWithUV(x1 + w, y1 + w, z1, u1, 0.0);
            tessellator.addVertexWithUV(x0 + w, y0 + w, z0, u0, 0.0);

            tessellator.addVertexWithUV(x0, y0 + w, z0, u0, 1.0);
            tessellator.addVertexWithUV(x1, y1 + w, z1, u1, 1.0);
            tessellator.addVertexWithUV(x1 + w, y1, z1 + w, u1, 0.0);
            tessellator.addVertexWithUV(x0 + w, y0, z0 + w, u0, 0.0);

            x0 = x1;
            y0 = y1;
            z0 = z1;
            u0 = u1;
        }
        tessellator.draw();
        GL11.glEnable(GL11.GL_CULL_FACE);
        return true;
    }

    @Override
    public String toString() {
        return "LineRenderer{" + texture + ", " + (width + plusWidth) + "}";
    }
}
