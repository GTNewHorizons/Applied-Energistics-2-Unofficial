package appeng.client.gui.implementations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;

import appeng.api.config.ActionItems;
import appeng.api.config.HealthSortOrder;
import appeng.api.config.ReshufflePhase;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.YesNo;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.api.util.NamedDimensionalCoord;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.TypeToggleButton;
import appeng.client.render.highlighter.BlockPosHighlighter;
import appeng.container.implementations.ContainerStorageReshuffle;
import appeng.core.AELog;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.ReshuffleReport;
import appeng.helpers.ReshuffleReport.ItemChange;
import appeng.helpers.ScanTask;
import appeng.helpers.ScanTask.ScanRecord;
import appeng.tile.misc.TileStorageReshuffle;
import appeng.util.Platform;

public class GuiStorageReshuffle extends AEBaseGui {

    private static final int BOX_TOP = 38;
    private static final int BOX_HEIGHT = 78;
    private static final int BOX_LEFT = 10;
    private static final int BOX_WIDTH = 162;
    private static final int SCROLL_X = 175;
    private static final int PROGRESS_Y = 122;
    private static final int BOTTOM_Y = 137;
    private static final float TEXT_SCALE = 0.65f;

    private static final int SCAN_XO = 9;
    private static final int SCAN_YO = 39;
    private static final int SCAN_ROW_W = 160;
    private static final int SCAN_ROW_H = 22;
    private static final int SCAN_ROWS = 4;
    private static final int LOCATE_BG_SIZE = 16;
    private static final int LOCATE_X = SCAN_XO + SCAN_ROW_W - LOCATE_BG_SIZE - 3;
    private static final int LOCATE_Y_OFF = 3;

    private static final int SCROLL_SIZE = BOX_HEIGHT;
    private static final int SCROLL_SCAN_SIZE = SCROLL_SIZE + 13;
    private static final int SCROLL_TOP = BOX_TOP + 1;

    private final ContainerStorageReshuffle container;
    private final Map<TypeToggleButton, IAEStackType<?>> typeToggleButtons = new IdentityHashMap<>();

    private final GuiScrollbar reportScrollbar = new GuiScrollbar();
    private final GuiScrollbar scanScrollbar = new GuiScrollbar();
    private final GuiScrollbar healthScrollbar = new GuiScrollbar();

    private final List<ScanRecord> scanRecords = new ArrayList<>();

    private GuiImgButton reshuffleTab;
    private GuiImgButton scanModeButton;
    private GuiImgButton healthModeButton;
    private GuiImgButton locateButton;
    private GuiImgButton includeSubnetsButton;
    private GuiImgButton insertOrderButton;
    private GuiImgButton healthSortOrderButton;
    private GuiImgButton healthSortDirButton;

    private GuiButton startCancelButton;
    private GuiButton scanButton;

    private List<String> reportLines = new ArrayList<>();
    private int hoveredRow = -1;

    private final List<IAEStack<?>> itemList = new ArrayList<>();

    public GuiStorageReshuffle(final InventoryPlayer inventoryPlayer, final TileStorageReshuffle te) {
        super(new ContainerStorageReshuffle(inventoryPlayer, te));
        this.container = (ContainerStorageReshuffle) this.inventorySlots;
        this.xSize = 195;
        this.ySize = 177;
    }

