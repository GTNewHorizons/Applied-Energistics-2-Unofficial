package appeng.integration.modules;

import net.minecraft.tileentity.TileEntity;

import appeng.helpers.Reflected;
import appeng.integration.IIntegrationModule;
import appeng.integration.IntegrationHelper;
import appeng.integration.abstraction.IThaumicTinkerer;
import thaumic.tinkerer.common.block.tile.transvector.TileTransvectorInterface;

public class ThaumicTinkerer implements IIntegrationModule, IThaumicTinkerer {

    @Reflected
    public static ThaumicTinkerer instance;

    @Reflected
    public ThaumicTinkerer() {
        IntegrationHelper.testClassExistence(this, TileTransvectorInterface.class);
    }

    @Override
    public void init() throws Throwable {}

    @Override
    public void postInit() {}

    @Override
    public boolean isTransvectorInterface(Object te) {
        return te instanceof TileTransvectorInterface;
    }

    @Override
    public TileEntity getTile(Object te) {
        if (te instanceof TileTransvectorInterface ti) {
            return ti.getTile();
        }
        return null;
    }
}
