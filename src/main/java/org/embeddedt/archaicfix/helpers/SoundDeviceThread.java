package org.embeddedt.archaicfix.helpers;

import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.client.renderer.GLAllocation;
import org.embeddedt.archaicfix.ArchaicLogger;
import org.embeddedt.archaicfix.proxy.ClientProxy;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCdevice;

import java.nio.IntBuffer;

public class SoundDeviceThread extends Thread {
    private static final int ALC_CONNECTED = 0x313;
    public SoundDeviceThread() {
        setName("Default Sound Device Checker");
        setDaemon(true);
    }
    private String getDefault() {
        return ALC10.alcGetString(null, ALC10.ALC_DEFAULT_DEVICE_SPECIFIER);
    }

    private IntBuffer connectionBuffer = GLAllocation.createDirectIntBuffer(1);

    private boolean isDisconnected(ALCdevice device) {
        if(!ALC10.alcIsExtensionPresent(device, "ALC_EXT_disconnect"))
            return false;
        ALC10.alcGetInteger(device, ALC_CONNECTED, connectionBuffer);
        return connectionBuffer.get(0) == ALC10.ALC_FALSE;
    }

    public void run() {
        try {
            String previousDefault = null;
            SoundManager manager = ReflectionHelper.getPrivateValue(SoundHandler.class, Minecraft.getMinecraft().getSoundHandler(), "sndManager", "field_147694_f");
            while(!Thread.interrupted()) {
                boolean managerLoaded = ReflectionHelper.getPrivateValue(SoundManager.class, manager, "loaded", "field_148617_f");
                if(managerLoaded && !ClientProxy.soundSystemReloadLock) {
                    if(previousDefault == null) {
                        previousDefault = getDefault();
                        continue;
                    }
                    ALCdevice device = AL.getDevice();
                    if((device != null && !device.isValid()) || isDisconnected(device) || !previousDefault.equals(getDefault())) {
                        ArchaicLogger.LOGGER.info("Sound device is not valid anymore, reloading sound system");
                        previousDefault = getDefault();
                        ClientProxy.soundSystemReloadLock = true;
                        Minecraft.getMinecraft().func_152344_a(manager::reloadSoundSystem);
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } catch(Throwable e) {
            ArchaicLogger.LOGGER.error("An exception occured while checking sound device status", e);
        }
    }
}
