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
import java.text.DecimalFormat;
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
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import appeng.api.config.ActionItems;
import appeng.api.config.HealthSortOrder;
import appeng.api.config.ReshuffleView;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.NamedDimensionalCoord;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.gui.widgets.TypeToggleButton;
import appeng.client.me.ItemRepo;
import appeng.client.render.highlighter.BlockPosHighlighter;
import appeng.container.implementations.ContainerStorageReshuffle;
import appeng.container.implementations.ReshuffleState;
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
    private static final int BOX_HEIGHT = 78;
    private static final int BOX_LEFT = 10;
    private static final int BOX_WIDTH = 162;
    private static final int SCROLL_X = 175;
    private static final int PROGRESS_Y = 122;
    private static final int BOTTOM_Y = 137;
    private static final float TEXT_SCALE = 0.65f;
    private static final int LOCATE_ICON_INDEX = 8 * 16 + 6;
    private static final int SCAN_XO = 9;
    private static final int SCAN_YO = 38;
    private static final int SCAN_ROW_H = 22;
    private static final int SCAN_ROWS = 4;
    private static final int SCAN_ROW_W = 159;
    private static final int LOCATE_BG_SIZE = 25;

    private final ContainerStorageReshuffle container;
    private final Map<TypeToggleButton, IAEStackType<?>> typeToggleButtons = new IdentityHashMap<>();

    private final ItemRepo scanRepo;
    private final GuiScrollbar reportScrollbar = new GuiScrollbar();
    private final GuiScrollbar scanScrollbar = new GuiScrollbar();
    private final GuiScrollbar healthScrollbar = new GuiScrollbar();

    private static final class HealthEntry {

        final String cellItemId;
        final int cellMeta;
        final String cellName;
        final int x, y, z, dim, slot;
        final long bytesUsed, bytesTotal;
        final int typesUsed, typesTotal;
        final List<String[]> topItems; // each entry is [name, count]
        final String stackTypeId;

        HealthEntry(String id, int meta, String name, int x, int y, int z, int dim, int slot, long bu, long bt, int tu,
                int tt, List<String[]> topItems, String stackTypeId) {
            this.cellItemId = id;
            this.cellMeta = meta;
            this.cellName = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
            this.slot = slot;
            this.bytesUsed = bu;
            this.bytesTotal = bt;
            this.typesUsed = tu;
            this.typesTotal = tt;
            this.topItems = topItems;
            this.stackTypeId = stackTypeId;
        }

        double fillPct() {
            return bytesTotal > 0 ? (bytesUsed * 100.0 / bytesTotal) : 0.0;
        }
    }

    private List<HealthEntry> healthEntries = new ArrayList<>();

    private GuiImgButton voidProtectionButton;
    private GuiImgButton includeSubnetsButton;
    private GuiImgButton reshuffleTab;
    private GuiImgButton scanModeButton;
    private GuiImgButton healthModeButton;
    private GuiImgButton healthSortButton;
    private GuiImgButton healthSortDirButton;
    private GuiButton startCancelButton;
    private GuiButton scanButton;
    private GuiTabButton locateButton;

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
                Settings.ACTIONS,
                ActionItems.VOID_PROTECTION_ON);
        this.buttonList.add(this.voidProtectionButton);

        this.includeSubnetsButton = new GuiImgButton(
                leftCol - 18,
                this.guiTop + 28,
                Settings.ACTIONS,
                ActionItems.INCLUDE_SUBNETS_ON);
        this.buttonList.add(this.includeSubnetsButton);

        this.reshuffleTab = new GuiImgButton(
                rightTabX,
                this.guiTop + 8,
                Settings.RESHUFFLE_VIEW,
                ReshuffleView.RESHUFFLE);
        this.buttonList.add(this.reshuffleTab);

        this.scanModeButton = new GuiImgButton(
                rightTabX,
                this.guiTop + 28,
                Settings.RESHUFFLE_VIEW,
                ReshuffleView.SCAN);
        this.buttonList.add(this.scanModeButton);

        this.healthModeButton = new GuiImgButton(
                rightTabX,
                this.guiTop + 48,
                Settings.RESHUFFLE_VIEW,
                ReshuffleView.HEALTH);
        this.buttonList.add(this.healthModeButton);

        final int sortBtnY = this.guiTop + SCAN_YO - 20;
        this.healthSortButton = new GuiImgButton(
                this.guiLeft + SCAN_XO + SCAN_ROW_W - 36,
                sortBtnY,
                Settings.CELL_HEALTH_SORT,
                this.container.getHealthSortOrder());
        this.buttonList.add(this.healthSortButton);

        this.healthSortDirButton = new GuiImgButton(
                this.guiLeft + SCAN_XO + SCAN_ROW_W - 18,
                sortBtnY,
                Settings.CELL_HEALTH_SORT_DIR,
                this.container.getHealthSortDir());
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

        this.reportScrollbar.setLeft(SCROLL_X).setTop(39).setHeight(80);
        this.scanScrollbar.setLeft(SCROLL_X).setTop(39).setHeight(88);
        this.scanScrollbar.setRange(0, 0, 1);
        this.healthScrollbar.setLeft(SCROLL_X).setTop(39).setHeight(88);
        this.healthScrollbar.setRange(0, 0, 1);

        this.locateButton = new GuiTabButton(
                0,
                0,
                LOCATE_ICON_INDEX,
                GuiText.ReshuffleLocate.getUnlocalized(),
                itemRender);

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
        if (!this.container.isScanMode()) {
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

    public void onHealthUpdated() {
        this.healthEntries = new ArrayList<>();
        final String data = this.container.getHealthData();
        if (!data.isEmpty()) {
            for (final String line : data.split("\n", -1)) {
                final String[] f = line.split("\t", 15);
                if (f.length < 14) continue;
                try {
                    final List<String[]> topItems = new ArrayList<>();
                    if (!f[12].isEmpty()) {
                        final String[] parts = f[12].split(";");
                        for (int i = 0; i + 1 < parts.length; i += 2) {
                            topItems.add(new String[] { parts[i], parts[i + 1] });
                        }
                    }
                    this.healthEntries.add(
                            new HealthEntry(
                                    f[0],
                                    Integer.parseInt(f[1]),
                                    f[2],
                                    Integer.parseInt(f[3]),
                                    Integer.parseInt(f[4]),
                                    Integer.parseInt(f[5]),
                                    Integer.parseInt(f[6]),
                                    Integer.parseInt(f[7]),
                                    Long.parseLong(f[8]),
                                    Long.parseLong(f[9]),
                                    Integer.parseInt(f[10]),
                                    Integer.parseInt(f[11]),
                                    topItems,
                                    f[13]));
                } catch (final NumberFormatException ignored) {}
            }
        }
        sortHealthEntries();
        this.healthScrollbar.setRange(0, Math.max(0, this.healthEntries.size() - SCAN_ROWS), 1);
    }

    private void sortHealthEntries() {
        final HealthSortOrder order = this.container.getHealthSortOrder();
        final int dir = this.container.getHealthSortDir() == SortDir.ASCENDING ? 1 : -1;
        this.healthEntries.sort((a, b) -> {
            final double va = order == HealthSortOrder.FILL_PCT ? a.fillPct() : (double) a.bytesTotal;
            final double vb = order == HealthSortOrder.FILL_PCT ? b.fillPct() : (double) b.bytesTotal;
            return dir * Double.compare(vb, va); // higher value first by default (descending natural)
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
            } else if (btn == this.includeSubnetsButton) {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.ToggleIncludeSubnets", ""));
            } else if (btn == this.reshuffleTab) {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.View", "reshuffle"));
            } else if (btn == this.scanModeButton) {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.View", "scan"));
            } else if (btn == this.healthModeButton) {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.View", "health"));
            } else if (btn == this.healthSortButton) {
                final HealthSortOrder next = this.container.getHealthSortOrder() == HealthSortOrder.FILL_PCT
                        ? HealthSortOrder.BYTES_TOTAL
                        : HealthSortOrder.FILL_PCT;
                NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.HealthSort", next.name()));
                this.healthSortButton.set(next);
                sortHealthEntries();
            } else if (btn == this.healthSortDirButton) {
                final SortDir next = this.container.getHealthSortDir() == SortDir.ASCENDING ? SortDir.DESCENDING
                        : SortDir.ASCENDING;
                NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.HealthSortDir", next.name()));
                this.healthSortDirButton.set(next);
                sortHealthEntries();
            } else if (btn == this.startCancelButton) {
                if (this.container.getReshuffleState().running) {
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
    }

    @Override
    protected void mouseClicked(final int mouseX, final int mouseY, final int mouseButton) {
        if (mouseButton == 0) {
            final int relX = mouseX - this.guiLeft;
            final int relY = mouseY - this.guiTop;

            if (this.container.isScanMode() && relX >= SCAN_XO
                    && relX < SCAN_XO + SCAN_ROW_W
                    && relY >= SCAN_YO
                    && relY < SCAN_YO + SCAN_ROWS * SCAN_ROW_H) {
                final int row = (relY - SCAN_YO) / SCAN_ROW_H;
                final int absRow = row + this.scanScrollbar.getCurrentScroll();
                final int locX = SCAN_XO + SCAN_ROW_W - LOCATE_BG_SIZE;
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

            if (this.container.isHealthMode() && relX >= SCAN_XO
                    && relX < SCAN_XO + SCAN_ROW_W
                    && relY >= SCAN_YO
                    && relY < SCAN_YO + SCAN_ROWS * SCAN_ROW_H) {
                final int row = (relY - SCAN_YO) / SCAN_ROW_H;
                final int absRow = row + this.healthScrollbar.getCurrentScroll();
                final int locX = SCAN_XO + SCAN_ROW_W - LOCATE_BG_SIZE;
                if (relX >= locX && absRow < this.healthEntries.size()) {
                    final HealthEntry e = this.healthEntries.get(absRow);
                    final NamedDimensionalCoord coord = new NamedDimensionalCoord(e.x, e.y, e.z, e.dim, e.cellName);
                    final Map<NamedDimensionalCoord, String[]> ndcm = new HashMap<>(1);
                    ndcm.put(
                            coord,
                            new String[] { PlayerMessages.MachineHighlightedNamed.getUnlocalized(),
                                    PlayerMessages.MachineInOtherDimNamed.getUnlocalized() });
                    BlockPosHighlighter.highlightNamedBlocks(this.mc.thePlayer, ndcm, e.cellName);
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

        final boolean scanMode = this.container.isScanMode();
        final boolean healthMode = this.container.isHealthMode();
        final boolean reshuffleMode = !scanMode && !healthMode;

        this.voidProtectionButton.visible = reshuffleMode;
        this.includeSubnetsButton.visible = reshuffleMode;
        for (final TypeToggleButton tb : this.typeToggleButtons.keySet()) {
            tb.setEnabled(
                    reshuffleMode && this.container.getTypeFilters() != null
                            && this.container.getTypeFilters().getBoolean(this.typeToggleButtons.get(tb)));
            tb.visible = reshuffleMode;
        }

        if (this.startCancelButton != null) this.startCancelButton.visible = reshuffleMode;
        if (this.scanButton != null) this.scanButton.visible = scanMode || healthMode;

        this.healthSortButton.visible = healthMode;
        this.healthSortDirButton.visible = healthMode;

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

        if (healthMode) {
            final int bx = this.guiLeft + SCAN_XO;
            final int by = this.guiTop + SCAN_YO;
            for (int i = 0; i < SCAN_ROWS; i++) {
                final int absRow = i + this.healthScrollbar.getCurrentScroll();
                if (absRow >= this.healthEntries.size()) break;
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
        final boolean scanMode = this.container.isScanMode();
        final boolean healthMode = this.container.isHealthMode();
        final boolean reshuffleMode = !scanMode && !healthMode;

        this.voidProtectionButton.set(
                this.container.isVoidProtection() ? ActionItems.VOID_PROTECTION_ON : ActionItems.VOID_PROTECTION_OFF);
        this.includeSubnetsButton.set(
                this.container.isIncludeSubnets() ? ActionItems.INCLUDE_SUBNETS_ON : ActionItems.INCLUDE_SUBNETS_OFF);

        this.healthSortButton.set(this.container.getHealthSortOrder());
        this.healthSortDirButton.set(this.container.getHealthSortDir());

        if (scanMode) {
            this.fontRendererObj.drawString(
                    GuiText.ReshufflePartitionScanner.getLocal(),
                    8,
                    6,
                    GuiColors.ReshuffleTitle.getColor());
        } else if (healthMode) {
            this.fontRendererObj
                    .drawString(GuiText.ReshuffleHealthTitle.getLocal(), 8, 6, GuiColors.ReshuffleTitle.getColor());
        } else {
            this.fontRendererObj.drawString(
                    this.getGuiDisplayName(GuiText.StorageReshuffle.getLocal()),
                    8,
                    6,
                    GuiColors.ReshuffleTitle.getColor());
            final String statusLabel = GuiText.ReshuffleStatusLabel.getLocal();
            final String statusValue;
            final int statusColor;
            final ReshuffleState rs = this.container.getReshuffleState();
            if (rs.running) {
                if (rs.extracting) {
                    statusValue = GuiText.ReshuffleStatusExtracting.getLocal();
                    statusColor = GuiColors.ReshuffleStatusExtracting.getColor();
                } else {
                    statusValue = GuiText.ReshuffleStatusInjecting.getLocal();
                    statusColor = GuiColors.ReshuffleStatusInjecting.getColor();
                }
            } else if (rs.complete) {
                statusValue = GuiText.ReshuffleStatusComplete.getLocal();
                statusColor = GuiColors.ReshuffleStatusComplete.getColor();
            } else if (rs.failed) {
                statusValue = GuiText.ReshuffleStatusFailed.getLocal();
                statusColor = GuiColors.ReshuffleStatusFailed.getColor();
            } else if (rs.cancelled) {
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

        if (scanMode) {
            drawScanContent(offsetX, offsetY, mouseX, mouseY);
        } else if (healthMode) {
            drawHealthContent(offsetX, offsetY, mouseX, mouseY);
        } else {
            drawReshuffleContent(offsetX, offsetY, mouseX, mouseY);
        }

        if (reshuffleMode && this.startCancelButton != null) {
            this.startCancelButton.displayString = this.container.getReshuffleState().running
                    ? GuiText.ReshuffleCancel.getLocal()
                    : GuiText.ReshuffleStart.getLocal();
        }

        if (scanMode && this.hoveredRow != -1) {
            final int relX = mouseX - this.guiLeft;
            final int relY = mouseY - this.guiTop;
            final int row = (relY - SCAN_YO) / SCAN_ROW_H;
            final int locX = SCAN_XO + SCAN_ROW_W - LOCATE_BG_SIZE;
            final int locY = SCAN_YO + row * SCAN_ROW_H;
            if (relX >= locX && relX < locX + LOCATE_BG_SIZE && relY >= locY && relY < locY + SCAN_ROW_H) {
                this.drawTooltip(mouseX - this.guiLeft, mouseY - this.guiTop, GuiText.ReshuffleLocate.getLocal());
            } else if (relX >= SCAN_XO && relX < locX) {
                final int visualRow = this.hoveredRow - this.scanScrollbar.getCurrentScroll();
                final IAEStack<?> stack = this.scanRepo.getReferenceStack(visualRow);
                if (stack instanceof IAEItemStack ais && ais.getItemStack() != null
                        && ais.getItemStack().hasTagCompound()) {
                    final NBTTagList cellInfoList = ais.getItemStack().getTagCompound().getTagList("cellInfo", 10);
                    final List<String> lines = new ArrayList<>();
                    lines.add(EnumChatFormatting.WHITE.toString() + EnumChatFormatting.BOLD + ais.getDisplayName());
                    for (int ci = 0; ci < cellInfoList.tagCount(); ci++) {
                        final NBTTagCompound ce = cellInfoList.getCompoundTagAt(ci);
                        lines.add(
                                EnumChatFormatting.DARK_GRAY + GuiText.ReshuffleScanDuplicatePartition.getLocal()
                                        + " "
                                        + (ci + 1));
                        lines.add(EnumChatFormatting.GRAY + ce.getString("cellName"));
                        lines.add(
                                GuiText.ReshuffleTooltipCoords.getLocal(
                                        ce.getInteger("x"),
                                        ce.getInteger("y"),
                                        ce.getInteger("z"),
                                        ce.getInteger("dim"),
                                        ce.getInteger("slot")));
                    }
                    this.drawTooltip(mouseX - this.guiLeft, mouseY - this.guiTop, String.join("\n", lines));
                }
            }
        }

        if (healthMode && this.hoveredRow != -1) {
            final int relX = mouseX - this.guiLeft;
            final int relY = mouseY - this.guiTop;
            if (relY >= SCAN_YO && relY < SCAN_YO + SCAN_ROWS * SCAN_ROW_H) {
                if (this.hoveredRow >= 0 && this.hoveredRow < this.healthEntries.size()) {
                    final HealthEntry e = this.healthEntries.get(this.hoveredRow);
                    final int locX = SCAN_XO + SCAN_ROW_W - LOCATE_BG_SIZE;
                    if (relX >= locX && relX < locX + LOCATE_BG_SIZE) {
                        this.drawTooltip(
                                mouseX - this.guiLeft,
                                mouseY - this.guiTop,
                                GuiText.ReshuffleLocate.getLocal());
                    } else {
                        final String fillPct = String.format("%.2f%%", e.fillPct());
                        final List<String> lines = new ArrayList<>();
                        lines.add(EnumChatFormatting.WHITE + e.cellName);
                        lines.add(
                                GuiText.ReshuffleHealthTooltipBytes
                                        .getLocal(formatBytes(e.bytesUsed), formatBytes(e.bytesTotal), fillPct));
                        lines.add(GuiText.ReshuffleHealthTooltipTypes.getLocal(e.typesUsed, e.typesTotal));
                        if (!e.topItems.isEmpty()) {
                            String typePrefix = "";
                            if (e.stackTypeId != null && !e.stackTypeId.isEmpty()) {
                                final IAEStackType<?> stackType = AEStackTypeRegistry.getType(e.stackTypeId);
                                if (stackType != null) {
                                    typePrefix = " " + EnumChatFormatting.GRAY + "[" + stackType.getDisplayName() + "]";
                                }
                            }
                            lines.add(GuiText.ReshuffleHealthTopItems.getLocal() + typePrefix);
                            for (final String[] item : e.topItems) {
                                lines.add(GuiText.ReshuffleHealthTopItemEntry.getLocal(item[0], formatCount(item[1])));
                            }
                        }
                        lines.add(GuiText.ReshuffleTooltipCoords.getLocal(e.x, e.y, e.z, e.dim, e.slot));
                        this.drawTooltip(mouseX - this.guiLeft, mouseY - this.guiTop, String.join("\n", lines));
                    }
                }
            }
        }

        if (reshuffleMode && this.startCancelButton != null && this.startCancelButton.visible) {
            final int bx = this.startCancelButton.xPosition - offsetX;
            final int by = this.startCancelButton.yPosition - offsetY;
            if (mouseX - offsetX >= bx && mouseX - offsetX < bx + this.startCancelButton.width
                    && mouseY - offsetY >= by
                    && mouseY - offsetY < by + this.startCancelButton.height) {
                this.drawTooltip(
                        mouseX - this.guiLeft,
                        mouseY - this.guiTop,
                        this.container.getReshuffleState().running ? GuiText.ReshuffleCancel.getLocal()
                                : GuiText.ReshuffleStart.getLocal());
            }
        }
        if ((scanMode || healthMode) && this.scanButton != null && this.scanButton.visible) {
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
        final ReshuffleState rs = this.container.getReshuffleState();
        final int displayProcessed;
        final int displayTotal;
        if (rs.running && rs.phaseTotal > 0) {
            displayProcessed = rs.phaseProcessed;
            displayTotal = rs.phaseTotal;
        } else if (rs.typeCount > 0) {
            displayProcessed = rs.typeCount;
            displayTotal = rs.typeCount;
        } else {
            displayProcessed = rs.processedItems;
            displayTotal = rs.totalItems;
        }
        this.fontRendererObj.drawString(
                GuiText.ReshuffleTotalItems.getLocal(displayProcessed, displayTotal),
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
            drawRect(barX - 1, barY - 1, barX + barW + 1, barY + 7, GuiColors.ReshuffleProgressBorder.getColor());
            drawRect(barX, barY, barX + barW, barY + 6, GuiColors.ReshuffleProgressBackground.getColor());
            if (this.container.getReshuffleState().progressPercent > 0) {
                final int fill = (int) Math.floor(this.container.getReshuffleState().progressPercent * barW / 100.0);
                final int alpha = 255 - this.container.getReshuffleState().progressPercent * 255 / 100;
                this.drawGradientRect(
                        barX,
                        barY,
                        barX + fill,
                        barY + 6,
                        GuiColors.ReshuffleProgressFillStart.getColor(),
                        (GuiColors.ReshuffleProgressFillEnd.getColor() & 0xFFFFFF00) | alpha);
                drawRect(
                        barX + fill - 1,
                        barY,
                        barX + fill + 1,
                        barY + 6,
                        GuiColors.ReshuffleProgressMarker.getColor());
            }
            this.fontRendererObj.drawString(
                    this.container.getReshuffleState().progressPercent + "%",
                    barX + barW + 3,
                    barY,
                    GuiColors.ReshuffleTitle.getColor());
        }
    }

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
            final int locBtnX = SCAN_XO + SCAN_ROW_W - LOCATE_BG_SIZE;

            if (isHovered) {
                drawRect(SCAN_XO, rowY, locBtnX, rowY + SCAN_ROW_H, GuiColors.ReshuffleScanRowHover.getColor());
            }

            final ItemStack is = this.scanRepo.getItem(i);
            if (is != null) {
                this.drawItem(SCAN_XO + 1, rowY + 1, is);
            }

            String name = Platform.getItemDisplayName(is);
            if (name.isEmpty()) name = ais.getDisplayName();
            if (name == null) name = "";

            final int maxNameW = (int) ((locBtnX - SCAN_XO - 22 - 2) / TEXT_SCALE);
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

            this.locateButton.xPosition = locBtnX + 4;
            this.locateButton.yPosition = rowY + 1;
            this.locateButton.drawButton(this.mc, mouseX - this.guiLeft, mouseY - this.guiTop);
        }
    }

    private static final DecimalFormat BYTES_FORMAT = new DecimalFormat("#.##");

    private static String formatBytes(final long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double v = bytes;
        final String[] units = { "KB", "MB", "GB", "TB", "PB", "EB" };
        int i = 0;
        while (v >= 1024.0 && i < units.length - 1) {
            v /= 1024.0;
            i++;
        }
        return BYTES_FORMAT.format(v) + ' ' + units[i];
    }

    private static String formatCount(final String countStr) {
        try {
            final long v = Long.parseLong(countStr);
            if (v >= 1_000_000_000_000L) return String.format("%.1fT", v / 1_000_000_000_000.0);
            if (v >= 1_000_000_000L) return String.format("%.1fB", v / 1_000_000_000.0);
            if (v >= 1_000_000L) return String.format("%.1fM", v / 1_000_000.0);
            if (v >= 10_000L) return String.format("%.1fK", v / 1_000.0);
            return countStr;
        } catch (final NumberFormatException e) {
            return countStr;
        }
    }

    private void drawHealthContent(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        if (this.healthEntries.isEmpty()) {
            this.fontRendererObj.drawString(
                    GuiText.ReshuffleHealthEmpty.getLocal(),
                    SCAN_XO + 4,
                    SCAN_YO + SCAN_ROWS * SCAN_ROW_H + 10,
                    GuiColors.DefaultBlack.getColor());
            return;
        }

        final int scroll = this.healthScrollbar.getCurrentScroll();

        for (int i = 0; i < SCAN_ROWS; i++) {
            final int absRow = i + scroll;
            if (absRow >= this.healthEntries.size()) break;
            final HealthEntry e = this.healthEntries.get(absRow);

            final boolean isHovered = this.hoveredRow == absRow;
            final int rowY = SCAN_YO + i * SCAN_ROW_H;
            final int locBtnX = SCAN_XO + SCAN_ROW_W - LOCATE_BG_SIZE;

            if (isHovered) {
                drawRect(SCAN_XO, rowY, locBtnX, rowY + SCAN_ROW_H, GuiColors.ReshuffleScanRowHover.getColor());
            }

            if (e.cellItemId != null && !e.cellItemId.isEmpty()) {
                final net.minecraft.item.Item cellItem = (net.minecraft.item.Item) net.minecraft.item.Item.itemRegistry
                        .getObject(e.cellItemId);
                if (cellItem != null) {
                    this.drawItem(SCAN_XO + 1, rowY + 3, new ItemStack(cellItem, 1, e.cellMeta));
                }
            }

            final double pct = e.fillPct();
            final int barColor = pct >= 90.0 ? GuiColors.CellHealthCrit.getColor()
                    : pct >= 75.0 ? GuiColors.CellHealthWarn.getColor() : GuiColors.CellHealthOk.getColor();

            final int barLeft = SCAN_XO + 20;
            final int barTop = rowY + 17;
            final int barW = 60;
            drawRect(barLeft, barTop, barLeft + barW, barTop + 3, GuiColors.CellHealthBarBackground.getColor());
            final int fillW = (int) Math.round(pct * barW / 100.0);
            if (fillW > 0) drawRect(barLeft, barTop, barLeft + fillW, barTop + 3, barColor);

            String cellName = e.cellName;
            final String typesStr = e.typesUsed + "/" + e.typesTotal + " " + GuiText.Types.getLocal();
            final String pctStr = String.format("%.2f%%", pct);
            final int maxNameW = (int) ((locBtnX - SCAN_XO - 22 - 2) / TEXT_SCALE);
            GL11.glPushMatrix();
            GL11.glScalef(TEXT_SCALE, TEXT_SCALE, 1.0f);
            final float inv = 1.0f / TEXT_SCALE;
            while (!cellName.isEmpty() && this.fontRendererObj.getStringWidth(cellName) > maxNameW) {
                cellName = cellName.substring(0, cellName.length() - 1);
            }
            this.fontRendererObj.drawString(
                    cellName,
                    (int) ((SCAN_XO + 20) * inv),
                    (int) ((rowY + 3) * inv),
                    GuiColors.DefaultBlack.getColor());
            this.fontRendererObj.drawString(pctStr, (int) ((SCAN_XO + 20) * inv), (int) ((rowY + 11) * inv), barColor);
            this.fontRendererObj.drawString(
                    typesStr,
                    (int) ((barLeft + barW + 4) * inv),
                    (int) ((rowY + 11) * inv),
                    GuiColors.NetworkStatusItemCount.getColor());
            GL11.glPopMatrix();

            this.locateButton.xPosition = locBtnX + 4;
            this.locateButton.yPosition = rowY + 1;
            this.locateButton.drawButton(this.mc, mouseX - this.guiLeft, mouseY - this.guiTop);
        }
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
