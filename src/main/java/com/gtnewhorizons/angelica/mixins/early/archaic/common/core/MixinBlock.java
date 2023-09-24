package com.gtnewhorizons.angelica.mixins.early.archaic.common.core;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.block.Block;
import net.minecraft.server.MinecraftServer;
import org.embeddedt.archaicfix.block.ThreadedBlockData;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.ref.WeakReference;

@Mixin(Block.class)
public class MixinBlock {
    @Shadow public Block.SoundType stepSound;
    private WeakReference<MinecraftServer> lastServer = new WeakReference<>(null);
    private ThreadedBlockData arch$serverThreadedData = null;
    private ThreadedBlockData arch$clientThreadedData = new ThreadedBlockData();

    private final ThreadLocal<ThreadedBlockData> arch$threadBlockData = new ThreadLocal<>();

    @Redirect(method = "<init>", at = @At(opcode = Opcodes.PUTFIELD, value = "FIELD", target = "Lnet/minecraft/block/Block;stepSound:Lnet/minecraft/block/Block$SoundType;", ordinal = 0))
    private void onConstruct(Block block, Block.SoundType sound) {
        stepSound = sound;
    }

    private ThreadedBlockData arch$calculateThreadedData() {
        FMLCommonHandler inst = FMLCommonHandler.instance();
        Side trueSide;
        if(inst.getSidedDelegate() == null) {
            trueSide = inst.getEffectiveSide();
        } else {
            trueSide = inst.getSide();
        }
        if(trueSide == Side.SERVER || inst.getEffectiveSide() == Side.SERVER) {
            if(lastServer.get() != inst.getMinecraftServerInstance()) {
                lastServer = new WeakReference<>(inst.getMinecraftServerInstance());
                arch$serverThreadedData = new ThreadedBlockData(arch$clientThreadedData);
            }
            return arch$serverThreadedData;
        }
        return arch$clientThreadedData;
    }

    public ThreadedBlockData arch$getThreadedData() {
        ThreadedBlockData calculated = arch$threadBlockData.get();
        if(calculated == null) {
            calculated = arch$calculateThreadedData();
            arch$threadBlockData.set(calculated);
        }
        return calculated;
    }
}
