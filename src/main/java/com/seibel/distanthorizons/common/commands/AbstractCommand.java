package com.seibel.distanthorizons.common.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.seibel.distanthorizons.common.wrappers.misc.ServerPlayerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IServerPlayerWrapper;
import net.minecraft.commands.CommandSourceStack;

#if MC_VER >= MC_1_19_2
import net.minecraft.network.chat.Component;

import java.util.Objects;
#else // < 1.19.2
import net.minecraft.network.chat.TranslatableComponent;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
#endif

/**
 * Abstract class providing common functionality for DH's commands.
 */
public abstract class AbstractCommand
{
	public abstract LiteralArgumentBuilder<CommandSourceStack> buildCommand();
	
	
	/**
	 * Sends a success response to the player with the given text.
	 *
	 * @param commandContext The command context to send the response to.
	 * @param text The text to display in the success message.
	 * @return 1, indicating that the command was successful.
	 */
	protected int sendSuccessResponse(CommandContext<CommandSourceStack> commandContext, String text, boolean notifyAdmins)
	{
		#if MC_VER >= MC_1_20_1
		commandContext.getSource().sendSuccess(() -> Component.literal(text), notifyAdmins);
		#elif MC_VER >= MC_1_19_2
		commandContext.getSource().sendSuccess(Component.literal(text), notifyAdmins);
		#else
		commandContext.getSource().sendSuccess(new TranslatableComponent(text), notifyAdmins);
		#endif
		return 1;
	}
	
	/**
	 * Sends a failure response to the player with the given text.
	 *
	 * @param commandContext The command context to send the response to.
	 * @param text The text to display in the failure message.
	 * @return 1, indicating that the command was successful.
	 */
	protected int sendFailureResponse(CommandContext<CommandSourceStack> commandContext, String text)
	{
		#if MC_VER >= MC_1_20_1
		commandContext.getSource().sendFailure(Component.literal(text));
		#elif MC_VER >= MC_1_19_2
		commandContext.getSource().sendFailure(Component.literal(text));
		#else
		commandContext.getSource().sendFailure(new TranslatableComponent(text));
		#endif
		return 1;
	}
	
	/**
	 * Gets the server player from a command context.
	 *
	 * @param commandContext The command context to get the server player from.
	 * @return The server player wrapper for the player who sent the command.
	 */
	protected IServerPlayerWrapper getSourcePlayer(CommandContext<CommandSourceStack> commandContext) #if MC_VER < MC_1_19_2 throws CommandSyntaxException #endif
	{
		#if MC_VER >= MC_1_19_2
		return ServerPlayerWrapper.getWrapper(Objects.requireNonNull(commandContext.getSource().getPlayer()));
		#else
		return ServerPlayerWrapper.getWrapper(commandContext.getSource().getPlayerOrException());
		#endif
	}
	
	/**
	 * Checks if the source of a command is a player.
	 *
	 * @param source The source of the command to check.
	 * @return True if the source is a player, false otherwise.
	 */
	protected boolean isPlayerSource(CommandSourceStack source)
	{
		#if MC_VER >= MC_1_19_2
		return source.isPlayer();
		#else
		try
		{
			source.getPlayerOrException();
			return true;
		}
		catch (CommandSyntaxException e)
		{
			return false;
		}
		#endif
	}
	
}
