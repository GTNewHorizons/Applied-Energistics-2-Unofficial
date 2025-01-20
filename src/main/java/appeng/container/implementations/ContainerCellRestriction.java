package appeng.container.implementations;

import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.tileentity.TileEntity;

import appeng.api.parts.IPart;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.helpers.ICellRestriction;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ContainerCellRestriction extends AEBaseContainer {

    public static class cellData {

        private Long totalBytes;
        private Integer totalTypes;
        private Integer perType;
        private Integer perByte;

        public void setPerType(Integer perType) {
            this.perType = perType;
        }

        public void setTotalBytes(Long totalBytes) {
            this.totalBytes = totalBytes;
        }

        public void setTotalTypes(Integer totalTypes) {
            this.totalTypes = totalTypes;
        }

        public void setPerByte(Integer perByte) {
            this.perByte = perByte;
        }

        public Integer getPerType() {
            return perType;
        }

        public Integer getTotalTypes() {
            return totalTypes;
        }

        public Long getTotalBytes() {
            return totalBytes;
        }

        public Integer getPerByte() {
            return perByte;
        }
    }

    private final ICellRestriction Host;

    @SideOnly(Side.CLIENT)
    private MEGuiTextField typesField;

    @SideOnly(Side.CLIENT)
    private MEGuiTextField amountField;

    @SideOnly(Side.CLIENT)
    private cellData cellData;

    @GuiSync(69)
    public String cellRestriction;

    public ContainerCellRestriction(final InventoryPlayer ip, final ICellRestriction te) {
        super(ip, (TileEntity) (te instanceof TileEntity ? te : null), (IPart) (te instanceof IPart ? te : null));
        this.Host = te;
    }

    @SideOnly(Side.CLIENT)
    public void setAmountField(final MEGuiTextField f) {
        this.amountField = f;
    }

    @SideOnly(Side.CLIENT)
    public void setTypesField(final MEGuiTextField f) {
        this.typesField = f;
    }

    @SideOnly(Side.CLIENT)
    public void setCellData(cellData newCellData) {
        this.cellData = newCellData;
    }

    public void setCellRestriction(String data) {
        this.Host.setCellRestriction(null, data);
        this.cellRestriction = data;
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) this.cellRestriction = this.Host.getCellData(null);
        super.detectAndSendChanges();
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        if (field.equals("cellRestriction") && (this.amountField != null && this.typesField != null)) {
            List<String> newData = Arrays.asList(cellRestriction.split(",", 6));
            this.cellData.setTotalBytes(Long.parseLong(newData.get(0)));
            this.cellData.setTotalTypes(Integer.parseInt(newData.get(1)));
            this.cellData.setPerType(Integer.parseInt(newData.get(2)));
            this.cellData.setPerByte(Integer.parseInt(newData.get(3)));
            this.typesField.setText(newData.get(4));
            this.amountField.setText(newData.get(5));
        }
        super.onUpdate(field, oldValue, newValue);
    }
}
