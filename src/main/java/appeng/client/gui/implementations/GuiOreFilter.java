package appeng.client.gui.implementations;

import java.io.IOException;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import appeng.client.gui.GuiSub;
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

/** Editor for the ore dictionary filter of an {@link IOreFilterable}. */
public class GuiOreFilter extends GuiSub implements IDropToFillTextField {

    private static final int MAX_FILTER_CHARS = 4096;

    /** The renamer background has the recess of the text field at this position. */
    private static final int TEXT_X = 12;
    private static final int TEXT_Y = 35;
    private static final int TEXT_WIDTH = 231;
    private static final int TEXT_HEIGHT = 12;

    private static final int STATUS_X = 8;
    private static final int BUTTON_Y = 15;
    private static final int BOTTOM_TEXT_Y = 49;

    private static final int ICON_COPY = 197;
    private static final int ICON_PASTE = 130;
    private static final int ICON_CLEAR = 112;
    private static final int ICON_CHECK_OFF = 193;
    private static final int ICON_CHECK_ON = 177;
    private static final int ICON_SUBMIT = 7;
    private static final int ICON_SUBMIT_OK = 129;
    private static final int ICON_SUBMIT_FAILED = 128;

    private static final int DOUBLE_CLICK_TIME = 400;

    private final MEGuiTextField textField;

    private GuiSimpleImgButton copyButton;
    private GuiSimpleImgButton pasteButton;
    private GuiSimpleImgButton clearButton;
    private GuiSimpleImgButton checkButton;
    private GuiSimpleImgButton submitButton;

    private String statusText = "";
    private String statusTooltip = "";
    private int statusColor;
    private int statusWidth;

    private boolean useNEIFilter = true;
    private long lastClickTime;

    /** The filter the machine is currently using. */
    private String appliedFilter = "";

    public GuiOreFilter(final InventoryPlayer ip, final IOreFilterable obj) {
        super(new ContainerOreFilter(ip, obj));

        this.xSize = 256;
        this.ySize = 61;
        this.statusColor = ColorUtils.guiTextColorGray.getColor();

        this.textField = new MEGuiTextField(TEXT_WIDTH, TEXT_HEIGHT) {

            @Override
            public void onTextChange(final String oldText) {
                GuiOreFilter.this.onTextChanged();
            }
        };
        this.textField.setMaxStringLength(MAX_FILTER_CHARS);
        if (NEI.searchField.existsSearchField()) this.textField.setFormatter(new OreFilterTextFormatter());
    }

    @Override
    public void initGui() {
        super.initGui();

        this.copyButton = new GuiSimpleImgButton(
                this.guiLeft + 8,
                this.guiTop + BUTTON_Y,
                ICON_COPY,
                tooltip(GuiText.OreFilterCopy, GuiText.OreFilterCopyHint));
        this.pasteButton = new GuiSimpleImgButton(
                this.guiLeft + 28,
                this.guiTop + BUTTON_Y,
                ICON_PASTE,
                tooltip(GuiText.OreFilterPaste, GuiText.OreFilterPasteHint));
        this.clearButton = new GuiSimpleImgButton(
                this.guiLeft + 48,
                this.guiTop + BUTTON_Y,
                ICON_CLEAR,
                tooltip(GuiText.OreFilterClear, GuiText.OreFilterClearHint));
        this.checkButton = new GuiSimpleImgButton(
                this.guiLeft + 68,
                this.guiTop + BUTTON_Y,
                ICON_CHECK_OFF,
                tooltip(GuiText.OreFilterCheck, GuiText.OreFilterCheckHint));
        this.submitButton = new GuiSimpleImgButton(
                this.guiLeft + 88,
                this.guiTop + BUTTON_Y,
                ICON_SUBMIT,
                tooltip(GuiText.OreFilterSubmit, GuiText.OreFilterSubmitHint));

        this.buttonList.add(this.copyButton);
        this.buttonList.add(this.pasteButton);
        this.buttonList.add(this.clearButton);
        this.buttonList.add(this.checkButton);
        this.buttonList.add(this.submitButton);

        this.updateCheckButton();

        this.textField.x = this.guiLeft + TEXT_X;
        this.textField.y = this.guiTop + TEXT_Y;
        this.textField.setFocused(true);

        ((ContainerOreFilter) this.inventorySlots).setGui(this);

        this.syncNeiFilter();
        this.updateSubmitState();
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/renamer.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);

