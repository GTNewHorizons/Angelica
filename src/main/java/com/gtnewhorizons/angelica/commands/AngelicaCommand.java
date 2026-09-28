package com.gtnewhorizons.angelica.commands;

// Debug commands adapted from Beddium by Ven and FalsePattern

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.debug.ChunkDebugMinimap;
import com.gtnewhorizons.angelica.debug.flyby.FlybyRoute;
import com.gtnewhorizons.angelica.debug.flyby.FlybyRunner;
import com.gtnewhorizons.angelica.debug.profiling.AsprofRecorder;
import com.gtnewhorizons.angelica.debug.profiling.TracyCaptureNotifier;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.profiling.TracyBackend;
import com.gtnewhorizons.angelica.rendering.RenderRecovery;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasDebugScreenHandler;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AngelicaCommand extends CommandBase {

    private static final Map<String, String> HELP = buildHelp();
    private static final String[] SUBCOMMANDS = buildSubcommands();
    private static final String USAGE = "/angelica <" + String.join("|", SUBCOMMANDS) + ">";

    private static Map<String, String> buildHelp() {
        final Map<String, String> help = new LinkedHashMap<>();
        if (SystemProperties.debugTooling()) {
            help.put("wireframe", helpLine("wireframe", "Toggle wireframe rendering"));
            help.put("fog", helpLine("fog", "Toggle fog debug on F3"));
            help.put("minimap", helpLine("minimap", "Toggle chunk debug overlay"));
            help.put("flyby", helpLine("flyby <" + FlybyRoute.ids() + ">", "Run a deterministic benchmark route"));
            help.put("profile", helpLine("profile <start|stop|status>", "Control async-profiler (JFR) recording"));
            if (SystemProperties.isDeobf()) {
                help.put("crashtest", helpLine("crashtest", "Arm a crash on the next tile entity render"));
            }
        }
        if (Tracy.ENABLED) {
            help.put("tracy", helpLine("tracy <start|stop|status>", "Control Tracy capture recording"));
        }
        return help;
    }

    private static String[] buildSubcommands() {
        final String[] subcommands = HELP.keySet().toArray(new String[HELP.size() + 1]);
        subcommands[HELP.size()] = "help";
        return subcommands;
    }

    private static String helpLine(String syntax, String description) {
        return EnumChatFormatting.GRAY + "  /angelica " + syntax + EnumChatFormatting.WHITE + " - " + description;
    }

    @Override
    public String getCommandName() {
        return "angelica";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return USAGE;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    static boolean requiresCheats(String subcommand) {
        return "wireframe".equals(subcommand) || "flyby".equals(subcommand);
    }

    private static boolean cheatsAllowed() {
        if (SystemProperties.debugTooling()) return true;
        final Minecraft mc = Minecraft.getMinecraft();
        final IntegratedServer server = mc.getIntegratedServer();
        if (server == null || mc.thePlayer == null) return false;
        return server.getConfigurationManager().func_152596_g(mc.thePlayer.getGameProfile());
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, SUBCOMMANDS);
        }
        if (args.length == 2 && "flyby".equalsIgnoreCase(args[0])) {
            final List<String> options = new ArrayList<>();
            for (FlybyRoute route : FlybyRoute.values()) {
                options.add(route.id());
            }
            options.add("cancel");
            return getListOfStringsMatchingLastWord(args, options.toArray(new String[0]));
        }
        if (args.length == 2 && "profile".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "start", "stop", "status");
        }
        if (args.length == 2 && "tracy".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "start", "stop", "status");
        }
        return new ArrayList<>();
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        final String subcommand = args[0].toLowerCase();
        if (!HELP.containsKey(subcommand)) {
            sendHelp(sender);
            return;
        }
        if (requiresCheats(subcommand) && !cheatsAllowed()) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "[Angelica] " + subcommand + " needs cheats enabled on a world you host"));
            return;
        }

        switch (subcommand) {
            case "wireframe" -> handleWireframe(sender);
            case "fog"       -> handleFog(sender);
            case "minimap"   -> handleMinimap(sender);
            case "flyby"     -> handleFlyby(sender, args);
            case "profile"   -> handleProfile(sender, args);
            case "tracy"     -> handleTracy(sender, args);
            case "crashtest" -> handleCrashTest(sender);
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

    private void handleCrashTest(ICommandSender sender) {
        RenderRecovery.armCrashTest();
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] " + EnumChatFormatting.WHITE + "Crash test armed: fires on the next tile entity render"));
    }

    private void handleFlyby(ICommandSender sender, String[] args) {
        if (args.length >= 2 && "cancel".equalsIgnoreCase(args[1])) {
            FlybyRunner.INSTANCE.cancel();
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] " + EnumChatFormatting.WHITE + "Flyby cancelled"));
            return;
        }

        if (args.length < 2) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] " + EnumChatFormatting.WHITE + "Usage: /angelica flyby <" + FlybyRoute.ids() + "|cancel> [length] [blocksPerTick]"));
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
        if (args.length >= 3) {
            try {
                length = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "[Angelica] Not a length: " + args[2]));
                return;
            }
        }

        double speed = 0.0D;
        if (args.length >= 4) {
            try {
                speed = Double.parseDouble(args[3]);
            } catch (NumberFormatException e) {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "[Angelica] Not a speed: " + args[3]));
                return;
            }
        }

        FlybyRunner.INSTANCE.start(route, length, SystemProperties.FLYBY_WARMUP_TICKS, speed);
        final int used = length > 0 ? length : route.defaultLength();
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] " + EnumChatFormatting.WHITE
            + "Flyby started: " + route.id() + " for " + used + " " + route.lengthUnit()
            + " at " + route.speedOr(speed) + " b/t"
            + " (" + route.toTicks(used, speed) + " ticks)"));
    }

    private void handleProfile(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] " + EnumChatFormatting.WHITE + "Usage: /angelica profile <start|stop|status>"));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "start" -> {
                final String opts;
                if (args.length > 2) {
                    final StringBuilder sb = new StringBuilder();
                    for (int i = 2; i < args.length; i++) {
                        if (i > 2) sb.append(',');
                        sb.append(args[i]);
                    }
                    opts = sb.toString();
                } else {
                    opts = SystemProperties.PROFILE_OPTS;
                }
                final String error = AsprofRecorder.start("manual", opts);
                if (error != null) {
                    sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "[Angelica] " + error));
                } else {
                    sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] " + EnumChatFormatting.WHITE + "Profiling started: " + AsprofRecorder.outputPath()));
                }
            }
            case "stop" -> {
                final String path = AsprofRecorder.outputPath();
                final String error = AsprofRecorder.stop();
                if (error != null) {
                    sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "[Angelica] " + error));
                } else {
                    sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] " + EnumChatFormatting.WHITE + "Profile written to " + path));
                }
            }
            case "status" -> {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] " + EnumChatFormatting.WHITE + AsprofRecorder.status()));
                if (AsprofRecorder.isRecording()) {
                    sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "  " + AsprofRecorder.outputPath()));
                }
            }
            default -> sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] " + EnumChatFormatting.WHITE + "Usage: /angelica profile <start|stop|status>"));
        }
    }

    private void handleTracy(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] " + EnumChatFormatting.WHITE + "Usage: /angelica tracy <start|stop|status> [seconds]"));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "start" -> {
                int seconds = AngelicaConfig.tracyCaptureSeconds;
                if (args.length > 2) {
                    try {
                        seconds = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] " + EnumChatFormatting.WHITE + "Usage: /angelica tracy start [seconds]"));
                        return;
                    }
                    if (seconds < 0) {
                        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] " + EnumChatFormatting.WHITE + "Usage: /angelica tracy start [seconds]"));
                        return;
                    }
                }
                final String error = TracyCaptureNotifier.INSTANCE.startCapture(seconds);
                if (error != null) {
                    sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "[Angelica] " + error));
                } else {
                    final String length = seconds > 0 ? seconds + "s" : "until stopped";
                    sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] " + EnumChatFormatting.WHITE + "Tracy capture started (" + length + "): " + TracyCaptureNotifier.INSTANCE.path()));
                }
            }
            case "stop" -> {
                final int state = Tracy.captureState();
                if (state == TracyBackend.CAPTURE_CONNECTING || state == TracyBackend.CAPTURE_RECORDING) {
                    Tracy.captureStop();
                    sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] " + EnumChatFormatting.WHITE + "Stopping Tracy capture, saving..."));
                } else if (state == TracyBackend.CAPTURE_SAVING) {
                    sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "[Angelica] Tracy capture is already saving"));
                } else {
                    sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "[Angelica] No Tracy capture is running"));
                }
            }
            case "status" -> {
                final int state = Tracy.captureState();
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] " + EnumChatFormatting.WHITE + "Tracy capture: " + tracyStateName(state) + " (" + Tracy.captureElapsedMs() / 1000L + "s)"));
                if (state == TracyBackend.CAPTURE_FAILED) {
                    sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "  " + Tracy.captureError()));
                } else if (TracyCaptureNotifier.INSTANCE.path() != null) {
                    sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "  " + TracyCaptureNotifier.INSTANCE.path()));
                }
            }
            default -> sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] " + EnumChatFormatting.WHITE + "Usage: /angelica tracy <start|stop|status> [seconds]"));
        }
    }

    private static String tracyStateName(int state) {
        return switch (state) {
            case TracyBackend.CAPTURE_IDLE -> "idle";
            case TracyBackend.CAPTURE_CONNECTING -> "connecting";
            case TracyBackend.CAPTURE_RECORDING -> "recording";
            case TracyBackend.CAPTURE_SAVING -> "saving";
            case TracyBackend.CAPTURE_DONE -> "done";
            case TracyBackend.CAPTURE_FAILED -> "failed";
            default -> "unknown";
        };
    }

    private void sendHelp(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] Debug Commands:"));
        for (String line : HELP.values()) {
            sender.addChatMessage(new ChatComponentText(line));
        }
    }

    @Override
    public int compareTo(Object o) {
        if (o instanceof ICommand cmd) {
            return this.getCommandName().compareTo(cmd.getCommandName());
        }
        return 0;
    }
}
