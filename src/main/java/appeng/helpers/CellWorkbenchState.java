package appeng.helpers;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.CopyMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.implementations.tiles.ICellWorkbench;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.IConfigManager;
import appeng.helpers.ICellRestriction.CellData;
import appeng.helpers.ICellRestriction.CellRestrictionData;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;

public class CellWorkbenchState {

    protected static final String CELL_TAG = "cell";
    protected static final String CONFIG_TAG = "config";

    protected final ICellWorkbench host;
    protected final IAEAppEngInventory inventoryOwner;
    protected final AppEngInternalInventory cell;
    protected final IAEStackInventory config;
    protected final ConfigManager manager;

    protected IInventory cachedUpgrades;
    protected IAEStackInventory cachedConfig;
    protected boolean updatingCell;
    protected long cellRestrictAmount;
    protected byte cellRestrictTypes;
    @Nullable
    protected IAEStackType<?> stackType;
    @Nullable
    protected IAEStackType<?> previousStackType;

    public CellWorkbenchState(ICellWorkbench host, IAEAppEngInventory inventoryOwner, IConfigManagerHost configOwner) {
        this.host = host;
        this.inventoryOwner = inventoryOwner;
        this.cell = new AppEngInternalInventory(inventoryOwner, 1);
        this.config = new IAEStackInventory(host, 63);
        this.manager = new ConfigManager(configOwner);
        this.manager.registerSetting(Settings.COPY_MODE, CopyMode.CLEAR_ON_REMOVE);
        this.cell.setEnableClientEvents(true);
    }

    public IInventory getCellInventory() {
        return this.cell;
    }

    public ICellWorkbenchItem getCell() {
        ItemStack stack = this.cell.getStackInSlot(0);
        return stack != null && stack.getItem() instanceof ICellWorkbenchItem cellWorkbenchItem ? cellWorkbenchItem
                : null;
    }

    public IInventory getCellUpgradeInventory() {
        if (this.cachedUpgrades == null) {
            ICellWorkbenchItem cellWorkbenchItem = this.getCell();
            ItemStack stack = this.cell.getStackInSlot(0);
            if (cellWorkbenchItem == null || stack == null) {
                return null;
            }
            this.cachedUpgrades = cellWorkbenchItem.getUpgradesInventory(stack);
        }
        return this.cachedUpgrades;
    }

    public int getInstalledUpgrades(Upgrades upgrade) {
        IInventory upgrades = this.getCellUpgradeInventory();
        if (upgrades == null) {
            return 0;
        }
        for (int slot = 0; slot < upgrades.getSizeInventory(); slot++) {
            ItemStack stack = upgrades.getStackInSlot(slot);
            if (stack != null && stack.getItem() instanceof IUpgradeModule upgradeModule
                    && upgradeModule.getType(stack) == upgrade) {
                return 1;
            }
        }
        return 0;
    }

    public IConfigManager getConfigManager() {
        return this.manager;
    }

    public void writeToNBT(NBTTagCompound data) {
        this.cell.writeToNBT(data, CELL_TAG);
        this.config.writeToNBT(data, CONFIG_TAG);
        this.manager.writeToNBT(data);
    }

    public void readFromNBT(NBTTagCompound data) {
        this.cell.readFromNBT(data, CELL_TAG);
        this.config.readFromNBT(data, CONFIG_TAG);
        this.manager.readFromNBT(data);
        this.updateStackType();
        this.previousStackType = this.stackType;
    }

    public void onChangeInventory(IInventory inventory, InvOperation operation) {
        if (operation == InvOperation.markDirty || inventory != this.cell || this.updatingCell) {
            return;
        }

        this.cachedUpgrades = null;
        this.cachedConfig = null;
        if (Platform.isClient()) {
            this.updateStackType();
            return;
        }

        this.updatingCell = true;
        try {
            ItemStack stack = this.cell.getStackInSlot(0);
            if (this.manager.getSetting(Settings.COPY_MODE) == CopyMode.KEEP_ON_REMOVE && stack != null
                    && stack.getItem() instanceof ICellRestriction cellRestriction) {
                cellRestriction.setCellRestriction(
                        stack,
                        new CellRestrictionData(this.cellRestrictTypes, this.cellRestrictAmount));
            }

            if (this.updateStackType() && this.stackType != this.previousStackType) {
                this.clearConfig();
            }
            this.previousStackType = this.stackType;

            IAEStackInventory cellConfig = this.getCellConfigInventory();
            if (cellConfig != null) {
                if (this.hasConfig(cellConfig)) {
                    this.copyConfig(cellConfig, this.config);
                } else {
                    this.copyConfig(this.config, cellConfig);
                    cellConfig.markDirty();
                }
            } else if (this.manager.getSetting(Settings.COPY_MODE) == CopyMode.CLEAR_ON_REMOVE) {
                this.clearConfig();
            }
        } finally {
            this.updatingCell = false;
        }
    }

