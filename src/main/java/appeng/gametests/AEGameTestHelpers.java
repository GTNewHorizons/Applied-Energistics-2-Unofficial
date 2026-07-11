package appeng.gametests;

import static appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;
import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.parts.IPart;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.tile.networking.TileCableBus;
import appeng.tile.networking.TileController;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;

public final class AEGameTestHelpers {

    private static final BaseActionSource TEST_SOURCE = new BaseActionSource();

    private AEGameTestHelpers() {}

    public static <T extends TileEntity> T tile(GameTestHelper helper, Class<T> type, String label) {
        Coord pos = pos(helper, label);
        return helper.assertTileEntityPresent(type, pos.x(), pos.y(), pos.z());
    }

    public static Coord pos(GameTestHelper helper, String label) {
        TestPos pos = helper.pos(label);
        return new Coord(pos.x(), pos.y(), pos.z());
    }

    public static IPart part(GameTestHelper helper, String label, ForgeDirection side) {
        TileCableBus cableBus = tile(helper, TileCableBus.class, label);
        IPart part = cableBus.getPart(side);
        helper.assertNotNull(part, "Placed part should be readable from its host");
        return part;
    }

    public static void setRedstoneInput(GameTestHelper helper, String label, int strength) {
        Coord pos = pos(helper, label);
        helper.setRedstoneInput(pos.x(), pos.y(), pos.z(), strength);
    }

    public static void assertActive(GameTestHelper helper, AENetworkProxy proxy, String message) {
        IGridNode node = proxy.getNode();
        helper.assertNotNull(node, "Grid proxy should have a node");
        helper.assertTrue(node.isActive(), message);
    }

    public static void assertInactive(GameTestHelper helper, AENetworkProxy proxy, String message) {
        IGridNode node = proxy.getNode();
        helper.assertFalse(node != null && node.isActive(), message);
    }

    public static void assertActive(GameTestHelper helper, IPart part, String message) {
        IGridNode node = part.getGridNode();
        helper.assertNotNull(node, "Part should have a grid node");
        helper.assertTrue(node.isActive(), message);
    }

    public static void assertInactive(GameTestHelper helper, IPart part, String message) {
        IGridNode node = part.getGridNode();
        helper.assertNotNull(node, "Part should have a grid node");
        helper.assertFalse(node.isActive(), message);
    }

    public static IAEItemStack injectIntoGrid(TileController controller, Block block, long amount) {
        return itemMonitor(controller).injectItems(itemStack(block, amount), Actionable.MODULATE, TEST_SOURCE);
    }

    public static IAEItemStack simulateInjectIntoGrid(TileController controller, Block block, long amount) {
        return itemMonitor(controller).injectItems(itemStack(block, amount), Actionable.SIMULATE, TEST_SOURCE);
    }

    public static void assertNetworkStoredAmount(GameTestHelper helper, TileController controller, Block block,
            long expectedAmount) {
        helper.assertEquals(
                expectedAmount,
                networkStoredAmount(controller, block),
                "Network-visible stored item amount should match");
    }

    public static long networkStoredAmount(TileController controller, Block block) {
        IAEItemStack extracted = itemMonitor(controller)
                .extractItems(itemStack(block, Integer.MAX_VALUE), Actionable.SIMULATE, TEST_SOURCE);
        return extracted == null ? 0 : extracted.getStackSize();
    }

    public static void assertNetworkMonitorStoredAmount(GameTestHelper helper, TileController controller, Block block,
            long expectedAmount) {
        helper.assertEquals(
                expectedAmount,
                networkMonitorStoredAmount(controller, block),
                "Network monitor stored item amount should match");
    }

    public static long networkMonitorStoredAmount(TileController controller, Block block) {
        IAEItemStack stored = itemMonitor(controller).getStorageList().findPrecise(itemStack(block, 1));
        return stored == null ? 0 : stored.getStackSize();
    }

    public static IMEMonitor<IAEItemStack> itemMonitor(TileController controller) {
        try {
            return controller.getProxy().getStorage().getItemInventory();
        } catch (GridAccessException e) {
            throw new AssertionError("Network storage should be accessible", e);
        }
    }

    public static void setChestSlot(TileEntityChest chest, int slot, Block block, int amount) {
        chest.setInventorySlotContents(slot, new ItemStack(block, amount));
        chest.markDirty();
    }

    public static void clearChestSlot(TileEntityChest chest, int slot) {
        chest.setInventorySlotContents(slot, null);
        chest.markDirty();
    }

    public static void fillChest(TileEntityChest chest, Block block) {
        for (int slot = 0; slot < chest.getSizeInventory(); slot++) {
            setChestSlot(chest, slot, block, 64);
        }
    }