    @Override
    public void initGui() {
        super.initGui();

        final int leftCol = this.guiLeft - 18;
        final int rightTabX = this.guiLeft + this.xSize + 2;

        initTypeToggleButtons(leftCol, this.guiTop + 8);

        this.includeSubnetsButton = new GuiImgButton(
                leftCol - 18,
                this.guiTop + 28,
                Settings.INCLUDE_SUBNETS,
                YesNo.YES);
        this.buttonList.add(this.includeSubnetsButton);

        this.insertOrderButton = new GuiImgButton(leftCol - 18, this.guiTop + 48, Settings.INSERT_ORDER, YesNo.YES);
        this.buttonList.add(this.insertOrderButton);

        this.reshuffleTab = new GuiImgButton(
                rightTabX,
                this.guiTop + 8,
                Settings.ACTIONS,
                ActionItems.RESHUFFLE_MODE_RESHUFFLE);
        this.buttonList.add(this.reshuffleTab);

        this.scanModeButton = new GuiImgButton(
                rightTabX,
                this.guiTop + 28,
                Settings.ACTIONS,
                ActionItems.RESHUFFLE_MODE_PARTITION);
        this.buttonList.add(this.scanModeButton);

        this.healthModeButton = new GuiImgButton(
                rightTabX,
                this.guiTop + 48,
                Settings.ACTIONS,
                ActionItems.RESHUFFLE_MODE_HEALTH);
        this.buttonList.add(this.healthModeButton);

        final int sortBtnY = this.guiTop + SCAN_YO - 20;
        this.healthSortOrderButton = new GuiImgButton(
                this.guiLeft + SCAN_XO + SCAN_ROW_W - 36,
                sortBtnY,
                Settings.CELL_HEALTH_SORT,
                this.container.healthSortOrder);
        this.buttonList.add(this.healthSortOrderButton);

        this.healthSortDirButton = new GuiImgButton(
                this.guiLeft + SCAN_XO + SCAN_ROW_W - 18,
                sortBtnY,
                Settings.SORT_DIRECTION,
                SortDir.ASCENDING);
        this.buttonList.add(this.healthSortDirButton);

        this.startCancelButton = new GuiButton(
                2,
                this.guiLeft + 8,
                this.guiTop + BOTTOM_Y + 14,
                176,
                20,
                GuiText.ReshuffleStart.getLocal());
        this.buttonList.add(this.startCancelButton);

        this.scanButton = new GuiButton(
                3,
                this.guiLeft + 8,
                this.guiTop + BOTTOM_Y + 14,
                176,
                20,
                GuiText.ReshuffleScan.getLocal());
        this.buttonList.add(this.scanButton);

        this.reportScrollbar.setLeft(SCROLL_X).setTop(SCROLL_TOP).setHeight(SCROLL_SIZE);

        this.scanScrollbar.setLeft(SCROLL_X).setTop(SCROLL_TOP).setHeight(SCROLL_SCAN_SIZE);
        this.scanScrollbar.setRange(0, 0, 1);

        this.healthScrollbar.setLeft(SCROLL_X).setTop(SCROLL_TOP).setHeight(SCROLL_SCAN_SIZE);
        this.healthScrollbar.setRange(0, 0, 1);

        this.locateButton = new GuiImgButton(0, 0, Settings.ACTIONS, ActionItems.RESHUFFLE_MODE_LOCATE);

        this.setScrollBar(this.reportScrollbar);
    }

    private void initTypeToggleButtons(final int x, final int yStart) {
        this.typeToggleButtons.clear();

        int y = yStart;
        for (final IAEStackType<?> type : AEStackTypeRegistry.getSortedTypes()) {
            final ResourceLocation texture = type.getButtonTexture();
            final IIcon icon = type.getButtonIcon();
            if (texture == null || icon == null) continue;

            final TypeToggleButton btn = new TypeToggleButton(x, y, texture, icon, type.getDisplayName());
            btn.setEnabled(this.container.getTypeFilters().isEnabled(type));
            this.typeToggleButtons.put(btn, type);
            this.buttonList.add(btn);

            y += 20;
        }
    }

    public void onSettingsUpdated() {
        if (this.includeSubnetsButton != null) {
            this.includeSubnetsButton.set(this.container.includeSubnets);
        }

        if (this.insertOrderButton != null) {
            this.insertOrderButton.set(this.container.insertOrder);
        }

        if (this.healthSortOrderButton != null) {
            this.healthSortOrderButton.set(this.container.healthSortOrder);
            this.sortHealthEntries();
        }

        if (this.healthSortDirButton != null) {
            this.healthSortDirButton.set(this.container.healthSortDir);
            this.sortHealthEntries();
        }
    }

    public void onUpdateTypeFilters() {
        for (final Map.Entry<TypeToggleButton, IAEStackType<?>> entry : this.typeToggleButtons.entrySet()) {
            final boolean enabled = this.container.getTypeFilters().isEnabled(entry.getValue());
            entry.getKey().setEnabled(enabled);
        }
    }

    public void onReportUpdated() {
        if (this.container.report == null) return;
        this.reportLines = this.generateReportLines();
        this.reportScrollbar.setRange(0, Math.max(0, this.reportLines.size() - visibleReportLines()), 1);
        if (!this.container.isScanMode()) {
            this.setScrollBar(this.reportScrollbar);
        }
    }

    public void onScanUpdated() {
        this.itemList.clear();
        this.scanRecords.clear();

        final ScanTask scanTask = this.container.scanData;
        if (scanTask == null) return;
        scanTask.getScanData().forEach((s, partitions) -> partitions.forEach((key, value) -> {
            key.setStackSize(value.size());
            this.itemList.add(key);
        }));

        this.scanRecords.addAll(scanTask.getScanCellsData());
        this.sortHealthEntries();

        this.scanScrollbar.setRange(0, Math.max(0, this.itemList.size() - SCAN_ROWS), 1);
        this.healthScrollbar.setRange(0, Math.max(0, this.scanRecords.size() - SCAN_ROWS), 1);
    }

    private double fillPct(ScanRecord cr) {
        return cr.bytesTotal > 0 ? (cr.bytesUsed * 100.0 / cr.bytesTotal) : 0.0;
    }

