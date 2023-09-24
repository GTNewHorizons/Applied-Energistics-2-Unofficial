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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.google.common.collect.HashMultimap;

import appeng.api.AEApi;
import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.WorldCoord;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.IDropToFillTextField;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.me.ClientDCInternalInv;
import appeng.client.me.SlotDisconnected;
import appeng.client.render.BlockPosHighlighter;
import appeng.container.implementations.ContainerInterfaceTerminal;
import appeng.container.slot.AppEngSlot;
import appeng.core.AEConfig;
import appeng.core.CommonHelper;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.helpers.PatternHelper;
import appeng.integration.IntegrationRegistry;
import appeng.integration.IntegrationType;
import appeng.parts.reporting.PartInterfaceTerminal;
import appeng.util.Platform;

public class GuiInterfaceTerminal extends AEBaseGui implements IDropToFillTextField {

    private static final int MAGIC_HEIGHT_NUMBER = 52 + 99;
    private static final int offsetX = 21;

    private final HashMap<Long, ClientDCInternalInv> byId = new HashMap<>();
    private final HashMultimap<String, ClientDCInternalInv> byName = HashMultimap.create();
    private final HashMap<ClientDCInternalInv, DimensionalCoord> blockPosHashMap = new HashMap<>();
    private final HashMap<GuiButton, ClientDCInternalInv> guiButtonHashMap = new HashMap<>();
    private final ArrayList<String> names = new ArrayList<>();
    private final ArrayList<Object> lines = new ArrayList<>();
    private final Set<Object> matchedStacks = new HashSet<>();

    private final Map<String, Set<Object>> cachedSearches = new WeakHashMap<>();

    private final MEGuiTextField searchFieldOutputs;
    private final MEGuiTextField searchFieldInputs;
    private final MEGuiTextField searchFieldNames;
    private final GuiImgButton guiButtonHideFull;
    private final GuiImgButton guiButtonAssemblersOnly;
    private final GuiImgButton guiButtonBrokenRecipes;
    private final GuiImgButton terminalStyleBox;
    private boolean refreshList = false;
    private boolean onlyMolecularAssemblers = false;
    private boolean onlyBrokenRecipes = false;

    // private final IConfigManager configSrc;
    private int rows = 3;

    private static final String MOLECULAR_ASSEMBLER = "tile.appliedenergistics2.BlockMolecularAssembler";

    public GuiInterfaceTerminal(final InventoryPlayer inventoryPlayer, final PartInterfaceTerminal te) {
        super(new ContainerInterfaceTerminal(inventoryPlayer, te));

        this.setScrollBar(new GuiScrollbar());
        this.xSize = 208;
        this.ySize = 255;

        searchFieldInputs = new MEGuiTextField(86, 12, ButtonToolTips.SearchFieldInputs.getLocal()) {

            @Override
            public void onTextChange(final String oldText) {
                refreshList();
            }
        };

        searchFieldOutputs = new MEGuiTextField(86, 12, ButtonToolTips.SearchFieldOutputs.getLocal()) {

            @Override
            public void onTextChange(final String oldText) {
                refreshList();
            }
        };

        searchFieldNames = new MEGuiTextField(71, 12, ButtonToolTips.SearchFieldNames.getLocal()) {

            @Override
            public void onTextChange(final String oldText) {
                refreshList();
            }
        };
        searchFieldNames.setFocused(true);

        guiButtonAssemblersOnly = new GuiImgButton(0, 0, Settings.ACTIONS, null);
        guiButtonHideFull = new GuiImgButton(0, 0, Settings.ACTIONS, null);
        guiButtonBrokenRecipes = new GuiImgButton(0, 0, Settings.ACTIONS, null);

        terminalStyleBox = new GuiImgButton(0, 0, Settings.TERMINAL_STYLE, null);
    }

    private void setScrollBar() {
        this.getScrollBar().setTop(52).setLeft(189).setHeight(this.rows * 18 - 2);
        this.getScrollBar().setRange(0, this.lines.size() - this.rows, 2);
    }

