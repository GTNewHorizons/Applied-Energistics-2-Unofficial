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
import java.text.NumberFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.implementations.guiobjects.INetworkTool;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.NamedDimensionalCoord;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiContextMenu;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.me.ItemRepo;
import appeng.client.render.highlighter.BlockPosHighlighter;
import appeng.container.implementations.ContainerNetworkStatus;
import appeng.core.AEConfig;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketClick;
import appeng.core.sync.packets.PacketNetworkStatusSelected;
import appeng.util.Platform;

public class GuiNetworkStatus extends AEBaseGui implements ISortSource {

    private final ItemRepo repo;
    private final int rows = 4;
    private GuiImgButton units;
    private GuiImgButton cell;
    private int tooltip = -1;
    private final DecimalFormat df;
    private final boolean isAdvanced;
    private boolean isConsume;
    private final StringBuilder sb;
    private final String Equal;
    private final String Minus;
    private double totalBytes;
    private double usedBytes;
    private final int counterNumberGap = 20;
    private GuiContextMenu menu;

    public GuiNetworkStatus(final InventoryPlayer inventoryPlayer, final INetworkTool te) {
        super(new ContainerNetworkStatus(inventoryPlayer, te));
        final GuiScrollbar scrollbar = new GuiScrollbar();

        this.sb = new StringBuilder();
        this.df = new DecimalFormat("#.##");
        this.setScrollBar(scrollbar);
        this.repo = new ItemRepo(scrollbar, this);
        this.ySize = 183;
        this.xSize = 195;
        this.repo.setRowSize(5);
        this.isAdvanced = te.getSize() != 3;
        this.isConsume = true;
        this.Equal = "=";
        this.Minus = "-";
        menu = new GuiContextMenu(5) {

            @Override
            public void action(int i) {
                NamedDimensionalCoord dc = (NamedDimensionalCoord) this.list.get(i);
                if (isShiftKeyDown()) {
                    BlockPosHighlighter.highlightBlocks(
                            mc.thePlayer,
                            Collections.singletonList(dc),
                            dc.getCustomName(),
                            PlayerMessages.MachineHighlighted.getUnlocalized(),
                            PlayerMessages.MachineInOtherDim.getUnlocalized());
                    mc.thePlayer.closeScreen();
                } else NetworkHandler.instance
                        .sendToServer(new PacketClick(dc.x, dc.y, dc.z, ForgeDirection.UP.ordinal(), 0, 0, 0));
            }

            @Override
            public String getDrawText(int i) {
                NamedDimensionalCoord dc = (NamedDimensionalCoord) this.list.get(i);
                if (dc.getCustomName().isEmpty()) return "x:" + dc.x + " y:" + dc.y + " z:" + dc.z;
                return dc.getCustomName();
            }
        };
    }

    @Override
    protected boolean mouseWheelEvent(int x, int y, int wheel) {
        if (menu.mouseWheelEvent(x, y, wheel)) {
            return true;
        }
        return super.mouseWheelEvent(x, y, wheel);
    }

