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

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import appeng.api.config.ActionItems;
import appeng.api.config.CraftingMode;
import appeng.api.config.Settings;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.slots.VirtualMESlotSingle;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.implementations.ContainerCraftAmount;
import appeng.container.interfaces.IVirtualSlotHolder;
import appeng.core.AEConfig;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCraftRequest;
import appeng.helpers.Reflected;
import appeng.util.Platform;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

public class GuiCraftAmount extends GuiAmount implements IVirtualSlotHolder {

    private static final int[] BUTTON_X_OFFSETS = { 20, 48, 82, 120 };
    private static final int[] BUTTON_WIDTHS = { 22, 28, 32, 38 };
    private static final int[] DEFAULT_CONTROL_VALUES = { 1, 10, 100, 1000 };
    private static final int CONTROL_BAR_U = 60;
    private static final int CONTROL_BAR_V = 55;
    private static final int CONTROL_BAR_W = 61;
    private static final int CONTROL_BAR_H = 12;
    private static final int TOP_FIELDS_Y_OFFSET = 30;

    private GuiImgButton craftingMode;
    private GuiImgButton controlButtonValues;
    private final MEGuiTextField[] controlValueFields = new MEGuiTextField[4];
    private boolean isControlButtonPressed;
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
        this.buttonList.add(
                this.controlButtonValues = new GuiImgButton(
                        this.guiLeft - 18,
                        this.guiTop + 84,
                        Settings.ACTIONS,
                        ActionItems.CONTROL_BUTTON_VALUES_OFF) {

                    @Override
                    public void drawButton(final net.minecraft.client.Minecraft mc, final int mouseX,
                            final int mouseY) {
                        super.drawButton(mc, mouseX, mouseY);
                        if (this.visible && GuiCraftAmount.this.isControlButtonPressed) {
                            drawRect(
                                    this.xPosition,
                                    this.yPosition,
                                    this.xPosition + this.getWidth(),
                                    this.yPosition + this.getHeight(),
                                    GuiColors.ColorSelectBtnOverlayDisabled.getColor());
                        }
                    }
                });
        this.initializeControlValueFields();
        this.setControlButtonPressed(false);

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
        if (this.isControlButtonPressed) {
            this.drawControlValueFieldBackgrounds();
            for (final MEGuiTextField field : this.controlValueFields) {
                field.drawTextBox();
            }
        }
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        if (btn == this.controlButtonValues) {
            this.toggleControlButtonPressed();
            return;
        }

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
    protected void keyTyped(final char character, final int key) {
        if (this.isControlButtonPressed && this.handleControlValueFieldInput(character, key)) {
            return;
        }
        super.keyTyped(character, key);
    }

    @Override
    protected void mouseClicked(int xCoord, int yCoord, int btn) {
        super.mouseClicked(xCoord, yCoord, btn);

        if (this.isControlButtonPressed) {
            for (final MEGuiTextField field : this.controlValueFields) {
                field.mouseClicked(xCoord, yCoord, btn);
            }
        }
    }

    private void initializeControlValueFields() {
        for (int i = 0; i < this.controlValueFields.length; i++) {
            final MEGuiTextField field = this.createControlValueField(i);
            field.x = this.guiLeft + BUTTON_X_OFFSETS[i];
            field.y = this.guiTop + TOP_FIELDS_Y_OFFSET;
            final String initial = Integer.toString(this.getButtonQtyByIndex(i));
            field.setText(initial, true);
            this.controlValueFields[i] = field;
        }
    }

    private void toggleControlButtonPressed() {
        if (this.isControlButtonPressed) {
            this.saveControlValueFields();
            this.refreshAmountButtons();
            this.syncControlValueFieldsFromConfig();
        }

        this.setControlButtonPressed(!this.isControlButtonPressed);
    }

    private void setControlButtonPressed(final boolean pressed) {
        this.isControlButtonPressed = pressed;
        this.controlButtonValues
                .set(pressed ? ActionItems.CONTROL_BUTTON_VALUES_ON : ActionItems.CONTROL_BUTTON_VALUES_OFF);
        if (pressed) {
            this.amountTextField.setFocused(false);
        } else {
            this.amountTextField.setFocused(true);
        }

        this.setAmountButtonsVisible(!pressed);

        for (int i = 0; i < this.controlValueFields.length; i++) {
            this.controlValueFields[i].setFocused(pressed && i == 0);
        }
    }

