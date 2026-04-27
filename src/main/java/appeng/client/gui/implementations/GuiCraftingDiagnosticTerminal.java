package appeng.client.gui.implementations;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import appeng.api.config.ActionItems;
import appeng.api.config.DiagnosticSortButton;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.TerminalStyle;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.implementations.ContainerCraftingDiagnosticTerminal;
import appeng.container.slot.AppEngSlot;
import appeng.core.AEConfig;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCraftingDiagnosticReset;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.me.cache.CraftingGridCache;

public class GuiCraftingDiagnosticTerminal extends AEBaseGui {

    private static final boolean DEBUG_LAYOUT = false;
    private static final int GUI_WIDTH = 208;
    private static final int GUI_HEIGHT = 256;
    private static final int SMALL_VISIBLE_ROWS = 5;
    private static final int STRETCH_TOP = GuiInterfaceTerminal.HEADER_HEIGHT;
    private static final int STRETCH_BOTTOM = 124;
    private static final int SMALL_MIDDLE_HEIGHT = STRETCH_BOTTOM - STRETCH_TOP;
    private static final int FIXED_BOTTOM_HEIGHT = GUI_HEIGHT - STRETCH_BOTTOM;
    private static final int EXTRA_BOTTOM_MARGIN = 32;
    private static final long RESET_DOUBLE_CLICK_MILLIS = 500L;
    private static final Layout LAYOUT = new Layout();
    private static final CraftingGridCache.DiagnosticSortMode[] SORT_BUTTON_ORDER = {
            CraftingGridCache.DiagnosticSortMode.NAME, CraftingGridCache.DiagnosticSortMode.CRAFTED,
            CraftingGridCache.DiagnosticSortMode.CUMULATIVE_TIME, CraftingGridCache.DiagnosticSortMode.AVG_PER_SECOND };

    private final ContainerCraftingDiagnosticTerminal container;
    private final List<Row> rows = new ArrayList<>();
    private final DecimalFormat avgFormat = new DecimalFormat("#.##");
    private MEGuiTextField searchField;
    private GuiImgButton terminalStyleBox;
    private GuiImgButton sortModeBox;
    private GuiImgButton sortDirectionBox;
    private GuiImgButton resetButton;
    private int hoveredRow = -1;
    private boolean tallMode;
    private boolean suppressDebugRows;
    private int visibleRows = SMALL_VISIBLE_ROWS;
    private int lastMouseX;
    private int lastMouseY;
    private long lastResetClickAt;
    private long lastRowResetClickAt;
    private IAEStack<?> lastRowResetStack;

    public GuiCraftingDiagnosticTerminal(final InventoryPlayer inventoryPlayer, final Object host) {
        super(new ContainerCraftingDiagnosticTerminal(inventoryPlayer, (appeng.api.storage.ITerminalHost) host));
        this.container = (ContainerCraftingDiagnosticTerminal) this.inventorySlots;
        this.tallMode = AEConfig.instance.getConfigManager().getSetting(Settings.TERMINAL_STYLE) == TerminalStyle.TALL;
        this.xSize = GUI_WIDTH;
        this.recalculateScreenSize();
        this.setScrollBar(new GuiScrollbar());
    }

