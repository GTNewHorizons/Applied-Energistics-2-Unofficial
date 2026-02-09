package appeng.core.features.registries.entries;

import static appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.IFluidHandler;

import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IExternalStorageHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEStackType;
import appeng.me.storage.MEMonitorIFluidHandler;

// This handler is too generic, so it will overlap other mods' fluid handler.
// Make sure it is executed at last.
public class AEFluidTankHandler implements IExternalStorageHandler {

    public static final AEFluidTankHandler INSTANCE = new AEFluidTankHandler();

    @Override
    public boolean canHandle(TileEntity te, ForgeDirection d, IAEStackType<?> type, BaseActionSource mySrc) {
        return type == FLUID_STACK_TYPE && te instanceof IFluidHandler;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public IMEInventory getInventory(TileEntity te, ForgeDirection d, IAEStackType<?> type, BaseActionSource src) {
        if (type == FLUID_STACK_TYPE) {
            return new MEMonitorIFluidHandler((IFluidHandler) te, d);
        }
        return null;
    }
}
