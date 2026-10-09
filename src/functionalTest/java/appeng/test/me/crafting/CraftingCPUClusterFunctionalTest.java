package appeng.test.me.crafting;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;

import net.minecraft.init.Blocks;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.WorldCoord;
import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import appeng.helpers.PatternHelper;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.test.mockme.MockGrid;
import appeng.tile.crafting.TileCraftingTile;
import appeng.util.inv.MEInventoryCrafting;
import appeng.util.item.AEItemStack;

public class CraftingCPUClusterFunctionalTest {

    private MockGrid grid;
    private CraftingGridCache cache;
    private CountingCPU cpu;
    private CountingMedium medium;
    private ICraftingPatternDetails pattern;
    private IEnergyGrid energy;
    private double energyUsed;

    @BeforeEach
    void setUp() {
        grid = new MockGrid();
        cache = grid.getCache(ICraftingGrid.class);
        cpu = new CountingCPU();
        medium = new CountingMedium();
        pattern = processingPattern();
        cache.addCraftingOption(medium, pattern);
        energy = (IEnergyGrid) Proxy.newProxyInstance(
                IEnergyGrid.class.getClassLoader(),
                new Class<?>[] { IEnergyGrid.class },
                (proxy, method, args) -> {
                    if (!method.getName().equals("extractAEPower")) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    final double amount = (Double) args[0];
                    if (args[1] == Actionable.MODULATE) energyUsed += amount;
                    return amount;
                });
    }

    @AfterEach
    void tearDown() {
        cpu.destroy();
        grid.rootNode.destroy();
    }

    @Test
    void repeatedDispatchReusesInputsAndMarksDirtyOnce() {
        cpu.prepare(pattern, 64, 128);

        tick();

        assertAll(
                () -> assertEquals(64, medium.pushes),
                () -> assertEquals(512, medium.consumed),
                () -> assertEquals(512, energyUsed),
                () -> assertEquals(0, cpu.storedInputs()),
                () -> assertEquals(512, cpu.expectedOutputs()),
                () -> assertEquals(64, cpu.usedOperations()),
                () -> assertEquals(1, cpu.condensedExpansions),
                () -> assertEquals(1, cpu.slotExpansions),
                () -> assertEquals(1, cpu.core.dirtyNotifications));
        for (IAEStack<?> input : pattern.getAEInputs()) {
            assertEquals(4, input.getStackSize(), "The accepting medium must not mutate cached pattern inputs");
        }
    }

    @Test
    void operationLimitFlushesDirtyAndLaterTicksResolveInputsAgain() {
        cpu.prepare(pattern, 64, 4);

        tick();

        assertAll(
                () -> assertEquals(4, medium.pushes),
                () -> assertEquals(480, cpu.storedInputs()),
                () -> assertEquals(32, cpu.expectedOutputs()),
                () -> assertEquals(1, cpu.condensedExpansions),
                () -> assertEquals(1, cpu.slotExpansions),
                () -> assertEquals(1, cpu.core.dirtyNotifications));

        // The CPU's operation budget includes the previous three ticks.
        for (int idleTick = 0; idleTick < 3; idleTick++) {
            tick();
        }
        assertEquals(4, medium.pushes);
        assertEquals(1, cpu.core.dirtyNotifications, "Idle ticks must not dirty the CPU");
        tick();

        assertAll(
                () -> assertEquals(8, medium.pushes),
                () -> assertEquals(448, cpu.storedInputs()),
                () -> assertEquals(64, cpu.expectedOutputs()),
                () -> assertEquals(2, cpu.condensedExpansions),
                () -> assertEquals(2, cpu.slotExpansions),
                () -> assertEquals(2, cpu.core.dirtyNotifications));
    }

    @Test
    void fakeCraftingAlsoCoalescesDirtyNotifications() {
        cpu.prepare(pattern, 64, 128);
        cpu.enableFakeCrafting(64);

        tick();

        assertAll(
                () -> assertEquals(64, medium.pushes),
                () -> assertEquals(0, cpu.storedInputs()),
                () -> assertEquals(0, cpu.expectedOutputs()),
                () -> assertTrue(cpu.completed()),
                () -> assertEquals(1, cpu.condensedExpansions),
                () -> assertEquals(1, cpu.slotExpansions),
                () -> assertEquals(1, cpu.core.dirtyNotifications));
    }

    @Test
    void rejectedDispatchReturnsInputsWithoutMarkingDirty() {
        cpu.prepare(pattern, 64, 128);
        medium.accept = false;

        tick();

        assertAll(
                () -> assertEquals(0, medium.pushes),
                () -> assertEquals(512, cpu.storedInputs()),
                () -> assertEquals(0, cpu.expectedOutputs()),
                () -> assertEquals(0, cpu.usedOperations()),
                () -> assertEquals(0, cpu.core.dirtyNotifications));
    }

    @Test
    void dispatchFailureFlushesDirtyAndRestoresImmediateNotifications() {
        cpu.prepare(pattern, 64, 128);
        medium.failAfter = 1;

        assertThrows(IllegalStateException.class, this::tick);

        assertEquals(1, medium.pushes);
        assertEquals(1, cpu.core.dirtyNotifications);
        cpu.markDirty();
        assertEquals(2, cpu.core.dirtyNotifications, "Notifications outside the update must remain immediate");
    }

