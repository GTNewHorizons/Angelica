package com.gtnewhorizons.angelica.commands;

// Debug commands adapted from Beddium by Ven and FalsePattern

import com.gtnewhorizons.angelica.debug.ChunkDebugMinimap;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasDebugScreenHandler;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AngelicaCommand extends CommandBase {

    private static final List<String> SUBCOMMANDS = Arrays.asList("wireframe", "fog", "minimap", "help");

    @Override
    public String getCommandName() {
        return "angelica";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/angelica <wireframe|fog|minimap|help>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0; // Available to all players (client-side only)
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, SUBCOMMANDS.toArray(new String[0]));
        }
        return new ArrayList<>();
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "wireframe" -> handleWireframe(sender);
            case "fog"       -> handleFog(sender);
            case "minimap"   -> handleMinimap(sender);
            default          -> sendHelp(sender);
        }
    }

    private void handleWireframe(ICommandSender sender) {
        CeleritasWorldRenderer.DEBUG_WIREFRAME_MODE = !CeleritasWorldRenderer.DEBUG_WIREFRAME_MODE;
        final String state = CeleritasWorldRenderer.DEBUG_WIREFRAME_MODE ? "ON" : "OFF";
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] " + EnumChatFormatting.WHITE + "Wireframe mode: " + state));
    }

    private void handleFog(ICommandSender sender) {
        CeleritasDebugScreenHandler.showFogDebug = !CeleritasDebugScreenHandler.showFogDebug;
        final String f3State = CeleritasDebugScreenHandler.showFogDebug ? "ON" : "OFF";

        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] " + EnumChatFormatting.WHITE + "Fog debug (F3): " + f3State));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "  " + CeleritasDebugScreenHandler.getFogDebugString()));
    }

    private void handleMinimap(ICommandSender sender) {
        ChunkDebugMinimap.toggle();
        final String state = ChunkDebugMinimap.isEnabled() ? "ON" : "OFF";
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] " + EnumChatFormatting.WHITE + "Chunk debug minimap: " + state));
    }

    private void sendHelp(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] Debug Commands:"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "  /angelica wireframe" + EnumChatFormatting.WHITE + " - Toggle wireframe rendering"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "  /angelica fog" + EnumChatFormatting.WHITE + " - Toggle fog debug on F3"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "  /angelica minimap" + EnumChatFormatting.WHITE + " - Toggle chunk debug overlay"));
    }

    @Override
    public int compareTo(Object o) {
        if (o instanceof ICommand cmd) {
            return this.getCommandName().compareTo(cmd.getCommandName());
        }
        return 0;
    }
}
