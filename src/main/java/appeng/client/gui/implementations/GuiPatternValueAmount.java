package appeng.client.gui.implementations;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import appeng.api.storage.ITerminalHost;
import appeng.client.gui.slots.VirtualMESlotSingle;
import appeng.container.implementations.ContainerPatternValueAmount;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketPatternValueSet;
import appeng.helpers.Reflected;

public class GuiPatternValueAmount extends GuiAmount {

    private final ContainerPatternValueAmount container;
    private final VirtualMESlotSingle slot;

    @Reflected
    public GuiPatternValueAmount(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(new ContainerPatternValueAmount(inventoryPlayer, te));
        this.container = (ContainerPatternValueAmount) this.inventorySlots;

        this.slot = new VirtualMESlotSingle(34, 53, 0, null);
    }

    @Override
    public void initGui() {
        super.initGui();
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
        this.nextBtn.displayString = GuiText.Set.getLocal();

        try {
            long resultI = getAmountLong();
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
            if (btn == this.nextBtn && btn.enabled && slot.getAEStack() != null) {
                NetworkHandler.instance.sendToServer(
                        new PacketPatternValueSet(
                                this.container.getAEStack().setStackSize(getAmountLong()),
                                this.container.getInvName(),
                                this.container.getSlotIndex()));
            }
        } catch (final NumberFormatException e) {
            // nope..
            this.amountTextField.setText("1");
        }
    }

    protected String getBackground() {
        return "guis/craftAmt.png";
    }

    public void update() {
        this.slot.setAEStack(this.container.getAEStack());
        this.amountTextField.setText(String.valueOf(this.container.getAEStack().getStackSize()));
        this.amountTextField.setCursorPositionEnd();
        this.amountTextField.setSelectionPos(0);
    }
}
