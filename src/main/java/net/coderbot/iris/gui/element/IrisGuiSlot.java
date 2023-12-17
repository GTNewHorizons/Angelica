package net.coderbot.iris.gui.element;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

// TODO: look into GuiListExtended & GuiSelectStringEntries
public abstract class IrisGuiSlot extends GuiSlot {
    @Setter @Getter protected boolean renderBackground = true;

    protected IrisGuiSlot(Minecraft mc, int width, int height, int top, int bottom, int slotHeight) {
        super(mc, width, height, top, bottom, slotHeight);
        // Set Center Vertically to false
        this.field_148163_i = false;

    }

    @Override
    protected void drawContainerBackground(Tessellator tessellator) {
        if(this.renderBackground) {
            super.drawContainerBackground(tessellator);
        }
    }

    @Override
    protected int getScrollBarX() {
        // Position the scrollbar at the rightmost edge of the screen.
        // By default, the scrollbar is positioned moderately offset from the center.
        return this.width - 6;
    }

    @Override
    protected void drawSelectionBox(int x, int y, int mouseX, int mouseY) {
        final int oldPadding = this.headerPadding;
        this.headerPadding = 2;
        super.drawSelectionBox(x, y, mouseX, mouseY);
        this.headerPadding = oldPadding;
    }
    @Override
    protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
        // Do nothing
    }

    protected boolean elementClicked(int index, boolean doubleClick, int mouseX, int mouseY, int button) {
        return false;
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if(!this.func_148125_i/*enabled*/()) {
            return false;
        }
        final int size = this.getSize();
        final int scrollBarX = this.getScrollBarX();
        final int rightEdge = scrollBarX + 6;
        final int elementLeft = this.width / 2 - this.getListWidth() / 2;
        final int elementRight = this.width / 2 + this.getListWidth() / 2;
        final int relativeY = mouseY - this.top - this.headerPadding + (int) this.amountScrolled - 4;
        boolean handled = false;
        final boolean leftMouseDown = Mouse.isButtonDown(0);
        final boolean rightMouseDown = Mouse.isButtonDown(1);
        boolean scrolling = false;

        if (mouseX <= this.left || mouseX >= this.right || mouseY <= this.top || mouseY >= this.bottom) {
            return handled;
        }
        if (leftMouseDown && mouseX >= scrollBarX && mouseX <= rightEdge) {
            // TODO: Handle scrolling
            scrolling = true;
        }

        if ((leftMouseDown || rightMouseDown)) {
            final int index = relativeY / this.slotHeight;

            if (mouseX >= elementLeft && mouseX <= elementRight && index >= 0 && relativeY >= 0 && index < size) {
                final boolean doubleCLick = index == this.selectedElement && Minecraft.getSystemTime() - this.lastClicked < 250L;

                handled = this.elementClicked(index, doubleCLick, mouseX, mouseY, mouseButton);
                this.selectedElement = index;
                this.lastClicked = Minecraft.getSystemTime();
            } else if (mouseX >= elementLeft && mouseX <= elementRight && relativeY < 0) {
                this.func_148132_a(mouseX - elementLeft, mouseY - this.top + (int) this.amountScrolled - 4);
            }
        }

        return handled;
    }

    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.drawBackground();
        final int scrollBarX = this.getScrollBarX();
        final int rightEdge = scrollBarX + 6;
        final byte offset = 4;


        // Scrollwheel nonsense
        for (; !this.mc.gameSettings.touchscreen && Mouse.next(); this.mc.currentScreen.handleMouseInput()) {
            int dWheel = Mouse.getEventDWheel();

            if (dWheel != 0) {
                if (dWheel > 0) {
                    dWheel = -1;
                } else {
                    dWheel = 1;
                }

                this.amountScrolled += (dWheel * this.slotHeight / 2.0f);
            }
        }


        this.bindAmountScrolled();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        final Tessellator tessellator = Tessellator.instance;
        drawContainerBackground(tessellator);
        final int elementRight = this.left + this.width / 2 - this.getListWidth() / 2 + 2;
        final int relativeY = this.top + 4 - (int) this.amountScrolled;

        if (this.hasListHeader) {
            this.drawListHeader(elementRight, relativeY, tessellator);
        }

        this.drawSelectionBox(elementRight, relativeY, mouseX, mouseY);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        this.overlayBackground(0, this.top, 255, 255);
        this.overlayBackground(this.bottom, this.height, 255, 255);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 0, 1);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_I(0, 0);
        tessellator.addVertexWithUV(this.left, (this.top + offset), 0.0D, 0.0D, 1.0D);
        tessellator.addVertexWithUV(this.right, (this.top + offset), 0.0D, 1.0D, 1.0D);
        tessellator.setColorRGBA_I(0, 255);
        tessellator.addVertexWithUV(this.right, this.top, 0.0D, 1.0D, 0.0D);
        tessellator.addVertexWithUV(this.left, this.top, 0.0D, 0.0D, 0.0D);
        tessellator.draw();
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_I(0, 255);
        tessellator.addVertexWithUV(this.left, this.bottom, 0.0D, 0.0D, 1.0D);
        tessellator.addVertexWithUV(this.right, this.bottom, 0.0D, 1.0D, 1.0D);
        tessellator.setColorRGBA_I(0, 0);
        tessellator.addVertexWithUV(this.right, (this.bottom - offset), 0.0D, 1.0D, 0.0D);
        tessellator.addVertexWithUV(this.left, (this.bottom - offset), 0.0D, 0.0D, 0.0D);
        tessellator.draw();

        // Draw scrollbar if needed
        final int contentOverflow = this.func_148135_f();
        if (contentOverflow > 0) {
            registerScrollButtons(7, 8);
            int scrollPosSize = (this.bottom - this.top) * (this.bottom - this.top) / this.getContentHeight();

            if (scrollPosSize < 32) {
                scrollPosSize = 32;
            }

            if (scrollPosSize > this.bottom - this.top - 8) {
                scrollPosSize = this.bottom - this.top - 8;
            }

            int scrollPos = (int) this.amountScrolled * (this.bottom - this.top - scrollPosSize) / contentOverflow + this.top;

            if (scrollPos < this.top) {
                scrollPos = this.top;
            }

            tessellator.startDrawingQuads();
            tessellator.setColorRGBA_I(0, 255);
            tessellator.addVertexWithUV(scrollBarX, this.bottom, 0.0D, 0.0D, 1.0D);
            tessellator.addVertexWithUV(rightEdge, this.bottom, 0.0D, 1.0D, 1.0D);
            tessellator.addVertexWithUV(rightEdge, this.top, 0.0D, 1.0D, 0.0D);
            tessellator.addVertexWithUV(scrollBarX, this.top, 0.0D, 0.0D, 0.0D);
            tessellator.draw();
            tessellator.startDrawingQuads();
            tessellator.setColorRGBA_I(8421504, 255);
            tessellator.addVertexWithUV(scrollBarX, (scrollPos + scrollPosSize), 0.0D, 0.0D, 1.0D);
            tessellator.addVertexWithUV(rightEdge, (scrollPos + scrollPosSize), 0.0D, 1.0D, 1.0D);
            tessellator.addVertexWithUV(rightEdge, scrollPos, 0.0D, 1.0D, 0.0D);
            tessellator.addVertexWithUV(scrollBarX, scrollPos, 0.0D, 0.0D, 0.0D);
            tessellator.draw();
            tessellator.startDrawingQuads();
            tessellator.setColorRGBA_I(12632256, 255);
            tessellator.addVertexWithUV(scrollBarX, (scrollPos + scrollPosSize - 1), 0.0D, 0.0D, 1.0D);
            tessellator.addVertexWithUV((rightEdge - 1), (scrollPos + scrollPosSize - 1), 0.0D, 1.0D, 1.0D);
            tessellator.addVertexWithUV((rightEdge - 1), scrollPos, 0.0D, 1.0D, 0.0D);
            tessellator.addVertexWithUV(scrollBarX, scrollPos, 0.0D, 0.0D, 0.0D);
            tessellator.draw();
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_BLEND);
    }
}
