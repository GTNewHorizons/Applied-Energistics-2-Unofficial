package appeng.items.contents;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import appeng.api.features.IWirelessTermHandler;
import appeng.api.parts.IPatternTerminalEx;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.IConfigManager;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.IIAEStackInventory;
import appeng.tile.inventory.InvOperation;

public class WirelessPatternTerminalGuiObject extends WirelessTerminalGuiObject
        implements IIAEStackInventory, IPatternTerminalEx {

    int craftingInvSize;
    int outputInvSize;
    IAEStackInventory crafting;
    IAEStackInventory output;

    private final AppEngInternalInventory pattern = new AppEngInternalInventory(this, 2);

    private boolean craftingMode;
    private boolean substitute = false;
    private boolean beSubstitute = false;
    private final String nbtPrefix;
    private final int mode;

    private boolean inverted;
    private int activePage;

    public WirelessPatternTerminalGuiObject(IWirelessTermHandler wh, ItemStack is, EntityPlayer ep, World w, int x,
            int y, int z) {
        super(wh, is, ep, w, x, y, z);
        this.mode = y != Integer.MIN_VALUE ? y : getMode();
        this.nbtPrefix = mode == 1 ? "pattern" : "pattern_ex"; // ...
        this.craftingMode = mode == 1;
    }

    public void setInventorySize(int inputs, int outputs) {
        craftingInvSize = inputs;
        outputInvSize = outputs;
    }

    @Override
    public void readInventory() {
        crafting = new IAEStackInventory(this, craftingInvSize, StorageName.CRAFTING_INPUT);
        output = new IAEStackInventory(this, outputInvSize, StorageName.CRAFTING_OUTPUT);

        final NBTTagCompound tag = getItemStack().getTagCompound().getCompoundTag(this.nbtPrefix);
        if (mode == 1) this.craftingMode = tag.getBoolean("crafting");
        this.substitute = tag.getBoolean("substitute");
        this.beSubstitute = tag.getBoolean("beSubstitute");
        this.inverted = tag.getBoolean("inverted");
        this.activePage = tag.getInteger("activePage");
        this.pattern.readFromNBT(tag, "pattern");
        this.output.readFromNBT(tag, "outputList");
        this.crafting.readFromNBT(tag, "craftingGrid");
    }

    @Override
    public void writeInventory() {
        final NBTTagCompound tag = new NBTTagCompound();
        if (mode == 1) tag.setBoolean("crafting", this.craftingMode);
        tag.setBoolean("substitute", this.substitute);
        tag.setBoolean("beSubstitute", this.beSubstitute);

        if (this.mode == 2) {
            tag.setBoolean("inverted", this.inverted);
            tag.setInteger("activePage", this.activePage);
        }

        this.pattern.writeToNBT(tag, "pattern");
        this.output.writeToNBT(tag, "outputList");
        this.crafting.writeToNBT(tag, "craftingGrid");
        getItemStack().getTagCompound().setTag(this.nbtPrefix, tag);
    }

    @Override
    public IAEStackInventory getAEInventoryByName(StorageName name) {
        switch (name) {
            case CRAFTING_INPUT -> {
                return this.crafting;
            }
            case CRAFTING_OUTPUT -> {
                return this.output;
            }
        }
        return null;
    }

    @Override
    public boolean isInverted() {
        return inverted;
    }

    @Override
    public void setInverted(boolean inverted) {
        this.inverted = inverted;
        writeInventory();
    }

    @Override
    public int getActivePage() {
        return this.activePage;
    }

    @Override
    public void setActivePage(int activePage) {
        this.activePage = activePage;
        writeInventory();
    }

    @Override
    public boolean isCraftingRecipe() {
        return this.craftingMode;
    }

    @Override
    public void setCraftingRecipe(boolean craftingMode) {
        this.craftingMode = craftingMode;
        writeInventory();
    }

    @Override
    public boolean isSubstitution() {
        return substitute;
    }

    @Override
    public boolean canBeSubstitution() {
        return this.beSubstitute;
    }

    @Override
    public void setSubstitution(boolean canSubstitute) {
        this.substitute = canSubstitute;
        writeInventory();
    }

    @Override
    public void setCanBeSubstitution(boolean beSubstitute) {
        this.beSubstitute = beSubstitute;
    }

    @Override
    public IInventory getInventoryByName(final String name) {
        if (name.equals("pattern")) {
            return this.pattern;
        }
        return null;
    }

    @Override
    public void saveAEStackInv() {
        writeInventory();
    }

    @Override
    public void exPatternTerminalCall(IAEStack<?>[] in, IAEStack<?>[] out) {}

    @Override
    public void saveChanges() {}

    @Override
    public void onChangeInventory(IInventory inv, int slot, InvOperation mc, ItemStack removedStack,
            ItemStack newStack) {
        if (inv == pattern && slot == 1) {
            loadPatternFromItem(this.pattern.getStackInSlot(1), this.myPlayer.worldObj, this.crafting, this.output);
            writeInventory();
        }
        if (mc != InvOperation.markDirty) writeInventory(); // couz spam
    }

    @Override
    public void updateSetting(IConfigManager manager, Enum settingName, Enum newValue) {}
}
