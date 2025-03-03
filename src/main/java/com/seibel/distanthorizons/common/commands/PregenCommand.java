package com.seibel.distanthorizons.common.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.core.generation.PregenManager;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.server.level.ServerLevel;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class PregenCommand extends AbstractCommand
{
	private final PregenManager pregenManager = new PregenManager();
	
	@Override
	public LiteralArgumentBuilder<CommandSourceStack> buildCommand()
	{
		LiteralArgumentBuilder<CommandSourceStack> statusCommand = literal("status")
				.executes(this::pregenStatus);
		
		LiteralArgumentBuilder<CommandSourceStack> startCommand = literal("start")
				.then(argument("dimension", DimensionArgument.dimension())
						.then(argument("origin", ColumnPosArgument.columnPos())
								.then(argument("chunkRadius", integer(32))
										.executes(this::pregenStart))));
		
		LiteralArgumentBuilder<CommandSourceStack> stopCommand = literal("stop")
				.executes(this::pregenStop);
		
		return literal("pregen")
				.then(statusCommand)
				.then(startCommand)
				.then(stopCommand);
	}
	
	
	private int pregenStatus(CommandContext<CommandSourceStack> c)
	{
		String statusString = this.pregenManager.getStatusString();
		//noinspection ReplaceNullCheck
		if (statusString != null)
		{
			return this.sendSuccessResponse(c, statusString, false);
		}
		else
		{
			return this.sendSuccessResponse(c, "Pregen is not running", false);
		}
	}
	
	private int pregenStart(CommandContext<CommandSourceStack> c) throws CommandSyntaxException
	{
		this.sendSuccessResponse(c, "Starting pregen. Progress will be in the server console.", true);
		
		ServerLevel level = DimensionArgument.getDimension(c, "dimension");
		ColumnPos origin = ColumnPosArgument.getColumnPos(c, "origin");
		int chunkRadius = getInteger(c, "chunkRadius");
		
		CompletableFuture<Void> future = this.pregenManager.startPregen(
				ServerLevelWrapper.getWrapper(level),
				new DhBlockPos2D(#if MC_VER >= MC_1_19_2 origin.x(), origin.z() #else origin.x, origin.z #endif),
				chunkRadius
		);
		
		future.whenComplete((result, throwable) -> {
			if (throwable instanceof CancellationException)
			{
				this.sendSuccessResponse(c, "Pregen is cancelled", true);
				return;
			}
			else if (throwable != null)
			{
				this.sendFailureResponse(c, "Pregen failed: " + throwable.getMessage() + "\n Check the logs for more details.");
				return;
			}
			
			this.sendSuccessResponse(c, "Pregen is complete", true);
		});
		
		return 1;
	}
	
	private int pregenStop(CommandContext<CommandSourceStack> c)
	{
		CompletableFuture<Void> runningPregen = this.pregenManager.getRunningPregen();
		if (runningPregen == null)
		{
			return this.sendFailureResponse(c, "Pregen is not running");
		}
		
		runningPregen.cancel(true);
		return 1;
	}
	
}
