package appeng.client.gui.implementations;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import org.lwjgl.input.Keyboard;

import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;

import appeng.client.gui.GuiSub;
import appeng.client.gui.widgets.GuiAeButton;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.implementations.ContainerCellRestriction;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.helpers.ICellRestriction;
import appeng.helpers.ICellRestriction.CellData;
import appeng.helpers.ICellRestriction.CellRestrictionData;
import appeng.util.calculators.ArithHelper;
import appeng.util.calculators.Calculator;

public class GuiCellRestriction extends GuiSub {

    private final MEGuiTextField amountField;
    private final MEGuiTextField typesField;
    private final ContainerCellRestriction cellRestriction;
    private final GuiAeButton reset;

    private CellData cellData = new CellData(0, 0, 0, 0);
    private CellRestrictionData cellRestrictionData = new CellRestrictionData((byte) 0, 0);

    private static final int BASE_LINE_X = 48;

    public GuiCellRestriction(InventoryPlayer ip, ICellRestriction obj) {
        super(new ContainerCellRestriction(ip, obj));
        this.xSize = 213;

        this.cellRestriction = (ContainerCellRestriction) this.inventorySlots;
        this.amountField = new MEGuiTextField(95, 12);
        this.typesField = new MEGuiTextField(30, 12);
        this.typesField.setMaxStringLength(3);

        this.reset = new GuiAeButton(
                0,
                0,
                0,
                12,
                12,
                GuiText.ResetRestriction.getLocal(),
                GuiText.ResetRestrictionHint.getLocal());
    }

    @Override
    public void initGui() {
        super.initGui();

        this.amountField.x = this.guiLeft + 62;
        this.typesField.x = this.guiLeft + 170;

        this.amountField.y = this.guiTop + 32;
        this.typesField.y = this.amountField.y;

        this.reset.xPosition = this.guiLeft + BASE_LINE_X;
        this.reset.yPosition = this.amountField.y;

        this.buttonList.add(this.reset);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        if (this.cellRestriction.updated) {
            this.cellData = this.cellRestriction.cellDataSync.get();
            this.cellRestrictionData = this.cellRestriction.cellRestrictionDataSync.get();
            this.typesField.setText(String.valueOf(this.cellRestrictionData.restrictionTypes));
            this.amountField.setText(String.valueOf(this.cellRestrictionData.restrictionAmount));

            this.cellRestriction.updated = false;
        }

        this.fontRendererObj
                .drawString(GuiText.CellRestriction.getLocal(), BASE_LINE_X, 6, GuiColors.GuiTextColorGray.getColor());

        this.fontRendererObj
                .drawString(GuiText.ResourceAmount.getLocal(), BASE_LINE_X, 22, GuiColors.GuiTextColorGray.getColor());

        this.fontRendererObj
                .drawString(GuiText.Types.getLocal(), BASE_LINE_X + 122, 22, GuiColors.GuiTextColorGray.getColor());

        int cellDataLine = 37;
        final int types = this.getTypes();
        final long bytesAllocated = this.getBytesAllocated(types);
        this.fontRendererObj.drawString(
                GuiText.MaximumOfResource.getLocal(fmt(this.getMaxAmount(types))),
                BASE_LINE_X,
                cellDataLine += 10,
                GuiColors.GuiTextColorGray.getColor());
        this.fontRendererObj.drawString(
                GuiText.BytesTotal.getLocal(fmt(this.cellData.totalBytes)),
                BASE_LINE_X,
                cellDataLine += 10,
                GuiColors.GuiTextColorGray.getColor());

        this.fontRendererObj.drawString(
                GuiText.BytesAllocated.getLocal(fmt(bytesAllocated)),
                BASE_LINE_X,
                cellDataLine += 10,
                GuiColors.GuiTextColorGray.getColor());

        this.fontRendererObj.drawString(
                GuiText.BytesFree.getLocal(fmt(this.getFreeBytes(bytesAllocated))),
                BASE_LINE_X,
                cellDataLine += 10,
                GuiColors.GuiTextColorGray.getColor());

        this.fontRendererObj.drawString(
                GuiText.ResourcesPerByte.getLocal(fmt(this.cellData.perByte)),
                BASE_LINE_X,
                cellDataLine += 10,
                GuiColors.GuiTextColorGray.getColor());

        this.fontRendererObj.drawString(
                GuiText.BytesPerType.getLocal(fmt(this.cellData.perType)),
                BASE_LINE_X,
                cellDataLine += 10,
                GuiColors.GuiTextColorGray.getColor());
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
    protected void actionPerformed(GuiButton btn) {
        if (btn == this.reset) {
            this.cellRestriction.cellRestrictionDataSync.set(new CellRestrictionData((byte) 0, 0));
            this.cellRestriction.updated = true;
        } else super.actionPerformed(btn);
    }

    @Override
    protected void keyTyped(final char character, final int key) {
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            this.cellRestriction.cellRestrictionDataSync.set(filterCellRestriction());
            this.flushPendingSync();
            NetworkHandler.instance.sendToServer(new PacketSwitchGuis());
        } else
            if (!(this.amountField.textboxKeyTyped(character, key) || this.typesField.textboxKeyTyped(character, key)))
                super.keyTyped(character, key);
    }

    private static String fmt(double v) {
        return NumberFormatUtil.formatNumber(v);
    }

    private CellRestrictionData filterCellRestriction() {
        final byte types = (byte) this.getTypes();
        return new CellRestrictionData(types, this.getAmount(types));
    }

    private long getFreeBytes(final long bytesAllocated) {
        return this.cellData.totalBytes - bytesAllocated;
    }

    private long getBytesAllocated(final int types) {
        final int nTypes = types == 0 ? this.cellData.totalTypes : types;
        return (long) this.cellData.perType * nTypes
                + (long) Math.ceil((double) this.getAmount(types) / this.cellData.perByte);
    }

    private long getMaxAmount(final int types) {
        final int nTypes = types == 0 ? this.cellData.totalTypes : types;
        return (this.cellData.totalBytes - (long) this.cellData.perType * nTypes) * this.cellData.perByte;
    }

    private long getAmount(final int types) {
        try {
            final double resultD = Calculator.conversion(this.amountField.getText());

            if (resultD <= 0 || Double.isNaN(resultD)) return 0;
            else return (long) Math.min(ArithHelper.round(resultD, 0), this.getMaxAmount(types));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private int getTypes() {
        try {
            return Math.max(
                    0,
                    Math.min(
                            Integer.parseInt(this.typesField.getText()),
                            this.cellRestriction.cellDataSync.get().totalTypes));
        } catch (Exception ignored) {
            return 0;
        }
    }
}
