package appeng.parts.reporting;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.helpers.Reflected;
import appeng.tile.inventory.AppEngInternalAEInventory;

public class PartPatternTerminalEx extends PartPatternTerminal {

    private int activePage = 0;

    @Reflected
    public PartPatternTerminalEx(final ItemStack is) {
        super(is);
        this.crafting = new AppEngInternalAEInventory(this, 32);
        this.output = new AppEngInternalAEInventory(this, 32);
        this.craftingMode = false;
        this.inverted = false;
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);

        this.setInverted(data.getBoolean("inverted"));
        this.setActivePage(data.getInteger("activePage"));
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);

        data.setBoolean("inverted", this.inverted);
        data.setInteger("activePage", this.activePage);
    }

    @Override
    public boolean isCraftingRecipe() {
        return false;
    }

    @Override
    public void setCraftingRecipe(boolean craftingMode) {}

    public void setInverted(boolean inverted) {
        this.inverted = inverted;
    }

    public int getActivePage() {
        return this.activePage;
    }

    public void setActivePage(int activePage) {
        this.activePage = activePage;
    }
}
