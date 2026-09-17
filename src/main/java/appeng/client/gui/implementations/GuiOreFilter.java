package appeng.client.gui.implementations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import appeng.client.gui.GuiSub;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiSimpleImgButton;
import appeng.client.gui.widgets.IDropToFillTextField;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.implementations.ContainerOreFilter;
import appeng.core.AELog;
import appeng.core.localization.ColorUtils;
import appeng.core.localization.GuiText;
import appeng.core.localization.Localization;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.IOreFilterable;
import appeng.integration.modules.NEI;
import appeng.util.prioitylist.OreFilteredList;
import appeng.util.prioitylist.OreFilteredList.OreFilterTextFormatter;

/**
 * Editor for the ore dictionary filter of an {@link IOreFilterable}. The filter is a single expression of at most
 * {@value #MAX_FILTER_CHARS} characters, which is displayed as soft wrapped lines in a scrollable text area. Submitting
 * the filter - either with the submit button or by pressing return - parses it and shows the outcome in the bottom bar.
 */
public class GuiOreFilter extends GuiSub implements IDropToFillTextField {

    /** Character limit of the whole filter expression. */
    private static final int MAX_FILTER_CHARS = 4096;
    /** Number of text lines that are visible at the same time. */
    private static final int VISIBLE_LINES = 8;
    /** Distance between two text lines. */
    private static final int LINE_SPACING = 16;
    /** Height of a single text field. */
    private static final int LINE_HEIGHT = 14;

    private static final int TEXT_X = 8;
    private static final int TEXT_Y = 38;
    private static final int TEXT_WIDTH = 230;
    private static final int SCROLLBAR_X = 240;

    private static final int STATUS_X = 28;
    private static final int BOTTOM_BUTTON_Y = 172;
    private static final int BOTTOM_TEXT_Y = 177;

    private static final int ICON_COPY = 197;
    private static final int ICON_PASTE = 130;
    private static final int ICON_CLEAR = 112;
    private static final int ICON_CHECK_OFF = 193;
    private static final int ICON_CHECK_ON = 177;
    /** The filter was edited and has to be submitted again. */
    private static final int ICON_SUBMIT = 7;
    /** The submitted filter is valid. */
    private static final int ICON_SUBMIT_OK = 129;
    /** The submitted filter could not be parsed. */
    private static final int ICON_SUBMIT_FAILED = 128;

    private static final int FOCUSED_LINE_OVERLAY = 0x22FFFFFF;
    private static final int PRESSED_DARK = 0xFF444444;
    private static final int PRESSED_LIGHT = 0xFFE5E5E5;
    private static final int NEI_INDICATOR = 0xFFFFFF00;

    private static final int DOUBLE_CLICK_TIME = 400;

    private final List<MEGuiTextField> rows = new ArrayList<>(VISIBLE_LINES);
    private final GuiScrollbar scrollbar = new GuiScrollbar();

    /** The complete filter expression. */
    private String buffer = "";
    /** {@link #buffer} split into lines that fit into the text area. */
    private final List<String> lines = new ArrayList<>();
    /** Index of the first character of every line inside {@link #buffer}. */
    private final List<Integer> lineStarts = new ArrayList<>();

    private GuiSimpleImgButton copyButton;
    private GuiSimpleImgButton pasteButton;
    private GuiSimpleImgButton clearButton;
    private GuiSimpleImgButton checkButton;
    private GuiSimpleImgButton submitButton;

    private String statusText = "";
    private String statusTooltip = "";
    private int statusColor;
    private int statusWidth;
    private int boundScroll = -1;

    private boolean suppressTextChange;
    private boolean useNEIFilter = true;
    private long lastClickTime;

    /** The filter that was last applied, i.e. what the machine is using right now. */
    private String appliedFilter = "";
    private boolean appliedFilterValid = true;
    /** True once the submitted state was refreshed for this GUI instance. */
    private boolean stateRefreshed;

