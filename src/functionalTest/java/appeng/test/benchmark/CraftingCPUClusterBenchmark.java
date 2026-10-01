package appeng.test.benchmark;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

import net.minecraft.init.Blocks;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.DimensionManager;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.WorldCoord;
import appeng.helpers.PatternHelper;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.test.DummySaveHandler;
import appeng.test.mockme.MockGrid;
import appeng.tile.crafting.TileCraftingTile;
import appeng.util.inv.MEInventoryCrafting;
import appeng.util.item.AEItemStack;

/** Opt-in timing harness; deliberately excluded from normal JUnit discovery. */
public final class CraftingCPUClusterBenchmark {

    private static final String PROPERTY = "ae2.craftingBenchmark.";

    private CraftingCPUClusterBenchmark() {}

    public static void run() {
        final long units = Long.parseLong(System.getProperty(PROPERTY + "units", "10000000"));
        final int operations = positiveInt("operations", 4096);
        final int warmup = positiveInt("warmup", 10);
        final int samples = positiveInt("samples", 7);
        if (units <= 0 || units % 8 != 0 || units / 8 > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("units must be a positive multiple of 8, with at most 2^31-1 crafts");
        }
        final int crafts = (int) (units / 8);
        final World world = createWorld();
        final ICraftingPatternDetails pattern = processingPattern();
        final com.sun.management.ThreadMXBean allocationBean = allocationBean();
        final JsonObject result = new JsonObject();
        result.addProperty("schema", 1);
        result.addProperty("units", units);
        result.addProperty("unitsPerPattern", 8);
        result.addProperty("crafts", crafts);
        result.addProperty("operations", operations);
        result.addProperty("warmup", warmup);
        result.addProperty("javaVersion", System.getProperty("java.runtime.version"));
        result.addProperty("javaVm", System.getProperty("java.vm.name"));
        result.add(
                "jvmArguments",
                new GsonBuilder().create().toJsonTree(ManagementFactory.getRuntimeMXBean().getInputArguments()));
        final JsonArray measurements = new JsonArray();
        final JsonArray warmups = new JsonArray();
        result.add("samples", measurements);
        result.add("warmups", warmups);

        for (int iteration = 0; iteration < warmup + samples; iteration++) {
            final MockGrid grid = new MockGrid();
            final CraftingGridCache cache = grid.getCache(ICraftingGrid.class);
            final IEnergyGrid energy = grid.getCache(IEnergyGrid.class);
            energy.setHasInfiniteStore(true);
            final CountingMedium medium = new CountingMedium();
            final BenchmarkCPU cpu = new BenchmarkCPU(world, pattern, crafts, operations);
            cache.addCraftingOption(medium, pattern);
            try {
                final long allocatedBefore = allocatedBytes(allocationBean);
                final long collectionsBefore = gcCollections();
                final long gcMillisBefore = gcMillis();
                final long started = System.nanoTime();
                int ticks = 0;
                final long maximumTicks = 4L * ((crafts + (long) operations - 1) / operations) + 4;
                while (medium.pushes < crafts) {
                    cpu.updateCraftingLogic(grid, energy, cache);
                    if (++ticks > maximumTicks) throw new IllegalStateException("Dispatch made insufficient progress");
                }
                final long elapsed = System.nanoTime() - started;
                final long allocatedAfter = allocatedBytes(allocationBean);
                final long collections = gcCollections() - collectionsBefore;
                final long gcMillis = gcMillis() - gcMillisBefore;

                // Validate outside the timer so missing work cannot masquerade as a speedup.
                if (medium.pushes != crafts || medium.consumed != units
                        || cpu.storedInputs() != 0
                        || cpu.expectedOutputs() != units) {
                    throw new IllegalStateException("Benchmark did not transfer the requested inputs and outputs");
                }
                for (IAEStack<?> input : pattern.getAEInputs()) {
                    if (input.getStackSize() != 4) throw new IllegalStateException("Pattern input was mutated");
                }
                final JsonObject measurement = new JsonObject();
                measurement.addProperty("nanos", elapsed);
                measurement.addProperty("allocatedBytes", allocatedBefore < 0 ? -1 : allocatedAfter - allocatedBefore);
                measurement.addProperty("gcCollections", collections);
                measurement.addProperty("gcMillis", gcMillis);
                measurement.addProperty("dispatches", medium.pushes);
                measurement.addProperty("consumedUnits", medium.consumed);
                measurement.addProperty("waitingUnits", cpu.expectedOutputs());
                measurement.addProperty("ticks", ticks);
                measurement.addProperty("condensedExpansions", cpu.condensedExpansions);
                measurement.addProperty("slotExpansions", cpu.slotExpansions);
                measurement.addProperty("dirtyNotifications", cpu.core.dirtyNotifications);
                (iteration < warmup ? warmups : measurements).add(measurement);
                System.out.printf(
                        Locale.ROOT,
                        "[AE2 crafting benchmark] %s %d: %.3f ms, %.1f ns/dispatch, dirty=%d, expansions=%d/%d%n",
                        iteration < warmup ? "warmup" : "sample",
                        iteration < warmup ? iteration + 1 : iteration - warmup + 1,
                        elapsed / 1_000_000.0,
                        elapsed / (double) crafts,
                        cpu.core.dirtyNotifications,
                        cpu.condensedExpansions,
                        cpu.slotExpansions);
            } finally {
                cpu.destroy();
                grid.rootNode.destroy();
            }
        }

        final Path output = Paths.get(System.getProperty(PROPERTY + "output", "crafting-benchmark.json"))
                .toAbsolutePath();
        try {
            Files.createDirectories(output.getParent());
            try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(result, writer);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not write benchmark results", e);
        }
        System.out.println("[AE2 crafting benchmark] Results: " + output);
    }

