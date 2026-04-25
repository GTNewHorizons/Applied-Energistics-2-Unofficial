package appeng.api.implementations.tiles;

import net.minecraft.inventory.IInventory;

import org.jetbrains.annotations.Nullable;

import appeng.api.implementations.IUpgradeableHost;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.data.IAEStackType;
import appeng.helpers.ICellRestriction;
import appeng.helpers.IOreFilterable;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.IIAEStackInventory;
import appeng.util.IConfigManagerHost;

public interface ICellWorkbench extends IUpgradeableHost, IAEAppEngInventory, IConfigManagerHost, IOreFilterable,
        ICellRestriction, IIAEStackInventory {

    ICellWorkbenchItem getCell();

    boolean isSame(ICellWorkbench cellWorkbench);

    IInventory getCellUpgradeInventory();

    @Nullable
    IAEStackType<?> getStackType();
}
