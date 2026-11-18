package com.gtnewhorizons.angelica.debug.profiling;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.profiling.TracyBackend;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Locale;

public final class TracyCaptureNotifier {
    private static final Logger LOGGER = LogManager.getLogger("Angelica/Tracy");

    public static final TracyCaptureNotifier INSTANCE = new TracyCaptureNotifier();

    private File file;
    private boolean inFlight;
    private KeyBinding captureKey;

    private TracyCaptureNotifier() {}

    public void register() {
        this.captureKey = new KeyBinding("angelica.keybind.tracyCapture", 0, AngelicaMod.KEY_CATEGORY);
        ClientRegistry.registerKeyBinding(this.captureKey);
        FMLCommonHandler.instance().bus().register(this);
    }

    public void toggle() {
        final int state = Tracy.captureState();
        if (state == TracyBackend.CAPTURE_CONNECTING || state == TracyBackend.CAPTURE_RECORDING) {
            Tracy.captureStop();
        } else if (state != TracyBackend.CAPTURE_SAVING) {
            final Minecraft mc = Minecraft.getMinecraft();
            final String error = this.startCapture(AngelicaConfig.tracyCaptureSeconds);
            if (error != null) {
                report(mc, true, "Tracy capture failed to start: " + error);
            } else {
                report(mc, false, "Tracy capture started: " + this.path());
            }
        }
    }

    public String startCapture(int seconds) {
        final File candidate = new File(SystemProperties.PROFILE_DIR, AsprofRecorder.fileName("tracy", "tracy", System.currentTimeMillis()));
        final String error = Tracy.captureStart(candidate.getPath(), seconds);
        if (error != null) return error;
        this.file = candidate;
        this.inFlight = true;
        return null;
    }

    public String path() {
        return this.file == null ? null : this.file.getAbsolutePath();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (this.captureKey != null && this.captureKey.isPressed()) {
            this.toggle();
        }

        if (!this.inFlight) return;
        final Minecraft mc = Minecraft.getMinecraft();
        final int state = Tracy.captureState();
        if (state == TracyBackend.CAPTURE_DONE) {
            report(mc, false, "Tracy capture saved: " + this.path() + sizeSuffix(this.file));
            this.inFlight = false;
        } else if (state == TracyBackend.CAPTURE_FAILED) {
            report(mc, true, "Tracy capture failed: " + Tracy.captureError());
            this.inFlight = false;
        }
    }

    private static String sizeSuffix(File file) {
        if (file == null) return "";
        final long bytes = file.length();
        return String.format(Locale.ROOT, " (%.1f MB)", bytes / (1024.0 * 1024.0));
    }

    private static void report(Minecraft mc, boolean failure, String text) {
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText((failure ? EnumChatFormatting.RED : EnumChatFormatting.AQUA) + "[Angelica] " + text));
        } else if (failure) {
            LOGGER.warn(text);
        } else {
            LOGGER.info(text);
        }
    }
}
