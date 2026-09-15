package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.rendering.ReducerStateMachine.State;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

public final class FpsReducer {

    private static final ReducerStateMachine MACHINE = new ReducerStateMachine(System::nanoTime);
    private static final FpsReducer INSTANCE = new FpsReducer();

    private static KeyBinding forceKey;
    private static KeyBinding disableKey;

    private static State applied;
    private static int appliedVolumePercent = 100;
    private static boolean configDirty;
    private static String hudText;
    private static long lastActiveMillis = Minecraft.getSystemTime();
    private static volatile int volumePercent = 100;

    private FpsReducer() {}

    public static void init() {
        forceKey = new KeyBinding("angelica.keybind.fps_reducer_force", 0, "key.categories.misc");
        disableKey = new KeyBinding("angelica.keybind.fps_reducer_disable", 0, "key.categories.misc");
        ClientRegistry.registerKeyBinding(forceKey);
        ClientRegistry.registerKeyBinding(disableKey);
        FMLCommonHandler.instance().bus().register(INSTANCE);
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    public static void evaluateFrame() {
        final SodiumGameOptions.ReducerSettings s = ClientProxy.options().reducer;
        final boolean created = s.enabled && Display.isCreated();
        final boolean visible = !created || Display.isVisible();
        final boolean active = !created || Display.isActive();
        final boolean changed = MACHINE.evaluate(s.enabled, visible, active, s.idleTimeoutMinutes * 60_000_000_000L);
        if (changed || configDirty) {
            applyTransition(s);
        }
    }

    private static void applyTransition(SodiumGameOptions.ReducerSettings s) {
        final State prev = applied;
        final State next = MACHINE.state();
        applied = next;
        configDirty = false;

        if (prev != null && prev != next) {
            AngelicaMod.LOGGER.info("FPS reducer: {} -> {}", prev, next);
        }

        final int percent = ReducerStateMachine.stateVolume(next, s.unfocusedVolume, s.minimizedVolume, s.idleVolume);
        if (percent != appliedVolumePercent) {
            appliedVolumePercent = percent;
            applyVolume(percent);
        }

        if (next == State.FORCED) {
            hudText = I18n.format("angelica.fps_reducer.hud.forced");
        } else if (MACHINE.keybindDisabled()) {
            hudText = I18n.format("angelica.fps_reducer.hud.disabled");
        } else {
            hudText = null;
        }

        GLStateManager.setPresentSuppressed(next == State.MINIMIZED);
    }

    public static int effectiveCap(int userLimit, boolean inMenu) {
        final SodiumGameOptions.ReducerSettings s = ClientProxy.options().reducer;
        final int stateCap = ReducerStateMachine.stateCap(MACHINE.state(), s.unfocusedFpsLimit, s.idleFpsLimit);
        return ReducerStateMachine.mergeCap(userLimit, stateCap, inMenu, s.limitMenuFrameRate);
    }

    public static boolean skipRender() {
        return MACHINE.state() == State.MINIMIZED;
    }

    public static void onRenderSkipped() {
        final Minecraft mc = Minecraft.getMinecraft();
        if (Display.isActive()) {
            lastActiveMillis = Minecraft.getSystemTime();
            return;
        }
        if (mc.theWorld == null || mc.currentScreen != null || !mc.gameSettings.pauseOnLostFocus) return;
        if (mc.gameSettings.touchscreen && Mouse.isButtonDown(1)) return;
        if (Minecraft.getSystemTime() - lastActiveMillis > 500L) {
            mc.displayInGameMenu();
        }
    }

    public static float scaleVolume(float user) {
        return ReducerStateMachine.masterVolume(user, volumePercent);
    }

    private static void applyVolume(int percent) {
        volumePercent = percent;
        final Minecraft mc = Minecraft.getMinecraft();
        final SoundHandler handler = mc.getSoundHandler();
        if (handler == null || mc.gameSettings == null) return;
        final float user = mc.gameSettings.getSoundLevel(SoundCategory.MASTER);
        handler.setSoundLevel(SoundCategory.MASTER, scaleVolume(user));
    }

    public static void markConfigChanged() {
        configDirty = true;
    }

    public static String debugTag() {
        return applied == null ? null : applied.debugTag();
    }

    public static void onInput() {
        MACHINE.onInput();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (forceKey != null) {
            while (forceKey.isPressed()) {
                MACHINE.toggleForced();
                configDirty = true;
            }
        }
        if (disableKey != null) {
            while (disableKey.isPressed()) {
                MACHINE.toggleKeybindDisabled();
                configDirty = true;
            }
        }
    }

    private static void drawHud(boolean requireNoWorld) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.showDebugInfo) return;
        if (requireNoWorld && mc.theWorld != null) return;
        mc.fontRenderer.drawStringWithShadow(hudText, 2, 2, 0xFFFFFF);
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || hudText == null) return;
        drawHud(false);
    }

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (hudText == null || !(event.gui instanceof GuiMainMenu)) return;
        drawHud(true);
    }
}
