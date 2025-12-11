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

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import org.lwjgl.input.Mouse;

import appeng.api.config.CraftingMode;
import appeng.api.config.Settings;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.slots.VirtualMESlotSingle;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.container.implementations.ContainerCraftAmount;
import appeng.container.interfaces.IVirtualSlotHolder;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCraftRequest;
import appeng.helpers.Reflected;
import appeng.util.Platform;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

public class GuiCraftAmount extends GuiAmount implements IVirtualSlotHolder {

    private GuiImgButton craftingMode;
    private final VirtualMESlotSingle slot;

    @Reflected
    public GuiCraftAmount(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(new ContainerCraftAmount(inventoryPlayer, te));

        this.slot = new VirtualMESlotSingle(34, 53, 0, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        super.initGui();

        this.buttonList.add(
                this.craftingMode = new GuiImgButton(
                        this.guiLeft + 10,
                        this.guiTop + 53,
                        Settings.CRAFTING_MODE,
                        CraftingMode.STANDARD));

        ((ContainerCraftAmount) this.inventorySlots).setAmountField(this.amountTextField);
        this.registerVirtualSlots(this.slot);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRendererObj
                .drawString(GuiText.SelectAmount.getLocal(), 8, 6, GuiColors.CraftAmountSelectAmount.getColor());
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {

        super.drawBG(offsetX, offsetY, mouseX, mouseY);

        // Only display the word "Start" if either Ctrl OR Shift is held not both
        if (isShiftKeyDown() && !isCtrlKeyDown()) {
            this.nextBtn.displayString = GuiText.Start.getLocal();
        } else if (!isShiftKeyDown() && isCtrlKeyDown()) {
            this.nextBtn.displayString = GuiText.Start.getLocal();
        } else {
            this.nextBtn.displayString = GuiText.Next.getLocal();
        }

        try {

            int resultI = getAmount();

            this.nextBtn.enabled = resultI > 0;
        } catch (final NumberFormatException e) {
            this.nextBtn.enabled = false;
        }

        this.amountTextField.drawTextBox();
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        try {
            if (btn == this.craftingMode) {
                GuiImgButton iBtn = (GuiImgButton) btn;

                final Enum cv = iBtn.getCurrentValue();
                final boolean backwards = Mouse.isButtonDown(1);
                final Enum next = Platform.rotateEnum(cv, backwards, iBtn.getSetting().getPossibleValues());

                iBtn.set(next);
            }
            if (btn == this.nextBtn && btn.enabled) {
                NetworkHandler.instance.sendToServer(
                        new PacketCraftRequest(
                                getAmountLong(),
                                isShiftKeyDown(),
                                isCtrlKeyDown(),
                                (CraftingMode) this.craftingMode.getCurrentValue()));
            }
        } catch (final NumberFormatException e) {
            // nope..
            this.amountTextField.setText("1");
        }
    }

    @Override
    protected String getBackground() {
        return "guis/craftAmt.png";
    }

    @Override
    public void receiveSlotStacks(StorageName _invName, Int2ObjectMap<IAEStack<?>> slotStacks) {
        this.slot.setAEStack(slotStacks.get(0));
    }
}
