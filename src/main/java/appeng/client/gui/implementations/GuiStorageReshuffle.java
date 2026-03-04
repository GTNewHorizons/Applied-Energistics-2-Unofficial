/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.client.gui.implementations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.config.YesNo;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.NamedDimensionalCoord;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiToggleButton;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.gui.widgets.TypeToggleButton;
import appeng.client.me.ItemRepo;
import appeng.client.render.highlighter.BlockPosHighlighter;
import appeng.client.texture.ExtraBlockTextures;
import appeng.container.implementations.ContainerStorageReshuffle;
import appeng.core.AELog;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.tile.misc.TileStorageReshuffle;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;

public class GuiStorageReshuffle extends AEBaseGui implements ISortSource {

    private static final int BOX_TOP = 38;
    private static final int BOX_HEIGHT = 80;
    private static final int BOX_LEFT = 10;
    private static final int BOX_WIDTH = 162;
    private static final int SCROLL_X = 175;
    private static final int PROGRESS_Y = 126;
    private static final int BOTTOM_Y = 141;
    private static final float TEXT_SCALE = 0.65f;
    private static final int LOCATE_ICON_INDEX = 8 * 16 + 7;

    private final ContainerStorageReshuffle container;
    private final Map<TypeToggleButton, IAEStackType<?>> typeToggleButtons = new IdentityHashMap<>();

    private final ItemRepo scanRepo;
    private final GuiScrollbar reportScrollbar = new GuiScrollbar();
    private final GuiScrollbar scanScrollbar = new GuiScrollbar();

    private GuiImgButton voidProtectionButton;
    private GuiToggleButton reshuffleTab;
    private GuiToggleButton scanModeButton;
    private GuiButton startCancelButton;
    private GuiButton scanButton;

    private List<String> reportLines = new ArrayList<>();
    private int hoveredRow = -1;

    public GuiStorageReshuffle(final InventoryPlayer inventoryPlayer, final TileStorageReshuffle te) {
        super(new ContainerStorageReshuffle(inventoryPlayer, te));
        this.container = (ContainerStorageReshuffle) this.inventorySlots;
        this.xSize = 195;
        this.ySize = 187;
        this.scanRepo = new ItemRepo(this.scanScrollbar, this);
        this.scanRepo.setRowSize(1);
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
                8 * 16 + 6,
                8 * 16 + 6,
                GuiText.ReshuffleTabReshuffle.getUnlocalized(),
                GuiText.ReshuffleTabReshuffleHint.getUnlocalized());
        this.buttonList.add(this.reshuffleTab);

        this.scanModeButton = new GuiToggleButton(
                rightTabX,
                this.guiTop + 28,
                8 * 16 + 5,
                8 * 16 + 5,
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

        this.reportScrollbar.setLeft(SCROLL_X).setTop(BOX_TOP).setHeight(BOX_HEIGHT);
        this.scanScrollbar.setLeft(175).setTop(39).setHeight(78);
        this.scanScrollbar.setRange(0, 0, 1);

        this.setScrollBar(this.reportScrollbar);
    }

    private void initTypeToggleButtons(final int x, final int yStart) {
        this.typeToggleButtons.clear();
        final Reference2BooleanMap<IAEStackType<?>> filters = this.container.getTypeFilters();

        int y = yStart;
        for (final IAEStackType<?> type : AEStackTypeRegistry.getSortedTypes()) {
            final ResourceLocation texture = type.getButtonTexture();
            final IIcon icon = type.getButtonIcon();
            if (texture == null || icon == null) continue;

            final TypeToggleButton btn = new TypeToggleButton(x, y, texture, icon, type.getDisplayName());
            btn.setEnabled(filters != null && filters.getBoolean(type));
            this.typeToggleButtons.put(btn, type);
            this.buttonList.add(btn);

            y += 20;
        }
    }

    public void onUpdateTypeFilters() {
        final Reference2BooleanMap<IAEStackType<?>> filters = this.container.getTypeFilters();
        if (filters == null) return;
        for (final Map.Entry<TypeToggleButton, IAEStackType<?>> entry : this.typeToggleButtons.entrySet()) {
            entry.getKey().setEnabled(filters.getBoolean(entry.getValue()));
        }
    }

    public void onReportUpdated() {
        this.reportLines = this.container.getReportLines();
        this.reportScrollbar.setRange(0, Math.max(0, this.reportLines.size() - visibleReportLines()), 1);
        if (!this.container.scanMode) {
            this.setScrollBar(this.reportScrollbar);
        }
    }

