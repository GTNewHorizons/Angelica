package com.prupe.mcpatcher.renderpass;

import com.prupe.mcpatcher.MCLogger;
import com.prupe.mcpatcher.MCPatcherUtils;
import com.prupe.mcpatcher.ctm.CTMUtils;
import com.prupe.mcpatcher.mal.block.BlockAPI;
import com.prupe.mcpatcher.mal.block.RenderPassAPI;
import com.prupe.mcpatcher.mal.resource.BlendMethod;
import com.prupe.mcpatcher.mal.resource.PropertiesFile;
import com.prupe.mcpatcher.mal.resource.TexturePackAPI;
import com.prupe.mcpatcher.mal.resource.TexturePackChangeHandler;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import static com.gtnewhorizons.angelica.glsm.GLStateManager.glColor4f;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glDisable;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glEnable;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glShadeModel;
import static org.lwjgl.opengl.GL11C.glPolygonOffset;

public class RenderPass {

    private static final MCLogger logger = MCLogger.getLogger(MCLogger.Category.BETTER_GLASS);

    private static final ResourceLocation RENDERPASS_PROPERTIES = TexturePackAPI
        .newMCPatcherResourceLocation("renderpass.properties");

    private static final Map<Block, Integer> baseRenderPass = new IdentityHashMap<>();
    private static final Map<Block, Integer> extraRenderPass = new IdentityHashMap<>();
    private static final Map<Block, Integer> renderPassBits = new IdentityHashMap<>();
    private static final Set<Block> customRenderPassBlocks = new HashSet<>();

    private static BlendMethod blendMethod;
    private static ResourceLocation blendBlankResource;
    private static boolean enableLightmap;
    private static boolean enableColormap;
    private static final boolean[] backfaceCulling = new boolean[RenderPassAPI.NUM_RENDER_PASSES];

    private static int currentRenderPass = -1;
    private static int maxRenderPass = 1;
    private static boolean canRenderInThisPass;
    private static boolean hasCustomRenderPasses;
    private static boolean ambientOcclusion;

    private static final int COLOR_POS_0 = 3;
    private static final int COLOR_POS_1 = COLOR_POS_0 + 7;
    private static final int COLOR_POS_2 = COLOR_POS_1 + 7;
    private static final int COLOR_POS_3 = COLOR_POS_2 + 7;

    private static int saveColor0;
    private static int saveColor1;
    private static int saveColor2;
    private static int saveColor3;