    @Test
    void craftingLogDoesNotBreakJobCompletion() {
        cpu.prepare(pattern, 64, 128);
        cpu.trackFinalOutput(64);
        cpu.setStartItemCount(7);
        final long[] reportedCount = { -1 };
        cpu.addOnCompleteListener((output, count, elapsedTime) -> reportedCount[0] = count);
        tick();

        withCraftingLog(
                () -> cpu.injectItems(
                        AEItemStack.create(new ItemStack(Blocks.stone)).setStackSize(512),
                        Actionable.MODULATE,
                        new BaseActionSource()));

        assertAll(
                () -> assertTrue(cpu.completed()),
                () -> assertEquals(512, reportedCount[0], "The crafting log must not change the reported output"));
    }

    @Test
    void craftingLogDoesNotBreakFakeCraftingCompletion() {
        cpu.prepare(pattern, 64, 128);
        cpu.enableFakeCrafting(64);

        withCraftingLog(this::tick);

        assertTrue(cpu.completed());
    }

    private void tick() {
        cpu.updateCraftingLogic(grid, energy, cache);
    }

    private static void withCraftingLog(Runnable action) {
        final boolean wasEnabled = AEConfig.instance.isFeatureEnabled(AEFeature.CraftingLog);
        AEConfig.instance.featureFlags.add(AEFeature.CraftingLog);
        try {
            action.run();
        } finally {
            if (!wasEnabled) AEConfig.instance.featureFlags.remove(AEFeature.CraftingLog);
        }
    }

    private static ICraftingPatternDetails processingPattern() {
        final ItemStack encoded = AEApi.instance().definitions().items().encodedPattern().maybeStack(1).get();
        final NBTTagCompound tag = new NBTTagCompound();
        final NBTTagList inputs = new NBTTagList();
        for (int slot = 0; slot < 2; slot++) {
            inputs.appendTag(new ItemStack(Blocks.cobblestone, 4).writeToNBT(new NBTTagCompound()));
        }
        final NBTTagList outputs = new NBTTagList();
        outputs.appendTag(new ItemStack(Blocks.stone, 8).writeToNBT(new NBTTagCompound()));
        tag.setTag("in", inputs);
        tag.setTag("out", outputs);
        encoded.setTagCompound(tag);
        return new PatternHelper(encoded, null);
    }

    private static final class CountingCPU extends CraftingCPUCluster {

        private final CountingCore core = new CountingCore();
        private int condensedExpansions;
        private int slotExpansions;

        private CountingCPU() {
            super(new WorldCoord(0, 0, 0), new WorldCoord(0, 0, 0));
        }

        private void prepare(ICraftingPatternDetails pattern, int crafts, int operations) {
            isComplete = false;
            accelerator = operations - 1;
            tasks.put(pattern, new Progress(crafts));
            inventory.injectItems(
                    AEItemStack.create(new ItemStack(Blocks.cobblestone)).setStackSize(crafts * 8L),
                    Actionable.MODULATE);
        }

        private void setStartItemCount(long count) {
            startItemCount = count;
        }

        private void trackFinalOutput(int crafts) {
            finalOutput.init(AEItemStack.create(new ItemStack(Blocks.stone)).setStackSize(crafts * 8L));
        }

        private void enableFakeCrafting(int crafts) {
            trackFinalOutput(crafts);
            finalOutput.setFakeCrafting();
        }

        private long storedInputs() {
            final IAEItemStack remaining = inventory.extractItems(
                    AEItemStack.create(new ItemStack(Blocks.cobblestone)).setStackSize(Long.MAX_VALUE),
                    Actionable.SIMULATE);
            return remaining == null ? 0 : remaining.getStackSize();
        }

        private long expectedOutputs() {
            final IAEStack<?> expected = waitingFor.findPrecise(AEItemStack.create(new ItemStack(Blocks.stone)));
            return expected == null ? 0 : expected.getStackSize();
        }

        private int usedOperations() {
            return usedOps[0];
        }

        private boolean completed() {
            return isComplete;
        }

        @Override
        protected TileCraftingTile getCore() {
            return core;
        }

        @Override
        protected List<IAEStack<?>> getExpandedCondensedInputs(ICraftingPatternDetails details,
                CraftingGridCache cache) {
            condensedExpansions++;
            return super.getExpandedCondensedInputs(details, cache);
        }

        @Override
        protected List<IAEStack<?>> getExpandedInputs(ICraftingPatternDetails details, CraftingGridCache cache) {
            slotExpansions++;
            return super.getExpandedInputs(details, cache);
        }
    }

    private static final class Progress extends CraftingCPUCluster.TaskProgress {

        private Progress(long crafts) {
            value = crafts;
        }
    }

    private static final class CountingCore extends TileCraftingTile {

        private int dirtyNotifications;

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void markDirty() {
            dirtyNotifications++;
        }
    }

    private static final class CountingMedium implements ICraftingMedium {

        private boolean accept = true;
        private int failAfter = Integer.MAX_VALUE;
        private int pushes;
        private long consumed;

        @Override
        public boolean pushPattern(ICraftingPatternDetails details, InventoryCrafting table) {
            if (pushes == failAfter) throw new IllegalStateException("Test dispatch failure");
            if (!accept) return false;
            for (int slot = 0; slot < table.getSizeInventory(); slot++) {
                final IAEStack<?> input = ((MEInventoryCrafting) table).getAEStackInSlot(slot);
                consumed += input.getStackSize();
                // Interfaces consume the transferred stacks in place.
                input.setStackSize(0);
            }
            pushes++;
            return true;
        }

        @Override
        public boolean isBusy() {
            return false;
        }
    }
}
