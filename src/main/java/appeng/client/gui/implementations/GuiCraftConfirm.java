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

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.google.common.base.Joiner;

import appeng.api.AEApi;
import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiCraftingCPUTable;
import appeng.client.gui.widgets.GuiCraftingTree;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiSimpleImgButton;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.ICraftingCPUTableHolder;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.container.implementations.CraftingCPUStatus;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.crafting.v2.CraftingJobV2;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.integration.modules.NEI;
import appeng.parts.reporting.PartCraftingTerminal;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.parts.reporting.PartPatternTerminalEx;
import appeng.parts.reporting.PartTerminal;
import appeng.util.Platform;
import appeng.util.ReadableNumberConverter;

public class GuiCraftConfirm extends AEBaseGui implements ICraftingCPUTableHolder {

    public static final int TREE_VIEW_TEXTURE_WIDTH = 238;
    public static final int TREE_VIEW_TEXTURE_HEIGHT = 238;

    public static final int LIST_VIEW_TEXTURE_WIDTH = 238;
    public static final int LIST_VIEW_TEXTURE_HEIGHT = 206;
    public static final int LIST_VIEW_TEXTURE_BELOW_TOP_ROW_Y = 41;
    public static final int LIST_VIEW_TEXTURE_ABOVE_BOTTOM_ROW_Y = 110;
    public static final int LIST_VIEW_TEXTURE_ROW_HEIGHT = 23;
    /** How many pixels tall is the list view texture minus the space for rows of items */
    public static final int LIST_VIEW_TEXTURE_NONROW_HEIGHT = LIST_VIEW_TEXTURE_HEIGHT
            - (LIST_VIEW_TEXTURE_ABOVE_BOTTOM_ROW_Y - LIST_VIEW_TEXTURE_BELOW_TOP_ROW_Y)
            - 2 * LIST_VIEW_TEXTURE_ROW_HEIGHT;

    public enum DisplayMode {

        LIST,
        TREE;

        public DisplayMode next() {
            return switch (this) {
                case LIST -> TREE;
                case TREE -> LIST;
                default -> throw new IllegalArgumentException(this.toString());
            };
        }
    }

    protected void recalculateScreenSize() {
        switch (this.displayMode) {
            case LIST -> {
                final int maxAvailableHeight = height - 64;
                this.xSize = LIST_VIEW_TEXTURE_WIDTH;
                if (tallMode) {
                    this.rows = (maxAvailableHeight - LIST_VIEW_TEXTURE_NONROW_HEIGHT) / LIST_VIEW_TEXTURE_ROW_HEIGHT;
                    this.ySize = LIST_VIEW_TEXTURE_NONROW_HEIGHT + this.rows * LIST_VIEW_TEXTURE_ROW_HEIGHT;
                } else {
                    this.rows = 5;
                    this.ySize = LIST_VIEW_TEXTURE_HEIGHT;
                }
            }
            case TREE -> {
                this.xSize = tallMode ? width - 200 : TREE_VIEW_TEXTURE_WIDTH;
                this.ySize = tallMode ? height - 64 : TREE_VIEW_TEXTURE_HEIGHT;
                this.craftingTree.widgetW = xSize - 35;
                this.craftingTree.widgetH = ySize - 46;
            }
        }
    }

    private final ContainerCraftConfirm ccc;
    private final GuiCraftingCPUTable cpuTable;
    private final GuiCraftingTree craftingTree;

    private int rows = 5;

    private final IItemList<IAEItemStack> storage = AEApi.instance().storage().createItemList();
    private final IItemList<IAEItemStack> pending = AEApi.instance().storage().createItemList();
    private final IItemList<IAEItemStack> missing = AEApi.instance().storage().createItemList();
    private CraftingJobV2 jobTree = null;

    private final List<IAEItemStack> visual = new ArrayList<>();

    private DisplayMode displayMode = DisplayMode.LIST;
    private boolean tallMode;

    private GuiBridge OriginalGui;
    private GuiButton cancel;
    private GuiButton start;
    private GuiButton selectCPU;
    private GuiImgButton switchTallMode;
    private GuiSimpleImgButton takeScreenshot;
    private GuiTabButton switchDisplayMode;
    private int tooltip = -1;
    private ItemStack hoveredStack;

    final GuiScrollbar scrollbar;

