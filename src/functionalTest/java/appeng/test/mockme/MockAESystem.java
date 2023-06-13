package appeng.test.mockme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.ICellProvider;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingJobV2;
import appeng.helpers.PatternHelper;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cache.GridStorageCache;
import appeng.me.storage.MEPassThrough;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;

public class MockAESystem implements ICellProvider {

    public final World world;
    public final MockGrid grid = new MockGrid();
    public final BaseActionSource dummyActionSource = new BaseActionSource();
    public final CraftingGridCache cgCache;
    public final GridStorageCache sgCache;
    private boolean dirtyPatterns = false;

    public MockAESystem(World world) {
        this.world = world;
        this.cgCache = grid.getCache(ICraftingGrid.class);
        this.sgCache = grid.getCache(IStorageGrid.class);
        sgCache.registerCellProvider(this);
    }

    public MockAESystem addStoredItem(ItemStack stack) {
        final IAEItemStack aeStack = AEItemStack.create(stack);
        this.itemStorage.injectItems(aeStack, Actionable.MODULATE, dummyActionSource);
        this.sgCache.postAlterationOfStoredItems(
                StorageChannel.ITEMS,
                Collections.singletonList(aeStack),
                dummyActionSource);
        return this;
    }

    public CraftingJobV2 makeCraftingJob(ItemStack request) {
        if (dirtyPatterns) {
            dirtyPatterns = false;
            this.cgCache.setMockPatternsFromMethods();
        }
        return new CraftingJobV2(world, grid, dummyActionSource, AEItemStack.create(request), null);
    }

    public PatternBuilder newProcessingPattern() {
        dirtyPatterns = true;
        return new PatternBuilder(false);
    }

    public PatternBuilder newCraftingPattern() {
        dirtyPatterns = true;
        return new PatternBuilder(true);
    }

    public class PatternBuilder {

        public final boolean isCrafting;
        public final List<ItemStack> inputs = new ArrayList<>(9);
        public final List<ItemStack> outputs = new ArrayList<>(9);
        public boolean canUseSubstitutes = false;
        public boolean canBeSubstitute = false;

        private PatternBuilder(boolean isCrafting) {
            this.isCrafting = isCrafting;
        }

        public PatternBuilder allowUsingSubstitutes() {
            this.canUseSubstitutes = true;
            return this;
        }

        public PatternBuilder allowUsingSubstitutes(boolean allow) {
            this.canUseSubstitutes = allow;
            return this;
        }

        public PatternBuilder allowBeingASubstitute() {
            this.canBeSubstitute = true;
            return this;
        }

        public PatternBuilder allowBeingASubstitute(boolean allow) {
            this.canBeSubstitute = allow;
            return this;
        }

        public PatternBuilder addInput(ItemStack stack) {
            inputs.add(stack);
            return this;
        }

        public PatternBuilder addOutput(ItemStack stack) {
            outputs.add(stack);
            return this;
        }

        public void buildAndAdd() {
            final ItemStack encodedPattern = AEApi.instance().definitions().items().encodedPattern().maybeStack(1)
                    .get();
            final NBTTagCompound patternTags = new NBTTagCompound();
            patternTags.setBoolean("crafting", isCrafting);
            patternTags.setBoolean("substitute", canUseSubstitutes);
            patternTags.setBoolean("beSubstitute", canBeSubstitute);
            patternTags.setBoolean("crafting", isCrafting);
            final NBTTagList ins = new NBTTagList();
            final NBTTagList outs = new NBTTagList();
            for (ItemStack input : inputs) {
                NBTTagCompound nbt = new NBTTagCompound();
                if (input != null) {
                    Platform.writeItemStackToNBT(input, nbt);
                }
                ins.appendTag(nbt);
            }
            patternTags.setTag("in", ins);
            for (ItemStack output : outputs) {
                NBTTagCompound nbt = new NBTTagCompound();
                if (output != null) {
                    Platform.writeItemStackToNBT(output, nbt);
                }
                outs.appendTag(nbt);
            }
            patternTags.setTag("out", outs);
            encodedPattern.setTagCompound(patternTags);
            PatternHelper helper = new PatternHelper(encodedPattern, world);
            cgCache.addCraftingOption(new MockCraftingMedium(), helper);
        }
    }

    // Simulated inventories
    private final MECraftingInventory itemStorage = new MECraftingInventory();
    private final IMEInventoryHandler<IAEItemStack> storageHandler = new MEPassThrough<>(
            itemStorage,
            StorageChannel.ITEMS);

    @Override
    public List<IMEInventoryHandler> getCellArray(StorageChannel channel) {
        return Collections.singletonList(storageHandler);
    }

    @Override
    public int getPriority() {
        return 0;
    }
}
