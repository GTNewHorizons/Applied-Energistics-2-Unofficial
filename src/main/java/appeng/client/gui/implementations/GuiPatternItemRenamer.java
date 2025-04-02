package appeng.client.gui.implementations;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import appeng.api.storage.ITerminalHost;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.IDropToFillTextField;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerPatternItemRenamer;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketPatternItemRenamer;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.parts.reporting.PartPatternTerminalEx;

public class GuiPatternItemRenamer extends AEBaseGui implements IDropToFillTextField {

    private final MEGuiTextField textField;
    private final String oldName;
    private final int valueIndex;
    private GuiBridge originalGui;

    public GuiPatternItemRenamer(InventoryPlayer ip, ITerminalHost p) {
        super(new ContainerPatternItemRenamer(ip, p));
        GuiContainer gui = (GuiContainer) Minecraft.getMinecraft().currentScreen;
        if (gui != null && gui.theSlot != null && gui.theSlot.getHasStack()) {
            Slot slot = gui.theSlot;
            oldName = slot.getStack().getDisplayName();
            valueIndex = slot.slotNumber;
        } else {
            valueIndex = -1;
            oldName = "";
        }
        xSize = 256;

        textField = new MEGuiTextField(231, 12);
    }

    @Override
    public void initGui() {
        super.initGui();

        textField.x = guiLeft + 12;
        textField.y = guiTop + 35;
        textField.setFocused(true);
        textField.setText(oldName);
        textField.setCursorPositionEnd();
        textField.setSelectionPos(0);
        setOriginGUI(((AEBaseContainer) inventorySlots).getTarget());
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

    protected void setOriginGUI(Object target) {
        if (target instanceof PartPatternTerminal) {
            originalGui = GuiBridge.GUI_PATTERN_TERMINAL;
        } else if (target instanceof PartPatternTerminalEx) {
            originalGui = GuiBridge.GUI_PATTERN_TERMINAL_EX;
        }
    }

    @Override
    protected void keyTyped(final char character, final int key) {
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            NetworkHandler.instance
                    .sendToServer(new PacketPatternItemRenamer(originalGui.ordinal(), textField.getText(), valueIndex));
        } else if (!textField.textboxKeyTyped(character, key)) {
            super.keyTyped(character, key);
        }
    }

    public boolean isOverTextField(final int mousex, final int mousey) {
        return textField.isMouseIn(mousex, mousey);
    }

    public void setTextFieldValue(final String displayName, final int mousex, final int mousey, final ItemStack stack) {
        textField.setText(displayName);
    }
}
