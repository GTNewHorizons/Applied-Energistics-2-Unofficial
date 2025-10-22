package appeng.helpers;

import net.minecraft.inventory.IInventory;

import appeng.api.implementations.IUpgradeableHost;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.util.IConfigManagerHost;

public interface ICellWorkbench
        extends IUpgradeableHost, IAEAppEngInventory, IConfigManagerHost, IOreFilterable, ICellRestriction {

    ICellWorkbenchItem getCell();

    boolean isSame(ICellWorkbench cellWorkbench);

    IInventory getCellUpgradeInventory();
}
