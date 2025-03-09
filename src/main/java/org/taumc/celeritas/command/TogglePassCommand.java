package org.taumc.celeritas.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class TogglePassCommand extends CommandBase {
    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public String getCommandName() {
        return "celeritas_toggle_pass";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/celeritas_toggle_pass [pass_name]";
    }

    private static Stream<TerrainRenderPass> getAllPasses() {
        var renderer = CeleritasWorldRenderer.instanceNullable();

        if (renderer == null) {
            return Stream.empty();
        }

        return renderer.getRenderPassConfiguration().getAllKnownRenderPasses();
    }

    @Override
    public List addTabCompletionOptions(ICommandSender iCommandSender, String[] args) {
         return new ArrayList(getAllPasses().map(TerrainRenderPass::name).toList());
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if(args.length < 1) {
            sender.addChatMessage(new ChatComponentText("Pass name must be provided"));
            return;
        }

        Optional<TerrainRenderPass> foundPass = getAllPasses().filter(pass -> pass.name().equals(args[0])).findFirst();
        if (foundPass.isPresent()) {
            CeleritasWorldRenderer.instance().getRenderSectionManager().toggleRenderingForTerrainPass(foundPass.get());
        } else {
            sender.addChatMessage(new ChatComponentText("Pass " + args[0] + " not found"));
        }
    }

    @Override
    public int compareTo(Object iCommand) {
        return this.getCommandName().compareTo(((ICommand)iCommand).getCommandName());
    }
}
