package appeng.client.gui.implementations;

import java.text.NumberFormat;
import java.util.Locale;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.inventory.Container;

import org.lwjgl.input.Keyboard;

import appeng.client.gui.GuiSub;
import appeng.client.gui.widgets.GuiQuantityButton;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import appeng.helpers.Reflected;
import appeng.util.ReadableNumberConverter;
import appeng.util.calculators.ArithHelper;
import appeng.util.calculators.Calculator;

public abstract class GuiAmount extends GuiSub {

    protected MEGuiTextField amountTextField;

    protected GuiButton nextBtn;

    protected GuiQuantityButton plus1;
    protected GuiQuantityButton plus10;
    protected GuiQuantityButton plus100;
    protected GuiQuantityButton plus1000;
    protected GuiQuantityButton minus1;
    protected GuiQuantityButton minus10;
    protected GuiQuantityButton minus100;
    protected GuiQuantityButton minus1000;

    @Reflected
    public GuiAmount(final Container container) {
        super(container);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        super.initGui();

        this.refreshAmountButtons();

        this.buttonList.add(
                this.nextBtn = new GuiButton(0, this.guiLeft + 128, this.guiTop + 51, 38, 20, GuiText.Next.getLocal()));

        this.amountTextField = new MEGuiTextField(61, 12) {

            @Override
            public void onTextChange(final String oldText) {
                GuiAmount.this.updateTextFieldTooltip();
            }
        };
        this.amountTextField.x = this.guiLeft + 60;
        this.amountTextField.y = this.guiTop + 55;
        this.amountTextField.setMaxStringLength(16);
        this.amountTextField.setFocused(true);
        this.amountTextField.setUnfocusWithEnter(false);
        this.updateTextFieldTooltip();
    }

    protected int getButtonQtyByIndex(int index) {
        return AEConfig.instance.craftItemsByStackAmounts(index);
    }

    protected void refreshAmountButtons() {
        this.removeAmountButtons();

        final int a = this.getButtonQtyByIndex(0);
        final int b = this.getButtonQtyByIndex(1);
        final int c = this.getButtonQtyByIndex(2);
        final int d = this.getButtonQtyByIndex(3);

        this.buttonList.add(
                this.plus1 = new GuiQuantityButton(
                        0,
                        this.guiLeft + 20,
                        this.guiTop + 26,
                        22,
                        20,
                        GuiText.IncreaseAmount,
                        a,
                        "+%s"));
        this.buttonList.add(
                this.plus10 = new GuiQuantityButton(
                        0,
                        this.guiLeft + 48,
                        this.guiTop + 26,
                        28,
                        20,
                        GuiText.IncreaseAmount,
                        b,
                        "+%s"));
        this.buttonList.add(
                this.plus100 = new GuiQuantityButton(
                        0,
                        this.guiLeft + 82,
                        this.guiTop + 26,
                        32,
                        20,
                        GuiText.IncreaseAmount,
                        c,
                        "+%s"));
        this.buttonList.add(
                this.plus1000 = new GuiQuantityButton(
                        0,
                        this.guiLeft + 120,
                        this.guiTop + 26,
                        38,
                        20,
                        GuiText.IncreaseAmount,
                        d,
                        "+%s"));

        this.buttonList.add(
                this.minus1 = new GuiQuantityButton(
                        0,
                        this.guiLeft + 20,
                        this.guiTop + 75,
                        22,
                        20,
                        GuiText.DecreaseAmount,
                        -a,
                        "-%s"));
        this.buttonList.add(
                this.minus10 = new GuiQuantityButton(
                        0,
                        this.guiLeft + 48,
                        this.guiTop + 75,
                        28,
                        20,
                        GuiText.DecreaseAmount,
                        -b,
                        "-%s"));
        this.buttonList.add(
                this.minus100 = new GuiQuantityButton(
                        0,
                        this.guiLeft + 82,
                        this.guiTop + 75,
                        32,
                        20,
                        GuiText.DecreaseAmount,
                        -c,
                        "-%s"));
        this.buttonList.add(
                this.minus1000 = new GuiQuantityButton(
                        0,
                        this.guiLeft + 120,
                        this.guiTop + 75,
                        38,
                        20,
                        GuiText.DecreaseAmount,
                        -d,
                        "-%s"));
    }

    protected void removeAmountButtons() {
        this.buttonList.remove(this.plus1);
        this.buttonList.remove(this.plus10);
        this.buttonList.remove(this.plus100);
        this.buttonList.remove(this.plus1000);
        this.buttonList.remove(this.minus1);
        this.buttonList.remove(this.minus10);
        this.buttonList.remove(this.minus100);
        this.buttonList.remove(this.minus1000);
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture(getBackground());
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
        if (this.isAmountTextFieldVisible()) {
            this.handleTooltip(mouseX, mouseY, this.amountTextField);
        }
    }

    @Override
    protected void keyTyped(final char character, final int key) {
        if (!this.checkHotbarKeys(key)) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                this.actionPerformed(this.nextBtn);
            }
            this.amountTextField.textboxKeyTyped(character, key);
            super.keyTyped(character, key);
        }
    }

    @Override
    protected void mouseClicked(int xCoord, int yCoord, int btn) {
        super.mouseClicked(xCoord, yCoord, btn);
        this.amountTextField.mouseClickedNoFocusDrop(xCoord, yCoord, btn);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        final boolean isPlus = btn == this.plus1 || btn == this.plus10 || btn == this.plus100 || btn == this.plus1000;
        final boolean isMinus = btn == this.minus1 || btn == this.minus10
                || btn == this.minus100
                || btn == this.minus1000;

        if (isPlus || isMinus) {
            this.addAmount(this.getQty(btn));
        }
    }

    protected void addAmount(final int i) {
        long resultL = getAmountLong();

        if (resultL == 1 && i > 1) {
            resultL = 0;
        }

        resultL += i;
        if (resultL < 1) {
            resultL = 1;
        }

        this.amountTextField.setText(Long.toString(resultL));
        this.amountTextField.setCursorPositionEnd();
    }

    protected int getAmount() {
        String out = this.amountTextField.getText();
        double resultD = Calculator.conversion(out);

        if (resultD <= 0 || Double.isNaN(resultD)) {
            return 0;
        } else {
            return (int) ArithHelper.round(resultD, 0);
        }
    }

    protected long getAmountLong() {
        String out = this.amountTextField.getText();
        double resultD = Calculator.conversion(out);

        if (resultD <= 0 || Double.isNaN(resultD)) {
            return 0;
        } else {
            return (long) ArithHelper.round(resultD, 0);
        }
    }

    protected boolean isAmountTextFieldVisible() {
        return true;
    }

    protected void updateTextFieldTooltip() {
        final String text = this.amountTextField.getText();
        final long amount;

        try {
            amount = this.getAmountLong();
        } catch (final NumberFormatException e) {
            this.amountTextField.setMessage("");
            return;
        }

        final boolean containsNonDigit = text.chars().anyMatch(ch -> !Character.isDigit(ch));
        if (amount >= 1 && containsNonDigit) {
            final String formattedAmount = "= " + NumberFormat.getNumberInstance(Locale.US).format(amount);
            if (amount > 1_000_000L) {
                this.amountTextField.setMessage(
                        formattedAmount + " (" + ReadableNumberConverter.INSTANCE.toWideReadableForm(amount) + ")");
            } else {
                this.amountTextField.setMessage(formattedAmount);
            }
        } else {
            this.amountTextField.setMessage("");
        }
    }

    protected abstract String getBackground();
}