        this.textField.drawTextBox();
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRendererObj
                .drawString(GuiText.OreFilterLabel.getLocal(), 8, 6, ColorUtils.guiTextColorGray.getColor());

        final String counter = this.getText().length() + "/" + MAX_FILTER_CHARS;
        final int counterColor = this.getText().length() >= MAX_FILTER_CHARS
                ? ColorUtils.oreFilterTextLengthFull.getColor()
                : ColorUtils.guiTextColorGray.getColor();
        final int counterWidth = this.fontRendererObj.getStringWidth(counter);
        this.fontRendererObj.drawString(counter, TEXT_X + TEXT_WIDTH - counterWidth - 1, BOTTOM_TEXT_Y, counterColor);

        if (!this.statusText.isEmpty()) {
            final int availableWidth = Math.max(0, this.xSize - 8 - counterWidth - 6 - STATUS_X);
            final String status = this.fontRendererObj.trimStringToWidth(this.statusText, availableWidth);
            this.statusWidth = this.fontRendererObj.getStringWidth(status);
            this.fontRendererObj.drawString(status, STATUS_X, BOTTOM_TEXT_Y, this.statusColor);
        } else {
            this.statusWidth = 0;
        }

        if (this.useNEIFilter) {
            final int color = ColorUtils.oreFilterNeiIndicator.getColor();
            final int left = TEXT_X - 1;
            final int right = TEXT_X + TEXT_WIDTH + 1;
            final int top = TEXT_Y - 1;
            final int bottom = TEXT_Y + TEXT_HEIGHT + 1;

            drawRect(left, top, right, top + 1, color);
            drawRect(left, bottom - 1, right, bottom, color);
            drawRect(left, top, left + 1, bottom, color);
            drawRect(right - 1, top, right, bottom, color);
        }

        // the pressed look belongs to the foreground layer, so that it stays below the tooltips
        if (this.useNEIFilter && this.checkButton.enabled) {
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

    private void drawPressedCheckButton() {
        final int x = this.checkButton.xPosition - this.guiLeft;
        final int y = this.checkButton.yPosition - this.guiTop;
        final int right = x + this.checkButton.width;
        final int bottom = y + this.checkButton.height;

        final int dark = ColorUtils.oreFilterPressedDark.getColor();
        final int light = ColorUtils.oreFilterPressedLight.getColor();

        drawRect(x, y, right, y + 1, dark);
        drawRect(x, y + 1, x + 1, bottom - 1, dark);
        drawRect(x + 1, bottom - 1, right, bottom, light);
        drawRect(right - 1, y + 1, right, bottom - 1, light);
    }

    private boolean isOverStatus(final int mouseX, final int mouseY) {
        final int left = this.guiLeft + STATUS_X;
        final int top = this.guiTop + BOTTOM_TEXT_Y - 1;

        return this.statusWidth > 0 && mouseX >= left
                && mouseX <= left + this.statusWidth
                && mouseY >= top
                && mouseY <= top + 10;
    }

    private static String tooltip(final Localization name, final Localization hint) {
        return name.getLocal() + "\n" + hint.getLocal();
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        if (btn == this.copyButton) {
            GuiScreen.setClipboardString(this.getText());
        } else if (btn == this.pasteButton) {
            this.pasteFromClipboard();
        } else if (btn == this.clearButton) {
            this.textField.setText("");
            this.textField.setCursorPositionEnd();
        } else if (btn == this.checkButton) {
            this.toggleNeiFilter();
        } else if (btn == this.submitButton) {
            this.submit();
        }
    }

    private void toggleNeiFilter() {
        if (!NEI.searchField.existsSearchField()) return;

        this.useNEIFilter = !this.useNEIFilter;
        NEI.searchField.updateFilter();
        this.updateCheckButton();
    }

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

        if (this.textField.textboxKeyTyped(character, key)) return;

        super.keyTyped(character, key);
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) {
        if (btn == 0 && NEI.searchField.existsSearchField() && this.textField.isMouseIn(xCoord, yCoord)) {
            if (System.currentTimeMillis() - this.lastClickTime < DOUBLE_CLICK_TIME) {
                this.toggleNeiFilter();
            }
            this.lastClickTime = System.currentTimeMillis();
        }

        if (btn == 1) {
            // a right click only moves the focus, it must not clear the text
            if (this.textField.isMouseIn(xCoord, yCoord)) this.textField.setFocused(true);
        } else {
            this.textField.mouseClicked(xCoord, yCoord, btn);
        }

        super.mouseClicked(xCoord, yCoord, btn);
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
        this.appliedFilter = text;

        if (!this.getText().equals(text)) {
            this.textField.setText(text, true);
            this.textField.setCursorPositionEnd();
        }

        this.updateSubmitState();
    }

