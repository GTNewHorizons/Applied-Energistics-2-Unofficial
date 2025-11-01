package appeng.core.features.registries.entries;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.IFluidHandler;

import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IExternalStorageHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.StorageChannel;
import appeng.me.storage.MEMonitorIFluidHandler;

// This handler is too generic, so it will overlap other mods' fluid handler.
// Make sure it is executed at last.
public class AEFluidTankHandler implements IExternalStorageHandler {

    public static final AEFluidTankHandler INSTANCE = new AEFluidTankHandler();

    @Override
    public boolean canHandle(TileEntity te, ForgeDirection d, StorageChannel channel, BaseActionSource mySrc) {
        return channel == StorageChannel.FLUIDS && te instanceof IFluidHandler;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public IMEInventory getInventory(TileEntity te, ForgeDirection d, StorageChannel channel, BaseActionSource src) {
        if (channel == StorageChannel.FLUIDS) {
            return new MEMonitorIFluidHandler((IFluidHandler) te, d);
        }
        return null;
    }
}
