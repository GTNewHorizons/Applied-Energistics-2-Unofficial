package appeng.items.contents;

import java.util.UUID;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Upgrades;
import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.implementations.tiles.ICellWorkbench;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.IConfigManager;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.helpers.CellWorkbenchState;
import appeng.helpers.ICellRestriction.CellData;
import appeng.helpers.ICellRestriction.CellRestrictionData;
import appeng.helpers.IPrimaryGuiIconProvider;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.Platform;

public class PortableCellWorkbenchHost
        implements ICellWorkbench, IGuiItemObject, IInventorySlotAware, IPrimaryGuiIconProvider, IAEAppEngInventory {

    protected static final String ID_TAG = "PortableCellWorkbenchId";

    protected final IInventory playerInventory;
    protected final Item workbenchItem;
    protected final int inventorySlot;
    protected final int inventorySlotIndex;
    protected final String portableId;
    protected final CellWorkbenchState state;

    public PortableCellWorkbenchHost(IInventory playerInventory, Item workbenchItem, int inventorySlot) {
        this(playerInventory, workbenchItem, inventorySlot, inventorySlot);
    }

    public PortableCellWorkbenchHost(IInventory playerInventory, Item workbenchItem, int inventorySlot,
            int inventorySlotIndex) {
        this.playerInventory = playerInventory;
        this.workbenchItem = workbenchItem;
        this.inventorySlot = inventorySlot;
        this.inventorySlotIndex = inventorySlotIndex;

        ItemStack stack = this.getItemStack();
        if (stack == null || stack.getItem() != workbenchItem) {
            throw new IllegalArgumentException("Portable Cell Workbench must be opened from its inventory slot");
        }

        this.portableId = this.ensurePortableId(stack);
        this.state = new CellWorkbenchState(this, this, this);
        if (stack.hasTagCompound()) {
            this.state.readFromNBT(stack.getTagCompound());
        }
    }

    @Override
    public ItemStack getItemStack() {
        return this.playerInventory.getStackInSlot(this.inventorySlot);
    }

    @Override
    public int getInventorySlot() {
        return this.inventorySlotIndex;
    }

    @Override
    public IInventory getInventoryByName(String name) {
        return "cell".equals(name) ? this.state.getCellInventory() : null;
    }

    @Override
    public ICellWorkbenchItem getCell() {
        return this.state.getCell();
    }

    @Override
    public IInventory getCellUpgradeInventory() {
        return this.state.getCellUpgradeInventory();
    }

    @Override
    public int getInstalledUpgrades(Upgrades upgrade) {
        return this.state.getInstalledUpgrades(upgrade);
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.state.getConfigManager();
    }

    @Override
    public void updateSetting(IConfigManager manager, Enum settingName, Enum newValue) {
        this.saveChanges();
    }

    @Override
    public void saveChanges() {
        if (Platform.isClient()) {
            return;
        }
        ItemStack stack = this.getItemStack();
        if (stack == null || stack.getItem() != this.workbenchItem) {
            return;
        }
        NBTTagCompound data = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        this.state.writeToNBT(data);
        stack.setTagCompound(data);
        this.playerInventory.markDirty();
    }

    @Override
    public void onChangeInventory(IInventory inventory, int slot, InvOperation operation, ItemStack removedStack,
            ItemStack newStack) {
        this.state.onChangeInventory(inventory, operation);
        if (operation == InvOperation.markDirty) {
            this.saveChanges();
        }
    }

    @Override
    public void saveAEStackInv() {
        this.state.saveAEStackInv();
    }

    @Override
    public IAEStackInventory getAEInventoryByName(StorageName name) {
        return this.state.getAEInventoryByName(name);
    }

    @Override
    public String getFilter() {
        return this.state.getFilter();
    }

    @Override
    public void setFilter(String filter) {
        this.state.setFilter(filter);
        this.saveChanges();
    }

    @Override
    @Nullable
    public CellData getCellData(ItemStack stack) {
        return this.state.getCellData();
    }

    @Override
    @Nullable
    public CellRestrictionData getCellRestrictionData(ItemStack stack) {
        return this.state.getCellRestrictionData();
    }

    @Override
    public void setCellRestriction(ItemStack stack, CellRestrictionData restriction) {
        this.state.setCellRestriction(restriction);
        this.saveChanges();
    }

    @Override
    public boolean isSame(ICellWorkbench cellWorkbench) {
        return cellWorkbench == this && this.isValid();
    }

    @Override
    @Nullable
    public IAEStackType<?> getStackType() {
        return this.state.getStackType();
    }

    @Override
    public TileEntity getTile() {
        return null;
    }

    @Override
    public ItemStack getPrimaryGuiIcon() {
        return new ItemStack(this.workbenchItem);
    }

    public boolean isValid() {
        ItemStack stack = this.getItemStack();
        return stack != null && stack.getItem() == this.workbenchItem
                && this.portableId.equals(this.getPortableId(stack));
    }

    protected String ensurePortableId(ItemStack stack) {
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey(ID_TAG)) {
            return stack.getTagCompound().getString(ID_TAG);
        }
        if (Platform.isClient()) {
            return "";
        }
        NBTTagCompound data = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        data.setString(ID_TAG, UUID.randomUUID().toString());
        stack.setTagCompound(data);
        this.playerInventory.markDirty();
        return data.getString(ID_TAG);
    }

    protected String getPortableId(ItemStack stack) {
        return stack.hasTagCompound() ? stack.getTagCompound().getString(ID_TAG) : "";
    }
}