    public void saveAEStackInv() {
        if (this.updatingCell) {
            return;
        }
        IAEStackInventory cellConfig = this.getCellConfigInventory();
        if (cellConfig != null) {
            this.copyConfig(this.config, cellConfig);
            cellConfig.markDirty();
        }
        this.inventoryOwner.saveChanges();
    }

    public IAEStackInventory getAEInventoryByName(StorageName name) {
        return this.config;
    }

    public String getFilter() {
        ItemStack stack = this.cell.getStackInSlot(0);
        return stack != null && stack.getItem() instanceof ICellWorkbenchItem cellWorkbenchItem
                ? cellWorkbenchItem.getOreFilter(stack)
                : "";
    }

    public void setFilter(String filter) {
        ItemStack stack = this.cell.getStackInSlot(0);
        if (stack != null && stack.getItem() instanceof ICellWorkbenchItem cellWorkbenchItem) {
            cellWorkbenchItem.setOreFilter(stack, filter);
        }
    }

    @Nullable
    public CellData getCellData() {
        ItemStack stack = this.cell.getStackInSlot(0);
        return stack != null && stack.getItem() instanceof ICellRestriction cellRestriction
                ? cellRestriction.getCellData(stack)
                : null;
    }

    @Nullable
    public CellRestrictionData getCellRestrictionData() {
        ItemStack stack = this.cell.getStackInSlot(0);
        return stack != null && stack.getItem() instanceof ICellRestriction cellRestriction
                ? cellRestriction.getCellRestrictionData(stack)
                : null;
    }

    public void setCellRestriction(CellRestrictionData restriction) {
        if (restriction.isReset() || this.manager.getSetting(Settings.COPY_MODE) == CopyMode.KEEP_ON_REMOVE) {
            this.cellRestrictTypes = restriction.restrictionTypes;
            this.cellRestrictAmount = restriction.restrictionAmount;
        }
        ItemStack stack = this.cell.getStackInSlot(0);
        if (stack != null && stack.getItem() instanceof ICellRestriction cellRestriction) {
            cellRestriction.setCellRestriction(stack, restriction);
        }
    }

    @Nullable
    public IAEStackType<?> getStackType() {
        return this.stackType;
    }

    protected IAEStackInventory getCellConfigInventory() {
        if (this.cachedConfig == null) {
            ICellWorkbenchItem cellWorkbenchItem = this.getCell();
            ItemStack stack = this.cell.getStackInSlot(0);
            if (cellWorkbenchItem == null || stack == null) {
                return null;
            }
            this.cachedConfig = cellWorkbenchItem.getConfigAEInventory(stack);
        }
        return this.cachedConfig;
    }

    protected boolean updateStackType() {
        ItemStack stack = this.cell.getStackInSlot(0);
        if (stack != null && stack.getItem() instanceof ICellWorkbenchItem cellWorkbenchItem) {
            this.stackType = cellWorkbenchItem.getStackType();
            return true;
        }
        this.stackType = null;
        return false;
    }

    protected boolean hasConfig(IAEStackInventory inventory) {
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            if (inventory.getAEStackInSlot(slot) != null) {
                return true;
            }
        }
        return false;
    }

    protected void clearConfig() {
        for (int slot = 0; slot < this.config.getSizeInventory(); slot++) {
            this.config.putAEStackInSlot(slot, null);
        }
    }

    protected void copyConfig(IAEStackInventory source, IAEStackInventory destination) {
        for (int slot = 0; slot < destination.getSizeInventory(); slot++) {
            destination.putAEStackInSlot(slot, source.getAEStackInSlot(slot));
        }
    }
}
