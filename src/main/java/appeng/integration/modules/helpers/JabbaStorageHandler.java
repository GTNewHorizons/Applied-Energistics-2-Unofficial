package appeng.integration.modules.helpers;

import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IExternalStorageHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.integration.modules.Jabba;
import appeng.me.storage.MEMonitorIInventory;
import appeng.util.inv.IMEAdaptor;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

public class JabbaStorageHandler implements IExternalStorageHandler
{
    @Override
    public boolean canHandle( final TileEntity te, final ForgeDirection d, final StorageChannel chan, final BaseActionSource mySrc )
    {
        return chan == StorageChannel.ITEMS && Jabba.instance.isBarrel( te );
    }

    @Override
    public IMEInventory<IAEItemStack> getInventory( final TileEntity te, final ForgeDirection d, final StorageChannel chan, final BaseActionSource src )
    {
        if( chan == StorageChannel.ITEMS )
        {
            return new MEMonitorIInventory( new IMEAdaptor( Jabba.instance.getBarrel( te ), src ) );
        }

        return null;
    }
}
