package appeng.client.gui.implementations;

import java.io.IOException;

import net.minecraft.entity.player.InventoryPlayer;

import org.lwjgl.input.Keyboard;

import appeng.client.gui.GuiSub;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.implementations.ContainerCellRestriction;
import appeng.container.implementations.ContainerCellRestriction.CellData;
import appeng.core.AELog;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.ICellRestriction;
import appeng.util.calculators.ArithHelper;
import appeng.util.calculators.Calculator;

public class GuiCellRestriction extends GuiSub {

    private MEGuiTextField amountField;
    private MEGuiTextField typesField;
    private CellData cellData;

    public GuiCellRestriction(InventoryPlayer ip, ICellRestriction obj) {
        super(new ContainerCellRestriction(ip, obj));
        this.xSize = 204;

        this.amountField = new MEGuiTextField(85, 12);
        this.typesField = new MEGuiTextField(30, 12);
        this.cellData = new CellData();

    }

    @Override
    public void initGui() {
        super.initGui();

        this.amountField.x = this.guiLeft + 64;
        this.amountField.y = this.guiTop + 32;

        this.typesField.x = this.guiLeft + 162;
        this.typesField.y = this.guiTop + 32;

        if (this.inventorySlots instanceof ContainerCellRestriction ccr) {
            ccr.setAmountField(this.amountField);
            ccr.setTypesField(this.typesField);
            ccr.setCellData(cellData);
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
    }

    public String filterCellRestriction() {

        String types = this.typesField.getText();
        long amount = getAmount();

        int restrictionTypes = 0;
        long restrictionAmount = 0;

        try {
            restrictionTypes = Math.min(Integer.parseInt(types), cellData.getTotalTypes());
        } catch (Exception ignored) {
            //
        }
        try {
            restrictionAmount = Math
                    .min(amount, (cellData.getTotalBytes() - cellData.getPerType()) * cellData.getPerByte());
        } catch (Exception ignored) {
            //
        }
        return restrictionTypes + "," + restrictionAmount;
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRendererObj.drawString(GuiText.CellRestriction.getLocal(), 58, 6, GuiColors.DefaultBlack.getColor());
        String type = cellData.getCellType();
        switch (type) {
            case "item":
                this.fontRendererObj
                        .drawString(GuiText.NumberOfItems.getLocal(), 64, 23, GuiColors.DefaultBlack.getColor());
                break;
            case "fluid":
                this.fontRendererObj
                        .drawString(GuiText.NumberOfFluids.getLocal(), 64, 23, GuiColors.DefaultBlack.getColor());
                break;
        }
        this.fontRendererObj.drawString(GuiText.Types.getLocal(), 162, 23, GuiColors.DefaultBlack.getColor());
        this.fontRendererObj
                .drawString(GuiText.CellRestrictionTips.getLocal(), 64, 50, GuiColors.DefaultBlack.getColor());
        switch (type) {
            case "item":
                this.fontRendererObj.drawString(
                        GuiText.ItemsPerByte.getLocal() + " " + cellData.getPerByte(),
                        64,
                        60,
                        GuiColors.DefaultBlack.getColor());
                break;
            case "fluid":
                this.fontRendererObj.drawString(
                        GuiText.FluidsPerByte.getLocal() + " " + cellData.getPerByte() + " mB",
                        64,
                        60,
                        GuiColors.DefaultBlack.getColor());
                break;
        }
        this.fontRendererObj.drawString(
                GuiText.BytesPerType.getLocal() + " " + cellData.getPerType(),
                64,
                70,
                GuiColors.DefaultBlack.getColor());
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.bindTexture("guis/cellRestriction.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
        this.amountField.drawTextBox();
        this.typesField.drawTextBox();
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) {
        this.amountField.mouseClicked(xCoord, yCoord, btn);
        this.typesField.mouseClicked(xCoord, yCoord, btn);
        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void keyTyped(final char character, final int key) {
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            try {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("cellRestriction", filterCellRestriction()));
            } catch (IOException e) {
                AELog.debug(e);
            }
            NetworkHandler.instance.sendToServer(new PacketSwitchGuis());
        } else if (!(this.amountField.textboxKeyTyped(character, key)
                || this.typesField.textboxKeyTyped(character, key))) {
                    super.keyTyped(character, key);
                }
    }

    private long getAmount() {
        String out = this.amountField.getText();
        double resultD = Calculator.conversion(out);

        if (resultD <= 0 || Double.isNaN(resultD)) {
            return 0;
        } else {
            return (long) ArithHelper.round(resultD, 0);
        }
    }
}