    @Override
    public void initGui() {
        this.rows = calculateRowsCount();

        super.initGui();

        this.ySize = MAGIC_HEIGHT_NUMBER + this.rows * 18;
        final int unusedSpace = this.height - this.ySize;
        this.guiTop = (int) Math.floor(unusedSpace / (unusedSpace < 0 ? 3.8f : 2.0f));

        searchFieldInputs.x = guiLeft + Math.max(32, offsetX);
        searchFieldInputs.y = guiTop + 25;

        searchFieldOutputs.x = guiLeft + Math.max(32, offsetX);
        searchFieldOutputs.y = guiTop + 38;

        searchFieldNames.x = guiLeft + Math.max(32, offsetX) + 99;
        searchFieldNames.y = guiTop + 38;

        guiButtonAssemblersOnly.xPosition = guiLeft + Math.max(32, offsetX) + 99;
        guiButtonAssemblersOnly.yPosition = guiTop + 20;

        guiButtonHideFull.xPosition = guiButtonAssemblersOnly.xPosition + 18;
        guiButtonHideFull.yPosition = guiTop + 20;

        guiButtonBrokenRecipes.xPosition = guiButtonHideFull.xPosition + 18;
        guiButtonBrokenRecipes.yPosition = guiTop + 20;

        terminalStyleBox.xPosition = guiLeft - 18;
        terminalStyleBox.yPosition = guiTop + 8;

        this.setScrollBar();
        this.repositionSlots();
    }

    protected void repositionSlots() {
        for (final Object obj : this.inventorySlots.inventorySlots) {
            if (obj instanceof AppEngSlot slot) {
                slot.yDisplayPosition = this.ySize + slot.getY() - 78 - 7;
            }
        }
    }

