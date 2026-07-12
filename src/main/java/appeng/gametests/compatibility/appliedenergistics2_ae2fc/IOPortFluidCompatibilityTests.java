package appeng.gametests.compatibility.appliedenergistics2_ae2fc;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.assertStoredFluidAmount;
import static appeng.gametests.AEGameTestHelpers.insertFluids;
import static appeng.gametests.AEGameTestHelpers.tile;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import com.glodblock.github.loader.ItemAndBlockHolder;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.core.AppEng;
import appeng.tile.storage.TileDrive;
import appeng.tile.storage.TileIOPort;

@GameTestHolder(AppEng.MOD_ID)
public class IOPortFluidCompatibilityTests {

    private static final String IO_PORT_LABEL = "io_port";
    private static final String DRIVE_LABEL = "drive";

    // AE2FC fluid cells use the real IO port transfer path and the same base transfer budget as item cells.
    @GameTest(template = "ioport", timeoutTicks = 30)
    public static void noUpgradeTransfersTwoHundredFiftySixBucketsOfFluidPerTick(GameTestHelper helper) {
        TileIOPort ioport = tile(helper, TileIOPort.class, IO_PORT_LABEL);
        TileDrive drive = tile(helper, TileDrive.class, DRIVE_LABEL);
        Fluid water = FluidRegistry.WATER;
        helper.assertNotNull(water, "Water fluid should be registered");
        ItemStack sourceCell = fluidCell1k(helper);
        ItemStack driveCell = fluidCell1k(helper);
        insertFluids(helper, sourceCell, water, 300_000);

        helper.startSequence()
                .thenWaitUntilAtEnd(
                        "wait for AE2FC IO port network activation",
                        () -> assertActive(helper, ioport.getProxy(), "IO port network should become active"))
                .thenIdle(1).thenExecuteAtStart("insert AE2FC source and destination fluid cells", () -> {
                    drive.setInventorySlotContents(0, driveCell);
                    ioport.setInventorySlotContents(0, sourceCell);
                }).thenExecute("assert the first tick transfers exactly 256,000 mB", () -> {
                    helper.assertNotNull(
                            ioport.getStackInSlot(0),
                            "Fluid cell should remain in input after exhausting transfer budget");
                    assertStoredFluidAmount(helper, ioport.getStackInSlot(0), water, 44_000);
                    assertStoredFluidAmount(helper, drive.getStackInSlot(0), water, 256_000);
                }).thenSucceed();
    }

    private static ItemStack fluidCell1k(GameTestHelper helper) {
        helper.assertNotNull(ItemAndBlockHolder.CELL1K, "AE2FC 1k fluid cell should be available");
        return ItemAndBlockHolder.CELL1K.stack();
    }
}
