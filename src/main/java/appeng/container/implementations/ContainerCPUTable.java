package appeng.container.implementations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.minecraft.entity.player.EntityPlayerMP;

import com.google.common.collect.ImmutableSet;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.interfaces.ICraftingCPUSelectorContainer;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCraftingCPUsUpdate;
import appeng.util.Platform;

public class ContainerCPUTable implements ICraftingCPUSelectorContainer {

    private final AEBaseContainer parent;

    private ImmutableSet<ICraftingCPU> lastCpuSet = null;
    private List<CraftingCPUStatus> cpus = new ArrayList<>();
    private final WeakHashMap<ICraftingCPU, Integer> cpuSerialMap = new WeakHashMap<>();
    private int nextCpuSerial = 1;
    private int lastUpdate = 0;

    @GuiSync(0)
    public int selectedCpuSerial = -1;

    private final Consumer<ICraftingCPU> onCPUChange;
    private final boolean preferBusyCPUs;
    private final Predicate<CraftingCPUStatus> cpuFilter;

    private static final Comparator<CraftingCPUStatus> CPU_COMPARATOR = Comparator
            .comparing((CraftingCPUStatus e) -> e.getName() == null || e.getName().isEmpty())
            .thenComparing(e -> e.getName() != null ? e.getName() : "").thenComparingInt(CraftingCPUStatus::getSerial);

    /**
     * @param parent         Container parent, of which this is a field
     * @param onCPUChange    Called whenever the current CPU is changed
     * @param preferBusyCPUs Whether busy CPUs should be picked first (e.g. crafting status vs. picking a CPU for a job)
     */
    public ContainerCPUTable(AEBaseContainer parent, Consumer<ICraftingCPU> onCPUChange, boolean preferBusyCPUs,
            Predicate<CraftingCPUStatus> cpuFilter) {
        this.parent = parent;
        this.onCPUChange = onCPUChange;
        this.preferBusyCPUs = preferBusyCPUs;
        this.cpuFilter = cpuFilter;
    }

    public boolean isBusyCPUsPreferred() {
        return preferBusyCPUs;
    }

    public Predicate<CraftingCPUStatus> getCpuFilter() {
        return cpuFilter;
    }

    public void detectAndSendChanges(IGrid network, List<?> crafters) {
        if (Platform.isServer() && network != null) {
            final ICraftingGrid cc = network.getCache(ICraftingGrid.class);
            final ImmutableSet<ICraftingCPU> cpuSet = cc.getCpus();
            // Update at least once a second
            ++lastUpdate;
            if (!cpuSet.equals(lastCpuSet) || lastUpdate > 20) {
                lastUpdate = 0;
                lastCpuSet = cpuSet;
                updateCpuList();
                sendCPUs(crafters);
            }
        }

        // Clear selection if CPU is no longer in list
        if (selectedCpuSerial != -1) {
            if (cpus.stream().noneMatch(c -> c.getSerial() == selectedCpuSerial)) {
                selectCPU(-1);
            }
        }

        // Select a suitable CPU if none is selected
        if (selectedCpuSerial == -1) {
            // Try preferred CPUs first
            for (CraftingCPUStatus cpu : cpus) {
                if (preferBusyCPUs == cpu.isBusy() && cpuFilter.test(cpu)) {
                    selectCPU(cpu.getSerial());
                    break;
                }
            }
            // If we couldn't find a preferred one, just select the first
            if (selectedCpuSerial == -1 && !cpus.isEmpty()) {
                selectCPU(cpus.get(0).getSerial());
            }
        }
    }

    private void updateCpuList() {
        this.cpus.clear();
        for (ICraftingCPU cpu : lastCpuSet) {
            int serial = getOrAssignCpuSerial(cpu);
            this.cpus.add(new CraftingCPUStatus(cpu, serial));
        }
        this.cpus.sort(CPU_COMPARATOR);
    }

    private int getOrAssignCpuSerial(ICraftingCPU cpu) {
        return cpuSerialMap.computeIfAbsent(cpu, unused -> nextCpuSerial++);
    }

    private void sendCPUs(List<?> crafters) {
        final PacketCraftingCPUsUpdate update;
        for (final Object player : crafters) {
            if (player instanceof EntityPlayerMP) {
                try {
                    NetworkHandler.instance.sendTo(new PacketCraftingCPUsUpdate(this.cpus), (EntityPlayerMP) player);
                } catch (IOException e) {
                    AELog.debug(e);
                }
            }
        }
    }

    @Override
    public void selectCPU(int serial) {
        if (Platform.isServer()) {
            if (serial < -1) {
                serial = -1;
            }

            final int searchedSerial = serial;
            if (serial > -1 && cpus.stream().noneMatch(c -> c.getSerial() == searchedSerial)) {
                serial = -1;
            }

            ICraftingCPU newSelectedCpu = null;
            if (serial != -1) {
                for (ICraftingCPU cpu : lastCpuSet) {
                    if (cpuSerialMap.getOrDefault(cpu, -1) == serial) {
                        newSelectedCpu = cpu;
                        break;
                    }
                }
            }

            this.selectedCpuSerial = serial;
            if (onCPUChange != null) {
                onCPUChange.accept(newSelectedCpu);
            }
        }
    }

    public List<CraftingCPUStatus> getCPUs() {
        return Collections.unmodifiableList(cpus);
    }

    public CraftingCPUStatus getSelectedCPU() {
        return this.cpus.stream().filter(c -> c.getSerial() == selectedCpuSerial).findFirst().orElse(null);
    }

    public void handleCPUUpdate(CraftingCPUStatus[] cpus) {
        this.cpus = Arrays.asList(cpus);
    }
}