    protected int calculateRowsCount() {
        final int maxRows = this.getMaxRows();
        final boolean hasNEI = IntegrationRegistry.INSTANCE.isEnabled(IntegrationType.NEI);
        final int NEIPadding = hasNEI ? 22 /* input */ + 18 /* top panel */ : 0;
        final int extraSpace = this.height - MAGIC_HEIGHT_NUMBER - NEIPadding;

        return Math.max(3, Math.min(maxRows, extraSpace / 18));
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        fontRendererObj.drawString(
                getGuiDisplayName(GuiText.InterfaceTerminal.getLocal()),
                8,
                6,
                GuiColors.InterfaceTerminalTitle.getColor());
        fontRendererObj.drawString(
                GuiText.inventory.getLocal(),
                GuiInterfaceTerminal.offsetX + 2,
                this.ySize - 96,
                GuiColors.InterfaceTerminalInventory.getColor());

        int offset = 51;
        final int ex = getScrollBar().getCurrentScroll();
        for (int x = 0; x < this.rows && ex + x < this.lines.size(); x++) {
            final Object lineObj = this.lines.get(ex + x);
            if (lineObj instanceof ClientDCInternalInv inv) {
                for (int z = 0; z < inv.getInventory().getSizeInventory(); z++) {
                    if (this.matchedStacks.contains(inv.getInventory().getStackInSlot(z))) drawRect(
                            z * 18 + 22,
                            1 + offset,
                            z * 18 + 22 + 16,
                            1 + offset + 16,
                            GuiColors.InterfaceTerminalMatch.getColor());
                }
            } else if (lineObj instanceof String name) {
                final int rows = this.byName.get(name).size();
                String postfix = "";

                if (rows > 1) {
                    postfix = " (" + rows + ')';
                }

                while (name.length() > 2 && this.fontRendererObj.getStringWidth(name + postfix) > 158) {
                    name = name.substring(0, name.length() - 1);
                }

                this.fontRendererObj.drawString(
                        name + postfix,
                        GuiInterfaceTerminal.offsetX + 3,
                        6 + offset,
                        GuiColors.InterfaceTerminalName.getColor());
            }

            offset += 18;
        }
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {

        buttonList.clear();
        inventorySlots.inventorySlots.removeIf(slot -> slot instanceof SlotDisconnected);

        guiButtonAssemblersOnly.set(
                onlyMolecularAssemblers ? ActionItems.MOLECULAR_ASSEMBLEERS_ON : ActionItems.MOLECULAR_ASSEMBLEERS_OFF);
        guiButtonHideFull.set(
                AEConfig.instance.showOnlyInterfacesWithFreeSlotsInInterfaceTerminal
                        ? ActionItems.TOGGLE_SHOW_FULL_INTERFACES_OFF
                        : ActionItems.TOGGLE_SHOW_FULL_INTERFACES_ON);
        guiButtonBrokenRecipes.set(
                onlyBrokenRecipes ? ActionItems.TOGGLE_SHOW_ONLY_INVALID_PATTERN_OFF
                        : ActionItems.TOGGLE_SHOW_ONLY_INVALID_PATTERN_ON);

        terminalStyleBox.set(AEConfig.instance.settings.getSetting(Settings.TERMINAL_STYLE));

        buttonList.add(guiButtonAssemblersOnly);
        buttonList.add(guiButtonHideFull);
        buttonList.add(guiButtonBrokenRecipes);

        buttonList.add(terminalStyleBox);
        guiButtonHashMap.clear();

        int offset = 51;
        final int ex = this.getScrollBar().getCurrentScroll();
        for (int x = 0; x < this.rows && ex + x < this.lines.size(); x++) {
            final Object lineObj = this.lines.get(ex + x);
            if (lineObj instanceof ClientDCInternalInv inv) {
                for (int z = 0; z < inv.getInventory().getSizeInventory(); z++) {
                    inventorySlots.inventorySlots.add(new SlotDisconnected(inv, z, z * 18 + 22, 1 + offset));
                }

                GuiButton guiButton = new GuiImgButton(
                        guiLeft + 4,
                        guiTop + offset + 1,
                        Settings.ACTIONS,
                        ActionItems.HIGHLIGHT_INTERFACE);
                guiButtonHashMap.put(guiButton, inv);
                buttonList.add(guiButton);
            }

            offset += 18;
        }

        super.drawScreen(mouseX, mouseY, btn);

        handleTooltip(mouseX, mouseY, searchFieldInputs);
        handleTooltip(mouseX, mouseY, searchFieldOutputs);
        handleTooltip(mouseX, mouseY, searchFieldNames);
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) {
        searchFieldInputs.mouseClicked(xCoord, yCoord, btn);
        searchFieldOutputs.mouseClicked(xCoord, yCoord, btn);
        searchFieldNames.mouseClicked(xCoord, yCoord, btn);

        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        if (guiButtonHashMap.containsKey(btn)) {
            DimensionalCoord blockPos = blockPosHashMap.get(guiButtonHashMap.get(btn));
            WorldCoord blockPos2 = new WorldCoord(
                    (int) mc.thePlayer.posX,
                    (int) mc.thePlayer.posY,
                    (int) mc.thePlayer.posZ);
            if (mc.theWorld.provider.dimensionId != blockPos.getDimension()) {
                mc.thePlayer.addChatMessage(
                        new ChatComponentTranslation(
                                PlayerMessages.InterfaceInOtherDim.getName(),
                                blockPos.getDimension()));
            } else {
                BlockPosHighlighter.highlightBlock(
                        blockPos,
                        System.currentTimeMillis() + 500 * WorldCoord.getTaxicabDistance(blockPos, blockPos2));
                mc.thePlayer.addChatMessage(
                        new ChatComponentTranslation(
                                PlayerMessages.InterfaceHighlighted.getName(),
                                blockPos.x,
                                blockPos.y,
                                blockPos.z));
            }
            mc.thePlayer.closeScreen();
        } else if (btn == guiButtonHideFull) {
            AEConfig.instance.showOnlyInterfacesWithFreeSlotsInInterfaceTerminal = !AEConfig.instance.showOnlyInterfacesWithFreeSlotsInInterfaceTerminal;
            this.refreshList();
        } else if (btn == guiButtonAssemblersOnly) {
            onlyMolecularAssemblers = !onlyMolecularAssemblers;
            this.refreshList();
        } else if (btn == guiButtonBrokenRecipes) {
            onlyBrokenRecipes = !onlyBrokenRecipes;
            this.refreshList();
        } else if (btn instanceof GuiImgButton iBtn) {
            if (iBtn.getSetting() != Settings.ACTIONS) {
                final Enum cv = iBtn.getCurrentValue();
                final boolean backwards = Mouse.isButtonDown(1);
                final Enum next = Platform.rotateEnum(cv, backwards, iBtn.getSetting().getPossibleValues());

                if (btn == this.terminalStyleBox) {
                    AEConfig.instance.settings.putSetting(iBtn.getSetting(), next);

                    this.reinitialize();
                }

                iBtn.set(next);
            }
        }
    }

    private void reinitialize() {
        this.buttonList.clear();
        this.initGui();
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/newinterfaceterminal.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, 53);

        int offset = 51;
        final int ex = this.getScrollBar().getCurrentScroll();

        for (int x = 0; x < this.rows; x++) {
            this.drawTexturedModalRect(offsetX, offsetY + 53 + x * 18, 0, 52, this.xSize, 18);
        }

        for (int x = 0; x < this.rows && ex + x < this.lines.size(); x++) {

            final Object lineObj = this.lines.get(ex + x);
            if (lineObj instanceof ClientDCInternalInv inv) {

                GL11.glColor4f(1, 1, 1, 1);
                final int width = inv.getInventory().getSizeInventory() * 18;
                this.drawTexturedModalRect(offsetX + 20, offsetY + offset, 20, 173, width, 18);
            }

            offset += 18;
        }

        this.drawTexturedModalRect(offsetX, offsetY + 50 + this.rows * 18, 0, 158, this.xSize, 99);

        searchFieldInputs.drawTextBox();
        searchFieldOutputs.drawTextBox();
        searchFieldNames.drawTextBox();
    }