    private static int positiveInt(String name, int fallback) {
        final int value = Integer.parseInt(System.getProperty(PROPERTY + name, Integer.toString(fallback)));
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static com.sun.management.ThreadMXBean allocationBean() {
        final java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (!(bean instanceof com.sun.management.ThreadMXBean)) return null;
        final com.sun.management.ThreadMXBean allocationBean = (com.sun.management.ThreadMXBean) bean;
        if (!allocationBean.isThreadAllocatedMemorySupported()) return null;
        allocationBean.setThreadAllocatedMemoryEnabled(true);
        return allocationBean;
    }

    private static long allocatedBytes(com.sun.management.ThreadMXBean bean) {
        return bean == null ? -1 : bean.getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    private static long gcCollections() {
        long count = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            count += Math.max(0, bean.getCollectionCount());
        }
        return count;
    }

    private static long gcMillis() {
        long millis = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            millis += Math.max(0, bean.getCollectionTime());
        }
        return millis;
    }

    private static World createWorld() {
        if (!DimensionManager.isDimensionRegistered(256)) {
            DimensionManager.registerProviderType(256, WorldProviderSurface.class, false);
            DimensionManager.registerDimension(256, 256);
        }
        final World world = new WorldServer(
                MinecraftServer.getServer(),
                new DummySaveHandler(),
                "CraftingBenchmark",
                256,
                new WorldSettings(256, GameType.SURVIVAL, false, false, WorldType.FLAT),
                MinecraftServer.getServer().theProfiler) {

            @Override
            public File getChunkSaveLocation() {
                return new File("dummy-ignoreme");
            }
        };
        // A loaded block exercises the real chunk-dirty and neighbor-notification code in TileEntity.markDirty.
        // Keep it in a dummy world, with ordinary stone neighbors and no receiving network or tile callbacks.
        world.setBlock(8, 80, 8, Blocks.stone, 0, 2);
        for (net.minecraftforge.common.util.ForgeDirection direction : net.minecraftforge.common.util.ForgeDirection.VALID_DIRECTIONS) {
            world.setBlock(8 + direction.offsetX, 80 + direction.offsetY, 8 + direction.offsetZ, Blocks.stone, 0, 2);
        }
        return world;
    }

