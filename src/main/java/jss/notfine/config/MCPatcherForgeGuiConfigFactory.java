package jss.notfine.config;

import com.gtnewhorizon.gtnhlib.config.SimpleGuiFactory;
import net.minecraft.client.gui.GuiScreen;

public class MCPatcherForgeGuiConfigFactory implements SimpleGuiFactory {
    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return MCPatcherForgeGuiConfig.class;
    }
}
