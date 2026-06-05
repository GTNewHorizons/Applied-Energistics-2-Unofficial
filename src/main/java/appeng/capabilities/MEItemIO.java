package appeng.capabilities;

import java.util.stream.IntStream;

import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.item.ImmutableItemStack;
import com.gtnewhorizon.gtnhlib.item.InsertionItemStack;
import com.gtnewhorizon.gtnhlib.item.InventoryIterator;
import com.gtnewhorizon.gtnhlib.item.SimpleItemIO;
import com.gtnewhorizon.gtnhlib.item.StandardInventoryIterator;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.DualityInterface;
import appeng.me.GridAccessException;
import appeng.parts.misc.PartInterface;
import appeng.tile.misc.TileInterface;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;

public class MEItemIO extends SimpleItemIO {

    private static final int[] SLOTS = IntStream.range(0, 9).toArray();

    private final DualityInterface duality;
    private final IEnergyGrid energyGrid;
    private final IMEMonitor<IAEItemStack> storage;

    private final InsertionItemStack iteration = new InsertionItemStack();

    public MEItemIO(TileInterface iface) throws GridAccessException {
        this.energyGrid = iface.getProxy().getGrid().getCache(IEnergyGrid.class);
        this.storage = iface.getProxy().getGrid().<IStorageGrid>getCache(IStorageGrid.class).getItemInventory();

        this.duality = iface.getInterfaceDuality();
    }

    public MEItemIO(PartInterface iface) throws GridAccessException {
        this.energyGrid = iface.getProxy().getGrid().getCache(IEnergyGrid.class);
        this.storage = iface.getProxy().getGrid().<IStorageGrid>getCache(IStorageGrid.class).getItemInventory();

        this.duality = iface.getInterfaceDuality();
    }

    @Override
    public @Nullable InventoryIterator simulatedSinkIterator() {
        return null;
    }

    @Override
    public int store(ImmutableItemStack stack) {
        int rejected = insertIntoNetwork(stack);

        if (rejected == 0) return 0;

        iteration.set(stack, rejected);
        return super.store(iteration);
    }

    @Override
    protected @NotNull InventoryIterator iterator(int[] allowedSlots) {
        return new StandardInventoryIterator(this.duality.getStorage(), ForgeDirection.UNKNOWN, SLOTS, allowedSlots);
    }

    private int insertIntoNetwork(ImmutableItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;

        if (!duality.getProxy().isActive()) return stack.getStackSize();

        IAEItemStack rejected = Platform.poweredInsert(
                energyGrid,
                storage,
                AEItemStack.create(stack.toStack()),
                duality.getActionSource(),
                Actionable.MODULATE);

        return rejected == null ? 0 : (int) rejected.getStackSize();
    }
}
