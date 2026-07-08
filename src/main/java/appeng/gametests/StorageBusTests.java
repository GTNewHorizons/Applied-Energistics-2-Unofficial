package appeng.gametests;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.assertNetworkMonitorStoredAmount;
import static appeng.gametests.AEGameTestHelpers.part;
import static appeng.gametests.AEGameTestHelpers.tile;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.parts.IPart;
import appeng.core.AppEng;
import appeng.tile.networking.TileController;

@GameTestHolder(AppEng.MOD_ID)
public class StorageBusTests {

    private static final String CONTROLLER_LABEL = "controller";
    private static final String STORAGE_BUS_LABEL = "storage_bus";
    private static final String EXTERNAL_CHEST_LABEL = "external_chest";

    // Exposes items in the adjacent vanilla chest through the ME item storage monitor.
    @GameTest(template = "storage_bus", timeoutTicks = 100)
    public static void storageBusExposesExternalChestContents(GameTestHelper helper) {
        TileController controller = getController(helper);
        TileEntityChest chest = getExternalChest(helper);
        IPart storageBus = getStorageBus(helper);
        setChestContents(chest, Blocks.cobblestone, 64);

        helper.startSequence().thenWaitUntil(60, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, storageBus, "Storage bus should receive a channel");
            assertNetworkMonitorStoredAmount(helper, controller, Blocks.cobblestone, 64);
        }).thenSucceed();
    }

    // Reflects external chest mutations after the storage bus monitor refreshes.
    @GameTest(template = "storage_bus", timeoutTicks = 140)
    public static void storageBusReflectsExternalMutation(GameTestHelper helper) {
        TileController controller = getController(helper);
        TileEntityChest chest = getExternalChest(helper);
        IPart storageBus = getStorageBus(helper);
        setChestContents(chest, Blocks.cobblestone, 16);

        helper.startSequence().thenWaitUntil(60, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, storageBus, "Storage bus should receive a channel");
            assertNetworkMonitorStoredAmount(helper, controller, Blocks.cobblestone, 16);
        }).thenExecute(() -> setChestContents(chest, Blocks.cobblestone, 40))
                .thenWaitUntil(80, () -> assertNetworkMonitorStoredAmount(helper, controller, Blocks.cobblestone, 40))
                .thenSucceed();
    }

    private static TileController getController(GameTestHelper helper) {
        return tile(helper, TileController.class, CONTROLLER_LABEL);
    }

    private static TileEntityChest getExternalChest(GameTestHelper helper) {
        return tile(helper, TileEntityChest.class, EXTERNAL_CHEST_LABEL);
    }

    private static IPart getStorageBus(GameTestHelper helper) {
        return part(helper, STORAGE_BUS_LABEL, ForgeDirection.EAST);
    }

    private static void setChestContents(TileEntityChest chest, Block block, int amount) {
        chest.setInventorySlotContents(0, new ItemStack(block, amount));
        chest.markDirty();
    }
}
