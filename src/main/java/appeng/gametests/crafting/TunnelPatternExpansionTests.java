package appeng.gametests.crafting;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.AEApi;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.WorldCoord;
import appeng.core.AppEng;
import appeng.helpers.UltimatePatternHelper;
import appeng.items.misc.ItemTunnelPattern;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.Platform;
import appeng.util.TunnelPatternExpander;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;

@GameTestHolder(AppEng.MOD_ID)
public class TunnelPatternExpansionTests {

    @GameTest
    public static void ordinaryInputsAvoidItemStackCopiesAndKeepIndependentMutableResult(GameTestHelper helper) {
        final CountingItem item = new CountingItem();
        final IAEStack<?> fluid = AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1_000));
        final IAEStack<?>[] inputs = { item.tracked, null, fluid };

        final List<IAEStack<?>> expanded = TunnelPatternExpander.expandInputs(inputs, null, null);

        helper.assertEquals(0L, item.materializations, "Ordinary inputs should not allocate ItemStacks or copy NBT");
        helper.assertEquals(2L, expanded.size(), "Null slots should be filtered while retaining items and fluids");
        helper.assertTrue(expanded.get(0) == item.tracked, "Ordinary item inputs should retain their stack identity");
        helper.assertTrue(expanded.get(1) == fluid, "Ordinary fluid inputs should retain their stack identity");
        expanded.set(0, fluid);
        expanded.clear();
        expanded.add(item.tracked);
        helper.assertTrue(inputs[0] == item.tracked, "Changing the result must not modify the input array");
        helper.assertEquals(3L, inputs.length, "Changing the result must not modify the input array length");
        helper.succeed();
    }

    @GameTest
    public static void ordinaryCondensedInputsAvoidRepeatedCopies(GameTestHelper helper) {
        final CountingItem item = new CountingItem();
        final IAEStack<?>[] inputs = { item.tracked };
        final ICraftingPatternDetails pattern = (ICraftingPatternDetails) Proxy.newProxyInstance(
                ICraftingPatternDetails.class.getClassLoader(),
                new Class<?>[] { ICraftingPatternDetails.class },
                (ignored, method, args) -> {
                    if (method.getName().equals("isCraftable")) return false;
                    if (method.getName().equals("getCondensedAEInputs")) return inputs;
                    throw new UnsupportedOperationException(method.getName());
                });
        final ProbeCPU cpu = new ProbeCPU();
        final DefinitionCache cache = new DefinitionCache();

        for (int visit = 0; visit < 128; visit++) {
            final List<IAEStack<?>> expanded = cpu.condensedInputs(pattern, cache);
            helper.assertEquals(4L, expanded.get(0).getStackSize(), "Each visit should retain the encoded amount");
        }

        helper.assertEquals(0L, item.copies, "Already-condensed ordinary inputs should not be copied again");
        helper.assertEquals(0L, item.materializations, "Ordinary task visits should not allocate ItemStacks");
        helper.assertEquals(0L, cache.resolutions, "Ordinary task visits should not resolve tunnel definitions");
        helper.succeed();
    }

    @GameTest
    public static void nestedTunnelsPreserveAmountsAndRefreshDefinitions(GameTestHelper helper) {
        final UUID innerId = UUID.randomUUID();
        final UUID outerId = UUID.randomUUID();
        final DefinitionCache cache = new DefinitionCache();
        cache.define(innerId, AEItemStack.create(new ItemStack(Items.iron_ingot, 3)));
        cache.define(outerId, reference(innerId, 2));
        final IAEStack<?>[] inputs = { reference(outerId, 4), reference(outerId, 1) };

        List<IAEStack<?>> expanded = TunnelPatternExpander.expandInputs(inputs, cache, null);
        helper.assertTrue(expanded != null, "Repeated independent references should expand");
        helper.assertEquals(2L, expanded.size(), "Both independent references should remain in the result");
        helper.assertEquals(
                24L,
                expanded.get(0).getStackSize(),
                "Nested multipliers should apply to the first reference");
        helper.assertEquals(
                6L,
                expanded.get(1).getStackSize(),
                "Nested multipliers should apply to the second reference");
        expanded.get(0).setStackSize(1);

        cache.define(innerId, AEItemStack.create(new ItemStack(Items.iron_ingot, 5)));
        expanded = TunnelPatternExpander.expandInputs(inputs, cache, null);
        helper.assertEquals(40L, expanded.get(0).getStackSize(), "Later visits should resolve the updated definition");
        helper.assertEquals(10L, expanded.get(1).getStackSize(), "Later visits should apply updated nested amounts");
        helper.assertEquals(4L, inputs[0].getStackSize(), "Expansion must not modify the encoded reference");
        helper.succeed();
    }

    @GameTest
    public static void cyclesOverflowAndUnresolvedReferencesKeepExistingBehavior(GameTestHelper helper) {
        final UUID id = UUID.randomUUID();
        final DefinitionCache cache = new DefinitionCache();
        final IAEStack<?> reference = reference(id, 2);
        final IAEStack<?>[] inputs = { reference };
        helper.assertTrue(
                TunnelPatternExpander.expandInputs(inputs, cache, null).get(0) == reference,
                "An unavailable definition should retain the original reference");

        cache.define(id, reference(id, 1));
        helper.assertTrue(
                TunnelPatternExpander.expandInputs(inputs, cache, null) == null,
                "Cycles should fail expansion");

        cache.define(id, AEItemStack.create(new ItemStack(Items.iron_ingot)).setStackSize(Long.MAX_VALUE));
        helper.assertTrue(
                TunnelPatternExpander.expandInputs(inputs, cache, null) == null,
                "Overflowing amounts should fail expansion");

        cache.define(id, AEItemStack.create(new ItemStack(Items.iron_ingot, 3)));
        helper.assertTrue(
                TunnelPatternExpander.expandInputs(inputs, cache, Collections.singleton(cache.getInputOnlyPattern(id)))
                        == null,
                "A parent-pattern cycle should fail expansion");
        helper.assertEquals(
                6L,
                TunnelPatternExpander.expandInputs(inputs, cache, null).get(0).getStackSize(),
                "Failed expansion must not leave recursion state in later calls");
        helper.succeed();
    }

    private static IAEItemStack reference(UUID id, int amount) {
        return AEItemStack.create(tunnelPattern(id, amount));
    }

    private static ItemStack tunnelPattern(UUID id, int amount) {
        final ItemStack stack = AEApi.instance().definitions().items().encodedTunnelPattern().maybeStack(amount).get();
        final NBTTagCompound tag = new NBTTagCompound();
        ItemTunnelPattern.writeTunnelUuid(tag, id);
        stack.setTagCompound(tag);
        return stack;
    }

    private static final class DefinitionCache extends CraftingGridCache {

        private long resolutions;

        private DefinitionCache() {
            super(null);
        }

        private void define(UUID id, IAEStack<?>... inputs) {
            final ItemStack stack = tunnelPattern(id, 1);
            final NBTTagList encodedInputs = new NBTTagList();
            for (final IAEStack<?> input : inputs) {
                final NBTTagCompound tag = new NBTTagCompound();
                Platform.writeStackNBT(input, tag, true);
                encodedInputs.appendTag(tag);
            }
            stack.getTagCompound().setTag("in", encodedInputs);
            stack.getTagCompound().setTag("out", new NBTTagList());
            inputOnlyPatterns.put(id, new UltimatePatternHelper(stack));
        }

        @Override
        public ICraftingPatternDetails getInputOnlyPattern(UUID id) {
            resolutions++;
            return super.getInputOnlyPattern(id);
        }
    }

    private static final class ProbeCPU extends CraftingCPUCluster {

        private ProbeCPU() {
            super(new WorldCoord(0, 0, 0), new WorldCoord(0, 0, 0));
        }

        private List<IAEStack<?>> condensedInputs(ICraftingPatternDetails pattern, CraftingGridCache cache) {
            return getExpandedCondensedInputs(pattern, cache);
        }
    }

    private static final class CountingItem {

        private final IAEItemStack item;
        private final IAEItemStack tracked;
        private long materializations;
        private long copies;

        private CountingItem() {
            final ItemStack stack = new ItemStack(Items.iron_ingot, 4);
            final NBTTagCompound tag = new NBTTagCompound();
            tag.setString("payload", "An ordinary NBT-bearing ingredient should not have its NBT copied for detection");
            stack.setTagCompound(tag);
            item = AEItemStack.create(stack);
            tracked = (IAEItemStack) Proxy.newProxyInstance(
                    IAEItemStack.class.getClassLoader(),
                    new Class<?>[] { IAEItemStack.class },
                    (ignored, method, args) -> {
                        if (method.getName().equals("getItemStack")) materializations++;
                        if (method.getName().equals("copy")) copies++;
                        try {
                            return method.invoke(item, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }
    }
}
