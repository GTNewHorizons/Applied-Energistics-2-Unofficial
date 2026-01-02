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

import java.io.IOException;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.GuiScreenEvent.InitGuiEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import appeng.api.config.CraftingStatus;
import appeng.api.config.PinsState;
import appeng.api.config.SearchBoxFocusPriority;
import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;
import appeng.api.config.TerminalFontSize;
import appeng.api.config.TerminalStyle;
import appeng.api.config.YesNo;
import appeng.api.implementations.tiles.IViewCellStorage;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.ITerminalPins;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IDisplayRepo;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.client.ActionKey;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.slots.VirtualMEMonitorableSlot;
import appeng.client.gui.slots.VirtualMEPatternSlot;
import appeng.client.gui.slots.VirtualMEPinSlot;
import appeng.client.gui.slots.VirtualMESlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.IDropToFillTextField;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.me.ItemRepo;
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

public class GuiMEMonitorable extends AEBaseGui
        implements ISortSource, IConfigManagerHost, IDropToFillTextField, IPinsHandler {

    public static int craftingGridOffsetX;
    public static int craftingGridOffsetY;

    private static String memoryText = "";
    private final IDisplayRepo repo;
    protected int offsetRepoX = 9;
    protected int offsetRepoY = 18;
    private final int MAGIC_HEIGHT_NUMBER = 114 + 1;
    private final int lowerTextureOffset = 0;
    private final IConfigManager configSrc;
    private final boolean viewCell;
    private final ItemStack[] myCurrentViewCells = new ItemStack[5];
    private final ContainerMEMonitorable monitorableContainer;
    private GuiTabButton craftingStatusBtn;
    private GuiImgButton craftingStatusImgBtn;
    protected final MEGuiTextField searchField;
    private GuiText myName;
    private int perRow = 9;
    private int reservedSpace = 0;
    private boolean customSortOrder = true;
    protected int rows = 0;
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
    private boolean enableShiftPause = true;

    protected VirtualMEPinSlot[] pinSlots = null;
    protected VirtualMEMonitorableSlot[] monitorableSlots = null;

    private final ITerminalHost host;

    public GuiMEMonitorable(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        this(inventoryPlayer, te, new ContainerMEMonitorable(inventoryPlayer, te));
    }

    public GuiMEMonitorable(final InventoryPlayer inventoryPlayer, final ITerminalHost te,
            final ContainerMEMonitorable c) {

        super(c);

        final GuiScrollbar scrollbar = new GuiScrollbar();
        this.setScrollBar(scrollbar);
        this.repo = new ItemRepo(scrollbar, this);
        this.host = te;

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
        this.getScrollBar().setTop(this.offsetRepoY).setLeft(166 + this.offsetRepoX).setHeight(this.rows * 18 - 2);
        this.getScrollBar().setRange(
                0,
                (this.repo.size() + pinsState.ordinal() * 9 + this.perRow - 1) / this.perRow - this.rows,
                Math.max(1, this.rows / 6));
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        if (actionPerformedCustomButtons(btn)) return;

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

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);

        this.perRow = AEConfig.instance.getConfigManager().getSetting(Settings.TERMINAL_STYLE) != TerminalStyle.FULL ? 9
                : 9 + ((this.width - this.standardSize) / 18);
        this.rows = calculateRowsCount();

        // make sure we have space at least for one row of normal slots, because pins not adjusted by scroll bar
        adjustPinsSize();

        super.initGui();

        int pinsRows = pinsState.ordinal();
        this.pinSlots = new VirtualMEPinSlot[pinsRows * this.perRow];
        for (int y = 0; y < pinsRows; y++) {
            for (int x = 0; x < this.perRow; x++) {
                VirtualMEPinSlot slot = new VirtualMEPinSlot(
                        this.offsetRepoX + x * 18,
                        y * 18 + this.offsetRepoY,
                        this.repo,
                        y * this.perRow + x);
                this.pinSlots[y * this.perRow + x] = slot;
                this.registerVirtualSlots(slot);
            }
        }

        int normalSlotRows = this.rows - pinsRows;
        this.monitorableSlots = new VirtualMEMonitorableSlot[normalSlotRows * this.perRow];
        for (int y = 0; y < normalSlotRows; y++) {
            for (int x = 0; x < this.perRow; x++) {
                VirtualMEMonitorableSlot slot = new VirtualMEMonitorableSlot(
                        this.offsetRepoX + x * 18,
                        this.offsetRepoY + y * 18 + pinsRows * 18,
                        this.repo,
                        y * this.perRow + x);
                this.monitorableSlots[y * this.perRow + x] = slot;
                this.registerVirtualSlots(slot);
            }
        }

        if (AEConfig.instance.getConfigManager().getSetting(Settings.TERMINAL_STYLE) != TerminalStyle.FULL) {
            this.xSize = this.standardSize + ((this.perRow - 9) * 18);
        } else {
            this.xSize = this.standardSize;
        }

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

        if (this.viewCell) {
            this.buttonList.add(
                    this.ViewBox = new GuiImgButton(
                            this.guiLeft - 18,
                            offset,
                            Settings.VIEW_MODE,
                            this.configSrc.getSetting(Settings.VIEW_MODE)));
            offset += 20;
        }
        this.buttonList.add(
                this.typeFilter = new GuiImgButton(
                        this.guiLeft - 18,
                        offset,
                        Settings.TYPE_FILTER,
                        this.configSrc.getSetting(Settings.TYPE_FILTER)));
        offset += 20;

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

        if (!(this instanceof GuiMEPortableCell)) {
            this.buttonList.add(
                    this.terminalStyleBox = new GuiImgButton(
                            this.guiLeft - 18,
                            offset,
                            Settings.TERMINAL_STYLE,
                            AEConfig.instance.settings.getSetting(Settings.TERMINAL_STYLE)));
            offset += 20;
        }

        initCustomButtons(this.guiLeft - 18, offset);

        if (this.viewCell) {
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
                            getPinButtonX(),
                            getPinButtonY(),
                            Settings.PINS_STATE,
                            configSrc.getSetting(Settings.PINS_STATE)));
        }

        // Enum setting = AEConfig.INSTANCE.getSetting( "Terminal", SearchBoxMode.class, SearchBoxMode.AUTOSEARCH );
        final Enum searchMode = AEConfig.instance.settings.getSetting(Settings.SEARCH_MODE);
        this.canBeAutoFocused = SearchBoxMode.AUTOSEARCH == searchMode || SearchBoxMode.NEI_AUTOSEARCH == searchMode;

        this.searchField.x = this.guiLeft + this.offsetRepoX + 71;
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

        this.enableShiftPause = AEConfig.instance.settings.getSetting(Settings.PAUSE_WHEN_HOLDING_SHIFT) == YesNo.YES;
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

    protected int getPinButtonX() {
        return this.guiLeft + 178;
    }

    protected int getPinButtonY() {
        return this.guiTop + 18 + (rows * 18) + 25;
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

        VirtualMEPinSlot.drawSlotsBackground(this.pinSlots, this.mc, this.zLevel);

        this.currentMouseX = mouseX;
        this.currentMouseY = mouseY;
    }

    @Override
    public List<String> handleItemTooltip(ItemStack stack, int mouseX, int mouseY, List<String> currentToolTip) {
        super.handleItemTooltip(stack, mouseX, mouseY, currentToolTip);
        VirtualMESlot hoveredSlot = this.getVirtualMESlotUnderMouse();

        final boolean isMonitorableSlot = hoveredSlot instanceof VirtualMEMonitorableSlot;
        if (isMonitorableSlot || hoveredSlot instanceof VirtualMEPatternSlot) {
            IAEStack<?> aes = hoveredSlot.getAEStack();
            final int threshold = AEConfig.instance.getTerminalFontSize() == TerminalFontSize.SMALL ? 9999 : 999;
            if (aes != null && aes.getStackSize() > threshold) {
                final String local = isMonitorableSlot ? ButtonToolTips.ItemsStored.getLocal()
                        : ButtonToolTips.ItemCount.getLocal();
                final String formattedAmount = NumberFormat.getNumberInstance(Locale.US).format(aes.getStackSize());
                currentToolTip.add(EnumChatFormatting.GRAY + String.format(local, formattedAmount));
            }
        }

        return currentToolTip;
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) {
        searchField.mouseClicked(xCoord, yCoord, btn);
        isAutoFocused = false;

        if (handleViewCellClick(xCoord, yCoord, btn)) return;

        super.mouseClicked(xCoord, yCoord, btn);
    }

    private void sendAction(MonitorableAction action, @Nullable IAEStack<?> stack, int custom) {
        ((AEBaseContainer) this.inventorySlots).setTargetStack(stack);
        final PacketMonitorableAction p = new PacketMonitorableAction(action, custom);
        NetworkHandler.instance.sendToServer(p);
    }

    @Override
    protected boolean handleVirtualSlotClick(VirtualMESlot virtualSlot, final int mouseButton) {
        if (this.handleMonitorableSlotClick(virtualSlot, mouseButton)) return true;
        else return super.handleVirtualSlotClick(virtualSlot, mouseButton);
    }

    private boolean handleMonitorableSlotClick(VirtualMESlot virtualSlot, final int mouseButton) {
        if (!(virtualSlot instanceof VirtualMEMonitorableSlot slot)) return false;
        IAEItemStack slotStack = slot.getAEStack() instanceof IAEItemStack ais ? ais : null;

        if (Keyboard.isKeyDown(Keyboard.KEY_SPACE)) {
            if (slotStack != null) {
                this.sendAction(MonitorableAction.MOVE_REGION, slotStack, -1);
                return true;
            }
            return false;
        }

        final EntityPlayer player = Minecraft.getMinecraft().thePlayer;

        final boolean isLShiftDown = isShiftKeyDown();
        final boolean isLControlDown = isCtrlKeyDown();

        switch (mouseButton) {
            case 0 -> { // left click
                if (slot instanceof VirtualMEPinSlot && player.inventory.getItemStack() != null) {
                    this.sendAction(
                            isLControlDown ? MonitorableAction.SET_CONTAINER_PIN : MonitorableAction.SET_ITEM_PIN,
                            null,
                            slot.getSlotIndex());
                    return true;
                }

                if (isLControlDown) {
                    this.sendAction(
                            isLShiftDown ? MonitorableAction.FILL_CONTAINERS : MonitorableAction.FILL_SINGLE_CONTAINER,
                            slot.getAEStack(),
                            -1);
                    return true;
                }

                if (isLShiftDown) {
                    this.sendAction(MonitorableAction.SHIFT_CLICK, slotStack, -1);
                    return true;
                }

                if (slot.getAEStack() != null && slot.getAEStack().getStackSize() == 0
                        && player.inventory.getItemStack() == null) {
                    this.sendAction(MonitorableAction.AUTO_CRAFT, slot.getAEStack(), -1);
                    return true;
                }
                this.sendAction(MonitorableAction.PICKUP_OR_SET_DOWN, slotStack, -1);
                return true;
            }
            case 1 -> { // right click
                if (slot instanceof VirtualMEPinSlot) {
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
                            slot.getAEStack(),
                            -1);
                    return true;
                }

                if (isLShiftDown) {
                    this.sendAction(MonitorableAction.PICKUP_SINGLE, slotStack, -1);
                    return true;
                }

                this.sendAction(MonitorableAction.SPLIT_OR_PLACE_SINGLE, slotStack, -1);
                return true;
            }
            case 2 -> { // middle click
                if (slot.getAEStack() != null && slot.getAEStack().isCraftable()) {
                    this.sendAction(MonitorableAction.AUTO_CRAFT, slot.getAEStack(), -1);
                    return true;
                } else if (player.capabilities.isCreativeMode) {
                    this.sendAction(MonitorableAction.CREATIVE_DUPLICATE, slotStack, -1);
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
    protected boolean mouseWheelEvent(int x, int y, int wheel) {
        VirtualMESlot slot = this.getVirtualMESlotUnderMouse();
        if (!isShiftKeyDown() || !(slot instanceof VirtualMEMonitorableSlot)) return false;

        final MonitorableAction direction = wheel > 0 ? MonitorableAction.ROLL_DOWN : MonitorableAction.ROLL_UP;
        if (direction == MonitorableAction.ROLL_DOWN
                && Minecraft.getMinecraft().thePlayer.inventory.getItemStack() == null) {
            return false;
        }
        if (direction == MonitorableAction.ROLL_UP && !(slot.getAEStack() instanceof IAEItemStack)) {
            return false;
        }

        ((AEBaseContainer) this.inventorySlots).setTargetStack(slot.getAEStack());
        final int times = Math.abs(wheel);
        for (int i = 0; i < times; i++) {
            final PacketMonitorableAction p = new PacketMonitorableAction(direction, -1);
            NetworkHandler.instance.sendToServer(p);
        }

        return true;
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

        this.searchField.drawTextBox();

        this.updateViewCells();
    }

    protected String getBackground() {
        return "guis/terminal.png";
    }

    protected void updateViewCells() {
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
    }

    protected int getMaxRows() {
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
                VirtualMESlot slot = this.getVirtualMESlotUnderMouse();
                if (slot instanceof VirtualMEMonitorableSlot monitorableSlot) {
                    IAEStack<?> stack = monitorableSlot.getAEStack();
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

        if (searchField.isFocused() && (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER)) {
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

    protected int getReservedSpace() {
        return this.reservedSpace;
    }

    protected void setReservedSpace(final int reservedSpace) {
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
        if (enableShiftPause) this.repo.setPaused(isShiftKeyDown());
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
}
