package appeng.client.gui.implementations;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.api.definitions.IDefinitions;
import appeng.api.definitions.IParts;
import appeng.api.storage.ITerminalHost;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.container.implementations.ContainerPatternMulti;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketPatternMultiSet;
import appeng.helpers.Reflected;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.parts.reporting.PartPatternTerminalEx;
import appeng.util.calculators.ArithHelper;
import appeng.util.calculators.Calculator;

public class GuiPatternMulti extends GuiAmount {

    private static final int DEFAULT_VALUE = 0;
    private GuiImgButton symbolSwitch;

    @Reflected
    public GuiPatternMulti(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(new ContainerPatternMulti(inventoryPlayer, te));
    }

    @Override
    public void initGui() {
        super.initGui();

        this.buttonList.add(
                this.symbolSwitch = new GuiImgButton(
                        this.guiLeft + 22,
                        this.guiTop + 53,
                        Settings.ACTIONS,
                        ActionItems.MULTIPLY));

        this.amountTextField.setText(String.valueOf(DEFAULT_VALUE));
        this.amountTextField.setSelectionPos(0);
    }

    @Override
    protected void setOriginGUI(Object target) {
        final IDefinitions definitions = AEApi.instance().definitions();
        final IParts parts = definitions.parts();

        if (target instanceof PartPatternTerminal) {
            for (final ItemStack stack : parts.patternTerminal().maybeStack(1).asSet()) {
                myIcon = stack;
            }
            this.originalGui = GuiBridge.GUI_PATTERN_TERMINAL;
        }

        else if (target instanceof PartPatternTerminalEx) {
            for (final ItemStack stack : parts.patternTerminalEx().maybeStack(1).asSet()) {
                myIcon = stack;
            }
            this.originalGui = GuiBridge.GUI_PATTERN_TERMINAL_EX;
        }
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
            int resultI = getAmount();

            this.symbolSwitch.set(resultI >= 0 ? ActionItems.MULTIPLY : ActionItems.DIVIDE);
            this.nextBtn.enabled = resultI < -1 || resultI > 1;
        } catch (final NumberFormatException e) {
            this.nextBtn.enabled = false;
        }

        this.amountTextField.drawTextBox();
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        try {

            if (btn == this.nextBtn && btn.enabled) {
                int resultI = getAmount();
                if (resultI > 1 || resultI < -1) NetworkHandler.instance
                        .sendToServer(new PacketPatternMultiSet(this.originalGui.ordinal(), resultI));
            }
        } catch (final NumberFormatException e) {
            // nope..
            this.amountTextField.setText(String.valueOf(DEFAULT_VALUE));
        }

        if (btn == this.symbolSwitch) {
            int resultI = -getAmount();
            this.amountTextField.setText(Integer.toString(resultI));
        }

    }

    @Override
    protected int getAmount() {
        String out = this.amountTextField.getText();

        double resultD = Calculator.conversion(out);

        if (Double.isNaN(resultD)) {
            return DEFAULT_VALUE;
        } else {
            return (int) ArithHelper.round(resultD, 0);
        }
    }

    @Override
    protected int addOrderAmount(final int i) {
        return i + getAmount();
    }

    @Override
    protected String getBackground() {
        return "guis/patternMulti.png";
    }
}
