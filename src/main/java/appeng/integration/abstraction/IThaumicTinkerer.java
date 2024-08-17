package appeng.integration.abstraction;

import net.minecraft.tileentity.TileEntity;

public interface IThaumicTinkerer {

    boolean isTransvectorInterface(Object te);

    TileEntity getTile(Object te);
}
