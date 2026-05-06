package appeng.client.gui.implementations;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.util.AEColor;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiColorButton;
import appeng.container.implementations.ContainerWirelessNetworkManager;

public class GuiWirelessNetworkManager extends AEBaseGui {

    private final GuiColorButton[] colorButtons = new GuiColorButton[16];
    private final ContainerWirelessNetworkManager containerWirelessNetworkManager;

    public GuiWirelessNetworkManager(final InventoryPlayer inventoryPlayer, final IGuiItemObject item) {
        super(new ContainerWirelessNetworkManager(inventoryPlayer, item));
        this.containerWirelessNetworkManager = (ContainerWirelessNetworkManager) inventorySlots;
        this.xSize = 82;
        this.ySize = 82;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.addButtons();
    }

    private void addButtons() {
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                final int index = x + y * 4;
                this.colorButtons[index] = new GuiColorButton(
                        0,
                        this.guiLeft + 6 + (18 * x),
                        this.guiTop + 6 + (18 * y),
                        16,
                        16,
                        AEColor.values()[index],
                        AEColor.values()[index].name());
                this.buttonList.add(this.colorButtons[index]);
            }
        }
    }

    private void buttonsVisibility() {
        final boolean[] keysStatus = this.containerWirelessNetworkManager.getKeys();
        for (int i = 0; i < 16; i++) this.colorButtons[i].visible = keysStatus[i];

    }

    @Override
    protected void actionPerformed(GuiButton button) {
        for (int i = 0; i < 16; i++) {
            if (button == this.colorButtons[i]) {
                this.containerWirelessNetworkManager.color.set(isShiftKeyDown() ? i + 100 : i);
                this.containerWirelessNetworkManager.tickClientSync();
            }
        }
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.buttonsVisibility();
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.bindTexture("guis/wireless_network_manager.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }
}
