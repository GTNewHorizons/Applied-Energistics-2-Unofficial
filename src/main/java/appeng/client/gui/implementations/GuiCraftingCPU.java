package appeng.client.gui.implementations;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.google.common.base.Joiner;

import appeng.api.config.CraftingAllow;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.config.YesNo;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.NamedDimensionalCoord;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.IGuiTooltipHandler;
import appeng.client.gui.widgets.GuiAeButton;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.gui.widgets.ITooltip;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.render.highlighter.BlockPosHighlighter;
import appeng.container.implementations.ContainerCraftingCPU;
import appeng.container.implementations.CraftingCpuEntry;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.localization.Localization;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCraftingItemInterface;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.InventoryAction;
import appeng.util.Platform;
import appeng.util.ReadableNumberConverter;
import appeng.util.ScheduledReason;

public class GuiCraftingCPU extends AEBaseGui implements ISortSource, IGuiTooltipHandler {

    protected static final int GUI_HEIGHT = 184;
    protected static final int GUI_WIDTH = 238;

    protected static final int DISPLAYED_ROWS = 6;
    protected static final int SECTION_LENGTH = 67;
    protected static final int SECTION_HEIGHT = 23;
    protected static final int TEXTURE_BELOW_TOP_ROW_Y = 41;
    protected static final int TEXTURE_ABOVE_BOTTOM_ROW_Y = 51;

    protected static final int SCROLLBAR_TOP = 19;
    protected static final int SCROLLBAR_LEFT = 218;
    protected static final int SCROLLBAR_HEIGHT = 137;

    private static final int CANCEL_LEFT_OFFSET = 163;
    private static final int CANCEL_TOP_OFFSET = 25;
    private static final int CANCEL_HEIGHT = 20;
    private static final int CANCEL_WIDTH = 50;

    private static final int SUSPEND_LEFT_OFFSET = 60;
    private static final int SUSPEND_TOP_OFFSET = 25;
    private static final int SUSPEND_HEIGHT = 20;
    private static final int SUSPEND_WIDTH = 50;

    private static final int TITLE_TOP_OFFSET = 7;
    private static final int TITLE_LEFT_OFFSET = 8;

    private static final int ITEMSTACK_LEFT_OFFSET = 9;
    private static final int ITEMSTACK_TOP_OFFSET = 22;
    private static final int ITEMS_PER_ROW = 3;

    private static final int ICON_NO_TARGET = 132;
    private static final int ICON_LOCK_MODE = 133;
    private static final int ICON_BLOCK_MODE = 134;

    private final ContainerCraftingCPU container;
    private final CraftingCpuVisualState visualState = new CraftingCpuVisualState();
    private final RemainingOperationsTooltip remainingOperationsTooltip = new RemainingOperationsTooltip();

    protected int rows = DISPLAYED_ROWS;

    private GuiButton cancel;
    private GuiAeButton suspend;
    private GuiImgButton toggleHideStored;
    private GuiImgButton changeAllow;
    private MEGuiTextField searchField;
    private boolean hideStored;
    private int remainingOperations;
    private int hoveredVisibleIndex = -1;
    private IAEStack<?> hoveredStack;
    private NBTTagCompound hoveredInterfaceLocations;

    public GuiCraftingCPU(final InventoryPlayer inventoryPlayer, final Object target) {
        this(new ContainerCraftingCPU(inventoryPlayer, target));
    }

    protected GuiCraftingCPU(final ContainerCraftingCPU container) {
        super(container);
        this.container = container;
        this.ySize = GUI_HEIGHT;
        this.xSize = GUI_WIDTH;
        this.hideStored = AEConfig.instance.getConfigManager().getSetting(Settings.HIDE_STORED) == YesNo.YES;
        this.setScrollBar(new GuiScrollbar());
    }

    public void clearItems() {
        this.visualState.clear();
        this.remainingOperations = 0;
        this.hoveredVisibleIndex = -1;
        this.hoveredStack = null;
        this.hoveredInterfaceLocations = null;
        this.rebuildFilteredEntries();
    }

    private final class RemainingOperationsTooltip implements ITooltip {

        @Override
        public String getMessage() {
            return GuiText.RemainingOperations.getLocal();
        }

        @Override
        public int xPos() {
            return guiLeft + TITLE_LEFT_OFFSET + 200 - this.getWidth();
        }

