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

import appeng.api.AEApi;
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
        final NBTTagCompound nbt = is.getTagCompound();

        if (nbt == null || nbt.getBoolean("InvalidPattern")) {
            throw new IllegalArgumentException("No pattern here!");
        }

        this.canSubstitute = nbt.getBoolean("substitute");
        this.canBeSubstitute = nbt.getBoolean("beSubstitute");
        this.patternItem = is;
        if (nbt.hasKey("author")) {
            final ItemStack forComparison = this.patternItem.copy();
            forComparison.stackTagCompound.removeTag("author");
            this.pattern = AEItemStack.create(forComparison);
        } else {
            this.pattern = AEItemStack.create(is);
        }

        final NBTTagList inTag = nbt.getTagList("in", 10);
        final NBTTagList outTag = nbt.getTagList("out", 10);

        final List<IAEItemStack> inLegacy = new ArrayList<>();
        final List<IAEItemStack> outLegacy = new ArrayList<>();

        final List<IAEStack<?>> in = new ArrayList<>();
        final List<IAEStack<?>> out = new ArrayList<>();

        for (int x = 0; x < inTag.tagCount(); x++) {
            final NBTTagCompound tag = inTag.getCompoundTagAt(x);
            final IAEStack<?> aeStack = readStackNBT(tag, true);

            if (aeStack == null && !tag.hasNoTags()) {
                nbt.setBoolean("InvalidPattern", true);
                throw new IllegalStateException("No pattern here!");
            }

            inLegacy.add(stackConvert(aeStack));
            in.add(aeStack);
        }

        for (int x = 0; x < outTag.tagCount(); x++) {
            final NBTTagCompound tag = outTag.getCompoundTagAt(x);
            final IAEStack<?> aeStack = readStackNBT(tag, true);

            if (aeStack == null && !tag.hasNoTags()) {
                nbt.setBoolean("InvalidPattern", true);
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

        if (condensedAEInputs.length == 0 || condensedAEOutputs.length == 0) {
            nbt.setBoolean("InvalidPattern", true);
            throw new IllegalStateException("No pattern here!");
        }
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

    public static IAEStack<?>[] loadIAEStackFromNBT(final NBTTagList tags, boolean saveOrder,
            final ItemStack unknownItem) {
        final List<IAEStack<?>> items = new ArrayList<>();

        for (int x = 0; x < tags.tagCount(); x++) {
            final NBTTagCompound tag = tags.getCompoundTagAt(x);

            if (tag.hasNoTags()) {
                continue;
            }

            IAEStack<?> gs = readStackNBT(tag, true);

            if (gs == null && unknownItem != null) {
                gs = AEApi.instance().storage().createItemStack(unknownItem);
            }

            if (gs != null || saveOrder) {
                items.add(gs);
            }
        }

        return items.toArray(new IAEStack<?>[0]);
    }
}