    @Override
    protected void keyTyped(final char character, final int key) {
        if (!checkHotbarKeys(key)) {
            if (character == ' ') {
                if ((searchFieldInputs.getText().isEmpty() && searchFieldInputs.isFocused())
                        || (searchFieldOutputs.getText().isEmpty() && searchFieldOutputs.isFocused())
                        || (searchFieldNames.getText().isEmpty() && searchFieldNames.isFocused()))
                    return;
            } else if (character == '\t') {
                if (handleTab()) return;
            }
            if (searchFieldInputs.textboxKeyTyped(character, key) || searchFieldOutputs.textboxKeyTyped(character, key)
                    || searchFieldNames.textboxKeyTyped(character, key)) {
                refreshList();
            } else {
                super.keyTyped(character, key);
            }
        }
    }

    private boolean handleTab() {
        if (searchFieldInputs.isFocused()) {
            searchFieldInputs.setFocused(false);
            if (isShiftKeyDown()) searchFieldNames.setFocused(true);
            else searchFieldOutputs.setFocused(true);
            return true;
        } else if (searchFieldOutputs.isFocused()) {
            searchFieldOutputs.setFocused(false);
            if (isShiftKeyDown()) searchFieldInputs.setFocused(true);
            else searchFieldNames.setFocused(true);
            return true;
        } else if (searchFieldNames.isFocused()) {
            searchFieldNames.setFocused(false);
            if (isShiftKeyDown()) searchFieldOutputs.setFocused(true);
            else searchFieldInputs.setFocused(true);
            return true;
        }
        return false;
    }

