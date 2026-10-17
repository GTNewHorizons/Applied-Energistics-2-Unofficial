package appeng.gametests.patterns;

import java.util.UUID;

import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.AEApi;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.core.AppEng;
import appeng.items.misc.ItemTunnelPattern;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.tile.networking.TileCableBus;
import appeng.util.item.AEItemStack;

@GameTestHolder(AppEng.MOD_ID)
public class TunnelPatternEncodingTests {

    @GameTest(template = "interface_network", timeoutTicks = 20)
    public static void reencodingPreservesNameAndUuid(GameTestHelper helper) {
        PartPatternTerminal terminal = createTerminal(helper);
        ItemStack original = encodeTunnel(helper, terminal);
        original.setStackDisplayName("Protection gas");
        UUID uuid = ItemTunnelPattern.getTunnelUuid(original);

        terminal.getAEInventoryByName(StorageName.CRAFTING_INPUT)
                .putAEStackInSlot(0, AEItemStack.create(new ItemStack(Blocks.dirt, 5)));
        ItemStack updated = encode(helper, terminal);
        helper.assertEquals("Protection gas", updated.getDisplayName(), "Re-encoding should preserve the custom name");
        helper.assertEquals(uuid, ItemTunnelPattern.getTunnelUuid(updated), "Re-encoding should preserve the UUID");
        IAEStack<?> input = terminal.getAEInventoryByName(StorageName.CRAFTING_INPUT).getAEStackInSlot(0);
        helper.assertTrue(
                input.getItemStackForNEI().getItem() == new ItemStack(Blocks.dirt).getItem(),
                "Re-encoding should update the input item");
        helper.assertEquals(5L, input.getStackSize(), "Re-encoding should update the input amount");
        helper.succeed();
    }

    @GameTest(template = "interface_network", timeoutTicks = 20)
    public static void reencodingDoesNotCreateCustomName(GameTestHelper helper) {
        PartPatternTerminal terminal = createTerminal(helper);
        encodeTunnel(helper, terminal);
        ItemStack updated = encode(helper, terminal);
        helper.assertFalse(updated.hasDisplayName(), "An unnamed Tunnel Pattern should remain unnamed");
        helper.succeed();
    }

    @GameTest(template = "interface_network", timeoutTicks = 20)
    public static void convertingToProcessingDoesNotCopyTunnelName(GameTestHelper helper) {
        PartPatternTerminal terminal = createTerminal(helper);
        encodeTunnel(helper, terminal).setStackDisplayName("Protection gas");
        terminal.getAEInventoryByName(StorageName.CRAFTING_OUTPUT)
                .putAEStackInSlot(0, AEItemStack.create(new ItemStack(Blocks.stone)));
        ItemStack updated = encode(helper, terminal);
        helper.assertFalse(
                ItemTunnelPattern.isTunnelPattern(updated),
                "Adding an output should create a processing pattern");
        helper.assertFalse(updated.hasDisplayName(), "A processing pattern should not inherit the Tunnel Pattern name");
        helper.succeed();
    }

    private static PartPatternTerminal createTerminal(GameTestHelper helper) {
        TileCableBus host = helper.assertTileEntityPresent(TileCableBus.class, "block_interface_cable");
        ItemStack item = AEApi.instance().definitions().parts().patternTerminal().maybeStack(1).get();
        helper.assertNotNull(host.addPart(item, ForgeDirection.UP, null), "Pattern Terminal should fit on the cable");
        helper.assertTrue(
                host.getPart(ForgeDirection.UP) instanceof PartPatternTerminal,
                "Pattern Terminal should exist");
        PartPatternTerminal terminal = (PartPatternTerminal) host.getPart(ForgeDirection.UP);
        terminal.setCraftingRecipe(false);
        IInventory pattern = terminal.getInventoryByName("pattern");
        pattern.setInventorySlotContents(
                0,
                AEApi.instance().definitions().materials().blankPattern().maybeStack(1).get());
        terminal.getAEInventoryByName(StorageName.CRAFTING_INPUT)
                .putAEStackInSlot(0, AEItemStack.create(new ItemStack(Blocks.cobblestone, 2)));
        return terminal;
    }

    private static ItemStack encodeTunnel(GameTestHelper helper, PartPatternTerminal terminal) {
        ItemStack pattern = encode(helper, terminal);
        helper.assertTrue(
                ItemTunnelPattern.isTunnelPattern(pattern),
                "Input-only encoding should create a Tunnel Pattern");
        return pattern;
    }

    private static ItemStack encode(GameTestHelper helper, PartPatternTerminal terminal) {
        helper.assertTrue(
                terminal.encode(null, null, new BaseActionSource(), "test", terminal.getTile().getWorldObj()),
                "Pattern encoding should succeed without a network extraction");
        return terminal.getInventoryByName("pattern").getStackInSlot(1);
    }
}