        @Override
        public int yPos() {
            return guiTop + TITLE_TOP_OFFSET;
        }

        @Override
        public int getWidth() {
            return fontRendererObj.getStringWidth(String.valueOf(remainingOperations));
        }

        @Override
        public int getHeight() {
            return fontRendererObj.FONT_HEIGHT;
        }

        @Override
        public boolean isVisible() {
            return true;
        }
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        if (this.cancel == btn) {
            this.sendConfigPacket("TileCrafting.Cancel", "Cancel");
            return;
        }

        if (this.suspend == btn) {
            this.sendConfigPacket("TileCrafting.Suspend", "Suspend");
            return;
        }

        if (this.toggleHideStored == btn) {
            this.hideStored = !this.hideStored;
            AEConfig.instance.getConfigManager()
                    .putSetting(Settings.HIDE_STORED, this.hideStored ? YesNo.YES : YesNo.NO);
            this.toggleHideStored.set(this.hideStored ? YesNo.YES : YesNo.NO);
            this.rebuildFilteredEntries();
            return;
        }

        if (btn == this.changeAllow) {
            final String msg = String.valueOf(this.changeAllow.getCurrentValue().ordinal());
            this.sendConfigPacket("TileCrafting.Allow", msg);
        }
    }

    private void sendConfigPacket(final String key, final String value) {
        try {
            NetworkHandler.instance.sendToServer(new PacketValueConfig(key, value));
        } catch (final IOException e) {
            AELog.debug(e);
        }
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) {
        if (isShiftKeyDown() && this.hoveredInterfaceLocations != null) {
            this.highlightHoveredInterfaces();
        } else if (this.hoveredStack != null && btn == 2) {
            container.setTargetStack(this.hoveredStack);
            final PacketInventoryAction packet = new PacketInventoryAction(
                    InventoryAction.AUTO_CRAFT,
                    this.inventorySlots.inventorySlots.size(),
                    this.hoveredStack.getStackSize());
            NetworkHandler.instance.sendToServer(packet);
        }

        super.mouseClicked(xCoord, yCoord, btn);
        this.searchField.mouseClicked(xCoord, yCoord, btn);
    }

    private void highlightHoveredInterfaces() {
        final List<NamedDimensionalCoord> blocks = NamedDimensionalCoord
                .readAsListFromNBTNamed(this.hoveredInterfaceLocations);
        if (blocks.isEmpty()) {
            return;
        }

        final Map<NamedDimensionalCoord, String[]> messages = new HashMap<>();
        for (final NamedDimensionalCoord block : blocks) {
            messages.put(
                    block,
                    new String[] { PlayerMessages.MachineHighlightedNamed.getUnlocalized(),
                            PlayerMessages.MachineInOtherDimNamed.getUnlocalized() });
        }

        BlockPosHighlighter.highlightNamedBlocks(
                this.mc.thePlayer,
                messages,
                ((Localization) () -> "tile.appliedenergistics2.BlockInterface.name").getLocal());
        this.mc.thePlayer.closeScreen();
    }

    @Override
    public void initGui() {
        super.initGui();
        this.cancel = new GuiButton(
                0,
                this.guiLeft + CANCEL_LEFT_OFFSET,
                this.guiTop + this.ySize - CANCEL_TOP_OFFSET,
                CANCEL_WIDTH,
                CANCEL_HEIGHT,
                GuiText.Cancel.getLocal());
        this.suspend = new GuiAeButton(
                1,
                this.guiLeft + SUSPEND_LEFT_OFFSET,
                this.guiTop + this.ySize - SUSPEND_TOP_OFFSET,
                SUSPEND_WIDTH,
                SUSPEND_HEIGHT,
                GuiText.Suspend.getLocal(),
                ButtonToolTips.Suspend.getLocal());
        this.toggleHideStored = new GuiImgButton(
                this.guiLeft + 221,
                this.guiTop + this.ySize - 19,
                Settings.HIDE_STORED,
                AEConfig.instance.getConfigManager().getSetting(Settings.HIDE_STORED));
        this.changeAllow = new GuiImgButton(
                this.guiLeft - 20,
                this.guiTop + 2,
                Settings.CRAFTING_ALLOW,
                CraftingAllow.ALLOW_ALL);

        this.searchField = new MEGuiTextField(76, 12, "Search") {

            @Override
            public void onTextChange(final String oldText) {
                super.onTextChange(oldText);
                rebuildFilteredEntries();
            }
        };
        this.searchField.x = this.guiLeft + this.xSize - 101;
        this.searchField.y = this.guiTop + 5;

        this.buttonList.add(this.toggleHideStored);
        this.buttonList.add(this.cancel);
        this.buttonList.add(this.suspend);
        this.buttonList.add(this.changeAllow);
        this.rebuildFilteredEntries();
    }

