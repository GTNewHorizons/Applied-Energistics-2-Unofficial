package appeng.container.implementations;

import appeng.helpers.IRegulatorCard;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.tileentity.TileEntity;

import appeng.api.parts.IPart;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ContainerRegulatorCard extends AEBaseContainer {

    private final IRegulatorCard filterHost;

    @SideOnly(Side.CLIENT)
    private MEGuiTextField amountField;

    @SideOnly(Side.CLIENT)
    private MEGuiTextField ticksField;

    @GuiSync(69)
    public String regulatorSettings = "1000:20";

    public ContainerRegulatorCard(final InventoryPlayer ip, final IRegulatorCard te) {
        super(ip, (TileEntity) (te instanceof TileEntity ? te : null), (IPart) (te instanceof IPart ? te : null));
        this.filterHost = te;
    }

    @SideOnly(Side.CLIENT)
    public void setAmountField(final MEGuiTextField f) {
        this.amountField = f;
        String[] rs = regulatorSettings.split(":");
        this.amountField.setText(rs[0]);
    }

    @SideOnly(Side.CLIENT)
    public void setTicksField(final MEGuiTextField f) {
        this.ticksField = f;
        String[] rs = regulatorSettings.split(":");
        this.ticksField.setText(rs[1]);
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
        }
        super.onUpdate(field, oldValue, newValue);
    }
}