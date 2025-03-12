package net.coderbot.iris.gui.element.widget;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.managers.GLTextureManager;
import cpw.mods.fml.client.config.GuiUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL14;

import java.util.function.Consumer;

public class IrisImageButton extends IrisButton {
    private final ResourceLocation textureLocation;
    private final int xTexStart;
    private final int yTexStart;
    private final int yDiffTex;

    public IrisImageButton(int x, int y, int width, int height, int xTexStart, int yTexStart, int yDiffTex, ResourceLocation resourceLocation,Consumer<IrisButton> onPress) {
        super(x, y, width, height, "", onPress);

        this.textureLocation = resourceLocation;
        this.xTexStart = xTexStart;
        this.yTexStart = yTexStart;
        this.yDiffTex = yDiffTex;
    }


    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if(!this.visible) {
            return;
        }

        int yTex = this.yTexStart;
        this.field_146123_n/*isMouseOver*/ = mouseX >= this.xPosition && mouseY >= this.yPosition && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;
        if(this.getHoverState(this.field_146123_n) == 2) {
            yTex += this.yDiffTex;
        }

        // Sets RenderSystem to use solid white as the tint color for blend mode, and enables blend mode
        GL14.glBlendColor(1.0f, 1.0f, 1.0f, 1.0f);
        GLStateManager.enableBlend();

        // Sets RenderSystem to be able to use textures when drawing
        GLTextureManager.enableTexture2D();
        mc.getTextureManager().bindTexture(this.textureLocation);

        // Draw the texture to the screen
        GuiUtils.drawTexturedModalRect(this.xPosition, this.yPosition, this.xTexStart, yTex, width, height, 256);

    }

}