    public void postUpdate(final NBTTagCompound in) {
        if (in.getBoolean("clear")) {
            this.byId.clear();
            this.refreshList = true;
        }

        for (final Object oKey : in.func_150296_c()) {
            final String key = (String) oKey;
            if (key.startsWith("=")) {
                try {
                    final long id = Long.parseLong(key.substring(1), Character.MAX_RADIX);
                    final NBTTagCompound invData = in.getCompoundTag(key);
                    int size = invData.getInteger("size");
                    final ClientDCInternalInv current = this
                            .getById(id, invData.getLong("sortBy"), invData.getString("un"), size);
                    int X = invData.getInteger("x");
                    int Y = invData.getInteger("y");
                    int Z = invData.getInteger("z");
                    int dim = invData.getInteger("dim");
                    blockPosHashMap.put(current, new DimensionalCoord(X, Y, Z, dim));

                    for (int x = 0; x < current.getInventory().getSizeInventory(); x++) {
                        final String which = Integer.toString(x);
                        if (invData.hasKey(which)) {
                            current.getInventory().setInventorySlotContents(
                                    x,
                                    ItemStack.loadItemStackFromNBT(invData.getCompoundTag(which)));
                        }
                    }
                } catch (final NumberFormatException ignored) {}
            }
        }

        if (this.refreshList) {
            this.refreshList = false;
            // invalid caches on refresh
            this.cachedSearches.clear();
            this.refreshList();
        }
    }

    /**
     * Rebuilds the list of interfaces.
     * <p>
     * Respects a search term if present (ignores case) and adding only matching patterns.
     */
    private void refreshList() {
        this.byName.clear();
        this.buttonList.clear();
        this.matchedStacks.clear();

        final String searchFieldInputs = this.searchFieldInputs.getText().toLowerCase();
        final String searchFieldOutputs = this.searchFieldOutputs.getText().toLowerCase();
        final String searchFieldNames = this.searchFieldNames.getText().toLowerCase();

        final Set<Object> cachedSearch = this.getCacheForSearchTerm(
                "IN:" + searchFieldInputs
                        + "OUT:"
                        + searchFieldOutputs
                        + "NAME:"
                        + searchFieldNames
                        + AEConfig.instance.showOnlyInterfacesWithFreeSlotsInInterfaceTerminal
                        + onlyMolecularAssemblers
                        + onlyBrokenRecipes);
        final boolean rebuild = cachedSearch.isEmpty();

        for (final ClientDCInternalInv entry : this.byId.values()) {
            // ignore inventory if not doing a full rebuild and cache already marks it as miss.
            if (!rebuild && !cachedSearch.contains(entry)) {
                continue;
            }

            // Shortcut to skip any filter if search term is ""/empty
            boolean found = searchFieldInputs.isEmpty() && searchFieldOutputs.isEmpty();
            boolean interfaceHasFreeSlots = false;
            boolean interfaceHasBrokenRecipes = false;

            // Search if the current inventory holds a pattern containing the search term.
            if (!found || AEConfig.instance.showOnlyInterfacesWithFreeSlotsInInterfaceTerminal || onlyBrokenRecipes) {
                for (final ItemStack itemStack : entry.getInventory()) {
                    // If only Interfaces with empty slots should be shown, check that here
                    if (itemStack == null) {
                        interfaceHasFreeSlots = true;
                        continue;
                    }

                    if (onlyBrokenRecipes && recipeIsBroken(itemStack)) {
                        interfaceHasBrokenRecipes = true;
                    }

                    if ((!searchFieldInputs.isEmpty() && itemStackMatchesSearchTerm(itemStack, searchFieldInputs, 0))
                            || (!searchFieldOutputs.isEmpty()
                                    && itemStackMatchesSearchTerm(itemStack, searchFieldOutputs, 1))) {
                        found = true;
                        matchedStacks.add(itemStack);
                    }
                }
            }

            if ((found && entry.getName().toLowerCase().contains(searchFieldNames))
                    && (!onlyMolecularAssemblers || entry.getUnlocalizedName().contains(MOLECULAR_ASSEMBLER))
                    && (!AEConfig.instance.showOnlyInterfacesWithFreeSlotsInInterfaceTerminal || interfaceHasFreeSlots)
                    && (!onlyBrokenRecipes || interfaceHasBrokenRecipes)) {
                this.byName.put(entry.getName(), entry);
                cachedSearch.add(entry);
            } else {
                cachedSearch.remove(entry);
            }
        }

        this.names.clear();
        this.names.addAll(this.byName.keySet());

        Collections.sort(this.names);

        this.lines.clear();
        this.lines.ensureCapacity(this.names.size() + this.byId.size());

        for (final String n : this.names) {
            this.lines.add(n);
            final ArrayList<ClientDCInternalInv> clientInventories = new ArrayList<>(this.byName.get(n));
            Collections.sort(clientInventories);
            this.lines.addAll(clientInventories);
        }

        this.setScrollBar();
    }