    private void rebuildFilteredEntries() {
        final String searchText = this.searchField == null ? "" : this.searchField.getText();
        this.visualState.rebuildFilteredEntries(this.hideStored, searchText);
        this.updateScrollBar();
    }

    protected void updateScrollBar() {
        final int size = this.visualState.filteredSize();
        this.getScrollBar().setTop(SCROLLBAR_TOP).setLeft(SCROLLBAR_LEFT).setHeight(this.getScrollBarHeight());
        this.getScrollBar().setRange(0, (size + 2) / ITEMS_PER_ROW - this.rows, 1);
    }

    protected int getScrollBarHeight() {
        return SCROLLBAR_HEIGHT;
    }

    protected boolean hasVisualEntries() {
        return !this.visualState.isEmpty();
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {
        this.cancel.enabled = this.hasVisualEntries();
        this.suspend.visible = this.hasVisualEntries();
        this.updateSuspendButtonText();
        this.changeAllow.set(CraftingAllow.values()[this.container.allow]);

        this.hoveredVisibleIndex = this.resolveHoveredIndex(mouseX, mouseY);
        this.handleTooltip(mouseX, mouseY, this.remainingOperationsTooltip);
        super.drawScreen(mouseX, mouseY, btn);
    }

    private int resolveHoveredIndex(final int mouseX, final int mouseY) {
        final int gridLeft = (this.width - this.xSize) / 2;
        final int gridTop = (this.height - this.ySize) / 2;
        int row = 0;
        int column = 0;

        for (int slotIndex = 0; slotIndex <= ITEMS_PER_ROW * this.rows; slotIndex++) {
            final int minX = gridLeft + ITEMSTACK_LEFT_OFFSET + column * SECTION_LENGTH;
            final int minY = gridTop + ITEMSTACK_TOP_OFFSET + row * SECTION_HEIGHT;

            if (minX < mouseX && minX + SECTION_LENGTH > mouseX && minY < mouseY && minY + SECTION_HEIGHT > mouseY) {
                return slotIndex;
            }

            column++;
            if (column == ITEMS_PER_ROW) {
                row++;
                column = 0;
            }
        }

        return -1;
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.drawHeader();
        this.drawVisibleEntries();
    }

    private void drawHeader() {
        String title = this.getGuiDisplayName(GuiText.CraftingStatus.getLocal());

        if (this.container.getElapsedTime() > 0 && this.hasVisualEntries()) {
            final long elapsedInMilliseconds = TimeUnit.MILLISECONDS
                    .convert(this.container.getElapsedTime(), TimeUnit.NANOSECONDS);
            final String elapsedTimeText = DurationFormatUtils
                    .formatDuration(elapsedInMilliseconds, GuiText.ETAFormat.getLocal());
            title = title.isEmpty() ? elapsedTimeText : title + " - " + elapsedTimeText;
        }

        this.fontRendererObj.drawString(
                String.valueOf(this.remainingOperations),
                TITLE_LEFT_OFFSET + 128 - this.remainingOperationsTooltip.getWidth(),
                TITLE_TOP_OFFSET,
                GuiColors.CraftingCPUTitle.getColor());
        this.fontRendererObj
                .drawString(title, TITLE_LEFT_OFFSET, TITLE_TOP_OFFSET, GuiColors.CraftingCPUTitle.getColor());
    }

    private void drawVisibleEntries() {
        final int viewStart = this.getScrollBar().getCurrentScroll() * ITEMS_PER_ROW;
        final int viewEnd = Math.min(viewStart + ITEMS_PER_ROW * this.rows, this.visualState.filteredSize());
        final List<CraftingCpuEntry> filteredEntries = this.visualState.filteredEntries();
        final ReadableNumberConverter converter = ReadableNumberConverter.INSTANCE;
        final IAEStack<?> lastHoveredStack = this.hoveredStack;

        this.hoveredStack = null;

        int x = 0;
        int y = 0;
        for (int index = viewStart; index < viewEnd; index++) {
            final CraftingCpuEntry entry = filteredEntries.get(index);
            final EntryTooltip tooltipData = this
                    .drawEntry(entry, index - viewStart, x, y, converter, lastHoveredStack);
            if (tooltipData != null) {
                this.drawTooltip(tooltipData.x, tooltipData.y, tooltipData.message);
            }

            x++;
            if (x > 2) {
                y++;
                x = 0;
            }
        }
    }

    private EntryTooltip drawEntry(final CraftingCpuEntry entry, final int visibleIndex, final int x, final int y,
            final ReadableNumberConverter converter, final IAEStack<?> lastHoveredStack) {
        final boolean active = entry.hasActiveAmount();
        final boolean scheduled = entry.hasPendingAmount();
        final int lines = (entry.hasStoredAmount() ? 1 : 0) + (active ? 1 : 0) + (scheduled ? 1 : 0);
        final ScheduledReason scheduledReason = entry.getScheduledReason();

        GL11.glPushMatrix();
        GL11.glScaled(0.5, 0.5, 0.5);

        if (AEConfig.instance.useColoredCraftingStatus && (active || scheduled)) {
            final int startX = (x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET) * 2;
            final int startY = ((y * SECTION_HEIGHT + ITEMSTACK_TOP_OFFSET) - 3) * 2;
            final int endX = startX + (SECTION_LENGTH * 2);
            final int endY = startY + (SECTION_HEIGHT * 2) - 2;
            drawRect(startX, startY, endX, endY, this.getCraftingStateColor(scheduledReason, active));
        }

        final LinkedList<String> lineList = new LinkedList<>();
        final int negY = ((lines - 1) * 5) / 2;
        int downY = 0;

        if (entry.hasStoredAmount()) {
            downY = this.drawAmountLine(
                    GuiText.Stored.getLocal(),
                    entry.getStoredAmount(),
                    x,
                    y,
                    negY,
                    downY,
                    GuiColors.CraftingCPUStored.getColor(),
                    visibleIndex,
                    lineList,
                    converter);
        }

        if (entry.hasActiveAmount()) {
            downY = this.drawAmountLine(
                    GuiText.Crafting.getLocal(),
                    entry.getActiveAmount(),
                    x,
                    y,
                    negY,
                    downY,
                    GuiColors.CraftingCPUAmount.getColor(),
                    visibleIndex,
                    lineList,
                    converter);
        }

        if (entry.hasPendingAmount()) {
            this.drawAmountLine(
                    GuiText.Scheduled.getLocal(),
                    entry.getPendingAmount(),
                    x,
                    y,
                    negY,
                    downY,
                    GuiColors.CraftingCPUScheduled.getColor(),
                    visibleIndex,
                    lineList,
                    converter);
        }

        GL11.glPopMatrix();

        final int iconX = x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET;
        final int iconY = y * SECTION_HEIGHT + ITEMSTACK_TOP_OFFSET - 3;
        this.drawScheduledReasonIcon(iconX, iconY, this.getScheduledReasonIconIndex(scheduledReason, active));

        final int posX = x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 19;
        final int posY = y * SECTION_HEIGHT + ITEMSTACK_TOP_OFFSET;

        EntryTooltip tooltipData = null;
        if (this.hoveredVisibleIndex == visibleIndex) {
            final IAEStack<?> hoveredVisualStack = entry.getVisualStack();
            final boolean stackChanged = lastHoveredStack == null || !hoveredVisualStack.isSameType(lastHoveredStack);
            final String tooltipMessage = this.buildTooltipMessage(hoveredVisualStack, lineList, stackChanged);
            tooltipData = new EntryTooltip(
                    x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 8,
                    y * SECTION_HEIGHT + ITEMSTACK_TOP_OFFSET + 10,
                    tooltipMessage);
            this.hoveredStack = hoveredVisualStack;
        }

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        entry.getStack().drawInGui(this.mc, posX, posY);
        GL11.glPopAttrib();

        return tooltipData;
    }

    private int drawAmountLine(final String label, final long amount, final int x, final int y, final int negY,
            final int downY, final int color, final int visibleIndex, final List<String> tooltipLines,
            final ReadableNumberConverter converter) {
        final String compactText = label + ": " + converter.toWideReadableForm(amount);
        final int width = 4 + this.fontRendererObj.getStringWidth(compactText);
        this.fontRendererObj.drawString(
                compactText,
                (int) ((x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 19 - (width * 0.5)) * 2),
                (y * SECTION_HEIGHT + ITEMSTACK_TOP_OFFSET + 6 - negY + downY) * 2,
                color);

        if (this.hoveredVisibleIndex == visibleIndex) {
            tooltipLines.add(label + ": " + NumberFormat.getInstance().format(amount));
        }

        return downY + 5;
    }

    private String buildTooltipMessage(final IAEStack<?> refStack, final List<String> lineList,
            final boolean stackChanged) {
        String tooltipMessage = Platform.getItemDisplayName(refStack);
        if (!lineList.isEmpty()) {
            this.addItemTooltip(refStack, lineList, stackChanged);
            tooltipMessage = tooltipMessage + '\n' + Joiner.on("\n").join(lineList);
        }
        return tooltipMessage;
    }

    private int getCraftingStateColor(final ScheduledReason scheduledReason, final boolean active) {
        if (scheduledReason == ScheduledReason.UNDEFINED) {
            return active ? GuiColors.CraftingCPUActive.getColor() : GuiColors.CraftingCPUInactive.getColor();
        }

        return switch (scheduledReason) {
            case UNSUPPORTED_STACK -> GuiColors.CraftingCPUUnsupportedStack.getColor();
            case SAME_NETWORK -> GuiColors.CraftingCPUSameNetwork.getColor();
            case SOMETHING_STUCK -> GuiColors.CraftingCPUSomethingStuck.getColor();
            case NO_TARGET -> GuiColors.CraftingCPUNoTarget.getColor();
            default -> active ? GuiColors.CraftingCPUActive.getColor() : GuiColors.CraftingCPUInactive.getColor();
        };
    }

    private int getScheduledReasonIconIndex(final ScheduledReason scheduledReason, final boolean active) {
        if (scheduledReason == ScheduledReason.UNDEFINED) {
            return -1;
        }

        return switch (scheduledReason) {
            case NO_TARGET, UNSUPPORTED_STACK, SAME_NETWORK -> ICON_NO_TARGET;
            case LOCK_MODE -> ICON_LOCK_MODE;
            case BLOCKING_MODE -> ICON_BLOCK_MODE;
            case NOT_ENOUGH_INGREDIENTS, SOMETHING_STUCK -> -1;
            default -> -1;
        };
    }

    private void drawScheduledReasonIcon(final int x, final int y, final int iconIndex) {
        if (iconIndex < 0) {
            return;
        }

        this.bindTexture("guis/states.png");
        final int uvY = iconIndex / 16;
        final int uvX = iconIndex - uvY * 16;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 0.9f);
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0.0f);
        GL11.glScalef(0.75f, 0.75f, 1.0f);
        this.drawTexturedModalRect(0, 0, uvX * 16, uvY * 16, 11, 11);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    protected void addItemTooltip(final IAEStack<?> refStack, final List<String> lineList, final boolean stackChanged) {
        final ScheduledReason scheduledReason = this.visualState.findScheduledReason(refStack);
        if (scheduledReason != ScheduledReason.UNDEFINED) {
            lineList.add(scheduledReason.getLocal());
        }

        if (isShiftKeyDown()) {
            final List<String> tooltipLines = refStack instanceof IAEItemStack itemStack
                    ? itemStack.getItemStack().getTooltip(this.mc.thePlayer, this.mc.gameSettings.advancedItemTooltips)
                    : Collections.emptyList();
            if (!tooltipLines.isEmpty()) {
                tooltipLines.remove(0);
            }
            lineList.addAll(tooltipLines);

            if (this.hoveredInterfaceLocations == null || stackChanged) {
                try {
                    NetworkHandler.instance.sendToServer(new PacketCraftingItemInterface(refStack.copy()));
                } catch (final Exception ignored) {}
            } else {
                final List<NamedDimensionalCoord> blocks = NamedDimensionalCoord
                        .readAsListFromNBTNamed(this.hoveredInterfaceLocations);
                if (blocks.isEmpty()) {
                    return;
                }

                for (final NamedDimensionalCoord blockPos : blocks) {
                    lineList.add(
                            String.format(
                                    "Dim:%s X:%s Y:%s Z:%s \"%s\"",
                                    blockPos.getDimension(),
                                    blockPos.x,
                                    blockPos.y,
                                    blockPos.z,
                                    blockPos.getCustomName()));
                }
                lineList.add(GuiText.HoldShiftClick_HIGHLIGHT_INTERFACE.getLocal());
            }
        } else {
            this.hoveredInterfaceLocations = null;
            lineList.add(GuiText.HoldShiftForTooltip.getLocal());
        }
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/craftingcpu.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
        this.drawSearch();
    }

