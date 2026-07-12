package appeng.gametests.interfaces;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.assertChestStoredAmount;
import static appeng.gametests.AEGameTestHelpers.assertStoredAmount;
import static appeng.gametests.AEGameTestHelpers.cell1k;
import static appeng.gametests.AEGameTestHelpers.continuousInvariant;
import static appeng.gametests.AEGameTestHelpers.insertItems;
import static appeng.gametests.AEGameTestHelpers.itemStack;
import static appeng.gametests.AEGameTestHelpers.tile;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.bsideup.jabel.Desugar;
import com.google.common.collect.ImmutableCollection;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.AEApi;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.parts.IPart;
import appeng.container.ContainerNull;
import appeng.core.AppEng;
import appeng.gametests.AEGameTestHelpers;
import appeng.gametests.AEGameTestHelpers.ContinuousInvariant;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.me.GridAccessException;
import appeng.parts.misc.PartInterface;
import appeng.tile.crafting.TileMolecularAssembler;
import appeng.tile.misc.TileInterface;
import appeng.tile.networking.TileCableBus;
import appeng.tile.networking.TileController;
import appeng.tile.storage.TileDrive;
import appeng.util.Platform;
import appeng.util.inv.MEInventoryCrafting;

@GameTestHolder(AppEng.MOD_ID)
public class InterfaceTests {

    private static final String CONTROLLER_LABEL = "controller";
    private static final String DRIVE_LABEL = "drive";
    private static final String BLOCK_INTERFACE_LABEL = "block_interface";
    private static final String PART_INTERFACE_HOST_LABEL = "part_interface_host";
    private static final String ADJACENT_CHEST_LABEL = "adjacent_chest";
    private static final String ASSEMBLER_LABEL = "assembler";

    private static final int STOCK_AMOUNT = 32;

    // Configured interface stock should be pulled from ME storage into the interface inventory.
    @GameTest(template = "interface_network", timeoutTicks = 160)
    public static void interfaceStocksConfiguredItem(GameTestHelper helper) {
        InterfaceNetwork network = getInterfaceNetwork(helper);
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, Blocks.cobblestone, 64);
        network.drive.setInventorySlotContents(0, driveCell);

