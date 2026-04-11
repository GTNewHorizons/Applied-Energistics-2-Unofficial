package appeng.client.gui.implementations;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.NamedDimensionalCoord;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiToggleButton;
import appeng.client.gui.widgets.TypeToggleButton;
import appeng.client.render.highlighter.BlockPosHighlighter;
import appeng.client.texture.ExtraBlockTextures;
import appeng.container.implementations.ContainerStorageReshuffle;
import appeng.core.AELog;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.PartitionScanTask;
import appeng.helpers.PartitionScanTask.PartitionRecord;
import appeng.helpers.ReshuffleReport;
import appeng.helpers.ReshuffleReport.ItemChange;
import appeng.tile.misc.TileStorageReshuffle;
import appeng.util.ReadableNumberConverter;

public class GuiStorageReshuffle extends AEBaseGui {

    private static final int BOX_TOP = 38;
    private static final int BOX_HEIGHT = 80;
    private static final int BOX_LEFT = 10;
    private static final int BOX_WIDTH = 162;
    private static final int SCROLL_X = 175;
    private static final int PROGRESS_Y = 126;
    private static final int BOTTOM_Y = 141;
    private static final float TEXT_SCALE = 0.65f;
    private static final int LOCATE_ICON_INDEX = 8 * 16 + 10;

    private final ContainerStorageReshuffle container;
    private final Map<TypeToggleButton, IAEStackType<?>> typeToggleButtons = new IdentityHashMap<>();

    private final GuiScrollbar reportScrollbar = new GuiScrollbar();
    private final GuiScrollbar scanScrollbar = new GuiScrollbar();

    private GuiImgButton voidProtectionButton;
    private GuiToggleButton reshuffleTab;
    private GuiToggleButton scanModeButton;
    private GuiButton startCancelButton;
    private GuiButton scanButton;

    private List<String> reportLines = new ArrayList<>();
    private int hoveredRow = -1;

    private final List<IAEStack<?>> itemList = new ArrayList<>();

    public GuiStorageReshuffle(final InventoryPlayer inventoryPlayer, final TileStorageReshuffle te) {
        super(new ContainerStorageReshuffle(inventoryPlayer, te));
        this.container = (ContainerStorageReshuffle) this.inventorySlots;
        this.xSize = 195;
        this.ySize = 187;
    }