    private void setAmountButtonsVisible(final boolean visible) {
        this.plus1.visible = visible;
        this.plus1.enabled = visible;
        this.plus10.visible = visible;
        this.plus10.enabled = visible;
        this.plus100.visible = visible;
        this.plus100.enabled = visible;
        this.plus1000.visible = visible;
        this.plus1000.enabled = visible;
        this.minus1.visible = true;
        this.minus1.enabled = true;
        this.minus10.visible = true;
        this.minus10.enabled = true;
        this.minus100.visible = true;
        this.minus100.enabled = true;
        this.minus1000.visible = true;
        this.minus1000.enabled = true;
    }

    private boolean handleControlValueFieldInput(final char character, final int key) {
        if (key == Keyboard.KEY_TAB) {
            this.focusNextControlField();
            return true;
        }

        for (final MEGuiTextField field : this.controlValueFields) {
            if (field.textboxKeyTyped(character, key)) {
                return true;
            }
        }

        return false;
    }

    private void focusNextControlField() {
        int focusedIndex = 0;
        for (int i = 0; i < this.controlValueFields.length; i++) {
            if (this.controlValueFields[i].isFocused()) {
                focusedIndex = i + 1;
                break;
            }
        }

        for (final MEGuiTextField field : this.controlValueFields) {
            field.setFocused(false);
        }

        this.controlValueFields[focusedIndex % this.controlValueFields.length].setFocused(true);
    }

    private void saveControlValueFields() {
        for (int i = 0; i < this.controlValueFields.length; i++) {
            final int fallback = DEFAULT_CONTROL_VALUES[i];
            final int parsed = this.parseControlValueField(this.controlValueFields[i], fallback);
            AEConfig.instance.setCraftItemsByStackAmount(i, parsed, false);
        }
        AEConfig.instance.save();
    }

    private int parseControlValueField(final MEGuiTextField field, final int fallback) {
        final String text = field.getText() == null ? "" : field.getText().trim();
        if (text.isEmpty()) {
            return fallback;
        }

        try {
            final long parsed = Long.parseLong(text);
            if (parsed < 1) {
                return fallback;
            }

            return parsed > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) parsed;
        } catch (final NumberFormatException ignored) {
            return fallback;
        }
    }

    private void syncControlValueFieldsFromConfig() {
        for (int i = 0; i < this.controlValueFields.length; i++) {
            final String value = Integer.toString(this.getButtonQtyByIndex(i));
            this.controlValueFields[i].setText(value, true);
        }
    }

    private void drawControlValueFieldBackgrounds() {
        this.bindTexture(getBackground());
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        for (final MEGuiTextField field : this.controlValueFields) {
            final int x = field.x;
            final int y = field.y;
            final int w = field.w;
            final int bodyWidth = Math.max(0, w - 1);
            if (bodyWidth > 0) {
                this.drawTexturedModalRect(x, y, CONTROL_BAR_U, CONTROL_BAR_V, bodyWidth, CONTROL_BAR_H);
            }
            this.drawTexturedModalRect(
                    x + w - 1,
                    y,
                    CONTROL_BAR_U + CONTROL_BAR_W - 1,
                    CONTROL_BAR_V,
                    1,
                    CONTROL_BAR_H);
        }
    }

    private MEGuiTextField createControlValueField(final int index) {
        final int maxDigits = index + 1;
        final MEGuiTextField field = new MEGuiTextField(BUTTON_WIDTHS[index], 12) {

            @Override
            public void drawTextBox() {
                if (field.getVisible()) {
                    setDimensionsAndColor();
                    drawRect(
                            this.x + 1,
                            this.y + 1,
                            this.x + this.w - 1,
                            this.y + this.h - 1,
                            isFocused() ? GuiColors.SearchboxFocused.getColor()
                                    : GuiColors.SearchboxUnfocused.getColor());
                    field.drawTextBox();
                }
            }

            @Override
            public void onTextChange(final String oldText) {
                final String value = sanitizeControlValue(getText(), maxDigits);
                if (!value.equals(getText())) {
                    setText(value, true);
                }
            }
        };
        field.setMaxStringLength(maxDigits);
        field.setUnfocusWithEnter(false);
        return field;
    }

    private String sanitizeControlValue(final String input, final int maxDigits) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        final String digitsOnly = input.replaceAll("\\D+", "");
        if (digitsOnly.length() <= maxDigits) {
            return digitsOnly;
        }

        return digitsOnly.substring(0, maxDigits);
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