    static {
        RenderPassAPI.instance = new RenderPassAPI() {

            @Override
            public boolean skipDefaultRendering(Block block) {
                return currentRenderPass > MAX_BASE_RENDER_PASS;
            }

            @Override
            public boolean skipThisRenderPass(Block block, int pass) {
                if (currentRenderPass < 0) {
                    return pass > MAX_BASE_RENDER_PASS;
                }
                if (pass < 0) {
                    pass = RenderPassMap.getDefaultRenderPass(block);
                }
                return pass != currentRenderPass;
            }

            @Override
            public boolean useColorMultiplierThisPass(Block block) {
                return currentRenderPass != OVERLAY_RENDER_PASS || enableColormap;
            }

            @Override
            public boolean useLightmapThisPass() {
                return currentRenderPass != OVERLAY_RENDER_PASS || enableLightmap;
            }

            @Override
            public void clear() {
                canRenderInThisPass = false;
                maxRenderPass = MAX_BASE_RENDER_PASS - 1;
                baseRenderPass.clear();
                extraRenderPass.clear();
                renderPassBits.clear();
                customRenderPassBlocks.clear();

                blendMethod = BlendMethod.ALPHA;
                blendBlankResource = blendMethod.getBlankResource();
                if (blendBlankResource == null) {
                    blendBlankResource = BlendMethod.ALPHA.getBlankResource();
                }
                enableLightmap = true;
                enableColormap = false;
                Arrays.fill(backfaceCulling, true);
                backfaceCulling[RenderPassAPI.BACKFACE_RENDER_PASS] = false;

                for (Block block : BlockAPI.getAllBlocks()) {
                    baseRenderPass.put(block, RenderPassMap.getDefaultRenderPass(block));
                }
            }

            @Override
            public void refreshBlendingOptions() {
                PropertiesFile properties = PropertiesFile.get(logger, RENDERPASS_PROPERTIES);
                if (properties != null) {
                    remapProperties(properties);
                    String method = properties.getString("blend.overlay", "alpha")
                        .trim()
                        .toLowerCase();
                    blendMethod = BlendMethod.parse(method);
                    if (blendMethod == null) {
                        logger.error("%s: unknown blend method '%s'", RENDERPASS_PROPERTIES, method);
                        blendMethod = BlendMethod.ALPHA;
                    }
                    blendBlankResource = blendMethod.getBlankResource();
                    if (blendBlankResource == null) {
                        blendBlankResource = BlendMethod.ALPHA.getBlankResource();
                    }
                    enableLightmap = properties.getBoolean("enableLightmap.overlay", !blendMethod.isColorBased());
                    enableColormap = properties.getBoolean("enableColormap.overlay", false);
                    backfaceCulling[RenderPassAPI.OVERLAY_RENDER_PASS] = properties
                        .getBoolean("backfaceCulling.overlay", true);
                    backfaceCulling[RenderPassAPI.CUTOUT_RENDER_PASS] = backfaceCulling[RenderPassMap
                        .getCutoutRenderPass()] = properties.getBoolean("backfaceCulling.cutout", true);
                    backfaceCulling[RenderPassAPI.CUTOUT_MIPPED_RENDER_PASS] = properties
                        .getBoolean("backfaceCulling.cutout_mipped", backfaceCulling[RenderPassAPI.CUTOUT_RENDER_PASS]);
                    backfaceCulling[RenderPassAPI.TRANSLUCENT_RENDER_PASS] = properties
                        .getBoolean("backfaceCulling.translucent", true);
                }
            }

            private void remapProperties(PropertiesFile properties) {
                for (Map.Entry<String, String> entry : properties.entrySet()) {
                    String key = entry.getKey();
                    key = key.replaceFirst("\\.3$", ".overlay");
                    key = key.replaceFirst("\\.2$", ".backface");
                    if (!key.equals(entry.getKey())) {
                        properties.warning("%s is deprecated in 1.8.  Use %s instead", entry.getKey(), key);
                    }
                    properties.setProperty(key, entry.getValue());
                }
            }

            @Override
            public void setRenderPassForBlock(Block block, int pass) {
                if (block == null || pass < 0) {
                    return;
                }
                String name;
                if (pass <= MAX_BASE_RENDER_PASS) {
                    baseRenderPass.put(block, pass);
                    name = "base";
                } else {
                    extraRenderPass.put(block, pass);
                    name = "extra";
                }
                logger.fine(
                    "%s %s render pass -> %s",
                    BlockAPI.getBlockName(block),
                    name,
                    RenderPassAPI.instance.getRenderPassName(pass));
                customRenderPassBlocks.add(block);
                maxRenderPass = Math.max(maxRenderPass, pass);
            }

            @Override
            public ResourceLocation getBlankResource(int pass) {
                return pass == OVERLAY_RENDER_PASS ? blendBlankResource : super.getBlankResource(pass);
            }

            @Override
            public ResourceLocation getBlankResource() {
                return getBlankResource(currentRenderPass);
            }
        };

        TexturePackChangeHandler.register(new TexturePackChangeHandler(MCPatcherUtils.BETTER_GLASS, 4) {

            @Override
            public void beforeChange() {}

            @Override
            public void afterChange() {
                for (Block block : BlockAPI.getAllBlocks()) {
                    int bits = 0;
                    Integer i = baseRenderPass.get(block);
                    if (i != null && i >= 0) {
                        bits |= (1 << i);
                    }
                    i = extraRenderPass.get(block);
                    if (i != null && i >= 0) {
                        bits |= (1 << i);
                    }
                    renderPassBits.put(block, bits);
                }
            }
        });
    }

    public static void start(int pass) {
        currentRenderPass = RenderPassMap.vanillaToMCPatcher(pass);
        CTMUtils.setBlankResource();
    }

    public static void finish() {
        currentRenderPass = -1;
        CTMUtils.setBlankResource();
    }

    public static boolean skipAllRenderPasses(boolean[] skipRenderPass) {
        return skipRenderPass[0] && skipRenderPass[1] && skipRenderPass[2] && skipRenderPass[3];
    }

    public static boolean checkRenderPasses(Block block, boolean moreRenderPasses) {
        int bits = renderPassBits.get(block) >>> currentRenderPass;
        canRenderInThisPass = (bits & 1) != 0;
        hasCustomRenderPasses = customRenderPassBlocks.contains(block);
        return moreRenderPasses || (bits >>> 1) != 0;
    }

