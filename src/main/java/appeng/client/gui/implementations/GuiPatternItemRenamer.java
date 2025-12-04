package appeng.client.gui.implementations;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.GuiSub;
import appeng.client.gui.widgets.IDropToFillTextField;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.implementations.ContainerPatternValueAmount;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketPatternValueSet;
import appeng.util.item.AEItemStack;

public class GuiPatternItemRenamer extends GuiSub implements IDropToFillTextField {

    private final ContainerPatternValueAmount container;
    private final MEGuiTextField textField;

    public GuiPatternItemRenamer(InventoryPlayer ip, ITerminalHost p) {
        super(new ContainerPatternValueAmount(ip, p));
        this.container = (ContainerPatternValueAmount) this.inventorySlots;
        xSize = 256;
        textField = new MEGuiTextField(231, 12);
    }

    @Override
    public void initGui() {
        super.initGui();

        textField.x = guiLeft + 12;
        textField.y = guiTop + 35;
        textField.setFocused(true);
    }

    public void update() {
        textField.setText(container.getAEStack().getDisplayName());
        textField.setCursorPositionEnd();
        textField.setSelectionPos(0);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        fontRendererObj.drawString(GuiText.Renamer.getLocal(), 12, 8, GuiColors.RenamerTitle.getColor());
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        bindTexture("guis/renamer.png");
        drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
        textField.drawTextBox();
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) {
        textField.mouseClicked(xCoord, yCoord, btn);
        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void keyTyped(final char character, final int key) {
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            NetworkHandler.instance.sendToServer(
                    new PacketPatternValueSet(
                            getNewNameStack(),
                            this.container.getInvName(),
                            this.container.getSlotIndex()));
        } else if (!textField.textboxKeyTyped(character, key)) {
            super.keyTyped(character, key);
        }
    }

    private IAEStack<?> getNewNameStack() {
        return AEItemStack.create(
                ((IAEItemStack) this.container.getAEStack()).getItemStack().setStackDisplayName(textField.getText()));
    }

    public boolean isOverTextField(final int mousex, final int mousey) {
        return textField.isMouseIn(mousex, mousey);
    }

    public void setTextFieldValue(final String displayName, final int mousex, final int mousey, final ItemStack stack) {
        textField.setText(displayName);
    }
}
