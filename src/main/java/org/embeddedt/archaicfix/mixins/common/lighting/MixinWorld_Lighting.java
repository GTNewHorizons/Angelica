package org.embeddedt.archaicfix.mixins.common.lighting;

import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;
import org.embeddedt.archaicfix.lighting.api.ILightingEngineProvider;
import org.embeddedt.archaicfix.lighting.world.lighting.LightingEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;

@Mixin(World.class)
public abstract class MixinWorld_Lighting implements ILightingEngineProvider {
    @Shadow protected Set activeChunkSet;

    @Shadow public abstract IChunkProvider getChunkProvider();

    private LightingEngine lightingEngine;

    /**
     * @author Angeline
     * Initialize the lighting engine on world construction.
     */
    @Redirect(method = "<init>(Lnet/minecraft/world/storage/ISaveHandler;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;Lnet/minecraft/world/WorldProvider;Lnet/minecraft/profiler/Profiler;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/storage/ISaveHandler;loadWorldInfo()Lnet/minecraft/world/storage/WorldInfo;"))
    private WorldInfo onConstructed(ISaveHandler handler) {
        this.lightingEngine = new LightingEngine((World) (Object) this);
        return handler.loadWorldInfo();
    }


    /**
     * Directs the light update to the lighting engine and always returns a success value.
     * @author Angeline
     * @reason Phosphor replaces the lighting engine with a more efficient implementation.
     */
    @Overwrite
    public boolean updateLightByType(EnumSkyBlock type, int x, int y, int z) {
        this.lightingEngine.scheduleLightUpdate(type, x, y, z);

        return true;
    }

    @Override
    public LightingEngine getLightingEngine() {
        return this.lightingEngine;
    }
}
