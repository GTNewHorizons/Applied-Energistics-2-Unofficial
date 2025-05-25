package appeng.helpers;

import static appeng.helpers.PatternHelper.convertToCondensedAEList;
import static appeng.helpers.PatternHelper.convertToCondensedList;
import static appeng.util.Platform.readStackNBT;
import static appeng.util.Platform.stackConvert;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.ItemSorters;
import appeng.util.item.AEItemStack;

public class UltimatePatternHelper implements ICraftingPatternDetails, Comparable<UltimatePatternHelper> {

    private final ItemStack patternItem;
    private final IAEItemStack pattern;
    private final boolean canSubstitute;
    private final boolean canBeSubstitute;
    private int priority = 0;

    private final IAEItemStack[] condensedInputs;
    private final IAEItemStack[] condensedOutputs;
    private final IAEItemStack[] inputs;
    private final IAEItemStack[] outputs;

    private final IAEStack<?>[] condensedAEInputs;
    private final IAEStack<?>[] condensedAEOutputs;
    private final IAEStack<?>[] aeInputs;
    private final IAEStack<?>[] aeOutputs;

    public UltimatePatternHelper(final ItemStack is) {
        if (is.hasTagCompound()) {
            final NBTTagCompound encodedValue = is.getTagCompound();

            this.canSubstitute = encodedValue.getBoolean("substitute");
            this.canBeSubstitute = encodedValue.getBoolean("beSubstitute");
            this.patternItem = is;
            if (encodedValue.hasKey("author")) {
                final ItemStack forComparison = this.patternItem.copy();
                forComparison.stackTagCompound.removeTag("author");
                this.pattern = AEItemStack.create(forComparison);
            } else {
                this.pattern = AEItemStack.create(is);
            }

            final NBTTagList inTag = encodedValue.getTagList("in", 10);
            final NBTTagList outTag = encodedValue.getTagList("out", 10);

            final List<IAEItemStack> inLegacy = new ArrayList<>();
            final List<IAEItemStack> outLegacy = new ArrayList<>();

            final List<IAEStack<?>> in = new ArrayList<>();
            final List<IAEStack<?>> out = new ArrayList<>();

            for (int x = 0; x < inTag.tagCount(); x++) {
                final NBTTagCompound tag = inTag.getCompoundTagAt(x);
                final IAEStack<?> aeStack = readStackNBT(tag);

                if (aeStack == null && !tag.hasNoTags()) {
                    throw new IllegalStateException("No pattern here!");
                }

                inLegacy.add(stackConvert(aeStack));
                in.add(aeStack);
            }

            for (int x = 0; x < outTag.tagCount(); x++) {
                final NBTTagCompound tag = outTag.getCompoundTagAt(x);
                final IAEStack<?> aeStack = readStackNBT(tag);

                if (aeStack == null && !tag.hasNoTags()) {
                    throw new IllegalStateException("No pattern here!");
                }

                outLegacy.add(stackConvert(aeStack));
                out.add(aeStack);
            }

            inputs = inLegacy.toArray(new IAEItemStack[0]);
            outputs = outLegacy.toArray(new IAEItemStack[0]);

            condensedInputs = convertToCondensedList(inputs);
            condensedOutputs = convertToCondensedList(outputs);

            aeInputs = in.toArray(new IAEStack<?>[0]);
            aeOutputs = out.toArray(new IAEStack<?>[0]);

            condensedAEInputs = convertToCondensedAEList(aeInputs);
            condensedAEOutputs = convertToCondensedAEList(aeOutputs);

            if (condensedAEInputs.length == 0 || condensedAEOutputs.length == 0)
                throw new IllegalStateException("No pattern here!");
        } else throw new IllegalArgumentException("No pattern here!");
    }

    @Override
    public ItemStack getPattern() {
        return this.patternItem;
    }

    @Override
    public boolean isCraftable() {
        return false;
    }

    @Override
    public IAEItemStack[] getInputs() {
        return this.inputs;
    }

    @Override
    public IAEStack<?>[] getAEInputs() {
        return aeInputs;
    }

    @Override
    public IAEItemStack[] getCondensedInputs() {
        return this.condensedInputs;
    }

    @Override
    public IAEStack<?>[] getCondensedAEInputs() {
        return condensedAEInputs;
    }

    @Override
    public IAEItemStack[] getCondensedOutputs() {
        return this.condensedOutputs;
    }

    @Override
    public IAEStack<?>[] getCondensedAEOutputs() {
        return condensedAEOutputs;
    }

    @Override
    public IAEItemStack[] getOutputs() {
        return this.outputs;
    }

    @Override
    public IAEStack<?>[] getAEOutputs() {
        return aeOutputs;
    }

    @Override
    public boolean canSubstitute() {
        return this.canSubstitute;
    }

    @Override
    public boolean canBeSubstitute() {
        return this.canBeSubstitute;
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void setPriority(final int priority) {
        this.priority = priority;
    }

    @Override
    public int compareTo(final UltimatePatternHelper o) {
        return ItemSorters.compareInt(o.priority, this.priority);
    }

    @Override
    public int hashCode() {
        return this.pattern.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null || this.getClass() != obj.getClass()) {
            return false;
        }

        final UltimatePatternHelper other = (UltimatePatternHelper) obj;

        if (this.pattern != null && other.pattern != null) {
            return this.pattern.equals(other.pattern);
        }
        return false;
    }

    @Override
    public ItemStack getOutput(final InventoryCrafting craftingInv, final World w) {
        throw new IllegalStateException("Not a crafting recipe!");
    }

    @Override
    public synchronized boolean isValidItemForSlot(final int slotIndex, final IAEStack<?> i, final World w) {
        throw new IllegalStateException("Only crafting recipes supported.");
    }

    @Override
    public synchronized boolean isValidItemForSlot(final int slotIndex, final ItemStack i, final World w) {
        throw new IllegalStateException("Only crafting recipes supported.");
    }
}