    public GuiCraftConfirm(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(new ContainerCraftConfirm(inventoryPlayer, te));
        this.craftingTree = new GuiCraftingTree(this, 9, 19, 203, 192);
        this.tallMode = AEConfig.instance.getConfigManager().getSetting(Settings.TERMINAL_STYLE) == TerminalStyle.TALL;
        recalculateScreenSize();

        scrollbar = new GuiScrollbar();
        this.setScrollBar(scrollbar);

        this.cpuTable = new GuiCraftingCPUTable(this, ((ContainerCraftConfirm) inventorySlots).cpuTable);

        this.ccc = (ContainerCraftConfirm) this.inventorySlots;

        if (te instanceof WirelessTerminalGuiObject) {
            this.OriginalGui = GuiBridge.GUI_WIRELESS_TERM;
        }

        if (te instanceof PartTerminal) {
            this.OriginalGui = GuiBridge.GUI_ME;
        }

        if (te instanceof PartCraftingTerminal) {
            this.OriginalGui = GuiBridge.GUI_CRAFTING_TERMINAL;
        }

        if (te instanceof PartPatternTerminal) {
            this.OriginalGui = GuiBridge.GUI_PATTERN_TERMINAL;
        }

        if (te instanceof PartPatternTerminalEx) {
            this.OriginalGui = GuiBridge.GUI_PATTERN_TERMINAL_EX;
        }
    }

    @Override
    public GuiCraftingCPUTable getCPUTable() {
        return cpuTable;
    }

    boolean isAutoStart() {
        return ((ContainerCraftConfirm) this.inventorySlots).isAutoStart();
    }