    private void sortHealthEntries() {
        final HealthSortOrder order = this.container.healthSortOrder;
        final int dir = SortDir.ASCENDING == this.container.healthSortDir ? 1 : -1;
        this.scanRecords.sort((a, b) -> {
            final double va = order == HealthSortOrder.FILL_PCT ? this.fillPct(a) : (double) a.bytesTotal;
            final double vb = order == HealthSortOrder.FILL_PCT ? this.fillPct(b) : (double) b.bytesTotal;
            return dir * Double.compare(vb, va);
        });
    }

    private int visibleReportLines() {
        return (int) (BOX_HEIGHT / (this.fontRendererObj.FONT_HEIGHT * TEXT_SCALE + 1));
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        if (btn instanceof final TypeToggleButton tbtn) {
            final IAEStackType<?> type = this.typeToggleButtons.get(tbtn);
            if (type != null) {
                final boolean next = this.container.getTypeFilters().toggle(type);
                tbtn.setEnabled(next);

                try {
                    NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.TypeFilter", type.getId()));
                } catch (final IOException e) {
                    AELog.debug(e);
                }
            }
            return;
        }

        try {
            if (btn == this.reshuffleTab) {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.View", "reshuffle"));
            } else if (btn == this.scanModeButton) {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.View", "scan"));
            } else if (btn == this.healthModeButton) {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.View", "health"));
            } else if (btn == this.startCancelButton) {
                if (this.container.reportRunning()) {
                    NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.Cancel", ""));
                } else {
                    NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.Start", ""));
                }
            } else if (btn == this.scanButton) {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.Scan", ""));
            }
        } catch (final IOException e) {
            AELog.debug(e);
        }

        if (!(btn instanceof GuiImgButton iBtn) || iBtn.getSetting() == Settings.ACTIONS) return;

        final Enum cv = iBtn.getCurrentValue();
        final boolean backwards = Mouse.isButtonDown(1);
        final Enum next = Platform.rotateEnum(cv, backwards, iBtn.getSetting().getPossibleValues());

        try {
            NetworkHandler.instance.sendToServer(new PacketValueConfig(iBtn.getSetting().name(), next.name()));
        } catch (final IOException e) {
            AELog.debug(e);
        }

        iBtn.set(next);
    }

