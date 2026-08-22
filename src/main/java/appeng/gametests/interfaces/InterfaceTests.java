package appeng.gametests.interfaces;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.assertNetworkStoredAmount;
import static appeng.gametests.AEGameTestHelpers.assertStoredAmount;
import static appeng.gametests.AEGameTestHelpers.cell1k;
import static appeng.gametests.AEGameTestHelpers.continuousInvariant;
import static appeng.gametests.AEGameTestHelpers.insertItems;
import static appeng.gametests.AEGameTestHelpers.itemMonitor;
import static appeng.gametests.AEGameTestHelpers.itemStack;
import static appeng.gametests.AEGameTestHelpers.part;

import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntityChest;

import com.github.bsideup.jabel.Desugar;
import com.google.common.collect.ImmutableCollection;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.InventoryHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.container.ContainerNull;
import appeng.core.AppEng;
import appeng.gametests.AEGameTestHelpers.ContinuousInvariant;
import appeng.helpers.IInterfaceHost;
import appeng.items.misc.ItemTunnelPattern;
import appeng.me.GridAccessException;
import appeng.parts.misc.PartInterface;
import appeng.tile.crafting.TileMolecularAssembler;
import appeng.tile.misc.TileInterface;
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
    private static final String MAIN_CONTROLLER_LABEL = "main_controller";
    private static final String SMART_INTERFACE_LABEL = "smart_interface";
    private static final String NORMAL_INTERFACE_LABEL = "normal_interface";
    private static final String SUBNET_CONTROLLER_LABEL = "subnet_controller";

    private static final int STOCK_AMOUNT = 32;

    // Configured interface stock should be pulled from ME storage into the interface inventory.
    @GameTest(template = "interface_network", timeoutTicks = 160)
    public static void interfaceStocksConfiguredItem(GameTestHelper helper) {
        InterfaceNetwork network = getInterfaceNetwork(helper);
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, Blocks.cobblestone, 64);
        helper.setSlot(DRIVE_LABEL, 0, driveCell);

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
                    assertStoredAmount(helper, network.drive.getStackInSlot(0), Blocks.cobblestone, 64 - STOCK_AMOUNT);
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
                    InventoryHelper.setSlot(
                            network.partInterface.getInterfaceDuality().getPatterns(),
                            0,
                            encodedProcessingPattern(Blocks.cobblestone, 1, Blocks.stone, 1));
                    helper.setSlot(ADJACENT_CHEST_LABEL, 0, new ItemStack(Blocks.dirt));
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
                    helper.assertInventoryCount(ADJACENT_CHEST_LABEL, new ItemStack(Blocks.dirt), 1);
                    helper.assertInventoryCount(ADJACENT_CHEST_LABEL, new ItemStack(Blocks.cobblestone), 0);
                }).thenExecute("clear blocking target inventory", () -> helper.clearSlot(ADJACENT_CHEST_LABEL, 0))
                .thenExecute("attempt pattern push into empty target", () -> {
                    ICraftingPatternDetails details = firstPattern(helper, network.controller, Blocks.stone);
                    boolean pushed = network.partInterface.pushPattern(details, craftingTable(Blocks.cobblestone, 1));

                    helper.assertTrue(pushed, "Blocking interface should push once the target is empty");
                    helper.assertInventoryCount(ADJACENT_CHEST_LABEL, new ItemStack(Blocks.cobblestone), 1);
                }).thenSucceed();
    }

    // A push from any interface must revoke another interface's smart-blocking recipe allowance.
    @GameTest(template = "interface_smart_blocking_subnet", timeoutTicks = 240)
    public static void normalInterfacePushInvalidatesSmartBlockingAllowance(GameTestHelper helper) {
        SmartBlockingSubnet network = getSmartBlockingSubnet(helper);

        helper.startSequence()
                .thenWaitUntil(
                        "wait for both interface networks to activate",
                        120,
                        () -> assertSmartBlockingSubnetActive(helper, network))
                .thenWaitUntil("wait for both processing patterns to be advertised", 60, () -> {
                    helper.assertFalse(
                            craftingOptionsFor(network.mainController, Blocks.stone).isEmpty(),
                            "Smart interface pattern should be advertised");
                    helper.assertFalse(
                            craftingOptionsFor(network.mainController, Blocks.gravel).isEmpty(),
                            "Normal interface pattern should be advertised");
                }).thenExecute("prime the smart interface recipe allowance", () -> {
                    ICraftingPatternDetails smartPattern = firstPattern(helper, network.mainController, Blocks.stone);
                    boolean pushed = network.smartInterface
                            .pushPattern(smartPattern, craftingTable(Blocks.cobblestone, 1));

                    helper.assertTrue(pushed, "Smart interface should push its first pattern into an empty subnet");
                    assertNetworkStoredAmount(helper, network.subnetController, Blocks.cobblestone, 1);
                }).thenExecute("empty the subnet without changing the smart interface allowance", () -> {
                    IAEItemStack extracted = itemMonitor(network.subnetController).extractItems(
                            itemStack(Blocks.cobblestone, 1),
                            Actionable.MODULATE,
                            new BaseActionSource());

                    helper.assertNotNull(extracted, "Priming input should be extractable from the subnet");
                    helper.assertEquals(1L, extracted.getStackSize(), "Exactly one priming input should be extracted");
                    assertNetworkStoredAmount(helper, network.subnetController, Blocks.cobblestone, 0);
                }).thenExecute("push a pattern from the normal blocking interface", () -> {
                    ICraftingPatternDetails normalPattern = firstPattern(helper, network.mainController, Blocks.gravel);
                    boolean pushed = network.normalInterface.pushPattern(normalPattern, craftingTable(Blocks.dirt, 1));

                    helper.assertTrue(pushed, "Normal blocking interface should push into the empty subnet");
                    assertNetworkStoredAmount(helper, network.subnetController, Blocks.dirt, 1);
                }).thenExecute("reject the stale smart-blocking recipe allowance", () -> {
                    ICraftingPatternDetails smartPattern = firstPattern(helper, network.mainController, Blocks.stone);
                    boolean pushed = network.smartInterface
                            .pushPattern(smartPattern, craftingTable(Blocks.cobblestone, 1));

                    helper.assertFalse(
                            pushed,
                            "Smart interface should block after the normal interface starts a new subnet batch");
                    assertNetworkStoredAmount(helper, network.subnetController, Blocks.dirt, 1);
                    assertNetworkStoredAmount(helper, network.subnetController, Blocks.cobblestone, 0);
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
                        () -> InventoryHelper.setSlot(
                                network.blockInterface.getInterfaceDuality().getPatterns(),
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

    // Pattern details exposed to crafting providers should contain inputs expanded from Tunnel Patterns.
    @GameTest(template = "interface_network", timeoutTicks = 160)
    public static void interfacePatternDetailsExposeExpandedTunnelInputs(GameTestHelper helper) {
        InterfaceNetwork network = getInterfaceNetwork(helper);
        UUID tunnelUuid = UUID.randomUUID();
        ItemStack tunnelPattern = encodedTunnelPattern(tunnelUuid, Blocks.cobblestone, 3);
        ItemStack tunnelReference = tunnelPattern.copy();
        tunnelReference.stackSize = 2;
        ItemStack referencingPattern = encodedProcessingPattern(tunnelReference, Blocks.stone, 1);

        helper.startSequence()
                .thenWaitUntil(
                        "wait for tunnel-pattern network to activate",
                        80,
                        () -> assertInterfaceNetworkActive(helper, network))
                .thenExecute("install Tunnel Pattern definition and referencing processing pattern", () -> {
                    InventoryHelper
                            .setSlot(network.blockInterface.getInterfaceDuality().getPatterns(), 0, tunnelPattern);
                    InventoryHelper
                            .setSlot(network.blockInterface.getInterfaceDuality().getPatterns(), 1, referencingPattern);
                    InventoryHelper.setSlot(
                            network.partInterface.getInterfaceDuality().getPatterns(),
                            0,
                            referencingPattern.copy());
                })
                .thenWaitUntil(
                        "wait for referencing pattern output to become craftable",
                        80,
                        () -> helper.assertFalse(
                                craftingOptionsFor(network.controller, Blocks.stone).isEmpty(),
                                "Referencing pattern output should be craftable"))
                .thenExecute("verify the advertised pattern details expose expanded inputs", () -> {
                    assertExpandedTunnelInputs(
                            helper,
                            firstPattern(helper, network.controller, Blocks.stone),
                            "Crafting cache");
                    assertProviderPatternInputs(helper, network.blockInterface, "Block interface");
                    assertProviderPatternInputs(helper, network.partInterface, "Part interface");
                }).thenSucceed();
    }

    // Block and part interfaces should both maintain configured stock in their internal inventories.
    @GameTest(template = "interface_network", timeoutTicks = 180)
    public static void partAndBlockInterfacesExposeSameStockBehavior(GameTestHelper helper) {
        InterfaceNetwork network = getInterfaceNetwork(helper);
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, Blocks.cobblestone, 64);
        insertItems(helper, driveCell, Blocks.dirt, 64);
        helper.setSlot(DRIVE_LABEL, 0, driveCell);

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
                    assertStoredAmount(helper, network.drive.getStackInSlot(0), Blocks.cobblestone, 48);
                    assertStoredAmount(helper, network.drive.getStackInSlot(0), Blocks.dirt, 48);
                }).thenSucceed();
    }

    // If the interface storage already satisfies the configured stock amount, ME storage should not be drained.
    @GameTest(template = "interface_network", timeoutTicks = 120)
    public static void interfaceDoesNotOverstock(GameTestHelper helper) {
        InterfaceNetwork network = getInterfaceNetwork(helper);
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, Blocks.cobblestone, 64);
        helper.setSlot(DRIVE_LABEL, 0, driveCell);
        ContinuousInvariant configuredStockDoesNotDrainNetwork = continuousInvariant(
                helper,
                "already satisfied interface stock must not drain ME storage",
                () -> {
                    assertInterfaceStoredAmount(helper, network.blockInterface, Blocks.cobblestone, STOCK_AMOUNT);
                    assertStoredAmount(helper, network.drive.getStackInSlot(0), Blocks.cobblestone, 64);
                });

        helper.startSequence()
                .thenWaitUntil(
                        "wait for no-overstock interface network to activate",
                        80,
                        () -> assertInterfaceNetworkActive(helper, network))
                .thenExecute("pre-stock interface and enable no-overstock invariant", () -> {
                    configureStock(network.blockInterface, Blocks.cobblestone, STOCK_AMOUNT);
                    InventoryHelper.setSlot(
                            network.blockInterface.getInterfaceDuality().getStorage(),
                            0,
                            new ItemStack(Blocks.cobblestone, STOCK_AMOUNT));
                    configuredStockDoesNotDrainNetwork.enable();
                }).thenIdle(30)
                .thenExecute("finish no-overstock observation window", configuredStockDoesNotDrainNetwork::disable)
                .thenSucceed();
    }

    private static InterfaceNetwork getInterfaceNetwork(GameTestHelper helper) {
        TileController controller = helper.assertTileEntityPresent(TileController.class, CONTROLLER_LABEL);
        TileDrive drive = helper.assertTileEntityPresent(TileDrive.class, DRIVE_LABEL);
        TileInterface blockInterface = helper.assertTileEntityPresent(TileInterface.class, BLOCK_INTERFACE_LABEL);
        PartInterface partInterface = part(helper, PART_INTERFACE_HOST_LABEL, PartInterface.class);
        helper.assertTileEntityPresent(TileEntityChest.class, ADJACENT_CHEST_LABEL);
        helper.assertTileEntityPresent(TileMolecularAssembler.class, ASSEMBLER_LABEL);

        return new InterfaceNetwork(controller, drive, blockInterface, partInterface);
    }

    private static SmartBlockingSubnet getSmartBlockingSubnet(GameTestHelper helper) {
        return new SmartBlockingSubnet(
                helper.assertTileEntityPresent(TileController.class, MAIN_CONTROLLER_LABEL),
                helper.assertTileEntityPresent(TileController.class, SUBNET_CONTROLLER_LABEL),
                helper.assertTileEntityPresent(TileInterface.class, SMART_INTERFACE_LABEL),
                helper.assertTileEntityPresent(TileInterface.class, NORMAL_INTERFACE_LABEL));
    }

    private static void assertSmartBlockingSubnetActive(GameTestHelper helper, SmartBlockingSubnet network) {
        assertActive(helper, network.mainController.getProxy(), "Main controller should become active");
        assertActive(helper, network.subnetController.getProxy(), "Subnet controller should become active");
        assertActive(
                helper,
                network.smartInterface.getProxy(),
                "Smart interface should receive a main-network channel");
        assertActive(
                helper,
                network.normalInterface.getProxy(),
                "Normal interface should receive a main-network channel");
    }

    private static void assertInterfaceNetworkActive(GameTestHelper helper, InterfaceNetwork network) {
        assertActive(helper, network.controller.getProxy(), "Controller grid proxy should become active");
        assertActive(helper, network.drive.getProxy(), "Drive grid proxy should become active");
        assertActive(helper, network.blockInterface.getProxy(), "Block interface should receive a channel");
        assertActive(helper, network.partInterface, "Part interface should receive a channel");
    }

    private static void configureStock(IInterfaceHost interfaceHost, Block block, int amount) {
        InventoryHelper.setSlot(interfaceHost.getInterfaceDuality().getConfig(), 0, new ItemStack(block, amount));
    }

    private static void assertInterfaceStoredAmount(GameTestHelper helper, IInterfaceHost interfaceHost, Block block,
            long expectedAmount) {
        Object blockId = Block.blockRegistry.getNameForObject(block);
        helper.assertEquals(
                expectedAmount,
                interfaceStoredAmount(interfaceHost, block),
                "Interface storage for " + (blockId == null ? block.getUnlocalizedName() : blockId)
                        + " should match; host="
                        + interfaceHost.getClass().getSimpleName());
    }

    private static long interfaceStoredAmount(IInterfaceHost interfaceHost, Block block) {
        return InventoryHelper.count(interfaceHost.getInterfaceDuality().getStorage(), new ItemStack(block));
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

    private static void assertProviderPatternInputs(GameTestHelper helper, IInterfaceHost interfaceHost,
            String source) {
        helper.assertNotNull(interfaceHost.getInterfaceDuality().craftingList, source + " should cache its patterns");
        int processingPatterns = 0;
        for (ICraftingPatternDetails details : interfaceHost.getInterfaceDuality().craftingList) {
            if (!details.isInputOnly()) {
                assertExpandedTunnelInputs(helper, details, source);
                processingPatterns++;
            }
        }
        helper.assertEquals(1, processingPatterns, source + " should contain one processing pattern");
    }

    private static void assertExpandedTunnelInputs(GameTestHelper helper, ICraftingPatternDetails details,
            String source) {
        IAEStack<?>[] inputs = details.getAEInputs();

        helper.assertEquals(1, inputs.length, source + " Tunnel Pattern should expand to one input");
        helper.assertTrue(inputs[0] instanceof IAEItemStack, source + " expanded input should be an item stack");
        IAEItemStack input = (IAEItemStack) inputs[0];
        helper.assertTrue(
                Platform.isSameItem(input.getItemStack(), new ItemStack(Blocks.cobblestone)),
                source + " should expose cobblestone instead of the Tunnel Pattern item");
        helper.assertEquals(6L, input.getStackSize(), source + " Tunnel Pattern multiplier should be applied");
    }

    private static MEInventoryCrafting craftingTable(Block block, int amount) {
        MEInventoryCrafting table = new MEInventoryCrafting(new ContainerNull(), 1, 1);
        table.setInventorySlotContents(0, itemStack(block, amount));
        return table;
    }

    private static ItemStack encodedProcessingPattern(Block input, int inputAmount, Block output, int outputAmount) {
        return encodedProcessingPattern(new ItemStack(input, inputAmount), output, outputAmount);
    }

    private static ItemStack encodedProcessingPattern(ItemStack input, Block output, int outputAmount) {
        ItemStack encodedPattern = AEApi.instance().definitions().items().encodedPattern().maybeStack(1).get();
        NBTTagCompound patternTags = new NBTTagCompound();
        NBTTagList inputs = new NBTTagList();
        NBTTagList outputs = new NBTTagList();

        patternTags.setBoolean("crafting", false);
        patternTags.setBoolean("substitute", false);
        patternTags.setBoolean("beSubstitute", false);
        inputs.appendTag(itemTag(input));
        outputs.appendTag(itemTag(output, outputAmount));
        patternTags.setTag("in", inputs);
        patternTags.setTag("out", outputs);
        encodedPattern.setTagCompound(patternTags);

        return encodedPattern;
    }

    private static ItemStack encodedTunnelPattern(UUID uuid, Block input, int inputAmount) {
        ItemStack encodedPattern = AEApi.instance().definitions().items().encodedTunnelPattern().maybeStack(1).get();
        NBTTagCompound patternTags = new NBTTagCompound();
        NBTTagList inputs = new NBTTagList();

        patternTags.setBoolean("crafting", false);
        patternTags.setBoolean("substitute", false);
        patternTags.setBoolean("beSubstitute", false);
        ItemTunnelPattern.writeTunnelUuid(patternTags, uuid);
        inputs.appendTag(itemStack(input, inputAmount).toNBTGeneric());
        patternTags.setTag("in", inputs);
        patternTags.setTag("out", new NBTTagList());
        encodedPattern.setTagCompound(patternTags);

        return encodedPattern;
    }

    private static NBTTagCompound itemTag(Block block, int amount) {
        return itemTag(new ItemStack(block, amount));
    }

    private static NBTTagCompound itemTag(ItemStack item) {
        NBTTagCompound tag = new NBTTagCompound();
        Platform.writeItemStackToNBT(item, tag);
        return tag;
    }

    @Desugar
    private record InterfaceNetwork(TileController controller, TileDrive drive, TileInterface blockInterface,
            PartInterface partInterface) {}

    @Desugar
    private record SmartBlockingSubnet(TileController mainController, TileController subnetController,
            TileInterface smartInterface, TileInterface normalInterface) {}
}