    @Override
    public void initGui() {
        recalculateScreenSize();
        super.initGui();

        this.setScrollBar();

        this.start = new GuiButton(
                0,
                this.guiLeft + this.xSize - 76,
                this.guiTop + this.ySize - 25,
                50,
                20,
                GuiText.Start.getLocal());
        this.start.enabled = false;
        this.buttonList.add(this.start);

        this.selectCPU = new GuiButton(
                0,
                this.guiLeft + (219 - 180) / 2,
                this.guiTop + this.ySize - 68,
                180,
                20,
                GuiText.CraftingCPU.getLocal() + ": " + GuiText.Automatic);
        this.selectCPU.enabled = false;
        this.buttonList.add(this.selectCPU);

        this.cancel = new GuiButton(
                0,
                this.guiLeft + 6,
                this.guiTop + this.ySize - 25,
                50,
                20,
                GuiText.Cancel.getLocal());
        this.buttonList.add(this.cancel);

        this.switchTallMode = new GuiImgButton(
                this.guiLeft - 18,
                this.guiTop + 166,
                Settings.TERMINAL_STYLE,
                tallMode ? TerminalStyle.TALL : TerminalStyle.SMALL);
        this.buttonList.add(switchTallMode);

        this.takeScreenshot = new GuiSimpleImgButton(
                this.guiLeft - 18,
                this.guiTop + 184,
                16 * 9,
                ButtonToolTips.SaveAsImage.getLocal());
        this.buttonList.add(takeScreenshot);

        this.switchDisplayMode = new GuiTabButton(
                this.guiLeft + this.xSize - 25,
                this.guiTop - 4,
                13 * 16 + 3,
                GuiText.SwitchCraftingSimulationDisplayMode.getLocal(),
                itemRender);
        this.switchDisplayMode.setHideEdge(1);
        this.buttonList.add(this.switchDisplayMode);
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {
        this.updateCPUButtonText();

        cpuTable.drawScreen();

        this.start.enabled = !(this.ccc.hasNoCPU() || this.isSimulation());
        if (this.start.enabled) {
            CraftingCPUStatus selected = this.cpuTable.getContainer().getSelectedCPU();
            if (selected == null || selected.getStorage() < this.ccc.getUsedBytes() || selected.isBusy()) {
                this.start.enabled = false;
            }
        }

        this.selectCPU.enabled = (displayMode == DisplayMode.LIST) && !this.isSimulation();
        this.selectCPU.visible = (displayMode == DisplayMode.LIST);
        this.takeScreenshot.visible = (displayMode == DisplayMode.TREE);

        super.drawScreen(mouseX, mouseY, btn);

        switch (displayMode) {
            case LIST -> drawListScreen(mouseX, mouseY, btn);
            case TREE -> drawTreeScreen(mouseX, mouseY, btn);
        }
    }

    private void drawListScreen(final int mouseX, final int mouseY, final float btn) {
        final int gx = (this.width - this.xSize) / 2;
        final int gy = (this.height - this.ySize) / 2;

        this.tooltip = -1;

        final int offY = 23;
        int y = 0;
        int x = 0;
        for (int z = 0; z <= 4 * this.rows; z++) {
            final int minX = gx + 9 + x * 67;
            final int minY = gy + 22 + y * offY;

            if (minX < mouseX && minX + 67 > mouseX) {
                if (minY < mouseY && minY + offY - 2 > mouseY) {
                    this.tooltip = z;
                    break;
                }
            }

            x++;

            if (x > 2) {
                y++;
                x = 0;
            }
        }
    }

    private void drawTreeScreen(final int mouseX, final int mouseY, final float btn) {
        this.craftingTree.drawTooltip(mouseX, mouseY);
    }

    private void updateCPUButtonText() {
        String btnTextText = GuiText.CraftingCPU.getLocal() + ": " + GuiText.Automatic.getLocal();
        if (this.ccc.getSelectedCpu() >= 0) // && status.selectedCpu < status.cpus.size() )
        {
            if (this.ccc.getName().length() > 0) {
                final String name = this.ccc.getName().substring(0, Math.min(20, this.ccc.getName().length()));
                btnTextText = GuiText.CraftingCPU.getLocal() + ": " + name;
            } else {
                btnTextText = GuiText.CraftingCPU.getLocal() + ": #" + this.ccc.getSelectedCpu();
            }
        }

        if (this.ccc.hasNoCPU()) {
            btnTextText = GuiText.NoCraftingCPUs.getLocal();
        }

        this.selectCPU.displayString = btnTextText;
    }

    private boolean isSimulation() {
        return ((ContainerCraftConfirm) this.inventorySlots).isSimulation();
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        cpuTable.drawFG(offsetX, offsetY, mouseX, mouseY, guiLeft, guiTop);

        final long BytesUsed = this.ccc.getUsedBytes();
        final String byteUsed = NumberFormat.getInstance().format(BytesUsed);
        final String bannerText;
        if (jobTree != null && !jobTree.getErrorMessage().isEmpty()) {
            bannerText = StatCollector.translateToLocal(jobTree.getErrorMessage());
        } else if (BytesUsed > 0) {
            bannerText = (byteUsed + ' ' + GuiText.BytesUsed.getLocal());
        } else {
            bannerText = GuiText.CalculatingWait.getLocal();
        }
        this.fontRendererObj.drawString(
                GuiText.CraftingPlan.getLocal() + " - " + bannerText,
                8,
                7,
                GuiColors.CraftConfirmCraftingPlan.getColor());

        switch (displayMode) {
            case LIST -> drawListFG(offsetX, offsetY, mouseX, mouseY);
            case TREE -> drawTreeFG(offsetX, offsetY, mouseX, mouseY);
        }
    }

    private void drawListFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        String dsp = null;

        if (this.isSimulation()) {
            dsp = GuiText.Simulation.getLocal();
        } else {
            dsp = this.ccc.getCpuAvailableBytes() > 0
                    ? (GuiText.Bytes.getLocal() + ": "
                            + NumberFormat.getInstance().format(this.ccc.getCpuAvailableBytes())
                            + " : "
                            + GuiText.CoProcessors.getLocal()
                            + ": "
                            + NumberFormat.getInstance().format(this.ccc.getCpuCoProcessors()))
                    : GuiText.Bytes.getLocal() + ": N/A : " + GuiText.CoProcessors.getLocal() + ": N/A";
        }

        final int offset = (219 - this.fontRendererObj.getStringWidth(dsp)) / 2;
        this.fontRendererObj.drawString(dsp, offset, ySize - 41, GuiColors.CraftConfirmSimulation.getColor());

        final int sectionLength = 67;

        int x = 0;
        int y = 0;
        final int xo = 9;
        final int yo = 22;
        final int viewStart = this.getScrollBar().getCurrentScroll() * 3;
        final int viewEnd = viewStart + 3 * this.rows;

        String dspToolTip = "";
        final List<String> lineList = new LinkedList<>();
        int toolPosX = 0;
        int toolPosY = 0;
        hoveredStack = null;
        final int offY = 23;

        for (int z = viewStart; z < Math.min(viewEnd, this.visual.size()); z++) {
            final IAEItemStack refStack = this.visual.get(z); // repo.getReferenceItem( z );
            if (refStack != null) {
                GL11.glPushMatrix();
                GL11.glScaled(0.5, 0.5, 0.5);

                final IAEItemStack stored = this.storage.findPrecise(refStack);
                final IAEItemStack pendingStack = this.pending.findPrecise(refStack);
                final IAEItemStack missingStack = this.missing.findPrecise(refStack);

                int lines = 0;

                if (stored != null && stored.getStackSize() > 0) {
                    lines++;
                }
                if (pendingStack != null && pendingStack.getStackSize() > 0) {
                    lines++;
                }
                if (pendingStack != null && pendingStack.getStackSize() > 0) {
                    lines++;
                }

                final int negY = ((lines - 1) * 5) / 2;
                int downY = 0;

                if (stored != null && stored.getStackSize() > 0) {
                    String str = GuiText.FromStorage.getLocal() + ": "
                            + ReadableNumberConverter.INSTANCE.toWideReadableForm(stored.getStackSize());
                    final int w = 4 + this.fontRendererObj.getStringWidth(str);
                    this.fontRendererObj.drawString(
                            str,
                            (int) ((x * (1 + sectionLength) + xo + sectionLength - 19 - (w * 0.5)) * 2),
                            (y * offY + yo + 6 - negY + downY) * 2,
                            GuiColors.CraftConfirmFromStorage.getColor());

                    if (this.tooltip == z - viewStart) {
                        lineList.add(
                                GuiText.FromStorage.getLocal() + ": "
                                        + NumberFormat.getInstance().format(stored.getStackSize()));
                    }

                    downY += 5;
                }

                boolean red = false;
                if (missingStack != null && missingStack.getStackSize() > 0) {
                    String str = GuiText.Missing.getLocal() + ": "
                            + ReadableNumberConverter.INSTANCE.toWideReadableForm(missingStack.getStackSize());
                    final int w = 4 + this.fontRendererObj.getStringWidth(str);
                    this.fontRendererObj.drawString(
                            str,
                            (int) ((x * (1 + sectionLength) + xo + sectionLength - 19 - (w * 0.5)) * 2),
                            (y * offY + yo + 6 - negY + downY) * 2,
                            GuiColors.CraftConfirmMissing.getColor());

                    if (this.tooltip == z - viewStart) {
                        lineList.add(
                                GuiText.Missing.getLocal() + ": "
                                        + NumberFormat.getInstance().format(missingStack.getStackSize()));
                    }

                    red = true;
                    downY += 5;
                }

                if (pendingStack != null && pendingStack.getStackSize() > 0) {
                    String str = GuiText.ToCraft.getLocal() + ": "
                            + ReadableNumberConverter.INSTANCE.toWideReadableForm(pendingStack.getStackSize());
                    final int w = 4 + this.fontRendererObj.getStringWidth(str);
                    this.fontRendererObj.drawString(
                            str,
                            (int) ((x * (1 + sectionLength) + xo + sectionLength - 19 - (w * 0.5)) * 2),
                            (y * offY + yo + 6 - negY + downY) * 2,
                            GuiColors.CraftConfirmToCraft.getColor());

                    if (this.tooltip == z - viewStart) {
                        lineList.add(
                                GuiText.ToCraft.getLocal() + ": "
                                        + NumberFormat.getInstance().format(pendingStack.getStackSize()));
                    }
                }

                GL11.glPopMatrix();
                final int posX = x * (1 + sectionLength) + xo + sectionLength - 19;
                final int posY = y * offY + yo;

                final ItemStack is = refStack.copy().getItemStack();

                if (this.tooltip == z - viewStart) {
                    dspToolTip = Platform.getItemDisplayName(is);
                    if (lineList.size() > 0) {
                        addItemTooltip(is, lineList);
                        dspToolTip = dspToolTip + '\n' + Joiner.on("\n").join(lineList);
                    }

                    toolPosX = x * (1 + sectionLength) + xo + sectionLength - 8;
                    toolPosY = y * offY + yo;

                    hoveredStack = is;
                }

                this.drawItem(posX, posY, is);

                if (red) {
                    final int startX = x * (1 + sectionLength) + xo;
                    final int startY = posY - 4;
                    drawRect(
                            startX,
                            startY,
                            startX + sectionLength,
                            startY + offY,
                            GuiColors.CraftConfirmMissingItem.getColor());
                }

                x++;

                if (x > 2) {
                    y++;
                    x = 0;
                }
            }
        }

        if (this.tooltip >= 0 && dspToolTip.length() > 0) {
            this.drawTooltip(toolPosX, toolPosY + 10, 0, dspToolTip);
        }
    }

