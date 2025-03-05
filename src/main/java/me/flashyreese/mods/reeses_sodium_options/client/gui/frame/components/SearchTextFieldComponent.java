package me.flashyreese.mods.reeses_sodium_options.client.gui.frame.components;

import static com.gtnewhorizons.angelica.glsm.GLStateManager.glDisable;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glEnable;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glLogicOp;

import lombok.Getter;
import lombok.Setter;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import me.flashyreese.mods.reeses_sodium_options.client.gui.OptionExtended;
import me.flashyreese.mods.reeses_sodium_options.client.gui.ReeseSodiumVideoOptionsScreen;
import me.flashyreese.mods.reeses_sodium_options.util.StringUtils;
import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import me.jellysquid.mods.sodium.client.gui.widgets.AbstractWidget;
import me.jellysquid.mods.sodium.client.util.Dim2i;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

@Lwjgl3Aware
public class SearchTextFieldComponent extends AbstractWidget {
    protected final Dim2i dim;
    protected final List<OptionPage> pages;
    private final FontRenderer textRenderer = Minecraft.getMinecraft().fontRenderer;
    private final Predicate<String> textPredicate = Objects::nonNull;
    private final AtomicReference<String> tabFrameSelectedTab;
    private final AtomicReference<Integer> tabFrameScrollBarOffset;
    private final AtomicReference<Integer> optionPageScrollBarOffset;
    private final int tabDimHeight;
    private final ReeseSodiumVideoOptionsScreen sodiumVideoOptionsScreen;
    private final AtomicReference<String> lastSearch;
    private final AtomicReference<Integer> lastSearchIndex;
    protected boolean selecting;
    protected String text = "";
    protected int maxLength = 100;
    @Getter
    protected boolean visible = true;
    @Getter
    protected boolean editable = true;
    private int firstCharacterIndex;
    private int selectionStart;
    private int selectionEnd;
    @Setter
    @Getter
    private boolean focused;
    private int lastCursorPosition = this.getCursor();

