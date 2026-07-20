package appeng.gametests;

import static appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;
import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import net.minecraft.block.Block;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.horizonqa.api.GameTestAssertException;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.InventoryHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.TickCallbackHandle;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IStorageGrid;
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

    /**
     * @deprecated Use directly GameTestHelper#assertTileEntityPresent(Class, String)
     * @see GameTestHelper#assertTileEntityPresent(Class, String)
     */
    @Deprecated
    public static <T extends TileEntity> T tile(GameTestHelper helper, Class<T> type, String label) {
        return helper.assertTileEntityPresent(type, label);
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

    public static <T extends IPart> T part(GameTestHelper helper, String label, Class<T> type) {
        TileCableBus cableBus = tile(helper, TileCableBus.class, label);
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            IPart candidate = cableBus.getPart(side);
            if (type.isInstance(candidate)) {
                return type.cast(candidate);
            }
        }

        throw new AssertionError("Template role '" + label + "' should contain part type " + type.getSimpleName());
    }

    /**
     * @deprecated Use directly GameTestHelper#setRedstoneInput(String, int)
     * @see GameTestHelper#setRedstoneInput(String, int)
     */
    @Deprecated
    public static void setRedstoneInput(GameTestHelper helper, String label, int strength) {
        helper.setRedstoneInput(label, strength);
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

    public static void assertItemRemainder(GameTestHelper helper, IAEItemStack remainder, Block block,
            long expectedAmount) {
        helper.assertNotNull(remainder, "Rejected " + describe(block) + " stack should be returned as a remainder");
        helper.assertTrue(
                remainder.isSameType(new ItemStack(block, 1)),
                "Rejected remainder item should be " + describe(block)
                        + "; actual="
                        + describe(remainder.getItemStack()));
        helper.assertEquals(
                expectedAmount,
                remainder.getStackSize(),
                "Rejected remainder amount for " + describe(block) + " should match");
    }

    public static void assertNetworkStoredAmount(GameTestHelper helper, TileController controller, Block block,
            long expectedAmount) {
        long actualAmount = networkStoredAmount(controller, block);
        helper.assertEquals(
                expectedAmount,
                actualAmount,
                "Network storage for " + describe(block) + " should match; controller=" + describe(controller));
    }

    public static long networkStoredAmount(TileController controller, Block block) {
        IAEItemStack extracted = itemMonitor(controller)
                .extractItems(itemStack(block, Long.MAX_VALUE), Actionable.SIMULATE, TEST_SOURCE);
        return extracted == null ? 0 : extracted.getStackSize();
    }

    public static void assertNetworkStoredAmount(GameTestHelper helper, IGridNode node, Block block,
            long expectedAmount) {
        long actualAmount = networkStoredAmount(node, block);
        helper.assertEquals(
                expectedAmount,
                actualAmount,
                "Network storage for " + describe(block) + " should match; node=" + describe(node));
    }

    public static long networkStoredAmount(IGridNode node, Block block) {
        IAEItemStack extracted = itemMonitor(node)
                .extractItems(itemStack(block, Long.MAX_VALUE), Actionable.SIMULATE, TEST_SOURCE);
        return extracted == null ? 0 : extracted.getStackSize();
    }

    public static void assertNetworkMonitorStoredAmount(GameTestHelper helper, TileController controller, Block block,
            long expectedAmount) {
        long actualAmount = networkMonitorStoredAmount(controller, block);
        helper.assertEquals(
                expectedAmount,
                actualAmount,
                "Network monitor for " + describe(block) + " should match; controller=" + describe(controller));
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

    public static IMEMonitor<IAEItemStack> itemMonitor(IGridNode node) {
        if (node == null || node.getGrid() == null) {
            throw new AssertionError("Network storage should have an attached grid node");
        }

        IStorageGrid storageGrid = node.getGrid().getCache(IStorageGrid.class);
        if (storageGrid == null) {
            throw new AssertionError("Network storage cache should be accessible");
        }
        return storageGrid.getItemInventory();
    }

    /**
     * @deprecated Use directly InventoryHelper#setSlot(IInventory, int, ItemStack)
     * @see InventoryHelper#setSlot(IInventory, int, ItemStack)
     */
    @Deprecated
    public static void setChestSlot(TileEntityChest chest, int slot, Block block, int amount) {
        InventoryHelper.setSlot(chest, slot, new ItemStack(block, amount));
    }

    /**
     * @deprecated Use directly InventoryHelper#clearSlot(IInventory, int)
     * @see InventoryHelper#clearSlot(IInventory, int)
     */
    @Deprecated
    public static void clearChestSlot(TileEntityChest chest, int slot) {
        InventoryHelper.clearSlot(chest, slot);
    }

    public static void fillChest(TileEntityChest chest, Block block) {
        for (int slot = 0; slot < chest.getSizeInventory(); slot++) {
            setChestSlot(chest, slot, block, 64);
        }
    }

    public static void assertChestStoredAmount(GameTestHelper helper, TileEntityChest chest, Block block,
            long expectedAmount) {
        helper.assertEquals(
                expectedAmount,
                chestStoredAmount(chest, block),
                "Chest storage for " + describe(block) + " should match; chest=" + describe(chest));
    }

    /**
     * @deprecated Use directly InventoryHelper#count(IInventory, ItemStack)
     * @see InventoryHelper#count(IInventory, ItemStack)
     */
    @Deprecated
    public static long chestStoredAmount(TileEntityChest chest, Block block) {
        return InventoryHelper.count(chest, new ItemStack(block));
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
        helper.assertNotNull(cell, "Cell containing " + describe(block) + " should exist");
        helper.assertEquals(
                expectedAmount,
                storedAmount(helper, cell, block),
                "Cell storage for " + describe(block) + " should match; cell=" + describe(cell));
    }

    public static void assertStoredFluidAmount(GameTestHelper helper, ItemStack cell, Fluid fluid,
            long expectedAmount) {
        helper.assertNotNull(cell, "Cell should exist");
        helper.assertEquals(
                expectedAmount,
                storedFluidAmount(helper, cell, fluid),
                "Cell storage for fluid " + fluid.getName() + " should match; cell=" + describe(cell));
    }

    public static long storedAmount(GameTestHelper helper, ItemStack cell, Block block) {
        IAEItemStack extracted = itemInventory(helper, cell)
                .extractItems(itemStack(block, Long.MAX_VALUE), Actionable.SIMULATE, TEST_SOURCE);
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

    /**
     * @deprecated Use directly GameTestHelper#onEachTick(Runnable) and disable the returned handle initially
     * @see GameTestHelper#onEachTick(Runnable)
     */
    @Deprecated
    public static ContinuousInvariant continuousInvariant(GameTestHelper helper, String description,
            Runnable assertion) {
        TickCallbackHandle callback = helper.onEachTick(() -> checkContinuousInvariant(description, assertion));
        callback.disable();
        return new ContinuousInvariant(callback);
    }

    private static void checkContinuousInvariant(String description, Runnable assertion) {
        try {
            assertion.run();
        } catch (GameTestAssertException failure) {
            String message = "Continuous invariant '" + description + "' failed: " + failure.getMessage();
            GameTestAssertException enrichedFailure = failure.hasPosition()
                    ? new GameTestAssertException(message, failure.getPos())
                    : new GameTestAssertException(message, failure.getX(), failure.getY(), failure.getZ());
            enrichedFailure.initCause(failure);
            throw enrichedFailure;
        } catch (AssertionError failure) {
            throw new AssertionError(
                    "Continuous invariant '" + description + "' failed: " + failure.getMessage(),
                    failure);
        }
    }

    private static String describe(Block block) {
        Object registryName = Block.blockRegistry.getNameForObject(block);
        return registryName == null ? block.getUnlocalizedName() : registryName.toString();
    }

    private static String describe(ItemStack stack) {
        if (stack == null) {
            return "null";
        }
        return stack.getDisplayName() + " x" + stack.stackSize + " nbt=" + stack.getTagCompound();
    }

    private static String describe(TileEntity tile) {
        return tile.getClass().getSimpleName() + "@(" + tile.xCoord + ',' + tile.yCoord + ',' + tile.zCoord + ')';
    }

    private static String describe(IGridNode node) {
        if (node == null) {
            return "null";
        }
        return node.getMachine().getClass().getSimpleName() + "[active=" + node.isActive() + ']';
    }

    public static final class ContinuousInvariant {

        private final TickCallbackHandle callback;

        private ContinuousInvariant(TickCallbackHandle callback) {
            this.callback = callback;
        }

        public void enable() {
            this.callback.enable();
        }

        public void disable() {
            this.callback.disable();
        }
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
