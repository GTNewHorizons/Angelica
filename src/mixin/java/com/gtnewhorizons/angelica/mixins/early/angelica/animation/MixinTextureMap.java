package com.gtnewhorizons.angelica.mixins.early.angelica.animation;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.glsm.managers.GLTextureManager;
import com.gtnewhorizons.angelica.mixins.interfaces.IPatchedTextureAtlasSprite;
import com.gtnewhorizons.angelica.utils.AnimationMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import org.lwjglx.opengl.GL11;
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
     * @author laetansky
     * @reason only update animations for textures that are being currently drawn By default minecraft handles any
     *         animations that present in listAnimatedSprites no matter if you see it or not which can lead to a huge
     *         performance decrease
     */
    @Overwrite
    public void updateAnimations() {
        final boolean renderAllAnimations = AngelicaMod.animationsMode.is(AnimationMode.ALL);
        final boolean renderVisibleAnimations = AngelicaMod.animationsMode.is(AnimationMode.VISIBLE_ONLY);

        mc.mcProfiler.startSection("updateAnimations");
        GLTextureManager.glBindTexture(GL11.GL_TEXTURE_2D, this.getGlTextureId());
        // C Style loop should be faster
        final int size = listAnimatedSprites.size();
        for (int i = 0; i < size; i++) {
            final TextureAtlasSprite textureAtlasSprite = listAnimatedSprites.get(i);
            final IPatchedTextureAtlasSprite patchedTextureAtlasSprite = ((IPatchedTextureAtlasSprite) textureAtlasSprite);

            if (renderAllAnimations || (renderVisibleAnimations && patchedTextureAtlasSprite.needsAnimationUpdate())) {
                mc.mcProfiler.startSection(textureAtlasSprite.getIconName());
                textureAtlasSprite.updateAnimation();
                patchedTextureAtlasSprite.unmarkNeedsAnimationUpdate();
                mc.mcProfiler.endSection();
            } else {
                patchedTextureAtlasSprite.updateAnimationsDryRun();
            }
        }
        mc.mcProfiler.endSection();
    }
}