    @Override
    public void initGui() {
        this.recalculateScreenSize();
        super.initGui();

        this.terminalStyleBox = new GuiImgButton(
                this.guiLeft - 18,
                this.guiTop + 8,
                Settings.TERMINAL_STYLE,
                this.tallMode ? TerminalStyle.TALL : TerminalStyle.SMALL);
        this.sortModeBox = new GuiImgButton(
                this.guiLeft - 18,
                this.guiTop + 28,
                Settings.CRAFTING_SORT_BY,
                this.getSortModeAppearance());
        this.sortDirectionBox = new GuiImgButton(
                this.guiLeft - 18,
                this.guiTop + 48,
                Settings.SORT_DIRECTION,
                this.container.ascending ? SortDir.ASCENDING : SortDir.DESCENDING);
        this.resetButton = new GuiImgButton(
                this.guiLeft - 18,
                this.guiTop + 68,
                Settings.ACTIONS,
                ActionItems.RESET_STATS);
        this.buttonList.add(this.resetButton);
        this.buttonList.add(this.terminalStyleBox);
        this.buttonList.add(this.sortModeBox);
        this.buttonList.add(this.sortDirectionBox);

        this.searchField = new MEGuiTextField(LAYOUT.searchWidth, 12, "Search") {

            @Override
            public void onTextChange(final String oldText) {
                super.onTextChange(oldText);
                GuiCraftingDiagnosticTerminal.this.clearResetConfirmation();
                try {
                    NetworkHandler.instance.sendToServer(
                            new PacketValueConfig(
                                    "CraftingDiagnostics.Search",
                                    GuiCraftingDiagnosticTerminal.this.searchField.getText()));
                } catch (final IOException ignored) {}
            }
        };
        this.searchField.x = this.guiLeft + LAYOUT.searchX;
        this.searchField.y = this.guiTop + LAYOUT.searchY;

        if (DEBUG_LAYOUT && this.rows.isEmpty() && !this.suppressDebugRows) {
            this.populateDebugRows();
        }

        this.setScrollBar();
        this.repositionSlots();
        this.updateButtonLabels();
    }

    private void setScrollBar() {
        this.getScrollBar().setTop(LAYOUT.headerY - 1).setLeft(LAYOUT.scrollbarLeft)
                .setHeight(LAYOUT.rowHeight * this.visibleRows + 16);
        this.getScrollBar().setRange(0, Math.max(0, this.rows.size() - this.visibleRows), 1);
    }

    private void updateButtonLabels() {
        this.resetButton.set(ActionItems.RESET_STATS);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        try {
            if (btn == this.sortModeBox) {
                this.clearResetConfirmation();
                final int current = this.getSortButtonIndex();
                final boolean backwards = Mouse.isButtonDown(1);
                final int next = backwards ? (current + SORT_BUTTON_ORDER.length - 1) % SORT_BUTTON_ORDER.length
                        : (current + 1) % SORT_BUTTON_ORDER.length;
                this.container.sortMode = SORT_BUTTON_ORDER[next].ordinal();
                this.applyClientSort();
                NetworkHandler.instance.sendToServer(
                        new PacketValueConfig(
                                "CraftingDiagnostics.Sort",
                                Integer.toString(SORT_BUTTON_ORDER[next].ordinal())));
            } else if (btn == this.sortDirectionBox) {
                this.clearResetConfirmation();
                final boolean nextAscending = !this.container.ascending;
                this.container.ascending = nextAscending;
                this.applyClientSort();
                NetworkHandler.instance.sendToServer(
                        new PacketValueConfig("CraftingDiagnostics.Direction", nextAscending ? "1" : "0"));
            } else if (btn == this.resetButton) {
                final long now = System.currentTimeMillis();
                if (now - this.lastResetClickAt > RESET_DOUBLE_CLICK_MILLIS) {
                    this.lastResetClickAt = now;
                    return;
                }

                this.clearResetConfirmation();
                this.suppressDebugRows = true;
                this.rows.clear();
                this.setScrollBar();
                NetworkHandler.instance.sendToServer(new PacketValueConfig("CraftingDiagnostics.Reset", "1"));
            } else if (btn == this.terminalStyleBox) {
                this.tallMode = !this.tallMode;
                final TerminalStyle nextStyle = this.tallMode ? TerminalStyle.TALL : TerminalStyle.SMALL;
                AEConfig.instance.getConfigManager().putSetting(Settings.TERMINAL_STYLE, nextStyle);
                this.terminalStyleBox.set(nextStyle);
                this.setWorldAndResolution(this.mc, this.width, this.height);
            }
        } catch (final IOException ignored) {}
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        this.updateButtonLabels();
        this.terminalStyleBox.set(this.tallMode ? TerminalStyle.TALL : TerminalStyle.SMALL);
        this.sortModeBox.set(this.getSortModeAppearance());
        this.sortDirectionBox.set(this.container.ascending ? SortDir.ASCENDING : SortDir.DESCENDING);
        super.drawScreen(mouseX, mouseY, btn);
    }

    @Override
    protected void keyTyped(final char character, final int key) {
        if (!(this.searchField.textboxKeyTyped(character, key))) {
            super.keyTyped(character, key);
        }
    }