    @Override
    protected void mouseClicked(final int mouseX, final int mouseY, final int mouseButton) {
        if (mouseButton == 0) {
            final int relX = mouseX - this.guiLeft;
            final int relY = mouseY - this.guiTop;

            if ((this.container.isScanMode() || this.container.isHealthMode()) && this.hoveredRow != -1) {
                final int row = (relY - SCAN_YO) / SCAN_ROW_H;
                if (isLocateButton(relX, relY, row)) {
                    final int currentScroll = this.container.isScanMode() ? this.scanScrollbar.getCurrentScroll()
                            : this.healthScrollbar.getCurrentScroll();
                    final int currentListSize = this.container.isScanMode() ? this.itemList.size()
                            : this.scanRecords.size();

                    final int absRow = ((relY - SCAN_YO) / SCAN_ROW_H) + currentScroll;

                    if (relX >= LOCATE_X && absRow < currentListSize) {
                        final List<ScanRecord> scanList;
                        final String highlightGroupName;
                        if (this.container.isScanMode()) {
                            final IAEStack<?> aes = this.itemList.get(absRow);
                            final Map<IAEStackType<?>, Map<IAEStack<?>, List<ScanRecord>>> scanData = this.container
                                    .getScanData().getScanData();
                            scanList = scanData.get(aes.getStackType()).get(aes);
                            highlightGroupName = aes.getDisplayName();
                        } else {
                            final ScanRecord e = this.scanRecords.get(absRow);
                            scanList = Collections.singletonList(e);
                            highlightGroupName = e.itemStack.getDisplayName();
                        }

                        final Map<NamedDimensionalCoord, String[]> ndcm = new HashMap<>();
                        final String[] highlightParameters = new String[] {
                                PlayerMessages.MachineHighlightedNamed.getUnlocalized(),
                                PlayerMessages.MachineInOtherDimNamed.getUnlocalized() };
                        for (final ScanRecord scanRecord : scanList) {
                            ndcm.put(
                                    new NamedDimensionalCoord(
                                            scanRecord.x,
                                            scanRecord.y,
                                            scanRecord.z,
                                            scanRecord.dim,
                                            scanRecord.itemStack.getDisplayName()),
                                    highlightParameters);
                        }

                        BlockPosHighlighter.highlightNamedBlocks(this.mc.thePlayer, ndcm, highlightGroupName);
                        this.mc.thePlayer.closeScreen();
                        return;
                    }
                }
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        this.hoveredRow = -1;

        final boolean scanMode = this.container.isScanMode();
        final boolean healthMode = this.container.isHealthMode();
        final boolean reshuffleMode = !scanMode && !healthMode;

        this.includeSubnetsButton.visible = reshuffleMode;
        this.insertOrderButton.visible = reshuffleMode;
        for (final TypeToggleButton tb : this.typeToggleButtons.keySet()) {
            tb.visible = reshuffleMode;
        }

        if (this.startCancelButton != null) this.startCancelButton.visible = reshuffleMode;
        if (this.scanButton != null) this.scanButton.visible = scanMode || healthMode;

        this.healthSortOrderButton.visible = healthMode;
        this.healthSortDirButton.visible = healthMode;

        if (scanMode) {
            this.drawMode(mouseX, mouseY, this.scanScrollbar.getCurrentScroll(), this.itemList.size());
        } else if (healthMode) {
            this.drawMode(mouseX, mouseY, this.healthScrollbar.getCurrentScroll(), this.scanRecords.size());
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawMode(final int mouseX, final int mouseY, final int scrollSize, final int listSize) {
        for (int i = 0; i < SCAN_ROWS; i++) {
            final int absRow = i + scrollSize;
            if (absRow >= listSize) break;
            if (isScanRow(mouseX, mouseY, i)) {
                this.hoveredRow = absRow;
                break;
            }
        }
    }

    private boolean isScanRow(final int mouseX, final int mouseY, final int i) {
        final int bx = this.guiLeft + SCAN_XO;
        final int by = this.guiTop + SCAN_YO;
        final int rowY = by + i * SCAN_ROW_H;
        return mouseX >= bx && mouseX < bx + SCAN_ROW_W && mouseY >= rowY && mouseY < rowY + SCAN_ROW_H;
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        final boolean scanMode = this.container.isScanMode();
        final boolean healthMode = this.container.isHealthMode();

        if (scanMode || healthMode) {
            drawScanContent(mouseX, mouseY);
        } else {
            drawReshuffleContent(offsetX, offsetY, mouseX, mouseY);
        }
    }

    private static @NotNull List<String> getStackPartitionsLines(IAEStack<?> stack, List<ScanRecord> partitionsList) {
        final List<String> lines = new ArrayList<>();
        lines.add(GuiText.ReshuffleScanDuplicatePartitionStackNameStyle.getLocal() + stack.getDisplayName());
        for (int i = 0; i < partitionsList.size(); i++) {
            final ScanRecord scanRecord = partitionsList.get(i);
            final GuiText locText = scanRecord.slot == -1 ? GuiText.ReshuffleTooltipCoordsNoSlot
                    : GuiText.ReshuffleTooltipCoords;

            lines.add(GuiText.ReshuffleScanDuplicatePartition.getLocal() + " " + (i + 1));
            lines.add(
                    GuiText.ReshuffleScanDuplicatePartitionTargetNameColor.getLocal()
                            + scanRecord.itemStack.getDisplayName());
            lines.add(locText.getLocal(scanRecord.x, scanRecord.y, scanRecord.z, scanRecord.dim, scanRecord.slot));
        }
        return lines;
    }

    private void drawReshuffleContent(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRendererObj.drawString(
                this.getGuiDisplayName(GuiText.StorageReshuffle.getLocal()),
                8,
                6,
                GuiColors.ReshuffleTitle.getColor());

        final String statusLabel = GuiText.ReshuffleStatusLabel.getLocal() + " ";
        final ReshuffleReport report = this.container.getReshuffleReport();

        final String statusValue;
        final int statusColor;

        if (report == null) {
            statusValue = GuiText.ReshuffleStatusIdle.getLocal();
            statusColor = GuiColors.ReshuffleStatusIdle.getColor();
        } else {
            switch (this.container.getReshuffleReport().phase) {
                case BEFORE_SNAPSHOT -> {
                    statusValue = GuiText.ReshuffleStatusBeforeSnapshot.getLocal();
                    statusColor = GuiColors.ReshuffleStatusBeforeSnapshot.getColor();
                }
                case AFTER_SNAPSHOT -> {
                    statusValue = GuiText.ReshuffleStatusAfterSnapshot.getLocal();
                    statusColor = GuiColors.ReshuffleStatusAfterSnapshot.getColor();
                }
                case EXTRACTION -> {
                    statusValue = GuiText.ReshuffleStatusExtracting.getLocal();
                    statusColor = GuiColors.ReshuffleStatusExtracting.getColor();
                }
                case INJECTION -> {
                    statusValue = GuiText.ReshuffleStatusInjecting.getLocal();
                    statusColor = GuiColors.ReshuffleStatusInjecting.getColor();
                }
                case DONE -> {
                    statusValue = GuiText.ReshuffleStatusComplete.getLocal();
                    statusColor = GuiColors.ReshuffleStatusComplete.getColor();
                }
                case CANCEL -> {
                    statusValue = GuiText.ReshuffleStatusCancelled.getLocal();
                    statusColor = GuiColors.ReshuffleStatusCancelled.getColor();
                }
                default -> {
                    statusValue = GuiText.ReshuffleStatusFailed.getLocal();
                    statusColor = GuiColors.ReshuffleStatusFailed.getColor();
                }
            }
        }

        final int labelW = this.fontRendererObj.getStringWidth(statusLabel);
        this.fontRendererObj.drawString(statusLabel, 8, 18, GuiColors.ReshuffleTitle.getColor());
        this.fontRendererObj.drawString(statusValue, 8 + labelW, 18, statusColor);

        if (report == null) return;
        final int displayProcessed = report.injectedTypes;
        final int displayTotal = report.extractedTypes;

        this.fontRendererObj.drawString(
                GuiText.ReshuffleTotalTypes.getLocal(displayProcessed, displayTotal),
                BOX_LEFT,
                BOTTOM_Y - 2,
                GuiColors.ReshuffleTotalItems.getColor());

        GL11.glPushMatrix();
        GL11.glScalef(0.6f, 0.6f, 1.0f);
        this.fontRendererObj.drawString(
                GuiText.ReshuffleReport.getLocal(),
                (int) (BOX_LEFT / 0.6f),
                (int) ((BOX_TOP - 9) / 0.6f),
                GuiColors.ReshuffleTitle.getColor());
        GL11.glPopMatrix();

        final int visLines = visibleReportLines();
        final int scroll = this.reportScrollbar.getCurrentScroll();
        final float lineH = this.fontRendererObj.FONT_HEIGHT * TEXT_SCALE + 1;

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        final int scale = new ScaledResolution(this.mc, this.mc.displayWidth, this.mc.displayHeight).getScaleFactor();
        GL11.glScissor(
                (this.guiLeft + BOX_LEFT) * scale,
                this.mc.displayHeight - (this.guiTop + BOX_TOP + BOX_HEIGHT) * scale,
                BOX_WIDTH * scale,
                BOX_HEIGHT * scale);

        GL11.glPushMatrix();
        GL11.glScalef(TEXT_SCALE, TEXT_SCALE, 1.0f);
        final float inv = 1.0f / TEXT_SCALE;
        for (int i = 0; i < visLines && (scroll + i) < this.reportLines.size(); i++) {
            this.fontRendererObj.drawString(
                    this.reportLines.get(scroll + i),
                    (int) (BOX_LEFT * inv),
                    (int) ((BOX_TOP + 2 + i * lineH) * inv),
                    GuiColors.ReshuffleReport.getColor());
        }
        GL11.glPopMatrix();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        final int barX = BOX_LEFT;
        final int barY = PROGRESS_Y;
        final int barW = BOX_WIDTH - 30;
        drawRect(barX - 1, barY - 1, barX + barW + 1, barY + 7, GuiColors.ReshuffleProgressBorder.getColor());
        drawRect(barX, barY, barX + barW, barY + 6, GuiColors.ReshuffleProgressBackground.getColor());

        final int progressPercent = report.getProgressPercent();
        if (progressPercent > 0) {
            final int fill = (int) Math.floor(progressPercent * barW / 100.0);
            final int alpha = 255 - progressPercent * 255 / 100;
            this.drawGradientRect(
                    barX,
                    barY,
                    barX + fill,
                    barY + 6,
                    GuiColors.ReshuffleProgressFillStart.getColor(),
                    (GuiColors.ReshuffleProgressFillEnd.getColor() & 0xFFFFFF00) | alpha);
            drawRect(barX + fill - 1, barY, barX + fill + 1, barY + 6, GuiColors.ReshuffleProgressMarker.getColor());
        }
        this.fontRendererObj
                .drawString(progressPercent + "%", barX + barW + 3, barY, GuiColors.ReshuffleTitle.getColor());

        if (this.startCancelButton != null) {
            this.startCancelButton.displayString = this.container.reportRunning() ? GuiText.ReshuffleCancel.getLocal()
                    : GuiText.ReshuffleStart.getLocal();
        }

        if (this.startCancelButton != null && this.startCancelButton.visible) {
            final int bx = this.startCancelButton.xPosition - offsetX;
            final int by = this.startCancelButton.yPosition - offsetY;
            if (mouseX - offsetX >= bx && mouseX - offsetX < bx + this.startCancelButton.width
                    && mouseY - offsetY >= by
                    && mouseY - offsetY < by + this.startCancelButton.height) {
                this.drawTooltip(
                        mouseX - this.guiLeft,
                        mouseY - this.guiTop,
                        this.container.reportRunning() ? GuiText.ReshuffleCancel.getLocal()
                                : GuiText.ReshuffleStart.getLocal());
            }
        }

    }

    private void drawScanContent(final int mouseX, final int mouseY) {
        final boolean partition = this.container.isScanMode();
        if (partition) {
            final int total = this.itemList.size();
            this.fontRendererObj.drawString(
                    GuiText.ReshufflePartitionScanner.getLocal(),
                    8,
                    6,
                    GuiColors.ReshuffleTitle.getColor());

            if (this.container.scanData != null) {
                if (total == 0) {
                    this.fontRendererObj.drawString(
                            GuiText.ReshuffleScanEmpty.getLocal(),
                            SCAN_XO + 4,
                            SCAN_YO + SCAN_ROWS * SCAN_ROW_H + 10,
                            GuiColors.DefaultBlack.getColor());
                    return;
                } else {
                    this.fontRendererObj.drawString(
                            GuiText.ReshuffleScanDuplicatesTitle.getLocal() + " " + total,
                            SCAN_XO,
                            SCAN_YO - 12,
                            GuiColors.ReshuffleTitle.getColor());
                }
            }
        } else {
            this.fontRendererObj
                    .drawString(GuiText.ReshuffleHealthTitle.getLocal(), 8, 6, GuiColors.ReshuffleTitle.getColor());
            if (this.container.scanData != null && this.scanRecords.isEmpty()) {
                this.fontRendererObj.drawString(
                        GuiText.ReshuffleReportNoMatchingCells.getLocal(),
                        SCAN_XO + 4,
                        SCAN_YO + SCAN_ROWS * SCAN_ROW_H + 10,
                        GuiColors.DefaultBlack.getColor());
                return;
            }
        }

        final int listSize;
        final int currentScrollSize;
        if (partition) {
            listSize = this.itemList.size();
            currentScrollSize = this.scanScrollbar.getCurrentScroll();
        } else {
            listSize = this.scanRecords.size();
            currentScrollSize = this.healthScrollbar.getCurrentScroll();
        }
        for (int i = 0; i < SCAN_ROWS; i++) {
            final int absRow = i + currentScrollSize;
            if (i >= listSize) break;

            final boolean isHovered = this.hoveredRow == absRow;
            final int rowY = i + SCAN_YO + i * SCAN_ROW_H;

            if (isHovered) {
                drawRect(
                        SCAN_XO,
                        rowY,
                        SCAN_XO + SCAN_ROW_W,
                        rowY + SCAN_ROW_H,
                        GuiColors.ReshuffleScanRowHover.getColor());
            }

            final int maxNameW = (int) ((LOCATE_X - SCAN_XO - 22 - 2) / TEXT_SCALE);
            final float inv = 1.0f / TEXT_SCALE;

            String name;
            final int networkStatusItemCountX;
            final int networkStatusItemCountY;
            final String size;

            if (partition) {
                final IAEStack<?> aes = this.itemList.get(absRow);
                aes.drawInGui(Minecraft.getMinecraft(), SCAN_XO + 1, rowY + 3);
                name = aes.getDisplayName();
                size = aes.getStackSize() + "x";
                networkStatusItemCountX = (int) ((SCAN_XO + 20) * inv);
                networkStatusItemCountY = (int) ((rowY + 14) * inv);

                GL11.glPushMatrix();
                GL11.glScalef(TEXT_SCALE, TEXT_SCALE, 1.0f);
            } else {
                final ScanRecord e = this.scanRecords.get(absRow);
                final int barLeft = SCAN_XO + 20;
                final int barTop = rowY + 17;
                final int barW = 60;
                final double pct = fillPct(e);
                final int fillW = (int) Math.round(pct * barW / 100.0);
                final String pctStr = String.format("%.2f%%", pct);
                final int barColor = pct >= 90.0 ? GuiColors.CellHealthCrit.getColor()
                        : pct >= 75.0 ? GuiColors.CellHealthWarn.getColor() : GuiColors.CellHealthOk.getColor();

                this.drawItem(SCAN_XO + 1, rowY + 3, e.itemStack);

                drawRect(barLeft, barTop, barLeft + barW, barTop + 3, GuiColors.CellHealthBarBackground.getColor());

                if (fillW > 0) drawRect(barLeft, barTop, barLeft + fillW, barTop + 3, barColor);

                size = e.typesUsed + "/" + e.typesTotal + " " + GuiText.Types.getLocal();
                name = e.itemStack.getDisplayName();
                networkStatusItemCountX = (int) ((barLeft + barW + 4) * inv);
                networkStatusItemCountY = (int) (int) ((rowY + 11) * inv);

                GL11.glPushMatrix();
                GL11.glScalef(TEXT_SCALE, TEXT_SCALE, 1.0f);

                this.fontRendererObj
                        .drawString(pctStr, (int) ((SCAN_XO + 20) * inv), (int) ((rowY + 11) * inv), barColor);
            }

            while (!name.isEmpty() && this.fontRendererObj.getStringWidth(name) > maxNameW) {
                name = name.substring(0, name.length() - 1);
            }

            this.fontRendererObj.drawString(
                    name,
                    (int) ((SCAN_XO + 20) * inv),
                    (int) ((rowY + 3) * inv),
                    GuiColors.DefaultBlack.getColor());

            this.fontRendererObj.drawString(
                    size,
                    networkStatusItemCountX,
                    networkStatusItemCountY,
                    GuiColors.NetworkStatusItemCount.getColor());

            GL11.glPopMatrix();

            this.locateButton.xPosition = LOCATE_X;
            this.locateButton.yPosition = rowY + LOCATE_Y_OFF;
            this.locateButton.drawButton(this.mc, mouseX - this.guiLeft, mouseY - this.guiTop);
        }

        if (this.hoveredRow != -1) {
            final int relX = mouseX - this.guiLeft;
            final int relY = mouseY - this.guiTop;
            final int row = (relY - SCAN_YO) / SCAN_ROW_H;

            if (isLocateButton(relX, relY, row)) {
                this.drawTooltip(mouseX - this.guiLeft, mouseY - this.guiTop, GuiText.ReshuffleLocate.getLocal());
            } else {
                final List<String> lines;
                if (partition) {
                    final IAEStack<?> stack = this.itemList.get(this.hoveredRow);
                    final Map<IAEStackType<?>, Map<IAEStack<?>, List<ScanRecord>>> partitions = this.container
                            .getScanData().getScanData();
                    final List<ScanRecord> partitionsList = partitions.get(stack.getStackType()).get(stack);
                    lines = getStackPartitionsLines(stack, partitionsList);
                } else {
                    final ScanRecord e = this.scanRecords.get(this.hoveredRow);
                    final String fillPct = String.format("%.2f%%", fillPct(e));
                    lines = new ArrayList<>();
                    lines.add(EnumChatFormatting.WHITE + e.itemStack.getDisplayName());
                    lines.add(
                            GuiText.ReshuffleHealthTooltipBytes.getLocal(
                                    Platform.formatByteDouble(e.bytesUsed),
                                    Platform.formatByteDouble(e.bytesTotal),
                                    fillPct));
                    lines.add(GuiText.ReshuffleHealthTooltipTypes.getLocal(e.typesUsed, e.typesTotal));
                    if (!e.topStoredItems.isEmpty()) {
                        String typePrefix = " " + EnumChatFormatting.GRAY
                                + "["
                                + e.topStoredItems.get(0).getStackType().getDisplayName()
                                + "]";
                        lines.add(GuiText.ReshuffleHealthTopItems.getLocal() + typePrefix);
                        e.topStoredItems.forEach(
                                aes -> {
                                    lines.add(
                                            GuiText.ReshuffleHealthTopItemEntry
                                                    .getLocal(aes.getDisplayName(), fmt(aes.getStackSize())));
                                });
                    }
                    lines.add(GuiText.ReshuffleTooltipCoords.getLocal(e.x, e.y, e.z, e.dim, e.slot));
                }
                this.drawTooltip(mouseX - this.guiLeft, mouseY - this.guiTop, String.join("\n", lines));
            }
        }
    }

    private boolean isLocateButton(final int relX, final int relY, final int row) {
        final int locY = row + SCAN_YO + row * SCAN_ROW_H + LOCATE_Y_OFF;
        return relX >= LOCATE_X && relX < LOCATE_X + LOCATE_BG_SIZE && relY >= locY && relY < locY + LOCATE_BG_SIZE;
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        if (this.container.isHealthMode()) {
            this.setScrollBar(this.healthScrollbar);
        } else if (this.container.isScanMode()) {
            this.setScrollBar(this.scanScrollbar);
        } else {
            this.setScrollBar(this.reportScrollbar);
        }
        if (this.container.isScanMode() || this.container.isHealthMode()) {
            this.bindTexture("guis/networkscan.png");
        } else {
            this.bindTexture("guis/networkstatus.png");
        }
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }

    // Reshuffle section
    private static final int LINE_WIDTH = 36;
    private static final String MAIN = GuiText.ReshuffleReportTextColorMain.getLocal();
    private static final String VALUES = GuiText.ReshuffleReportTextColorValues.getLocal();
    private static final String POSITIVE = GuiText.ReshuffleReportTextColorPositive.getLocal();
    private static final String NEGATIVE = GuiText.ReshuffleReportTextColorNegative.getLocal();
    private static final String NEUTRAL = GuiText.ReshuffleReportTextColorNeutral.getLocal();
    private static final String WARNING = GuiText.ReshuffleReportTextColorWarning.getLocal();

    public List<String> generateReportLines() {
        final ReshuffleReport r = this.container.report;
        List<String> lines = new ArrayList<>();
        long durationMs = (r.endTime != 0 ? r.endTime : System.currentTimeMillis()) - r.startTime;

        lines.add(MAIN + centerTitle(GuiText.ReshuffleReportTitle.getLocal()));
        lines.add(
                MAIN + GuiText.ReshuffleReportMode.getLocal()
                        + " "
                        + VALUES
                        + buildModeString()
                        + "  "
                        + MAIN
                        + GuiText.ReshuffleReportTime.getLocal()
                        + " "
                        + VALUES
                        + String.format("%.2fs", durationMs / 1000.0));

        final String subnetStr = r.includeSubnets ? POSITIVE + GuiText.ReshuffleReportSubnetsOn.getLocal()
                : NEUTRAL + GuiText.ReshuffleReportSubnetsOff.getLocal();
        lines.add(MAIN + GuiText.ReshuffleReportSubnetsLabel.getLocal() + " " + subnetStr);
        lines.add("");
        lines.add(POSITIVE + GuiText.ReshuffleReportExtracted.getLocal() + " " + VALUES + fmt(r.extractedItems));
        lines.add(POSITIVE + GuiText.ReshuffleReportInjected.getLocal() + " " + VALUES + fmt(r.injectedItems));

        long typeDelta = r.afterTypes - r.beforeTypes;
        double stackDelta = r.afterItems - r.beforeItems;
        lines.add(
                MAIN + GuiText.ReshuffleReportLabelTypes.getLocal()
                        + " "
                        + fmt(r.beforeTypes)
                        + VALUES
                        + " → "
                        + fmt(r.afterTypes)
                        + " "
                        + diffColor(typeDelta)
                        + "("
                        + fmt(typeDelta)
                        + ")");
        lines.add(
                MAIN + GuiText.ReshuffleReportLabelStacks.getLocal()
                        + " "
                        + fmt(r.beforeItems)
                        + VALUES
                        + " → "
                        + fmt(r.afterItems)
                        + " "
                        + diffColor(stackDelta)
                        + "("
                        + fmt(stackDelta)
                        + ")");

        if (!r.cantExtract.isEmpty()) {
            lines.add(
                    (WARNING + GuiText.ReshuffleReportSectionCantExtract.getLocal()
                            + " "
                            + VALUES
                            + fmt(r.cantExtract.size())));
            this.addItemList(r.cantExtract, lines);
        }

        if (!r.cantInject.isEmpty()) {
            lines.add(
                    (WARNING + GuiText.ReshuffleReportSectionCantInject.getLocal()
                            + " "
                            + VALUES
                            + fmt(r.cantInject.size())));
            this.addItemList(r.cantInject, lines);
        }

        if (r.phase == ReshufflePhase.DONE) {
            lines.add("");
            if (stackDelta != 0) {
                lines.add(
                        WARNING + GuiText.ReshuffleReportNetChanged.getLocal()
                                + " "
                                + diffColor(stackDelta)
                                + fmt(stackDelta));

                r.lostItems.forEach(item -> lines.addAll(buildItemLines(item)));

                r.gainedItems.forEach(item -> lines.addAll(buildItemLines(item)));
            } else {
                lines.add(POSITIVE + GuiText.ReshuffleReportIntegrityOk.getLocal());
            }
        }

        lines.add(MAIN + repeatEquals(LINE_WIDTH));
        return lines;
    }

    private void addItemList(final IItemList<IAEStack<?>> list, final List<String> lines) {
        for (IAEStack<?> stack : list) {
            lines.add(
                    WARNING + "• " + VALUES + stack.getDisplayName() + " " + NEUTRAL + "x" + fmt(stack.getStackSize()));
        }
    }

    private List<String> buildItemLines(ItemChange c) {
        final List<String> out = new ArrayList<>();
        final String name = c.stack.getDisplayName();
        final double delta = c.difference;
        final String sight = delta > 0 ? "+" : "";
        final String color = diffColor(delta);
        out.add(color + "• " + VALUES + name + " " + color + sight + fmt(delta));
        out.add(NEUTRAL + "  (" + fmt(c.beforeCount) + " → " + fmt(c.afterCount) + ")");
        return out;
    }

    private String buildModeString() {
        if (!this.container.typeFilters.getEnabledTypes().iterator().hasNext())
            return GuiText.ReshuffleReportModeNone.getLocal();
        StringBuilder sb = new StringBuilder();
        for (IAEStackType<?> type : this.container.typeFilters.getEnabledTypes()) {
            if (sb.length() > 0) sb.append('/');
            sb.append(type.getDisplayName());
        }
        return sb.toString();
    }

    private static String fmt(double v) {
        return NumberFormatUtil.formatNumber(v);
    }

    private static String diffColor(double delta) {
        if (delta > 0) return POSITIVE;
        if (delta < 0) return NEGATIVE;
        return NEUTRAL;
    }

    private static String centerTitle(String text) {
        int totalPad = Math.max(0, LINE_WIDTH - text.length());
        int left = totalPad / 2, right = totalPad - left;
        return repeatEquals(left) + text + repeatEquals(right);
    }

    private static String repeatEquals(int count) {
        if (count <= 0) return "";
        char[] buf = new char[count];
        Arrays.fill(buf, '=');
        return new String(buf);
    }
}