    public SearchTextFieldComponent(Dim2i dim, List<OptionPage> pages, AtomicReference<String> tabFrameSelectedTab, AtomicReference<Integer> tabFrameScrollBarOffset, AtomicReference<Integer> optionPageScrollBarOffset, int tabDimHeight, ReeseSodiumVideoOptionsScreen sodiumVideoOptionsScreen, AtomicReference<String> lastSearch, AtomicReference<Integer> lastSearchIndex) {
        this.dim = dim;
        this.pages = pages;
        this.tabFrameSelectedTab = tabFrameSelectedTab;
        this.tabFrameScrollBarOffset = tabFrameScrollBarOffset;
        this.optionPageScrollBarOffset = optionPageScrollBarOffset;
        this.tabDimHeight = tabDimHeight;
        this.sodiumVideoOptionsScreen = sodiumVideoOptionsScreen;
        this.lastSearch = lastSearch;
        this.lastSearchIndex = lastSearchIndex;
        if (!lastSearch.get().trim().isEmpty()) {
            this.write(lastSearch.get());
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        if (!this.isVisible()) {
            return;
        }
        if (!this.isFocused() && this.text.isEmpty()) {
            final String key = "rso.search_bar_empty";
            String text = I18n.format(key);
            if (text.equals(key))
                text = "Search options...";
            this.textRenderer.drawString(text, this.dim.getOriginX() + 6, this.dim.getOriginY() + 6, 0xFFAAAAAA);
        }

        drawRect(this.dim.getOriginX(), this.dim.getOriginY(), this.dim.getLimitX(), this.dim.getLimitY(), this.isFocused() ? 0xE0000000 : 0x90000000);
        final int j = this.selectionStart - this.firstCharacterIndex;
        int k = this.selectionEnd - this.firstCharacterIndex;
        final String string = this.textRenderer.trimStringToWidth(this.text.substring(this.firstCharacterIndex), this.getInnerWidth());
        final boolean bl = j >= 0 && j <= string.length();
        final int l = this.dim.getOriginX() + 6;
        final int m = this.dim.getOriginY() + 6;
        int n = l;
        if (k > string.length()) {
            k = string.length();
        }
        if (!string.isEmpty()) {
            final String string2 = bl ? string.substring(0, j) : string;
            n = this.textRenderer.drawStringWithShadow(string2, n, m, 0xE0E0E0);
        }
        final boolean bl3 = this.selectionStart < this.text.length() || this.text.length() >= this.getMaxLength();
        int o = n;
        if (!bl) {
            o = j > 0 ? l + this.dim.getWidth() - 12 : l;
        } else if (bl3) {
            --o;
            --n;
        }
        if (!string.isEmpty() && bl && j < string.length()) {
            this.textRenderer.drawStringWithShadow(string.substring(j), n, m, 0xE0E0E0);
        }
        // Cursor
        if (this.isFocused()) {
            drawRect(o, m - 1, o + 1, m + 1 + this.textRenderer.FONT_HEIGHT, -0x2F2F30);
        }
        // Highlighted text
        if (k != j) {
            final int p = l + this.textRenderer.getStringWidth(string.substring(0, k));
            this.drawSelectionHighlight(o, m - 1, p - 1, m + 1 + this.textRenderer.FONT_HEIGHT);
        }
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        final int i = MathHelper.floor_double(mouseX) - this.dim.getOriginX() - 6;
        final String string = this.textRenderer.trimStringToWidth(this.text.substring(this.firstCharacterIndex), this.getInnerWidth());
        this.setCursor(this.textRenderer.trimStringToWidth(string, i).length() + this.firstCharacterIndex);

        this.setFocused(this.dim.containsCursor(mouseX, mouseY));
        this.pages.forEach(page -> page
            .getOptions()
            .stream()
            .filter(OptionExtended.class::isInstance)
            .map(OptionExtended.class::cast)
            .forEach(optionExtended -> optionExtended.setSelected(false)));
        return this.isFocused();
    }

    private void drawSelectionHighlight(int x1, int y1, int x2, int y2) {
        int i;
        if (x1 < x2) {
            i = x1;
            x1 = x2;
            x2 = i;
        }
        if (y1 < y2) {
            i = y1;
            y1 = y2;
            y2 = i;
        }
        if (x2 > this.dim.getOriginX() + this.dim.getWidth()) {
            x2 = this.dim.getOriginX() + this.dim.getWidth();
        }
        if (x1 > this.dim.getOriginX() + this.dim.getWidth()) {
            x1 = this.dim.getOriginX() + this.dim.getWidth();
        }
        glEnable(GL11.GL_COLOR_LOGIC_OP);
        glLogicOp(GL11.GL_OR_REVERSE);

        drawRect(x1, y1, x2, y2, -0xFFFF01);

        glDisable(GL11.GL_COLOR_LOGIC_OP);

    }

    private int getMaxLength() {
        return this.maxLength;
    }

    public String getSelectedText() {
        final int i = Math.min(this.selectionStart, this.selectionEnd);
        final int j = Math.max(this.selectionStart, this.selectionEnd);
        return this.text.substring(i, j);
    }

    public void write(String text) {


        final int i = Math.min(this.selectionStart, this.selectionEnd);
        final int j = Math.max(this.selectionStart, this.selectionEnd);
        final int k = this.maxLength - this.text.length() - (i - j);
        String string = text; //SharedConstants.stripInvalidChars(text);
        int l = string.length();
        if (k < l) {
            string = string.substring(0, k);
            l = k;
        }

        final String string2 = (new StringBuilder(this.text)).replace(i, j, string).toString();
        if (this.textPredicate.test(string2)) {
            this.text = string2;
            this.setSelectionStart(i + l);
            this.setSelectionEnd(this.selectionStart);
            this.onChanged(this.text);
        }
    }

    private void onChanged(String newText) {
        this.pages.forEach(page -> page
            .getOptions()
            .stream()
            .filter(OptionExtended.class::isInstance)
            .map(OptionExtended.class::cast)
            .forEach(optionExtended -> optionExtended.setHighlight(false)));

        this.lastSearch.set(newText.trim());
        if (this.editable && (!newText.trim().isEmpty())) {
            final List<Option<?>> fuzzy = StringUtils.fuzzySearch(this.pages, newText, 2);
            fuzzy.stream()
                .filter(OptionExtended.class::isInstance)
                .map(OptionExtended.class::cast)
                .forEach(optionExtended -> optionExtended.setHighlight(true));

        }
    }

    private void erase(int offset) {
        if (GuiScreen.isCtrlKeyDown()) {
            this.eraseWords(offset);
        } else {
            this.eraseCharacters(offset);
        }

    }

    public void eraseWords(int wordOffset) {
        if (!this.text.isEmpty()) {
            if (this.selectionEnd != this.selectionStart) {
                this.write("");
            } else {
                this.eraseCharacters(this.getWordSkipPosition(wordOffset) - this.selectionStart);
            }
        }
    }

    public void eraseCharacters(int characterOffset) {
        if (!this.text.isEmpty()) {
            if (this.selectionEnd != this.selectionStart) {
                this.write("");
            } else {
                final int i = this.getCursorPosWithOffset(characterOffset);
                final int j = Math.min(i, this.selectionStart);
                final int k = Math.max(i, this.selectionStart);
                if (j != k) {
                    final String string = (new StringBuilder(this.text)).delete(j, k).toString();
                    if (this.textPredicate.test(string)) {
                        this.text = string;
                        this.setCursor(j);
                        this.onChanged(this.text);
                    }
                }
            }
        }
    }

    public int getWordSkipPosition(int wordOffset) {
        return this.getWordSkipPosition(wordOffset, this.getCursor());
    }

    private int getWordSkipPosition(int wordOffset, int cursorPosition) {
        return this.getWordSkipPosition(wordOffset, cursorPosition, true);
    }

    private int getWordSkipPosition(int wordOffset, int cursorPosition, boolean skipOverSpaces) {
        int i = cursorPosition;
        final boolean bl = wordOffset < 0;
        final int j = Math.abs(wordOffset);

        for (int k = 0; k < j; ++k) {
            if (!bl) {
                final int l = this.text.length();
                i = this.text.indexOf(32, i);
                if (i == -1) {
                    i = l;
                } else {
                    while (skipOverSpaces && i < l && this.text.charAt(i) == ' ') {
                        ++i;
                    }
                }
            } else {
                while (skipOverSpaces && i > 0 && this.text.charAt(i - 1) == ' ') {
                    --i;
                }

                while (i > 0 && this.text.charAt(i - 1) != ' ') {
                    --i;
                }
            }
        }

        return i;
    }

    public int getCursor() {
        return this.selectionStart;
    }

    public void setCursor(int cursor) {
        this.setSelectionStart(cursor);
        if (!this.selecting) {
            this.setSelectionEnd(this.selectionStart);
        }

        this.onChanged(this.text);
    }

    public void moveCursor(int offset) {
        this.setCursor(this.getCursorPosWithOffset(offset));
    }

    private int getCursorPosWithOffset(int offset) {
        //return Util.moveCursor(this.text, this.selectionStart, offset);
        return this.selectionStart + offset;
    }

    public void setSelectionStart(int cursor) {
        this.selectionStart = MathHelper.clamp_int(cursor, 0, this.text.length());
    }

    public void setCursorToStart() {
        this.setCursor(0);
    }

    public void setCursorToEnd() {
        this.setCursor(this.text.length());
    }

    public void setSelectionEnd(int index) {
        final int i = this.text.length();
        this.selectionEnd = MathHelper.clamp_int(index, 0, i);
        if (this.textRenderer != null) {
            if (this.firstCharacterIndex > i) {
                this.firstCharacterIndex = i;
            }

            final int j = this.getInnerWidth();
            final String string = this.textRenderer.trimStringToWidth(this.text.substring(this.firstCharacterIndex), j);
            final int k = string.length() + this.firstCharacterIndex;
            if (this.selectionEnd == this.firstCharacterIndex) {
                this.firstCharacterIndex -= this.textRenderer.trimStringToWidth(this.text, j, true).length();
            }

            if (this.selectionEnd > k) {
                this.firstCharacterIndex += this.selectionEnd - k;
            } else if (this.selectionEnd <= this.firstCharacterIndex) {
                this.firstCharacterIndex -= this.firstCharacterIndex - this.selectionEnd;
            }

            this.firstCharacterIndex = MathHelper.clamp_int(this.firstCharacterIndex, 0, i);
        }
    }

    public boolean isActive() {
        return this.isVisible() && this.isFocused() && this.isEditable();
    }

//    @Override
//    public boolean charTyped(char chr, int modifiers) {
//        if (!this.isActive()) {
//            return false;
//        }
//        if (SharedConstants.isValidChar(chr)) {
//            if (this.editable) {
//                this.lastSearch.set(this.text.trim());
//                this.write(Character.toString(chr));
//                this.lastSearchIndex.set(0);
//            }
//            return true;
//        }
//        return false;
//    }
//
    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        this.pages.forEach(page2 -> page2
            .getOptions()
            .stream()
            .filter(OptionExtended.class::isInstance)
            .map(OptionExtended.class::cast)
            .forEach(optionExtended2 -> optionExtended2.setSelected(false)));
        if (!this.isActive()) {
            return false;
        } else {
            this.selecting = GuiScreen.isShiftKeyDown();
            if (keyCode == Keyboard.KEY_A && GuiScreen.isCtrlKeyDown() && !GuiScreen.isShiftKeyDown() ) {
                // Select all
                this.setCursorToEnd();
                this.setSelectionEnd(0);
                return true;
            } else if (keyCode == Keyboard.KEY_C && GuiScreen.isCtrlKeyDown() && !GuiScreen.isShiftKeyDown() ) {
                // Copy
                GuiScreen.setClipboardString(this.getSelectedText());
                return true;
            } else if (keyCode == Keyboard.KEY_V && GuiScreen.isCtrlKeyDown() && !GuiScreen.isShiftKeyDown() ) {
                // Paste
                if (this.editable) {
                    this.write(GuiScreen.getClipboardString());
                }

                return true;
            } else if (keyCode == Keyboard.KEY_X && GuiScreen.isCtrlKeyDown() && !GuiScreen.isShiftKeyDown() ) {
                // Cut
                GuiScreen.setClipboardString(this.getSelectedText());
                if (this.editable) {
                    this.write("");
                }

                return true;
            } else {
                switch (keyCode) {
                    case Keyboard.KEY_RETURN: // GLFW.GLFW_KEY_ENTER
                        if (this.editable) {
                            int count = 0;
                            for (OptionPage page : this.pages) {
                                for (Option<?> option : page.getOptions()) {
                                    if(option instanceof OptionExtended<?> optionExtended) {
                                        if (optionExtended.isHighlight() && optionExtended.getParentDimension() != null) {
                                            if (count == this.lastSearchIndex.get()) {
                                                final Dim2i optionDim = optionExtended.getDim2i();
                                                final Dim2i parentDim = optionExtended.getParentDimension();
                                                final int maxOffset = parentDim.getHeight() - this.tabDimHeight;
                                                final int input = optionDim.getOriginY() - parentDim.getOriginY();
                                                final int inputOffset = input + optionDim.getHeight() == parentDim.getHeight() ? parentDim.getHeight() : input;
                                                final int offset = inputOffset * maxOffset / parentDim.getHeight();

                                                int total = this.pages.stream().mapToInt(page2 -> Math.toIntExact(page2.getOptions().stream().filter(OptionExtended.class::isInstance).map(OptionExtended.class::cast).filter(OptionExtended::isHighlight).count())).sum();

                                                final int value = total == this.lastSearchIndex.get() + 1 ? 0 : this.lastSearchIndex.get() + 1;
                                                optionExtended.setSelected(true);
                                                this.lastSearchIndex.set(value);
                                                this.tabFrameSelectedTab.set(page.getName());
                                                // todo: calculate tab frame scroll bar offset
                                                this.tabFrameScrollBarOffset.set(0);

                                                this.optionPageScrollBarOffset.set(offset);
                                                this.setFocused(false);
                                                this.sodiumVideoOptionsScreen.rebuildGUI();
                                                return true;
                                            }
                                            count++;
                                        }
                                    }
                                }
                            }
                        }
                        return true;
                    case Keyboard.KEY_BACK: // GLFW.GLFW_KEY_BACKSPACE
                        if (this.editable) {
                            this.selecting = false;
                            this.erase(-1);
                            this.selecting = GuiScreen.isShiftKeyDown();
                        }
                        return true;
                    case Keyboard.KEY_DELETE: // GLFW.GLFW_KEY_DELETE
                        if (this.editable) {
                            this.selecting = false;
                            this.erase(1);
                            this.selecting = GuiScreen.isShiftKeyDown();
                        }
                        return true;
                    case Keyboard.KEY_RIGHT: // GLFW.GLFW_KEY_RIGHT
                        if (GuiScreen.isCtrlKeyDown()) {
                            this.setCursor(this.getWordSkipPosition(1));
                        } else {
                            this.moveCursor(1);
                        }
                        final boolean state = this.getCursor() != this.lastCursorPosition && this.getCursor() != this.text.length() + 1;
                        this.lastCursorPosition = this.getCursor();
                        return state;
                    case Keyboard.KEY_LEFT: // GLFW.GLFW_KEY_LEFT
                        if (GuiScreen.isCtrlKeyDown()) {
                            this.setCursor(this.getWordSkipPosition(-1));
                        } else {
                            this.moveCursor(-1);
                        }
                        final boolean state2 = this.getCursor() != this.lastCursorPosition && this.getCursor() != 0;
                        this.lastCursorPosition = this.getCursor();
                        return state2;
                    case Keyboard.KEY_HOME: // GLFW.GLFW_KEY_HOME
                        this.setCursorToStart();
                        return true;
                    case Keyboard.KEY_END: // GLFW.GLFW_KEY_END
                        this.setCursorToEnd();
                        return true;
                    default:
                        if (ChatAllowedCharacters.isAllowedCharacter(typedChar)) {
                            if (this.editable) {
                                this.lastSearch.set(this.text.trim());
                                this.write(Character.toString(typedChar));
                                this.lastSearchIndex.set(0);
                            }
                            return true;
                        }
                        return false;
                }
            }
        }
    }

    public int getInnerWidth() {
        return this.dim.getWidth() - 12;
    }
}