    private void clearResetConfirmation() {
        this.lastResetClickAt = 0L;
    }

    private void clearRowResetConfirmation() {
        this.lastRowResetClickAt = 0L;
        this.lastRowResetStack = null;
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) {
        super.mouseClicked(xCoord, yCoord, btn);
        this.searchField.mouseClicked(xCoord, yCoord, btn);
        this.handleRowResetClick(xCoord, yCoord, btn);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        final int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel != 0) {
            this.getScrollBar().wheel(wheel);
        }
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        final Tessellator tessellator = Tessellator.instance;
        this.bindTexture("guis/interfaceterminal.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, GuiInterfaceTerminal.HEADER_HEIGHT);
        tessellator.startDrawingQuads();
        addTexturedRectToTesselator(
                offsetX,
                offsetY + STRETCH_TOP,
                offsetX + this.xSize,
                offsetY + STRETCH_TOP + this.getMiddleHeight() + 1,
                0.0f,
                0.0f,
                STRETCH_TOP / 256.0f,
                this.xSize / 256.0f,
                STRETCH_BOTTOM / 256.0f);
        tessellator.draw();
        this.drawTexturedModalRect(
                offsetX,
                offsetY + STRETCH_TOP + this.getMiddleHeight(),
                0,
                STRETCH_BOTTOM,
                this.xSize,
                FIXED_BOTTOM_HEIGHT);
        this.drawTableBackground(offsetX, offsetY);
        this.drawSearch();
        if (DEBUG_LAYOUT) {
            this.drawDebugOverlay(offsetX, offsetY);
        }
    }

    private void drawSearch() {
        this.searchField.drawTextBox();
    }

