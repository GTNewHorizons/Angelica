package com.seibel.distanthorizons.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;

import static com.seibel.distanthorizons.core.network.messages.MessageRegistry.DEBUG_CODEC_CRASH_MESSAGE;
import static net.minecraft.commands.Commands.literal;

/**
 * Initializes commands of the mod.
 */
public class CommandInitializer
{
	private final CommandDispatcher<CommandSourceStack> commandDispatcher;
	
	/**
	 * Constructs a new instance of this class.
	 *
	 * @param commandDispatcher The dispatcher to use for registering commands.
	 */
	public CommandInitializer(CommandDispatcher<CommandSourceStack> commandDispatcher)
	{
		this.commandDispatcher = commandDispatcher;
	}
	
	
	
	/**
	 * Initializes all available commands.
	 */
	public void initCommands()
	{
		LiteralArgumentBuilder<CommandSourceStack> builder = literal("dh")
				.requires(source -> source.hasPermission(4));
		
		builder.then(new ConfigCommand().buildCommand());
		builder.then(new DebugCommand().buildCommand());
		builder.then(new PregenCommand().buildCommand());
		
		if (DEBUG_CODEC_CRASH_MESSAGE)
		{
			builder.then(new CrashCommand().buildCommand());
		}
		
		this.commandDispatcher.register(builder);
	}
	
}