    public static boolean canRenderInThisPass(boolean canRender) {
        return hasCustomRenderPasses ? canRenderInThisPass : canRender;
    }

    // pre-14w02a
    public static boolean shouldSideBeRendered(Block block, IBlockAccess blockAccess, int x, int y, int z, int face) {
        if (block.shouldSideBeRendered(blockAccess, x, y, z, face)) {
            return true;
        } else if (!extraRenderPass.containsKey(block)) {
            Block neighbor = blockAccess.getBlock(x, y, z);
            return extraRenderPass.containsKey(neighbor);
        } else {
            return false;
        }
    }

    public static boolean setAmbientOcclusion(boolean ambientOcclusion) {
        RenderPass.ambientOcclusion = ambientOcclusion;
        return ambientOcclusion;
    }

    public static float getAOBaseMultiplier(float multiplier) {
        return RenderPassAPI.instance.useLightmapThisPass() ? multiplier : 1.0f;
    }

    public static boolean useBlockShading() {
        return RenderPassAPI.instance.useLightmapThisPass();
    }

    // *sigh* Mojang removed the "unshaded" model face buffer in 14w11a, making this hack necessary again
    public static void unshadeBuffer(int[] b) {
        if (!useBlockShading()) {
            saveColor0 = b[COLOR_POS_0];
            saveColor1 = b[COLOR_POS_1];
            saveColor2 = b[COLOR_POS_2];
            saveColor3 = b[COLOR_POS_3];
            b[COLOR_POS_0] = b[COLOR_POS_1] = b[COLOR_POS_2] = b[COLOR_POS_3] = -1;
        }
    }

    public static void reshadeBuffer(int[] b) {
        if (!useBlockShading()) {
            b[COLOR_POS_0] = saveColor0;
            b[COLOR_POS_1] = saveColor1;
            b[COLOR_POS_2] = saveColor2;
            b[COLOR_POS_3] = saveColor3;
        }
    }

    public static boolean preRenderPass(int pass) {
        currentRenderPass = pass;
        if (pass > maxRenderPass) {
            return false;
        }
        switch (pass) {
            case RenderPassAPI.SOLID_RENDER_PASS:
            case RenderPassAPI.CUTOUT_MIPPED_RENDER_PASS:
            case RenderPassAPI.CUTOUT_RENDER_PASS:
            case RenderPassAPI.TRANSLUCENT_RENDER_PASS:
            case RenderPassAPI.BACKFACE_RENDER_PASS:
                if (!backfaceCulling[pass]) {
                    GL11.glDisable(GL11.GL_CULL_FACE);
                }
                break;

            case RenderPassAPI.OVERLAY_RENDER_PASS:
                glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
                glPolygonOffset(-2.0f, -2.0f);
                glEnable(GL11.GL_POLYGON_OFFSET_FILL);
                if (backfaceCulling[pass]) {
                    glEnable(GL11.GL_CULL_FACE);
                } else {
                    glDisable(GL11.GL_CULL_FACE);
                }
                if (ambientOcclusion) {
                    glShadeModel(GL11.GL_SMOOTH);
                }
                blendMethod.applyBlending();
                break;

            default:
                break;
        }
        return true;
    }

    public static int postRenderPass(int value) {
        switch (currentRenderPass) {
            case RenderPassAPI.SOLID_RENDER_PASS:
            case RenderPassAPI.CUTOUT_MIPPED_RENDER_PASS:
            case RenderPassAPI.CUTOUT_RENDER_PASS:
            case RenderPassAPI.TRANSLUCENT_RENDER_PASS:
            case RenderPassAPI.BACKFACE_RENDER_PASS:
                if (!backfaceCulling[currentRenderPass]) {
                    GL11.glEnable(GL11.GL_CULL_FACE);
                }
                break;

            case RenderPassAPI.OVERLAY_RENDER_PASS:
                GL11.glPolygonOffset(0.0f, 0.0f);
                GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
                if (!backfaceCulling[currentRenderPass]) {
                    GL11.glEnable(GL11.GL_CULL_FACE);
                }
                glDisable(GL11.GL_BLEND);
                glShadeModel(GL11.GL_FLAT);
                break;

            default:
                break;
        }
        currentRenderPass = -1;
        return value;
    }

    public static void enableDisableLightmap(EntityRenderer renderer, double partialTick) {
        if (RenderPassAPI.instance.useLightmapThisPass()) {
            renderer.enableLightmap(partialTick);
        } else {
            renderer.disableLightmap(partialTick);
        }
    }
}
