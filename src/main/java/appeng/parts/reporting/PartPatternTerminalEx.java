package appeng.parts.reporting;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.texture.CableBusTextures;
import appeng.core.sync.GuiBridge;
import appeng.helpers.PatternHelper;
import appeng.helpers.Reflected;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.BiggerAppEngInventory;
import appeng.tile.inventory.InvOperation;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class PartPatternTerminalEx extends AbstractPartTerminal {
    private static final CableBusTextures FRONT_BRIGHT_ICON = CableBusTextures.PartPatternTerm_Bright;
    private static final CableBusTextures FRONT_DARK_ICON = CableBusTextures.PartPatternTerm_Dark;
    private static final CableBusTextures FRONT_COLORED_ICON = CableBusTextures.PartPatternTerm_Colored;

    private final AppEngInternalInventory crafting = new BiggerAppEngInventory(this, 32);
    private final AppEngInternalInventory output = new BiggerAppEngInventory(this, 32);
    private final AppEngInternalInventory pattern = new AppEngInternalInventory(this, 2);

    private boolean substitute = false;
    private boolean beSubstitute = false;
    private boolean inverted = false;
    private int activePage = 0;

    @Reflected
    public PartPatternTerminalEx(final ItemStack is) {
        super(is);
    }

    @Override
    public void getDrops(final List<ItemStack> drops, final boolean wrenched) {
        for (final ItemStack is : this.pattern) {
            if (is != null) {
                drops.add(is);
            }
        }
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);

        this.pattern.readFromNBT(data, "pattern");
        this.output.readFromNBT(data, "outputList");
        this.crafting.readFromNBT(data, "craftingGrid");

        this.setSubstitution(data.getBoolean("substitute"));
        this.setCanBeSubstitution(data.getBoolean("beSubstitute"));
        this.setInverted(data.getBoolean("inverted"));
        this.setActivePage(data.getInteger("activePage"));
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);

        this.pattern.writeToNBT(data, "pattern");
        this.output.writeToNBT(data, "outputList");
        this.crafting.writeToNBT(data, "craftingGrid");

        data.setBoolean("substitute", this.substitute);
        data.setBoolean("beSubstitute", this.beSubstitute);
        data.setBoolean("inverted", this.inverted);
        data.setInteger("activePage", this.activePage);
    }

    @Override
    public GuiBridge getGui(final EntityPlayer p) {
        int x = (int) p.posX;
        int y = (int) p.posY;
        int z = (int) p.posZ;
        if (this.getHost().getTile() != null) {
            x = this.getTile().xCoord;
            y = this.getTile().yCoord;
            z = this.getTile().zCoord;
        }

        if (GuiBridge.GUI_PATTERN_TERMINAL_EX.hasPermissions(this.getHost().getTile(), x, y, z, this.getSide(), p)) {
            return GuiBridge.GUI_PATTERN_TERMINAL_EX;
        }
        return GuiBridge.GUI_ME;
    }

    @Override
    public void onChangeInventory(
            final IInventory inv,
            final int slot,
            final InvOperation mc,
            final ItemStack removedStack,
            final ItemStack newStack) {
        if (inv == this.pattern && slot == 1) {
            final ItemStack stack = this.pattern.getStackInSlot(1);

            if (stack != null && stack.getItem() instanceof ICraftingPatternItem) {
                final ICraftingPatternItem pattern = (ICraftingPatternItem) stack.getItem();
                final NBTTagCompound encodedValue = stack.getTagCompound();

                if (encodedValue != null) {
                    final ICraftingPatternDetails details = pattern.getPatternForItem(
                            stack, this.getHost().getTile().getWorldObj());
                    final boolean substitute = encodedValue.getBoolean("substitute");
                    final IAEItemStack[] inItems;
                    final IAEItemStack[] outItems;
                    int inputsCount = 0;
                    int outputCount = 0;

                    if (details == null) {
                        inItems = PatternHelper.loadIAEItemStackFromNBT(encodedValue.getTagList("in", 10), true, null);
                        outItems =
                                PatternHelper.loadIAEItemStackFromNBT(encodedValue.getTagList("out", 10), false, null);
                    } else {
                        inItems = details.getInputs();
                        outItems = details.getOutputs();
                    }

                    for (int x = 0; x < inItems.length; x++) {
                        if (inItems[x] != null) {
                            inputsCount++;
                        }
                    }

                    for (int x = 0; x < outItems.length; x++) {
                        if (outItems[x] != null) {
                            outputCount++;
                        }
                    }

                    this.setSubstitution(substitute);
                    this.setInverted(inputsCount <= 8 && outputCount >= 8);
                    this.setActivePage(0);

                    for (int x = 0; x < this.crafting.getSizeInventory(); x++) {
                        this.crafting.setInventorySlotContents(x, null);
                    }

                    for (int x = 0; x < this.output.getSizeInventory(); x++) {
                        this.output.setInventorySlotContents(x, null);
                    }

                    for (int x = 0; x < this.crafting.getSizeInventory() && x < inItems.length; x++) {
                        if (inItems[x] != null) {
                            this.crafting.setInventorySlotContents(x, inItems[x].getItemStack());
                        }
                    }

                    if (inverted) {
                        for (int x = 0; x < this.output.getSizeInventory() && x < outItems.length; x++) {
                            if (outItems[x] != null) {
                                this.output.setInventorySlotContents(x, outItems[x].getItemStack());
                            }
                        }
                    } else {
                        for (int x = 0; x < outItems.length && x < 8; x++) {
                            this.output.setInventorySlotContents(x >= 4 ? 12 + x : x, outItems[x].getItemStack());
                        }
                    }
                }
            }
        }

        this.getHost().markForSave();
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

    @Override
    public IInventory getInventoryByName(final String name) {
        if (name.equals("crafting")) {
            return this.crafting;
        }

        if (name.equals("output")) {
            return this.output;
        }

        if (name.equals("pattern")) {
            return this.pattern;
        }

        return super.getInventoryByName(name);
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

    public boolean isInverted() {
        return inverted;
    }

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
