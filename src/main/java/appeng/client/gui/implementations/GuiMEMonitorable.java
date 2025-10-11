/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.client.gui.implementations;

import static appeng.util.Platform.stackConvert;
import static appeng.util.Platform.stackConvertPacket;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent.InitGuiEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import appeng.api.AEApi;
import appeng.api.config.CraftingStatus;
import appeng.api.config.PinsState;
import appeng.api.config.SearchBoxFocusPriority;
import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.api.config.YesNo;
import appeng.api.implementations.tiles.IViewCellStorage;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.ITerminalPins;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IDisplayRepo;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.client.ActionKey;
import appeng.client.gui.AEBaseMEGui;
import appeng.client.gui.IGuiTooltipHandler;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.IDropToFillTextField;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.gui.widgets.VirtualMESlot;
import appeng.client.gui.widgets.VirtualPinSlot;
import appeng.client.me.ItemRepo;
import appeng.client.me.SlotME;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotCraftingMatrix;
import appeng.container.slot.SlotFakeCraftingMatrix;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.CommonHelper;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.core.sync.packets.PacketMonitorableAction;
import appeng.core.sync.packets.PacketPinsUpdate;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.IPinsHandler;
import appeng.helpers.InventoryAction;
import appeng.helpers.MonitorableAction;
import appeng.integration.IntegrationRegistry;
import appeng.integration.IntegrationType;
import appeng.integration.modules.NEI;
import appeng.items.storage.ItemViewCell;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;
import codechicken.nei.recipe.StackInfo;

