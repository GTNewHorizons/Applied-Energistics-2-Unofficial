package appeng.container.implementations;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.tileentity.TileEntity;

import appeng.api.parts.IPart;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.helpers.IRegulatorCard;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ContainerRegulatorCard extends AEBaseContainer {

    private final IRegulatorCard filterHost;

    @SideOnly(Side.CLIENT)
    private MEGuiTextField amountField;

    @SideOnly(Side.CLIENT)
    private MEGuiTextField ticksField;

    @SideOnly(Side.CLIENT)
    private MEGuiTextField stockModeField;

    @GuiSync(69)
    public String regulatorSettings;

    public ContainerRegulatorCard(final InventoryPlayer ip, final IRegulatorCard te) {
        super(ip, (TileEntity) (te instanceof TileEntity ? te : null), (IPart) (te instanceof IPart ? te : null));
        this.filterHost = te;
    }

    @SideOnly(Side.CLIENT)
    public void setAmountField(final MEGuiTextField f) {
        this.amountField = f;
    }

    @SideOnly(Side.CLIENT)
    public void setTicksField(final MEGuiTextField f) {
        this.ticksField = f;
    }

    @SideOnly(Side.CLIENT)
    public void setStockModeField(final MEGuiTextField f) {
        this.stockModeField = f;
    }

    public void setRegulatorSettings(final String newValue) {
        this.filterHost.setRegulatorSettings(newValue);
        this.regulatorSettings = newValue;
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) this.regulatorSettings = this.filterHost.getRegulatorSettings();
        super.detectAndSendChanges();
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        if (field.equals("regulatorSettings") && (this.amountField != null && this.ticksField != null)) {
            String[] rs = regulatorSettings.split(":");
            this.amountField.setText(rs[0]);
            this.ticksField.setText(rs[1]);
            if (Boolean.parseBoolean(rs[2])) {
                this.stockModeField.setText("Active");
            } else {
                this.stockModeField.setText("Not Active");
            }
        }
        super.onUpdate(field, oldValue, newValue);
    }
}
