package com.gtnewhorizons.angelica.mixins.late.client.extrautils;

import com.rwtema.extrautils.block.IconConnectedTexture;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.util.IIcon;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(IconConnectedTexture.class)
public class MixinIconConnectedTexture {

    @Final
    @Shadow(remap = false)
    public IIcon[] icons;

    @Unique
    private ThreadLocal<Integer> newN = ThreadLocal.withInitial(() -> 0);

    /**
     * @author Cleptomania
     * @reason Supper thread-safety for connected textures
     */
    @Overwrite(remap = false)
    public void setType(int i) {
        this.newN.set(i);
    }

    /**
     * @author Cleptomania
     * @reason Supper thread-safety for connected textures
     */
    @Overwrite
    public float getMinU() {
        return this.icons[newN.get()].getMinU();
    }

    /**
     * @author Cleptomania
     * @reason Supper thread-safety for connected textures
     */
    @Overwrite
    public float getMaxU() {
        return this.icons[newN.get()].getMaxU();
    }

    /**
     * @author Cleptomania
     * @reason Supper thread-safety for connected textures
     */
    @Overwrite
    public float getMinV() {
        return this.icons[newN.get()].getMinV();
    }

    /**
     * @author Cleptomania
     * @reason Supper thread-safety for connected textures
     */
    @Overwrite
    public float getMaxV() {
        return this.icons[newN.get()].getMaxV();
    }

    /**
     * @author Cleptomania
     * @reason Supper thread-safety for connected textures
     */
    @Overwrite
    public String getIconName() {
        return this.icons[newN.get()].getIconName();
    }

    /**
     * @author Cleptomania
     * @reason Supper thread-safety for connected textures
     */
    @SideOnly(Side.CLIENT)
    @Overwrite
    public int getIconWidth() {
        return this.icons[newN.get()].getIconWidth();
    }

    /**
     * @author Cleptomania
     * @reason Supper thread-safety for connected textures
     */
    @SideOnly(Side.CLIENT)
    @Overwrite
    public int getIconHeight() {
        return this.icons[newN.get()].getIconHeight();
    }
}
