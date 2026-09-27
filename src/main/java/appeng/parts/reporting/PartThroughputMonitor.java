package appeng.parts.reporting;

import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Vec3;

import org.lwjgl.opengl.GL11;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.data.IAEStack;
import appeng.client.texture.CableBusTextures;
import appeng.core.settings.TickRates;
import appeng.helpers.Reflected;
import appeng.me.GridAccessException;
import appeng.util.IWideReadableNumberConverter;
import appeng.util.Platform;
import appeng.util.ReadableNumberConverter;
import io.netty.buffer.ByteBuf;

/**
 * @author MCTBL
 * @version rv3-beta-538-GTNH
 * @since rv3-beta-538-GTNH
 */
public class PartThroughputMonitor extends AbstractPartMonitor implements IGridTickable {

    private enum TimeUnit {

        Tick("/t", 1),
        Second("/s", 20),
        Minute("/m", 1_200),
        Hour("/h", 72_000);

        String label;
        int totalTicks;

        TimeUnit(String label, int totalTicks) {
            this.totalTicks = totalTicks;
            this.label = label;
        }

        public TimeUnit getNext() {
            if (this.ordinal() == TimeUnit.values().length - 1) {
                return Tick;
            }
            return TimeUnit.values()[this.ordinal() + 1];
        }

        public static TimeUnit fromOrdinal(int ordinal) {
            if (ordinal < 0 || ordinal >= TimeUnit.values().length) {
                return Tick;
            } else {
                return TimeUnit.values()[ordinal];
            }
        }
    }

    private static final IWideReadableNumberConverter NUMBER_CONVERTER = ReadableNumberConverter.INSTANCE;

    private static final CableBusTextures FRONT_BRIGHT_ICON = CableBusTextures.PartThroughputMonitor_Bright;
    private static final CableBusTextures FRONT_DARK_ICON = CableBusTextures.PartThroughputMonitor_Dark;
    private static final CableBusTextures FRONT_COLORED_ICON = CableBusTextures.PartThroughputMonitor_Colored;
    private static final CableBusTextures FRONT_COLORED_ICON_LOCKED = CableBusTextures.PartThroughputMonitor_Dark_Locked;

    private TimeUnit timeMode;
    private double itemNumsChange;
    private final ThroughputTracker throughputTracker = new ThroughputTracker();
    private IAEStack<?> trackedStack;
    private boolean discardNextInterval;

    @Reflected
    public PartThroughputMonitor(final ItemStack is) {
        super(is);
        this.itemNumsChange = 0;
        this.timeMode = TimeUnit.Tick;
    }

    @Override
    public CableBusTextures getFrontBright() {
        return FRONT_BRIGHT_ICON;
    }

    @Override
    public CableBusTextures getFrontColored() {
        return this.isLocked() ? FRONT_COLORED_ICON_LOCKED : FRONT_COLORED_ICON;
    }

    @Override
    public CableBusTextures getFrontDark() {
        return FRONT_DARK_ICON;
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.timeMode = TimeUnit.fromOrdinal(data.getInteger("timeMode"));
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        data.setInteger("timeMode", this.timeMode.ordinal());
    }

    @Override
    public void writeToStream(final ByteBuf data) throws IOException {
        super.writeToStream(data);
        data.writeInt(this.timeMode.ordinal());
        data.writeDouble(this.itemNumsChange);
    }

    @Override
    public boolean readFromStream(final ByteBuf data) throws IOException {
        boolean needRedraw = super.readFromStream(data);
        this.timeMode = TimeUnit.fromOrdinal(data.readInt());
        this.itemNumsChange = data.readDouble();
        return needRedraw;
    }

    @Override
    public boolean onPartActivate(final EntityPlayer player, final Vec3 pos) {
        final IAEStack<?> previousDisplayed = this.getDisplayed();
        final boolean activated = super.onPartActivate(player, pos);
        if (!player.worldObj.isRemote && previousDisplayed != this.getDisplayed()) {
            this.resetThroughput(this.getDisplayed());
            if (this.getDisplayed() != null) {
                this.discardNextInterval = true;
                try {
                    this.getProxy().getTick().alertDevice(this.getProxy().getNode());
                } catch (final GridAccessException ignored) {}
            }
        }
        return activated;
    }

    @Override
    public boolean onPartShiftActivate(final EntityPlayer player, final Vec3 pos) {
        if (Platform.isClient()) {
            return true;
        }

        if (!this.getProxy().isActive()) {
            return false;
        }

        if (!Platform.hasPermissions(this.getLocation(), player)) {
            return false;
        }

        this.timeMode = this.timeMode.getNext();
        this.itemNumsChange = this.throughputTracker.getAveragePerTick() * this.timeMode.totalTicks;
        this.host.markForUpdate();

        return true;
    }

