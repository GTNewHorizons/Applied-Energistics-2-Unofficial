package appeng.gametests.compatibility.appliedenergistics2_ae2fc;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.fluidStack;
import static appeng.gametests.AEGameTestHelpers.part;
import static appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.glodblock.github.common.parts.PartFluidStorageBus;
import com.glodblock.github.common.tile.TileCertusQuartzTank;
import com.glodblock.github.loader.ItemAndBlockHolder;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.Settings;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.security.ReshuffleActionSource;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEFluidStack;
import appeng.core.AppEng;
import appeng.parts.misc.PartStorageBus;
import appeng.tile.networking.TileController;

@GameTestHolder(value = AppEng.MOD_ID, requiredMods = "ae2fc")
public class FluidStorageBusReshuffleTests {

    private static final String CONTROLLER_LABEL = "controller";
    private static final String STORAGE_BUS_LABEL = "storage_bus";
    private static final String EXTERNAL_TANK_LABEL = "external_chest";

    @GameTest(template = "storage_bus", timeoutTicks = 180)
    public static void fluidBusRespectsReshufflerAndNormalAccess(GameTestHelper helper) {
        TileController controller = helper.assertTileEntityPresent(TileController.class, CONTROLLER_LABEL);
        Fluid water = FluidRegistry.WATER;
        helper.assertNotNull(water, "Water fluid should be registered");

        helper.setBlock(EXTERNAL_TANK_LABEL, ItemAndBlockHolder.CERTUS_QUARTZ_TANK);
        TileCertusQuartzTank tank = helper.assertTileEntityPresent(TileCertusQuartzTank.class, EXTERNAL_TANK_LABEL);
        helper.assertEquals(
                8_000,
                tank.fill(ForgeDirection.UNKNOWN, new FluidStack(water, 8_000), true),
                "External tank should start with 8,000 mB of water");

        PartStorageBus oldBus = part(helper, STORAGE_BUS_LABEL, PartStorageBus.class);
        IPartHost host = oldBus.getHost();
        ForgeDirection side = oldBus.getSide();
        host.removePart(side, true);
        ItemStack fluidBusItem = new ItemStack(ItemAndBlockHolder.FLUID_STORAGE_BUS);
        helper.assertEquals(side, host.addPart(fluidBusItem, side, null), "Fluid storage bus should be placed");
        IPart placedPart = host.getPart(side);
        helper.assertTrue(placedPart instanceof PartFluidStorageBus, "Replacement should be an AE2 FC fluid bus");
        PartFluidStorageBus bus = (PartFluidStorageBus) placedPart;

        helper.startSequence().thenWaitUntil("wait for fluid storage bus inventory", 80, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, bus, "Fluid storage bus should receive a channel");
            helper.assertEquals(1, bus.getCellArray(FLUID_STACK_TYPE).size(), "Fluid inventory should be exposed");
            helper.assertNotNull(
                    fluidInventory(bus)
                            .extractItems(fluidStack(water, 1_000), Actionable.SIMULATE, new MachineSource(controller)),
                    "Fluid bus should expose the external tank's water");
        }).thenExecute("verify real fluid transfers and reshuffler access modes", () -> {
            IMEInventoryHandler<IAEFluidStack> inventory = fluidInventory(bus);
            ReshuffleActionSource reshuffleSource = new ReshuffleActionSource(controller);
            MachineSource ordinarySource = new MachineSource(controller);

            IAEFluidStack extracted = inventory
                    .extractItems(fluidStack(water, 1_000), Actionable.MODULATE, reshuffleSource);
            helper.assertNotNull(extracted, "Reshuffler should extract water from the external tank");
            helper.assertEquals(1_000L, extracted.getStackSize(), "Reshuffler should extract 1,000 mB");
            helper.assertEquals(7_000, tank.tank.getFluidAmount(), "External tank should lose the extracted water");
            helper.assertNull(
                    inventory.injectItems(extracted, Actionable.MODULATE, reshuffleSource),
                    "Reshuffler should return water to the external tank");
            helper.assertEquals(8_000, tank.tank.getFluidAmount(), "External tank should regain the water");

            for (AccessRestriction access : AccessRestriction.values()) {
                bus.getConfigManager().putSetting(Settings.RESHUFFLE_ACCESS, access);
                helper.assertEquals(access, inventory.getReshuffleAccess(), "Fluid bus should report its access");
                helper.assertEquals(
                        access.hasPermission(AccessRestriction.READ),
                        inventory.extractItems(fluidStack(water, 1_000), Actionable.SIMULATE, reshuffleSource) != null,
                        access + " should control reshuffler fluid extraction");
                helper.assertEquals(
                        access.hasPermission(AccessRestriction.WRITE),
                        inventory.injectItems(fluidStack(water, 1_000), Actionable.SIMULATE, reshuffleSource) == null,
                        access + " should control reshuffler fluid insertion");
                helper.assertNotNull(
                        inventory.extractItems(fluidStack(water, 1_000), Actionable.SIMULATE, ordinarySource),
                        "Ordinary fluid extraction should remain allowed");
                helper.assertNull(
                        inventory.injectItems(fluidStack(water, 1_000), Actionable.SIMULATE, ordinarySource),
                        "Ordinary fluid insertion should remain allowed");
            }

            bus.getConfigManager().putSetting(Settings.ACCESS, AccessRestriction.WRITE);
            bus.getConfigManager().putSetting(Settings.RESHUFFLE_ACCESS, AccessRestriction.READ);
        }).thenWaitUntil("wait for contradictory fluid bus access settings", 80, () -> {
            IMEInventoryHandler<IAEFluidStack> inventory = fluidInventory(bus);
            helper.assertEquals(AccessRestriction.WRITE, inventory.getAccess(), "Bus should be input-only");
        }).thenExecute("verify conflicting settings deny reshuffler fluid transfers", () -> {
            IMEInventoryHandler<IAEFluidStack> inventory = fluidInventory(bus);
            ReshuffleActionSource reshuffleSource = new ReshuffleActionSource(controller);
            helper.assertEquals(
                    AccessRestriction.NO_ACCESS,
                    inventory.getReshuffleAccess(),
                    "Input-only bus plus extract-only reshuffler setting should deny both directions");
            helper.assertNull(
                    inventory.extractItems(fluidStack(water, 1_000), Actionable.SIMULATE, reshuffleSource),
                    "Conflicting settings should deny reshuffler fluid extraction");
            helper.assertNotNull(
                    inventory.injectItems(fluidStack(water, 1_000), Actionable.SIMULATE, reshuffleSource),
                    "Conflicting settings should deny reshuffler fluid insertion");
        }).thenSucceed();
    }

    @SuppressWarnings("unchecked")
    private static IMEInventoryHandler<IAEFluidStack> fluidInventory(PartFluidStorageBus bus) {
        return (IMEInventoryHandler<IAEFluidStack>) bus.getCellArray(FLUID_STACK_TYPE).get(0);
    }
}
