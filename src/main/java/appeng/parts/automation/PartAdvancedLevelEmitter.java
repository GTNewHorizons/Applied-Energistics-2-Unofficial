package appeng.parts.automation;

import java.util.Random;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import appeng.api.config.BooleanOperation;
import appeng.api.config.Settings;
import appeng.api.features.LevelItemInfo;
import appeng.api.features.LevelState;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.networking.storage.IStackWatcher;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IAdvancedLevelEmitter;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartRenderHelper;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.client.texture.CableBusTextures;
import appeng.core.AEConfig;
import appeng.core.sync.GuiBridge;
import appeng.helpers.Reflected;
import appeng.me.GridAccessException;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.Platform;
import appeng.util.SettingsFrom;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class PartAdvancedLevelEmitter extends PartUpgradeable implements IAdvancedLevelEmitter {

    private static final int FLAG_ON = 8;
    private static final int SLOTS = SLOT_COUNT;

    private final IAEStackInventory config = new IAEStackInventory(this, SLOTS, StorageName.CONFIG);

    private final boolean[] slotActive = new boolean[SLOTS];
    private final boolean[] slotInverted = new boolean[SLOTS];
    private final long[] amount = new long[SLOTS];
    private final long[] lastReportedValue = new long[SLOTS];

    private boolean prevState = false;

    private IStackWatcher myWatcher;

    private double centerX;
    private double centerY;
    private double centerZ;

    private int lastWorkingTick = 0;
    private boolean delayedUpdatesQueued = false;

    @Reflected
    public PartAdvancedLevelEmitter(final ItemStack is) {
        super(is);

        this.getConfigManager().registerSetting(Settings.ADVANCED_LEVEL_EMITTER_LOGIC, BooleanOperation.OR);

        // Workaround the emitter randomly breaking on world load
        if (MinecraftServer.getServer() != null) {
            delayedUpdatesQueued = true;
            lastWorkingTick = MinecraftServer.getServer().getTickCounter();
        }
    }

    @Override
    protected int getUpgradeSlots() {
        return 0;
    }

    @Override
    public long getReportingValue(final int slot) {
        return this.amount[slot];
    }

    @Override
    public void setReportingValue(final int slot, final long v) {
        this.amount[slot] = v;
        this.updateState();
    }

    @Override
    public boolean isSlotActive(final int slot) {
        return this.slotActive[slot];
    }

    @Override
    public void setSlotActive(final int slot, final boolean active) {
        this.slotActive[slot] = active;
        this.updateState();
    }

    @Override
    public boolean isSlotInverted(final int slot) {
        return this.slotInverted[slot];
    }

    @Override
    public void setSlotInverted(final int slot, final boolean inverted) {
        this.slotInverted[slot] = inverted;
        this.updateState();
    }

    public BooleanOperation getLogicMode() {
        return (BooleanOperation) this.getConfigManager().getSetting(Settings.ADVANCED_LEVEL_EMITTER_LOGIC);
    }

    @MENetworkEventSubscribe
    public void powerChanged(final MENetworkPowerStatusChange c) {
        this.updateState();
    }

    @MENetworkEventSubscribe
    public void channelChanged(final MENetworkChannelsChanged c) {
        this.updateState();
    }

    private void updateState() {
        final boolean isOn = this.isLevelEmitterOn();
        if (this.prevState != isOn) {
            this.getHost().markForUpdate();
            final TileEntity te = this.getHost().getTile();
            this.prevState = isOn;
            Platform.notifyBlocksOfNeighbors(te.getWorldObj(), te.xCoord, te.yCoord, te.zCoord);
            Platform.notifyBlocksOfNeighbors(
                    te.getWorldObj(),
                    te.xCoord + this.getSide().offsetX,
                    te.yCoord + this.getSide().offsetY,
                    te.zCoord + this.getSide().offsetZ);
        }
    }

    private boolean isLevelEmitterOn() {
        if (Platform.isClient()) {
            return (this.getClientFlags() & FLAG_ON) == FLAG_ON;
        }

        if (!this.getProxy().isActive()) {
            return false;
        }

        final BooleanOperation mode = this.getLogicMode();
        boolean result = mode == BooleanOperation.AND;
        boolean sawActive = false;

        for (int slot = 0; slot < SLOTS; slot++) {
            if (!this.slotActive[slot]) {
                continue;
            }

            final IAEStack<?> stack = this.config.getAEStackInSlot(slot);
            if (stack == null) {
                continue;
            }

            sawActive = true;
            final boolean slotState = this.slotInverted[slot] ? this.lastReportedValue[slot] < this.amount[slot]
                    : this.lastReportedValue[slot] >= this.amount[slot];

            result = mode == BooleanOperation.AND ? result && slotState : result || slotState;
        }

        return sawActive && result;
    }

    @Override
    protected int populateFlags(final int cf) {
        return cf | (this.prevState ? FLAG_ON : 0);
    }

    @Override
    public IIcon getBreakingTexture() {
        return this.getItemStack().getIconIndex();
    }

    @Override
    public void updateWatcher(final IStackWatcher newWatcher) {
        this.myWatcher = newWatcher;
        this.configureWatchers();
    }

    private void configureWatchers() {
        if (this.myWatcher != null) {
            this.myWatcher.clear();
        }

        try {
            for (final IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
                final IMEMonitor<?> monitor = this.getProxy().getStorage().getMEMonitor(type);
                if (monitor == null) {
                    continue;
                }

                if (this.usesType(type)) {
                    monitor.addListener(this, this.getProxy().getGrid());
                } else {
                    monitor.removeListener(this);
                }
            }

            if (this.myWatcher != null) {
                for (int slot = 0; slot < SLOTS; slot++) {
                    final IAEStack<?> stack = this.config.getAEStackInSlot(slot);
                    if (stack != null) {
                        this.myWatcher.add(stack);
                    }
                }
            }

            this.updateAllReportingValues();
        } catch (final GridAccessException e) {
            // :P
        }
    }

    private boolean usesType(final IAEStackType<?> type) {
        for (int slot = 0; slot < SLOTS; slot++) {
            final IAEStack<?> stack = this.config.getAEStackInSlot(slot);
            if (stack != null && stack.getStackType() == type) {
                return true;
            }
        }
        return false;
    }

    private void updateAllReportingValues() {
        for (int slot = 0; slot < SLOTS; slot++) {
            this.updateReportingValueForSlot(slot);
        }
        this.updateState();
    }

    private void updateReportingValueForSlot(final int slot) {
        final IAEStack<?> myStack = this.config.getAEStackInSlot(slot);

        if (myStack == null) {
            this.lastReportedValue[slot] = 0;
            return;
        }

        try {
            final IMEMonitor monitor = this.getProxy().getStorage().getMEMonitor(myStack.getStackType());
            if (monitor == null) {
                this.lastReportedValue[slot] = 0;
                return;
            }

            final IAEStack<?> r = monitor.getStorageList().findPrecise((IAEStack) myStack);
            this.lastReportedValue[slot] = r == null ? 0 : r.getStackSize();
        } catch (final GridAccessException e) {
            // >.>
        }
    }

    @Override
    public void onStackChange(final IItemList o, final IAEStack fullStack, final IAEStack diffStack,
            final BaseActionSource src, final StorageChannel chan) {
        boolean changed = false;

        for (int slot = 0; slot < SLOTS; slot++) {
            final IAEStack<?> myStack = this.config.getAEStackInSlot(slot);
            if (myStack != null && fullStack.equals(myStack)) {
                this.lastReportedValue[slot] = fullStack.getStackSize();
                changed = true;
            }
        }

        if (changed) {
            this.updateState();
        }
    }

    @Override
    public boolean isValid(final Object effectiveGrid) {
        try {
            return this.getProxy().getGrid() == effectiveGrid;
        } catch (final GridAccessException e) {
            return false;
        }
    }

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(
                AEConfig.instance.levelEmitterDelay / 2,
                AEConfig.instance.levelEmitterDelay,
                !delayedUpdatesQueued,
                true);
    }

    private boolean canDoWork() {
        final int currentTick = MinecraftServer.getServer().getTickCounter();
        return (currentTick - lastWorkingTick) > AEConfig.instance.levelEmitterDelay;
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        if (delayedUpdatesQueued && canDoWork()) {
            delayedUpdatesQueued = false;
            lastWorkingTick = MinecraftServer.getServer().getTickCounter();
            this.onListUpdate();
        }
        return delayedUpdatesQueued ? TickRateModulation.IDLE : TickRateModulation.SLEEP;
    }

    @Override
    public void postChange(final IBaseMonitor<IAEStack<?>> monitor, final Iterable<IAEStack<?>> change,
            final BaseActionSource actionSource) {
        if (canDoWork()) {
            if (delayedUpdatesQueued) {
                delayedUpdatesQueued = false;
                try {
                    this.getProxy().getTick().sleepDevice(this.getProxy().getNode());
                } catch (final GridAccessException e) {
                    // :/
                }
            }
            lastWorkingTick = MinecraftServer.getServer().getTickCounter();

            final IAEStackType<?> changedType = ((IMEMonitor) monitor).getStackType();
            for (int slot = 0; slot < SLOTS; slot++) {
                final IAEStack<?> myStack = this.config.getAEStackInSlot(slot);
                if (myStack != null && myStack.getStackType() == changedType) {
                    this.updateReportingValueForSlot(slot);
                }
            }
            this.updateState();
        } else if (!delayedUpdatesQueued) {
            delayedUpdatesQueued = true;
            try {
                this.getProxy().getTick().alertDevice(this.getProxy().getNode());
            } catch (final GridAccessException e) {
                // :/
            }
        }
    }

    @Override
    public void onListUpdate() {
        this.updateAllReportingValues();
    }

    @Override
    public AECableType getCableConnectionType(final ForgeDirection dir) {
        return AECableType.SMART;
    }

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        bch.addBox(7, 7, 11, 9, 9, 16);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderInventory(final IPartRenderHelper rh, final RenderBlocks renderer) {
        rh.setTexture(this.getItemStack().getIconIndex());
        final Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        this.renderTorchAtAngle(0, -0.5, 0);
        tess.draw();
    }

    private void renderTorchAtAngle(double baseX, double baseY, double baseZ) {
        final boolean isOn = this.isLevelEmitterOn();
        final IIcon offTexture = this.getItemStack().getIconIndex();
        final IIcon icon = isOn ? CableBusTextures.AdvancedLevelEmitterTorchOn.getIcon() : offTexture;

        this.centerX = baseX + 0.5;
        this.centerY = baseY + 0.5;
        this.centerZ = baseZ + 0.5;

        baseY += 7.0 / 16.0;

        final float var16 = icon.getMinU();
        final float var17 = icon.getMaxU();
        final float var18 = icon.getMinV();
        final float var19 = icon.getMaxV();

        final double var20b = offTexture.getInterpolatedU(7.0D);
        final double var24b = offTexture.getInterpolatedU(9.0D);

        final double var20 = icon.getInterpolatedU(7.0D);
        final double var24 = icon.getInterpolatedU(9.0D);
        final double var22 = icon.getInterpolatedV(6.0D + (isOn ? 0 : 1.0D));
        final double var26 = icon.getInterpolatedV(8.0D + (isOn ? 0 : 1.0D));
        final double var28 = icon.getInterpolatedU(7.0D);
        final double var30 = icon.getInterpolatedV(13.0D);
        final double var32 = icon.getInterpolatedU(9.0D);
        final double var34 = icon.getInterpolatedV(15.0D);

        final double var22b = icon.getInterpolatedV(9.0D);
        final double var26b = icon.getInterpolatedV(11.0D);

        baseX += 0.5D;
        baseZ += 0.5D;
        final double var36 = baseX - 0.5D;
        final double var38 = baseX + 0.5D;
        final double var40 = baseZ - 0.5D;
        final double var42 = baseZ + 0.5D;

        double toff = 0.0d;

        if (!isOn) {
            toff = 1.0d / 16.0d;
        }

        final Tessellator var12 = Tessellator.instance;
        if (isOn) {
            var12.setColorOpaque_F(1.0F, 1.0F, 1.0F);
            var12.setBrightness(11 << 20 | 11 << 4);
        }

        final double torchLen = 0.625D;
        final double var44 = 0.0625D;
        final double zero = 0;
        final double par10 = 0;
        this.addVertexWithUV(
                baseX + zero * (1.0D - torchLen) - var44,
                baseY + torchLen - toff,
                baseZ + par10 * (1.0D - torchLen) - var44,
                var20,
                var22);
        this.addVertexWithUV(
                baseX + zero * (1.0D - torchLen) - var44,
                baseY + torchLen - toff,
                baseZ + par10 * (1.0D - torchLen) + var44,
                var20,
                var26);
        this.addVertexWithUV(
                baseX + zero * (1.0D - torchLen) + var44,
                baseY + torchLen - toff,
                baseZ + par10 * (1.0D - torchLen) + var44,
                var24,
                var26);
        this.addVertexWithUV(
                baseX + zero * (1.0D - torchLen) + var44,
                baseY + torchLen - toff,
                baseZ + par10 * (1.0D - torchLen) - var44,
                var24,
                var22);

        final double var422 = 0.1915D + 1.0 / 16.0;
        this.addVertexWithUV(
                baseX + zero * (1.0D - torchLen) + var44,
                baseY + var422,
                baseZ + par10 * (1.0D - torchLen) - var44,
                var24b,
                var22b);
        this.addVertexWithUV(
                baseX + zero * (1.0D - torchLen) + var44,
                baseY + var422,
                baseZ + par10 * (1.0D - torchLen) + var44,
                var24b,
                var26b);
        this.addVertexWithUV(
                baseX + zero * (1.0D - torchLen) - var44,
                baseY + var422,
                baseZ + par10 * (1.0D - torchLen) + var44,
                var20b,
                var26b);
        this.addVertexWithUV(
                baseX + zero * (1.0D - torchLen) - var44,
                baseY + var422,
                baseZ + par10 * (1.0D - torchLen) - var44,
                var20b,
                var22b);

        this.addVertexWithUV(baseX + var44 + zero, baseY, baseZ - var44 + par10, var32, var30);
        this.addVertexWithUV(baseX + var44 + zero, baseY, baseZ + var44 + par10, var32, var34);
        this.addVertexWithUV(baseX - var44 + zero, baseY, baseZ + var44 + par10, var28, var34);
        this.addVertexWithUV(baseX - var44 + zero, baseY, baseZ - var44 + par10, var28, var30);

        this.addVertexWithUV(baseX - var44, baseY + 1.0D, var40, var16, var18);
        this.addVertexWithUV(baseX - var44 + zero, baseY + 0.0D, var40 + par10, var16, var19);
        this.addVertexWithUV(baseX - var44 + zero, baseY + 0.0D, var42 + par10, var17, var19);
        this.addVertexWithUV(baseX - var44, baseY + 1.0D, var42, var17, var18);

        this.addVertexWithUV(baseX + var44, baseY + 1.0D, var42, var16, var18);
        this.addVertexWithUV(baseX + zero + var44, baseY + 0.0D, var42 + par10, var16, var19);
        this.addVertexWithUV(baseX + zero + var44, baseY + 0.0D, var40 + par10, var17, var19);
        this.addVertexWithUV(baseX + var44, baseY + 1.0D, var40, var17, var18);

        this.addVertexWithUV(var36, baseY + 1.0D, baseZ + var44, var16, var18);
        this.addVertexWithUV(var36 + zero, baseY + 0.0D, baseZ + var44 + par10, var16, var19);
        this.addVertexWithUV(var38 + zero, baseY + 0.0D, baseZ + var44 + par10, var17, var19);
        this.addVertexWithUV(var38, baseY + 1.0D, baseZ + var44, var17, var18);

        this.addVertexWithUV(var38, baseY + 1.0D, baseZ - var44, var16, var18);
        this.addVertexWithUV(var38 + zero, baseY + 0.0D, baseZ - var44 + par10, var16, var19);
        this.addVertexWithUV(var36 + zero, baseY + 0.0D, baseZ - var44 + par10, var17, var19);
        this.addVertexWithUV(var36, baseY + 1.0D, baseZ - var44, var17, var18);
    }

    private void addVertexWithUV(double x, double y, double z, final double u, final double v) {
        final Tessellator var12 = Tessellator.instance;

        x -= this.centerX;
        y -= this.centerY;
        z -= this.centerZ;

        if (this.getSide() == ForgeDirection.DOWN) {
            y = -y;
            z = -z;
        }

        if (this.getSide() == ForgeDirection.EAST) {
            final double m = x;
            x = y;
            y = m;
            y = -y;
        }

        if (this.getSide() == ForgeDirection.WEST) {
            final double m = x;
            x = -y;
            y = m;
        }

        if (this.getSide() == ForgeDirection.SOUTH) {
            final double m = z;
            z = y;
            y = m;
            y = -y;
        }

        if (this.getSide() == ForgeDirection.NORTH) {
            final double m = z;
            z = -y;
            y = m;
        }

        x += this.centerX;
        y += this.centerY;
        z += this.centerZ;

        var12.addVertexWithUV(x, y, z, u, v);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderStatic(final int x, final int y, final int z, final IPartRenderHelper rh,
            final RenderBlocks renderer) {
        rh.setTexture(this.getItemStack().getIconIndex());

        renderer.renderAllFaces = true;

        final Tessellator tess = Tessellator.instance;
        tess.setBrightness(rh.getBlock().getMixedBrightnessForBlock(this.getHost().getTile().getWorldObj(), x, y, z));
        tess.setColorOpaque_F(1.0F, 1.0F, 1.0F);

        this.renderTorchAtAngle(x, y, z);

        renderer.renderAllFaces = false;

        rh.setBounds(7, 7, 11, 9, 9, 12);
        this.renderLights(x, y, z, rh, renderer);
    }

    @Override
    public int isProvidingStrongPower() {
        return this.prevState ? 15 : 0;
    }

    @Override
    public int isProvidingWeakPower() {
        return this.prevState ? 15 : 0;
    }

    @Override
    public void randomDisplayTick(final World world, final int x, final int y, final int z, final Random r) {
        if (this.isLevelEmitterOn()) {
            final ForgeDirection d = this.getSide();

            final double d0 = d.offsetX * 0.45F + (r.nextFloat() - 0.5F) * 0.2D;
            final double d1 = d.offsetY * 0.45F + (r.nextFloat() - 0.5F) * 0.2D;
            final double d2 = d.offsetZ * 0.45F + (r.nextFloat() - 0.5F) * 0.2D;

            world.spawnParticle("reddust", 0.5 + x + d0, 0.5 + y + d1, 0.5 + z + d2, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public int cableConnectionRenderTo() {
        return 16;
    }

    @Override
    public boolean onPartActivate(final EntityPlayer player, final Vec3 pos) {
        if (!player.isSneaking()) {
            if (Platform.isClient()) {
                return true;
            }

            Platform.openGUI(player, this.getHost().getTile(), this.getSide(), GuiBridge.GUI_ADVANCED_LEVEL_EMITTER);
            return true;
        }

        return false;
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        this.updateState();
    }

    @Override
    public boolean canConnectRedstone() {
        return true;
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.config.readFromNBT(data, "config");
        this.prevState = data.getBoolean("prevState");

        for (int slot = 0; slot < SLOTS; slot++) {
            this.slotActive[slot] = data.getBoolean("active" + slot);
            this.slotInverted[slot] = data.getBoolean("inverted" + slot);
            this.amount[slot] = data.getLong("amount" + slot);
            this.lastReportedValue[slot] = data.getLong("lastReportedValue" + slot);
        }
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.config.writeToNBT(data, "config");
        data.setBoolean("prevState", this.prevState);

        for (int slot = 0; slot < SLOTS; slot++) {
            data.setBoolean("active" + slot, this.slotActive[slot]);
            data.setBoolean("inverted" + slot, this.slotInverted[slot]);
            data.setLong("amount" + slot, this.amount[slot]);
            data.setLong("lastReportedValue" + slot, this.lastReportedValue[slot]);
        }
    }

    @Override
    public void uploadSettings(@NotNull final SettingsFrom from, @NotNull final NBTTagCompound compound) {
        super.uploadSettings(from, compound);

        for (int slot = 0; slot < SLOTS; slot++) {
            this.slotActive[slot] = compound.getBoolean("active" + slot);
            this.slotInverted[slot] = compound.getBoolean("inverted" + slot);
            this.amount[slot] = compound.getLong("amount" + slot);
        }

        this.configureWatchers();
    }

    @Override
    @NotNull
    public NBTTagCompound downloadSettings(@NotNull final SettingsFrom from) {
        final NBTTagCompound nbt = super.downloadSettings(from);

        for (int slot = 0; slot < SLOTS; slot++) {
            nbt.setBoolean("active" + slot, this.slotActive[slot]);
            nbt.setBoolean("inverted" + slot, this.slotInverted[slot]);
            nbt.setLong("amount" + slot, this.amount[slot]);
        }

        return nbt;
    }

    @Override
    public LevelItemInfo[] getLevelItemInfoList() {
        final LevelItemInfo[] result = new LevelItemInfo[SLOTS];
        for (int slot = 0; slot < SLOTS; slot++) {
            final IAEStack<?> stack = this.config.getAEStackInSlot(slot);
            if (stack == null) {
                result[slot] = null;
                continue;
            }

            final boolean slotState = this.slotActive[slot] && (this.slotInverted[slot]
                    ? this.lastReportedValue[slot] < this.amount[slot]
                    : this.lastReportedValue[slot] >= this.amount[slot]);

            result[slot] = new LevelItemInfo(
                    stack,
                    this.lastReportedValue[slot],
                    -1,
                    slotState ? LevelState.Craft : LevelState.Idle);
        }
        return result;
    }

    @Override
    public int rowSize() {
        return SLOTS;
    }

    @Override
    public void saveAEStackInv() {
        this.configureWatchers();
    }

    @Override
    public IAEStackInventory getAEInventoryByName(final StorageName name) {
        if (name == StorageName.CONFIG) {
            return this.config;
        }
        return null;
    }
}