    public void onScanUpdated() {
        this.scanRepo.clear();
        final String data = this.container.getScanData();
        if (!data.isEmpty()) {
            for (final String line : data.split("\n", -1)) {
                final String[] parts = line.split("@");
                if (parts.length < 3) continue;
                try {
                    final Item item = (Item) Item.itemRegistry.getObject(parts[0]);
                    if (item == null) continue;
                    final int meta = Integer.parseInt(parts[1]);
                    final int count = Integer.parseInt(parts[2]);
                    final ItemStack is = new ItemStack(item, count, meta);

                    final List<NamedDimensionalCoord> coords = new ArrayList<>();
                    final NBTTagList cellInfoList = new NBTTagList();
                    for (int i = 3; i < parts.length; i++) {
                        final String[] halves = parts[i].split("\\|", 2);
                        if (halves.length < 2) continue;
                        final String[] c = halves[0].split(",", 6);
                        if (c.length < 6) continue;
                        final int cx = Integer.parseInt(c[0]);
                        final int cy = Integer.parseInt(c[1]);
                        final int cz = Integer.parseInt(c[2]);
                        final int cdim = Integer.parseInt(c[3]);
                        coords.add(new NamedDimensionalCoord(cx, cy, cz, cdim, halves[1]));
                        final NBTTagCompound ce = new NBTTagCompound();
                        ce.setInteger("x", cx);
                        ce.setInteger("y", cy);
                        ce.setInteger("z", cz);
                        ce.setInteger("dim", cdim);
                        ce.setInteger("slot", Integer.parseInt(c[4]));
                        ce.setInteger("types", Integer.parseInt(c[5]));
                        ce.setString("cellName", halves[1]);
                        cellInfoList.appendTag(ce);
                    }

                    if (!coords.isEmpty()) {
                        final NBTTagCompound tag = new NBTTagCompound();
                        NamedDimensionalCoord.writeListToNBTNamed(tag, coords);
                        tag.setTag("cellInfo", cellInfoList);
                        is.setTagCompound(tag);
                    }

                    final IAEItemStack ais = AEItemStack.create(is);
                    ais.setStackSize(count);
                    this.scanRepo.postUpdate(ais);
                } catch (final NumberFormatException ignored) {}
            }
        }
        this.scanRepo.updateView();
        this.scanScrollbar.setRange(0, Math.max(0, this.scanRepo.size() - SCAN_ROWS), 1);
    }

