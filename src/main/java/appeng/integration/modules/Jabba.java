package appeng.integration.modules;

import appeng.api.AEApi;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.Reflected;
import appeng.integration.IIntegrationModule;
import appeng.integration.IntegrationHelper;
import appeng.integration.modules.helpers.JabbaBarrel;
import appeng.integration.modules.helpers.JabbaStorageHandler;
import mcp.mobius.betterbarrels.common.blocks.TileEntityBarrel;
import net.minecraft.tileentity.TileEntity;

public class Jabba implements IIntegrationModule {
    @Reflected
    public static Jabba instance;
    @Reflected
    public Jabba()
    {
        IntegrationHelper.testClassExistence( this, mcp.mobius.betterbarrels.common.blocks.TileEntityBarrel.class );
    }
    public boolean isBarrel( final TileEntity te )
    {
        return te instanceof TileEntityBarrel;
    }

    public IMEInventory<IAEItemStack> getBarrel( final TileEntity te )
    {
        return new JabbaBarrel( te );
    }

    @Override
    public void init()
    {
    }

    @Override
    public void postInit()
    {
        AEApi.instance().registries().externalStorage().addExternalStorageInterface( new JabbaStorageHandler() );
    }
}
