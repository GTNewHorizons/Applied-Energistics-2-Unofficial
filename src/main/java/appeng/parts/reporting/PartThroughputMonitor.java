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
    private long lastStackSize;

    @Reflected
    public PartThroughputMonitor(final ItemStack is) {
        super(is);
        this.itemNumsChange = 0;
        this.lastStackSize = -1;
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
                + (Platform.formatNumberDoubleRestrictedByWidth(this.itemNumsChange, 3))
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
                false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int TicksSinceLastCall) {
        if (Platform.isClient()) {
            return TickRateModulation.SAME;
        }

        if (this.getDisplayed() == null) {
            this.lastStackSize = -1;
            this.host.markForUpdate();
            return TickRateModulation.IDLE;
        } else {
            long nowStackSize = this.getDisplayed().getStackSize();
            if (this.lastStackSize != -1) {
                long changeStackSize = nowStackSize - this.lastStackSize;
                this.itemNumsChange = (changeStackSize * this.timeMode.totalTicks) / TicksSinceLastCall;
                this.host.markForUpdate();
            }
            this.lastStackSize = nowStackSize;
        }
        return TickRateModulation.FASTER;
    }

}
