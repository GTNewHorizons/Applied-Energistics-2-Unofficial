package appeng.client.gui.implementations;

import java.util.ArrayList;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.util.AEColor;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiColorButton;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.implementations.ContainerWirelessNetworkManager;
import appeng.core.localization.GuiText;
import appeng.server.ServerHelper;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class GuiWirelessNetworkManager extends AEBaseGui {

    private final ArrayList<GuiColorButton> colorButtons = new ArrayList<>();
    private final ContainerWirelessNetworkManager containerWirelessNetworkManager;
    private final MEGuiTextField renameField = new MEGuiTextField(96, 12);
    private GuiColorButton buttonOnRename = null;
    boolean needReInitialize = false;

    public GuiWirelessNetworkManager(final InventoryPlayer inventoryPlayer, final IGuiItemObject item) {
        super(new ContainerWirelessNetworkManager(inventoryPlayer, item));
        this.containerWirelessNetworkManager = (ContainerWirelessNetworkManager) inventorySlots;
        this.xSize = 106;
        this.ySize = 232;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.addButtons();
        this.renameField.setFocused(false);
        this.renameField.setVisible(false);
        this.renameField.setMaxStringLength(96);
    }

    private void addButtons() {
        final Int2ObjectOpenHashMap<Pair<Boolean, String>> keysStatus = this.containerWirelessNetworkManager.getKeys();
        for (int y = 0; y < 16; y++) {
            final Pair<Boolean, String> keyStatus = keysStatus.getOrDefault(y, Pair.of(false, ""));
            final GuiColorButton btn = new GuiColorButton(
                    y,
                    this.guiLeft + 5,
                    this.guiTop + 5 + (14 * y),
                    96,
                    12,
                    AEColor.values()[y],
                    keyStatus.value(),
                    GuiText.WirelessManagerToolTips.getLocal());
            btn.visible = keyStatus.key();
            this.colorButtons.add(btn);
            this.buttonList.add(btn);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        for (final GuiColorButton btn : this.colorButtons) {
            if (button == btn) {
                if (this.isExtraAction()) {
                    this.renameField.x = btn.xPosition - this.guiLeft;
                    this.renameField.y = btn.yPosition - this.guiTop;
                    this.renameField.setText(btn.displayString);
                    this.renameField.setVisible(true);
                    this.renameField.setFocused(true);
                    this.renameField.setCursorPositionEnd();
                    buttonOnRename = btn;
                    btn.drawDisplayString = false;
                } else if (isShiftKeyDown()) {
                    this.containerWirelessNetworkManager.removeNetwork((byte) btn.id);
                    this.needReInitialize = true;
                } else {
                    this.containerWirelessNetworkManager.switch2Action((byte) btn.id);
                    this.needReInitialize = true;
                }
                this.flushPendingSync();
            }
        }
    }

    public boolean isExtraAction() {
        int keyCode = ServerHelper.WIRELESS_EXTRA_ACTION.getKeyCode();
        if (keyCode < 0) {
            // In vanilla code, mouse buttons are registered as keyCodes with their values offset by -100.
            return Mouse.isButtonDown(keyCode + 100);
        } else {
            return Keyboard.isKeyDown(keyCode);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if ((keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) && this.renameField.isFocused()) {
            final String newName = this.renameField.getText();
            this.containerWirelessNetworkManager.renameNetwork(this.buttonOnRename.getColor(), newName);
            this.needReInitialize = true;
        } else if (this.renameField.textboxKeyTyped(typedChar, keyCode)) {} else super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.renameField.drawTextBox();

        if (this.needReInitialize) {
            this.needReInitialize = false;
            this.reinitalize();
        }
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.bindTexture("guis/wireless_network_manager.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }
}