        helper.startSequence()
                .thenWaitUntil(
                        "wait for interface stocking network to activate",
                        80,
                        () -> assertInterfaceNetworkActive(helper, network))
                .thenExecute(
                        "configure block interface to stock 32 cobblestone",
                        () -> configureStock(network.blockInterface, Blocks.cobblestone, STOCK_AMOUNT))
                .thenWaitUntil("wait for interface to stock 32 cobblestone from the drive", 80, () -> {
                    assertInterfaceStoredAmount(helper, network.blockInterface, Blocks.cobblestone, STOCK_AMOUNT);
                    assertStoredAmount(helper, driveCell, Blocks.cobblestone, 64 - STOCK_AMOUNT);
                }).thenSucceed();
    }

    // Blocking mode should reject a pattern push while the target inventory contains non-ignored items.
    @GameTest(template = "interface_network", timeoutTicks = 160)
    public static void blockingModeWaitsForEmptyInventory(GameTestHelper helper) {
        InterfaceNetwork network = getInterfaceNetwork(helper);

        helper.startSequence()
                .thenWaitUntil(
                        "wait for blocking-interface network to activate",
                        80,
                        () -> assertInterfaceNetworkActive(helper, network))
                .thenExecute("enable blocking mode, install pattern, and occupy target with dirt", () -> {
                    network.partInterface.getConfigManager().putSetting(Settings.BLOCK, YesNo.YES);
                    network.partInterface.getInterfaceDuality().getPatterns().setInventorySlotContents(
                            0,
                            encodedProcessingPattern(Blocks.cobblestone, 1, Blocks.stone, 1));
                    AEGameTestHelpers.setChestSlot(network.adjacentChest, 0, Blocks.dirt, 1);
                })
                .thenWaitUntil(
                        "wait for blocking pattern advertisement",
                        40,
                        () -> helper.assertFalse(
                                craftingOptionsFor(network.controller, Blocks.stone).isEmpty(),
                                "Blocking interface should advertise the processing pattern"))
                .thenExecute("attempt pattern push into occupied target", () -> {
                    ICraftingPatternDetails details = firstPattern(helper, network.controller, Blocks.stone);
                    boolean pushed = network.partInterface.pushPattern(details, craftingTable(Blocks.cobblestone, 1));

                    helper.assertFalse(pushed, "Blocking interface should not push into a non-empty target");
                    assertChestStoredAmount(helper, network.adjacentChest, Blocks.dirt, 1);
                    assertChestStoredAmount(helper, network.adjacentChest, Blocks.cobblestone, 0);
                }).thenExecute("clear blocking target inventory", () -> clearChest(network.adjacentChest))
                .thenExecute("attempt pattern push into empty target", () -> {
                    ICraftingPatternDetails details = firstPattern(helper, network.controller, Blocks.stone);
                    boolean pushed = network.partInterface.pushPattern(details, craftingTable(Blocks.cobblestone, 1));

                    helper.assertTrue(pushed, "Blocking interface should push once the target is empty");
                    assertChestStoredAmount(helper, network.adjacentChest, Blocks.cobblestone, 1);
                }).thenSucceed();
    }

    // Encoded patterns in interface pattern slots should be advertised by the network crafting cache.
    @GameTest(template = "interface_network", timeoutTicks = 160)
    public static void interfacePatternSlotsAdvertiseCraftableOutput(GameTestHelper helper) {
        InterfaceNetwork network = getInterfaceNetwork(helper);

        helper.startSequence()
                .thenWaitUntil(
                        "wait for pattern-advertisement network to activate",
                        80,
                        () -> assertInterfaceNetworkActive(helper, network))
                .thenExecute(
                        "install cobblestone-to-stone processing pattern",
                        () -> network.blockInterface.getInterfaceDuality().getPatterns().setInventorySlotContents(
                                0,
                                encodedProcessingPattern(Blocks.cobblestone, 1, Blocks.stone, 1)))
                .thenWaitUntil(
                        "wait for interface pattern output to become craftable",
                        80,
                        () -> helper.assertFalse(
                                craftingOptionsFor(network.controller, Blocks.stone).isEmpty(),
                                "Interface pattern output should be craftable"))
                .thenSucceed();
    }

    // Block and part interfaces should both maintain configured stock in their internal inventories.
    @GameTest(template = "interface_network", timeoutTicks = 180)
    public static void partAndBlockInterfacesExposeSameStockBehavior(GameTestHelper helper) {
        InterfaceNetwork network = getInterfaceNetwork(helper);
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, Blocks.cobblestone, 64);
        insertItems(helper, driveCell, Blocks.dirt, 64);
        network.drive.setInventorySlotContents(0, driveCell);

        helper.startSequence()
                .thenWaitUntil(
                        "wait for block-and-part interface network to activate",
                        80,
                        () -> assertInterfaceNetworkActive(helper, network))
                .thenExecute("configure block and part interface stock targets", () -> {
                    configureStock(network.blockInterface, Blocks.cobblestone, 16);
                    configureStock(network.partInterface, Blocks.dirt, 16);
                }).thenWaitUntil("wait for both interfaces to reach their 16-item stock targets", 100, () -> {
                    assertInterfaceStoredAmount(helper, network.blockInterface, Blocks.cobblestone, 16);
                    assertInterfaceStoredAmount(helper, network.partInterface, Blocks.dirt, 16);
                    assertStoredAmount(helper, driveCell, Blocks.cobblestone, 48);
                    assertStoredAmount(helper, driveCell, Blocks.dirt, 48);
                }).thenSucceed();
    }

    // If the interface storage already satisfies the configured stock amount, ME storage should not be drained.
    @GameTest(template = "interface_network", timeoutTicks = 120)
    public static void interfaceDoesNotOverstock(GameTestHelper helper) {
        InterfaceNetwork network = getInterfaceNetwork(helper);
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, Blocks.cobblestone, 64);
        network.drive.setInventorySlotContents(0, driveCell);
        ContinuousInvariant configuredStockDoesNotDrainNetwork = continuousInvariant(
                helper,
                "already satisfied interface stock must not drain ME storage",
                () -> {
                    assertInterfaceStoredAmount(helper, network.blockInterface, Blocks.cobblestone, STOCK_AMOUNT);
                    assertStoredAmount(helper, driveCell, Blocks.cobblestone, 64);
                });

        helper.startSequence()
                .thenWaitUntil(
                        "wait for no-overstock interface network to activate",
                        80,
                        () -> assertInterfaceNetworkActive(helper, network))
                .thenExecute("pre-stock interface and enable no-overstock invariant", () -> {
                    configureStock(network.blockInterface, Blocks.cobblestone, STOCK_AMOUNT);
                    network.blockInterface.getInterfaceDuality().getStorage()
                            .setInventorySlotContents(0, new ItemStack(Blocks.cobblestone, STOCK_AMOUNT));
                    configuredStockDoesNotDrainNetwork.enable();
                }).thenIdle(30)
                .thenExecute("finish no-overstock observation window", configuredStockDoesNotDrainNetwork::disable)
                .thenSucceed();
    }

    private static InterfaceNetwork getInterfaceNetwork(GameTestHelper helper) {
        TileController controller = tile(helper, TileController.class, CONTROLLER_LABEL);
        TileDrive drive = tile(helper, TileDrive.class, DRIVE_LABEL);
        TileInterface blockInterface = tile(helper, TileInterface.class, BLOCK_INTERFACE_LABEL);
        PartInterface partInterface = getPartInterface(helper);
        TileEntityChest adjacentChest = tile(helper, TileEntityChest.class, ADJACENT_CHEST_LABEL);
        tile(helper, TileMolecularAssembler.class, ASSEMBLER_LABEL);

        return new InterfaceNetwork(controller, drive, blockInterface, partInterface, adjacentChest);
    }

    private static PartInterface getPartInterface(GameTestHelper helper) {
        TileCableBus host = tile(helper, TileCableBus.class, PART_INTERFACE_HOST_LABEL);

        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            IPart part = host.getPart(side);
            if (part instanceof PartInterface partInterface) {
                return partInterface;
            }
        }

        throw new AssertionError("Part interface host label should contain a part interface");
    }

    private static void assertInterfaceNetworkActive(GameTestHelper helper, InterfaceNetwork network) {
        assertActive(helper, network.controller.getProxy(), "Controller grid proxy should become active");
        assertActive(helper, network.drive.getProxy(), "Drive grid proxy should become active");
        assertActive(helper, network.blockInterface.getProxy(), "Block interface should receive a channel");
        assertActive(helper, network.partInterface, "Part interface should receive a channel");
    }

    private static void configureStock(IInterfaceHost interfaceHost, Block block, int amount) {
        interfaceHost.getInterfaceDuality().getConfig().setInventorySlotContents(0, new ItemStack(block, amount));
    }

    private static void assertInterfaceStoredAmount(GameTestHelper helper, IInterfaceHost interfaceHost, Block block,
            long expectedAmount) {
        helper.assertEquals(
                expectedAmount,
                interfaceStoredAmount(interfaceHost, block),
                "Interface stocked item amount should match");
    }

    private static long interfaceStoredAmount(IInterfaceHost interfaceHost, Block block) {
        long amount = 0;
        ItemStack expected = new ItemStack(block, 1);
        DualityInterface duality = interfaceHost.getInterfaceDuality();

        for (ItemStack stack : duality.getStorage()) {
            if (stack != null && stack.isItemEqual(expected)) {
                amount += stack.stackSize;
            }
        }

        return amount;
    }

    private static ImmutableCollection<ICraftingPatternDetails> craftingOptionsFor(TileController controller,
            Block output) {
        try {
            ICraftingGrid crafting = controller.getProxy().getCrafting();
            return crafting.getCraftingFor(itemStack(output, 1), null, -1, controller.getWorldObj());
        } catch (GridAccessException e) {
            throw new AssertionError("Network crafting cache should be accessible", e);
        }
    }

    private static ICraftingPatternDetails firstPattern(GameTestHelper helper, TileController controller,
            Block output) {
        ImmutableCollection<ICraftingPatternDetails> patterns = craftingOptionsFor(controller, output);
        helper.assertFalse(patterns.isEmpty(), "Network crafting cache should advertise the encoded pattern");
        return patterns.iterator().next();
    }

    private static MEInventoryCrafting craftingTable(Block block, int amount) {
        MEInventoryCrafting table = new MEInventoryCrafting(new ContainerNull(), 1, 1);
        table.setInventorySlotContents(0, itemStack(block, amount));
        return table;
    }

    private static ItemStack encodedProcessingPattern(Block input, int inputAmount, Block output, int outputAmount) {
        ItemStack encodedPattern = AEApi.instance().definitions().items().encodedPattern().maybeStack(1).get();
        NBTTagCompound patternTags = new NBTTagCompound();
        NBTTagList inputs = new NBTTagList();
        NBTTagList outputs = new NBTTagList();

        patternTags.setBoolean("crafting", false);
        patternTags.setBoolean("substitute", false);
        patternTags.setBoolean("beSubstitute", false);
        inputs.appendTag(itemTag(input, inputAmount));
        outputs.appendTag(itemTag(output, outputAmount));
        patternTags.setTag("in", inputs);
        patternTags.setTag("out", outputs);
        encodedPattern.setTagCompound(patternTags);

        return encodedPattern;
    }

    private static NBTTagCompound itemTag(Block block, int amount) {
        NBTTagCompound tag = new NBTTagCompound();
        Platform.writeItemStackToNBT(new ItemStack(block, amount), tag);
        return tag;
    }

    private static void clearChest(TileEntityChest chest) {
        for (int slot = 0; slot < chest.getSizeInventory(); slot++) {
            chest.setInventorySlotContents(slot, null);
        }
        chest.markDirty();
    }

    @Desugar
    private record InterfaceNetwork(TileController controller, TileDrive drive, TileInterface blockInterface,
            PartInterface partInterface, TileEntityChest adjacentChest) {}
}
