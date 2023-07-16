package appeng.parts.reporting;

import java.util.List;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.AEApi;
import appeng.client.texture.CableBusTextures;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.BiggerAppEngInventory;
import appeng.tile.inventory.IAEAppEngInventory;

/**
 * Common Pattern terminal operations.
 */
public abstract class AbstractPartPatternTerm extends AbstractPartTerminal {

    protected static final CableBusTextures FRONT_BRIGHT_ICON = CableBusTextures.PartPatternTerm_Bright;
    protected static final CableBusTextures FRONT_DARK_ICON = CableBusTextures.PartPatternTerm_Dark;
    protected static final CableBusTextures FRONT_COLORED_ICON = CableBusTextures.PartPatternTerm_Colored;

    /* Inventories */
    protected final AppEngInternalInventory crafting;
    protected final AppEngInternalInventory output;
    protected final AppEngInternalInventory pattern = new AppEngInternalInventory(this, 2);
    protected final AppEngInternalInventory upgrades = new RefillerInventory(this);

    /* Pattern encoding modes */
    protected boolean substitute = false;
    protected boolean beSubstitute = false;

    /**
     * Init a general pattern table.
     * 
     * @param is         - part term stack
     * @param inputSize  - total crafting grid slot count
     * @param outputSize - total output slot count
     */
    public AbstractPartPatternTerm(ItemStack is, int inputSize, int outputSize) {
        super(is);
        this.crafting = new BiggerAppEngInventory(this, inputSize);
        this.output = new BiggerAppEngInventory(this, outputSize);
    }

    @Override
    public void getDrops(List<ItemStack> drops, boolean wrenched) {
        super.getDrops(drops, wrenched);

        for (final ItemStack is : this.pattern) {
            if (is != null) {
                drops.add(is);
            }
        }
        ItemStack u = upgrades.getStackInSlot(0);
        if (u != null) drops.add(u);
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        upgrades.readFromNBT(data, "upgrades");
        this.substitute = data.getBoolean("substitute");
        this.beSubstitute = data.getBoolean("beSubstitute");
        this.pattern.readFromNBT(data, "pattern");
        this.output.readFromNBT(data, "outputList");
        this.crafting.readFromNBT(data, "craftingGrid");
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        upgrades.writeToNBT(data, "upgrades");
        data.setBoolean("substitute", this.substitute);
        data.setBoolean("beSubstitute", this.beSubstitute);
        this.pattern.writeToNBT(data, "pattern");
        this.output.writeToNBT(data, "outputList");
        this.crafting.writeToNBT(data, "craftingGrid");
    }

    @Override
    public IInventory getInventoryByName(final String name) {
        return switch (name) {
            case "crafting" -> this.crafting;
            case "output" -> this.output;
            case "pattern" -> this.pattern;
            case "upgrades" -> this.upgrades;
            default -> super.getInventoryByName(name);
        };
    }

    @Override
    public CableBusTextures getFrontBright() {
        return FRONT_BRIGHT_ICON;
    }

    @Override
    public CableBusTextures getFrontColored() {
        return FRONT_COLORED_ICON;
    }

    @Override
    public CableBusTextures getFrontDark() {
        return FRONT_DARK_ICON;
    }

    public boolean isSubstitution() {
        return this.substitute;
    }

    public boolean canBeSubstitution() {
        return this.beSubstitute;
    }

    public void setSubstitution(boolean canSubstitute) {
        this.substitute = canSubstitute;
    }

    public void setCanBeSubstitution(boolean beSubstitute) {
        this.beSubstitute = beSubstitute;
    }

    public boolean hasRefillerUpgrade() {
        return upgrades.getStackInSlot(0) != null;
    }

    static class RefillerInventory extends AppEngInternalInventory {

        public RefillerInventory(final IAEAppEngInventory parent) {
            super(parent, 1, 1);
            setTileEntity(parent);
        }

        public boolean isItemValidForSlot(final int i, final ItemStack itemstack) {
            return i == 0 && getStackInSlot(0) == null
                    && AEApi.instance().definitions().materials().cardPatternRefiller().isSameAs(itemstack);
        }
    }

}
