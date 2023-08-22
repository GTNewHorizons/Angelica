package com.gtnewhorizons.angelica.client;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;

import org.lwjgl.Sys;

import com.gtnewhorizons.angelica.loading.AngelicaTweaker;

public class GuiShaders extends GuiScreen {

    /** This GUI's parent GUI. */
    protected GuiScreen parentGui;

    private int updateTimer = -1;
    public boolean needReinit;

    /*
     * private class GuiListShaderpacks { int posX,posY,sizeX,sizeY; int viewPosY; int itemHeight = 20; int selection;
     * ArrayList<String> listShaderpacks; public void draw() { Tessellator tess = Tessellator.instance; int listSize =
     * listShaderpacks.size(); int i,j; for (i=viewPosY/itemHeight, j=Math.min((viewPosY+sizeY)/itemHeight,listSize);
     * i<j; ++i) { int itemY = posY-viewPosY+itemHeight*i; boolean selected = i==selection; tess.startDrawingQuads();
     * tess.setColorRGBA(0,0,selected?128:0,128); tess.addVertex(posX,itemY,0); tess.addVertex(posX,itemY+itemHeight,0);
     * tess.addVertex(posX+sizeX,itemY+itemHeight,0); tess.addVertex(posX+sizeX,itemY,0); tess.draw();
     * field_146289_q.drawString(listShaderpacks.get(i), posX, itemY, 0xFFFFFFFF, true); } } } private
     * GuiListShaderpacks guiListShaderpacks;
     */

    private GuiSlotShaders shaderList;

    /** This GUI's 'Done' button. */
    // private GuiSmallButton doneButton;

    public GuiShaders(GuiScreen par1GuiScreen, GameSettings par2GameSettings) {
        this.parentGui = par1GuiScreen;
    }

    private static String toStringOnOff(boolean value) {
        return value ? "On" : "Off";
    }

    @Override
    /**
     * Adds the buttons (and other controls) to the screen in question.
     */
    public void initGui() {
        if (Shaders.shadersConfig == null) Shaders.loadConfig();
        List<GuiButton> buttonList = this.buttonList;
        int width = this.width;
        int height = this.height;
        buttonList.add(
                new GuiButton(
                        17,
                        width * 3 / 4 - 60,
                        30,
                        160,
                        18,
                        "NormalMap: " + toStringOnOff(Shaders.configNormalMap)));
        buttonList.add(
                new GuiButton(
                        18,
                        width * 3 / 4 - 60,
                        50,
                        160,
                        18,
                        "SpecularMap: " + toStringOnOff(Shaders.configSpecularMap)));
        buttonList.add(
                new GuiButton(
                        15,
                        width * 3 / 4 - 60,
                        70,
                        160,
                        18,
                        "RenderResMul: " + String.format("%.04f", Shaders.configRenderResMul)));
        buttonList.add(
                new GuiButton(
                        16,
                        width * 3 / 4 - 60,
                        90,
                        160,
                        18,
                        "ShadowResMul: " + String.format("%.04f", Shaders.configShadowResMul)));
        buttonList.add(
                new GuiButton(
                        10,
                        width * 3 / 4 - 60,
                        110,
                        160,
                        18,
                        "HandDepth: " + String.format("%.04f", Shaders.configHandDepthMul)));
        buttonList.add(
                new GuiButton(
                        9,
                        width * 3 / 4 - 60,
                        130,
                        160,
                        18,
                        "CloudShadow: " + toStringOnOff(Shaders.configCloudShadow)));
        // buttonList.add(new GuiButton(14, width *3 /4 -60, 150, 160, 18, "ShadowClipFrustrum: "
        // +toStringOnOff(Shaders.configShadowClipFrustrum)));
        buttonList.add(
                new GuiButton(
                        4,
                        width * 3 / 4 - 60,
                        170,
                        160,
                        18,
                        "tweakBlockDamage: " + toStringOnOff(Shaders.configTweakBlockDamage)));
        buttonList.add(
                new GuiButton(
                        19,
                        width * 3 / 4 - 60,
                        190,
                        160,
                        18,
                        "OldLighting: " + toStringOnOff(Shaders.configOldLighting)));
        // buttonList.add(new GuiButton(11, width *3 /4 -60, 210, 160, 18, "Tex Min: "
        // +Shaders.texMinFilDesc[Shaders.configTexMinFilB]));
        // buttonList.add(new GuiButton(12, width *3 /4 -60, 230, 160, 18, "Tex_n Mag: "
        // +Shaders.texMagFilDesc[Shaders.configTexMagFilN]));
        // buttonList.add(new GuiButton(13, width *3 /4 -60, 250, 160, 18, "Tex_s Mag: "
        // +Shaders.texMagFilDesc[Shaders.configTexMagFilS]));
        buttonList.add(new GuiButton(6, width * 3 / 4 - 60, height - 25, 160, 20, "Done"));
        buttonList.add(new GuiButton(5, width / 4 - 80, height - 25, 160, 20, "Open shaderpacks folder"));
        this.shaderList = new GuiSlotShaders(this);
        this.shaderList.registerScrollButtons(7, 8); // registerScrollButtons(7, 8);
        this.needReinit = false;
    }

