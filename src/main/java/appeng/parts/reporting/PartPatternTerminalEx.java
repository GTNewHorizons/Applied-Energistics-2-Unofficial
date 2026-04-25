package appeng.parts.reporting;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.AEApi;
import appeng.api.parts.IPatternTerminalEx;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.core.sync.GuiBridge;
import appeng.helpers.Reflected;
import appeng.tile.inventory.IAEStackInventory;

public class PartPatternTerminalEx extends PartPatternTerminal implements IPatternTerminalEx {

    public static final int exPatternInputsWidth = 4;
    public static final int exPatternInputsHeigh = 4;
    public static final int exPatternInputsPages = 2;

    public static final int exPatternOutputsWidth = 4;
    public static final int exPatternOutputsHeigh = 4;
    public static final int exPatternOutputPages = 2;

    private boolean inverted = false;
    private int activePage = 0;

    @Reflected
    public PartPatternTerminalEx(final ItemStack is) {
        super(is);
        setCraftingRecipe(false);
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
    protected GuiBridge getPatternGui() {
        return GuiBridge.GUI_PATTERN_TERMINAL_EX;
    }

    @Override
    public void exPatternTerminalCall(IAEStack<?>[] in, IAEStack<?>[] out) {
        int inputsCount = 0;
        int outputCount = 0;

        for (IAEStack<?> inItem : in) {
            if (inItem != null) {
                inputsCount++;
            }
        }

        for (IAEStack<?> outItem : out) {
            if (outItem != null) {
                outputCount++;
            }
        }

        this.setInverted(inputsCount <= 8 && outputCount >= 8);
        this.setActivePage(0);
    }

    public boolean isInverted() {
        return inverted;
    }

    public void setInverted(boolean inverted) {
        this.inverted = inverted;

        if (inverted) {
            IAEStackInventory crafting = this.getAEInventoryByName(StorageName.CRAFTING_INPUT);
            for (int i = 0; i < crafting.getSizeInventory(); i++) {
                if (i >= getPatternInputsHeigh() * getPatternInputPages()) crafting.putAEStackInSlot(i, null);
            }
        } else {
            IAEStackInventory output = this.getAEInventoryByName(StorageName.CRAFTING_OUTPUT);
            for (int i = 0; i < output.getSizeInventory(); i++) {
                if (i >= getPatternOutputsHeigh() * getPatternOutputPages()) output.putAEStackInSlot(i, null);
            }
        }
    }

    public int getActivePage() {
        return this.activePage;
    }

    public void setActivePage(int activePage) {
        this.activePage = activePage;
    }

    @Override
    public void setCraftingRecipe(boolean craftingMode) {
        super.setCraftingRecipe(false);
    }

    @Override
    protected int getPatternInputsWidth() {
        return exPatternInputsWidth;
    }

    @Override
    protected int getPatternInputsHeigh() {
        return exPatternInputsHeigh;
    }

    @Override
    protected int getPatternInputPages() {
        return exPatternInputsPages;
    }

    @Override
    protected int getPatternOutputsWidth() {
        return exPatternOutputsWidth;
    }

    @Override
    protected int getPatternOutputsHeigh() {
        return exPatternOutputsHeigh;
    }

    @Override
    protected int getPatternOutputPages() {
        return exPatternOutputPages;
    }

    @Override
    public ItemStack getPrimaryGuiIcon() {
        return AEApi.instance().definitions().parts().patternTerminalEx().maybeStack(1).orNull();
    }
}
