package appeng.test.me.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.UUID;

import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.common.collect.ImmutableCollection;

import appeng.api.AEApi;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.helpers.IResolvablePatternDetails;
import appeng.helpers.PatternHelper;
import appeng.helpers.UltimatePatternHelper;
import appeng.items.misc.ItemTunnelPattern;
import appeng.me.cache.CraftingGridCache;
import appeng.parts.misc.PartPatternRepeater;
import appeng.test.mockme.MockCraftingMedium;
import appeng.test.mockme.MockGrid;
import appeng.util.TunnelPatternExpander;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.objects.GTDualInputPattern;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME.PatternSlot;

public class TunnelPatternResolutionFunctionalTest {

    private MockGrid grid;
    private TestCraftingCache cache;
    private final ICraftingMedium medium = new MockCraftingMedium();

    @BeforeEach
    void setUp() {
        grid = new MockGrid();
        cache = new TestCraftingCache(grid);
    }

    @AfterEach
    void tearDown() {
        grid.rootNode.destroy();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void providersRetainResolvedItemAndFluidInputs(boolean ultimate) {
        UUID uuid = UUID.randomUUID();
        ItemStack tunnel = tunnel(uuid, item(3), fluid(FluidRegistry.WATER, 1_000), item(2));
        IResolvablePatternDetails pattern = processing(reference(tunnel, 2), ultimate);
        IResolvablePatternDetails equalProvider = processing(reference(tunnel, 2), ultimate);
        cache.addCraftingOption(medium, new UltimatePatternHelper(tunnel));
        cache.addCraftingOption(medium, pattern);
        cache.addCraftingOption(new MockCraftingMedium(), equalProvider);

        cache.setMockPatternsFromMethods();

        assertEquals(pattern, equalProvider);
        for (IResolvablePatternDetails provider : new IResolvablePatternDetails[] { pattern, equalProvider }) {
            assertEquals(2, provider.getAEInputs().length);
            assertItem(provider.getAEInputs()[0], 10);
            assertFluid(provider.getAEInputs()[1], FluidRegistry.WATER, 2_000);
            assertItem(provider.getCondensedAEInputs()[0], 10);
            assertFluid(provider.getCondensedAEInputs()[1], FluidRegistry.WATER, 2_000);
            assertTrue(ItemTunnelPattern.isTunnelPattern(provider.getEncodedAEInputs()[0].getItemStackForNEI()));
        }
        assertNotSame(pattern.getAEInputs(), equalProvider.getAEInputs());
        assertEquals(2, cache.getMediums(pattern).size(), "Equal patterns must retain both providers");
    }

    @Test
    void ordinaryProcessingPatternsKeepTheirOriginalInputSnapshots() {
        for (boolean ultimate : new boolean[] { false, true }) {
            IResolvablePatternDetails pattern = processing(new ItemStack(Blocks.cobblestone, 3), ultimate);
            IAEStack<?>[] inputs = pattern.getAEInputs();
            IAEStack<?>[] condensedInputs = pattern.getCondensedAEInputs();
            cache.addCraftingOption(medium, pattern);
            cache.setMockPatternsFromMethods();
            cache.setMockPatternsFromMethods();

            assertFalse(pattern.requiresInputResolution());
            assertSame(inputs, pattern.getAEInputs());
            assertSame(condensedInputs, pattern.getCondensedAEInputs());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void localDefinitionsTakePrecedenceOverRepeatedDefinitions(boolean repeatedFirst) {
        UUID uuid = UUID.randomUUID();
        ItemStack localDefinition = tunnel(uuid, fluid(FluidRegistry.WATER, 1_000));
        ICraftingPatternDetails local = new UltimatePatternHelper(localDefinition);
        ICraftingPatternDetails repeated = new UltimatePatternHelper(tunnel(uuid, fluid(FluidRegistry.LAVA, 500)));
        PartPatternRepeater repeater = new PartPatternRepeater(
                AEApi.instance().definitions().parts().patternRepeater().maybeStack(1).get());
        if (repeatedFirst) {
            cache.addCraftingOption(repeater, repeated);
            cache.addCraftingOption(medium, local);
        } else {
            cache.addCraftingOption(medium, local);
            cache.addCraftingOption(repeater, repeated);
        }
        IResolvablePatternDetails pattern = processing(reference(localDefinition, 2), false);
        cache.addCraftingOption(medium, pattern);

        cache.setMockPatternsFromMethods();

        assertSame(local, cache.getInputOnlyPattern(uuid));
        assertEquals(1, cache.getInputOnlyPatterns().size());
        assertFluid(pattern.getAEInputs()[0], FluidRegistry.WATER, 2_000);
        assertTrue(
                cache.getCraftingMultiPatterns().values().stream().flatMap(patterns -> patterns.stream())
                        .noneMatch(ICraftingPatternDetails::isInputOnly));
    }

    @Test
    void exportedDefinitionSnapshotIsImmutableAndDoesNotChangeDuringRebuilds() {
        UUID uuid = UUID.randomUUID();
        ICraftingPatternDetails initial = new UltimatePatternHelper(tunnel(uuid, fluid(FluidRegistry.WATER, 1_000)));
        cache.addCraftingOption(medium, initial);
        cache.setMockPatternsFromMethods();
        ImmutableCollection<ICraftingPatternDetails> snapshot = cache.getInputOnlyPatterns();

        cache.replaceDefinition(uuid, new UltimatePatternHelper(tunnel(uuid, fluid(FluidRegistry.LAVA, 500))));

        assertSame(initial, snapshot.iterator().next());
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertNotSame(initial, cache.getInputOnlyPatterns().iterator().next());
        assertTrue(cache.getCraftingMultiPatterns().isEmpty(), "Definitions must not advertise crafting outputs");
    }

    @Test
    void nestedDefinitionsMultiplyInputsWithoutChangingEncodedPatterns() {
        ItemStack inner = tunnel(UUID.randomUUID(), fluid(FluidRegistry.WATER, 1_000));
        ItemStack outer = tunnel(UUID.randomUUID(), AEItemStack.create(reference(inner, 3)), item(1));
        IResolvablePatternDetails pattern = processing(reference(outer, 2), true);
        NBTTagCompound encodedBefore = (NBTTagCompound) pattern.getPattern().getTagCompound().copy();
        cache.addCraftingOption(medium, new UltimatePatternHelper(inner));
        cache.addCraftingOption(medium, new UltimatePatternHelper(outer));
        cache.addCraftingOption(medium, pattern);

        cache.setMockPatternsFromMethods();

        assertEquals(2, pattern.getAEInputs().length);
        assertFluid(pattern.getAEInputs()[0], FluidRegistry.WATER, 6_000);
        assertItem(pattern.getAEInputs()[1], 2);
        assertEquals(encodedBefore, pattern.getPattern().getTagCompound());
    }

    @Test
    void cyclicAndOverflowingDefinitionsDoNotPublishPartialInputs() {
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        ItemStack first = tunnel(firstUuid, item(1));
        ItemStack second = tunnel(secondUuid, AEItemStack.create(first));
        first = tunnel(firstUuid, AEItemStack.create(second));
        IResolvablePatternDetails cyclic = processing(first, false);
        cache.addCraftingOption(medium, new UltimatePatternHelper(first));
        cache.addCraftingOption(medium, new UltimatePatternHelper(second));
        cache.addCraftingOption(medium, cyclic);
        cache.setMockPatternsFromMethods();
        assertSame(cyclic.getEncodedAEInputs(), cyclic.getAEInputs());
        assertNull(TunnelPatternExpander.expandInputs(cyclic.getAEInputs(), cache, null));

        ItemStack overflowing = tunnel(UUID.randomUUID(), item(Long.MAX_VALUE));
        IResolvablePatternDetails overflow = processing(reference(overflowing, 2), true);
        cache.addCraftingOption(medium, new UltimatePatternHelper(overflowing));
        cache.addCraftingOption(medium, overflow);
        cache.setMockPatternsFromMethods();
        assertSame(overflow.getEncodedAEInputs(), overflow.getAEInputs());
        assertNull(TunnelPatternExpander.expandInputs(overflow.getAEInputs(), cache, null));
    }

    @Test
    void definitionUpdatesAndRemovalReplacePreviouslyResolvedInputs() {
        UUID uuid = UUID.randomUUID();
        ItemStack definition = tunnel(uuid, item(3));
        IResolvablePatternDetails pattern = processing(reference(definition, 2), false);
        cache.addCraftingOption(medium, pattern);
        cache.setMockPatternsFromMethods();
        assertTrue(ItemTunnelPattern.isTunnelPattern(pattern.getAEInputs()[0].getItemStackForNEI()));

        cache.replaceDefinition(uuid, new UltimatePatternHelper(definition));
        assertItem(pattern.getAEInputs()[0], 6);
        cache.replaceDefinition(uuid, new UltimatePatternHelper(tunnel(uuid, fluid(FluidRegistry.WATER, 250))));
        assertFluid(pattern.getAEInputs()[0], FluidRegistry.WATER, 500);
        assertFluid(pattern.getCondensedAEInputs()[0], FluidRegistry.WATER, 500);

        cache.replaceDefinition(uuid, null);
        assertTrue(ItemTunnelPattern.isTunnelPattern(pattern.getAEInputs()[0].getItemStackForNEI()));
        assertFalse(
                pattern.getAEInputs()[0] instanceof IAEFluidStack,
                "Removed definitions must not leave stale fluids");
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void exportedCopiesResolveIndependentlyInEitherCacheRebuildOrder(boolean ultimate) {
        MockGrid targetGrid = new MockGrid();
        try {
            TestCraftingCache target = new TestCraftingCache(targetGrid);
            UUID uuid = UUID.randomUUID();
            ItemStack sourceDefinition = tunnel(uuid, fluid(FluidRegistry.WATER, 1_000));
            IResolvablePatternDetails source = processing(reference(sourceDefinition, 2), ultimate);
            source.setPriority(7);
            IResolvablePatternDetails exported = source.copyForGrid(null);
            cache.addCraftingOption(medium, source);
            target.addCraftingOption(medium, exported);
            cache.replaceDefinition(uuid, new UltimatePatternHelper(sourceDefinition));
            target.replaceDefinition(uuid, new UltimatePatternHelper(tunnel(uuid, fluid(FluidRegistry.LAVA, 500))));

            assertNotSame(source, exported);
            assertEquals(source.getClass(), exported.getClass());
            assertEquals(source, exported);
            assertEquals(exported, source);
            assertEquals(source.hashCode(), exported.hashCode());
            assertEquals(7, exported.getPriority());
            assertSame(medium, cache.getMediums(exported).get(0), "The copy must still route to the source provider");
            assertFluid(source.getAEInputs()[0], FluidRegistry.WATER, 2_000);
            assertFluid(exported.getAEInputs()[0], FluidRegistry.LAVA, 1_000);

            target.setMockPatternsFromMethods();
            cache.setMockPatternsFromMethods();
            assertFluid(source.getAEInputs()[0], FluidRegistry.WATER, 2_000);
            assertFluid(exported.getAEInputs()[0], FluidRegistry.LAVA, 1_000);
            cache.replaceDefinition(uuid, new UltimatePatternHelper(tunnel(uuid, item(4))));
            assertItem(source.getAEInputs()[0], 8);
            assertFluid(exported.getAEInputs()[0], FluidRegistry.LAVA, 1_000);
        } finally {
            targetGrid.rootNode.destroy();
        }
    }

    @Test
    void gtInputAssemblyReadsResolvedInputsFromItsRetainedPatternSlot() {
        ItemStack definition = tunnel(UUID.randomUUID(), item(3), fluid(FluidRegistry.WATER, 1_000));
        IResolvablePatternDetails encoded = processing(reference(definition, 2), false);
        MTEHatchCraftingInputME hatch = new MTEHatchCraftingInputME(
                "test_tunnel_pattern",
                6,
                new String[0],
                null,
                true);
        IGregTechTileEntity baseTile = (IGregTechTileEntity) Proxy.newProxyInstance(
                IGregTechTileEntity.class.getClassLoader(),
                new Class<?>[] { IGregTechTileEntity.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getWorld") || method.getName().equals("setMetaTileEntity")) {
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        hatch.setBaseMetaTileEntity(baseTile);
        PatternSlot<MTEHatchCraftingInputME> slot = new PatternSlot<>(encoded.getPattern(), hatch);
        ICraftingPatternDetails retained = slot.getPatternDetails();
        cache.addCraftingOption(medium, new UltimatePatternHelper(definition));
        cache.addCraftingOption(medium, retained);
        cache.setMockPatternsFromMethods();

        GTDualInputPattern inputs = slot.getPatternInputs();

        assertEquals(1, inputs.inputItems.length);
        assertSame(Item.getItemFromBlock(Blocks.cobblestone), inputs.inputItems[0].getItem());
        assertEquals(6, inputs.inputItems[0].stackSize);
        assertEquals(1, inputs.inputFluid.length);
        assertSame(FluidRegistry.WATER, inputs.inputFluid[0].getFluid());
        assertEquals(2_000, inputs.inputFluid[0].amount);
        assertSame(retained, slot.getPatternDetails(), "GT must continue using its original pattern detail instance");
    }

    private static IAEItemStack item(long amount) {
        return AEItemStack.create(new ItemStack(Blocks.cobblestone)).setStackSize(amount);
    }

    private static IAEFluidStack fluid(Fluid type, long amount) {
        return AEFluidStack.create(new FluidStack(type, 1)).setStackSize(amount);
    }

    private static ItemStack reference(ItemStack tunnel, int amount) {
        ItemStack reference = tunnel.copy();
        reference.stackSize = amount;
        return reference;
    }

    private static ItemStack tunnel(UUID uuid, IAEStack<?>... inputs) {
        ItemStack tunnel = AEApi.instance().definitions().items().encodedTunnelPattern().maybeStack(1).get();
        NBTTagCompound tag = tags(inputs, new IAEStack<?>[0]);
        ItemTunnelPattern.writeTunnelUuid(tag, uuid);
        tunnel.setTagCompound(tag);
        return tunnel;
    }

    private static IResolvablePatternDetails processing(ItemStack input, boolean ultimate) {
        ItemStack pattern = (ultimate ? AEApi.instance().definitions().items().encodedUltimatePattern()
                : AEApi.instance().definitions().items().encodedPattern()).maybeStack(1).get();
        pattern.setTagCompound(tags(new IAEStack<?>[] { AEItemStack.create(input) }, new IAEStack<?>[] { item(1) }));
        return ultimate ? new UltimatePatternHelper(pattern) : new PatternHelper(pattern, null);
    }

    private static NBTTagCompound tags(IAEStack<?>[] inputs, IAEStack<?>[] outputs) {
        NBTTagCompound tags = new NBTTagCompound();
        NBTTagList in = new NBTTagList();
        NBTTagList out = new NBTTagList();
        for (IAEStack<?> input : inputs) {
            in.appendTag(input.toNBTGeneric());
        }
        for (IAEStack<?> output : outputs) {
            out.appendTag(output.toNBTGeneric());
        }
        tags.setTag("in", in);
        tags.setTag("out", out);
        tags.setBoolean("crafting", false);
        return tags;
    }

    private static void assertItem(IAEStack<?> input, long amount) {
        assertTrue(input instanceof IAEItemStack);
        assertSame(Item.getItemFromBlock(Blocks.cobblestone), ((IAEItemStack) input).getItem());
        assertEquals(amount, input.getStackSize());
    }

    private static void assertFluid(IAEStack<?> input, Fluid type, long amount) {
        assertTrue(input instanceof IAEFluidStack);
        assertSame(type, ((IAEFluidStack) input).getFluid());
        assertEquals(amount, input.getStackSize());
    }

    private static final class TestCraftingCache extends CraftingGridCache {

        private TestCraftingCache(MockGrid grid) {
            super(grid);
        }

        private void replaceDefinition(UUID uuid, ICraftingPatternDetails definition) {
            craftingMethods.keySet().removeIf(pattern -> uuid.equals(pattern.getInputOnlyUuid()));
            resolvablePatterns.removeIf(pattern -> uuid.equals(pattern.getInputOnlyUuid()));
            if (definition != null) {
                addCraftingOption(new MockCraftingMedium(), definition);
            }
            setMockPatternsFromMethods();
        }
    }
}
