package jss.notfine.config;

import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.SimpleGuiConfig;
import net.minecraft.client.gui.GuiScreen;

public class MCPatcherForgeGuiConfig extends SimpleGuiConfig {
    public MCPatcherForgeGuiConfig(GuiScreen parent) throws ConfigException {
        super(parent, "mcpatcherforge", "MCPatcherForge", true,
            MCPatcherForgeConfig.CustomColors.class,
            MCPatcherForgeConfig.CustomItemTextures.class,
            MCPatcherForgeConfig.ConnectedTextures.class,
            MCPatcherForgeConfig.ExtendedHD.class,
            MCPatcherForgeConfig.RandomMobs.class,
            MCPatcherForgeConfig.BetterSkies.class
        );
    }
}