    public void drawSearch() {
        this.bindTexture("guis/searchField.png");
        this.drawTexturedModalRect(this.guiLeft + this.xSize - 101, this.guiTop + 5, 0, 0, 76, 12);
        this.searchField.drawTextBox();
    }

    @Override
    protected void keyTyped(final char character, final int key) {
        if (!this.searchField.textboxKeyTyped(character, key)) {
            super.keyTyped(character, key);
        }
    }

    public void postUpdateTooltip(final NBTTagCompound nbt) {
        this.hoveredInterfaceLocations = nbt;
    }

    public void postVisualEntryUpdate(final CraftingCpuEntry[] updates, final boolean clearFirst,
            final int remainingOperations) {
        this.visualState.applyEntryUpdates(updates, clearFirst);
        this.remainingOperations = remainingOperations;
        this.rebuildFilteredEntries();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Enum getSortBy() {
        return SortOrder.NAME;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Enum getSortDir() {
        return SortDir.ASCENDING;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Enum getSortDisplay() {
        return ViewItems.ALL;
    }

    @Override
    public ItemStack getHoveredStack() {
        return this.hoveredStack != null ? this.hoveredStack.getItemStackForNEI() : null;
    }

    private void updateSuspendButtonText() {
        final boolean suspended = this.container.cachedSuspend;
        this.suspend.displayString = suspended ? GuiText.Resume.getLocal() : GuiText.Suspend.getLocal();
        this.suspend.setTootipString(suspended ? ButtonToolTips.Resume.getLocal() : ButtonToolTips.Suspend.getLocal());
    }

    private static final class EntryTooltip {

        private final int x;
        private final int y;
        private final String message;

        private EntryTooltip(final int x, final int y, final String message) {
            this.x = x;
            this.y = y;
            this.message = message;
        }
    }

    private static final class CraftingCpuVisualState {

        private final Map<IAEStack<?>, CraftingCpuEntry> entries = new LinkedHashMap<>();
        private final List<CraftingCpuEntry> filteredEntries = new ArrayList<>();

        public void clear() {
            this.entries.clear();
            this.filteredEntries.clear();
        }

        public void applyEntryUpdates(final CraftingCpuEntry[] updates, final boolean clearFirst) {
            if (clearFirst) {
                this.clear();
            }

            if (updates == null) {
                return;
            }

            for (final CraftingCpuEntry update : updates) {
                if (update == null || update.getStack() == null) {
                    continue;
                }

                final IAEStack<?> key = CraftingCpuEntry.normalizeStack(update.getStack());
                if (update.getTotalAmount() <= 0) {
                    this.entries.remove(key);
                    continue;
                }

                this.entries.put(key, update);
            }
        }

        public void rebuildFilteredEntries(final boolean hideStored, final String searchText) {
            final String normalizedSearchText = searchText == null ? "" : searchText.toLowerCase();
            this.filteredEntries.clear();

            for (final CraftingCpuEntry entry : this.entries.values()) {
                if (hideStored && !entry.isVisibleWhenHideStoredEnabled()) {
                    continue;
                }

                if (!entry.matchesSearch(normalizedSearchText)) {
                    continue;
                }

                this.filteredEntries.add(entry);
            }
        }

        public boolean isEmpty() {
            return this.entries.isEmpty();
        }

        public int filteredSize() {
            return this.filteredEntries.size();
        }

        public List<CraftingCpuEntry> filteredEntries() {
            return this.filteredEntries;
        }

        public ScheduledReason findScheduledReason(final IAEStack<?> stack) {
            if (stack == null) {
                return ScheduledReason.UNDEFINED;
            }

            final CraftingCpuEntry entry = this.entries.get(CraftingCpuEntry.normalizeStack(stack));
            return entry == null ? ScheduledReason.UNDEFINED : entry.getScheduledReason();
        }
    }
}