    public String getText() {
        return this.textField.getText();
    }

    public boolean useNEIFilter() {
        return this.useNEIFilter;
    }

    @Override
    public boolean isOverTextField(final int mousex, final int mousey) {
        return this.textField.isMouseIn(mousex, mousey);
    }

    @Override
    public void setTextFieldValue(final String displayName, final int mousex, final int mousey, final ItemStack stack) {
        final int[] ores = OreDictionary.getOreIDs(stack);
        if (ores.length == 0) return;

        final String oreName = OreDictionary.getOreName(ores[0]);

        if (Mouse.isButtonDown(1)) {
            this.textField.setText(oreName);
        } else if (Mouse.isButtonDown(0)) {
            final String current = this.getText();
            this.textField.setText(current.isEmpty() ? oreName : current + " | " + oreName);
        }

        this.textField.setCursorPositionEnd();
    }

    private void submit() {
        this.appliedFilter = this.getText();
        this.updateSubmitState();

        try {
            NetworkHandler.instance.sendToServer(new PacketValueConfig("OreFilter", this.appliedFilter));
        } catch (final IOException e) {
            AELog.debug(e);
        }
    }

    /**
     * The submit button and the status line report whether the edited filter differs from the one the machine is using,
     * and - while both are equal - whether the filter can be parsed.
     */
    private void updateSubmitState() {
        final String filter = this.getText();

        if (filter.isEmpty() && this.appliedFilter.isEmpty()) {
            this.clearStatus();
            this.submitButton.setIconIndex(ICON_SUBMIT);
        } else if (!filter.equals(this.appliedFilter)) {
            this.setStatus(GuiText.OreFilterUnsaved.getLocal(), ColorUtils.guiTextColorGray.getColor());
            this.submitButton.setIconIndex(ICON_SUBMIT);
        } else {
            final OreFilteredList.ParseResult result = OreFilteredList.parse(filter);

            this.setStatus(
                    result.isSuccess() ? GuiText.OreFilterParseOk.getLocal() : GuiText.OreFilterParseError.getLocal(),
                    result.isSuccess() ? ColorUtils.oreFilterParseOk.getColor()
                            : ColorUtils.oreFilterParseError.getColor(),
                    result.getError());

            this.submitButton.setIconIndex(result.isSuccess() ? ICON_SUBMIT_OK : ICON_SUBMIT_FAILED);
        }
    }

    private void pasteFromClipboard() {
        String clipboard = GuiScreen.getClipboardString();
        if (clipboard.isEmpty()) return;

        // line breaks have no meaning inside the filter expression, treat them as an alternative separator
        clipboard = clipboard.replace("\r\n", "\n").replace('\r', '\n').replace("\n", " | ");

        this.textField.setText(clipboard);
        this.textField.setCursorPositionEnd();
    }

    private void onTextChanged() {
        this.updateSubmitState();
        this.syncNeiFilter();
    }

    private void syncNeiFilter() {
        if (!this.useNEIFilter) return;

        NEI.searchField.updateFilter();
    }

    private void setStatus(final String text, final int color) {
        this.setStatus(text, color, "");
    }

    private void setStatus(final String text, final int color, final String tooltip) {
        this.statusText = text;
        this.statusColor = color;
        this.statusTooltip = tooltip == null ? "" : tooltip;
    }

    private void clearStatus() {
        this.statusText = "";
        this.statusTooltip = "";
    }
}
