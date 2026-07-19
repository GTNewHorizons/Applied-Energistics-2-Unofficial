package appeng.client.gui.implementations;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import org.lwjgl.input.Mouse;

import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.parts.IAdvancedLevelEmitter;
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.implementations.ContainerAdvancedLevelEmitter;
import appeng.core.localization.ColorUtils;
import appeng.core.localization.GuiText;
import appeng.util.calculators.ArithHelper;
import appeng.util.calculators.Calculator;

public class GuiAdvancedLevelEmitter extends AEBaseGui {

    private static final int SLOTS = IAdvancedLevelEmitter.SLOT_COUNT;
    private static final int ROW_HEIGHT = 20;

    private static final int TOGGLE_X = 10;
    private static final int TOGGLE_Y = 24;
    private static final int SLOT_X = 31;
    private static final int SLOT_Y = 23;
    private static final int FIELD_X = 54;
    private static final int FIELD_Y = 26;
    private static final int FIELD_W = 90;
    private static final int FIELD_H = 12;
    private static final int INVERT_X = 149;
    private static final int INVERT_Y = 24;
    private static final int PLAYER_INV_Y = 153;

    private final ContainerAdvancedLevelEmitter container;

    private final GuiImgButton[] toggleButtons = new GuiImgButton[SLOTS];
    private final GuiImgButton[] invertButtons = new GuiImgButton[SLOTS];
    private final MEGuiTextField[] amountFields = new MEGuiTextField[SLOTS];

    private GuiImgButton logicMode;

    public GuiAdvancedLevelEmitter(final InventoryPlayer inventoryPlayer, final IAdvancedLevelEmitter te) {
        super(new ContainerAdvancedLevelEmitter(inventoryPlayer, te));
        this.container = (ContainerAdvancedLevelEmitter) this.inventorySlots;
        this.xSize = 246;
        this.ySize = 235;
    }

    @Override
    public void initGui() {
        super.initGui();

        this.buttonList.add(
                this.logicMode = new GuiImgButton(
                        this.guiLeft - 18,
                        this.guiTop + 8,
                        Settings.ADVANCED_LEVEL_EMITTER_LOGIC,
                        this.container.getLogicMode()));

        for (int slot = 0; slot < SLOTS; slot++) {
            final int s = slot;
            final int y = slot * ROW_HEIGHT;

            this.buttonList.add(
                    this.toggleButtons[slot] = new GuiImgButton(
                            this.guiLeft + TOGGLE_X,
                            this.guiTop + TOGGLE_Y + y,
                            Settings.ADVANCED_LEVEL_EMITTER_SLOT_ENABLED,
                            this.container.isSlotActive(slot) ? YesNo.YES : YesNo.NO));

            this.buttonList.add(
                    this.invertButtons[slot] = new GuiImgButton(
                            this.guiLeft + INVERT_X,
                            this.guiTop + INVERT_Y + y,
                            Settings.REDSTONE_EMITTER,
                            this.container.isSlotInverted(slot) ? RedstoneMode.LOW_SIGNAL : RedstoneMode.HIGH_SIGNAL));

            final MEGuiTextField field = new MEGuiTextField(FIELD_W, FIELD_H) {

                @Override
                public void onTextChange(final String oldText) {
                    GuiAdvancedLevelEmitter.this.onAmountChanged(s);
                }
            };
            field.x = this.guiLeft + FIELD_X;
            field.y = this.guiTop + FIELD_Y + y;
            this.amountFields[slot] = field;
            field.setText(Long.toString(this.container.getReportingValue(slot)), true);
            this.container.setTextField(slot, field);

            this.registerVirtualSlots(
                    new VirtualMEPhantomSlot(
                            SLOT_X,
                            SLOT_Y + y,
                            this.container.configSync,
                            slot,
                            GuiAdvancedLevelEmitter::acceptType));
        }
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRendererObj.drawString(
                this.getGuiDisplayName(GuiText.AdvancedLevelEmitter.getLocal()),
                8,
                6,
                ColorUtils.guiTextColorGray.getColor());
        this.fontRendererObj
                .drawString(GuiText.inventory.getLocal(), 8, PLAYER_INV_Y - 11, ColorUtils.guiTextColorGray.getColor());

        if (this.logicMode != null) {
            this.logicMode.set(this.container.getLogicMode());
        }

        for (int slot = 0; slot < SLOTS; slot++) {
            if (this.toggleButtons[slot] != null) {
                this.toggleButtons[slot].set(this.container.isSlotActive(slot) ? YesNo.YES : YesNo.NO);
            }
            if (this.invertButtons[slot] != null) {
                this.invertButtons[slot]
                        .set(this.container.isSlotInverted(slot) ? RedstoneMode.LOW_SIGNAL : RedstoneMode.HIGH_SIGNAL);
            }
        }
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture(this.getBackground());
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);

        for (final MEGuiTextField field : this.amountFields) {
            field.drawTextBox();
        }
    }

    private String getBackground() {
        return "guis/lvlemitteradvanced.png";
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        final boolean backwards = Mouse.isButtonDown(1);

        if (btn == this.logicMode) {
            this.container.rotateLogicMode(backwards);
            return;
        }

        for (int slot = 0; slot < SLOTS; slot++) {
            if (btn == this.toggleButtons[slot]) {
                this.container.setSlotActive(slot, !this.container.isSlotActive(slot));
                return;
            }
            if (btn == this.invertButtons[slot]) {
                this.container.setSlotInverted(slot, !this.container.isSlotInverted(slot));
                return;
            }
        }
    }

    private void onAmountChanged(final int slot) {
        final double parsed = Calculator.conversion(this.amountFields[slot].getText());
        if (!Double.isNaN(parsed)) {
            this.container.setLevel(slot, Math.max(0, (long) ArithHelper.round(parsed, 0)));
        }
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) {
        for (final MEGuiTextField field : this.amountFields) {
            field.mouseClicked(xCoord, yCoord, btn);
        }

        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void keyTyped(final char character, final int key) {
        if (!this.checkHotbarKeys(key)) {
            boolean handled = false;
            for (final MEGuiTextField field : this.amountFields) {
                if (field.textboxKeyTyped(character, key)) {
                    handled = true;
                    break;
                }
            }

            if (!handled) {
                super.keyTyped(character, key);
            }
        }
    }

    private static boolean acceptType(final VirtualMEPhantomSlot slot, final IAEStackType<?> type,
            final int mouseButton) {
        return true;
    }
}
