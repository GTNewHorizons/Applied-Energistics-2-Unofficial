package appeng.test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.*;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraftforge.common.DimensionManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.v2.CraftingJobV2;
import appeng.test.mockme.MockAESystem;
import appeng.util.item.AEItemStack;
import appeng.util.item.ItemList;
import gregtech.api.enums.Materials;
import gregtech.common.items.GT_MetaGenerated_Tool_01;

public class CraftingV2Tests {

    static World dummyWorld = null;
    final int SIMPLE_SIMULATION_TIMEOUT_MS = 100;

    final ItemStack bronzePlate, bronzeIngot, gtHammer;

    public CraftingV2Tests() {
        bronzePlate = Materials.Bronze.getPlates(1);
        bronzeIngot = Materials.Bronze.getIngots(1);
        gtHammer = GT_MetaGenerated_Tool_01.INSTANCE
                .getToolWithStats(GT_MetaGenerated_Tool_01.HARDHAMMER, 1, Materials.VanadiumSteel, null, null);

        if (!DimensionManager.isDimensionRegistered(256)) {
            DimensionManager.registerProviderType(256, WorldProviderSurface.class, false);
            DimensionManager.registerDimension(256, 256);
        }
        if (dummyWorld == null) {
            dummyWorld = new WorldServer(
                    MinecraftServer.getServer(),
                    new DummySaveHandler(),
                    "DummyTestWorld",
                    256,
                    new WorldSettings(256, GameType.SURVIVAL, false, false, WorldType.DEFAULT),
                    MinecraftServer.getServer().theProfiler) {

                @Override
                public File getChunkSaveLocation() {
                    return new File("dummy-ignoreme");
                }
            };
        }
    }

    private static ItemStack withSize(ItemStack stack, int newSize) {
        stack.stackSize = newSize;
        return stack;
    }

    private void simulateJobAndCheck(CraftingJobV2 job, int timeoutMs) {
        job.simulateFor(SIMPLE_SIMULATION_TIMEOUT_MS);

        assertTrue(job.isDone());
        assertFalse(job.isCancelled());
    }

    private void assertJobPlanEquals(CraftingJobV2 job, IAEItemStack... stacks) {
        assertTrue(job.isDone());
        ItemList plan = new ItemList();
        job.populatePlan(plan);
        for (IAEItemStack stack : stacks) {
            IAEItemStack matching = plan.findPrecise(stack);
            assertNotNull(matching, stack::toString);
            assertEquals(stack.getStackSize(), matching.getStackSize(), () -> "Stack size of " + stack);
            assertEquals(
                    stack.getCountRequestable(),
                    matching.getCountRequestable(),
                    () -> "Requestable count of " + stack);
            matching.setStackSize(0);
            matching.setCountRequestable(0);
        }
        for (IAEItemStack planStack : plan) {
            assertEquals(0, planStack.getStackSize(), () -> "Extra item in the plan: " + planStack);
            assertEquals(0, planStack.getCountRequestable(), () -> "Extra item in the plan: " + planStack);
        }
    }

    private void addDummyGappleRecipe(MockAESystem aeSystem) {
        aeSystem.newProcessingPattern().addInput(new ItemStack(Items.gold_ingot, 1))
                .addOutput(new ItemStack(Items.golden_apple, 1)).buildAndAdd();
    }

    @Test
    void noPatternSimulation() {
        MockAESystem aeSystem = new MockAESystem(dummyWorld);
        final CraftingJobV2 job = aeSystem.makeCraftingJob(new ItemStack(Items.stick, 13));
        simulateJobAndCheck(job, SIMPLE_SIMULATION_TIMEOUT_MS);
        assertTrue(job.isSimulation());
        assertEquals(job.getOutput(), AEItemStack.create(new ItemStack(Items.stick, 13)));
        assertJobPlanEquals(job, AEItemStack.create(new ItemStack(Items.stick, 13)));
    }

    @Test
    void simplePatternSimulation() {
        MockAESystem aeSystem = new MockAESystem(dummyWorld);
        // Very expensive sticks
        aeSystem.newProcessingPattern().addInput(new ItemStack(Items.diamond, 1))
                .addOutput(new ItemStack(Items.stick, 1)).buildAndAdd();
        // Another pattern that shouldn't match
        addDummyGappleRecipe(aeSystem);
        final CraftingJobV2 job = aeSystem.makeCraftingJob(new ItemStack(Items.stick, 13));
        simulateJobAndCheck(job, SIMPLE_SIMULATION_TIMEOUT_MS);
        assertTrue(job.isSimulation());
        assertEquals(job.getOutput(), AEItemStack.create(new ItemStack(Items.stick, 13)));
        assertJobPlanEquals(
                job,
                AEItemStack.create(new ItemStack(Items.stick, 0)).setCountRequestable(13),
                AEItemStack.create(new ItemStack(Items.diamond, 13)));
    }

