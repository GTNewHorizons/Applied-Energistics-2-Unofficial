package appeng.parts.reporting;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.core.sync.GuiBridge;
import appeng.helpers.PatternHelper;
import appeng.helpers.Reflected;
import appeng.tile.inventory.InvOperation;

public class PartPatternTerminalEx extends AbstractPartPatternTerm {

    private boolean inverted = false;
    private int activePage = 0;

    @Reflected
    public PartPatternTerminalEx(final ItemStack is) {
        super(is, 32, 32);
    }

    @Override
    public void getDrops(final List<ItemStack> drops, final boolean wrenched) {
        super.getDrops(drops, wrenched);

        for (final ItemStack is : this.pattern) {
            if (is != null) {
                drops.add(is);
            }
        }
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.inverted = data.getBoolean("inverted");
        this.activePage = data.getInteger("activePage");
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
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
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc,
            final ItemStack removedStack, final ItemStack newStack) {
        if (inv == this.pattern && slot == 1) {
            final ItemStack stack = this.pattern.getStackInSlot(1);

            if (stack != null && stack.getItem() instanceof ICraftingPatternItem craftPattern) {
                final NBTTagCompound encodedValue = stack.getTagCompound();

                if (encodedValue != null) {
                    final ICraftingPatternDetails details = craftPattern
                            .getPatternForItem(stack, this.getHost().getTile().getWorldObj());
                    final boolean substitute = encodedValue.getBoolean("substitute");
                    final IAEItemStack[] inItems;
                    final IAEItemStack[] outItems;
                    int inputsCount = 0;
                    int outputCount = 0;

                    if (details == null) {
                        inItems = PatternHelper.loadIAEItemStackFromNBT(encodedValue.getTagList("in", 10), true, null);
                        outItems = PatternHelper
                                .loadIAEItemStackFromNBT(encodedValue.getTagList("out", 10), false, null);
                    } else {
                        inItems = details.getInputs();
                        outItems = details.getOutputs();
                    }

                    for (IAEItemStack inItem : inItems) {
                        if (inItem != null) {
                            inputsCount++;
                        }
                    }

                    for (IAEItemStack outItem : outItems) {
                        if (outItem != null) {
                            outputCount++;
                        }
                    }

                    this.substitute = substitute;
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