    private void drawTableBackground(final int offsetX, final int offsetY) {
        final int left = offsetX + LAYOUT.listLeft;
        final int right = offsetX + LAYOUT.listRight;
        final int lineColor = GuiColors.CraftingDiagnosticTerminalLine.getColor();
        drawRect(left, offsetY + LAYOUT.listTop - 1, right, offsetY + LAYOUT.listTop, lineColor);
        drawRect(
                offsetX + LAYOUT.itemColumnRight,
                offsetY + LAYOUT.listTop,
                offsetX + LAYOUT.itemColumnRight + 1,
                offsetY + this.getListBottom(),
                lineColor);
        drawRect(
                offsetX + LAYOUT.qtyColumnRight,
                offsetY + LAYOUT.listTop,
                offsetX + LAYOUT.qtyColumnRight + 1,
                offsetY + this.getListBottom(),
                lineColor);
        drawRect(
                offsetX + LAYOUT.timeColumnRight,
                offsetY + LAYOUT.listTop,
                offsetX + LAYOUT.timeColumnRight + 1,
                offsetY + this.getListBottom(),
                lineColor);

        for (int i = 1; i <= this.visibleRows; i++) {
            final int y = offsetY + LAYOUT.listTop + i * LAYOUT.rowHeight - 1;
            drawRect(left, y, right, y + 1, lineColor);
        }
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRendererObj.drawString(
                this.fontRendererObj.trimStringToWidth(GuiText.CraftingDiagnosticTerminal.getLocal(), 104),
                8,
                7,
                GuiColors.CraftingTerminalTitle.getColor());

        this.fontRendererObj.drawString(
                GuiText.Item.getLocal(),
                LAYOUT.listLeft,
                LAYOUT.headerY,
                GuiColors.DefaultBlack.getColor());
        this.fontRendererObj.drawString(
                GuiText.Qty.getLocal(),
                LAYOUT.qtyHeaderX,
                LAYOUT.headerY,
                GuiColors.DefaultBlack.getColor());
        this.fontRendererObj.drawString(
                GuiText.CumulativeTime.getLocal(),
                LAYOUT.timeHeaderX,
                LAYOUT.headerY,
                GuiColors.DefaultBlack.getColor());
        this.fontRendererObj.drawString(
                GuiText.AvgPerSecond.getLocal(),
                LAYOUT.avgHeaderX,
                LAYOUT.headerY,
                GuiColors.DefaultBlack.getColor());

        this.hoveredRow = -1;
        final int start = this.getScrollBar().getCurrentScroll();
        final int end = Math.min(this.rows.size(), start + this.visibleRows);
        final int relMouseX = this.lastMouseX - offsetX;
        final int relMouseY = this.lastMouseY - offsetY;

        for (int index = start; index < end; index++) {
            final Row row = this.rows.get(index);
            final int visualIndex = index - start;
            final int rowTop = LAYOUT.listTop + visualIndex * LAYOUT.rowHeight;
            final boolean hovered = relMouseX >= LAYOUT.listLeft && relMouseX <= LAYOUT.listRight
                    && relMouseY >= rowTop
                    && relMouseY < rowTop + LAYOUT.rowHeight;

            if (hovered) {
                this.hoveredRow = index;
                drawRect(
                        LAYOUT.listLeft,
                        rowTop,
                        LAYOUT.listRight,
                        rowTop + LAYOUT.rowHeight - 1,
                        GuiColors.CraftingDiagnosticTerminalRowHover.getColor());
            }

            final ItemStack displayStack = row.getDisplayStack();
            if (displayStack != null) {
                this.drawItem(LAYOUT.listLeft + 2, rowTop + 1, displayStack);
            }

            final String name = this.fontRendererObj.trimStringToWidth(row.getDisplayName(), LAYOUT.itemTextWidth);
            this.fontRendererObj.drawString(
                    name,
                    LAYOUT.itemTextX + 1,
                    rowTop + LAYOUT.rowTextOffsetY,
                    GuiColors.DefaultBlack.getColor());
            this.drawRightAligned(row.getCompactProduced(), LAYOUT.qtyValueRightX, rowTop + LAYOUT.rowTextOffsetY);
            this.drawRightAligned(
                    row.getCompactFormattedTime(),
                    LAYOUT.timeValueRightX,
                    rowTop + LAYOUT.rowTextOffsetY);
            this.drawRightAligned(
                    row.getCompactFormattedItemsPerSecond(this.avgFormat),
                    LAYOUT.avgValueRightX,
                    rowTop + LAYOUT.rowTextOffsetY);
        }

        if (this.hoveredRow >= 0 && this.hoveredRow < this.rows.size()) {
            final Row row = this.rows.get(this.hoveredRow);
            final List<String> lines = new ArrayList<>();
            final ItemStack displayStack = row.getDisplayStack();
            if (displayStack != null) {
                lines.addAll(displayStack.getTooltip(this.mc.thePlayer, this.mc.gameSettings.advancedItemTooltips));
            } else {
                lines.add(row.getDisplayName());
            }
            lines.add(GuiText.Crafted.getLocal() + ": " + row.totalProduced);
            lines.add(GuiText.CumulativeTime.getLocal() + ": " + row.getFormattedTime());
            lines.add(GuiText.AvgPerSecond.getLocal() + ": " + row.getFormattedItemsPerSecond(this.avgFormat));
            lines.add(GuiText.Samples.getLocal() + ": " + row.sampleCount);
            lines.add(GuiText.DiagnosticObserved.getLocal());
            lines.add(GuiText.DiagnosticParallel.getLocal());
            if (row.canReset()) {
                lines.add(GuiText.DiagnosticResetItem.getLocal());
            }
            this.drawTooltip(relMouseX + 12, relMouseY, lines.toArray(new String[0]));
        }

        GL11.glColor4f(1, 1, 1, 1);
        this.getScrollBar().draw(this);

        if (DEBUG_LAYOUT) {
            this.fontRendererObj.drawString(
                    "x=" + (mouseX - this.guiLeft) + ", y=" + (mouseY - this.guiTop),
                    8,
                    this.ySize - 106,
                    0xFF00FF);
        }
    }

    private void drawRightAligned(final String text, final int rightX, final int y) {
        this.fontRendererObj.drawString(
                text,
                rightX - this.fontRendererObj.getStringWidth(text),
                y,
                GuiColors.DefaultBlack.getColor());
    }