    @Test
    void noPatternWithItemsSimulation() {
        MockAESystem aeSystem = new MockAESystem(dummyWorld);
        aeSystem.addStoredItem(new ItemStack(Items.stick, 64));
        aeSystem.addStoredItem(new ItemStack(Items.diamond, 64));
        aeSystem.addStoredItem(new ItemStack(Items.gold_ingot, 64));
        final CraftingJobV2 job = aeSystem.makeCraftingJob(new ItemStack(Items.stick, 13));
        simulateJobAndCheck(job, SIMPLE_SIMULATION_TIMEOUT_MS);
        assertTrue(job.isSimulation());
        assertEquals(job.getOutput(), AEItemStack.create(new ItemStack(Items.stick, 13)));
        assertJobPlanEquals(job, AEItemStack.create(new ItemStack(Items.stick, 13)));
    }

    @Test
    void simplePatternWithItemsSimulation() {
        MockAESystem aeSystem = new MockAESystem(dummyWorld);
        aeSystem.addStoredItem(new ItemStack(Items.diamond, 64));
        aeSystem.addStoredItem(new ItemStack(Items.gold_ingot, 64));
        // Very expensive sticks
        aeSystem.newProcessingPattern().addInput(new ItemStack(Items.diamond, 1))
                .addOutput(new ItemStack(Items.stick, 1)).buildAndAdd();
        // Another pattern that shouldn't match
        addDummyGappleRecipe(aeSystem);
        final CraftingJobV2 job = aeSystem.makeCraftingJob(new ItemStack(Items.stick, 13));
        simulateJobAndCheck(job, SIMPLE_SIMULATION_TIMEOUT_MS);
        assertFalse(job.isSimulation());
        assertEquals(job.getOutput(), AEItemStack.create(new ItemStack(Items.stick, 13)));
        assertJobPlanEquals(
                job,
                AEItemStack.create(new ItemStack(Items.stick, 0)).setCountRequestable(13),
                AEItemStack.create(new ItemStack(Items.diamond, 13)));
    }

    private void addPlankPatterns(MockAESystem aeSystem) {
        // Add all types of wood
        for (int meta = 0; meta < 4; meta++) {
            aeSystem.newCraftingPattern().allowBeingASubstitute().addInput(new ItemStack(Blocks.log, 1, meta))
                    .addOutput(new ItemStack(Blocks.planks, 4, meta)).buildAndAdd();
        }
    }