    private void drawTreeFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        final CraftingJobV2 jobTree = this.jobTree;
        if (jobTree == null) {
            this.drawTooltip(16, 48, 0, GuiText.NoCraftingTreeReceived.getLocal());
            return;
        }
        if (jobTree.getOutput() == null) {
            this.drawTooltip(16, 48, 0, GuiText.Nothing.getLocal());
            return;
        }

        craftingTree.setRequest(jobTree.originalRequest);

        craftingTree.draw(mouseX, mouseY);
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        cpuTable.drawBG(offsetX, offsetY);
        this.setScrollBar();

        switch (displayMode) {
            case LIST -> {
                this.bindTexture("guis/craftingreport.png");
                if (tallMode) {
                    this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, LIST_VIEW_TEXTURE_BELOW_TOP_ROW_Y);
                    int y = LIST_VIEW_TEXTURE_BELOW_TOP_ROW_Y;
                    // first and last row are pre-baked
                    for (int row = 1; row < rows - 1; row++) {
                        this.drawTexturedModalRect(
                                offsetX,
                                offsetY + y,
                                0,
                                LIST_VIEW_TEXTURE_BELOW_TOP_ROW_Y,
                                this.xSize,
                                LIST_VIEW_TEXTURE_ROW_HEIGHT);
                        y += LIST_VIEW_TEXTURE_ROW_HEIGHT;
                    }
                    this.drawTexturedModalRect(
                            offsetX,
                            offsetY + y,
                            0,
                            LIST_VIEW_TEXTURE_ABOVE_BOTTOM_ROW_Y,
                            this.xSize,
                            LIST_VIEW_TEXTURE_HEIGHT - LIST_VIEW_TEXTURE_ABOVE_BOTTOM_ROW_Y);
                } else {
                    this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
                }
            }
            case TREE -> {
                this.bindTexture("guis/craftingtree.png");
                this.drawTextured9PatchRect(
                        offsetX,
                        offsetY,
                        this.xSize,
                        this.ySize,
                        0,
                        0,
                        TREE_VIEW_TEXTURE_WIDTH,
                        TREE_VIEW_TEXTURE_HEIGHT);
            }
        }
    }

    private void setScrollBar() {
        switch (displayMode) {
            case LIST -> {
                if (getScrollBar() == null) {
                    setScrollBar(scrollbar);
                }
                final int size = this.visual.size();
                this.getScrollBar().setTop(19).setLeft(218).setHeight(ySize - 92);
                this.getScrollBar().setRange(0, (size + 2) / 3 - this.rows, 1);
            }
            case TREE -> {
                if (getScrollBar() != null) {
                    setScrollBar(null);
                }
            }
        }
    }

    public void postUpdate(final List<IAEItemStack> list, final byte ref) {
        switch (ref) {
            case 0 -> {
                for (final IAEItemStack l : list) {
                    this.handleInput(this.storage, l);
                }
            }
            case 1 -> {
                for (final IAEItemStack l : list) {
                    this.handleInput(this.pending, l);
                }
            }
            case 2 -> {
                for (final IAEItemStack l : list) {
                    this.handleInput(this.missing, l);
                }
            }
        }

        for (final IAEItemStack l : list) {
            final long amt = this.getTotal(l);

            if (amt <= 0) {
                this.deleteVisualStack(l);
            } else {
                final IAEItemStack is = this.findVisualStack(l);
                is.setStackSize(amt);
            }
        }
        this.sortItems();
        this.setScrollBar();
    }

    public void setJobTree(CraftingJobV2 jobTree) {
        this.jobTree = jobTree;
    }

    Comparator<IAEItemStack> comparator = (i1, i2) -> {
        if (missing.findPrecise(i1) != null) {
            if (missing.findPrecise(i2) != null) return 0;
            return -1;
        } else if (missing.findPrecise(i2) != null) {
            return 1;
        } else {
            return 0;
        }
    };

    private void sortItems() {
        if (!this.missing.isEmpty()) {
            this.visual.sort(comparator);
        }
    }

    private void handleInput(final IItemList<IAEItemStack> s, final IAEItemStack l) {
        IAEItemStack a = s.findPrecise(l);

        if (l.getStackSize() <= 0) {
            if (a != null) {
                a.reset();
            }
        } else {
            if (a == null) {
                s.add(l.copy());
                a = s.findPrecise(l);
            }

            if (a != null) {
                a.setStackSize(l.getStackSize());
            }
        }
    }

    @Override
    protected boolean mouseWheelEvent(int x, int y, int wheel) {
        if (displayMode == DisplayMode.TREE && craftingTree != null
                && craftingTree.isPointInWidget(x - guiLeft, y - guiTop)) {
            craftingTree.onMouseWheel(x - guiLeft, y - guiTop, wheel);
            return true;
        }
        return super.mouseWheelEvent(x, y, wheel);
    }

    private long getTotal(final IAEItemStack is) {
        final IAEItemStack a = this.storage.findPrecise(is);
        final IAEItemStack c = this.pending.findPrecise(is);
        final IAEItemStack m = this.missing.findPrecise(is);

        long total = 0;

        if (a != null) {
            total += a.getStackSize();
        }

        if (c != null) {
            total += c.getStackSize();
        }

        if (m != null) {
            total += m.getStackSize();
        }

        return total;
    }

    private void deleteVisualStack(final IAEItemStack l) {
        final Iterator<IAEItemStack> i = this.visual.iterator();
        while (i.hasNext()) {
            final IAEItemStack o = i.next();
            if (o.equals(l)) {
                i.remove();
                return;
            }
        }
    }

    private IAEItemStack findVisualStack(final IAEItemStack l) {
        for (final IAEItemStack o : this.visual) {
            if (o.equals(l)) {
                return o;
            }
        }

        final IAEItemStack stack = l.copy();
        this.visual.add(stack);
        return stack;
    }

    @Override
    protected void keyTyped(final char character, final int key) {
        if (!this.checkHotbarKeys(key)) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                this.actionPerformed(this.start);
            }
            super.keyTyped(character, key);
        }
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        final boolean backwards = Mouse.isButtonDown(1);

        if (btn == this.selectCPU) {
            cpuTable.cycleCPU(backwards);
        } else if (btn == this.cancel) {
            this.addMissingItemsToBookMark();
            switchToOriginalGUI();
        } else if (btn == this.switchDisplayMode) {
            this.displayMode = this.displayMode.next();
            recalculateScreenSize();
            this.setWorldAndResolution(mc, width, height);
        } else if (btn == this.switchTallMode) {
            tallMode = !tallMode;
            switchTallMode.set(tallMode ? TerminalStyle.TALL : TerminalStyle.SMALL);
            recalculateScreenSize();
            this.setWorldAndResolution(mc, width, height);
        } else if (btn == this.takeScreenshot) {
            if (craftingTree != null) {
                craftingTree.saveScreenshot();
            }
        } else if (btn == this.start) {
            try {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("Terminal.Start", "Start"));
            } catch (final Throwable e) {
                AELog.debug(e);
            }
        }
    }

    public void switchToOriginalGUI() {
        // null if terminal is not a native AE2 terminal
        if (this.OriginalGui != null) {
            NetworkHandler.instance.sendToServer(new PacketSwitchGuis(this.OriginalGui));
        }
    }

    public ItemStack getHoveredStack() {
        return hoveredStack;
    }

    // expose GUI buttons for mod integrations
    public GuiButton getCancelButton() {
        return cancel;
    }

    @Override
    protected void mouseClicked(int xCoord, int yCoord, int btn) {
        super.mouseClicked(xCoord, yCoord, btn);
        cpuTable.mouseClicked(xCoord - guiLeft, yCoord - guiTop, btn);
    }

    @Override
    protected void mouseClickMove(int x, int y, int c, long d) {
        super.mouseClickMove(x, y, c, d);
        cpuTable.mouseClickMove(x - guiLeft, y - guiTop);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        super.mouseMovedOrUp(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() {
        if (cpuTable.handleMouseInput(guiLeft, guiTop)) {
            return;
        }
        super.handleMouseInput();
    }

    public boolean hideItemPanelSlot(int x, int y, int w, int h) {
        return cpuTable.hideItemPanelSlot(x - guiLeft, y - guiTop, w, h);
    }

    protected void addMissingItemsToBookMark() {
        if (!this.missing.isEmpty() && isShiftKeyDown()) {
            for (IAEItemStack iaeItemStack : this.missing) {
                NEI.instance.addItemToBookMark(iaeItemStack.getItemStack());
            }
        }
    }

    public IItemList<IAEItemStack> getStorage() {
        return this.storage;
    }

    public IItemList<IAEItemStack> getPending() {
        return this.pending;
    }

    public IItemList<IAEItemStack> getMissing() {
        return this.missing;
    }
}