    private boolean itemStackMatchesSearchTerm(final ItemStack itemStack, final String searchTerm, int pass) {
        if (itemStack == null) {
            return false;
        }

        final NBTTagCompound encodedValue = itemStack.getTagCompound();

        if (encodedValue == null) {
            return false;
        }

        final NBTTagList tags = encodedValue.getTagList(pass == 0 ? "in" : "out", 10);
        final boolean containsInvalidDisplayName = GuiText.UnknownItem.getLocal().toLowerCase().contains(searchTerm);

        for (int i = 0; i < tags.tagCount(); i++) {
            final NBTTagCompound tag = tags.getCompoundTagAt(i);
            final ItemStack parsedItemStack = ItemStack.loadItemStackFromNBT(tag);

            if (parsedItemStack != null) {
                final String displayName = Platform
                        .getItemDisplayName(AEApi.instance().storage().createItemStack(parsedItemStack)).toLowerCase();
                if (displayName.contains(searchTerm)) {
                    return true;
                }
            } else if (containsInvalidDisplayName && !tag.hasNoTags()) {
                return true;
            }
        }

        return false;
    }

    private boolean recipeIsBroken(final ItemStack itemStack) {

        if (itemStack == null) {
            return false;
        }

        final NBTTagCompound encodedValue = itemStack.getTagCompound();
        if (encodedValue == null) {
            return true;
        }

        final World w = CommonHelper.proxy.getWorld();
        if (w == null) {
            return false;
        }

        try {
            new PatternHelper(itemStack, w);
            return false;
        } catch (final Throwable t) {
            return true;
        }
    }

    /**
     * Tries to retrieve a cache for a with search term as keyword.
     * <p>
     * If this cache should be empty, it will populate it with an earlier cache if available or at least the cache for
     * the empty string.
     *
     * @param searchTerm the corresponding search
     * @return a Set matching a superset of the search term
     */
    private Set<Object> getCacheForSearchTerm(final String searchTerm) {
        if (!this.cachedSearches.containsKey(searchTerm)) {
            this.cachedSearches.put(searchTerm, new HashSet<>());
        }

        final Set<Object> cache = this.cachedSearches.get(searchTerm);

        if (cache.isEmpty() && searchTerm.length() > 1) {
            cache.addAll(this.getCacheForSearchTerm(searchTerm.substring(0, searchTerm.length() - 1)));
            return cache;
        }

        return cache;
    }

    private int getMaxRows() {
        return AEConfig.instance.getConfigManager().getSetting(Settings.TERMINAL_STYLE) == TerminalStyle.SMALL
                ? AEConfig.instance.InterfaceTerminalSmallSize
                : Integer.MAX_VALUE;
    }

    private ClientDCInternalInv getById(final long id, final long sortBy, final String unlocalizedName,
            final int sizeInit) {
        ClientDCInternalInv o = this.byId.get(id);

        if (o == null) {
            this.byId.put(id, o = new ClientDCInternalInv(sizeInit, id, sortBy, unlocalizedName));
            this.refreshList = true;
        }

        return o;
    }

    public boolean isOverTextField(final int mousex, final int mousey) {
        return searchFieldInputs.isMouseIn(mousex, mousey) || searchFieldOutputs.isMouseIn(mousex, mousey)
                || searchFieldNames.isMouseIn(mousex, mousey);
    }

    public void setTextFieldValue(final String displayName, final int mousex, final int mousey, final ItemStack stack) {

        if (searchFieldInputs.isMouseIn(mousex, mousey)) {
            searchFieldInputs.setText(displayName);
        } else if (searchFieldOutputs.isMouseIn(mousex, mousey)) {
            searchFieldOutputs.setText(displayName);
        } else if (searchFieldNames.isMouseIn(mousex, mousey)) {
            searchFieldNames.setText(displayName);
        }
    }
}
