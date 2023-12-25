package net.coderbot.iris.client;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.mitchej123.hodgepodge.client.HodgepodgeClient;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.coderbot.iris.Iris;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.List;
import java.util.Objects;

public class IrisDebugScreenHandler {
    public static final IrisDebugScreenHandler INSTANCE = new IrisDebugScreenHandler();
    private static final List<BufferPoolMXBean> iris$pools = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);

    private static final BufferPoolMXBean iris$directPool;

    static {
        BufferPoolMXBean found = null;

        for (BufferPoolMXBean pool : iris$pools) {
            if (pool.getName().equals("direct")) {
                found = pool;
                break;
            }
        }

        iris$directPool = Objects.requireNonNull(found);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onRenderGameOverlayTextEvent(RenderGameOverlayEvent.Text event) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.showDebugInfo) {
            event.right.add(2, "Direct Buffers: +" + iris$humanReadableByteCountBin(iris$directPool.getMemoryUsed()));

            event.right.add("");

            if (Iris.getIrisConfig().areShadersEnabled()) {
                event.right.add("[" + Iris.MODNAME + "] Shaderpack: " + Iris.getCurrentPackName() + (Iris.isFallback() ? " (fallback)" : ""));
                Iris.getCurrentPack().ifPresent(pack -> event.right.add("[" + Iris.MODNAME + "] " + pack.getProfileInfo()));
            } else {
                event.right.add("[" + Iris.MODNAME + "] Shaders are disabled");
            }
            if(AngelicaConfig.speedupAnimations) {
                event.right.add(9, "animationsMode: " + AngelicaMod.animationsMode);
            }

            Iris.getPipelineManager().getPipeline().ifPresent(pipeline -> pipeline.addDebugText(event.left));

        }
    }

    private static String iris$humanReadableByteCountBin(long bytes) {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) {
            return bytes + " B";
        }
        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        return String.format("%.3f %ciB", value / 1024.0, ci.current());
    }

}
