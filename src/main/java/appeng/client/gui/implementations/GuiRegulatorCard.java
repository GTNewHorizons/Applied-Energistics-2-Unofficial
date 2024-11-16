package appeng.client.gui.implementations;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.IDropToFillTextField;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerOreFilter;
import appeng.container.implementations.ContainerRegulatorCard;
import appeng.core.AELog;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.IOreFilterable;
import appeng.helpers.IRegulatorCard;
import appeng.integration.modules.NEI;
import appeng.parts.automation.PartSharedItemBus;
import appeng.parts.misc.PartStorageBus;
import appeng.tile.misc.TileCellWorkbench;
import appeng.util.prioitylist.OreFilteredList.OreFilterTextFormatter;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;

public class GuiRegulatorCard extends AEBaseGui {

    private MEGuiTextField amountField;
    private MEGuiTextField ticksField;

    public GuiRegulatorCard(InventoryPlayer ip, IRegulatorCard obj) {
        super(new ContainerRegulatorCard(ip, obj));
        this.xSize = 256;

        this.amountField = new MEGuiTextField(75, 12) {

            @Override
            public void onTextChange(final String oldText) {
                final String amountText = getAmountText();
                if (!amountText.equals(oldText)) {
                    ((ContainerRegulatorCard) inventorySlots).setRegulatorSettings(filterRegulatorSettings(amountText, getTicksText()));
                }
            }
        };

        this.ticksField = new MEGuiTextField(40, 12) {

            @Override
            public void onTextChange(final String oldText) {;
                final String ticksText = getTicksText();
                if (!ticksText.equals(oldText)) {
                    ((ContainerRegulatorCard) inventorySlots).setRegulatorSettings(filterRegulatorSettings(getAmountText() ,ticksText));
                }
            }
        };
    }

    @Override
    public void initGui() {
        super.initGui();

        this.amountField.x = this.guiLeft + 64;
        this.amountField.y = this.guiTop + 32;
        this.ticksField.x = this.guiLeft + 152;
        this.ticksField.y = this.guiTop + 32;
        ((ContainerRegulatorCard) this.inventorySlots).setAmountField(this.amountField);
        ((ContainerRegulatorCard) this.inventorySlots).setTicksField(this.ticksField);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
    }

    public String getAmountText() {
        return this.amountField.getText();
    }

    public String getTicksText() {
        return this.ticksField.getText();
    }

    public String filterRegulatorSettings(final String amount, final String ticks) {
        String s = "1000:20";
        if (amount != "" && ticks != "") {
            try {
                Integer.parseInt(amount);
                s = amount + ":";
            } catch (Exception ignored) {
                s = Integer.MAX_VALUE + ":";
            }
            try {
                final int m = Integer.parseInt(ticks);
                if (m > 72000) {
                    s += "72000";
                } else {
                    s += ticks;
                }
            } catch (Exception ignored) {
                s += "72000";
            }
        } else if (amount == "" && ticks != "") {
            s = "1000:" + ticks;
        } else if (ticks == ""){
            s = amount + ":20";
        }
        return s;
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRendererObj.drawString(GuiText.RegulatorCardLabel.getLocal(), 58, 6, GuiColors.RegulatorCardLabel.getColor());
        this.fontRendererObj.drawString(GuiText.RegulatorCardAmount.getLocal(), 64, 23, GuiColors.RegulatorCardAmount.getColor());
        this.fontRendererObj.drawString(GuiText.RegulatorCardTicks.getLocal(), 152, 23, GuiColors.RegulatorCardTicks.getColor());
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.bindTexture("guis/regulator.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
        this.amountField.drawTextBox();
        this.ticksField.drawTextBox();
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) {
        this.amountField.mouseClicked(xCoord, yCoord, btn);
        this.ticksField.mouseClicked(xCoord, yCoord, btn);
        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void keyTyped(final char character, final int key) {
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            try {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("RegulatorSettings", filterRegulatorSettings(this.amountField.getText(), this.ticksField.getText())));
            } catch (IOException e) {
                AELog.debug(e);
            }
            final Object target = ((AEBaseContainer) this.inventorySlots).getTarget();
            GuiBridge OriginalGui = null;
            if (target instanceof PartStorageBus) OriginalGui = GuiBridge.GUI_STORAGEBUS;
            else if (target instanceof PartSharedItemBus) OriginalGui = GuiBridge.GUI_BUS;

            if (OriginalGui != null) NetworkHandler.instance.sendToServer(new PacketSwitchGuis(OriginalGui));
            else this.mc.thePlayer.closeScreen();

        } else if (!(this.amountField.textboxKeyTyped(character, key) || this.ticksField.textboxKeyTyped(character, key))) {
            super.keyTyped(character, key);
        }
    }
}
