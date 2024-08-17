package appeng.container.implementations;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import org.apache.commons.lang3.tuple.Pair;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.IInterfaceViewable;
import appeng.container.AEBaseContainer;
import appeng.core.AELog;
import appeng.core.features.registries.InterfaceTerminalRegistry;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketMEInventoryUpdate;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingJobV2;
import appeng.crafting.v2.resolvers.CraftableItemResolver.CraftFromPatternTask;
import appeng.crafting.v2.resolvers.CraftingTask;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.me.cache.CraftingGridCache;
import appeng.parts.reporting.PartCraftingTerminal;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.parts.reporting.PartPatternTerminalEx;
import appeng.parts.reporting.PartTerminal;
import appeng.tile.misc.TilePatternOptimizationMatrix;
import appeng.util.PatternMultiplierHelper;
import appeng.util.Platform;
import codechicken.nei.ItemStackMap;
import codechicken.nei.ItemStackSet;

public class ContainerOptimizePatterns extends AEBaseContainer {

    private ICraftingJob result;

    HashMap<IAEItemStack, Pattern> patterns = new HashMap<>();

    public ContainerOptimizePatterns(final InventoryPlayer ip, final ITerminalHost te) {
        super(ip, te);
    }

    public void setResult(ICraftingJob result) {
        this.result = result;

        patterns.clear();

        if (this.result instanceof CraftingJobV2 cj) {
            // use context to reuse caches
            CraftingContext context = cj.getContext();

            // check blacklisted interfaces
            ItemStackSet blacklistedPatterns = new ItemStackSet();

            var supported = InterfaceTerminalRegistry.instance().getSupportedClasses();

            for (Class<? extends IInterfaceViewable> c : supported) {
                for (IGridNode node : context.meGrid.getMachines(c)) {
                    IInterfaceViewable machine = (IInterfaceViewable) node.getMachine();
                    if (!machine.allowsPatternOptimization()) {
                        IInventory patternInv = machine.getPatterns();

                        for (int i = 0; i < patternInv.getSizeInventory(); i++) {
                            ItemStack stack = patternInv.getStackInSlot(i);
                            if (stack != null) blacklistedPatterns.add(stack);
                        }
                    }
                }
            }

            for (CraftingTask resolvedTask : context.getResolvedTasks()) {
                if (resolvedTask instanceof CraftFromPatternTask cfpt) {
                    if (!blacklistedPatterns.contains(cfpt.pattern.getPattern()))
                        patterns.computeIfAbsent(cfpt.request.stack, i -> new Pattern()).addCraftingTask(cfpt);
                }
            }
            this.patterns.entrySet().removeIf(entry -> entry.getValue().patternDetails.size() != 1);
            this.patterns.entrySet().removeIf(entry -> entry.getValue().getPattern().isCraftable());

            try {
                final PacketMEInventoryUpdate patternsUpdate = new PacketMEInventoryUpdate((byte) 0);

                for (Entry<IAEItemStack, Pattern> entry : this.patterns.entrySet()) {
                    IAEItemStack stack = entry.getKey().copy();
                    stack.setCountRequestableCrafts(entry.getValue().requestedCrafts);
                    long perCraft = entry.getValue().getCraftAmountForItem(stack);
                    int hash = entry.getKey().hashCode();
                    if (hash < 0) // max multi is 30, that's 5 bits MAX!! + 1 bit to store sign of the hash
                        stack.setStackSize((long) (-hash) << 6 | 0b100000 | entry.getValue().getMaxBitMultiplier());
                    else stack.setStackSize((long) hash << 6 | entry.getValue().getMaxBitMultiplier());
                    stack.setCountRequestable(perCraft);
                    patternsUpdate.appendItem(stack);
                }

                for (final Object player : this.crafters) {
                    if (player instanceof EntityPlayerMP playerMP) {
                        NetworkHandler.instance.sendTo(patternsUpdate, playerMP);
                    }
                }

            } catch (IOException e) {
                //
            }
        }
    }