    private int visibleReportLines() {
        return (int) (BOX_HEIGHT / (this.fontRendererObj.FONT_HEIGHT * TEXT_SCALE + 1));
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        if (btn instanceof final TypeToggleButton tbtn) {
            final IAEStackType<?> type = this.typeToggleButtons.get(tbtn);
            final Reference2BooleanMap<IAEStackType<?>> filters = this.container.getTypeFilters();
            if (type != null && filters != null) {
                final boolean next = !filters.getBoolean(type);
                filters.put(type, next);
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
                final int row = (relY - SCAN_YO) / SCAN_ROW_H;
                final int absRow = row + this.scanScrollbar.getCurrentScroll();
                final int locX = SCAN_XO + SCAN_ROW_W - LOCATE_BG_SIZE + 1;
                if (relX >= locX && absRow < this.scanRepo.size()) {
                    final IAEStack<?> stack = this.scanRepo.getReferenceStack(row);
                    if (stack instanceof IAEItemStack ais) {
                        final ItemStack is = ais.getItemStack();
                        if (is != null && is.hasTagCompound()) {
                            final List<NamedDimensionalCoord> dcl = NamedDimensionalCoord
                                    .readAsListFromNBTNamed(is.getTagCompound());
                            if (!dcl.isEmpty()) {
                                final Map<NamedDimensionalCoord, String[]> ndcm = new HashMap<>(dcl.size());
                                for (final NamedDimensionalCoord dc : dcl) {
                                    ndcm.put(
                                            dc,
                                            dc.getCustomName().isEmpty()
                                                    ? new String[] { PlayerMessages.MachineHighlighted.getUnlocalized(),
                                                            PlayerMessages.MachineInOtherDim.getUnlocalized() }
                                                    : new String[] {
                                                            PlayerMessages.MachineHighlightedNamed.getUnlocalized(),
                                                            PlayerMessages.MachineInOtherDimNamed.getUnlocalized() });
                                }
                                BlockPosHighlighter.highlightNamedBlocks(this.mc.thePlayer, ndcm, is.getDisplayName());
                                this.mc.thePlayer.closeScreen();
                                return;
                            }
                        }
                    }
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
                if (absRow >= this.scanRepo.size()) break;
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
            final String statusLabel = GuiText.ReshuffleStatusLabel.getLocal();
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
            drawScanContent(offsetX, offsetY, mouseX, mouseY);
        } else {
            drawReshuffleContent(offsetX, offsetY, mouseX, mouseY);
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
                final int visualRow = this.hoveredRow - this.scanScrollbar.getCurrentScroll();
                final IAEStack<?> stack = this.scanRepo.getReferenceStack(visualRow);
                if (stack instanceof IAEItemStack ais && ais.getItemStack() != null
                        && ais.getItemStack().hasTagCompound()) {
                    final NBTTagList cellInfoList = ais.getItemStack().getTagCompound().getTagList("cellInfo", 10);
                    final List<String> lines = new ArrayList<>();
                    lines.add("§f§l" + ais.getDisplayName());
                    for (int ci = 0; ci < cellInfoList.tagCount(); ci++) {
                        final NBTTagCompound ce = cellInfoList.getCompoundTagAt(ci);
                        lines.add("§8" + GuiText.ReshuffleScanDuplicatePartition.getLocal() + " " + (ci + 1));
                        lines.add("§7" + ce.getString("cellName"));
                        lines.add(
                                "§8Loc: §e" + ce.getInteger("x")
                                        + ", "
                                        + ce.getInteger("y")
                                        + ", "
                                        + ce.getInteger("z")
                                        + "  §8Dim: §e"
                                        + ce.getInteger("dim")
                                        + "  §8"
                                        + GuiText.ReshuffleScanSlot.getLocal()
                                        + ": §e"
                                        + ce.getInteger("slot"));
                    }
                    this.drawTooltip(mouseX - this.guiLeft, mouseY - this.guiTop, String.join("\n", lines));
                }
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

    private void drawReshuffleContent(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
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

        {
            final int barX = BOX_LEFT;
            final int barY = PROGRESS_Y;
            final int barW = BOX_WIDTH - 30;
            drawRect(barX - 1, barY - 1, barX + barW + 1, barY + 7, 0xFF333333);
            drawRect(barX, barY, barX + barW, barY + 6, 0xFF111111);
            if (this.container.reshuffleProgress > 0) {
                final int fill = (int) Math.floor(this.container.reshuffleProgress * barW / 100.0);
                final int alpha = 255 - this.container.reshuffleProgress * 255 / 100;
                this.drawGradientRect(barX, barY, barX + fill, barY + 6, 0xFF00FFFF, 0xFF00FF00 | alpha);
                drawRect(barX + fill - 1, barY, barX + fill + 1, barY + 6, 0xFFFFFFFF);
            }
            this.fontRendererObj.drawString(
                    this.container.reshuffleProgress + "%",
                    barX + barW + 3,
                    barY,
                    GuiColors.ReshuffleTitle.getColor());
        }
    }

    private static final int SCAN_XO = 9;
    private static final int SCAN_YO = 38;
    private static final int SCAN_ROW_H = 20;
    private static final int SCAN_ROWS = 4;
    private static final int SCAN_ROW_W = 159;
    private static final int LOCATE_BG_SIZE = 16;

    private void drawScanContent(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        final int total = this.scanRepo.size();

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
            final int absRow = i + this.scanScrollbar.getCurrentScroll();
            final IAEStack<?> stack = this.scanRepo.getReferenceStack(i);
            if (stack == null) break;
            if (!(stack instanceof IAEItemStack ais)) continue;

            final boolean isHovered = this.hoveredRow == absRow;
            final int rowY = SCAN_YO + i * SCAN_ROW_H;
            final int locBtnX = SCAN_XO + SCAN_ROW_W - LOCATE_BG_SIZE + 1;

            if (isHovered) {
                drawRect(SCAN_XO, rowY, locBtnX, rowY + SCAN_ROW_H, 0x80FFFF00);
            }

            final ItemStack is = this.scanRepo.getItem(i);
            if (is != null) {
                this.drawItem(SCAN_XO + 1, rowY + 1, is);
            }

            String name = Platform.getItemDisplayName(is);
            if (name.isEmpty()) name = ais.getDisplayName();
            if (name == null) name = "";

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
                    ais.getStackSize() + "x",
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

    @Override
    public Enum getSortBy() {
        return SortOrder.NAME;
    }

    @Override
    public Enum getSortDir() {
        return SortDir.ASCENDING;
    }

    @Override
    public Enum getSortDisplay() {
        return ViewItems.ALL;
    }
}