    private void drawDebugOverlay(final int offsetX, final int offsetY) {
        drawRect(
                offsetX + LAYOUT.searchX,
                offsetY + LAYOUT.searchY,
                offsetX + LAYOUT.searchX + LAYOUT.searchWidth,
                offsetY + LAYOUT.searchY + 12,
                0x44FF0000);
        drawRect(
                offsetX + LAYOUT.listLeft,
                offsetY + LAYOUT.listTop,
                offsetX + LAYOUT.listRight,
                offsetY + this.getListBottom(),
                0x2200FF00);
        drawRect(
                offsetX + LAYOUT.itemTextX,
                offsetY + LAYOUT.listTop,
                offsetX + LAYOUT.itemTextX + LAYOUT.itemTextWidth,
                offsetY + this.getListBottom(),
                0x220000FF);
    }

    private void recalculateScreenSize() {
        int extraRows = 0;
        if (this.tallMode && this.height > 0) {
            final int availableSpace = Math.max(0, this.height - GUI_HEIGHT - EXTRA_BOTTOM_MARGIN);
            extraRows = availableSpace / LAYOUT.rowHeight;
        }

        this.visibleRows = SMALL_VISIBLE_ROWS + extraRows;
        this.ySize = GUI_HEIGHT + extraRows * LAYOUT.rowHeight;
    }

    private void applyClientSort() {
        Comparator<Row> comparator = switch (CraftingGridCache.DiagnosticSortMode.values()[this.container.sortMode]) {
            case NAME -> Comparator.comparing(Row::getDisplayName, String.CASE_INSENSITIVE_ORDER);
            case CRAFTED -> Comparator.comparingLong(row -> row.totalProduced);
            case SAMPLES -> Comparator.comparingLong(row -> row.sampleCount);
            case AVG_PER_SECOND -> Comparator.comparingDouble(Row::getItemsPerSecond);
            case CUMULATIVE_TIME -> Comparator.comparingLong(row -> row.elapsedTimeNanos);
        };

        if (!this.container.ascending) {
            comparator = comparator.reversed();
        }

        this.rows.sort(comparator);
    }

    private void handleRowResetClick(final int mouseX, final int mouseY, final int button) {
        if (button != 0 || !isCtrlKeyDown()) {
            this.clearRowResetConfirmation();
            return;
        }

        final int rowIndex = this.getRowIndexAt(mouseX, mouseY);
        if (rowIndex < 0 || rowIndex >= this.rows.size()) {
            this.clearRowResetConfirmation();
            return;
        }

        final Row row = this.rows.get(rowIndex);
        if (!row.canReset()) {
            this.clearRowResetConfirmation();
            return;
        }

        final long now = System.currentTimeMillis();
        if (now - this.lastRowResetClickAt > RESET_DOUBLE_CLICK_MILLIS || !row.matches(this.lastRowResetStack)) {
            this.lastRowResetClickAt = now;
            this.lastRowResetStack = row.copyStack();
            return;
        }

        this.clearRowResetConfirmation();
        this.clearResetConfirmation();
        this.rows.remove(rowIndex);
        this.setScrollBar();
        NetworkHandler.instance.sendToServer(new PacketCraftingDiagnosticReset(row.copyStack()));
    }

    private int getRowIndexAt(final int mouseX, final int mouseY) {
        final int relMouseX = mouseX - this.guiLeft;
        final int relMouseY = mouseY - this.guiTop;
        if (relMouseX < LAYOUT.listLeft || relMouseX > LAYOUT.listRight
                || relMouseY < LAYOUT.listTop
                || relMouseY >= this.getListBottom()) {
            return -1;
        }

        final int visualIndex = (relMouseY - LAYOUT.listTop) / LAYOUT.rowHeight;
        final int rowIndex = this.getScrollBar().getCurrentScroll() + visualIndex;
        return rowIndex < this.rows.size() ? rowIndex : -1;
    }

    private int getListBottom() {
        return LAYOUT.listTop + LAYOUT.rowHeight * this.visibleRows;
    }

    private int getMiddleHeight() {
        return SMALL_MIDDLE_HEIGHT + (this.visibleRows - SMALL_VISIBLE_ROWS) * LAYOUT.rowHeight;
    }

    private int getButtonY() {
        return this.ySize - 97;
    }

