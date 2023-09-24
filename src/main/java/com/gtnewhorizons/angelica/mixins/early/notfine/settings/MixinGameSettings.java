package com.gtnewhorizons.angelica.mixins.early.notfine.settings;

import jss.notfine.core.SettingsManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.EnumDifficulty;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = GameSettings.class, priority = 990)
public abstract class MixinGameSettings {

    @Final
    private static String[] GUISCALES = new String[] {
        "options.guiScale.auto",
        "options.guiScale.small",
        "options.guiScale.normal",
        "options.guiScale.large",
        "options.guiScale.massive"
    };

    /**
     * @author jss2a98aj
     * @reason Makes this function not unreasonably slow.
     */
    @Overwrite
    public void setOptionFloatValue(GameSettings.Options option, float value) {
        switch(option) {
            case SENSITIVITY:
                mouseSensitivity = value;
                break;
            case FOV:
                fovSetting = value;
                break;
            case GAMMA:
                gammaSetting = value;
                break;
            case FRAMERATE_LIMIT:
                limitFramerate = (int) value;
                break;
            case CHAT_OPACITY:
                chatOpacity = value;
                mc.ingameGUI.getChatGUI().refreshChat();
                break;
            case CHAT_HEIGHT_FOCUSED:
                chatHeightFocused = value;
                mc.ingameGUI.getChatGUI().refreshChat();
                break;
            case CHAT_HEIGHT_UNFOCUSED:
                chatHeightUnfocused = value;
                mc.ingameGUI.getChatGUI().refreshChat();
                break;
            case CHAT_WIDTH:
                chatWidth = value;
                mc.ingameGUI.getChatGUI().refreshChat();
                break;
            case CHAT_SCALE:
                chatScale = value;
                mc.ingameGUI.getChatGUI().refreshChat();
                break;
            case ANISOTROPIC_FILTERING:
                if(anisotropicFiltering != (int) value) {
                    anisotropicFiltering = (int) value;
                    mc.getTextureMapBlocks().setAnisotropicFiltering(this.anisotropicFiltering);
                    mc.scheduleResourcesRefresh();
                }
                break;
            case MIPMAP_LEVELS:
                if(mipmapLevels != (int) value) {
                    mipmapLevels = (int) value;
                    mc.getTextureMapBlocks().setMipmapLevels(this.mipmapLevels);
                    mc.scheduleResourcesRefresh();
                }
                break;
            case RENDER_DISTANCE:
                renderDistanceChunks = (int) value;
                break;
            case STREAM_BYTES_PER_PIXEL:
                field_152400_J = value;
                break;
            case STREAM_VOLUME_MIC:
                field_152401_K = value;
                mc.func_152346_Z().func_152915_s();
                break;
            case STREAM_VOLUME_SYSTEM:
                field_152402_L = value;
                mc.func_152346_Z().func_152915_s();
                break;
            case STREAM_KBPS:
                field_152403_M = value;
                break;
            case STREAM_FPS:
                field_152404_N = value;
                break;
        }
    }

