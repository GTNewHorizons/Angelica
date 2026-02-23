package com.gtnewhorizons.angelica.mixins.early.angelica.animation;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.mixins.interfaces.IPatchedTextureAtlasSprite;
import com.gtnewhorizons.angelica.utils.AnimationMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(value = TextureMap.class, priority = 999)
@SuppressWarnings("ForLoopReplaceableByForEach")
public abstract class MixinTextureMap extends AbstractTexture {

    @Shadow
    @Final
    private List<TextureAtlasSprite> listAnimatedSprites;

    @Unique
    private static final Minecraft mc = Minecraft.getMinecraft();

    /**
     * @author laetansky, jss2a98aj
     * @reason Only update visible animations; use UV auto-marking via IPatchedTextureAtlasSprite
     */
    @Overwrite
    public void updateAnimations() {
        final boolean renderAll = AngelicaMod.animationsMode.is(AnimationMode.ALL);
        final boolean renderVisible = AngelicaMod.animationsMode.is(AnimationMode.VISIBLE_ONLY);

        mc.mcProfiler.startSection("updateAnimations");
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, this.getGlTextureId());

        final int size = listAnimatedSprites.size();
        for (int i = 0; i < size; i++) {
            final TextureAtlasSprite sprite = listAnimatedSprites.get(i);
            final IPatchedTextureAtlasSprite patched = (IPatchedTextureAtlasSprite) sprite;

            // needsAnimationUpdate() is one-shot: returns true if marked, then auto-resets
            if (renderAll || (renderVisible && patched.needsAnimationUpdate())) {
                sprite.updateAnimation();
            } else {
                // Keep frame counters in sync for sprites not being updated
                patched.updateAnimationsDryRun();
            }
        }
        mc.mcProfiler.endSection();
    }
}