    public void optimizePatterns(HashMap<Integer, Integer> hashCodeToMultipliers) {

        IGrid grid = getGrid();

        if (grid == null || grid.getMachines(TilePatternOptimizationMatrix.class).isEmpty()) return;

        Map<IAEItemStack, Integer> multipliersMap = patterns.keySet().stream()
                .filter(i -> hashCodeToMultipliers.containsKey(i.hashCode()))
                .collect(Collectors.toMap(i -> i, i -> hashCodeToMultipliers.get(i.hashCode())));

        ItemStackMap<Pair<Pattern, Integer>> lookupMap = new ItemStackMap<>();
        for (Entry<IAEItemStack, Integer> entry : multipliersMap.entrySet()) {
            Pattern pattern = patterns.get(entry.getKey());
            lookupMap.put(pattern.getPattern().getPattern(), Pair.of(pattern, entry.getValue()));
        }

        // Detect P2P interfaces
        IdentityHashMap<ItemStack, Boolean> alreadyDone = new IdentityHashMap<>();

        var supported = InterfaceTerminalRegistry.instance().getSupportedClasses();

        CraftingGridCache.pauseRebuilds();
        try {

            for (Class<? extends IInterfaceViewable> c : supported) {
                for (IGridNode node : grid.getMachines(c)) {
                    IInterfaceViewable machine = (IInterfaceViewable) node.getMachine();
                    if (!machine.allowsPatternOptimization()) continue;

                    IInventory patternInv = machine.getPatterns();

                    for (int i = 0; i < patternInv.getSizeInventory(); i++) {
                        ItemStack stack = patternInv.getStackInSlot(i);

                        if (stack != null && !alreadyDone.containsKey(stack)) {
                            var pair = lookupMap.get(stack);
                            if (pair == null) continue;
                            ItemStack sCopy = stack.copy();
                            pair.getKey().applyModification(sCopy, pair.getValue());
                            patternInv.setInventorySlotContents(i, sCopy);
                            alreadyDone.put(stack, true);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            AELog.debug(t);
        }

        CraftingGridCache.unpauseRebuilds();

        switchToOriginalGUI();

    }

    private IGrid getGrid() {
        final IActionHost h = ((IActionHost) this.getTarget());
        if (h == null || h.getActionableNode() == null) return null;
        return h.getActionableNode().getGrid();
    }

    public void switchToOriginalGUI() {
        GuiBridge originalGui = null;

        final IActionHost ah = this.getActionHost();
        if (ah instanceof WirelessTerminalGuiObject) {
            originalGui = GuiBridge.GUI_WIRELESS_TERM;
        }

        if (ah instanceof PartTerminal) {
            originalGui = GuiBridge.GUI_ME;
        }

        if (ah instanceof PartCraftingTerminal) {
            originalGui = GuiBridge.GUI_CRAFTING_TERMINAL;
        }

        if (ah instanceof PartPatternTerminal) {
            originalGui = GuiBridge.GUI_PATTERN_TERMINAL;
        }

        if (ah instanceof PartPatternTerminalEx) {
            originalGui = GuiBridge.GUI_PATTERN_TERMINAL_EX;
        }

        if (originalGui != null && this.getOpenContext() != null) {
            NetworkHandler.instance
                    .sendTo(new PacketSwitchGuis(originalGui), (EntityPlayerMP) this.getInventoryPlayer().player);

            final TileEntity te = this.getOpenContext().getTile();
            Platform.openGUI(this.getInventoryPlayer().player, te, this.getOpenContext().getSide(), originalGui);
        }
    }

    public static int getBitMultiplier(long currentCrafts, long perCraft, long maximumCrafts) {
        int multi = 0;
        long crafted = currentCrafts * perCraft;
        while (Math.ceil((double) crafted / (double) perCraft) > maximumCrafts) {
            perCraft <<= 1;
            multi++;
        }

        return multi;
    }

    private static class Pattern {

        private HashSet<ICraftingPatternDetails> patternDetails = new HashSet<>();
        // private long requestedAmount;
        private long requestedCrafts = 0;

        private void addCraftingTask(CraftFromPatternTask task) {
            patternDetails.add(task.pattern);
            requestedCrafts += task.getTotalCraftsDone();
        }

        private long getCraftAmountForItem(IAEItemStack stack) {
            IAEItemStack s = Arrays.stream(patternDetails.stream().findFirst().get().getCondensedOutputs())
                    .filter(i -> i.isSameType(stack)).findFirst().orElse(null);
            if (s != null) return s.getStackSize();
            else return 0;
        }

        private ICraftingPatternDetails getPattern() {
            return patternDetails.stream().findFirst().get();
        }

        private int getMaxBitMultiplier() {
            return PatternMultiplierHelper.getMaxBitMultiplier(this.getPattern());
        }

        private void applyModification(ItemStack stack, int multiplier) {
            PatternMultiplierHelper.applyModification(stack, multiplier);
        }
    }
}
