package appeng.client.gui.implementations;

import java.io.IOException;

import net.minecraft.entity.player.InventoryPlayer;

import org.lwjgl.input.Keyboard;

import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerRegulatorCard;
import appeng.core.AELog;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.IRegulatorCard;
import appeng.parts.automation.PartSharedItemBus;
import appeng.parts.misc.PartStorageBus;

public class GuiRegulatorCard extends AEBaseGui {

    private MEGuiTextField amountField;
    private MEGuiTextField ticksField;
    private MEGuiTextField stockModeLabel;
    private GuiImgButton stockModeButtonActive;
    private boolean stockMode;

    public GuiRegulatorCard(InventoryPlayer ip, IRegulatorCard obj) {
        super(new ContainerRegulatorCard(ip, obj));
        this.xSize = 256;

        this.amountField = new MEGuiTextField(75, 12);
        this.ticksField = new MEGuiTextField(40, 12);
        this.stockModeLabel = new MEGuiTextField(70, 12) {

            @Override
            public void onTextChange(final String oldText) {
                final String text = getText();
                if (text == "Active") {
                    stockMode = true;
                } else if (text == "Not Active") {
                    stockMode = false;
                }
            }
        };
        this.stockModeButtonActive = new GuiImgButton(
                this.guiLeft,
                this.guiTop,
                Settings.ACTIONS,
                ActionItems.REGULATOR_CARD_STOCK);

    }

    @Override
    public void initGui() {
        super.initGui();

        this.amountField.x = this.guiLeft + 64;
        this.amountField.y = this.guiTop + 32;

        this.ticksField.x = this.guiLeft + 152;
        this.ticksField.y = this.guiTop + 32;

        this.stockModeLabel.x = this.guiLeft + 84;
        this.stockModeLabel.y = this.guiTop + 48;

        this.stockModeButtonActive.xPosition = this.guiLeft + 64;
        this.stockModeButtonActive.yPosition = this.guiTop + 46;

        this.buttonList.add(this.stockModeButtonActive);
        ((ContainerRegulatorCard) this.inventorySlots).setAmountField(this.amountField);
        ((ContainerRegulatorCard) this.inventorySlots).setTicksField(this.ticksField);
        ((ContainerRegulatorCard) this.inventorySlots).setStockModeField(this.stockModeLabel);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
    }

    public String filterRegulatorSettings(final String amount, final String ticks) {
        String s = "1000:20";
        if (amount != "" && ticks != "") {
            try {
                if (Integer.parseInt(amount) == 0) {
                    s = "1:";
                } else {
                    s = amount + ":";
                }
            } catch (Exception ignored) {
                s = Integer.MAX_VALUE + ":";
            }
            try {
                final int m = Integer.parseInt(ticks);
                if (m > 72000) {
                    s += "72000";
                } else if (m == 0) {
                    s += "1";
                } else {
                    s += ticks;
                }
            } catch (Exception ignored) {
                s += "72000";
            }
        } else if (amount == "" && ticks != "") {
            s = "1000:" + ticks;
        } else if (ticks == "") {
            s = amount + ":20";
        }
        return s;
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRendererObj
                .drawString(GuiText.RegulatorCardLabel.getLocal(), 58, 6, GuiColors.RegulatorCardLabel.getColor());
        this.fontRendererObj
                .drawString(GuiText.RegulatorCardAmount.getLocal(), 64, 23, GuiColors.RegulatorCardAmount.getColor());
        this.fontRendererObj
                .drawString(GuiText.RegulatorCardTicks.getLocal(), 152, 23, GuiColors.RegulatorCardTicks.getColor());
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.bindTexture("guis/regulator.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
        this.amountField.drawTextBox();
        this.ticksField.drawTextBox();
        this.stockModeLabel.drawTextBox();
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) {
        this.amountField.mouseClicked(xCoord, yCoord, btn);
        this.ticksField.mouseClicked(xCoord, yCoord, btn);
        if (this.stockModeButtonActive.mousePressed(mc, xCoord, yCoord)) {
            if (!stockMode) {
                this.stockModeLabel.setText("Active");
            } else {
                this.stockModeLabel.setText("Not Active");
            }
        }
        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void keyTyped(final char character, final int key) {
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            try {
                NetworkHandler.instance.sendToServer(
                        new PacketValueConfig(
                                "RegulatorSettings",
                                filterRegulatorSettings(this.amountField.getText(), this.ticksField.getText()) + ":"
                                        + stockMode));
            } catch (IOException e) {
                AELog.debug(e);
            }
            final Object target = ((AEBaseContainer) this.inventorySlots).getTarget();
            GuiBridge OriginalGui = null;
            if (target instanceof PartStorageBus) OriginalGui = GuiBridge.GUI_STORAGEBUS;
            else if (target instanceof PartSharedItemBus) OriginalGui = GuiBridge.GUI_BUS;

            if (OriginalGui != null) NetworkHandler.instance.sendToServer(new PacketSwitchGuis(OriginalGui));
            else this.mc.thePlayer.closeScreen();

        } else if (!(this.amountField.textboxKeyTyped(character, key)
                || this.ticksField.textboxKeyTyped(character, key))) {
                    super.keyTyped(character, key);
                }
    }
}