    @Override
    public void initGui() {
        super.initGui();

        final int leftCol = this.guiLeft - 18;
        final int rightTabX = this.guiLeft + this.xSize + 2;

        initTypeToggleButtons(leftCol, this.guiTop + 8);

        this.voidProtectionButton = new GuiImgButton(
                leftCol - 18,
                this.guiTop + 8,
                Settings.VOID_PROTECTION,
                this.container.voidProtection ? YesNo.YES : YesNo.NO);
        this.buttonList.add(this.voidProtectionButton);

        this.reshuffleTab = new GuiToggleButton(
                rightTabX,
                this.guiTop + 8,
                8 * 16 + 9,
                8 * 16 + 9,
                GuiText.ReshuffleTabReshuffle.getUnlocalized(),
                GuiText.ReshuffleTabReshuffleHint.getUnlocalized());
        this.buttonList.add(this.reshuffleTab);

        this.scanModeButton = new GuiToggleButton(
                rightTabX,
                this.guiTop + 28,
                8 * 16 + 8,
                8 * 16 + 8,
                GuiText.ReshuffleTabScan.getUnlocalized(),
                GuiText.ReshuffleTabScanHint.getUnlocalized());
        this.buttonList.add(this.scanModeButton);

        this.startCancelButton = new GuiButton(
                2,
                this.guiLeft + 8,
                this.guiTop + BOTTOM_Y + 10,
                90,
                20,
                GuiText.ReshuffleStart.getLocal());
        this.buttonList.add(this.startCancelButton);

        this.scanButton = new GuiButton(
                3,
                this.guiLeft + 102,
                this.guiTop + BOTTOM_Y + 10,
                84,
                20,
                GuiText.ReshuffleScan.getLocal());
        this.buttonList.add(this.scanButton);

        this.reportScrollbar.setLeft(SCROLL_X).setTop(BOX_TOP + 1).setHeight(BOX_HEIGHT - 2);
        this.scanScrollbar.setLeft(175).setTop(39).setHeight(78);
        this.scanScrollbar.setRange(0, 0, 1);

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

    public void onUpdateTypeFilters() {
        for (final Map.Entry<TypeToggleButton, IAEStackType<?>> entry : this.typeToggleButtons.entrySet()) {
            final boolean enabled = this.container.getTypeFilters().isEnabled(entry.getValue());
            entry.getKey().setEnabled(enabled);
        }
    }

    public void onReportUpdated() {
        this.reportLines = this.generateReportLines();
        this.reportScrollbar.setRange(0, Math.max(0, this.reportLines.size() - visibleReportLines()), 1);
        if (!this.container.scanMode) {
            this.setScrollBar(this.reportScrollbar);
        }
    }

    public void onScanUpdated() {
        this.itemList.clear();

        final PartitionScanTask scanTask = this.container.scanData;
        if (scanTask == null) return;
        scanTask.getPartitions().forEach((s, partitions) -> partitions.forEach((key, value) -> this.itemList.add(key)));

        this.scanScrollbar.setRange(0, Math.max(0, this.itemList.size() - SCAN_ROWS), 1);
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
            if (btn == this.voidProtectionButton) {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.ToggleVoidProtection", ""));
            } else if (btn == this.reshuffleTab) {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.Mode", "reshuffle"));
            } else if (btn == this.scanModeButton) {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.Mode", "scan"));
            } else if (btn == this.startCancelButton) {
                if (this.container.reshuffleRunning) {
                    NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.Cancel", ""));
                } else {
                    final boolean confirmed = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                            || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                    NetworkHandler.instance
                            .sendToServer(new PacketValueConfig("Reshuffle.Start", confirmed ? "confirmed" : ""));
                }
            } else if (btn == this.scanButton) {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.Scan", ""));
                this.container.report = null;
            }
        } catch (final IOException e) {
            AELog.debug(e);
        }
    }

