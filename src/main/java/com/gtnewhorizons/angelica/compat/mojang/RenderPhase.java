package com.gtnewhorizons.angelica.compat.mojang;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;


public abstract class RenderPhase {
    protected final String name;
    protected Runnable beginAction;
    private final Runnable endAction;
    protected static final Transparency NO_TRANSPARENCY = new Transparency("no_transparency", GLStateManager::disableBlend, () -> {
    });
    protected static final Transparency ADDITIVE_TRANSPARENCY = new Transparency("additive_transparency", () -> {
        GLStateManager.enableBlend();
        GLStateManager.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
    }, () -> {
        GLStateManager.disableBlend();
        GLStateManager.defaultBlendFunc();
        GLStateManager.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    });
    protected static final Transparency LIGHTNING_TRANSPARENCY = new Transparency("lightning_transparency", () -> {
        GLStateManager.disableBlend();
        GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
    }, () -> {
        GLStateManager.disableBlend();
        GLStateManager.defaultBlendFunc();
    });
    protected static final Transparency GLINT_TRANSPARENCY = new Transparency("glint_transparency", () -> {
        GLStateManager.disableBlend();
        GLStateManager.glBlendFuncSeparate(GL11.GL_SRC_COLOR, GL11.GL_ONE,  GL11.GL_ZERO, GL11.GL_ONE);
    }, () -> {
        GLStateManager.disableBlend();
        GLStateManager.defaultBlendFunc();
    });
    protected static final Transparency CRUMBLING_TRANSPARENCY = new Transparency("crumbling_transparency", () -> {
        GLStateManager.disableBlend();
        GLStateManager.glBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_SRC_COLOR, GL11.GL_ONE, GL11.GL_ZERO);
    }, () -> {
        GLStateManager.disableBlend();
        GLStateManager.defaultBlendFunc();
    });
    protected static final Transparency TRANSLUCENT_TRANSPARENCY = new Transparency("translucent_transparency", () -> {
        GLStateManager.disableBlend();
        GLStateManager.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }, () -> {
        GLStateManager.disableBlend();
        GLStateManager.defaultBlendFunc();
    });
    protected static final Alpha ZERO_ALPHA = new Alpha(0.0F);
    protected static final Alpha ONE_TENTH_ALPHA = new Alpha(0.003921569F);
    protected static final Alpha HALF_ALPHA = new Alpha(0.5F);
    protected static final ShadeModel SHADE_MODEL = new ShadeModel(false);
    protected static final ShadeModel SMOOTH_SHADE_MODEL = new ShadeModel(true);
    protected static final Texture MIPMAP_BLOCK_ATLAS_TEXTURE;
    protected static final Texture BLOCK_ATLAS_TEXTURE;
    protected static final Texture NO_TEXTURE;
    protected static final Texturing DEFAULT_TEXTURING;
    protected static final Texturing OUTLINE_TEXTURING;
    protected static final Texturing GLINT_TEXTURING;
    protected static final Texturing ENTITY_GLINT_TEXTURING;
    protected static final Lightmap ENABLE_LIGHTMAP;
    protected static final Lightmap DISABLE_LIGHTMAP;
    protected static final Overlay ENABLE_OVERLAY_COLOR;
    protected static final Overlay DISABLE_OVERLAY_COLOR;
    protected static final DiffuseLighting ENABLE_DIFFUSE_LIGHTING;
    protected static final DiffuseLighting DISABLE_DIFFUSE_LIGHTING;
    protected static final Cull ENABLE_CULLING;
    protected static final Cull DISABLE_CULLING;
    protected static final DepthTest ALWAYS_DEPTH_TEST;
    protected static final DepthTest EQUAL_DEPTH_TEST;
    protected static final DepthTest LEQUAL_DEPTH_TEST;
    protected static final WriteMaskState ALL_MASK;
    protected static final WriteMaskState COLOR_MASK;
    protected static final WriteMaskState DEPTH_MASK;
    protected static final Layering NO_LAYERING;
    protected static final Layering POLYGON_OFFSET_LAYERING;
    protected static final Layering VIEW_OFFSET_Z_LAYERING;
    protected static final Fog NO_FOG;
    protected static final Fog FOG;
    protected static final Fog BLACK_FOG;
    protected static final Target MAIN_TARGET;
    protected static final Target OUTLINE_TARGET;
    protected static final Target TRANSLUCENT_TARGET;
    protected static final Target PARTICLES_TARGET;
    protected static final Target WEATHER_TARGET;
    protected static final Target CLOUDS_TARGET;
    protected static final Target ITEM_TARGET;
    protected static final LineWidth FULL_LINE_WIDTH;

    public RenderPhase(String name, Runnable beginAction, Runnable endAction) {
        this.name = name;
        this.beginAction = beginAction;
        this.endAction = endAction;
    }

    public void startDrawing() {
        this.beginAction.run();
    }

    public void endDrawing() {
        this.endAction.run();
    }

    public boolean equals(@Nullable Object object) {
        if (this == object) {
            return true;
        } else if (object != null && this.getClass() == object.getClass()) {
            RenderPhase lv = (RenderPhase)object;
            return this.name.equals(lv.name);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return this.name.hashCode();
    }

    public String toString() {
        return this.name;
    }

    private static void setupGlintTexturing(float scale) {
        throw new RuntimeException("Not Implemented Yet");
//        RenderSystem.matrixMode(5890);
//        RenderSystem.pushMatrix();
//        RenderSystem.loadIdentity();
//        long l = Util.getMeasuringTimeMs() * 8L;
//        float g = (float)(l % 110000L) / 110000.0F;
//        float h = (float)(l % 30000L) / 30000.0F;
//        RenderSystem.translatef(-g, h, 0.0F);
//        RenderSystem.rotatef(10.0F, 0.0F, 0.0F, 1.0F);
//        RenderSystem.scalef(scale, scale, scale);
//        RenderSystem.matrixMode(5888);
    }

    private static ResourceLocation ATLAS = new ResourceLocation("sodium", "textures/atlas.png");
    static {
        // TODO: Sodium - SpriteAtlasTexture
        MIPMAP_BLOCK_ATLAS_TEXTURE = new Texture(ATLAS/*SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE*/, false, true);
        BLOCK_ATLAS_TEXTURE = new Texture(ATLAS /*SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE*/, false, false);
        NO_TEXTURE = new Texture();
        DEFAULT_TEXTURING = new Texturing("default_texturing", () -> {
        }, () -> {
        });
        OUTLINE_TEXTURING = new Texturing("outline_texturing", () -> {
            throw new RuntimeException("Not Implemented Yet");
//            RenderSystem.setupOutline();
        }, () -> {
            throw new RuntimeException("Not Implemented Yet");
//            RenderSystem.teardownOutline();
        });
        GLINT_TEXTURING = new Texturing("glint_texturing", () -> {
            setupGlintTexturing(8.0F);
        }, () -> {
            throw new RuntimeException("Not Implemented Yet");
//            RenderSystem.matrixMode(5890);
//            RenderSystem.popMatrix();
//            RenderSystem.matrixMode(5888);
        });
        ENTITY_GLINT_TEXTURING = new Texturing("entity_glint_texturing", () -> {
            setupGlintTexturing(0.16F);
        }, () -> {
            throw new RuntimeException("Not Implemented Yet");
//            RenderSystem.matrixMode(5890);
//            RenderSystem.popMatrix();
//            RenderSystem.matrixMode(5888);
        });
        ENABLE_LIGHTMAP = new Lightmap(true);
        DISABLE_LIGHTMAP = new Lightmap(false);
        ENABLE_OVERLAY_COLOR = new Overlay(true);
        DISABLE_OVERLAY_COLOR = new Overlay(false);
        ENABLE_DIFFUSE_LIGHTING = new DiffuseLighting(true);
        DISABLE_DIFFUSE_LIGHTING = new DiffuseLighting(false);
        ENABLE_CULLING = new Cull(true);
        DISABLE_CULLING = new Cull(false);
        ALWAYS_DEPTH_TEST = new DepthTest("always", GL11.GL_ALWAYS);
        EQUAL_DEPTH_TEST = new DepthTest("==", GL11.GL_EQUAL);
        LEQUAL_DEPTH_TEST = new DepthTest("<=", GL11.GL_LEQUAL);
        ALL_MASK = new WriteMaskState(true, true);
        COLOR_MASK = new WriteMaskState(true, false);
        DEPTH_MASK = new WriteMaskState(false, true);
        NO_LAYERING = new Layering("no_layering", () -> {
        }, () -> {
        });
        POLYGON_OFFSET_LAYERING = new Layering("polygon_offset_layering", () -> {
            throw new RuntimeException("Not Implemented Yet");
//            RenderSystem.polygonOffset(-1.0F, -10.0F);
//            RenderSystem.enablePolygonOffset();
        }, () -> {
            throw new RuntimeException("Not Implemented Yet");
//            RenderSystem.polygonOffset(0.0F, 0.0F);
//            RenderSystem.disablePolygonOffset();
        });
        VIEW_OFFSET_Z_LAYERING = new Layering("view_offset_z_layering", () -> {
            throw new RuntimeException("Not Implemented Yet");
//            RenderSystem.pushMatrix();
//            RenderSystem.scalef(0.99975586F, 0.99975586F, 0.99975586F);
        }, () -> {
            throw new RuntimeException("Not Implemented Yet");
//            RenderSystem::popMatrix();
        });
        NO_FOG = new Fog("no_fog", () -> {
        }, () -> {
        });
        FOG = new Fog("fog", () -> {
            // Unclear what this should do
            // BackgroundRenderer.setFogBlack(), also levelFogColor()
            SodiumClientMod.LOGGER.debug("Fog - Not setting level fog color");
            GLStateManager.enableFog();
        }, GLStateManager::disableFog);
        BLACK_FOG = new Fog("black_fog", () -> {
            GLStateManager.fogColor(0.0F, 0.0F, 0.0F, 1.0F);
            GLStateManager.enableFog();
        }, () -> {
            // Unclear what this should do
            // BackgroundRenderer.setFogBlack(), also levelFogColor()
            SodiumClientMod.LOGGER.debug("Fog - Not setting level fog color");
            GLStateManager.disableFog();
        });
        MAIN_TARGET = new Target("main_target", () -> {
        }, () -> {
        });
        OUTLINE_TARGET = new Target("outline_target", () -> {
            // TODO: Sodium
            SodiumClientMod.LOGGER.debug("NOT enabling the entity outline framebuffer");
            //MinecraftClient.getInstance().worldRenderer.getEntityOutlinesFramebuffer().beginWrite(false);
        }, () -> {
            Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(false);
        });
        TRANSLUCENT_TARGET = new Target("translucent_target", () -> {
            if (Minecraft.isFancyGraphicsEnabled()) {
                // TODO: Sodium
                SodiumClientMod.LOGGER.debug("NOT enabling the translucent framebuffer");
                // MinecraftClient.getInstance().worldRenderer.getTranslucentFramebuffer().beginWrite(false);
            }

        }, () -> {
            if (Minecraft.isFancyGraphicsEnabled()) {
                Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(false);
            }

        });
        PARTICLES_TARGET = new Target("particles_target", () -> {
            if (Minecraft.isFancyGraphicsEnabled()) {
                throw new RuntimeException("Not Implemented Yet");
//                MinecraftClient.getInstance().worldRenderer.getParticlesFramebuffer().beginWrite(false);
            }

        }, () -> {
            if (Minecraft.isFancyGraphicsEnabled()) {
                Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(false);
            }

        });
        WEATHER_TARGET = new Target("weather_target", () -> {
            if (Minecraft.isFancyGraphicsEnabled()) {
                throw new RuntimeException("Not Implemented Yet");
//                MinecraftClient.getInstance().worldRenderer.getWeatherFramebuffer().beginWrite(false);
            }

        }, () -> {
            if (Minecraft.isFancyGraphicsEnabled()) {
                Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(false);
            }

        });
        CLOUDS_TARGET = new Target("clouds_target", () -> {
            if (Minecraft.isFancyGraphicsEnabled()) {
                throw new RuntimeException("Not Implemented Yet");
//                MinecraftClient.getInstance().worldRenderer.getCloudsFramebuffer().beginWrite(false);
            }

        }, () -> {
            if (Minecraft.isFancyGraphicsEnabled()) {
                Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(false);
            }

        });
        ITEM_TARGET = new Target("item_entity_target", () -> {
            if (Minecraft.isFancyGraphicsEnabled()) {
                throw new RuntimeException("Not Implemented Yet");
//                MinecraftClient.getInstance().worldRenderer.getEntityFramebuffer().beginWrite(false);
            }

        }, () -> {
            if (Minecraft.isFancyGraphicsEnabled()) {
                Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(false);
            }

        });
        FULL_LINE_WIDTH = new LineWidth(OptionalDouble.of(1.0));
    }


    public static class LineWidth extends RenderPhase {
        private final OptionalDouble width;

        public LineWidth(OptionalDouble optionalDouble) {
            super("line_width", () -> {
                if (!Objects.equals(optionalDouble, OptionalDouble.of(1.0))) {
                    if (optionalDouble.isPresent()) {
                        throw new RuntimeException("Not Implemented Yet");
//                        RenderSystem.lineWidth((float)optionalDouble.getAsDouble());
                    } else {
                        throw new RuntimeException("Not Implemented Yet");
//                        RenderSystem.lineWidth(Math.max(2.5F, (float)MinecraftClient.getInstance().getWindow().getFramebufferWidth() / 1920.0F * 2.5F));
                    }
                }

            }, () -> {
                if (!Objects.equals(optionalDouble, OptionalDouble.of(1.0))) {
                    throw new RuntimeException("Not Implemented Yet");
//                    RenderSystem.lineWidth(1.0F);
                }

            });
            this.width = optionalDouble;
        }

        public boolean equals(@Nullable Object object) {
            if (this == object) {
                return true;
            } else if (object != null && this.getClass() == object.getClass()) {
                return !super.equals(object) ? false : Objects.equals(this.width, ((LineWidth)object).width);
            } else {
                return false;
            }
        }

        public int hashCode() {
            return Objects.hash(new Object[]{super.hashCode(), this.width});
        }

        public String toString() {
            return this.name + '[' + (this.width.isPresent() ? this.width.getAsDouble() : "window_scale") + ']';
        }
    }


    public static class Target extends RenderPhase {
        public Target(String string, Runnable runnable, Runnable runnable2) {
            super(string, runnable, runnable2);
        }
    }


    public static class Fog extends RenderPhase {
        public Fog(String string, Runnable runnable, Runnable runnable2) {
            super(string, runnable, runnable2);
        }
    }


    public static class Layering extends RenderPhase {
        public Layering(String string, Runnable runnable, Runnable runnable2) {
            super(string, runnable, runnable2);
        }
    }


    public static class WriteMaskState extends RenderPhase {
        private final boolean color;
        private final boolean depth;

        public WriteMaskState(boolean color, boolean depth) {
            super("write_mask_state", () -> {
                if (!depth) {
                    GLStateManager.glDepthMask(depth);
                }

                if (!color) {
                    GLStateManager.glColorMask(color, color, color, color);
                }

            }, () -> {
                if (!depth) {
                    GLStateManager.glDepthMask(true);
                }

                if (!color) {
                    GLStateManager.glColorMask(true, true, true, true);
                }

            });
            this.color = color;
            this.depth = depth;
        }

        public boolean equals(Object object) {
            if (this == object) {
                return true;
            } else if (object != null && this.getClass() == object.getClass()) {
                WriteMaskState lv = (WriteMaskState)object;
                return this.color == lv.color && this.depth == lv.depth;
            } else {
                return false;
            }
        }

        public int hashCode() {
            return Objects.hash(new Object[]{this.color, this.depth});
        }

        public String toString() {
            return this.name + "[writeColor=" + this.color + ", writeDepth=" + this.depth + ']';
        }
    }


    public static class DepthTest extends RenderPhase {
        private final String depthFunction;
        private final int func;

        public DepthTest(String string, int i) {
            super("depth_test", () -> {
                if (i != GL11.GL_ALWAYS) {
                    GLStateManager.enableDepthTest();
                    GLStateManager.glDepthFunc(i);
                }

            }, () -> {
                if (i != GL11.GL_ALWAYS) {
                    GLStateManager.disableDepthTest();
                    GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
                }

            });
            this.depthFunction = string;
            this.func = i;
        }

        public boolean equals(Object object) {
            if (this == object) {
                return true;
            } else if (object != null && this.getClass() == object.getClass()) {
                DepthTest lv = (DepthTest)object;
                return this.func == lv.func;
            } else {
                return false;
            }
        }

        public int hashCode() {
            return Integer.hashCode(this.func);
        }

        public String toString() {
            return this.name + '[' + this.depthFunction + ']';
        }
    }


    public static class Cull extends Toggleable {
        public Cull(boolean culling) {
            super("cull", () -> {
                if (!culling) {
                    GLStateManager.disableCull();
                }

            }, () -> {
                if (!culling) {
                    GLStateManager.enableCull();
                }

            }, culling);
        }
    }


    public static class DiffuseLighting extends Toggleable {
        public DiffuseLighting(boolean guiLighting) {
            super("diffuse_lighting", () -> {
                if (guiLighting) {
                    throw new RuntimeException("Not Implemented Yet");
//                    net.minecraft.client.render.DiffuseLighting.enable();
                }

            }, () -> {
                if (guiLighting) {
                    throw new RuntimeException("Not Implemented Yet");
//                    net.minecraft.client.render.DiffuseLighting.disable();
                }

            }, guiLighting);
        }
    }


    public static class Overlay extends Toggleable {
        public Overlay(boolean overlayColor) {
            super("overlay", () -> {
                if (overlayColor) {
                    throw new RuntimeException("Not Implemented Yet");
//                    MinecraftClient.getInstance().gameRenderer.getOverlayTexture().setupOverlayColor();
                }

            }, () -> {
                if (overlayColor) {
                    throw new RuntimeException("Not Implemented Yet");
//                    MinecraftClient.getInstance().gameRenderer.getOverlayTexture().teardownOverlayColor();
                }

            }, overlayColor);
        }
    }


    public static class Lightmap extends Toggleable {
        public Lightmap(boolean lightmap) {
            super("lightmap", () -> {
                if (lightmap) {
                    // TODO: Sodium - LightmapTextureManager
                    SodiumClientMod.LOGGER.debug("Lightmap - enable (not implemented)");
//                    throw new RuntimeException("Not Implemented Yet");
//                    MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager().enable();

                }

            }, () -> {
                if (lightmap) {
                    // TODO: Sodium - LightmapTextureManager
                    SodiumClientMod.LOGGER.debug("Lightmap - disable (not implemented)");
//                    throw new RuntimeException("Not Implemented Yet");
//                    MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager().disable();
                }

            }, lightmap);
        }
    }


    static class Toggleable extends RenderPhase {
        private final boolean enabled;

        public Toggleable(String string, Runnable runnable, Runnable runnable2, boolean bl) {
            super(string, runnable, runnable2);
            this.enabled = bl;
        }

        public boolean equals(Object object) {
            if (this == object) {
                return true;
            } else if (object != null && this.getClass() == object.getClass()) {
                Toggleable lv = (Toggleable)object;
                return this.enabled == lv.enabled;
            } else {
                return false;
            }
        }

        public int hashCode() {
            return Boolean.hashCode(this.enabled);
        }

        public String toString() {
            return this.name + '[' + this.enabled + ']';
        }
    }


    public static final class PortalTexturing extends Texturing {
        private final int layer;

        public PortalTexturing(int layer) {
            super("portal_texturing", () -> {
                  throw new RuntimeException("Not Implemented Yet");
//                RenderSystem.matrixMode(5890);
//                RenderSystem.pushMatrix();
//                RenderSystem.loadIdentity();
//                RenderSystem.translatef(0.5F, 0.5F, 0.0F);
//                RenderSystem.scalef(0.5F, 0.5F, 1.0F);
//                RenderSystem.translatef(17.0F / (float)layer, (2.0F + (float)layer / 1.5F) * ((float)(Util.getMeasuringTimeMs() % 800000L) / 800000.0F), 0.0F);
//                RenderSystem.rotatef(((float)(layer * layer) * 4321.0F + (float)layer * 9.0F) * 2.0F, 0.0F, 0.0F, 1.0F);
//                RenderSystem.scalef(4.5F - (float)layer / 4.0F, 4.5F - (float)layer / 4.0F, 1.0F);
//                RenderSystem.mulTextureByProjModelView();
//                RenderSystem.matrixMode(5888);
//                RenderSystem.setupEndPortalTexGen();
            }, () -> {
                  throw new RuntimeException("Not Implemented Yet");
//                RenderSystem.matrixMode(5890);
//                RenderSystem.popMatrix();
//                RenderSystem.matrixMode(5888);
//                RenderSystem.clearTexGen();
            });
            this.layer = layer;
        }

        public boolean equals(Object object) {
            if (this == object) {
                return true;
            } else if (object != null && this.getClass() == object.getClass()) {
                PortalTexturing lv = (PortalTexturing)object;
                return this.layer == lv.layer;
            } else {
                return false;
            }
        }

        public int hashCode() {
            return Integer.hashCode(this.layer);
        }
    }


    public static final class OffsetTexturing extends Texturing {
        private final float x;
        private final float y;

        public OffsetTexturing(float x, float y) {
            super("offset_texturing", () -> {
                throw new RuntimeException("Not Implemented Yet");
//                RenderSystem.matrixMode(5890);
//                RenderSystem.pushMatrix();
//                RenderSystem.loadIdentity();
//                RenderSystem.translatef(x, y, 0.0F);
//                RenderSystem.matrixMode(5888);
            }, () -> {
                throw new RuntimeException("Not Implemented Yet");
//                RenderSystem.matrixMode(5890);
//                RenderSystem.popMatrix();
//                RenderSystem.matrixMode(5888);
            });
            this.x = x;
            this.y = y;
        }

        public boolean equals(Object object) {
            if (this == object) {
                return true;
            } else if (object != null && this.getClass() == object.getClass()) {
                OffsetTexturing lv = (OffsetTexturing)object;
                return Float.compare(lv.x, this.x) == 0 && Float.compare(lv.y, this.y) == 0;
            } else {
                return false;
            }
        }

        public int hashCode() {
            return Objects.hash(new Object[]{this.x, this.y});
        }
    }


    public static class Texturing extends RenderPhase {
        public Texturing(String string, Runnable runnable, Runnable runnable2) {
            super(string, runnable, runnable2);
        }
    }


    public static class Texture extends RenderPhase {
        private final Optional<ResourceLocation> id;
        protected boolean bilinear;
        protected boolean mipmap;

        public Texture(ResourceLocation id, boolean bilinear, boolean mipmap) {
            super("texture", () -> {
                GLStateManager.enableTexture();
                TextureManager lv = Minecraft.getMinecraft().getTextureManager();
                lv.bindTexture(id);
                GLStateManager.setFilter(bilinear, mipmap);
            }, () -> {
            });
            this.id = Optional.of(id);
            this.bilinear = bilinear;
            this.mipmap = mipmap;
        }

        public Texture() {
            super("texture", GLStateManager::disableTexture, GLStateManager::enableTexture);
            this.id = Optional.empty();
            this.bilinear = false;
            this.mipmap = false;
        }

        public boolean equals(Object object) {
            if (this == object) {
                return true;
            } else if (object != null && this.getClass() == object.getClass()) {
                Texture lv = (Texture)object;
                return this.id.equals(lv.id) && this.bilinear == lv.bilinear && this.mipmap == lv.mipmap;
            } else {
                return false;
            }
        }

        public int hashCode() {
            return this.id.hashCode();
        }

        public String toString() {
            return this.name + '[' + this.id + "(blur=" + this.bilinear + ", mipmap=" + this.mipmap + ")]";
        }

        protected Optional<ResourceLocation> getId() {
            return this.id;
        }
    }


    public static class ShadeModel extends RenderPhase {
        private final boolean smooth;

        public ShadeModel(boolean smooth) {
            super("shade_model",
                () -> GLStateManager.glShadeModel(smooth ? GL11.GL_SMOOTH : GL11.GL_FLAT),
                () -> GLStateManager.glShadeModel(GL11.GL_FLAT));
            this.smooth = smooth;
        }

        public boolean equals(Object object) {
            if (this == object) {
                return true;
            } else if (object != null && this.getClass() == object.getClass()) {
                ShadeModel lv = (ShadeModel)object;
                return this.smooth == lv.smooth;
            } else {
                return false;
            }
        }

        public int hashCode() {
            return Boolean.hashCode(this.smooth);
        }

        public String toString() {
            return this.name + '[' + (this.smooth ? "smooth" : "flat") + ']';
        }
    }


    public static class Alpha extends RenderPhase {
        private final float alpha;

        public Alpha(float alpha) {
            super("alpha", () -> {
                if (alpha > 0.0F) {

                    GLStateManager.enableAlphaTest();
                    GLStateManager.glAlphaFunc(GL11.GL_GREATER, alpha);
                } else {
                    GLStateManager.disableAlphaTest();
                }

            }, () -> {
                GLStateManager.disableAlphaTest();
                GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1F);
            });
            this.alpha = alpha;
        }

        public boolean equals(@Nullable Object object) {
            if (this == object) {
                return true;
            } else if (object != null && this.getClass() == object.getClass()) {
                if (!super.equals(object)) {
                    return false;
                } else {
                    return this.alpha == ((Alpha)object).alpha;
                }
            } else {
                return false;
            }
        }

        public int hashCode() {
            return Objects.hash(new Object[]{super.hashCode(), this.alpha});
        }

        public String toString() {
            return this.name + '[' + this.alpha + ']';
        }
    }


    public static class Transparency extends RenderPhase {
        public Transparency(String string, Runnable runnable, Runnable runnable2) {
            super(string, runnable, runnable2);
        }
    }
}
