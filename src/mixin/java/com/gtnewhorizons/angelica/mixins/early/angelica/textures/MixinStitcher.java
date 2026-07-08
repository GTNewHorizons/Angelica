package com.gtnewhorizons.angelica.mixins.early.angelica.textures;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.gtnewhorizons.angelica.textures.FastTextureStitcher;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Stitcher.class)
public abstract class MixinStitcher {

    @Shadow @Final private Set setStitchHolders;
    @Shadow @Final private int maxWidth;
    @Shadow @Final private int maxHeight;
    @Shadow @Final private boolean forcePowerOf2;
    @Shadow private int currentWidth;
    @Shadow private int currentHeight;

    @Unique private List<TextureAtlasSprite> angelica$stitchedSprites = Collections.emptyList();

    /**
     * @author Angelica
     * @reason Use a skyline packer instead of vanilla recursive slots.
     */
    @Overwrite
    public void doStitch() {
        Stitcher.Holder[] holders = (Stitcher.Holder[]) this.setStitchHolders.toArray(new Stitcher.Holder[this.setStitchHolders.size()]);
        Arrays.sort(holders);

        FastTextureStitcher.Result result = FastTextureStitcher.stitch(holders, this.maxWidth, this.maxHeight, this.forcePowerOf2);
        this.currentWidth = result.width;
        this.currentHeight = result.height;
        this.angelica$stitchedSprites = result.sprites;
    }

    /**
     * @author Angelica
     * @reason FastTextureStitcher initializes sprites directly.
     */
    @Overwrite
    public List getStichSlots() {
        return new ArrayList(this.angelica$stitchedSprites);
    }
}
