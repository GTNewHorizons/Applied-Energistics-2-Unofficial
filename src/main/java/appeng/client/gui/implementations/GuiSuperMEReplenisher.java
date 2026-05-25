package appeng.client.gui.implementations;

import static appeng.util.Platform.fmt;

import net.minecraft.entity.player.InventoryPlayer;

import org.lwjgl.input.Keyboard;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.client.gui.slots.VirtualMEPhantomSlotPrecise;
import appeng.client.gui.slots.VirtualMESlot;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.implementations.ContainerSuperMEReplenisher;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.tile.misc.TileSuperMEReplenisher;

public class GuiSuperMEReplenisher extends AEBaseGui {

    private final ContainerSuperMEReplenisher containerSuperMEReplenisher;
    private final MEGuiTextField tickRateField;
    private final MEGuiTextField thresholdField;

    public GuiSuperMEReplenisher(final InventoryPlayer inventoryPlayer, final TileSuperMEReplenisher te) {
        super(new ContainerSuperMEReplenisher(inventoryPlayer, te));
        this.containerSuperMEReplenisher = (ContainerSuperMEReplenisher) inventorySlots;
        this.ySize = 246;
        this.xSize = 220;

        this.thresholdField = new MEGuiTextField(28, 12);
        this.thresholdField.setMaxStringLength(3);
        this.tickRateField = new MEGuiTextField(34, 12);
        this.tickRateField.setMaxStringLength(4);
    }

    @Override
    public void initGui() {
        super.initGui();
        this.initVirtualSlots();

        this.tickRateField.x = this.guiLeft + 29;
        this.thresholdField.x = this.guiLeft + 101;

        this.tickRateField.y = this.guiTop + 133;
        this.thresholdField.y = this.tickRateField.y;
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        if (this.containerSuperMEReplenisher.needUpdate) {
            this.containerSuperMEReplenisher.needUpdate = false;

            this.tickRateField.setText(String.valueOf(this.containerSuperMEReplenisher.tickRate.get()));
            this.thresholdField.setText(String.valueOf(this.containerSuperMEReplenisher.threshold.get()));
        }

        final VirtualMESlot slot = this.getVirtualMESlotUnderMouse();
        if (slot != null) {
            final IAEStack<?> config = slot.getAEStack();
            if (config != null) {
                final IAEStackType<?> type = config.getStackType();
                final IAEStack<?> stored = this.containerSuperMEReplenisher.storedData.get().lits.get(type)
                        .findPrecise(config);
                final long storedSize = stored == null ? 0 : stored.getStackSize();

                this.fontRendererObj.drawString(
                        GuiText.SuperMEReplenisherTarget.getLocal(fmt(config.getStackSize())),
                        29,
                        65,
                        GuiColors.SuperMEReplenisherStatus.getColor());

                this.fontRendererObj.drawString(
                        GuiText.SuperMEReplenisherStored.getLocal(fmt(storedSize)),
                        29,
                        75,
                        GuiColors.SuperMEReplenisherStatus.getColor());

                this.fontRendererObj.drawString(
                        GuiText.SuperMEReplenisherBytesUsed
                                .getLocal(fmt((long) Math.ceil((double) storedSize / type.getAmountPerByte()))),
                        29,
                        85,
                        GuiColors.SuperMEReplenisherStatus.getColor());
            }
        }

        // Status

        final long totalBytes = this.containerSuperMEReplenisher.getTotalBytes();
        final boolean unlimited = totalBytes >= Long.MAX_VALUE / 16;
        this.fontRendererObj.drawString(
                GuiText.SuperMEReplenisherBytesTotal
                        .getLocal(unlimited ? GuiText.SuperMEReplenisherBytesUnlimited.getLocal() : fmt(totalBytes)),
                29,
                104,
                GuiColors.DefaultBlack.getColor());
        this.fontRendererObj.drawString(
                GuiText.SuperMEReplenisherBytesUsed.getLocal(fmt(this.containerSuperMEReplenisher.getUsedBytes())),
                29,
                114,
                GuiColors.DefaultBlack.getColor());

        // Settings

        this.fontRendererObj.drawString(
                GuiText.SuperMEReplenisherTickRate.getLocal(),
                29,
                124,
                GuiColors.SuperMEReplenisherStatus.getColor());

        this.fontRendererObj.drawString(
                GuiText.SuperMEReplenisherThreshold.getLocal(),
                100,
                124,
                GuiColors.SuperMEReplenisherStatus.getColor());

        this.fontRendererObj.drawString("%", 100 + 31, 136, GuiColors.SuperMEReplenisherStatus.getColor());

        this.fontRendererObj.drawString(
                GuiText.inventory.getLocal(),
                29,
                this.ySize - 99,
                GuiColors.SuperMEReplenisherInventory.getColor());
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.bindTexture("guis/superMEReplenisher.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
        this.tickRateField.drawTextBox();
        this.thresholdField.drawTextBox();
    }

    private void initVirtualSlots() {
        final int xo = 30;
        final int yo = -154;

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 9; x++) {
                this.registerVirtualSlots(
                        new VirtualMEPhantomSlotPrecise(
                                xo + x * 18,
                                yo + y * 18 + 9 * 18,
                                this.containerSuperMEReplenisher.config,
                                x + y * 9,
                                this::acceptType));
            }
        }
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) {
        this.tickRateField.mouseClicked(xCoord, yCoord, btn);
        this.thresholdField.mouseClicked(xCoord, yCoord, btn);
        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void keyTyped(final char character, final int key) {
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            this.containerSuperMEReplenisher.tickRate.set(this.getInt(this.tickRateField, 10_000));
            this.containerSuperMEReplenisher.threshold.set(this.getInt(this.thresholdField, 100));
            this.tickRateField.setFocused(false);
            this.thresholdField.setFocused(false);
        } else if (!(this.tickRateField.textboxKeyTyped(character, key)
                || this.thresholdField.textboxKeyTyped(character, key)))
            super.keyTyped(character, key);
    }

    private int getInt(final MEGuiTextField field, final int maxValue) {
        try {
            return Math.min(Math.max(1, Integer.parseInt(field.getText())), maxValue);
        } catch (Exception ignored) {
            return 1;
        }
    }

    private boolean acceptType(VirtualMEPhantomSlot slot, IAEStackType<?> type, int mouseButton) {
        return true;
    }
}
