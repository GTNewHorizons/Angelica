package com.gtnewhorizons.angelica.rendering;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.CodeSource;
import java.util.Arrays;

import net.minecraft.block.Block;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.angelica.mixins.interfaces.IRenderingRegistryExt;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.GameRegistry.UniqueIdentifier;

public final class WorkerWorldAccess {

    private static final String REPORT_URL = "https://github.com/GTNewHorizons/angelica/issues";
    private static final int INITIAL_SLOTS = 64;

    private static final Logger LOGGER = LogManager.getLogger("Angelica");

    private static final LoggedRenderTypeErrors READS = new LoggedRenderTypeErrors();
    private static final LoggedRenderTypeErrors WRITES = new LoggedRenderTypeErrors();

    private WorkerWorldAccess() {}

    public static void readOutsideSlice(String worldMethod, int x, int y, int z, Block renderingBlock) {
        if (!READS.shouldLog(renderingBlock)) return;
        LOGGER.warn(describe(worldMethod, "read outside the chunk slice", x, y, z, renderingBlock), new Throwable());
    }

    public static void blockedWrite(String worldMethod, int x, int y, int z, Block renderingBlock) {
        if (!WRITES.shouldLog(renderingBlock)) return;
        LOGGER.error(describe(worldMethod, "ignored, it would corrupt world data", x, y, z, renderingBlock), new Throwable());
    }

    private static String describe(String worldMethod, String outcome, int x, int y, int z, Block block) {
        final StringBuilder sb = new StringBuilder(224);
        sb.append("Off-thread World.").append(worldMethod).append(' ').append(outcome).append(" at ").append(x).append(',').append(y).append(',').append(z);
        if (block != null) {
            sb.append(". Rendering ").append(nameOf(block));
            final ISimpleBlockRenderingHandler isbrh = isbrhOf(block);
            if (isbrh != null) {
                sb.append(" via ").append(isbrh.getClass().getName());
                final String owner = modIdOf(isbrh.getClass());
                if (owner != null) sb.append(" from ").append(owner);
            }
        }
        return sb.append(". Please report with this trace: ").append(REPORT_URL).toString();
    }

    private static String nameOf(Block block) {
        try {
            final UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(block);
            if (id != null) return id.modId + ':' + id.name;
        } catch (Throwable ignored) {}
        return block.getClass().getName();
    }

    @SuppressWarnings("deprecation")
    private static ISimpleBlockRenderingHandler isbrhOf(Block block) {
        try {
            return ((IRenderingRegistryExt) RenderingRegistry.instance()).getISBRH(block.getRenderType());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String modIdOf(Class<?> clazz) {
        try {
            final CodeSource source = clazz.getProtectionDomain() == null ? null : clazz.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) return null;
            final File jar = new File(jarFileUrl(source.getLocation()).toURI());
            for (ModContainer mod : Loader.instance().getModList()) {
                if (jar.equals(mod.getSource())) return mod.getModId();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static URL jarFileUrl(URL location) throws MalformedURLException {
        if (!"jar".equals(location.getProtocol())) return location;
        final String nested = location.getPath();
        final int separator = nested.indexOf("!/");
        return new URL(separator < 0 ? nested : nested.substring(0, separator));
    }

    private static final class LoggedRenderTypeErrors {

        private volatile boolean[] logged = new boolean[INITIAL_SLOTS];

        boolean shouldLog(Block block) {
            final int renderType = block == null ? -1 : block.getRenderType();
            final int slot = renderType < 0 ? 0 : renderType + 1;
            final boolean[] current = this.logged;
            return (slot >= current.length || !current[slot]) && markLogged(slot);
        }

        private synchronized boolean markLogged(int slot) {
            boolean[] current = this.logged;
            if (slot < current.length) {
                if (current[slot]) return false;
                current[slot] = true;
                return true;
            }
            current = Arrays.copyOf(current, Math.max(slot + 1, current.length * 2));
            current[slot] = true;
            this.logged = current;
            return true;
        }
    }
}