    public GuiOreFilter(final InventoryPlayer ip, final IOreFilterable obj) {
        super(new ContainerOreFilter(ip, obj));

        this.xSize = 256;
        this.ySize = 200;
        this.statusColor = ColorUtils.guiTextColorGray.getColor();

        final boolean hasSearchField = NEI.searchField.existsSearchField();
        for (int i = 0; i < VISIBLE_LINES; i++) {
            final MEGuiTextField row = new MEGuiTextField(TEXT_WIDTH, LINE_HEIGHT) {

                @Override
                public void onTextChange(final String oldText) {
                    if (GuiOreFilter.this.suppressTextChange) return;

                    GuiOreFilter.this.onRowChanged(GuiOreFilter.this.rows.indexOf(this));
                }
            };
            row.setMaxStringLength(MAX_FILTER_CHARS);
            if (hasSearchField) row.setFormatter(new OreFilterTextFormatter());
            this.rows.add(row);
        }

        this.setBuffer("");
    }

    @Override
    public void initGui() {
        super.initGui();

        this.copyButton = new GuiSimpleImgButton(
                this.guiLeft + 8,
                this.guiTop + 18,
                ICON_COPY,
                tooltip(GuiText.OreFilterCopy, GuiText.OreFilterCopyHint));
        this.pasteButton = new GuiSimpleImgButton(
                this.guiLeft + 28,
                this.guiTop + 18,
                ICON_PASTE,
                tooltip(GuiText.OreFilterPaste, GuiText.OreFilterPasteHint));
        this.clearButton = new GuiSimpleImgButton(
                this.guiLeft + 48,
                this.guiTop + 18,
                ICON_CLEAR,
                tooltip(GuiText.OreFilterClear, GuiText.OreFilterClearHint));
        this.checkButton = new GuiSimpleImgButton(
                this.guiLeft + 68,
                this.guiTop + 18,
                ICON_CHECK_OFF,
                tooltip(GuiText.OreFilterCheck, GuiText.OreFilterCheckHint));
        this.submitButton = new GuiSimpleImgButton(
                this.guiLeft + 8,
                this.guiTop + BOTTOM_BUTTON_Y,
                ICON_SUBMIT,
                tooltip(GuiText.OreFilterSubmit, GuiText.OreFilterSubmitHint));

        this.buttonList.add(this.copyButton);
        this.buttonList.add(this.pasteButton);
        this.buttonList.add(this.clearButton);
        this.buttonList.add(this.checkButton);
        this.buttonList.add(this.submitButton);

        this.updateCheckButton();
        this.updateSubmitButton();

        this.scrollbar.setLeft(SCROLLBAR_X).setTop(TEXT_Y).setHeight(VISIBLE_LINES * LINE_SPACING - 2);
        this.setScrollBar(this.scrollbar);
        this.updateScrollRange();

        ((ContainerOreFilter) this.inventorySlots).setGui(this);

        this.rebindRows();
        this.updateRowLayout();

        // the filter is used as the NEI search filter while the GUI is open
        this.syncNeiFilter();

        this.focusFirstRow();
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/orefilter.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);

        this.updateRowLayout();

        for (final MEGuiTextField row : this.rows) {
            if (row.isFocused()) {
                drawRect(row.x, row.y, row.x + TEXT_WIDTH, row.y + LINE_HEIGHT, FOCUSED_LINE_OVERLAY);
            }
            row.drawTextBox();
        }
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRendererObj
                .drawString(GuiText.OreFilterLabel.getLocal(), 8, 6, ColorUtils.guiTextColorGray.getColor());

        final String counter = this.buffer.length() + "/" + MAX_FILTER_CHARS;
        final int counterColor = this.buffer.length() >= MAX_FILTER_CHARS
                ? ColorUtils.oreFilterTextLengthFull.getColor()
                : ColorUtils.guiTextColorGray.getColor();
        final int counterWidth = this.fontRendererObj.getStringWidth(counter);
        this.fontRendererObj.drawString(counter, this.xSize - counterWidth - 8, BOTTOM_TEXT_Y, counterColor);

        if (!this.statusText.isEmpty()) {
            final int availableWidth = Math.max(0, this.xSize - 8 - counterWidth - 6 - STATUS_X);
            final String status = this.fontRendererObj.trimStringToWidth(this.statusText, availableWidth);
            this.statusWidth = this.fontRendererObj.getStringWidth(status);
            this.fontRendererObj.drawString(status, STATUS_X, BOTTOM_TEXT_Y, this.statusColor);
        } else {
            this.statusWidth = 0;
        }