    private DiagnosticSortButton getSortModeAppearance() {
        return switch (CraftingGridCache.DiagnosticSortMode.values()[this.container.sortMode]) {
            case NAME -> DiagnosticSortButton.NAME;
            case CRAFTED, SAMPLES -> DiagnosticSortButton.QTY;
            case CUMULATIVE_TIME -> DiagnosticSortButton.TIME;
            case AVG_PER_SECOND -> DiagnosticSortButton.AVG_PER_SECOND;
        };
    }

    private int getSortButtonIndex() {
        final CraftingGridCache.DiagnosticSortMode mode = CraftingGridCache.DiagnosticSortMode
                .values()[this.container.sortMode];
        for (int i = 0; i < SORT_BUTTON_ORDER.length; i++) {
            if (SORT_BUTTON_ORDER[i] == mode) {
                return i;
            }
        }

        return 1;
    }

    private void repositionSlots() {
        for (final Object obj : this.inventorySlots.inventorySlots) {
            if (obj instanceof AppEngSlot slot) {
                slot.yDisplayPosition = this.ySize + slot.getY() - 78 - 7;
            }
        }
    }

    private void populateDebugRows() {
        this.rows.clear();
        final long[] producedValues = { 7L, 42L, 999L, 1_250L, 9_999L, 12_345L, 250_000L, 9_876_543L, 1_234_567_890L,
                3_200_000_000_000L, 9_900_000_000_000_000L, 123_456_789_012_345_678L, 88L, 5_500L, 777_777L,
                45_000_000L, 6_100_000_000L, 800_000_000_000_000L };
        final long[] millisValues = { 120L, 950L, 1_500L, 9_900L, 45_000L, 90_000L, 8L * 60L * 1000L, 42L * 60L * 1000L,
                3L * 60L * 60L * 1000L, 11L * 60L * 60L * 1000L, 26L * 60L * 60L * 1000L, 5L * 24L * 60L * 60L * 1000L,
                300L, 12_000L, 600_000L, 2L * 60L * 60L * 1000L, 18L * 60L * 60L * 1000L,
                9L * 24L * 60L * 60L * 1000L };
        for (int i = 0; i < 18; i++) {
            final Row row = new Row();
            row.stack = null;
            row.debugDisplayName = "Debug Item " + (i + 1);
            row.totalProduced = producedValues[i];
            row.elapsedTimeNanos = TimeUnit.MILLISECONDS.toNanos(millisValues[i]);
            row.sampleCount = 50L + i * 13L;
            this.rows.add(row);
        }
    }

    public void postUpdate(final List<CraftingGridCache.DiagnosticRowView> updatedRows) {
        this.rows.clear();
        for (final CraftingGridCache.DiagnosticRowView row : updatedRows) {
            this.rows.add(Row.fromPacket(row));
        }
        if (!this.rows.isEmpty()) {
            this.suppressDebugRows = false;
        } else if (DEBUG_LAYOUT && !this.suppressDebugRows) {
            this.populateDebugRows();
        }
        this.applyClientSort();
        this.setScrollBar();
    }

    private static final class Row {

        private IAEStack<?> stack;
        private String debugDisplayName;
        private long totalProduced;
        private long elapsedTimeNanos;
        private long sampleCount;

        private static Row fromPacket(final CraftingGridCache.DiagnosticRowView packetRow) {
            final Row row = new Row();
            row.stack = packetRow.stack;
            row.totalProduced = packetRow.totalProduced;
            row.elapsedTimeNanos = packetRow.elapsedTimeNanos;
            row.sampleCount = packetRow.sampleCount;
            return row;
        }

        private ItemStack getDisplayStack() {
            return this.stack == null ? null : this.stack.getItemStackForNEI();
        }

        private String getDisplayName() {
            if (this.stack != null) {
                return this.stack.getDisplayName();
            }

            return this.debugDisplayName != null ? this.debugDisplayName : GuiText.Terminal.getLocal();
        }

        private String getFormattedTime() {
            return DurationFormatUtils.formatDuration(
                    TimeUnit.MILLISECONDS.convert(this.elapsedTimeNanos, TimeUnit.NANOSECONDS),
                    GuiText.ETAFormat.getLocal());
        }

        private String getCompactProduced() {
            return formatScientificIfNeeded(this.totalProduced);
        }