    @Override
    protected void mouseClicked(final int mouseX, final int mouseY, final int mouseButton) {
        if (this.container.scanMode && mouseButton == 0) {
            final int relX = mouseX - this.guiLeft;
            final int relY = mouseY - this.guiTop;

            if (relX >= SCAN_XO && relX < SCAN_XO + SCAN_ROW_W
                    && relY >= SCAN_YO
                    && relY < SCAN_YO + SCAN_ROWS * SCAN_ROW_H) {
                final int absRow = ((relY - SCAN_YO) / SCAN_ROW_H) + this.scanScrollbar.getCurrentScroll();
                final int locX = SCAN_XO + SCAN_ROW_W - LOCATE_BG_SIZE + 1;

                if (relX >= locX && absRow < this.itemList.size()) {
                    final IAEStack<?> aes = this.itemList.get(absRow);
                    final Map<IAEStackType<?>, Map<IAEStack<?>, List<PartitionRecord>>> partitions = this.container
                            .getScanData().getPartitions();
                    final List<PartitionRecord> partitionsList = partitions.get(aes.getStackType()).get(aes);

                    final Map<NamedDimensionalCoord, String[]> ndcm = new HashMap<>();
                    final String[] highlightParameters = new String[] {
                            PlayerMessages.MachineHighlightedNamed.getUnlocalized(),
                            PlayerMessages.MachineInOtherDimNamed.getUnlocalized() };
                    for (final PartitionRecord partition : partitionsList) {
                        ndcm.put(
                                new NamedDimensionalCoord(
                                        partition.x,
                                        partition.y,
                                        partition.z,
                                        partition.dim,
                                        partition.displayName),
                                highlightParameters);
                    }

                    BlockPosHighlighter.highlightNamedBlocks(this.mc.thePlayer, ndcm, aes.getDisplayName());
                    this.mc.thePlayer.closeScreen();
                    return;
                }
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        this.hoveredRow = -1;

        final boolean scanMode = this.container.scanMode;

        if (this.startCancelButton != null) this.startCancelButton.visible = !scanMode;
        if (this.scanButton != null) this.scanButton.visible = scanMode;

        if (scanMode) {
            final int bx = this.guiLeft + SCAN_XO;
            final int by = this.guiTop + SCAN_YO;
            for (int i = 0; i < SCAN_ROWS; i++) {
                final int absRow = i + this.scanScrollbar.getCurrentScroll();
                if (absRow >= this.itemList.size()) break;
                final int rowY = by + i * SCAN_ROW_H;
                if (mouseX >= bx && mouseX < bx + SCAN_ROW_W && mouseY >= rowY && mouseY < rowY + SCAN_ROW_H) {
                    this.hoveredRow = absRow;
                    break;
                }
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.voidProtectionButton.set(this.container.voidProtection ? YesNo.YES : YesNo.NO);
        this.reshuffleTab.setState(!this.container.scanMode);
        this.scanModeButton.setState(this.container.scanMode);

        if (this.container.scanMode) {
            this.fontRendererObj.drawString(
                    GuiText.ReshufflePartitionScanner.getLocal(),
                    8,
                    6,
                    GuiColors.ReshuffleTitle.getColor());
        } else {
            this.fontRendererObj.drawString(
                    this.getGuiDisplayName(GuiText.StorageReshuffle.getLocal()),
                    8,
                    6,
                    GuiColors.ReshuffleTitle.getColor());
            final String statusLabel = GuiText.ReshuffleStatusLabel.getLocal() + " ";
            final String statusValue;
            final int statusColor;
            if (this.container.reshuffleRunning) {
                statusValue = GuiText.ReshuffleStatusRunning.getLocal();
                statusColor = GuiColors.ReshuffleStatusRunning.getColor();
            } else if (this.container.reshuffleComplete) {
                statusValue = GuiText.ReshuffleStatusComplete.getLocal();
                statusColor = GuiColors.ReshuffleStatusComplete.getColor();
            } else if (this.container.reshuffleFailed) {
                statusValue = GuiText.ReshuffleStatusFailed.getLocal();
                statusColor = GuiColors.ReshuffleStatusFailed.getColor();
            } else if (this.container.reshuffleCancelled) {
                statusValue = GuiText.ReshuffleStatusCancelled.getLocal();
                statusColor = GuiColors.ReshuffleStatusCancelled.getColor();
            } else {
                statusValue = GuiText.ReshuffleStatusIdle.getLocal();
                statusColor = GuiColors.ReshuffleStatusIdle.getColor();
            }
            final int labelW = this.fontRendererObj.getStringWidth(statusLabel);
            this.fontRendererObj.drawString(statusLabel, 8, 18, GuiColors.ReshuffleTitle.getColor());
            this.fontRendererObj.drawString(statusValue, 8 + labelW, 18, statusColor);
        }

        if (this.container.scanMode) {
            drawScanContent();
        } else {
            drawReshuffleContent();
        }

        if (!this.container.scanMode) {
            if (this.container.reshuffleRunning) {
                this.startCancelButton.displayString = GuiText.ReshuffleCancel.getLocal();
            } else {
                this.startCancelButton.displayString = GuiText.ReshuffleStart.getLocal();
            }
        }

        if (this.container.scanMode && this.hoveredRow != -1) {
            final int relX = mouseX - this.guiLeft;
            final int relY = mouseY - this.guiTop;
            final int row = (relY - SCAN_YO) / SCAN_ROW_H;
            final int locX = SCAN_XO + SCAN_ROW_W - LOCATE_BG_SIZE + 1;
            final int locY = SCAN_YO + row * SCAN_ROW_H;
            if (relX >= locX && relX < locX + LOCATE_BG_SIZE && relY >= locY && relY < locY + LOCATE_BG_SIZE) {
                this.drawTooltip(mouseX - this.guiLeft, mouseY - this.guiTop, GuiText.ReshuffleLocate.getLocal());
            } else if (relX >= SCAN_XO && relX < locX) {
                final IAEStack<?> stack = this.itemList.get(this.hoveredRow);
                final Map<IAEStackType<?>, Map<IAEStack<?>, List<PartitionRecord>>> partitions = this.container
                        .getScanData().getPartitions();
                final List<PartitionRecord> partitionsList = partitions.get(stack.getStackType()).get(stack);
                this.drawTooltip(
                        mouseX - this.guiLeft,
                        mouseY - this.guiTop,
                        String.join("\n", getStackPartitionsLines(stack, partitionsList)));
            }
        }

        if (!this.container.scanMode && this.startCancelButton != null && this.startCancelButton.visible) {
            final int bx = this.startCancelButton.xPosition - offsetX;
            final int by = this.startCancelButton.yPosition - offsetY;
            if (mouseX - offsetX >= bx && mouseX - offsetX < bx + this.startCancelButton.width
                    && mouseY - offsetY >= by
                    && mouseY - offsetY < by + this.startCancelButton.height) {
                this.drawTooltip(
                        mouseX - this.guiLeft,
                        mouseY - this.guiTop,
                        this.container.reshuffleRunning ? GuiText.ReshuffleCancel.getLocal()
                                : GuiText.ReshuffleStart.getLocal() + "\n"
                                        + GuiText.ReshuffleStartShiftHint.getLocal());
            }
        }
        if (this.container.scanMode && this.scanButton != null && this.scanButton.visible) {
            final int bx = this.scanButton.xPosition - offsetX;
            final int by = this.scanButton.yPosition - offsetY;
            if (mouseX - offsetX >= bx && mouseX - offsetX < bx + this.scanButton.width
                    && mouseY - offsetY >= by
                    && mouseY - offsetY < by + this.scanButton.height) {
                this.drawTooltip(mouseX - this.guiLeft, mouseY - this.guiTop, GuiText.ReshuffleTabScanHint.getLocal());
            }
        }
    }

    private static @NotNull List<String> getStackPartitionsLines(IAEStack<?> stack,
            List<PartitionRecord> partitionsList) {
        final List<String> lines = new ArrayList<>();
        lines.add(GuiText.ReshuffleScanDuplicatePartitionStackNameStyle.getLocal() + stack.getDisplayName());
        for (int i = 0; i < partitionsList.size(); i++) {
            final PartitionRecord partitionRecord = partitionsList.get(i);
            final String locColor = GuiText.ReshuffleScanDuplicatePartitionLocColor.getLocal();
            final String locValueColor = GuiText.ReshuffleScanDuplicatePartitionLocValueColor.getLocal();

            lines.add(locColor + GuiText.ReshuffleScanDuplicatePartition.getLocal() + " " + (i + 1));
            lines.add(GuiText.ReshuffleScanDuplicatePartitionTargetNameColor.getLocal() + partitionRecord.displayName);
            lines.add(
                    locColor + GuiText.ReshuffleScanDuplicatePartitionLoc.getLocal()
                            + " "
                            + locValueColor
                            + partitionRecord.x
                            + ", "
                            + partitionRecord.y
                            + ", "
                            + partitionRecord.z
                            + "  "
                            + locColor
                            + GuiText.ReshuffleScanDuplicatePartitionLocDim.getLocal()
                            + " "
                            + locValueColor
                            + partitionRecord.dim
                            + "  "
                            + locColor
                            + (partitionRecord.slot != -1
                                    ? GuiText.ReshuffleScanSlot.getLocal() + ": " + locValueColor + partitionRecord.slot
                                    : ""));
        }
        return lines;
    }

    private void drawReshuffleContent() {
        this.fontRendererObj.drawString(
                GuiText.ReshuffleTotalItems
                        .getLocal(this.container.reshuffleProcessedItems, this.container.reshuffleTotalItems),
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

        if (!this.reportLines.isEmpty()) {
            final int visLines = visibleReportLines();
            final int scroll = this.reportScrollbar.getCurrentScroll();
            final float lineH = this.fontRendererObj.FONT_HEIGHT * TEXT_SCALE + 1;

            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            final int scale = new ScaledResolution(this.mc, this.mc.displayWidth, this.mc.displayHeight)
                    .getScaleFactor();
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
        }

        final int barX = BOX_LEFT;
        final int barY = PROGRESS_Y;
        final int barW = BOX_WIDTH - 30;

        drawRect(barX - 1, barY - 1, barX + barW + 1, barY + 7, GuiColors.ReshufflesStatusBarBackground.getColor());
        drawRect(barX, barY, barX + barW, barY + 6, GuiColors.ReshufflesStatusBarInactive.getColor());

        if (this.container.reshuffleProgress > 0) {
            final int fill = (int) Math.floor(this.container.reshuffleProgress * barW / 100.0);
            final int alpha = 255 - this.container.reshuffleProgress * 255 / 100;
            this.drawGradientRect(
                    barX,
                    barY,
                    barX + fill,
                    barY + 6,
                    GuiColors.ReshuffleStatusBarProgressGradientStart.getColor(),
                    GuiColors.ReshuffleStatusBarProgressGradientEnd.getColor() | alpha);
            drawRect(
                    barX + fill - 1,
                    barY,
                    barX + fill + 1,
                    barY + 6,
                    GuiColors.ReshuffleStatusBarProgressRunner.getColor());
        }

        this.fontRendererObj.drawString(
                this.container.reshuffleProgress + "%",
                barX + barW + 3,
                barY,
                GuiColors.ReshuffleTitle.getColor());
    }

    private static final int SCAN_XO = 9;
    private static final int SCAN_YO = 38;
    private static final int SCAN_ROW_H = 20;
    private static final int SCAN_ROWS = 4;
    private static final int SCAN_ROW_W = 159;
    private static final int LOCATE_BG_SIZE = 16;

    private void drawScanContent() {
        final int total = this.itemList.size();

        if (total == 0) {
            this.fontRendererObj.drawString(
                    GuiText.ReshuffleScanEmpty.getLocal(),
                    SCAN_XO + 4,
                    SCAN_YO + SCAN_ROWS * SCAN_ROW_H + 10,
                    GuiColors.DefaultBlack.getColor());
            return;
        }

        this.fontRendererObj.drawString(
                GuiText.ReshuffleScanDuplicatesTitle.getLocal() + " " + total,
                SCAN_XO,
                SCAN_YO - 12,
                GuiColors.ReshuffleTitle.getColor());

        for (int i = 0; i < SCAN_ROWS; i++) {
            if (i >= this.itemList.size()) continue;
            final int absRow = i + this.scanScrollbar.getCurrentScroll();
            final boolean isHovered = this.hoveredRow == absRow;
            final int rowY = SCAN_YO + i * SCAN_ROW_H;
            final int locBtnX = SCAN_XO + SCAN_ROW_W - LOCATE_BG_SIZE;

            if (isHovered) {
                drawRect(
                        SCAN_XO,
                        rowY + 1,
                        locBtnX + 17,
                        rowY + SCAN_ROW_H,
                        GuiColors.ReshuffleNetworkScanHoverColor.getColor());
            }

            final IAEStack<?> aes = this.itemList.get(absRow);
            aes.drawInGui(Minecraft.getMinecraft(), SCAN_XO + 1, rowY + 3);

            String name = aes.getDisplayName();

            final int maxNameW = (int) ((SCAN_ROW_W - 22 - LOCATE_BG_SIZE - 2) / TEXT_SCALE);
            GL11.glPushMatrix();
            GL11.glScalef(TEXT_SCALE, TEXT_SCALE, 1.0f);
            final float inv = 1.0f / TEXT_SCALE;
            while (!name.isEmpty() && this.fontRendererObj.getStringWidth(name) > maxNameW) {
                name = name.substring(0, name.length() - 1);
            }
            this.fontRendererObj.drawString(
                    name,
                    (int) ((SCAN_XO + 20) * inv),
                    (int) ((rowY + 3) * inv),
                    GuiColors.DefaultBlack.getColor());
            this.fontRendererObj.drawString(
                    aes.getStackSize() + "x",
                    (int) ((SCAN_XO + 20) * inv),
                    (int) ((rowY + 11) * inv),
                    GuiColors.NetworkStatusItemCount.getColor());
            GL11.glPopMatrix();

            drawLocateButton(locBtnX, rowY + 2, isHovered);
        }
    }

    private void drawLocateButton(final int x, final int y, final boolean hovered) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        this.mc.renderEngine.bindTexture(ExtraBlockTextures.GuiTexture("guis/states.png"));
        if (hovered) {
            GL11.glColor4f(1.2f, 1.2f, 1.2f, 1.0f);
        } else {
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        }
        this.drawTexturedModalRect(x, y, 256 - 16, 256 - 16, 16, 16);
        final int uv_y = LOCATE_ICON_INDEX / 16;
        final int uv_x = LOCATE_ICON_INDEX % 16;
        this.drawTexturedModalRect(x, y, uv_x * 16, uv_y * 16, 16, 16);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.setScrollBar(this.container.scanMode ? this.scanScrollbar : this.reportScrollbar);
        this.bindTexture(this.container.scanMode ? "guis/networkscan.png" : "guis/networkstatus.png");
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

    private List<String> buildItemLines(ItemChange c, String accentColor, String sign) {
        List<String> out = new ArrayList<>();
        String name = getStackDisplayName(c.stack);
        String delta = abbrev(Math.abs(c.difference));
        out.add(accentColor + "• " + VALUES + name + " " + accentColor + sign + delta);
        out.add(NEUTRAL + "  (" + abbrev(c.beforeCount) + " -> " + abbrev(c.afterCount) + ")");
        return out;
    }

    public List<String> generateReportLines() {
        final ReshuffleReport report = this.container.getReshuffleReport();
        if (report == null) return new ArrayList<>();
        List<String> lines = new ArrayList<>();
        long durationMs = report.endTime - report.startTime;

        lines.add(MAIN + centerTitle(GuiText.ReshuffleReportTitle.getLocal()));

        lines.add(
                MAIN + GuiText.ReshuffleReportTime.getLocal()
                        + " "
                        + VALUES
                        + String.format("%.2fs", durationMs / 1000.0)
                        + MAIN
                        + "  "
                        + GuiText.ReshuffleReportMode.getLocal()
                        + " "
                        + VALUES
                        + buildModeString());

        lines.add(
                MAIN + GuiText.ReshuffleReportVoidLabel.getLocal()
                        + " "
                        + (report.voidProtection ? POSITIVE + GuiText.ReshuffleReportVoidOn.getLocal()
                                : NEGATIVE + GuiText.ReshuffleReportVoidOff.getLocal()));

        lines.add("");
        lines.add(MAIN + GuiText.ReshuffleReportSectionProcessing.getLocal());
        lines.add(
                MAIN + GuiText.ReshuffleReportDone.getLocal()
                        + " "
                        + POSITIVE
                        + fmt(report.itemsProcessed)
                        + MAIN
                        + "  "
                        + GuiText.ReshuffleReportSkip.getLocal()
                        + " "
                        + WARNING
                        + fmt(report.itemsSkipped));

        lines.add("");
        lines.add(MAIN + GuiText.ReshuffleReportSectionStorageTotals.getLocal());
        lines.add(
                buildTotalLine(
                        GuiText.ReshuffleReportLabelTypes.getLocal(),
                        report.totalItemTypesBefore,
                        report.totalItemTypesAfter));
        lines.add(
                buildTotalLine(
                        GuiText.ReshuffleReportLabelStacks.getLocal(),
                        report.totalStacksBefore,
                        report.totalStacksAfter));

        lines.add("");
        lines.add(MAIN + GuiText.ReshuffleReportSectionItemChanges.getLocal());
        lines.add(
                POSITIVE + GuiText.ReshuffleReportGainedLabel.getLocal()
                        + " "
                        + VALUES
                        + fmt(report.itemsGained)
                        + POSITIVE
                        + " ("
                        + abbrev(report.totalGained)
                        + ")");
        lines.add(
                NEGATIVE + GuiText.ReshuffleReportLostLabel.getLocal()
                        + " "
                        + VALUES
                        + fmt(report.itemsLost)
                        + NEGATIVE
                        + " ("
                        + abbrev(report.totalLost)
                        + ")");
        lines.add(
                NEUTRAL + GuiText.ReshuffleReportUnchangedLabel.getLocal() + " " + VALUES + fmt(report.itemsUnchanged));

        if (!report.lostItems.isEmpty()) {
            lines.add("");
            lines.add(NEGATIVE + GuiText.ReshuffleReportSectionTopLost.getLocal());
            for (ItemChange c : report.lostItems) {
                lines.addAll(buildItemLines(c, NEGATIVE, "-"));
            }
        }

        if (!report.gainedItems.isEmpty() && report.totalGained > 0) {
            lines.add("");
            lines.add(POSITIVE + GuiText.ReshuffleReportSectionTopGained.getLocal());
            for (ItemChange c : report.gainedItems) {
                lines.addAll(buildItemLines(c, POSITIVE, "+"));
            }
        }

        if (!report.skippedItemsList.isEmpty()) {
            lines.add("");
            lines.add(WARNING + GuiText.ReshuffleReportSectionSkipped.getLocal());
            for (IAEStack<?> stack : report.skippedItemsList) {
                lines.add(
                        WARNING + "• "
                                + VALUES
                                + getStackDisplayName(stack)
                                + " "
                                + NEUTRAL
                                + abbrev(stack.getStackSize()));
            }
        }

        lines.add("");
        long net = report.totalStacksAfter - report.totalStacksBefore;
        if (net != 0) {
            lines.add(
                    WARNING + GuiText.ReshuffleReportNetChanged.getLocal()
                            + " "
                            + diffColor(net)
                            + signedAbbrev(net)
                            + " "
                            + NEUTRAL
                            + GuiText.ReshuffleReportNetChangedReason.getLocal());
        } else {
            lines.add(POSITIVE + GuiText.ReshuffleReportIntegrityOk.getLocal());
        }

        lines.add(MAIN + repeatEquals(LINE_WIDTH));
        return lines;
    }

    private String buildTotalLine(String label, long before, long after) {
        long delta = after - before;
        return MAIN + label
                + " "
                + NEUTRAL
                + abbrev(before)
                + VALUES
                + " -> "
                + VALUES
                + abbrev(after)
                + " "
                + diffColor(delta)
                + "("
                + signedAbbrev(delta)
                + ")";
    }

    private String buildModeString() {
        final ReshuffleReport report = this.container.getReshuffleReport();
        if (!report.types.getEnabledTypes().iterator().hasNext()) return GuiText.ReshuffleReportModeNone.getLocal();
        StringBuilder sb = new StringBuilder();

        for (IAEStackType<?> type : report.types.getEnabledTypes()) {
            if (sb.length() > 0) sb.append('/');
            sb.append(type.getDisplayName());
        }
        return sb.toString();
    }

    final static ReadableNumberConverter converter = ReadableNumberConverter.INSTANCE;

    private static String abbrev(long v) {
        return converter.toWideReadableForm(v);
    }

    private static String signedAbbrev(long v) {
        if (v > 0) return "+" + abbrev(v);
        if (v < 0) return "-" + abbrev(-v);
        return "0";
    }

    private static String fmt(long v) {
        return NumberFormat.getNumberInstance(Locale.US).format(v);
    }

    private static String diffColor(long delta) {
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

    private String getStackDisplayName(IAEStack<?> stack) {
        if (stack == null) return GuiText.ReshuffleReportUnknown.getLocal();
        try {
            String name = stack.getDisplayName();
            return (name != null && !name.isEmpty()) ? name : GuiText.ReshuffleReportUnknown.getLocal();
        } catch (Exception e) {
            return GuiText.ReshuffleReportUnknown.getLocal();
        }
    }
}
