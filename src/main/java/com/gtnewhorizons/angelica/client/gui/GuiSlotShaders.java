package com.gtnewhorizons.angelica.client.gui;

import java.util.List;

import com.gtnewhorizons.angelica.client.Shaders;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.Tessellator;

public class GuiSlotShaders extends GuiSlot {

    private List<String> shaderslist;

    final GuiShaders shadersGui;

    public GuiSlotShaders(GuiShaders par1GuiShaders) {
        // super(par1GuiShaders.getMc(), par1GuiShaders.width / 2 + 20, par1GuiShaders.height, 40, par1GuiShaders.height
        // - 70, 16);
        super(
                par1GuiShaders.getMc(),
                par1GuiShaders.width / 2 + 20,
                par1GuiShaders.height,
                40,
                par1GuiShaders.height - 70,
                16);
        this.shadersGui = par1GuiShaders;
        this.shaderslist = Shaders.listofShaders();
    }

    public void updateList() {
        this.shaderslist = Shaders.listofShaders();
    }

    @Override
    /** getSize */
    protected int getSize() {
        return this.shaderslist.size();
    }

    @Override
    /** elementClicked */
    protected void elementClicked(int par1, boolean par2, int par3, int par4) {
        Shaders.setShaderPack((String) shaderslist.get(par1));
        shadersGui.needReinit = false;
        Shaders.loadShaderPack();
        Shaders.uninit();
    }

    @Override
    /** isSelected */
    protected boolean isSelected(int par1) {
        return ((String) this.shaderslist.get(par1)).equals(Shaders.currentshadername);
    }

    @Override
    /** getScrollBarX */
    protected int getScrollBarX() {
        return this.width - 6;
    }

    @Override
    /** getContentHeight */
    protected int getContentHeight() {
        return this.getSize() * 18;
    }

    @Override
    /** drawBackground */
    protected void drawBackground() {
        // this.shadersGui.drawDefaultBackground();
    }

    @Override
    /** drawSlot */
    protected void drawSlot(int par1, int par2, int par3, int par4, Tessellator par5, int par6, int par7) {
        this.shadersGui.drawCenteredString(
                (String) this.shaderslist.get(par1),
                this.shadersGui.width / 4 + 10,
                par3 + 1,
                0xffffff);
    }
}
