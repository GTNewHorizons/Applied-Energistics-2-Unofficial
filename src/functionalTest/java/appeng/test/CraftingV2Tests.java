package appeng.test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraft.world.WorldType;
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
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GT_OreDictUnificator;
import gregtech.common.items.GT_MetaGenerated_Tool_01;

public class CraftingV2Tests {

    static World dummyWorld = null;
    final int SIMPLE_SIMULATION_TIMEOUT_MS = 100;

    final ItemStack bronzePlate, bronzeDoublePlate, bronzeIngot, gtHammer;
    final ItemStack ironDust, ironIngot, ironPlate, goldDust, goldIngot, goldBlock;

    public CraftingV2Tests() {
        bronzePlate = Materials.Bronze.getPlates(1);
        bronzeDoublePlate = GT_OreDictUnificator.get(OrePrefixes.plateDouble, Materials.Bronze, 1);
        bronzeIngot = Materials.Bronze.getIngots(1);
        gtHammer = GT_MetaGenerated_Tool_01.INSTANCE
                .getToolWithStats(GT_MetaGenerated_Tool_01.HARDHAMMER, 1, Materials.VanadiumSteel, null, null);
        ironDust = Materials.Iron.getDust(1);
        ironIngot = Materials.Iron.getIngots(1);
        ironPlate = Materials.Iron.getPlates(1);
        goldDust = Materials.Gold.getDust(1);
        goldIngot = Materials.Gold.getIngots(1);
        goldBlock = Materials.Gold.getBlocks(1);

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

    private void addHammerBronzePlateRecipe(MockAESystem aeSystem) {
        aeSystem.newCraftingPattern().allowBeingASubstitute().allowUsingSubstitutes() //
                .addInput(gtHammer.copy()).addInput(null).addInput(null) //
                .addInput(bronzeIngot.copy()).addInput(null).addInput(null) //
                .addInput(bronzeIngot.copy()).addInput(null).addInput(null) //
                .addOutput(bronzePlate.copy()).buildAndAdd();
    }

    private void addHammerBronzeDoublePlateRecipe(MockAESystem aeSystem) {
        aeSystem.newCraftingPattern().allowBeingASubstitute().allowUsingSubstitutes() //
                .addInput(bronzePlate.copy()).addInput(null).addInput(null) //
                .addInput(bronzePlate.copy()).addInput(null).addInput(null) //
                .addInput(gtHammer.copy()).addInput(null).addInput(null) //
                .addOutput(bronzeDoublePlate.copy()).buildAndAdd();
    }

    @Test
    void canCraftWithGtTool() {
        MockAESystem aeSystem = new MockAESystem(dummyWorld);
        aeSystem.addStoredItem(gtHammer.copy());
        aeSystem.addStoredItem(withSize(bronzeIngot.copy(), 2));
        addHammerBronzePlateRecipe(aeSystem);

        final CraftingJobV2 job = aeSystem.makeCraftingJob(bronzePlate);
        simulateJobAndCheck(job, SIMPLE_SIMULATION_TIMEOUT_MS);
        assertEquals(false, job.isSimulation());
        assertJobPlanEquals(
                job,
                AEItemStack.create(gtHammer.copy()),
                AEItemStack.create(withSize(bronzeIngot.copy(), 2)),
                AEItemStack.create(withSize(bronzePlate.copy(), 0)).setCountRequestable(1));
    }

    @Test
    void canCraft2WithGtTool() {
        MockAESystem aeSystem = new MockAESystem(dummyWorld);
        aeSystem.addStoredItem(gtHammer.copy());
        aeSystem.addStoredItem(withSize(bronzeIngot.copy(), 4));
        addHammerBronzePlateRecipe(aeSystem);

        final CraftingJobV2 job = aeSystem.makeCraftingJob(withSize(bronzePlate.copy(), 2));
        simulateJobAndCheck(job, SIMPLE_SIMULATION_TIMEOUT_MS);
        assertEquals(false, job.isSimulation());
        assertJobPlanEquals(
                job,
                AEItemStack.create(gtHammer.copy()),
                AEItemStack.create(withSize(bronzeIngot.copy(), 4)),
                AEItemStack.create(withSize(bronzePlate.copy(), 0)).setCountRequestable(2));
    }

    @Test
    void canCraftDoublePlateWithGtTool() {
        MockAESystem aeSystem = new MockAESystem(dummyWorld);
        aeSystem.addStoredItem(gtHammer.copy());
        aeSystem.addStoredItem(withSize(bronzeIngot.copy(), 4));
        addHammerBronzePlateRecipe(aeSystem);
        addHammerBronzeDoublePlateRecipe(aeSystem);

        final CraftingJobV2 job = aeSystem.makeCraftingJob(bronzeDoublePlate);
        simulateJobAndCheck(job, SIMPLE_SIMULATION_TIMEOUT_MS);
        assertEquals(false, job.isSimulation());
        assertJobPlanEquals(
                job,
                AEItemStack.create(gtHammer.copy()),
                AEItemStack.create(withSize(bronzeIngot.copy(), 4)),
                AEItemStack.create(withSize(bronzePlate.copy(), 0)).setCountRequestable(2),
                AEItemStack.create(withSize(bronzeDoublePlate.copy(), 0)).setCountRequestable(1));
    }

    @Test
    void canCraft2WithGtToolMissing1() {
        MockAESystem aeSystem = new MockAESystem(dummyWorld);
        aeSystem.addStoredItem(gtHammer.copy());
        aeSystem.addStoredItem(withSize(bronzeIngot.copy(), 2));
        addHammerBronzePlateRecipe(aeSystem);

        final CraftingJobV2 job = aeSystem.makeCraftingJob(withSize(bronzePlate.copy(), 2));
        simulateJobAndCheck(job, SIMPLE_SIMULATION_TIMEOUT_MS);
        assertEquals(true, job.isSimulation());
        assertJobPlanEquals(
                job,
                AEItemStack.create(gtHammer.copy()),
                AEItemStack.create(withSize(bronzeIngot.copy(), 4)),
                AEItemStack.create(withSize(bronzePlate.copy(), 0)).setCountRequestable(2));
    }

    @Test
    void partialMissingAmount() {
        MockAESystem aeSystem = new MockAESystem(dummyWorld);
        aeSystem.addStoredItem(withSize(bronzePlate.copy(), 1));
        aeSystem.newProcessingPattern().addInput(withSize(bronzePlate.copy(), 2)).addOutput(bronzeDoublePlate.copy())
                .buildAndAdd();

        final CraftingJobV2 job = aeSystem.makeCraftingJob(withSize(bronzeDoublePlate.copy(), 1));
        simulateJobAndCheck(job, SIMPLE_SIMULATION_TIMEOUT_MS);
        assertEquals(true, job.isSimulation());
        assertJobPlanEquals(
                job,
                AEItemStack.create(withSize(bronzePlate.copy(), 2)),
                AEItemStack.create(withSize(bronzeDoublePlate.copy(), 0)).setCountRequestable(1));
    }

    @Test
    void differentMissingAmounts() {
        MockAESystem aeSystem = new MockAESystem(dummyWorld);
        aeSystem.addStoredItem(withSize(ironIngot, 32));
        aeSystem.addStoredItem(withSize(goldIngot, 64));
        aeSystem.newProcessingPattern().addInput(withSize(ironIngot, 2)).addInput(new ItemStack(Items.gold_ingot, 2))
                .addOutput(new ItemStack(Blocks.gold_block)).buildAndAdd();

        final CraftingJobV2 job = aeSystem.makeCraftingJob(new ItemStack(Blocks.gold_block, 100));
        simulateJobAndCheck(job, SIMPLE_SIMULATION_TIMEOUT_MS);
        assertEquals(true, job.isSimulation());
        assertJobPlanEquals(
                job,
                AEItemStack.create(withSize(goldIngot, 200)),
                AEItemStack.create(withSize(ironIngot, 200)),
                AEItemStack.create(withSize(goldBlock, 0)).setCountRequestable(100));
    }

    @Test
    void complexRecipeChain() {
        MockAESystem aeSystem = new MockAESystem(dummyWorld);
        aeSystem.addStoredItem(withSize(ironDust, 2 * 64));
        aeSystem.addStoredItem(withSize(goldDust, 3 * 64));
        aeSystem.newProcessingPattern().addInput(withSize(ironDust, 2)) //
                .addOutput(withSize(ironIngot, 1)) //
                .buildAndAdd();
        aeSystem.newProcessingPattern().addInput(withSize(ironIngot, 1)) //
                .addOutput(withSize(ironPlate, 1)) //
                .buildAndAdd();
        aeSystem.newProcessingPattern().addInput(withSize(ironPlate, 1)) //
                .addInput(withSize(goldDust, 2)) //
                .addOutput(withSize(goldIngot, 1)) //
                .buildAndAdd();
        aeSystem.newProcessingPattern().addInput(withSize(goldIngot, 9)) //
                .addInput(withSize(ironPlate, 1)) //
                .addOutput(withSize(goldBlock, 1)) //
                .buildAndAdd();

        final CraftingJobV2 job = aeSystem.makeCraftingJob(new ItemStack(Blocks.gold_block, 100));
        simulateJobAndCheck(job, SIMPLE_SIMULATION_TIMEOUT_MS);
        assertEquals(true, job.isSimulation());
        assertJobPlanEquals(
                job,
                AEItemStack.create(withSize(ironDust, 2000)),
                AEItemStack.create(withSize(goldDust, 1800)),
                AEItemStack.create(withSize(ironIngot, 0)).setCountRequestable(1000),
                AEItemStack.create(withSize(ironPlate, 0)).setCountRequestable(1000),
                AEItemStack.create(withSize(goldIngot, 0)).setCountRequestable(900),
                AEItemStack.create(withSize(goldBlock, 0)).setCountRequestable(100));
    }
}