    /**
     * @author jss2a98aj
     * @reason Makes this function not unreasonably slow.
     */
    @Overwrite
    public void setOptionValue(GameSettings.Options option, int value) {
        switch(option) {
            case INVERT_MOUSE:
                invertMouse = !invertMouse;
                break;
            case GUI_SCALE:
                guiScale = (guiScale + value) % 5;
                break;
            case PARTICLES:
                particleSetting = (particleSetting + value) % 3;
                break;
            case VIEW_BOBBING:
                viewBobbing = !viewBobbing;
                break;
            case RENDER_CLOUDS:
                clouds = !clouds;
                break;
            case FORCE_UNICODE_FONT:
                forceUnicodeFont = !forceUnicodeFont;
                mc.fontRenderer.setUnicodeFlag(mc.getLanguageManager().isCurrentLocaleUnicode() || forceUnicodeFont);
                break;
            case ADVANCED_OPENGL:
                advancedOpengl = !advancedOpengl;
                mc.renderGlobal.loadRenderers();
            case FBO_ENABLE:
                fboEnable = !fboEnable;
                break;
            case ANAGLYPH:
                anaglyph = !anaglyph;
                //refreshResources was overkill.
                mc.renderGlobal.loadRenderers();
                break;
            case DIFFICULTY:
                difficulty = EnumDifficulty.getDifficultyEnum(difficulty.getDifficultyId() + value & 3);
                break;
            case GRAPHICS:
                fancyGraphics = !fancyGraphics;
                SettingsManager.graphicsUpdated();
                mc.renderGlobal.loadRenderers();
                break;
            case AMBIENT_OCCLUSION:
                ambientOcclusion = (ambientOcclusion + value) % 3;
                mc.renderGlobal.loadRenderers();
                break;
            case CHAT_VISIBILITY:
                chatVisibility = EntityPlayer.EnumChatVisibility.getEnumChatVisibility((chatVisibility.getChatVisibility() + value) % 3);
                break;
            case STREAM_COMPRESSION:
                field_152405_O = (field_152405_O + value) % 3;
                break;
            case STREAM_SEND_METADATA:
                field_152406_P = !field_152406_P;
                break;
            case STREAM_CHAT_ENABLED:
                field_152408_R = (field_152408_R + value) % 3;
                break;
            case STREAM_CHAT_USER_FILTER:
                field_152409_S = (field_152409_S + value) % 3;
                break;
            case STREAM_MIC_TOGGLE_BEHAVIOR:
                field_152410_T = (field_152410_T + value) % 2;
                break;
            case CHAT_COLOR:
                chatColours = !chatColours;
                break;
            case CHAT_LINKS:
                chatLinks = !chatLinks;
                break;
            case CHAT_LINKS_PROMPT:
                chatLinksPrompt = !chatLinksPrompt;
                break;
            case SNOOPER_ENABLED:
                snooperEnabled = !snooperEnabled;
                break;
            case SHOW_CAPE:
                showCape = !showCape;
                break;
            case TOUCHSCREEN:
                touchscreen = !touchscreen;
                break;
            case USE_FULLSCREEN:
                fullScreen = !fullScreen;
                if (mc.isFullScreen() != fullScreen) {
                    mc.toggleFullscreen();
                }
                break;
            case ENABLE_VSYNC:
                enableVsync = !enableVsync;
                Display.setVSyncEnabled(enableVsync);
                break;
        }
        saveOptions();
    }

    @Shadow
    public void saveOptions() {}

    @Shadow protected Minecraft mc;

    @Shadow public float mouseSensitivity;
    @Shadow public boolean invertMouse;
    @Shadow public float fovSetting;
    @Shadow public float gammaSetting;
    @Shadow public int renderDistanceChunks;
    @Shadow public int guiScale;
    @Shadow public int particleSetting;
    @Shadow public boolean viewBobbing;
    @Shadow public boolean anaglyph;
    @Shadow public boolean advancedOpengl;
    @Shadow public int limitFramerate;
    @Shadow public boolean fboEnable;
    @Shadow public EnumDifficulty difficulty;
    @Shadow public boolean fancyGraphics;
    @Shadow public int ambientOcclusion;
    @Shadow public boolean clouds;
    @Shadow public EntityPlayer.EnumChatVisibility chatVisibility;
    @Shadow public boolean chatColours;
    @Shadow public boolean chatLinks;
    @Shadow public boolean chatLinksPrompt;
    @Shadow public float chatOpacity;
    @Shadow public boolean snooperEnabled;
    @Shadow public boolean fullScreen;
    @Shadow public boolean enableVsync;
    @Shadow public boolean showCape;
    @Shadow public boolean touchscreen;
    @Shadow public float chatHeightFocused;
    @Shadow public float chatHeightUnfocused;
    @Shadow public float chatScale;
    @Shadow public float chatWidth;
    @Shadow public int mipmapLevels;
    @Shadow public int anisotropicFiltering;
    @Shadow public float field_152400_J;
    @Shadow public float field_152401_K;
    @Shadow public float field_152402_L;
    @Shadow public float field_152403_M;
    @Shadow public float field_152404_N;
    @Shadow public int field_152405_O;
    @Shadow public boolean field_152406_P;
    @Shadow public int field_152408_R;
    @Shadow public int field_152409_S;
    @Shadow public int field_152410_T;
    @Shadow public boolean forceUnicodeFont;

}
