package appeng.container.implementations;

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

    public static class CellData {

        private Long totalBytes;
        private Integer totalTypes;
        private Integer perType;
        private Integer perByte;
        private String cellType;

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

        public void setCellType(String cellType) {
            this.cellType = cellType;
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

        public String getCellType() {
            return cellType;
        }
    }

    private final ICellRestriction Host;

    @SideOnly(Side.CLIENT)
    private MEGuiTextField typesField;

    @SideOnly(Side.CLIENT)
    private MEGuiTextField amountField;

    @SideOnly(Side.CLIENT)
    private CellData cellData;

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
    public void setCellData(CellData newCellData) {
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
            String[] newData = cellRestriction.split(",", 7);
            this.cellData.setTotalBytes(Long.parseLong(newData[0]));
            this.cellData.setTotalTypes(Integer.parseInt(newData[1]));
            this.cellData.setPerType(Integer.parseInt(newData[2]));
            this.cellData.setPerByte(Integer.parseInt(newData[3]));
            this.cellData.setCellType(newData[6]);
            this.typesField.setMaxStringLength(cellData.getTotalTypes().toString().length());
            this.amountField.setMaxStringLength(
                    String.valueOf((cellData.getTotalBytes() - cellData.getPerType()) * cellData.getPerByte())
                            .length());
            this.typesField.setText(newData[4]);
            this.amountField.setText(newData[5]);
        }
        super.onUpdate(field, oldValue, newValue);
    }
}
