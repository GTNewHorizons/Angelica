package com.gtnewhorizons.angelica.commands;

// Debug commands adapted from Beddium by Ven and FalsePattern

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.debug.ChunkDebugMinimap;
import com.gtnewhorizons.angelica.debug.flyby.FlybyRoute;
import com.gtnewhorizons.angelica.debug.flyby.FlybyRunner;
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

    private static final List<String> SUBCOMMANDS = Arrays.asList("wireframe", "fog", "minimap", "flyby", "help");

    @Override
    public String getCommandName() {
        return "angelica";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/angelica <wireframe|fog|minimap|flyby|help>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, SUBCOMMANDS.toArray(new String[0]));
        }
        if (args.length == 2 && "flyby".equalsIgnoreCase(args[0])) {
            final List<String> options = new ArrayList<>();
            for (FlybyRoute route : FlybyRoute.values()) {
                options.add(route.id());
            }
            options.add("cancel");
            return getListOfStringsMatchingLastWord(args, options.toArray(new String[0]));
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
            case "flyby"     -> handleFlyby(sender, args);
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

    private void handleFlyby(ICommandSender sender, String[] args) {
        if (args.length >= 2 && "cancel".equalsIgnoreCase(args[1])) {
            FlybyRunner.INSTANCE.cancel();
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] " + EnumChatFormatting.WHITE + "Flyby cancelled"));
            return;
        }

        if (FlybyRunner.cannotRun()) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED
                + "[Angelica] Flyby is singleplayer only!!"));
            return;
        }

        if (args.length < 2) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] " + EnumChatFormatting.WHITE + "Usage: /angelica flyby <" + FlybyRoute.ids() + "|cancel> [length] [blocksPerTick] [Pig*24@10]"));
            for (FlybyRoute r : FlybyRoute.values()) {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "  " + r.id() + " - default " + r.defaultLength() + " " + r.lengthUnit()));
            }
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "  current: " + FlybyRunner.INSTANCE.describe()));
            return;
        }

        final FlybyRoute route = FlybyRoute.byId(args[1]);
        if (route == null) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "[Angelica] Unknown route '" + args[1] + "', expected one of " + FlybyRoute.ids()));
            return;
        }

        int length = 0;
        double speed = 0.0D;
        String mobs = "";
        boolean confirmed = false;
        int numbersSeen = 0;
        for (int i = 2; i < args.length; i++) {
            final String arg = args[i];
            if ("confirm".equalsIgnoreCase(arg)) {
                confirmed = true;
                continue;
            }
            if (arg.indexOf('*') > 0) {
                mobs = mobs.isEmpty() ? arg : mobs + "," + arg;
                continue;
            }
            try {
                if (numbersSeen == 0) {
                    length = Integer.parseInt(arg);
                } else if (numbersSeen == 1) {
                    speed = Double.parseDouble(arg);
                } else {
                    sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "[Angelica] Too many numbers: " + arg));
                    return;
                }
                numbersSeen++;
            } catch (NumberFormatException e) {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED
                    + "[Angelica] Expected a number or a mob group like Pig*24@10, got: " + arg));
                return;
            }
        }

        final int used = length > 0 ? length : route.defaultLength();
        if (!mobs.isEmpty() && !SystemProperties.FLYBY_DISCARD_WORLD_CHANGES && !confirmed) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED
                + "[Angelica] '" + mobs + "' kills every non-player entity in every loaded dimension."));
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED
                + "[Angelica] angelica.flyby.discardWorldChanges is off, so this will be written to the save."));
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW
                + "[Angelica] Add 'confirm' to the end of the command to run."));
            return;
        }

        final String chosenMobs = mobs.isEmpty() ? SystemProperties.FLYBY_MOBS : mobs;
        FlybyRunner.INSTANCE.start(route, used, SystemProperties.FLYBY_WARMUP_TICKS, speed, chosenMobs);
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] " + EnumChatFormatting.WHITE
            + "Flyby started: " + route.id() + " for " + used + " " + route.lengthUnit()
            + " at " + route.speedOr(speed) + " b/t"
            + " (" + route.toTicks(used, speed) + " ticks)"
            + ", seed=" + (SystemProperties.FLYBY_SEED == 0L ? "off" : Long.toHexString(SystemProperties.FLYBY_SEED))
            + ", freezeEntities=" + SystemProperties.FLYBY_FREEZE_ENTITIES
            + ", mobs=" + (chosenMobs.isEmpty() ? "none" : chosenMobs)));
    }

    private void sendHelp(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] Debug Commands:"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "  /angelica wireframe" + EnumChatFormatting.WHITE + " - Toggle wireframe rendering"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "  /angelica fog" + EnumChatFormatting.WHITE + " - Toggle fog debug on F3"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "  /angelica minimap" + EnumChatFormatting.WHITE + " - Toggle chunk debug overlay"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "  /angelica flyby <" + FlybyRoute.ids() + ">" + EnumChatFormatting.WHITE + " - Run a deterministic benchmark route"));
    }

    @Override
    public int compareTo(Object o) {
        if (o instanceof ICommand cmd) {
            return this.getCommandName().compareTo(cmd.getCommandName());
        }
        return 0;
    }
}