        if (this.useNEIFilter) {
            final int left = TEXT_X - 1;
            final int right = TEXT_X + TEXT_WIDTH + 1;
            final int top = TEXT_Y - 1;
            final int bottom = TEXT_Y + VISIBLE_LINES * LINE_SPACING;

            drawRect(left, top, right, top + 1, NEI_INDICATOR);
            drawRect(left, bottom, right, bottom + 1, NEI_INDICATOR);
            drawRect(left, top, left + 1, bottom, NEI_INDICATOR);
            drawRect(right - 1, top, right, bottom, NEI_INDICATOR);
        }

        // the pressed look belongs to the foreground layer, so that it stays below the tooltips
        if (this.useNEIFilter && this.checkButton != null && this.checkButton.enabled) {
            this.drawPressedCheckButton();
        }
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        if (!this.statusTooltip.isEmpty() && this.isOverStatus(mouseX, mouseY)) {
            this.drawTooltip(mouseX, mouseY, this.statusTooltip);
        }
    }

    /** Gives the check button the sunken bevel of a button that is currently pressed down. */
    private void drawPressedCheckButton() {
        final int x = this.checkButton.xPosition - this.guiLeft;
        final int y = this.checkButton.yPosition - this.guiTop;
        final int right = x + this.checkButton.width;
        final int bottom = y + this.checkButton.height;

        drawRect(x, y, right, y + 1, PRESSED_DARK);
        drawRect(x, y + 1, x + 1, bottom - 1, PRESSED_DARK);
        drawRect(x + 1, bottom - 1, right, bottom, PRESSED_LIGHT);
        drawRect(right - 1, y + 1, right, bottom - 1, PRESSED_LIGHT);
    }

    private boolean isOverStatus(final int mouseX, final int mouseY) {
        final int left = this.guiLeft + STATUS_X;
        final int top = this.guiTop + BOTTOM_TEXT_Y - 1;

        return this.statusWidth > 0 && mouseX >= left
                && mouseX <= left + this.statusWidth
                && mouseY >= top
                && mouseY <= top + 10;
    }

    /** The usual two line tooltip of the AE buttons: the name of the action and a short hint. */
    private static String tooltip(final Localization name, final Localization hint) {
        return name.getLocal() + "\n" + hint.getLocal();
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        if (btn == this.copyButton) {
            GuiScreen.setClipboardString(this.buffer);
        } else if (btn == this.pasteButton) {
            this.pasteFromClipboard();
        } else if (btn == this.clearButton) {
            this.setBuffer("");
            this.moveCaretTo(0);
            this.clearStatus();
        } else if (btn == this.checkButton) {
            this.toggleNeiFilter();
        } else if (btn == this.submitButton) {
            this.submit();
        }
    }

    /** Toggles using the filter as the NEI search filter, like double clicking the text area does. */
    private void toggleNeiFilter() {
        if (!NEI.searchField.existsSearchField()) return;

        this.useNEIFilter = !this.useNEIFilter;
        NEI.searchField.updateFilter();
        this.updateCheckButton();
    }

    /** Shows whether the check mode is currently on and disables the button while NEI is not available. */
    private void updateCheckButton() {
        final boolean available = NEI.searchField.existsSearchField();

        this.checkButton.enabled = available;
        this.checkButton.setIconIndex(this.useNEIFilter ? ICON_CHECK_ON : ICON_CHECK_OFF);
    }

    @Override
    protected void keyTyped(final char character, final int key) {
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            this.submit();
            return;
        }

        final MEGuiTextField focused = this.getFocusedRow();
        if (focused != null) {
            final int lineIndex = this.scrollbar.getCurrentScroll() + this.rows.indexOf(focused);

            if (lineIndex >= 0 && lineIndex < this.lineStarts.size()) {
                final int lineStart = this.lineStarts.get(lineIndex);

                // soft wrapped lines behave like a single text field, so backspace and delete cross the line ends
                if (key == Keyboard.KEY_BACK && focused.getCursorPosition() == 0 && lineIndex > 0) {
                    this.setBuffer(this.buffer.substring(0, lineStart - 1) + this.buffer.substring(lineStart));
                    this.moveCaretTo(lineStart - 1);
                    return;
                }

                if (key == Keyboard.KEY_DELETE && focused.getCursorPosition() >= focused.getText().length()) {
                    final int lineEnd = lineStart + this.lines.get(lineIndex).length();
                    if (lineEnd < this.buffer.length()) {
                        this.setBuffer(this.buffer.substring(0, lineEnd) + this.buffer.substring(lineEnd + 1));
                        this.moveCaretTo(lineEnd);
                        return;
                    }
                }
            }

            if (focused.textboxKeyTyped(character, key)) return;
        }

        super.keyTyped(character, key);
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) {
        if (btn == 0 && NEI.searchField.existsSearchField()) {
            for (final MEGuiTextField row : this.rows) {
                if (!row.isMouseIn(xCoord, yCoord)) continue;

                // double click toggles using the filter as the NEI search filter
                if (row.isFocused() && System.currentTimeMillis() - this.lastClickTime < DOUBLE_CLICK_TIME) {
                    this.toggleNeiFilter();
                }
                this.lastClickTime = System.currentTimeMillis();
                break;
            }
        }

        if (btn == 1) {
            // a right click only moves the focus, it must not clear the text
            for (final MEGuiTextField row : this.rows) {
                if (row.isMouseIn(xCoord, yCoord)) row.setFocused(true);
            }
        } else {
            for (final MEGuiTextField row : this.rows) {
                row.mouseClicked(xCoord, yCoord, btn);
            }
        }

        // clicking below the last line puts the caret at the end of the filter
        final MEGuiTextField focused = this.getFocusedRow();
        if (focused != null && this.scrollbar.getCurrentScroll() + this.rows.indexOf(focused) >= this.lines.size()) {
            this.moveCaretTo(this.buffer.length());
        }

        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected boolean mouseWheelEvent(final int x, final int y, final int wheel) {
        if (this.isOverTextArea(x, y)) {
            this.scrollbar.wheel(wheel);
            return true;
        }

        return false;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();

        if (this.useNEIFilter) {
            this.useNEIFilter = false;
            NEI.searchField.updateFilter();
        }
    }

    /** Called by the container whenever the filter changed on the server. */
    public void setFilterText(final String text) {
        if (!this.buffer.equals(text)) {
            // this is the value the machine is currently using
            this.appliedFilter = text;
            this.appliedFilterValid = OreFilteredList.parse(text).isSuccess();

            this.setBuffer(text);
            this.rebindRows();
        }

        this.refreshSubmitState();
    }

    /** @return the complete filter expression. */
    public String getText() {
        return this.buffer;
    }

    /** @return true while the edited expression is used as the NEI search filter. */
    public boolean useNEIFilter() {
        return this.useNEIFilter;
    }

    @Override
    public boolean isOverTextField(final int mousex, final int mousey) {
        return this.isOverTextArea(mousex, mousey);
    }

    @Override
    public void setTextFieldValue(final String displayName, final int mousex, final int mousey, final ItemStack stack) {
        final int[] ores = OreDictionary.getOreIDs(stack);
        if (ores.length == 0) return;

        final String oreName = OreDictionary.getOreName(ores[0]);

        if (Mouse.isButtonDown(1)) {
            // right click replaces the whole filter
            this.setBuffer(oreName);
        } else if (Mouse.isButtonDown(0)) {
            // left click appends to the filter
            this.setBuffer(this.buffer.isEmpty() ? oreName : this.buffer + " | " + oreName);
        }

        this.moveCaretTo(this.buffer.length());
    }

    private void submit() {
        this.applyFilter(true);
    }

    /**
     * Evaluates the edited filter and shows the outcome on the submit button and in the status line.
     *
     * @param send whether the filter is also sent to the server, which is not wanted when the GUI is opened.
     */
    private void applyFilter(final boolean send) {
        final OreFilteredList.ParseResult result = OreFilteredList.parse(this.buffer);

        this.stateRefreshed = true;
        this.showResult(result);

        // the submitted expression is what the machine uses from now on
        this.appliedFilter = this.buffer;
        this.appliedFilterValid = result.isSuccess();
        this.updateSubmitButton();

        if (!send) return;

        try {
            NetworkHandler.instance.sendToServer(new PacketValueConfig("OreFilter", this.buffer));
        } catch (final IOException e) {
            AELog.debug(e);
            this.setStatus(GuiText.OreFilterParseError.getLocal(), true);
        }
    }

    /** Entering the GUI refreshes the shown state the same way submitting does, without sending anything. */
    private void refreshSubmitState() {
        if (this.stateRefreshed) return;

        this.applyFilter(false);
    }

    private void showResult(final OreFilteredList.ParseResult result) {
        if (result.isSuccess()) {
            final int matches = result.isEmpty() ? -1
                    : NEI.searchField.countMatchingOreFilterItems(result.getPredicate());

            if (matches >= 0) {
                this.setStatus(
                        GuiText.OreFilterMatched.getLocal() + " " + matches + " " + GuiText.OreFilterItems.getLocal(),
                        false);
            } else {
                this.setStatus(GuiText.OreFilterParseOk.getLocal(), false);
            }
        } else {
            // the details are shown as a tooltip, the label itself stays short
            this.setStatus(GuiText.OreFilterParseError.getLocal(), true, result.getError());
        }
    }

    private void pasteFromClipboard() {
        String clipboard = GuiScreen.getClipboardString();
        if (clipboard.isEmpty()) return;

        // line breaks have no meaning inside the filter expression, treat them as an alternative separator
        clipboard = clipboard.replace("\r\n", "\n").replace('\r', '\n').replace("\n", " | ");

        if (clipboard.length() > MAX_FILTER_CHARS) {
            clipboard = clipboard.substring(0, MAX_FILTER_CHARS);
            this.setStatus(GuiText.OreFilterTooLong.getLocal(), true);
        }

        this.setBuffer(clipboard);
        this.moveCaretTo(this.buffer.length());
    }

    private void onRowChanged(final int rowIndex) {
        final int lineIndex = this.scrollbar.getCurrentScroll() + rowIndex;

        // typing below the last line simply continues at the end of the filter
        if (lineIndex < 0 || lineIndex >= this.lines.size()) {
            this.setBuffer(this.buffer + this.rows.get(rowIndex).getText());
            this.moveCaretTo(this.buffer.length());
            this.clearStatus();
            return;
        }

        final MEGuiTextField row = this.rows.get(rowIndex);
        final String oldLine = this.lines.get(lineIndex);
        final String newLine = row.getText();
        final int caretInLine = row.getCursorPosition();
        final int lineStart = this.lineStarts.get(lineIndex);

        String updated = this.buffer.substring(0, lineStart) + newLine
                + this.buffer.substring(lineStart + oldLine.length());
        if (updated.length() > MAX_FILTER_CHARS) {
            updated = updated.substring(0, MAX_FILTER_CHARS);
        }

        this.setBuffer(updated);
        this.moveCaretTo(Math.min(lineStart + caretInLine, updated.length()));
        this.clearStatus();
    }

    private void setBuffer(final String text) {
        this.buffer = text.length() > MAX_FILTER_CHARS ? text.substring(0, MAX_FILTER_CHARS) : text;
        this.rewrap();
        this.updateScrollRange();
        this.updateSubmitButton();
        this.syncNeiFilter();
    }

    /**
     * Mirrors the edited filter into the NEI item panel while the check mode is active, so that typing, pasting or
     * clearing the filter updates NEI right away.
     */
    private void syncNeiFilter() {
        // the buttons only exist once initGui ran, before that there is nothing to sync
        if (this.submitButton == null || !this.useNEIFilter) return;

        NEI.searchField.updateFilter();
    }

    /**
     * The submit button shows whether the edited filter is still pending: until it is applied - and as long as the
     * filter is empty - it shows the submit icon, afterwards the result of the submitted expression.
     */
    private void updateSubmitButton() {
        if (this.submitButton == null) return;

        if (this.buffer.isEmpty() || !this.buffer.equals(this.appliedFilter)) {
            this.submitButton.setIconIndex(ICON_SUBMIT);
        } else {
            this.submitButton.setIconIndex(this.appliedFilterValid ? ICON_SUBMIT_OK : ICON_SUBMIT_FAILED);
        }
    }

    /** Splits {@link #buffer} into lines that fit into the width of the text area. */
    private void rewrap() {
        final FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        final int maxWidth = TEXT_WIDTH - 10;

        this.lines.clear();
        this.lineStarts.clear();

        final StringBuilder line = new StringBuilder();
        int lineStart = 0;
        int width = 0;

        for (int i = 0; i < this.buffer.length(); i++) {
            final char c = this.buffer.charAt(i);
            final int charWidth = font.getCharWidth(c);

            if (line.length() > 0 && width + charWidth > maxWidth) {
                this.lines.add(line.toString());
                this.lineStarts.add(lineStart);
                line.setLength(0);
                width = 0;
                lineStart = i;
            }

            line.append(c);
            width += charWidth;
        }

        this.lines.add(line.toString());
        this.lineStarts.add(lineStart);
    }

    /** Shows the lines around the current scroll position in the text fields. */
    private void rebindRows() {
        final int scroll = this.scrollbar.getCurrentScroll();

        this.suppressTextChange = true;
        try {
            for (int i = 0; i < this.rows.size(); i++) {
                final int lineIndex = scroll + i;
                final String text = lineIndex >= 0 && lineIndex < this.lines.size() ? this.lines.get(lineIndex) : "";
                this.rows.get(i).setText(text, true);
            }
        } finally {
            this.suppressTextChange = false;
        }

        this.boundScroll = scroll;
    }

    private void moveCaretTo(final int globalIndex) {
        final int index = Math.max(0, Math.min(globalIndex, this.buffer.length()));

        int line = 0;
        for (int i = 1; i < this.lineStarts.size(); i++) {
            if (this.lineStarts.get(i) <= index) line = i;
            else break;
        }

        this.scrollToLine(line);
        this.rebindRows();

        final int offset = Math.max(0, Math.min(index - this.lineStarts.get(line), this.lines.get(line).length()));
        for (final MEGuiTextField row : this.rows) {
            row.setFocused(false);
        }

        final MEGuiTextField row = this.rows.get(line - this.scrollbar.getCurrentScroll());
        row.setFocused(true);
        row.setCursorPosition(offset);
    }

    private void scrollToLine(final int line) {
        final int scroll = this.scrollbar.getCurrentScroll();

        if (line < scroll) {
            this.scrollbar.setCurrentScroll(line);
        } else if (line >= scroll + VISIBLE_LINES) {
            this.scrollbar.setCurrentScroll(line - VISIBLE_LINES + 1);
        }
    }

    private void updateScrollRange() {
        this.scrollbar.setRange(0, Math.max(0, this.lines.size() - VISIBLE_LINES), VISIBLE_LINES);
    }

    private MEGuiTextField getFocusedRow() {
        for (final MEGuiTextField row : this.rows) {
            if (row.isFocused()) return row;
        }

        return null;
    }

    private void updateRowLayout() {
        if (this.boundScroll != this.scrollbar.getCurrentScroll()) this.rebindRows();

        for (int i = 0; i < this.rows.size(); i++) {
            final MEGuiTextField row = this.rows.get(i);
            row.x = this.guiLeft + TEXT_X;
            row.y = this.guiTop + TEXT_Y + i * LINE_SPACING;
        }
    }

    private void focusFirstRow() {
        this.rows.get(0).setFocused(true);
    }

    private void setStatus(final String text, final boolean error) {
        this.setStatus(text, error, "");
    }

    private void setStatus(final String text, final boolean error, final String tooltip) {
        this.statusText = text;
        this.statusColor = error ? ColorUtils.oreFilterParseError.getColor() : ColorUtils.oreFilterParseOk.getColor();
        this.statusTooltip = tooltip;
    }

    private void clearStatus() {
        this.statusText = "";
        this.statusTooltip = "";
    }

    private boolean isOverTextArea(final int x, final int y) {
        final int left = this.guiLeft + TEXT_X;
        final int top = this.guiTop + TEXT_Y;

        return x >= left && x <= left + TEXT_WIDTH && y >= top && y <= top + VISIBLE_LINES * LINE_SPACING;
    }
}
