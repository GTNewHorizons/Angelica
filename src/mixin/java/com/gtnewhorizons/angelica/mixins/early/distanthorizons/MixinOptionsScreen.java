/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.gtnewhorizons.angelica.mixins.early.distanthorizons;

import com.seibel.distanthorizons.common.wrappers.gui.GetConfigScreen;
import com.seibel.distanthorizons.common.wrappers.gui.TexturedButtonWidget;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.core.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a button to the menu to goto the config
 *
 * @author coolGi
 * @version 12-02-2021
 */
@Mixin(GuiOptions.class)
public class MixinOptionsScreen extends GuiScreen
{
	// Get the texture for the button
	private static final ResourceLocation ICON_TEXTURE = new ResourceLocation(ModInfo.ID, "textures/gui/button.png");

    private static final int button_id = 99;

	@Inject(at = @At("HEAD"), method = "initGui")
	private void lodconfig$init(CallbackInfo ci)
	{
		if (Config.Client.showDhOptionsButtonInMinecraftUi.get())
			this.buttonList.add(
					(new TexturedButtonWidget(
							// Where the button is on the screen
							this.width / 2 - 180, this.height / 6 - 12,
							// Width and height of the button
							20, 20,
							// Offset
							0, 0,
							// Some textuary stuff
							20, ICON_TEXTURE, 20, 20,
							// Create the button and tell it where to go
							// For now it goes to the client option by default
							button_id,
							// Add a title to the button
							"DH" /* ModInfo.ID + ".title" */)));

	}

    @Inject(at = @At("HEAD"), method = "actionPerformed", cancellable = true)
    private void lodconfig$actionPerformed(GuiButton button, CallbackInfo ci)
    {
        if (button.id == button_id)
        {
            Minecraft.getMinecraft().displayGuiScreen(GetConfigScreen.getScreen(this));
            ci.cancel();
        }
    }

}
