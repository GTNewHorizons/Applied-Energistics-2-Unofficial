package appeng.client.gui.implementations;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.AEBaseContainer;
import appeng.core.AEConfig;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.helpers.Reflected;
import appeng.util.calculators.ArithHelper;
import appeng.util.calculators.Calculator;

public abstract class GuiAmount extends AEBaseGui {

    protected GuiTextField amountTextField;
    protected GuiTabButton originalGuiBtn;

    protected GuiButton nextBtn;

    protected GuiButton plus1;
    protected GuiButton plus10;
    protected GuiButton plus100;
    protected GuiButton plus1000;
    protected GuiButton minus1;
    protected GuiButton minus10;
    protected GuiButton minus100;
    protected GuiButton minus1000;

    protected GuiBridge originalGui;
    protected ItemStack myIcon;

    @Reflected
    public GuiAmount(final Container container) {
        super(container);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        super.initGui();

        final int a = this.getButtonQtyByIndex(0);
        final int b = this.getButtonQtyByIndex(1);
        final int c = this.getButtonQtyByIndex(2);
        final int d = this.getButtonQtyByIndex(3);

        this.buttonList.add(this.plus1 = new GuiButton(0, this.guiLeft + 20, this.guiTop + 26, 22, 20, "+" + a));
        this.buttonList.add(this.plus10 = new GuiButton(0, this.guiLeft + 48, this.guiTop + 26, 28, 20, "+" + b));
        this.buttonList.add(this.plus100 = new GuiButton(0, this.guiLeft + 82, this.guiTop + 26, 32, 20, "+" + c));
        this.buttonList.add(this.plus1000 = new GuiButton(0, this.guiLeft + 120, this.guiTop + 26, 38, 20, "+" + d));

        this.buttonList.add(this.minus1 = new GuiButton(0, this.guiLeft + 20, this.guiTop + 75, 22, 20, "-" + a));
        this.buttonList.add(this.minus10 = new GuiButton(0, this.guiLeft + 48, this.guiTop + 75, 28, 20, "-" + b));
        this.buttonList.add(this.minus100 = new GuiButton(0, this.guiLeft + 82, this.guiTop + 75, 32, 20, "-" + c));
        this.buttonList.add(this.minus1000 = new GuiButton(0, this.guiLeft + 120, this.guiTop + 75, 38, 20, "-" + d));

        this.buttonList.add(
                this.nextBtn = new GuiButton(0, this.guiLeft + 128, this.guiTop + 51, 38, 20, GuiText.Next.getLocal()));

        final Object target = ((AEBaseContainer) this.inventorySlots).getTarget();

        this.setOriginGUI(target);
        if (this.originalGui != null && myIcon != null) {
            this.buttonList.add(
                    this.originalGuiBtn = new GuiTabButton(
                            this.guiLeft + 154,
                            this.guiTop,
                            this.myIcon,
                            this.myIcon.getDisplayName(),
                            itemRender));
        }

        this.amountTextField = new GuiTextField(
                this.fontRendererObj,
                this.guiLeft + 62,
                this.guiTop + 57,
                59,
                this.fontRendererObj.FONT_HEIGHT);
        this.amountTextField.setEnableBackgroundDrawing(false);
        this.amountTextField.setMaxStringLength(16);
        this.amountTextField.setTextColor(GuiColors.CraftAmountToCraft.getColor());
        this.amountTextField.setVisible(true);
        this.amountTextField.setFocused(true);
    }

    protected abstract void setOriginGUI(Object target);

    protected int getButtonQtyByIndex(int index) {
        return AEConfig.instance.craftItemsByStackAmounts(index);
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture(getBackground());
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
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
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);
        if (btn == this.originalGuiBtn) {
            NetworkHandler.instance.sendToServer(new PacketSwitchGuis(this.originalGui));
        }

        final boolean isPlus = btn == this.plus1 || btn == this.plus10 || btn == this.plus100 || btn == this.plus1000;
        final boolean isMinus = btn == this.minus1 || btn == this.minus10
                || btn == this.minus100
                || btn == this.minus1000;

        if (isPlus || isMinus) {
            int resultI = addOrderAmount(this.getQty(btn));
            this.amountTextField.setText(Integer.toString(resultI));
        }
    }

    protected int addOrderAmount(final int i) {
        int resultI = getAmount();

        if (resultI == 1 && i > 1) {
            resultI = 0;
        }

        resultI += i;
        if (resultI < 1) {
            resultI = 1;
        }
        return resultI;
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

    protected abstract String getBackground();
}