    private static ICraftingPatternDetails processingPattern() {
        final ItemStack encoded = AEApi.instance().definitions().items().encodedPattern().maybeStack(1).get();
        final NBTTagCompound tag = new NBTTagCompound();
        final NBTTagList inputs = new NBTTagList();
        for (int slot = 0; slot < 2; slot++) {
            inputs.appendTag(new ItemStack(Blocks.cobblestone, 4).writeToNBT(new NBTTagCompound()));
        }
        final NBTTagList outputs = new NBTTagList();
        outputs.appendTag(new ItemStack(Blocks.stone, 8).writeToNBT(new NBTTagCompound()));
        tag.setTag("in", inputs);
        tag.setTag("out", outputs);
        encoded.setTagCompound(tag);
        return new PatternHelper(encoded, null);
    }

    private static final class BenchmarkCPU extends CraftingCPUCluster {

        private final CountingCore core;
        private int condensedExpansions;
        private int slotExpansions;

        private BenchmarkCPU(World world, ICraftingPatternDetails pattern, int crafts, int operations) {
            super(new WorldCoord(8, 80, 8), new WorldCoord(8, 80, 8));
            core = new CountingCore();
            core.setWorldObj(world);
            core.xCoord = 8;
            core.yCoord = 80;
            core.zCoord = 8;
            core.getBlockType();
            isComplete = false;
            accelerator = operations - 1;
            tasks.put(pattern, new Progress(crafts));
            inventory.injectItems(
                    AEItemStack.create(new ItemStack(Blocks.cobblestone)).setStackSize(crafts * 8L),
                    Actionable.MODULATE);
        }

        private long storedInputs() {
            final IAEItemStack remaining = inventory.extractItems(
                    AEItemStack.create(new ItemStack(Blocks.cobblestone)).setStackSize(Long.MAX_VALUE),
                    Actionable.SIMULATE);
            return remaining == null ? 0 : remaining.getStackSize();
        }

        private long expectedOutputs() {
            final IAEStack<?> expected = waitingFor.findPrecise(AEItemStack.create(new ItemStack(Blocks.stone)));
            return expected == null ? 0 : expected.getStackSize();
        }

        @Override
        protected TileCraftingTile getCore() {
            return core;
        }

        @Override
        protected List<IAEStack<?>> getExpandedCondensedInputs(ICraftingPatternDetails details,
                CraftingGridCache cache) {
            condensedExpansions++;
            return super.getExpandedCondensedInputs(details, cache);
        }

        @Override
        protected List<IAEStack<?>> getExpandedInputs(ICraftingPatternDetails details, CraftingGridCache cache) {
            slotExpansions++;
            return super.getExpandedInputs(details, cache);
        }
    }

    private static final class Progress extends CraftingCPUCluster.TaskProgress {

        private Progress(long crafts) {
            value = crafts;
        }
    }

    private static final class CountingCore extends TileCraftingTile {

        private int dirtyNotifications;

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void markDirty() {
            dirtyNotifications++;
            super.markDirty();
        }
    }

    private static final class CountingMedium implements ICraftingMedium {

        private int pushes;
        private long consumed;

        @Override
        public boolean pushPattern(ICraftingPatternDetails details, InventoryCrafting table) {
            for (int slot = 0; slot < table.getSizeInventory(); slot++) {
                final IAEStack<?> input = ((MEInventoryCrafting) table).getAEStackInSlot(slot);
                consumed += input.getStackSize();
                input.setStackSize(0);
            }
            pushes++;
            return true;
        }

        @Override
        public boolean isBusy() {
            return false;
        }
    }
}