    public static void assertChestStoredAmount(GameTestHelper helper, TileEntityChest chest, Block block,
            long expectedAmount) {
        helper.assertEquals(expectedAmount, chestStoredAmount(chest, block), "Chest stored item amount should match");
    }

    public static long chestStoredAmount(TileEntityChest chest, Block block) {
        long amount = 0;
        ItemStack expectedStack = new ItemStack(block, 1);
        for (int slot = 0; slot < chest.getSizeInventory(); slot++) {
            ItemStack stack = chest.getStackInSlot(slot);
            if (stack != null && stack.isItemEqual(expectedStack)) {
                amount += stack.stackSize;
            }
        }
        return amount;
    }

    public static void insertItems(GameTestHelper helper, ItemStack cell, Block block, long amount) {
        IAEItemStack remainder = itemInventory(helper, cell)
                .injectItems(itemStack(block, amount), Actionable.MODULATE, TEST_SOURCE);
        helper.assertNull(remainder, "Items should fit completely into the cell");
    }

    public static void insertFluids(GameTestHelper helper, ItemStack cell, Fluid fluid, long amount) {
        IAEFluidStack remainder = fluidInventory(helper, cell)
                .injectItems(fluidStack(fluid, amount), Actionable.MODULATE, TEST_SOURCE);
        helper.assertNull(remainder, "Fluids should fit completely into the cell");
    }

    public static void assertStoredAmount(GameTestHelper helper, ItemStack cell, Block block, long expectedAmount) {
        helper.assertNotNull(cell, "Cell should exist");
        helper.assertEquals(expectedAmount, storedAmount(helper, cell, block), "Stored item amount should match");
    }

    public static void assertStoredFluidAmount(GameTestHelper helper, ItemStack cell, Fluid fluid,
            long expectedAmount) {
        helper.assertNotNull(cell, "Cell should exist");
        helper.assertEquals(expectedAmount, storedFluidAmount(helper, cell, fluid), "Stored fluid amount should match");
    }

    public static long storedAmount(GameTestHelper helper, ItemStack cell, Block block) {
        IAEItemStack extracted = itemInventory(helper, cell)
                .extractItems(itemStack(block, Integer.MAX_VALUE), Actionable.SIMULATE, TEST_SOURCE);
        return extracted == null ? 0 : extracted.getStackSize();
    }

    public static long storedFluidAmount(GameTestHelper helper, ItemStack cell, Fluid fluid) {
        IAEFluidStack extracted = fluidInventory(helper, cell)
                .extractItems(fluidStack(fluid, Long.MAX_VALUE), Actionable.SIMULATE, TEST_SOURCE);
        return extracted == null ? 0 : extracted.getStackSize();
    }

    @SuppressWarnings("unchecked")
    public static IMEInventoryHandler<IAEItemStack> itemInventory(GameTestHelper helper, ItemStack cell) {
        ICellHandler cellHandler = AEApi.instance().registries().cell().getHandler(cell);
        helper.assertNotNull(cellHandler, "Cell handler should exist");
        IMEInventoryHandler<IAEItemStack> cellInv = cellHandler.getCellInventory(cell, null, ITEM_STACK_TYPE);
        helper.assertNotNull(cellInv, "Item cell inventory should exist");
        return cellInv;
    }

    @SuppressWarnings("unchecked")
    public static IMEInventoryHandler<IAEFluidStack> fluidInventory(GameTestHelper helper, ItemStack cell) {
        ICellHandler cellHandler = AEApi.instance().registries().cell().getHandler(cell);
        helper.assertNotNull(cellHandler, "Cell handler should exist");
        IMEInventoryHandler<IAEFluidStack> cellInv = cellHandler.getCellInventory(cell, null, FLUID_STACK_TYPE);
        helper.assertNotNull(cellInv, "Fluid cell inventory should exist");
        return cellInv;
    }

    public static IAEItemStack itemStack(Block block, long amount) {
        IAEItemStack stack = AEItemStack.create(new ItemStack(block, 1));
        stack.setStackSize(amount);
        return stack;
    }

    public static IAEFluidStack fluidStack(Fluid fluid, long amount) {
        IAEFluidStack stack = AEFluidStack.create(new FluidStack(fluid, 1));
        stack.setStackSize(amount);
        return stack;
    }

    public static ItemStack cell1k() {
        return AEApi.instance().definitions().items().cell1k().maybeStack(1).get();
    }

    public static ItemStack cell4k() {
        return AEApi.instance().definitions().items().cell4k().maybeStack(1).get();
    }

    public static ItemStack cell64k() {
        return AEApi.instance().definitions().items().cell64k().maybeStack(1).get();
    }

    public static final class Coord {

        private final int x;
        private final int y;
        private final int z;

        public Coord(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int x() {
            return this.x;
        }

        public int y() {
            return this.y;
        }

        public int z() {
            return this.z;
        }
    }
}