    @Override
    /**
     * actionPerformed Fired when a control is clicked. This is the equivalent of
     * ActionListener.actionPerformed(ActionEvent e).
     */
    protected void actionPerformed(GuiButton par1GuiButton) {
        if (par1GuiButton.enabled) // enabled
        {
            switch (par1GuiButton.id) // id
            {
                case 4: /* New block breaking */
                    Shaders.configTweakBlockDamage = !Shaders.configTweakBlockDamage;
                    // displayString
                    par1GuiButton.displayString = "tweakBlockDamage: " + toStringOnOff(Shaders.configTweakBlockDamage);
                    break;

                case 9: /* Cloud shadow */
                    Shaders.configCloudShadow = !Shaders.configCloudShadow;
                    par1GuiButton.displayString = "CloudShadow: " + toStringOnOff(Shaders.configCloudShadow);
                    break;

                case 10: /* Hand Depth */ {
                    float val = Shaders.configHandDepthMul;
                    float[] choices = { 0.0625f, 0.125f, 0.25f, 0.5f, 1.0f };
                    int i;
                    if (!isShiftKeyDown()) { // isShiftKeyDown
                        for (i = 0; i < choices.length && choices[i] <= val; ++i) {}
                        if (i == choices.length) i = 0;
                    } else {
                        for (i = choices.length - 1; i >= 0 && val <= choices[i]; --i) {}
                        if (i < 0) i = choices.length - 1;
                    }
                    Shaders.configHandDepthMul = choices[i];
                    par1GuiButton.displayString = "HandDepth: " + String.format("%.4f", Shaders.configHandDepthMul);
                    break;
                }

                case 15: /* Render Resolution Multiplier */ {
                    float val = Shaders.configRenderResMul;
                    float[] choices = { 0.25f, 0.3333333333f, 0.5f, 0.7071067812f, 1.0f, 1.414213562f, 2.0f };
                    int i;
                    if (!isShiftKeyDown()) {
                        for (i = 0; i < choices.length && choices[i] <= val; ++i) {}
                        if (i == choices.length) i = 0;
                    } else {
                        for (i = choices.length - 1; i >= 0 && val <= choices[i]; --i) {}
                        if (i < 0) i = choices.length - 1;
                    }
                    Shaders.configRenderResMul = choices[i];
                    par1GuiButton.displayString = "RenderResMul: " + String.format("%.4f", Shaders.configRenderResMul);
                    Shaders.scheduleResize();
                    break;
                }

                case 16: /* Shadow Resolution Multiplier */ {
                    float val = Shaders.configShadowResMul;
                    float[] choices = { 0.25f, 0.3333333333f, 0.5f, 0.7071067812f, 1.0f, 1.414213562f, 2.0f, 3.0f,
                            4.0f };
                    int i;
                    if (!isShiftKeyDown()) {
                        for (i = 0; i < choices.length && choices[i] <= val; ++i) {}
                        if (i == choices.length) i = 0;
                    } else {
                        for (i = choices.length - 1; i >= 0 && val <= choices[i]; --i) {}
                        if (i < 0) i = choices.length - 1;
                    }
                    Shaders.configShadowResMul = choices[i];
                    par1GuiButton.displayString = "ShadowResMul: " + String.format("%.4f", Shaders.configShadowResMul);
                    Shaders.scheduleResizeShadow();
                    break;
                }

                case 17: /* Normal Map */ {
                    Shaders.configNormalMap = !Shaders.configNormalMap;
                    // displayString
                    par1GuiButton.displayString = "NormapMap: " + toStringOnOff(Shaders.configNormalMap);
                    mc.scheduleResourcesRefresh(); // schedule refresh texture
                    break;
                }

                case 18: /* Normal Map */ {
                    Shaders.configSpecularMap = !Shaders.configSpecularMap;
                    // displayString
                    par1GuiButton.displayString = "SpecularMap: " + toStringOnOff(Shaders.configSpecularMap);
                    mc.scheduleResourcesRefresh(); // schedule refresh texture
                    break;
                }

                case 19: /* old Lighting */ {
                    Shaders.configOldLighting = !Shaders.configOldLighting;
                    // displayString
                    par1GuiButton.displayString = "OldLighting: " + toStringOnOff(Shaders.configOldLighting);
                    Shaders.updateBlockLightLevel();
                    mc.renderGlobal.loadRenderers();
                    break;
                }

                case 11: /* texture filter */ {
                    Shaders.configTexMinFilB = (Shaders.configTexMinFilB + 1) % Shaders.texMinFilRange;
                    Shaders.configTexMinFilN = Shaders.configTexMinFilS = Shaders.configTexMinFilB;
                    par1GuiButton.displayString = "Tex Min: " + Shaders.texMinFilDesc[Shaders.configTexMinFilB];
                    ShadersTex.updateTextureMinMagFilter();
                    break;
                }

                case 12: /* texture filter */ {
                    Shaders.configTexMagFilN = (Shaders.configTexMagFilN + 1) % Shaders.texMagFilRange;
                    par1GuiButton.displayString = "Tex_n Mag: " + Shaders.texMagFilDesc[Shaders.configTexMagFilN];
                    ShadersTex.updateTextureMinMagFilter();
                    break;
                }

                case 13: /* texture filter */ {
                    Shaders.configTexMagFilS = (Shaders.configTexMagFilS + 1) % Shaders.texMagFilRange;
                    par1GuiButton.displayString = "Tex_s Mag: " + Shaders.texMagFilDesc[Shaders.configTexMagFilS];
                    ShadersTex.updateTextureMinMagFilter();
                    break;
                }

                case 14: /* shadow frustum clipping */ {
                    Shaders.configShadowClipFrustrum = !Shaders.configShadowClipFrustrum;
                    par1GuiButton.displayString = "ShadowClipFrustrum: "
                            + toStringOnOff(Shaders.configShadowClipFrustrum);
                    ShadersTex.updateTextureMinMagFilter();
                    break;
                }

                case 5: /* Open shaderpacks folder */
                    switch (net.minecraft.util.Util.getOSType()) {
                        case OSX: {
                            try {
                                Runtime.getRuntime().exec(
                                        new String[] { "/usr/bin/open", Shaders.shaderpacksdir.getAbsolutePath() });
                                return;
                            } catch (IOException var7) {
                                var7.printStackTrace();
                            }
                        }
                            break;
                        case WINDOWS: {
                            String var2 = String.format(
                                    "cmd.exe /C start \"Open file\" \"%s\"",
                                    new Object[] { Shaders.shaderpacksdir.getAbsolutePath() });

                            try {
                                Runtime.getRuntime().exec(var2);
                                return;
                            } catch (IOException var6) {
                                var6.printStackTrace();
                            }
                        }
                            break;
                        default:
                            break;
                    }
                    boolean var8 = false;

                    try {
                        Class<?> var3 = Class.forName("java.awt.Desktop");
                        Object var4 = var3.getMethod("getDesktop", new Class[0]).invoke((Object) null, new Object[0]);
                        var3.getMethod("browse", new Class[] { URI.class }).invoke(
                                var4,
                                new Object[] { (new File(mc.mcDataDir, Shaders.shaderpacksdirname)).toURI() });
                    } catch (Throwable var5) {
                        var5.printStackTrace();
                        var8 = true;
                    }

                    if (var8) {
                        AngelicaTweaker.LOGGER.debug("Opening via system class!");
                        Sys.openURL("file://" + Shaders.shaderpacksdir.getAbsolutePath());
                    }
                    break;

                case 6: /* Done */
                    try {
                        Shaders.storeConfig();
                    } catch (Exception ex) {}
                    if (needReinit) {
                        needReinit = false;
                        Shaders.loadShaderPack();
                        Shaders.uninit();
                        this.mc.renderGlobal.loadRenderers();
                    }
                    this.mc.displayGuiScreen(this.parentGui); // displayGuiScreen
                    break;

                default:
                    this.shaderList.actionPerformed(par1GuiButton); // actionPerformed
            }
        }
    }

    @Override
    /**
     * Draws the screen and all the components in it.
     */
    public void drawScreen(int par1, int par2, float par3) {
        drawDefaultBackground(); // background
        this.shaderList.drawScreen(par1, par2, par3); // drawScreen

        if (this.updateTimer <= 0) {
            this.shaderList.updateList();
            this.updateTimer += 20;
        }

        this.drawCenteredString(this.fontRendererObj, "Shaders ", this.width / 2, 16, 0xffffff);
        this.drawCenteredString(this.fontRendererObj, " v" + Shaders.versionString, this.width - 40, 10, 0x808080);
        // this.drawCenteredString(this.fontRenderer, "( Place zipped Shader files here. )", this.width / 4 + 10,
        // this.height - 26, 0x808080);
        super.drawScreen(par1, par2, par3);
    }

    @Override
    /**
     * Called from the main game loop to update the screen.
     */
    public void updateScreen() {
        super.updateScreen();
        --this.updateTimer;
    }

    public Minecraft getMc() {
        return this.mc;
    }

    public void drawCenteredString(String par1, int par2, int par3, int par4) {
        this.drawCenteredString(this.fontRendererObj, par1, par2, par3, par4);
    }
}