    private void addFuzzyChestPattern(MockAESystem aeSystem) {
        aeSystem.newCraftingPattern().allowUsingSubstitutes()
                // row 1
                .addInput(new ItemStack(Blocks.planks, 1)).addInput(new ItemStack(Blocks.planks, 1))
                .addInput(new ItemStack(Blocks.planks, 1))
                // row 2
                .addInput(new ItemStack(Blocks.planks, 1)).addInput(null).addInput(new ItemStack(Blocks.planks, 1))
                // row 3
                .addInput(new ItemStack(Blocks.planks, 1)).addInput(new ItemStack(Blocks.planks, 1))
                .addInput(new ItemStack(Blocks.planks, 1))
                // end
                .addOutput(new ItemStack(Blocks.chest, 1)).buildAndAdd();
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1 })
    void craftChestFromLogs(int woodMetadata) {
        MockAESystem aeSystem = new MockAESystem(dummyWorld);
        aeSystem.addStoredItem(new ItemStack(Blocks.log, 64, woodMetadata));
        aeSystem.addStoredItem(new ItemStack(Items.gold_ingot, 64));
        addPlankPatterns(aeSystem);
        addFuzzyChestPattern(aeSystem);
        // Another pattern that shouldn't match
        addDummyGappleRecipe(aeSystem);
        final CraftingJobV2 job = aeSystem.makeCraftingJob(new ItemStack(Blocks.chest, 1));
        simulateJobAndCheck(job, SIMPLE_SIMULATION_TIMEOUT_MS);
        assertFalse(job.isSimulation());
        assertEquals(job.getOutput(), AEItemStack.create(new ItemStack(Blocks.chest, 1)));
        assertJobPlanEquals(
                job,
                AEItemStack.create(new ItemStack(Blocks.log, 2, woodMetadata)),
                AEItemStack.create(new ItemStack(Blocks.planks, 0, woodMetadata)).setCountRequestable(8),
                AEItemStack.create(new ItemStack(Blocks.chest, 0)).setCountRequestable(1));
    }

    @Test
    void craftChestFromMixedLogs() {
        MockAESystem aeSystem = new MockAESystem(dummyWorld);
        aeSystem.addStoredItem(new ItemStack(Blocks.log, 1, 0));
        aeSystem.addStoredItem(new ItemStack(Blocks.log, 1, 1));
        aeSystem.addStoredItem(new ItemStack(Items.gold_ingot, 64));
        addPlankPatterns(aeSystem);
        addFuzzyChestPattern(aeSystem);
        // Another pattern that shouldn't match
        addDummyGappleRecipe(aeSystem);
        final CraftingJobV2 job = aeSystem.makeCraftingJob(new ItemStack(Blocks.chest, 1));
        simulateJobAndCheck(job, SIMPLE_SIMULATION_TIMEOUT_MS);
        assertFalse(job.isSimulation());
        assertEquals(job.getOutput(), AEItemStack.create(new ItemStack(Blocks.chest, 1)));
        assertJobPlanEquals(
                job,
                AEItemStack.create(new ItemStack(Blocks.log, 1, 0)),
                AEItemStack.create(new ItemStack(Blocks.log, 1, 1)),
                AEItemStack.create(new ItemStack(Blocks.planks, 0, 0)).setCountRequestable(4),
                AEItemStack.create(new ItemStack(Blocks.planks, 0, 1)).setCountRequestable(4),
                AEItemStack.create(new ItemStack(Blocks.chest, 0)).setCountRequestable(1));
    }

    @Test
    void canHandleCyclicalPatterns() {
        MockAESystem aeSystem = new MockAESystem(dummyWorld);
        aeSystem.addStoredItem(new ItemStack(Blocks.log, 4, 0));
        aeSystem.newProcessingPattern().addInput(new ItemStack(Blocks.log, 1))
                .addOutput(new ItemStack(Blocks.planks, 4)).buildAndAdd();
        aeSystem.newProcessingPattern().addInput(new ItemStack(Blocks.planks, 4))
                .addOutput(new ItemStack(Blocks.log, 1)).buildAndAdd();
        for (int plankAmount = 1; plankAmount < 64; plankAmount++) {
            final CraftingJobV2 job = aeSystem.makeCraftingJob(new ItemStack(Blocks.planks, plankAmount));
            simulateJobAndCheck(job, SIMPLE_SIMULATION_TIMEOUT_MS);
            assertEquals(job.isSimulation(), plankAmount > 16);
        }
    }

    @Test
    void strictNamedItems() {
        MockAESystem aeSystem = new MockAESystem(dummyWorld);
        aeSystem.addStoredItem(new ItemStack(Blocks.log, 4, 0).setStackDisplayName("Named Log"));
        aeSystem.newProcessingPattern().addInput(new ItemStack(Blocks.log, 1))
                .addOutput(new ItemStack(Blocks.planks, 4)).allowBeingASubstitute().buildAndAdd();

        final CraftingJobV2 job = aeSystem.makeCraftingJob(new ItemStack(Blocks.planks, 1));
        simulateJobAndCheck(job, SIMPLE_SIMULATION_TIMEOUT_MS);
        assertEquals(true, job.isSimulation()); // Don't use renamed items
    }

    private void addHammerTitaniumPlateRecipe(MockAESystem aeSystem) {
        aeSystem.newCraftingPattern() //
                .addInput(gtHammer.copy()).addInput(null).addInput(null) //
                .addInput(bronzeIngot.copy()).addInput(null).addInput(null) //
                .addInput(bronzeIngot.copy()).addInput(null).addInput(null) //
                .addOutput(bronzePlate.copy()).buildAndAdd();
    }

    @Test
    void canCraftWithGtTool() {
        MockAESystem aeSystem = new MockAESystem(dummyWorld);
        aeSystem.addStoredItem(gtHammer.copy());
        aeSystem.addStoredItem(withSize(bronzeIngot.copy(), 2));
        addHammerTitaniumPlateRecipe(aeSystem);

        final CraftingJobV2 job = aeSystem.makeCraftingJob(bronzePlate);
        simulateJobAndCheck(job, SIMPLE_SIMULATION_TIMEOUT_MS);
        assertEquals(false, job.isSimulation());
        assertJobPlanEquals(
                job,
                AEItemStack.create(gtHammer.copy()),
                AEItemStack.create(withSize(bronzeIngot.copy(), 2)),
                AEItemStack.create(withSize(bronzePlate.copy(), 0)).setCountRequestable(1));
    }
}
