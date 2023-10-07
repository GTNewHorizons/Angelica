package net.coderbot.iris.gui.option;

import codechicken.nei.config.OptionButton;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;

public class ShaderPackSelectionButtonOption extends Option {
	private final Screen parent;
	private final Minecraft client;

	public ShaderPackSelectionButtonOption(Screen parent, Minecraft client) {
		super("options.iris.shaderPackSelection");
		this.parent = parent;
		this.client = client;
	}

	@Override
	public AbstractWidget createButton(Options options, int x, int y, int width) {
		return new OptionButton(
				x, y, width, 20,
				this,
				I18n.format("options.iris.shaderPackSelection"),
				button -> client.setScreen(new ShaderPackScreen(parent))
		);
	}
}