    @Override
    public void tesrRenderItemNumber(final IAEStack<?> ais) {
        GL11.glTranslatef(0.0f, 0.14f, -0.24f);
        GL11.glScalef(1.0f / 120.0f, 1.0f / 120.0f, 1.0f / 120.0f);

        final long stackSize = ais.getStackSize();
        final String renderedStackSize = NUMBER_CONVERTER.toWideReadableForm(stackSize);

        final String renderedStackSizeChange = (this.itemNumsChange > 0 ? "+" : "")
                + (Platform.formatNumberDoubleRestrictedByWidth(this.itemNumsChange, 5))
                + (this.timeMode.label);

        final FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        int width = fr.getStringWidth(renderedStackSize);
        GL11.glTranslatef(-0.5f * width, 0.0f, -1.0f);
        fr.drawString(renderedStackSize, 0, 0, 0);
        GL11.glTranslatef(+0.5f * width, fr.FONT_HEIGHT + 3, -1.0f);

        width = fr.getStringWidth(renderedStackSizeChange);
        GL11.glTranslatef(-0.5f * width, 0.0f, -1.0f);
        int color = 0;
        if (this.itemNumsChange < 0) {
            color = 0xFF0000;
        } else if (this.itemNumsChange > 0) {
            color = 0x17B66C;
        }
        fr.drawString(renderedStackSizeChange, 0, 0, color);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(
                TickRates.ThroughputMonitor.getMin(),
                TickRates.ThroughputMonitor.getMax(),
                false,
                true);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int TicksSinceLastCall) {
        if (Platform.isClient()) {
            return TickRateModulation.SAME;
        }

        final IAEStack<?> displayed = this.getDisplayed();
        if (displayed == null) {
            if (this.trackedStack != null) {
                this.resetThroughput(null);
            }
            return TickRateModulation.IDLE;
        }

        if (this.trackedStack != displayed) {
            this.resetThroughput(displayed);
            return TickRateModulation.SLOWER;
        }

        if (this.discardNextInterval) {
            this.discardNextInterval = false;
            return TickRateModulation.SAME;
        }

        final boolean sampled = this.throughputTracker.update(displayed.getStackSize(), TicksSinceLastCall);
        if (sampled || !this.throughputTracker.isFull()) {
            final double itemNumsChange = this.throughputTracker.getAveragePerTick() * this.timeMode.totalTicks;
            if (this.itemNumsChange != itemNumsChange) {
                this.itemNumsChange = itemNumsChange;
                this.host.markForUpdate();
            }
        }

        return this.throughputTracker.isFull() ? TickRateModulation.IDLE : TickRateModulation.SLOWER;
    }

    private void resetThroughput(IAEStack<?> displayed) {
        this.trackedStack = displayed;
        this.discardNextInterval = false;
        if (displayed == null) {
            this.throughputTracker.clear();
        } else {
            this.throughputTracker.reset(displayed.getStackSize());
        }
        this.itemNumsChange = 0;
        this.host.markForUpdate();
    }

    static final class ThroughputTracker {

        private static final int SAMPLE_INTERVAL_TICKS = 200;
        private static final int HISTORY_SIZE = 12;

        private final long[] changes = new long[HISTORY_SIZE];
        private final int[] durations = new int[HISTORY_SIZE];
        private long previousAmount;
        private long latestAmount;
        private long totalChange;
        private long totalTicks;
        private int ticksSinceSample;
        private int nextSample;
        private int sampleCount;

        void reset(long amount) {
            this.clear();
            this.previousAmount = amount;
            this.latestAmount = amount;
        }

        void clear() {
            this.totalChange = 0;
            this.totalTicks = 0;
            this.ticksSinceSample = 0;
            this.nextSample = 0;
            this.sampleCount = 0;
        }

        boolean update(long amount, int ticksSinceLastCall) {
            this.latestAmount = amount;
            this.ticksSinceSample += ticksSinceLastCall;
            if (this.ticksSinceSample < SAMPLE_INTERVAL_TICKS) {
                return false;
            }

            if (this.sampleCount == HISTORY_SIZE) {
                this.totalChange -= this.changes[this.nextSample];
                this.totalTicks -= this.durations[this.nextSample];
            } else {
                this.sampleCount++;
            }

            final long change = amount - this.previousAmount;
            this.changes[this.nextSample] = change;
            this.durations[this.nextSample] = this.ticksSinceSample;
            this.totalChange += change;
            this.totalTicks += this.ticksSinceSample;
            this.previousAmount = amount;
            this.ticksSinceSample = 0;
            this.nextSample = (this.nextSample + 1) % HISTORY_SIZE;
            return true;
        }

        double getAveragePerTick() {
            final long elapsedTicks = this.totalTicks + this.ticksSinceSample;
            return elapsedTicks == 0 ? 0
                    : (this.totalChange + this.latestAmount - this.previousAmount) / (double) elapsedTicks;
        }

        boolean isFull() {
            return this.sampleCount == HISTORY_SIZE;
        }
    }

}
