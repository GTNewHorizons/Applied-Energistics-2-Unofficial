package appeng.container.implementations;

import java.util.Objects;

import net.minecraft.entity.player.InventoryPlayer;

import appeng.container.ContainerSubGui;
import appeng.container.sync.SyncCodecs;
import appeng.container.sync.SyncRegistrar;
import appeng.container.sync.handlers.ObjectSyncHandler;
import appeng.helpers.ICellRestriction;
import appeng.helpers.ICellRestriction.CellData;
import appeng.helpers.ICellRestriction.CellRestrictionData;
import appeng.util.Platform;

public class ContainerCellRestriction extends ContainerSubGui {

    private final ICellRestriction Host;
    public final ObjectSyncHandler<CellData> cellDataSync;
    public final ObjectSyncHandler<CellRestrictionData> cellRestrictionDataSync;
    public boolean updated = false;

    public ContainerCellRestriction(final InventoryPlayer ip, final ICellRestriction te) {
        super(ip, te);
        this.Host = te;
        final SyncRegistrar sync = this.syncRegistrar();
        this.cellDataSync = sync.object(
                "cellData",
                SyncCodecs.packetWritable(CellData.class, CellData::new, CellData::copy, Objects::equals),
                this.Host.getCellData(null)).onClientChange((oldValue, newValue) -> { this.updated = true; });
        this.cellRestrictionDataSync = sync
                .object(
                        "cellRestrictionData",
                        SyncCodecs.packetWritable(
                                CellRestrictionData.class,
                                CellRestrictionData::new,
                                CellRestrictionData::copy,
                                Objects::equals),
                        this.Host.getCellRestrictionData(null))
                .onServerChange((oldValue, newValue) -> { this.Host.setCellRestriction(null, newValue); })
                .onClientChange((oldValue, newValue) -> { this.updated = true; });
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) {
            this.cellDataSync.set(this.Host.getCellData(null));
            this.cellRestrictionDataSync.set(this.Host.getCellRestrictionData(null));
        }
        super.detectAndSendChanges();
    }
}
