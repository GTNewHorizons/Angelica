package com.gtnewhorizons.angelica.debug.screen;

import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.gtnewhorizons.angelica.api.debug.DebugEntryCategory;
import com.gtnewhorizons.angelica.api.debug.DebugScreenEntryStatus;
import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.mixins.interfaces.FontRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class DebugOptionsScreen extends GuiScreen {

    private static final int ROW_H = 20;
    private static final int TOOLTIP_WRAP_W = 200;
    private static final int ROW_BUTTON_W = 60;
    private static final int ROW_BUTTON_H = 16;
    private static final int ROW_BUTTON_GAP = 2;
    private static final int ARROW_W = 20;
    private static final int LIST_MAX_W = 350;
    private static final int SCROLLBAR_W = 6;
    private static final int SCROLLBAR_MIN_THUMB = 10;
    private static final int HEADER_H = 28;
    private static final int HEADER_LINE_H = 10;
    private static final int FOOTER_W = 120;
    private static final int FOOTER_H = 20;
    private static final int FOOTER_GAP = 4;
    private static final int WHEEL_ROWS = 3;
    private static final int TOOLTIP_OFFSET = 12;

    private static final ResourceLocation BUTTON_PRESS = new ResourceLocation("gui.button.press");
    private static final Comparator<DebugEntryDef> BY_ID = Comparator.comparing(d -> d.id);
    private static final Comparator<DebugGroup> GROUPS_BY_ID = Comparator.comparing(g -> g.id);

    private enum RowKind { HEADER, ENTRY, SCALE, PANEL }

    private static final int NORMAL_COLOR = 0xFFFFFF;
    private static final int WARNING_COLOR = 0xFF5555;
    private static final int INACTIVE_COLOR = 0x909090;
    private static final int NOTICE_COLOR = 0xFFFF55;
    private static final int DISABLED_COLOR = 0x505050;
    private static final int BUTTON_COLOR = DebugGroupContents.BODY_COLOR;
    private static final int BUTTON_LIT_COLOR = DebugGroupContents.TITLE_BAR_COLOR;
    private static final int OUTLINE_COLOR = 0xFFA0A0A0;
    private static final int PLACEHOLDER_COLOR = 0x707070;
    private static final int FIELD_BOX_COLOR = 0xFF000000;
    private static final int FIELD_TEXT_COLOR = 0xE0E0E0;
    private static final int FIELD_CURSOR_COLOR = 0xFFD0D0D0;
    private static final int FIELD_SELECTION_COLOR = 0xFF0000FF;
    private static final int TOOLTIP_BACKGROUND = 0xF0100010;
    private static final int TOOLTIP_BORDER = 0x505000FF;
    private static final int TOOLTIP_TEXT_COLOR = 0xFFFFFFFF;

    private record Choice(DebugScreenEntryStatus status, String labelKey, int activeColor) {}

    private static final Choice[] CHOICES = {
        new Choice(DebugScreenEntryStatus.NEVER, "options.off", 0xFFFFFF),
        new Choice(DebugScreenEntryStatus.IN_OVERLAY, "angelica.debug.options.status.overlay", 0xFFFF55),
        new Choice(DebugScreenEntryStatus.ALWAYS_ON, "angelica.debug.options.status.always", 0xDF5050)
    };
    private static final int ROW_BUTTONS_W = ROW_BUTTON_W * CHOICES.length + ROW_BUTTON_GAP * (CHOICES.length - 1);

    private final class Button {
        final int id;
        final int w;
        final int h;
        final String label;
        final int labelW;
        int x;
        int y;
        boolean enabled = true;
        boolean selected;
        boolean pressable = true;
        int activeColor;

        Button(int id, int w, int h, String label) {
            this.id = id;
            this.w = w;
            this.h = h;
            this.label = label;
            this.labelW = fontRendererObj.getStringWidth(label);
        }

        void setSelected(boolean selected, boolean pressable, int activeColor) {
            this.selected = selected;
            this.pressable = pressable;
            this.activeColor = activeColor;
        }

        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
        }

        boolean pressed(int mouseX, int mouseY) {
            return enabled && pressable && contains(mouseX, mouseY);
        }

        void draw(boolean hovered) {
            rect(x, y, x + w, y + h, selected || hovered && enabled ? BUTTON_LIT_COLOR : BUTTON_COLOR);
            if (selected) {
                rect(x, y, x + w, y + 1, OUTLINE_COLOR);
                rect(x, y + h - 1, x + w, y + h, OUTLINE_COLOR);
                rect(x, y + 1, x + 1, y + h - 1, OUTLINE_COLOR);
                rect(x + w - 1, y + 1, x + w, y + h - 1, OUTLINE_COLOR);
            }
            drawText(label, x + (w - labelW) / 2, y + (h - 8) / 2, !enabled ? DISABLED_COLOR : selected ? activeColor : INACTIVE_COLOR);
        }
    }

    private final class SearchField extends GuiTextField {
        private int blink;
        private String measuredText;
        private int measuredScroll = -1;
        private int measuredCursor = -1;
        private int measuredSelection = -1;
        private String shown;
        private int cursor;
        private int selection;
        private boolean cursorShown;
        private int prefixW;
        private int selectionW;
        private boolean cursorPending;
        private boolean cursorInsert;
        private int pendingX;
        private int pendingY;

        SearchField(int x, int y, int w, int h) {
            super(fontRendererObj, x, y, w, h);
        }

        @Override
        public void updateCursorCounter() {
            blink++;
        }

        @Override
        public void setFocused(boolean focused) {
            if (focused && !isFocused()) blink = 0;
            super.setFocused(focused);
        }

        private void measure(String text) {
            measuredText = text;
            measuredScroll = lineScrollOffset;
            measuredCursor = getCursorPosition();
            measuredSelection = getSelectionEnd();
            shown = fontRendererObj.trimStringToWidth(text.substring(lineScrollOffset), getWidth());
            cursor = measuredCursor - lineScrollOffset;
            selection = Math.min(measuredSelection - lineScrollOffset, shown.length());
            cursorShown = cursor >= 0 && cursor <= shown.length();
            prefixW = fontRendererObj.getStringWidth(cursorShown ? shown.substring(0, cursor) : shown);
            selectionW = fontRendererObj.getStringWidth(shown.substring(0, selection));
        }

        @Override
        public void drawTextBox() {
            cursorPending = false;
            if (!getVisible()) return;
            rect(xPosition - 1, yPosition - 1, xPosition + width + 1, yPosition + height + 1, OUTLINE_COLOR);
            rect(xPosition, yPosition, xPosition + width, yPosition + height, FIELD_BOX_COLOR);
            final String text = getText();
            if (text != measuredText || lineScrollOffset != measuredScroll || getCursorPosition() != measuredCursor || getSelectionEnd() != measuredSelection) measure(text);

            final int textX = xPosition + 4;
            final int textY = yPosition + (height - 8) / 2;
            final int end = shown.isEmpty() ? textX : textX + prefixW + 1;
            final boolean insert = measuredCursor < text.length() || text.length() >= getMaxStringLength();
            int cursorX = end;
            if (!cursorShown) {
                cursorX = cursor > 0 ? textX + width : textX;
            } else if (insert) {
                cursorX = end - 1;
            }
            if (selection != cursor) {
                final int right = xPosition + width;
                final int selectionX = textX + selectionW - 1;
                rect(Math.min(Math.min(cursorX, selectionX), right), textY - 1, Math.min(Math.max(cursorX, selectionX), right), textY + 1 + fontRendererObj.FONT_HEIGHT, FIELD_SELECTION_COLOR);
            }
            if (!shown.isEmpty()) drawText(shown, textX, textY, FIELD_TEXT_COLOR);
            cursorPending = isFocused() && blink / 6 % 2 == 0 && cursorShown;
            cursorInsert = insert;
            pendingX = cursorX;
            pendingY = textY;
        }

        void drawCursor() {
            if (!cursorPending) return;
            cursorPending = false;
            if (cursorInsert) {
                rect(pendingX, pendingY - 1, pendingX + 1, pendingY + 1 + fontRendererObj.FONT_HEIGHT, FIELD_CURSOR_COLOR);
            } else {
                drawText("_", pendingX, pendingY, FIELD_TEXT_COLOR);
            }
        }
    }

    private static final class Row {
        final RowKind kind;
        final DebugEntryDef def;
        final String plain;
        final String normalText;
        final List<String> normalTooltip;
        final Button[] buttons;
        String text;
        int textW;
        List<String> tooltip;
        String value;
        int valueW;
        int color = NORMAL_COLOR;
        boolean failed;

        Row(RowKind kind, DebugEntryDef def, String plain, String normalText, List<String> normalTooltip, Button[] buttons) {
            this.kind = kind;
            this.def = def;
            this.plain = plain;
            this.normalText = normalText;
            this.normalTooltip = normalTooltip;
            this.buttons = buttons;
            this.text = normalText;
            this.tooltip = normalTooltip;
        }
    }

    public static void open(Minecraft mc) {
        if (DebugScreen.ENTRIES == null) return;
        mc.displayGuiScreen(new DebugOptionsScreen());
    }

    private final ArrayList<Row> rows = new ArrayList<>();
    private final ArrayList<DebugEntryDef> thirdParty = new ArrayList<>();
    private final ArrayList<DebugGroup> panels = new ArrayList<>();
    private final Button[] profileButtons = new Button[DebugScreenProfile.VALUES.length];
    private Button doneButton;

    private FontRendererAccessor fonts;
    private BatchingFontRenderer batcher;
    private DebugText.Batched painter;

    private int[] headerLineX;
    private List<String> measuredTooltip;
    private int measuredTooltipW;

    private String search = "";
    private int scrollRow;
    private int visibleRows;
    private boolean dragging;
    private int dragOffset;
    private boolean grouped;
    private long lastVersion;
    private boolean configDirty;
    private int scaleMax;

    private SearchField searchField;

    private String title;
    private String searchPlaceholder;
    private int cursorW;
    private List<String> failedTooltip;
    private List<String> headerLines;
    private int headerColor;

    private int titleX;
    private int listX;
    private int listW;
    private int nameX;
    private int nameMaxW;
    private int buttonsX;
    private int viewTop;
    private int viewH;
    private int footerTop;

    @Override
    public void initGui() {
        if (DebugScreen.ENTRIES == null) {
            mc.displayGuiScreen(null);
            return;
        }
        Keyboard.enableRepeatEvents(true);
        dragging = false;
        if (fontRendererObj instanceof FontRendererAccessor accessor) {
            fonts = accessor;
            batcher = accessor.angelica$getBatcher();
            painter = new DebugText.Batched(fontRendererObj, batcher);
        } else {
            fonts = null;
            batcher = null;
            painter = null;
        }
        measuredTooltip = null;

        title = translate("angelica.debug.options.title");
        searchPlaceholder = translate("angelica.debug.options.search");
        failedTooltip = wrap(translate("angelica.debug.options.failed.tooltip"));

        final boolean even = DebugGuiScale.evenForUnicode(mc);
        final int gameScale = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
        scaleMax = DebugGuiScale.offeredMax(DebugGuiScale.maxScale(mc.displayWidth, mc.displayHeight, even), gameScale);

        listW = Math.min(LIST_MAX_W, width - 20);
        listX = (width - listW) / 2;
        nameX = listX + 4;
        buttonsX = listX + listW - SCROLLBAR_W - 4 - ROW_BUTTONS_W;
        nameMaxW = buttonsX - 4 - nameX;

        final int titleW = fontRendererObj.getStringWidth(title);
        titleX = (width - titleW) / 2;
        final int searchX = titleX + titleW + 8;
        searchField = new SearchField(searchX, 6, Math.max(40, Math.min(120, width - searchX - 8)), 18);
        searchField.setMaxStringLength(32);
        searchField.setText(search);
        searchField.setFocused(true);
        cursorW = fontRendererObj.getStringWidth("_");
        searchPlaceholder = fontRendererObj.trimStringToWidth(searchPlaceholder, searchField.getWidth() - cursorW);

        initFooter();
        layoutView();
        buildRows();
    }

    private void initFooter() {
        final int count = profileButtons.length + 1;
        final int buttonW = Math.min(FOOTER_W, (width - 8 - (count - 1) * FOOTER_GAP) / count);
        final int rowW = count * buttonW + (count - 1) * FOOTER_GAP;
        final int left = (width - rowW) / 2;
        footerTop = height - 6 - FOOTER_H;
        for (int i = 0; i < profileButtons.length; i++) {
            final Button button = new Button(i, buttonW, FOOTER_H, translate(DebugScreenProfile.VALUES[i].translationKey));
            button.x = left + i * (buttonW + FOOTER_GAP);
            button.y = footerTop;
            profileButtons[i] = button;
        }
        doneButton = new Button(profileButtons.length, buttonW, FOOTER_H, translate("gui.done"));
        doneButton.x = left + profileButtons.length * (buttonW + FOOTER_GAP);
        doneButton.y = footerTop;
    }

    private void layoutView() {
        grouped = DebugScreen.isGroupedActive();
        final String header = translate(grouped ? "angelica.debug.options.warning" : "angelica.debug.options.flat_notice");
        headerLines = fontRendererObj.listFormattedStringToWidth(header, Math.min(width - 20, 400));
        headerLineX = new int[headerLines.size()];
        for (int i = 0; i < headerLineX.length; i++) {
            headerLineX[i] = width / 2 - fontRendererObj.getStringWidth(headerLines.get(i)) / 2;
        }
        headerColor = grouped ? WARNING_COLOR : NOTICE_COLOR;
        viewTop = HEADER_H + headerLines.size() * HEADER_LINE_H + 2;
        visibleRows = Math.max(1, (footerTop - 6 - viewTop) / ROW_H);
        viewH = visibleRows * ROW_H;
    }

    private List<String> wrap(String text) {
        return fontRendererObj.listFormattedStringToWidth(text, TOOLTIP_WRAP_W);
    }

    private static boolean isBuiltin(String namespace) {
        return "minecraft".equals(namespace) || "forge".equals(namespace) || "angelica".equals(namespace);
    }

    private Row headerRow(String label) {
        final Row row = new Row(RowKind.HEADER, null, null, EnumChatFormatting.BOLD + "" + EnumChatFormatting.UNDERLINE + label, null, null);
        row.textW = fontRendererObj.getStringWidth(row.text);
        return row;
    }

    private void buildRows() {
        rows.clear();
        final String query = search.toLowerCase(Locale.ROOT);
        final DebugRegistry registry = DebugRegistry.MAIN;
        final DebugEntryCategory[] categories = DebugEntryCategory.values();
        for (int c = 0; c < categories.length; c++) {
            final DebugEntryCategory category = categories[c];
            final int start = rows.size();
            thirdParty.clear();
            for (int i = 0, n = registry.entryCount(); i < n; i++) {
                final DebugEntryDef def = registry.entryAt(i);
                if (def.category != category) continue;
                if (!grouped && category == DebugEntryCategory.SCREEN_TEXT && !def.flat) continue;
                if (def == DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR && !AngelicaConfig.modernizeF3Screen) continue;
                final boolean builtin = isBuiltin(def.namespace);
                if (!query.isEmpty() && !(builtin ? def.path : def.id).contains(query) && !(def.name != null && translate(def.name).toLowerCase(Locale.ROOT).contains(query))) continue;
                if (builtin) {
                    rows.add(entryRow(def, false));
                } else {
                    thirdParty.add(def);
                }
            }
            thirdParty.sort(BY_ID);
            for (int i = 0, n = thirdParty.size(); i < n; i++) {
                rows.add(entryRow(thirdParty.get(i), true));
            }
            if (rows.size() > start) rows.add(start, headerRow(translate(category.translationKey)));
        }
        if (grouped) {
            final int start = rows.size();
            addLayoutRow(RowKind.SCALE, translate("angelica.debug.options.text_size"), wrap(translate("angelica.debug.options.text_size.tooltip")), "<", ">", query);
            if (rows.size() > start) rows.add(start, headerRow(translate("angelica.debug.options.category.layout")));
            addPanelRows(registry, query);
        }
        thirdParty.clear();
        refreshStates();
        scrollTo(scrollRow);
    }

    private void addLayoutRow(RowKind kind, String label, List<String> tooltip, String decrease, String increase, String query) {
        if (!query.isEmpty() && !label.toLowerCase(Locale.ROOT).contains(query)) return;
        final String trimmed = fontRendererObj.trimStringToWidth(label, nameMaxW);
        final List<String> shownTooltip = tooltip != null ? tooltip : trimmed.equals(label) ? null : wrap(label);
        final Button[] buttons = {
            new Button(0, ARROW_W, ROW_BUTTON_H, decrease),
            new Button(1, ARROW_W, ROW_BUTTON_H, increase)
        };
        final Row row = new Row(kind, null, label, trimmed, shownTooltip, buttons);
        refreshLayoutValue(row);
        rows.add(row);
    }

    private void addPanelRows(DebugRegistry registry, String query) {
        for (int i = 0, n = registry.groupCount(); i < n; i++) panels.add(registry.groupAt(i));
        panels.sort(GROUPS_BY_ID);
        final int start = rows.size();
        for (int i = 0, n = panels.size(); i < n; i++) {
            final DebugGroup group = panels.get(i);
            final String title = group.hasTitle() ? translate(group.titleKey) : group.id;
            if (!query.isEmpty() && !group.id.contains(query) && !title.toLowerCase(Locale.ROOT).contains(query)) continue;
            final List<String> tooltip = wrap(StatCollector.translateToLocalFormatted("angelica.debug.options.panels.tooltip", group.id));
            final Row row = new Row(RowKind.PANEL, null, title, fontRendererObj.trimStringToWidth(title, nameMaxW), tooltip, null);
            row.value = fontRendererObj.trimStringToWidth(group.id, ROW_BUTTONS_W);
            row.valueW = fontRendererObj.getStringWidth(row.value);
            rows.add(row);
        }
        panels.clear();
        if (rows.size() > start) rows.add(start, headerRow(translate("angelica.debug.options.category.panels")));
    }

    private Row entryRow(DebugEntryDef def, boolean external) {
        final boolean named = def.name != null;
        final String full = named ? translate(def.name) : external ? def.id : def.path;
        final String trimmed = fontRendererObj.trimStringToWidth(full, nameMaxW);
        final String normalText;
        if (external && !named) {
            final int split = Math.min(trimmed.length(), def.namespace.length() + 1);
            normalText = EnumChatFormatting.GRAY + trimmed.substring(0, split) + EnumChatFormatting.WHITE + trimmed.substring(split);
        } else {
            normalText = trimmed;
        }
        final List<String> tooltip;
        final String helpKey = named ? def.name + ".tooltip" : null;
        if (helpKey != null && StatCollector.canTranslate(helpKey)) {
            final List<String> lines = new ArrayList<>(wrap(translate(helpKey)));
            lines.addAll(wrap(EnumChatFormatting.DARK_GRAY + def.id));
            tooltip = lines;
        } else {
            tooltip = !named && trimmed.equals(full) ? null : wrap(def.id);
        }
        final boolean always = def.supportsAlways && (grouped || def.category == DebugEntryCategory.RENDERER);
        final Button[] buttons = new Button[always ? CHOICES.length : CHOICES.length - 1];
        for (int i = 0; i < buttons.length; i++) {
            buttons[i] = new Button(i, ROW_BUTTON_W, ROW_BUTTON_H, translate(CHOICES[i].labelKey));
        }
        return new Row(RowKind.ENTRY, def, trimmed, normalText, tooltip, buttons);
    }

    private void refreshStates() {
        final DebugScreenEntryList entries = DebugScreen.ENTRIES;
        lastVersion = entries.version();
        for (int i = 0, n = rows.size(); i < n; i++) {
            final Row row = rows.get(i);
            if (row.kind != RowKind.ENTRY) continue;
            final boolean failed = row.def.isFailed();
            if (failed != row.failed) {
                row.failed = failed;
                if (failed) {
                    row.text = EnumChatFormatting.STRIKETHROUGH + row.plain;
                    row.tooltip = failedTooltip;
                    row.color = WARNING_COLOR;
                } else {
                    row.text = row.normalText;
                    row.tooltip = row.normalTooltip;
                    row.color = NORMAL_COLOR;
                }
            }
            final DebugScreenEntryStatus current = entries.effectiveStatus(row.def);
            final DebugScreenEntryStatus stored = entries.getStatus(row.def);
            final DebugScreenEntryStatus shown = current == DebugScreenEntryStatus.ALWAYS_ON && row.buttons.length < CHOICES.length ? DebugScreenEntryStatus.IN_OVERLAY : current;
            for (final Button button : row.buttons) {
                final Choice choice = CHOICES[button.id];
                button.enabled = !failed;
                button.setSelected(choice.status == shown, choice.status != shown || choice.status != stored, choice.activeColor);
            }
        }
        for (int i = 0; i < profileButtons.length; i++) {
            final boolean using = entries.isUsingProfile(DebugScreenProfile.VALUES[i]);
            profileButtons[i].setSelected(using, !using, NORMAL_COLOR);
        }
    }

    private void refreshLayoutValue(Row row) {
        switch (row.kind) {
            case SCALE -> {
                final int scale = Math.min(AngelicaConfig.debugGuiScale, scaleMax);
                row.value = scale <= DebugGuiScale.UNCHANGED ? translate("angelica.debug.options.text_size.match")
                    : scale == DebugGuiScale.AUTO ? translate("angelica.debug.options.text_size.auto") : Integer.toString(scale);
            }
            default -> {}
        }
        if (row.value != null) row.valueW = fontRendererObj.getStringWidth(row.value);
    }

    private void stepLayout(Row row, int direction) {
        switch (row.kind) {
            case SCALE -> {
                int next = Math.max(DebugGuiScale.UNCHANGED, Math.min(AngelicaConfig.debugGuiScale, scaleMax)) + direction;
                if (next > scaleMax) next = DebugGuiScale.UNCHANGED;
                else if (next < DebugGuiScale.UNCHANGED) next = scaleMax;
                AngelicaConfig.debugGuiScale = next;
            }
            default -> {}
        }
        configDirty = true;
        refreshLayoutValue(row);
    }

    private int maxScroll() {
        return Math.max(0, rows.size() - visibleRows);
    }

    private int shownEnd() {
        return Math.min(rows.size(), scrollRow + visibleRows);
    }

    private void scrollTo(int row) {
        scrollRow = Math.max(0, Math.min(row, maxScroll()));
        for (int i = scrollRow, end = shownEnd(); i < end; i++) {
            final Row shown = rows.get(i);
            if (shown.buttons == null) continue;
            final int y = viewTop + (i - scrollRow) * ROW_H + (ROW_H - ROW_BUTTON_H) / 2;
            if (shown.kind == RowKind.ENTRY) {
                for (final Button button : shown.buttons) {
                    button.x = buttonsX + button.id * (ROW_BUTTON_W + ROW_BUTTON_GAP);
                    button.y = y;
                }
            } else {
                shown.buttons[0].x = buttonsX;
                shown.buttons[0].y = y;
                shown.buttons[1].x = buttonsX + ROW_BUTTONS_W - ARROW_W;
                shown.buttons[1].y = y;
            }
        }
    }

    private int thumbHeight() {
        return Math.max(SCROLLBAR_MIN_THUMB, viewH * visibleRows / rows.size());
    }

    private void dragTo(int mouseY) {
        final int max = maxScroll();
        if (max == 0) return;
        final int track = viewH - thumbHeight();
        if (track <= 0) return;
        scrollTo(((mouseY - dragOffset - viewTop) * max + track / 2) / track);
    }

    private static String translate(String key) {
        return StatCollector.translateToLocal(key);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        final int page = Math.max(1, visibleRows - 1);
        final int step = switch (keyCode) {
            case Keyboard.KEY_UP -> -1;
            case Keyboard.KEY_DOWN -> 1;
            case Keyboard.KEY_PRIOR -> -page;
            case Keyboard.KEY_NEXT -> page;
            default -> 0;
        };
        if (step != 0) {
            scrollTo(scrollRow + step);
            return;
        }
        if (searchField.textboxKeyTyped(typedChar, keyCode)) {
            final String text = searchField.getText();
            if (!text.equals(search)) {
                search = text;
                scrollRow = 0;
                buildRows();
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        searchField.mouseClicked(mouseX, mouseY, mouseButton);
        searchField.setFocused(true);
        if (mouseButton != 0) return;

        if (doneButton.pressed(mouseX, mouseY)) {
            playPressSound();
            mc.displayGuiScreen(null);
            return;
        }
        for (final Button button : profileButtons) {
            if (!button.pressed(mouseX, mouseY)) continue;
            playPressSound();
            DebugScreen.ENTRIES.loadProfile(DebugScreenProfile.VALUES[button.id]);
            DebugScreen.ENTRIES.save();
            refreshStates();
            return;
        }

        final int barX = listX + listW - SCROLLBAR_W;
        if (mouseX >= barX && mouseX < barX + SCROLLBAR_W && mouseY >= viewTop && mouseY < viewTop + viewH) {
            final int max = maxScroll();
            if (max == 0) return;
            final int thumbH = thumbHeight();
            final int thumbY = viewTop + (viewH - thumbH) * scrollRow / max;
            dragging = true;
            if (mouseY >= thumbY && mouseY < thumbY + thumbH) {
                dragOffset = mouseY - thumbY;
            } else {
                dragOffset = thumbH / 2;
                dragTo(mouseY);
            }
            return;
        }

        for (int i = scrollRow, end = shownEnd(); i < end; i++) {
            final Row row = rows.get(i);
            if (row.buttons == null) continue;
            for (final Button button : row.buttons) {
                if (!button.pressed(mouseX, mouseY)) continue;
                playPressSound();
                if (row.kind == RowKind.ENTRY) {
                    DebugScreen.ENTRIES.setStatus(row.def, CHOICES[button.id].status);
                    refreshStates();
                } else {
                    stepLayout(row, button.id == 0 ? -1 : 1);
                }
                return;
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (dragging && clickedMouseButton == 0) dragTo(mouseY);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        if (state == 0) dragging = false;
        super.mouseMovedOrUp(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        final int wheel = Mouse.getEventDWheel();
        if (wheel != 0) scrollTo(scrollRow - Integer.signum(wheel) * WHEEL_ROWS);
    }

    @Override
    public void updateScreen() {
        searchField.updateCursorCounter();
        if (DebugScreen.isGroupedActive() != grouped) {
            layoutView();
            buildRows();
        } else if (DebugScreen.ENTRIES.version() != lastVersion) {
            refreshStates();
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        if (configDirty) {
            configDirty = false;
            ConfigurationManager.save(AngelicaConfig.class);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void playPressSound() {
        mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(BUTTON_PRESS, 1.0F));
    }

    private void rect(int x1, int y1, int x2, int y2, int argb) {
        if (painter != null) {
            painter.rect(x1, y1, x2, y2, argb);
        } else {
            Gui.drawRect(x1, y1, x2, y2, argb);
        }
    }

    private void drawText(String text, int x, int y, int color) {
        if (fonts != null) {
            fonts.angelica$drawStringBatched(text, x, y, color, true);
        } else {
            fontRendererObj.drawStringWithShadow(text, x, y, color);
        }
    }

    private void beginLayer() {
        if (painter == null) return;
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        batcher.beginBatch();
        painter.beginRects();
    }

    private void endLayer() {
        if (painter == null) return;
        try {
            painter.endRects();
        } finally {
            batcher.endBatch();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        final List<String> tooltip;
        try {
            beginLayer();
            tooltip = drawContents(mouseX, mouseY);
        } finally {
            endLayer();
        }
        if (tooltip == null && !searchField.cursorPending) return;
        try {
            beginLayer();
            searchField.drawCursor();
            if (tooltip != null) drawTooltip(tooltip, mouseX, mouseY);
        } finally {
            endLayer();
        }
    }

    private List<String> drawContents(int mouseX, int mouseY) {
        rect(0, 0, width, viewTop, 0x60000000);
        rect(0, footerTop - 4, width, height, 0x60000000);
        rect(listX, viewTop, listX + listW, viewTop + viewH, 0x40000000);

        drawText(title, titleX, 11, NORMAL_COLOR);
        for (int i = 0; i < headerLineX.length; i++) {
            drawText(headerLines.get(i), headerLineX[i], HEADER_H + i * HEADER_LINE_H, headerColor);
        }

        searchField.drawTextBox();
        if (search.isEmpty()) {
            drawText(searchPlaceholder, searchField.xPosition + 4 + cursorW, searchField.yPosition + 5, PLACEHOLDER_COLOR);
        }

        List<String> tooltip = null;
        for (int i = scrollRow, end = shownEnd(); i < end; i++) {
            final Row row = rows.get(i);
            final int y = viewTop + (i - scrollRow) * ROW_H;
            if (row.kind == RowKind.HEADER) {
                drawText(row.text, listX + listW / 2 - row.textW / 2, y + 6, NORMAL_COLOR);
                continue;
            }
            drawText(row.text, nameX, y + 6, row.color);
            if (row.kind == RowKind.PANEL) {
                drawText(row.value, buttonsX + ROW_BUTTONS_W - row.valueW, y + 6, INACTIVE_COLOR);
            } else {
                if (row.value != null) drawText(row.value, buttonsX + ROW_BUTTONS_W / 2 - row.valueW / 2, y + 6, NORMAL_COLOR);
                for (final Button button : row.buttons) {
                    button.draw(button.contains(mouseX, mouseY));
                }
            }
            final int hoverRight = row.buttons == null ? buttonsX + ROW_BUTTONS_W : buttonsX;
            if (row.tooltip != null && mouseX >= nameX && mouseX < hoverRight && mouseY >= y && mouseY < y + ROW_H) {
                tooltip = row.tooltip;
            }
        }

        final int max = maxScroll();
        if (max > 0) {
            final int thumbH = thumbHeight();
            final int thumbY = viewTop + (viewH - thumbH) * scrollRow / max;
            final int barX = listX + listW - SCROLLBAR_W;
            rect(barX, viewTop, barX + SCROLLBAR_W, viewTop + viewH, 0x60000000);
            rect(barX, thumbY, barX + SCROLLBAR_W, thumbY + thumbH, dragging ? 0xFFFFFFFF : OUTLINE_COLOR);
        }

        for (final Button button : profileButtons) {
            button.draw(button.contains(mouseX, mouseY));
        }
        doneButton.draw(doneButton.contains(mouseX, mouseY));
        return tooltip;
    }

    private void drawTooltip(List<String> lines, int mouseX, int mouseY) {
        if (lines != measuredTooltip) {
            int widest = 0;
            for (int i = 0, n = lines.size(); i < n; i++) {
                widest = Math.max(widest, fontRendererObj.getStringWidth(lines.get(i)));
            }
            measuredTooltip = lines;
            measuredTooltipW = widest;
        }
        final int w = measuredTooltipW;
        final int h = lines.size() > 1 ? 10 + (lines.size() - 1) * 10 : 8;
        int x = mouseX + TOOLTIP_OFFSET;
        int y = mouseY - TOOLTIP_OFFSET;
        if (x + w > width) x -= 28 + w;
        x = Math.max(4, x);
        if (y + h + 6 > height) y = height - h - 6;
        y = Math.max(4, y);

        rect(x - 3, y - 4, x + w + 3, y - 3, TOOLTIP_BACKGROUND);
        rect(x - 3, y + h + 3, x + w + 3, y + h + 4, TOOLTIP_BACKGROUND);
        rect(x - 3, y - 3, x + w + 3, y + h + 3, TOOLTIP_BACKGROUND);
        rect(x - 4, y - 3, x - 3, y + h + 3, TOOLTIP_BACKGROUND);
        rect(x + w + 3, y - 3, x + w + 4, y + h + 3, TOOLTIP_BACKGROUND);
        rect(x - 3, y - 2, x - 2, y + h + 2, TOOLTIP_BORDER);
        rect(x + w + 2, y - 2, x + w + 3, y + h + 2, TOOLTIP_BORDER);
        rect(x - 3, y - 3, x + w + 3, y - 2, TOOLTIP_BORDER);
        rect(x - 3, y + h + 2, x + w + 3, y + h + 3, TOOLTIP_BORDER);

        for (int i = 0, n = lines.size(); i < n; i++) {
            drawText(lines.get(i), x, y, TOOLTIP_TEXT_COLOR);
            y += i == 0 ? 12 : 10;
        }
    }
}