public class GuiMEMonitorable extends AEBaseMEGui
        implements ISortSource, IConfigManagerHost, IDropToFillTextField, IPinsHandler, IGuiTooltipHandler {

    public static int craftingGridOffsetX;
    public static int craftingGridOffsetY;

    private static String memoryText = "";
    private final IDisplayRepo repo;
    private final int offsetX = 9;
    private final int offsetY = 18;
    private final int MAGIC_HEIGHT_NUMBER = 114 + 1;
    private final int lowerTextureOffset = 0;
    private final IConfigManager configSrc;
    private final boolean viewCell;
    private final ItemStack[] myCurrentViewCells = new ItemStack[5];
    private final ContainerMEMonitorable monitorableContainer;
    private GuiTabButton craftingStatusBtn;
    private GuiImgButton craftingStatusImgBtn;
    private final MEGuiTextField searchField;
    private GuiText myName;
    private int perRow = 9;
    private int reservedSpace = 0;
    private boolean customSortOrder = true;
    private int rows = 0;
    private int standardSize;
    private GuiImgButton ViewBox;
    private GuiImgButton SortByBox;
    private GuiImgButton SortDirBox;
    private GuiImgButton searchBoxSettings;
    private GuiImgButton terminalStyleBox;
    private GuiImgButton searchStringSave;
    private GuiImgButton typeFilter;
    private GuiImgButton pinsStateButton;
    private boolean canBeAutoFocused = false;
    private boolean isAutoFocused = false;
    private int currentMouseX = 0;
    private int currentMouseY = 0;
    private PinsState pinsState;
    public final boolean hasPinHost;

    protected VirtualPinSlot[] pinSlots = null;
    protected VirtualMESlot[] meSlots = null;
    private IAEStack<?> hoveredItemStack = null;

    public GuiMEMonitorable(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        this(inventoryPlayer, te, new ContainerMEMonitorable(inventoryPlayer, te));
    }

    public GuiMEMonitorable(final InventoryPlayer inventoryPlayer, final ITerminalHost te,
            final ContainerMEMonitorable c) {

        super(c);

        final GuiScrollbar scrollbar = new GuiScrollbar();
        this.setScrollBar(scrollbar);
        this.repo = new ItemRepo(scrollbar, this);

        this.xSize = 195;
        this.ySize = 204;

        this.standardSize = this.xSize;

        this.configSrc = ((IConfigurableObject) this.inventorySlots).getConfigManager();

        pinsState = (PinsState) configSrc.getSetting(Settings.PINS_STATE);

        (this.monitorableContainer = (ContainerMEMonitorable) this.inventorySlots).setGui(this);

        this.viewCell = te instanceof IViewCellStorage;

        this.myName = te.getName();

        hasPinHost = te instanceof ITerminalPins;

        this.searchField = new MEGuiTextField(90, 12, ButtonToolTips.SearchStringTooltip.getLocal()) {

            @Override
            public void onTextChange(final String oldText) {
                final String text = getText();
                repo.setSearchString(text);
                repo.updateView();
                setScrollBar();
            }
        };

        NEI.searchField.putFormatter(this.searchField);
    }

    public void postUpdate(final List<IAEStack<?>> list) {
        for (final IAEStack<?> is : list) {
            this.repo.postUpdate(is);
        }

        this.repo.updateView();
        this.setScrollBar();
    }

    private void setScrollBar() {
        this.getScrollBar().setTop(18).setLeft(175).setHeight(this.rows * 18 - 2);
        this.getScrollBar().setRange(
                0,
                (this.repo.size() + pinsState.ordinal() * 9 + this.perRow - 1) / this.perRow - this.rows,
                Math.max(1, this.rows / 6));
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {

        if (btn == this.craftingStatusBtn || btn == this.craftingStatusImgBtn) {
            NetworkHandler.instance.sendToServer(new PacketSwitchGuis(GuiBridge.GUI_CRAFTING_STATUS));
        }

        if (!(btn instanceof GuiImgButton iBtn) || iBtn.getSetting() == Settings.ACTIONS) return;

        final Enum cv = iBtn.getCurrentValue();
        final boolean backwards = Mouse.isButtonDown(1);
        final Enum next = Platform.rotateEnum(cv, backwards, iBtn.getSetting().getPossibleValues());

        if (btn == this.terminalStyleBox) {
            AEConfig.instance.settings.putSetting(iBtn.getSetting(), next);
        } else if (btn == this.searchBoxSettings) {
            AEConfig.instance.settings.putSetting(iBtn.getSetting(), next);
        } else if (btn == this.searchStringSave) {
            AEConfig.instance.preserveSearchBar = next == YesNo.YES;
        } else if (btn == this.pinsStateButton) {
            try {
                if (next.ordinal() >= rows) return; // ignore to avoid hiding terminal inventory

                final PacketPinsUpdate p = new PacketPinsUpdate((PinsState) next);
                NetworkHandler.instance.sendToServer(p);
            } catch (final IOException e) {
                AELog.debug(e);
            }
        } else {
            try {
                NetworkHandler.instance.sendToServer(new PacketValueConfig(iBtn.getSetting().name(), next.name()));
            } catch (final IOException e) {
                AELog.debug(e);
            }
        }

        iBtn.set(next);

        if (next.getClass() == SearchBoxMode.class || next.getClass() == TerminalStyle.class) {
            this.reinitalize();
        }
    }

    private void adjustPinsSize() {
        final int pinMaxSize = rows - 1;
        if (pinsState.ordinal() <= pinMaxSize) return;

        try {
            PinsState newState = PinsState.fromOrdinal(pinMaxSize);
            final PacketPinsUpdate p = new PacketPinsUpdate(newState);
            NetworkHandler.instance.sendToServer(p);
        } catch (final IOException e) {
            AELog.debug(e);
        }
    }

    private void reinitalize() {
        memoryText = this.searchField.getText();
        if (!MinecraftForge.EVENT_BUS.post(new InitGuiEvent.Pre(this, this.buttonList))) {
            this.buttonList.clear();
            this.initGui();
        }
        MinecraftForge.EVENT_BUS.post(new InitGuiEvent.Post(this, this.buttonList));
    }

    private int getPinSlotIndex(int x, int y) {
        int pinsRows = pinsState.ordinal();
        int left = this.guiLeft + this.offsetX - 1;
        int top = this.guiTop + this.offsetY - 1;

        if (x < left || x >= left + 18 * perRow || y < top || y >= top + 18 * pinsRows) {
            return -1;
        }

        int ox = x - left;
        int oy = y - top;

        return oy / 18 * perRow + ox / 18;
    }

    private int getSlotIndex(int x, int y) {
        final int pinsHeight = 18 * pinsState.ordinal();
        int left = this.guiLeft + this.offsetX - 1;
        int top = this.guiTop + this.offsetY + pinsHeight - 1;

        if (x < left || x >= left + 18 * perRow || y < top || y >= top + 18 * this.rows) {
            return -1;
        }

        int ox = x - left;
        int oy = y - top;

        return oy / 18 * perRow + ox / 18;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);

        this.perRow = AEConfig.instance.getConfigManager().getSetting(Settings.TERMINAL_STYLE) != TerminalStyle.FULL ? 9
                : 9 + ((this.width - this.standardSize) / 18);
        this.rows = calculateRowsCount();

        this.getMeSlots().clear();

        // make sure we have space at least for one row of normal slots, because pins not adjusted by scroll bar
        adjustPinsSize();

        int pinsRows = pinsState.ordinal();
        this.pinSlots = new VirtualPinSlot[pinsRows * this.perRow];
        for (int y = 0; y < pinsRows; y++) {
            for (int x = 0; x < this.perRow; x++) {
                this.pinSlots[y * this.perRow + x] = new VirtualPinSlot(
                        this.offsetX + x * 18,
                        y * 18 + this.offsetY,
                        this.repo,
                        y * this.perRow + x);
            }
        }

        int normalSlotRows = this.rows - pinsRows;
        this.meSlots = new VirtualMESlot[normalSlotRows * this.perRow];
        for (int y = 0; y < normalSlotRows; y++) {
            for (int x = 0; x < this.perRow; x++) {
                this.meSlots[y * this.perRow + x] = new VirtualMESlot(
                        this.offsetX + x * 18,
                        this.offsetY + y * 18 + pinsRows * 18,
                        this.repo,
                        y * this.perRow + x);
            }
        }

        if (AEConfig.instance.getConfigManager().getSetting(Settings.TERMINAL_STYLE) != TerminalStyle.FULL) {
            this.xSize = this.standardSize + ((this.perRow - 9) * 18);
        } else {
            this.xSize = this.standardSize;
        }

        super.initGui();
        // full size : 204
        // extra slots : 72
        // slot 18

        this.ySize = MAGIC_HEIGHT_NUMBER + this.rows * 18 + this.reservedSpace;
        final int unusedSpace = this.height - this.ySize;
        this.guiTop = (int) Math.floor(unusedSpace / (unusedSpace < 0 ? 3.8f : 2.0f));

        int offset = this.guiTop + 8;

        buttonList.clear();

        if (this.customSortOrder) {
            this.buttonList.add(
                    this.SortByBox = new GuiImgButton(
                            this.guiLeft - 18,
                            offset,
                            Settings.SORT_BY,
                            this.configSrc.getSetting(Settings.SORT_BY)));
            offset += 20;
        }

        if (this.viewCell || this instanceof GuiWirelessTerm) {
            this.buttonList.add(
                    this.ViewBox = new GuiImgButton(
                            this.guiLeft - 18,
                            offset,
                            Settings.VIEW_MODE,
                            this.configSrc.getSetting(Settings.VIEW_MODE)));
            offset += 20;
        }
        if (!AEApi.instance().registries().itemDisplay().getItemFilters().isEmpty()) {
            this.buttonList.add(
                    this.typeFilter = new GuiImgButton(
                            this.guiLeft - 18,
                            offset,
                            Settings.TYPE_FILTER,
                            this.configSrc.getSetting(Settings.TYPE_FILTER)));
            offset += 20;
        }

        this.buttonList.add(
                this.SortDirBox = new GuiImgButton(
                        this.guiLeft - 18,
                        offset,
                        Settings.SORT_DIRECTION,
                        this.configSrc.getSetting(Settings.SORT_DIRECTION)));
        offset += 20;

        this.buttonList.add(
                this.searchBoxSettings = new GuiImgButton(
                        this.guiLeft - 18,
                        offset,
                        Settings.SEARCH_MODE,
                        AEConfig.instance.settings.getSetting(Settings.SEARCH_MODE)));
        offset += 20;

        this.buttonList.add(
                this.searchStringSave = new GuiImgButton(
                        this.guiLeft - 18,
                        offset,
                        Settings.SAVE_SEARCH,
                        AEConfig.instance.preserveSearchBar ? YesNo.YES : YesNo.NO));
        offset += 20;

        if (!(this instanceof GuiMEPortableCell) || this instanceof GuiWirelessTerm) {
            this.buttonList.add(
                    this.terminalStyleBox = new GuiImgButton(
                            this.guiLeft - 18,
                            offset,
                            Settings.TERMINAL_STYLE,
                            AEConfig.instance.settings.getSetting(Settings.TERMINAL_STYLE)));
            offset += 20;
        }

        if (this.viewCell || this instanceof GuiWirelessTerm) {
            if (AEConfig.instance.getConfigManager().getSetting(Settings.CRAFTING_STATUS)
                    .equals(CraftingStatus.BUTTON)) {
                this.buttonList.add(
                        this.craftingStatusImgBtn = new GuiImgButton(
                                this.guiLeft - 18,
                                offset,
                                Settings.CRAFTING_STATUS,
                                AEConfig.instance.settings.getSetting(Settings.CRAFTING_STATUS)));
            } else {
                this.buttonList.add(
                        this.craftingStatusBtn = new GuiTabButton(
                                this.guiLeft + 170,
                                this.guiTop - 4,
                                2 + 11 * 16,
                                GuiText.CraftingStatus.getLocal(),
                                itemRender));
                this.craftingStatusBtn.setHideEdge(13); // GuiTabButton implementation //
            }
        }

        if (hasPinHost) {
            this.buttonList.add(
                    this.pinsStateButton = new GuiImgButton(
                            this.guiLeft + 178,
                            this.guiTop + 18 + (rows * 18) + 25,
                            Settings.PINS_STATE,
                            configSrc.getSetting(Settings.PINS_STATE)));
        }

        // Enum setting = AEConfig.INSTANCE.getSetting( "Terminal", SearchBoxMode.class, SearchBoxMode.AUTOSEARCH );
        final Enum searchMode = AEConfig.instance.settings.getSetting(Settings.SEARCH_MODE);
        this.canBeAutoFocused = SearchBoxMode.AUTOSEARCH == searchMode || SearchBoxMode.NEI_AUTOSEARCH == searchMode;

        this.searchField.x = this.guiLeft + Math.max(80, this.offsetX);
        this.searchField.y = this.guiTop + 4;
        this.searchField.setFocused(this.canBeAutoFocused);
        this.isAutoFocused = this.canBeAutoFocused;

        if (this.isSubGui()) {
            this.searchField.setText(memoryText);
        } else if (AEConfig.instance.preserveSearchBar) {
            this.searchField.setText(memoryText, true);
            repo.setSearchString(memoryText);
        }
        this.searchField.setCursorPositionEnd();

        this.setScrollBar();

        craftingGridOffsetX = Integer.MAX_VALUE;
        craftingGridOffsetY = Integer.MAX_VALUE;

        for (final Object s : this.inventorySlots.inventorySlots) {
            if (s instanceof AppEngSlot) {
                if (((Slot) s).xDisplayPosition < 197) {
                    this.repositionSlot((AppEngSlot) s);
                }
            }

            if (s instanceof SlotCraftingMatrix || s instanceof SlotFakeCraftingMatrix) {
                final Slot g = (Slot) s;
                if (g.xDisplayPosition > 0 && g.yDisplayPosition > 0) {
                    craftingGridOffsetX = Math.min(craftingGridOffsetX, g.xDisplayPosition);
                    craftingGridOffsetY = Math.min(craftingGridOffsetY, g.yDisplayPosition);
                }
            }
        }

        craftingGridOffsetX -= 25;
        craftingGridOffsetY -= 6;
    }

    protected int calculateRowsCount() {
        final boolean hasNEI = IntegrationRegistry.INSTANCE.isEnabled(IntegrationType.NEI);
        final int NEIPadding = hasNEI ? 22
                /** input */
                + 20
                /** top panel */
                : 0;
        final int extraSpace = this.height - MAGIC_HEIGHT_NUMBER - NEIPadding - this.reservedSpace;

        return Math.max(3, Math.min(this.getMaxRows(), (int) Math.floor(extraSpace / 18)));
    }

    @Nullable
    private VirtualMESlot getVirtualMESlotUnderMouse(int mouseX, int mouseY) {
        VirtualMESlot slot = null;

        int hoveredPinSlotIndex = this.getPinSlotIndex(mouseX, mouseY);
        if (hoveredPinSlotIndex >= 0 && hoveredPinSlotIndex < this.pinSlots.length) {
            slot = this.pinSlots[hoveredPinSlotIndex];
        } else {
            final int hoveredSlotIndex = this.getSlotIndex(mouseX, mouseY);
            if (hoveredSlotIndex >= 0 && hoveredSlotIndex < this.meSlots.length) {
                slot = this.meSlots[hoveredSlotIndex];
            }
        }
        return slot;
    }

    private void drawVirtualSlot(VirtualMESlot slot) {
        var aes = slot.getAEStack();
        if (aes == null) return;

        int x = slot.getX();
        int y = slot.getY();
        aes.drawInGui(this.mc, x, y);
        aes.drawOverlayInGui(this.mc, x, y, true, true, true);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRendererObj.drawString(
                this.getGuiDisplayName(this.myName.getLocal()),
                8,
                6,
                GuiColors.MEMonitorableTitle.getColor());
        this.fontRendererObj.drawString(
                GuiText.inventory.getLocal(),
                8,
                this.ySize - 96 + 3,
                GuiColors.MEMonitorableInventory.getColor());

        VirtualPinSlot.drawSlotsBackground(this.pinSlots, this.mc, this.zLevel);
        this.drawVirtualSlots(this.pinSlots);
        this.drawVirtualSlots(this.meSlots);

        VirtualMESlot slot = this.getVirtualMESlotUnderMouse(mouseX, mouseY);
        if (slot != null) {
            slot.drawHoveredOverlay();
            this.hoveredItemStack = slot.getAEStack();
        } else {
            this.hoveredItemStack = null;
        }

        this.currentMouseX = mouseX;
        this.currentMouseY = mouseY;
    }

    protected void drawVirtualSlots(@Nonnull VirtualMESlot[] slots) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        RenderHelper.enableGUIStandardItemLighting();
        for (VirtualMESlot slot : slots) {
            this.drawVirtualSlot(slot);
        }
        GL11.glPopAttrib();
    }

    @Override
    public ItemStack getHoveredStack() {
        if (this.hoveredItemStack == null) return null;

        if (this.hoveredItemStack instanceof IAEItemStack ais) {
            return ais.getItemStack();
        } else {
            // Convert fluid into a proper item using the StackStringifyHandler registered with NEI
            ItemStack stack = stackConvertPacket(hoveredItemStack).getItemStack();
            return StackInfo.loadFromNBT(StackInfo.itemStackToNBT(stack));
        }
    }

    @Override
    public List<String> handleItemTooltip(ItemStack stack, int mouseX, int mouseY, List<String> currentToolTip) {
        if (this.hoveredItemStack instanceof IAEFluidStack afs) {
            currentToolTip.clear();
            currentToolTip.add(afs.getDisplayName());
            return currentToolTip;
        }
        return super.handleItemTooltip(stack, mouseX, mouseY, currentToolTip);
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) {
        searchField.mouseClicked(xCoord, yCoord, btn);
        isAutoFocused = false;

        if (handleSlotClick(xCoord, yCoord, btn)) return;
        if (handleViewCellClick(xCoord, yCoord, btn)) return;

        super.mouseClicked(xCoord, yCoord, btn);
    }

    private void sendAction(MonitorableAction action, @Nullable IAEItemStack stack, int slotIndex) {
        ((AEBaseContainer) this.inventorySlots).setTargetStack(stack);
        final PacketMonitorableAction p = new PacketMonitorableAction(action, slotIndex);
        NetworkHandler.instance.sendToServer(p);
    }

    private boolean handleSlotClick(final int mouseX, final int mouseY, final int mouseButton) {
        final VirtualMESlot slot = getVirtualMESlotUnderMouse(mouseX, mouseY);

        if (slot == null) return false;

        IAEItemStack slotStack = slot.getAEStack() instanceof IAEItemStack ais ? ais : null;

        if (Keyboard.isKeyDown(Keyboard.KEY_SPACE)) {
            if (slotStack != null) {
                this.sendAction(MonitorableAction.MOVE_REGION, slotStack, this.meSlots.length);
                return true;
            }
            return false;
        }

        final EntityPlayer player = Minecraft.getMinecraft().thePlayer;

        final boolean isLShiftDown = isShiftKeyDown();
        final boolean isLControlDown = isCtrlKeyDown();

        switch (mouseButton) {
            case 0 -> { // left click
                if (slot instanceof VirtualPinSlot && player.inventory.getItemStack() != null) {
                    this.sendAction(
                            isLControlDown ? MonitorableAction.SET_CONTAINER_PIN : MonitorableAction.SET_ITEM_PIN,
                            null,
                            slot.getSlotIndex());
                    return true;
                }

                if (isLControlDown) {
                    this.sendAction(
                            isLShiftDown ? MonitorableAction.FILL_CONTAINERS : MonitorableAction.FILL_SINGLE_CONTAINER,
                            stackConvert(slot.getAEStack()),
                            this.meSlots.length);
                    return true;
                }

                if (isLShiftDown) {
                    this.sendAction(MonitorableAction.SHIFT_CLICK, slotStack, this.meSlots.length);
                    return true;
                }

                if (slot.getAEStack() != null && slot.getAEStack().getStackSize() == 0
                        && player.inventory.getItemStack() == null) {
                    // TODO: native
                    this.sendAction(MonitorableAction.AUTO_CRAFT, stackConvert(slot.getAEStack()), this.meSlots.length);
                    return true;
                }
                this.sendAction(MonitorableAction.PICKUP_OR_SET_DOWN, slotStack, this.meSlots.length);
                return true;
            }
            case 1 -> { // right click
                if (slot instanceof VirtualPinSlot) {
                    if (isLShiftDown) {
                        this.sendAction(MonitorableAction.UNSET_PIN, null, slot.getSlotIndex());
                    } else {
                        this.sendAction(
                                isLControlDown ? MonitorableAction.SET_CONTAINER_PIN : MonitorableAction.SET_ITEM_PIN,
                                null,
                                slot.getSlotIndex());
                    }
                    return true;
                }

                if (isLControlDown) {
                    this.sendAction(
                            isLShiftDown ? MonitorableAction.DRAIN_CONTAINERS
                                    : MonitorableAction.DRAIN_SINGLE_CONTAINER,
                            stackConvert(slot.getAEStack()),
                            this.meSlots.length);
                    return true;
                }

                if (isLShiftDown) {
                    this.sendAction(MonitorableAction.PICKUP_SINGLE, slotStack, this.meSlots.length);
                    return true;
                }

                this.sendAction(MonitorableAction.SPLIT_OR_PLACE_SINGLE, slotStack, this.meSlots.length);
                return true;
            }
            case 2 -> { // middle click
                if (slot.getAEStack() != null && slot.getAEStack().isCraftable()) {
                    // TODO: native
                    this.sendAction(MonitorableAction.AUTO_CRAFT, stackConvert(slot.getAEStack()), this.meSlots.length);
                    return true;
                } else if (player.capabilities.isCreativeMode) {
                    this.sendAction(MonitorableAction.CREATIVE_DUPLICATE, slotStack, this.meSlots.length);
                    return true;
                }
            }
            default -> {}
        }

        return false;
    }

    private boolean handleViewCellClick(final int xCoord, final int yCoord, final int btn) {
        if (this.viewCell && monitorableContainer.canAccessViewCells && btn == 1) {
            Slot slot = getSlot(xCoord, yCoord);
            if (slot instanceof SlotRestrictedInput cvs) {
                // if it has an item
                if (!cvs.getHasStack()) return false;
                // if its a view cell
                if (!(cvs.getStack().getItem() instanceof ItemViewCell)) return false;

                // update the view cell
                try {
                    NetworkHandler.instance.sendToServer(
                            new PacketValueConfig("Terminal.UpdateViewCell", Integer.toString(cvs.getSlotIndex())));
                } catch (IOException e) {
                    AELog.debug(e);
                }

                // eat the right-click input if a view cell was successfully toggled
                return true;
            }
        }
        return false;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
        memoryText = this.searchField.getText();
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {

        this.bindTexture(this.getBackground());
        final int x_width = 195;
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, x_width, 18);

        if (this.viewCell || (this instanceof GuiSecurity)) {
            this.drawTexturedModalRect(offsetX + x_width, offsetY, x_width, 0, 46, 128);
        }

        for (int x = 0; x < this.rows; x++) {
            this.drawTexturedModalRect(offsetX, offsetY + 18 + x * 18, 0, 18, x_width, 18);
        }

        this.drawTexturedModalRect(
                offsetX,
                offsetY + 16 + this.rows * 18 + this.lowerTextureOffset,
                0,
                106 - 18 - 18,
                x_width,
                99 + this.reservedSpace - this.lowerTextureOffset);

        if (this.viewCell) {
            boolean update = false;

            for (int i = 0; i < 5; i++) {
                if (this.myCurrentViewCells[i] != this.monitorableContainer.getCellViewSlot(i).getStack()) {
                    update = true;
                    this.myCurrentViewCells[i] = this.monitorableContainer.getCellViewSlot(i).getStack();
                }
            }

            if (update) {
                this.repo.setViewCell(this.myCurrentViewCells);
            }
        }

        searchField.drawTextBox();
    }

    protected String getBackground() {
        return "guis/terminal.png";
    }

    @Override
    protected boolean isPowered() {
        return this.repo.hasPower();
    }

    int getMaxRows() {
        return AEConfig.instance.getConfigManager().getSetting(Settings.TERMINAL_STYLE) == TerminalStyle.SMALL
                ? AEConfig.instance.MEMonitorableSmallSize
                : Integer.MAX_VALUE;
    }

    protected void repositionSlot(final AppEngSlot s) {
        s.yDisplayPosition = s.getY() + this.ySize - 78 - 5;
    }

    @Override
    protected void keyTyped(final char character, final int key) {
        if (NEI.searchField.existsSearchField()) {
            if ((NEI.searchField.focused() || searchField.isFocused())
                    && CommonHelper.proxy.isActionKey(ActionKey.TOGGLE_FOCUS, key)) {
                final boolean focused = searchField.isFocused();
                searchField.setFocused(!focused);
                NEI.searchField.setFocus(focused);
                isAutoFocused = false;
                return;
            }

            if (CommonHelper.proxy.isActionKey(ActionKey.SEARCH_CONNECTED_INVENTORIES, key)
                    && !(NEI.searchField.focused() || searchField.isFocused())) {
                final boolean mouseInGui = this
                        .isPointInRegion(0, 0, this.xSize, this.ySize, this.currentMouseX, this.currentMouseY);
                if (mouseInGui && getSlot(this.currentMouseX, this.currentMouseY) instanceof SlotME sme) {
                    IAEItemStack stack = sme.getAEStack();
                    this.monitorableContainer.setTargetStack(stack);
                    if (stack != null) {
                        final PacketInventoryAction p = new PacketInventoryAction(
                                InventoryAction.FIND_ITEMS,
                                this.getInventorySlots().size(),
                                0);
                        NetworkHandler.instance.sendToServer(p);
                        this.mc.thePlayer.closeScreen();
                        return;
                    }
                }
            }

            if (NEI.searchField.focused()) {
                return;
            }
        }

        if (searchField.isFocused() && key == Keyboard.KEY_RETURN) {
            searchField.setFocused(false);
            isAutoFocused = false;
            return;
        }

        if (character == ' ' && searchField.getText().isEmpty()) {
            return;
        }

        boolean skipHotbarCheck = searchField.isFocused()
                && (AEConfig.instance.searchBoxFocusPriority == SearchBoxFocusPriority.ALWAYS
                        || (AEConfig.instance.searchBoxFocusPriority == SearchBoxFocusPriority.NO_AUTOSEARCH
                                && !isAutoFocused));

        if (!skipHotbarCheck && checkHotbarKeys(key)) {
            return;
        }

        final boolean mouseInGui = this
                .isPointInRegion(0, 0, this.xSize, this.ySize, this.currentMouseX, this.currentMouseY);

        if (this.canBeAutoFocused && !searchField.isFocused() && mouseInGui) {
            searchField.setFocused(true);
            isAutoFocused = true;
        }

        if (!searchField.textboxKeyTyped(character, key)) {
            super.keyTyped(character, key);
        }
    }

    @Override
    public void updateScreen() {
        this.repo.setPowered(this.monitorableContainer.isPowered());
        super.updateScreen();
    }

    @Override
    public Enum getSortBy() {
        return this.configSrc.getSetting(Settings.SORT_BY);
    }

    @Override
    public Enum getSortDir() {
        return this.configSrc.getSetting(Settings.SORT_DIRECTION);
    }

    @Override
    public Enum getTypeFilter() {
        return this.configSrc.getSetting(Settings.TYPE_FILTER);
    }

    @Override
    public Enum getSortDisplay() {
        return this.configSrc.getSetting(Settings.VIEW_MODE);
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        if (this.SortByBox != null) {
            this.SortByBox.set(this.configSrc.getSetting(Settings.SORT_BY));
        }

        if (this.SortDirBox != null) {
            this.SortDirBox.set(this.configSrc.getSetting(Settings.SORT_DIRECTION));
        }

        if (this.ViewBox != null) {
            this.ViewBox.set(this.configSrc.getSetting(Settings.VIEW_MODE));
        }

        if (this.typeFilter != null) {
            this.typeFilter.set(this.configSrc.getSetting(Settings.TYPE_FILTER));
        }

        if (this.pinsStateButton != null) {
            pinsState = (PinsState) this.configSrc.getSetting(Settings.PINS_STATE);
            this.pinsStateButton.set(pinsState);

            reinitalize();
        }

        this.repo.updateView();
    }

    @SuppressWarnings("SameParameterValue")
    protected boolean isPointInRegion(int rectX, int rectY, int rectWidth, int rectHeight, int pointX, int pointY) {
        pointX -= this.guiLeft;
        pointY -= this.guiTop;
        return pointX >= rectX - 1 && pointX < rectX + rectWidth + 1
                && pointY >= rectY - 1
                && pointY < rectY + rectHeight + 1;
    }

    int getReservedSpace() {
        return this.reservedSpace;
    }

    void setReservedSpace(final int reservedSpace) {
        this.reservedSpace = reservedSpace;
    }

    public boolean isCustomSortOrder() {
        return this.customSortOrder;
    }

    void setCustomSortOrder(final boolean customSortOrder) {
        this.customSortOrder = customSortOrder;
    }

    public int getStandardSize() {
        return this.standardSize;
    }

    void setStandardSize(final int standardSize) {
        this.standardSize = standardSize;
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {
        handleTooltip(mouseX, mouseY, searchField);

        super.drawScreen(mouseX, mouseY, btn);
    }

    public boolean isOverTextField(final int mousex, final int mousey) {
        return searchField.isMouseIn(mousex, mousey);
    }

    public void setTextFieldValue(final String displayName, final int mousex, final int mousey, final ItemStack stack) {
        searchField.setText(NEI.searchField.getEscapedSearchText(displayName));
    }

    @Override
    protected void handleMouseClick(Slot p_146984_1_, int p_146984_2_, int p_146984_3_, int p_146984_4_) {

        // Hack for view cells, because they are outside the container
        if (p_146984_1_ != null && p_146984_4_ == 4 && p_146984_1_.xDisplayPosition > this.xSize) {
            p_146984_4_ = 0;
        }

        super.handleMouseClick(p_146984_1_, p_146984_2_, p_146984_3_, p_146984_4_);
    }

    @Override
    public void handleKeyboardInput() {
        super.handleKeyboardInput();

        // Pause the terminal when holding shift
        this.repo.setPaused(isShiftKeyDown());
    }

    public boolean hideItemPanelSlot(int tx, int ty, int tw, int th) {

        if (this.viewCell) {
            int rw = 33;
            int rh = 14 + myCurrentViewCells.length * 18;
            if (monitorableContainer.isAPatternTerminal()) rh += 21;

            if (rw <= 0 || rh <= 0 || tw <= 0 || th <= 0) {
                return false;
            }

            int rx = this.guiLeft + this.xSize;
            int ry = this.guiTop + 0;

            rw += rx;
            rh += ry;
            tw += tx;
            th += ty;

            // overflow || intersect
            return (rw < rx || rw > tx) && (rh < ry || rh > ty) && (tw < tx || tw > rx) && (th < ty || th > ry);
        }

        return false;
    }

    @Override
    public void setAEPins(IAEStack<?>[] pins) {
        repo.setAEPins(pins);
    }

    @Override
    public void setPinsState(PinsState state) {
        configSrc.putSetting(Settings.PINS_STATE, state);
    }

    // Moving items via hotbar keys in terminals isn't working anyway.
    // Let's disable hotbar keys processing for terminal slots to allow proper input of numbers in the search field
    @Override
    protected boolean checkHotbarKeys(int keyCode) {
        if (theSlot instanceof SlotME) {
            return false;
        }
        return super.checkHotbarKeys(keyCode);
    }
}