        private String getCompactFormattedTime() {
            final double[] values = {
                    TimeUnit.MILLISECONDS.convert(this.elapsedTimeNanos, TimeUnit.NANOSECONDS) / 1000.0D,
                    TimeUnit.MILLISECONDS.convert(this.elapsedTimeNanos, TimeUnit.NANOSECONDS) / 60000.0D,
                    TimeUnit.MILLISECONDS.convert(this.elapsedTimeNanos, TimeUnit.NANOSECONDS) / 3_600_000.0D,
                    TimeUnit.MILLISECONDS.convert(this.elapsedTimeNanos, TimeUnit.NANOSECONDS) / 86_400_000.0D };
            final char[] units = { 's', 'm', 'h', 'd' };

            for (int i = 0; i < values.length; i++) {
                if (values[i] < 10.0D || i == values.length - 1) {
                    return formatCompactDecimal(values[i]) + units[i];
                }
            }

            return "9.9d";
        }

        private String getFormattedItemsPerSecond(final DecimalFormat format) {
            if (this.elapsedTimeNanos <= 0) {
                return "-";
            }

            final double itemsPerSecond = this.totalProduced * (double) TimeUnit.SECONDS.toNanos(1)
                    / (double) this.elapsedTimeNanos;
            return format.format(itemsPerSecond);
        }

        private double getItemsPerSecond() {
            if (this.elapsedTimeNanos <= 0) {
                return 0.0D;
            }

            return this.totalProduced * (double) TimeUnit.SECONDS.toNanos(1) / (double) this.elapsedTimeNanos;
        }

        private String getCompactFormattedItemsPerSecond(final DecimalFormat format) {
            if (this.elapsedTimeNanos <= 0) {
                return "-";
            }

            final double itemsPerSecond = this.getItemsPerSecond();
            if (itemsPerSecond >= 1000.0D) {
                return formatScientificIfNeeded(Math.round(itemsPerSecond));
            }

            return formatCompactDecimal(itemsPerSecond);
        }

        private boolean canReset() {
            return this.stack != null;
        }

        private IAEStack<?> copyStack() {
            return this.stack == null ? null : this.stack.copy();
        }

        private boolean matches(final IAEStack<?> other) {
            return this.stack != null && this.stack.equals(other);
        }

        private static String formatScientificIfNeeded(final long value) {
            if (value < 1000L) {
                return Long.toString(value);
            }

            final int exponent = (int) Math.floor(Math.log10(value));
            long mantissa = Math.round(value / Math.pow(10, exponent));
            int normalizedExponent = exponent;
            if (mantissa == 10L) {
                mantissa = 1L;
                normalizedExponent++;
            }

            return mantissa + "e" + normalizedExponent;
        }

        private static String formatCompactDecimal(final double value) {
            final long whole = (long) value;
            if (Math.abs(value - whole) < 0.1D) {
                return Long.toString(whole);
            }

            return String.format(java.util.Locale.ROOT, "%.1f", value);
        }
    }

    private static final class Layout {

        private final int searchWidth = 65;
        private final int searchX = 104;
        private final int searchY = 4;
        private final int listLeft = 10;
        private final int listTop = 28;
        private final int listRight = 168;
        private final int rowHeight = 18;
        private final int tableTop = GuiInterfaceTerminal.HEADER_HEIGHT - 14;
        private final int itemTextX = this.listLeft + 20;
        private final int itemTextWidth = 42;
        private final int itemColumnRight = itemTextX + itemTextWidth;
        private final int qtyColumnWidth = 32;
        private final int qtyColumnRight = itemColumnRight + qtyColumnWidth;
        private final int timeColumnWidth = 32;
        private final int timeColumnRight = qtyColumnRight + timeColumnWidth;
        private final int qtyValueRightX = qtyColumnRight - 2;
        private final int timeValueRightX = timeColumnRight - 2;
        private final int avgValueRightX = listRight - 5;
        private final int qtyHeaderX = itemColumnRight + 3;
        private final int timeHeaderX = qtyColumnRight + 4;
        private final int avgHeaderX = timeColumnRight + 3;
        private final int headerY = 19;
        private final int rowTextOffsetY = 4;
        private final int scrollbarLeft = 175;
    }
}
