package com.seibel.distanthorizons.common.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import net.minecraft.commands.CommandSourceStack;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.commands.Commands.literal;

public class DebugCommand extends AbstractCommand
{
	@Override
	public LiteralArgumentBuilder<CommandSourceStack> buildCommand()
	{
		return literal("debug")
				.executes(c -> {
					List<String> lines = new ArrayList<>();
					F3Screen.addStringToDisplay(lines);
					return this.sendSuccessResponse(c, String.join("\n", lines), false);
				});
	}
	
}
