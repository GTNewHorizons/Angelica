package net.loudcats.wyatt.skidzoom;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

@Mod(modid = "skidzoom", name = "SkidZoom", version = "1.0")
public class SkidZoom {

    public KeyBinding zoomKey;
    private static boolean zoom;
    private int zoomamt = -7;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        zoomKey = new KeyBinding("key.skidzoom", Keyboard.KEY_C, "key.categories.misc");
        zoom = false;
        ClientRegistry.registerKeyBinding(zoomKey);
    }

    @SubscribeEvent
    public void ZoomyZoomZoom(FOVUpdateEvent e) {
        if (zoomKey.getIsKeyPressed() || zoomKey.isPressed()) {
            zoom = true;
            if (zoom) {
                // Adjust zoom level with mouse wheel while holding zoom key
                int dWheel = Mouse.getDWheel();
                if (dWheel != 0) {
                    if (dWheel > 0) {
                        zoomamt--; // decrease fov when scrolling up
                    } else {
                        zoomamt++; // increase fov when scrolling down
                    }
                }

                // Apply the shit
                e.newfov = (float) (e.fov * (1.0 + zoomamt * 0.1));
            }
        } else {
            zoom = false;
            zoomamt = -7;
        }
    }

    public static boolean isZooming() {
        return zoom;
    }
}
