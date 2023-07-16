package appeng.client.gui.implementations;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import appeng.api.config.ActionItems;
import appeng.api.config.ItemSubstitution;
import appeng.api.config.PatternBeSubstitution;
import appeng.api.config.Settings;
import appeng.api.storage.ITerminalHost;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.container.ContainerBasePatternTerm;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.container.slot.AppEngSlot;

public abstract class GuiBasePatternTerm extends GuiMEMonitorable {

    protected static final String SUBSITUTION_DISABLE = "0";
    protected static final String SUBSITUTION_ENABLE = "1";
    protected final ContainerBasePatternTerm container;
    protected final GuiImgButton subsEnabledBtn;
    protected final GuiImgButton subsDisabledBtn;
    protected final GuiImgButton amSubEnabledBtn;
    protected final GuiImgButton amSubDisabledBtn;
    protected final GuiImgButton clearBtn;
    protected final GuiImgButton doubleBtn;
    protected final GuiImgButton encodeBtn;

    /**
     * It is the responsibility of children to set these in {@link #initLayout()}.
     */
    protected int btnArrLeft, btnArrTop, btnOffsetX, btnOffsetY;

    public GuiBasePatternTerm(InventoryPlayer inventoryPlayer, ITerminalHost te, ContainerMEMonitorable c) {
        super(inventoryPlayer, te, c);
        this.container = (ContainerBasePatternTerm) this.inventorySlots;
        this.setReservedSpace(81);

        this.subsEnabledBtn = new GuiImgButton(0, 0, Settings.ACTIONS, ItemSubstitution.ENABLED);
        this.subsEnabledBtn.setHalfSize(true);

        this.subsDisabledBtn = new GuiImgButton(0, 0, Settings.ACTIONS, ItemSubstitution.DISABLED);
        this.subsDisabledBtn.setHalfSize(true);

        this.amSubEnabledBtn = new GuiImgButton(0, 0, Settings.ACTIONS, PatternBeSubstitution.ENABLED);
        this.amSubEnabledBtn.setHalfSize(true);

        this.clearBtn = new GuiImgButton(0, 0, Settings.ACTIONS, ActionItems.CLOSE);
        this.clearBtn.setHalfSize(true);

        this.amSubDisabledBtn = new GuiImgButton(0, 0, Settings.ACTIONS, PatternBeSubstitution.DISABLED);
        this.amSubDisabledBtn.setHalfSize(true);

        this.doubleBtn = new GuiImgButton(0, 0, Settings.ACTIONS, ActionItems.DOUBLE);
        this.doubleBtn.setHalfSize(true);

        this.encodeBtn = new GuiImgButton(0, 0, Settings.ACTIONS, ActionItems.ENCODE);
    }

    /**
     * Layout the button positions.
     */
    protected abstract void initLayout();

    protected void layoutButton(final GuiButton button, final int gridX, final int gridY) {
        button.xPosition = this.btnArrLeft + gridX * this.btnOffsetX;
        button.yPosition = this.btnArrTop + gridY * this.btnOffsetY;
    }

    @Override
    public void initGui() {
        super.initGui();
        initLayout();

        layoutButton(clearBtn, 0, 0);
        layoutButton(subsEnabledBtn, 1, 0);
        layoutButton(subsDisabledBtn, 1, 0);
        layoutButton(amSubEnabledBtn, 1, 1);
        layoutButton(amSubDisabledBtn, 1, 1);
        layoutButton(doubleBtn, 0, 1);
        encodeBtn.xPosition = this.guiLeft + 147;
        encodeBtn.yPosition = this.guiTop + this.ySize - 142;

        buttonList.add(clearBtn);
        buttonList.add(subsEnabledBtn);
        buttonList.add(subsDisabledBtn);
        buttonList.add(amSubEnabledBtn);
        buttonList.add(amSubDisabledBtn);
        buttonList.add(doubleBtn);
        buttonList.add(encodeBtn);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        if (this.container.substitute) {
            this.subsEnabledBtn.visible = true;
            this.subsDisabledBtn.visible = false;
        } else {
            this.subsEnabledBtn.visible = false;
            this.subsDisabledBtn.visible = true;
        }
        this.amSubEnabledBtn.visible = this.container.beSubstitute;
        this.amSubDisabledBtn.visible = !this.container.beSubstitute;
        super.drawFG(offsetX, offsetY, mouseX, mouseY);
    }

    @Override
    protected void repositionSlot(AppEngSlot s) {
        if (s.isPlayerSide()) {
            s.yDisplayPosition = s.getY() + this.ySize - 78 - 5;
        } else {
            s.yDisplayPosition = s.getY() + this.ySize - 78 - 3;
        }
    }

    @Override
    public boolean hideItemPanelSlot(int tx, int ty, int tw, int th) {

        if (this.viewCell) {
            int rw = 33;
            // Make space for upgrade slot
            int rh = 14 + 21 + myCurrentViewCells.length * 18;

            if (rh <= 0 || tw <= 0 || th <= 0) {
                return false;
            }

            int rx = this.guiLeft + this.xSize;
            int ry = this.guiTop;

            rw += rx;
            rh += ry;
            tw += tx;
            th += ty;

            // overflow || intersect
            return (rw < rx || rw > tx) && (rh < ry || rh > ty) && (tw < tx || tw > rx) && (th < ty || th > ry);
        }
        return false;
    }
}
