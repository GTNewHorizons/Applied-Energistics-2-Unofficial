package appeng.api.features;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.IGridHost;
import appeng.api.util.DimensionalCoord;
import appeng.helpers.ICustomNameObject;

public interface ILevelViewable extends IGridHost, ICustomNameObject {

    DimensionalCoord getLocation();

    TileEntity getTile();

    ForgeDirection getSide();

    /**
     * Number of rows to expect. This is used with {@link #rowSize()} to determine how to render the slots.
     */
    default int rows() {
        return 1;
    };

    /**
     * Number of slots per row.
     */
    default int rowSize() {
        return 1;
    };

    LevelItemInfo[] getLevelItemInfoList();

}