    @Override
    protected void mouseClicked(int xCoord, int yCoord, int btn) {

        // Check if the context menu is active and handle it
        if (menu.mouseClick(xCoord, yCoord, btn)) {
            return;
        }

        ItemStack is = null;
        if (tooltip > -1) {
            final IAEStack<?> aeStack = repo.getReferenceStack(tooltip);

            if (aeStack instanceof IAEItemStack ais) {
                is = ais.getItemStack();
            }

        }

        if (is == null || !is.hasTagCompound()) {
            super.mouseClicked(xCoord, yCoord, btn);
            return;
        }

        NBTTagCompound tag = is.getTagCompound();
        switch (btn) {
            case 0 -> {
                if (!isShiftKeyDown()) break;
                // show all blocks in the world
                List<NamedDimensionalCoord> dcl = NamedDimensionalCoord.readAsListFromNBTNamed(tag);
                Map<NamedDimensionalCoord, String[]> namedCoordsMessage = new HashMap<>(dcl.size());
                for (NamedDimensionalCoord dc : dcl) {
                    namedCoordsMessage.put(
                            dc,
                            dc.getCustomName().isEmpty()
                                    ? new String[] { PlayerMessages.MachineHighlighted.getUnlocalized(),
                                            PlayerMessages.MachineInOtherDim.getUnlocalized() }
                                    : new String[] { PlayerMessages.MachineHighlightedNamed.getUnlocalized(),
                                            PlayerMessages.MachineInOtherDimNamed.getUnlocalized() });
                }
                BlockPosHighlighter.highlightNamedBlocks(mc.thePlayer, namedCoordsMessage, is.getDisplayName());
                mc.thePlayer.closeScreen();
            }

            case 1 -> {
                // open context menu
                List<NamedDimensionalCoord> dcl = NamedDimensionalCoord.readAsListFromNBTNamed(tag);
                menu.init(dcl, xCoord, yCoord);
            }
        }
        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        final boolean backwards = Mouse.isButtonDown(1);
        boolean oldConsume = this.isConsume;

        if (btn == this.units) {
            if (this.isConsume) {
                AEConfig.instance.nextPowerUnit(backwards);
                this.units.set(AEConfig.instance.selectedPowerUnit());
            } else {
                this.isConsume = !this.isConsume;
            }
        } else if (btn == this.cell) {
            if (!this.isConsume) {
                AEConfig.instance.nextCellType(backwards);
                this.cell.set(AEConfig.instance.selectedCellType());
            } else {
                this.isConsume = !this.isConsume;
            }
        }

        if (oldConsume != this.isConsume) {
            try {
                NetworkHandler.instance.sendToServer(new PacketNetworkStatusSelected(this.isConsume));
            } catch (IOException ignored) {
                // XD
            }
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        this.units = new GuiImgButton(
                this.guiLeft - 18,
                this.guiTop + 8,
                Settings.POWER_UNITS,
                AEConfig.instance.selectedPowerUnit());
        this.buttonList.add(this.units);
        if (this.isAdvanced) {
            this.cell = new GuiImgButton(
                    this.guiLeft - 18,
                    this.guiTop + 28,
                    Settings.CELL_TYPE,
                    AEConfig.instance.selectedCellType());
            this.buttonList.add(this.cell);
        }
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {

        final int gx = (this.width - this.xSize) / 2;
        final int gy = (this.height - this.ySize) / 2;

        this.tooltip = -1;

        int y = 0;
        int x = 0;
        final int viewEnd = Math.min(5 * 4, this.repo.size());

        for (int z = 0; z < viewEnd; z++) {
            final int minX = gx + 14 + x * 31;
            final int minY = gy + 41 + y * 18;

            if (!menu.isActive() && minX < mouseX && minX + 28 > mouseX) {
                if (minY < mouseY && minY + 20 > mouseY) {
                    this.tooltip = z;
                    break;
                }
            }

            x++;

            if (x > 4) {
                y++;
                x = 0;
            }
        }

        super.drawScreen(mouseX, mouseY, btn);
        menu.draw(mouseX, mouseY);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        if (this.isConsume) drawConsume();
        else {
            switch (AEConfig.instance.selectedCellType()) {
                case ITEM -> drawItemInfo();
                case FLUID -> drawFluidInfo();
                case ESSENTIA -> drawEssentiaInfo();
            }

        }
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/networkstatus.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }

    public void postUpdate(final List<IAEStack<?>> list) {
        this.repo.clear();

        for (final IAEStack<?> is : list) {
            this.repo.postUpdate(is);
        }

        this.repo.updateView();
        this.setScrollBar();
    }

    private void setScrollBar() {
        final int size = this.repo.size();
        this.getScrollBar().setTop(39).setLeft(175).setHeight(78);
        this.getScrollBar().setRange(0, (size + 4) / 5 - this.rows, 1);
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

    private void drawConsume() {
        final ContainerNetworkStatus ns = (ContainerNetworkStatus) this.inventorySlots;
        String tempStr;
        this.fontRendererObj
                .drawString(GuiText.NetworkDetails.getLocal(), 8, 6, GuiColors.NetworkStatusDetails.getColor());

        if (ns.isPowerInfinite()) {
            this.fontRendererObj.drawString(
                    GuiText.StoredPower.getLocal() + ": ∞",
                    13,
                    16,
                    GuiColors.NetworkStatusStoredPower.getColor());
            this.fontRendererObj.drawString(
                    GuiText.MaxPower.getLocal() + ": ∞",
                    13,
                    26,
                    GuiColors.NetworkStatusMaxPower.getColor());
        } else {
            this.fontRendererObj.drawString(
                    GuiText.StoredPower.getLocal() + ": " + Platform.formatPowerLong(ns.getCurrentPower(), false),
                    13,
                    16,
                    GuiColors.NetworkStatusStoredPower.getColor());
            this.fontRendererObj.drawString(
                    GuiText.MaxPower.getLocal() + ": " + Platform.formatPowerLong(ns.getMaxPower(), false),
                    13,
                    26,
                    GuiColors.NetworkStatusMaxPower.getColor());
        }

        this.fontRendererObj.drawString(
                GuiText.PowerInputRate.getLocal() + ": " + Platform.formatPowerLong(ns.getAverageAddition(), true),
                13,
                143 - 10,
                GuiColors.NetworkStatusPowerInputRate.getColor());
        this.fontRendererObj.drawString(
                GuiText.PowerUsageRate.getLocal() + ": " + Platform.formatPowerLong(ns.getPowerUsage(), true),
                13,
                143 - 20,
                GuiColors.NetworkStatusPowerUsageRate.getColor());

        // Item byte status
        totalBytes = Double.longBitsToDouble(ns.getItemBytesTotal());
        usedBytes = Double.longBitsToDouble(ns.getItemBytesUsed());
        tempStr = totalBytes == 0 ? "" : " (" + df.format(usedBytes * 100d / totalBytes) + "%)";
        this.fontRendererObj.drawString(
                GuiText.Items.getLocal() + ": "
                        + Platform.formatByteDouble(usedBytes)
                        + " / "
                        + Platform.formatByteDouble(totalBytes)
                        + tempStr,
                13,
                143,
                GuiColors.DefaultBlack.getColor());

        // Fluid byte status
        totalBytes = Double.longBitsToDouble(ns.getFluidBytesTotal());
        usedBytes = Double.longBitsToDouble(ns.getFluidBytesUsed());
        tempStr = totalBytes == 0 ? "" : " (" + df.format(usedBytes * 100d / totalBytes) + "%)";
        this.fontRendererObj.drawString(
                GuiText.Fluids.getLocal() + ": "
                        + Platform.formatByteDouble(usedBytes)
                        + " / "
                        + Platform.formatByteDouble(totalBytes)
                        + tempStr,
                13,
                143 + 10,
                GuiColors.DefaultBlack.getColor());

        // Essential byte status
        totalBytes = Double.longBitsToDouble(ns.getEssentiaBytesTotal());
        usedBytes = Double.longBitsToDouble(ns.getEssentiaBytesUsed());
        tempStr = totalBytes == 0 ? "" : " (" + df.format(usedBytes * 100d / totalBytes) + "%)";
        this.fontRendererObj.drawString(
                GuiText.Essentias.getLocal() + ": "
                        + Platform.formatByteDouble(usedBytes)
                        + " / "
                        + Platform.formatByteDouble(totalBytes)
                        + tempStr,
                13,
                143 + 20,
                GuiColors.DefaultBlack.getColor());

        this.drawItemRepo();
    }

    private void drawItemRepo() {
        final int sectionLength = 30;

        int x = 0;
        int y = 0;
        final int xo = 12;
        final int yo = 42;
        final int viewStart = 0; // myScrollBar.getCurrentScroll() * 5;
        final int viewEnd = viewStart + 5 * 4;

        String toolTip = "";
        int toolPosX = 0;
        int toolPosY = 0;

        for (int z = viewStart; z < Math.min(viewEnd, this.repo.size()); z++) {
            final IAEStack<?> aes = this.repo.getReferenceStack(z);
            if (aes instanceof IAEItemStack refStack) {
                GL11.glPushMatrix();
                GL11.glScaled(0.5, 0.5, 0.5);

                String str = Long.toString(refStack.getStackSize());
                if (refStack.getStackSize() >= 10000) {
                    str = Long.toString(refStack.getStackSize() / 1000) + 'k';
                }

                final int w = this.fontRendererObj.getStringWidth(str);
                this.fontRendererObj.drawString(
                        str,
                        (int) ((x * sectionLength + xo + sectionLength - 19 - (w * 0.5)) * 2),
                        (y * 18 + yo + 6) * 2,
                        GuiColors.NetworkStatusItemCount.getColor());

                GL11.glPopMatrix();
                final int posX = x * sectionLength + xo + sectionLength - 18;
                final int posY = y * 18 + yo;

                if (this.tooltip == z - viewStart) {
                    toolTip = Platform.getItemDisplayName(this.repo.getItem(z));

                    toolTip += ('\n' + GuiText.Installed.getLocal()
                            + ": "
                            + NumberFormat.getNumberInstance(Locale.US).format(refStack.getStackSize()));
                    if (refStack.getCountRequestable() > 0) {
                        toolTip += ('\n' + GuiText.EnergyDrain.getLocal()
                                + ": "
                                + Platform.formatPowerLong(refStack.getCountRequestable(), true));
                    }
                    toolPosX = x * sectionLength + xo + sectionLength - 8;
                    toolPosY = y * 18 + yo;
                }

                this.drawItem(posX, posY, this.repo.getItem(z));

                x++;

                if (x > 4) {
                    y++;
                    x = 0;
                }
            }
        }

        if (this.tooltip >= 0 && toolTip.length() > 0) {
            this.drawTooltip(toolPosX, toolPosY + 10, toolTip);
        }
    }

    private GuiColors getCorrespondingColor(final double percentage) {
        if (Double.isNaN(percentage)) {
            return GuiColors.DefaultBlack;
        } else {
            if (percentage > 95) {
                return GuiColors.CellStatusRed;
            } else if (percentage > 75) {
                return GuiColors.CellStatusOrange;
            } else {
                return GuiColors.DefaultBlack;
            }
        }
    }

    private String getProgressBar(final double percentage) {
        int count = (int) Math.round(percentage / 5d);
        sb.setLength(0);
        sb.append('<');
        for (int i = 0; i < 20; i++) {
            if (i < count) sb.append(this.Equal);
            else sb.append(this.Minus);
        }
        sb.append('>');
        return sb.toString();
    }

    private void drawItemInfo() {
        final ContainerNetworkStatus ns = (ContainerNetworkStatus) this.inventorySlots;
        String tempStr;
        double tempDouble;
        this.fontRendererObj
                .drawString(GuiText.NetworkBytesDetails.getLocal(), 8, 6, GuiColors.DefaultBlack.getColor());
        this.fontRendererObj.drawString(
                GuiText.NetworkItemCellCount.getLocal() + " : " + ns.getItemCellCount(),
                13,
                16,
                GuiColors.DefaultBlack.getColor());

        this.drawAllCellCount(ns.getItemCellG(), ns.getItemCellB(), ns.getItemCellO(), ns.getItemCellR());

        // Item byte status
        totalBytes = Double.longBitsToDouble(ns.getItemBytesTotal());
        usedBytes = Double.longBitsToDouble(ns.getItemBytesUsed());
        tempDouble = usedBytes * 100d / totalBytes;
        tempStr = totalBytes == 0 ? " (0%)" : " (" + df.format(tempDouble) + "%)";
        this.fontRendererObj.drawString(
                GuiText.BytesInfo.getLocal() + ": "
                        + Platform.formatByteDouble(usedBytes)
                        + " / "
                        + Platform.formatByteDouble(totalBytes),
                13,
                143 - 20,
                getCorrespondingColor(tempDouble).getColor());
        this.fontRendererObj.drawString(
                getProgressBar(tempDouble) + tempStr,
                13,
                143 - 10,
                getCorrespondingColor(tempDouble).getColor());

        // Item type status
        tempDouble = ns.getItemTypesUsed() * 100d / ns.getItemTypesTotal();
        tempStr = ns.getItemTypesTotal() == 0 ? " (0%)" : " (" + df.format(tempDouble) + "%)";
        this.fontRendererObj.drawString(
                GuiText.TypesInfo.getLocal() + ": " + ns.getItemTypesUsed() + " / " + ns.getItemTypesTotal(),
                13,
                143,
                getCorrespondingColor(tempDouble).getColor());
        this.fontRendererObj.drawString(
                getProgressBar(tempDouble) + tempStr,
                13,
                143 + 10,
                getCorrespondingColor(tempDouble).getColor());

        this.drawItemRepo();
    }

    private void drawFluidInfo() {
        final ContainerNetworkStatus ns = (ContainerNetworkStatus) this.inventorySlots;
        String tempStr;
        double tempDouble;
        this.fontRendererObj
                .drawString(GuiText.NetworkBytesDetails.getLocal(), 8, 6, GuiColors.DefaultBlack.getColor());
        this.fontRendererObj.drawString(
                GuiText.NetworkFluidCellCount.getLocal() + " : " + ns.getFluidCellCount(),
                13,
                16,
                GuiColors.DefaultBlack.getColor());

        this.drawAllCellCount(ns.getFluidCellG(), ns.getFluidCellB(), ns.getFluidCellO(), ns.getFluidCellR());

        // Fluid byte status
        totalBytes = Double.longBitsToDouble(ns.getFluidBytesTotal());
        usedBytes = Double.longBitsToDouble(ns.getFluidBytesUsed());
        tempDouble = usedBytes * 100d / totalBytes;
        tempStr = totalBytes == 0 ? " (0%)" : " (" + df.format(tempDouble) + "%)";
        this.fontRendererObj.drawString(
                GuiText.BytesInfo.getLocal() + ": "
                        + Platform.formatByteDouble(usedBytes)
                        + " / "
                        + Platform.formatByteDouble(totalBytes),
                13,
                143 - 20,
                getCorrespondingColor(tempDouble).getColor());
        this.fontRendererObj.drawString(
                getProgressBar(tempDouble) + tempStr,
                13,
                143 - 10,
                getCorrespondingColor(tempDouble).getColor());

        // Fluid type status
        tempDouble = ns.getFluidTypesUsed() * 100d / ns.getFluidTypesTotal();
        tempStr = ns.getFluidTypesTotal() == 0 ? " (0%)" : " (" + df.format(tempDouble) + "%)";
        this.fontRendererObj.drawString(
                GuiText.TypesInfo.getLocal() + ": " + ns.getFluidTypesUsed() + " / " + ns.getFluidTypesTotal(),
                13,
                143,
                getCorrespondingColor(tempDouble).getColor());
        this.fontRendererObj.drawString(
                getProgressBar(tempDouble) + tempStr,
                13,
                143 + 10,
                getCorrespondingColor(tempDouble).getColor());

        this.drawItemRepo();
    }

    private void drawEssentiaInfo() {
        final ContainerNetworkStatus ns = (ContainerNetworkStatus) this.inventorySlots;
        String tempStr;
        double tempDouble;
        this.fontRendererObj
                .drawString(GuiText.NetworkBytesDetails.getLocal(), 8, 6, GuiColors.DefaultBlack.getColor());
        this.fontRendererObj.drawString(
                GuiText.NetworkEssentiaCellCount.getLocal() + " : " + ns.getEssentiaCellCount(),
                13,
                16,
                GuiColors.DefaultBlack.getColor());

        this.drawAllCellCount(
                ns.getEssentiaCellG(),
                ns.getEssentiaCellB(),
                ns.getEssentiaCellO(),
                ns.getEssentiaCellR());

        // Essentia byte status
        totalBytes = Double.longBitsToDouble(ns.getEssentiaBytesTotal());
        usedBytes = Double.longBitsToDouble(ns.getEssentiaBytesUsed());
        tempDouble = usedBytes * 100d / totalBytes;
        tempStr = totalBytes == 0 ? " (0%)" : " (" + df.format(tempDouble) + "%)";
        this.fontRendererObj.drawString(
                GuiText.BytesInfo.getLocal() + ": "
                        + Platform.formatByteDouble(usedBytes)
                        + " / "
                        + Platform.formatByteDouble(totalBytes),
                13,
                143 - 20,
                getCorrespondingColor(tempDouble).getColor());
        this.fontRendererObj.drawString(
                getProgressBar(tempDouble) + tempStr,
                13,
                143 - 10,
                getCorrespondingColor(tempDouble).getColor());

        // Essentia type status
        tempDouble = ns.getEssentiaTypesUsed() * 100d / ns.getEssentiaTypesTotal();
        tempStr = ns.getEssentiaTypesTotal() == 0 ? " (0%)" : " (" + df.format(tempDouble) + "%)";
        this.fontRendererObj.drawString(
                GuiText.TypesInfo.getLocal() + ": " + ns.getEssentiaTypesUsed() + " / " + ns.getEssentiaTypesTotal(),
                13,
                143,
                getCorrespondingColor(tempDouble).getColor());
        this.fontRendererObj.drawString(
                getProgressBar(tempDouble) + tempStr,
                13,
                143 + 10,
                getCorrespondingColor(tempDouble).getColor());

        this.drawItemRepo();
    }

    private void drawAllCellCount(final long greenCellNum, final long blueCellNum, final long orangeCellNum,
            final long redCellNum) {
        this.fontRendererObj
                .drawString(GuiText.NetworkCellStatus.getLocal() + ":", 13, 27, GuiColors.DefaultBlack.getColor());

        int numStartAt = this.fontRendererObj.getStringWidth(GuiText.NetworkCellStatus.getLocal() + ":") + 20;

        this.fontRendererObj.drawString(
                String.valueOf(greenCellNum),
                numStartAt + this.counterNumberGap * 0,
                27,
                GuiColors.CellStatusGreen.getColor());
        this.fontRendererObj.drawString(
                String.valueOf(blueCellNum),
                numStartAt + this.counterNumberGap * 1,
                27,
                GuiColors.CellStatusBlue.getColor());
        this.fontRendererObj.drawString(
                String.valueOf(orangeCellNum),
                numStartAt + this.counterNumberGap * 2,
                27,
                GuiColors.CellStatusOrange.getColor());
        this.fontRendererObj.drawString(
                String.valueOf(redCellNum),
                numStartAt + this.counterNumberGap * 3,
                27,
                GuiColors.CellStatusRed.getColor());

    }
}
