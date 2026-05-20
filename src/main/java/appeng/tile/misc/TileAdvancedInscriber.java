package appeng.tile.misc;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Upgrades;
import appeng.api.definitions.ITileDefinition;
import appeng.api.features.IInscriberRecipe;
import appeng.api.features.InscriberProcessType;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.core.settings.TickRates;
import appeng.me.GridAccessException;
import appeng.parts.automation.DefinitionUpgradeInventory;
import appeng.parts.automation.UpgradeInventory;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkPowerTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;

public class TileAdvancedInscriber extends AENetworkPowerTile
        implements IGridTickable, IUpgradeableHost, IConfigManagerHost {

    public static final int SLOT_TOP = 0;
    public static final int SLOT_MIDDLE = 1;
    public static final int SLOT_BOTTOM = 2;
    public static final int SLOT_OUTPUT = 3;
    public static final String NBT_INV = "inv";
    public static final String NBT_UPGRADES = "upgrades";
    public static final String NBT_PROGRESS = "progress";
    public static final String NBT_TOP_LOCKED = "topLocked";
    public static final String NBT_BOTTOM_LOCKED = "bottomLocked";
    public static final String NBT_PENDING_OUTPUT = "output";

    private static final int MAX_PROCESSING_TIME = 100;
    private static final int BASE_POWER_PER_TICK = 10;
    private static final int[] ACCESSIBLE_SLOTS = { SLOT_TOP, SLOT_MIDDLE, SLOT_BOTTOM, SLOT_OUTPUT };

    private final AppEngInternalInventory inv = new AppEngInternalInventory(this, 4);
    private final IConfigManager settings;
    private final UpgradeInventory upgrades;

    private int processingTime = 0;
    private ItemStack pendingOutput;
    private boolean topLocked = true;
    private boolean bottomLocked = true;
    private boolean active = false;

    public TileAdvancedInscriber() {
        this.getProxy().setValidSides(EnumSet.allOf(ForgeDirection.class));
        this.setPowerSides(EnumSet.allOf(ForgeDirection.class));
        this.setInternalMaxPower(5000);
        this.getProxy().setIdlePowerUsage(0);
        this.settings = new ConfigManager(this);

        final ITileDefinition advancedInscriber = AEApi.instance().definitions().blocks().advancedInscriber();
        this.upgrades = new DefinitionUpgradeInventory(advancedInscriber, this, this.getUpgradeSlots());
    }

    private int getUpgradeSlots() {
        return 5;
    }

    @Override
    public AECableType getCableConnectionType(final ForgeDirection dir) {
        return AECableType.COVERED;
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeToNBT_TileAdvancedInscriber(final NBTTagCompound data) {
        this.inv.writeToNBT(data, NBT_INV);
        this.upgrades.writeToNBT(data, NBT_UPGRADES);
        this.settings.writeToNBT(data);
        data.setInteger(NBT_PROGRESS, this.processingTime);
        data.setBoolean(NBT_TOP_LOCKED, this.topLocked);
        data.setBoolean(NBT_BOTTOM_LOCKED, this.bottomLocked);

        if (this.pendingOutput != null) {
            final NBTTagCompound output = new NBTTagCompound();
            this.pendingOutput.writeToNBT(output);
            data.setTag(NBT_PENDING_OUTPUT, output);
        }
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readFromNBT_TileAdvancedInscriber(final NBTTagCompound data) {
        this.inv.readFromNBT(data, NBT_INV);
        this.upgrades.readFromNBT(data, NBT_UPGRADES);
        this.settings.readFromNBT(data);
        this.processingTime = data.getInteger(NBT_PROGRESS);
        this.topLocked = !data.hasKey(NBT_TOP_LOCKED) || data.getBoolean(NBT_TOP_LOCKED);
        this.bottomLocked = !data.hasKey(NBT_BOTTOM_LOCKED) || data.getBoolean(NBT_BOTTOM_LOCKED);
        this.pendingOutput = data.hasKey(NBT_PENDING_OUTPUT)
                ? ItemStack.loadItemStackFromNBT(data.getCompoundTag(NBT_PENDING_OUTPUT))
                : null;
        this.updateActiveState();
    }

    @Override
    public void getDrops(final World w, final int x, final int y, final int z, final List<ItemStack> drops) {
        super.getDrops(w, x, y, z, drops);

        for (int h = 0; h < this.upgrades.getSizeInventory(); h++) {
            final ItemStack is = this.upgrades.getStackInSlot(h);
            if (is != null) {
                drops.add(is);
            }
        }

        if (this.pendingOutput != null) {
            drops.add(this.pendingOutput.copy());
        }
    }

    @Override
    public IInventory getInternalInventory() {
        return this.inv;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isItemValidForSlot(final int slot, final ItemStack stack) {
        if (stack == null || slot == SLOT_OUTPUT) {
            return false;
        }

        return switch (slot) {
            case SLOT_TOP -> this
                    .isValidPartialRecipe(stack, this.getStackInSlot(SLOT_MIDDLE), this.getStackInSlot(SLOT_BOTTOM));
            case SLOT_MIDDLE -> this
                    .isValidPartialRecipe(this.getStackInSlot(SLOT_TOP), stack, this.getStackInSlot(SLOT_BOTTOM));
            case SLOT_BOTTOM -> this
                    .isValidPartialRecipe(this.getStackInSlot(SLOT_TOP), this.getStackInSlot(SLOT_MIDDLE), stack);
            default -> false;
        };
    }

    @Override
    public boolean canExtractItem(final int slotIndex, final ItemStack extractedItem, final int side) {
        return switch (slotIndex) {
            case SLOT_OUTPUT -> true;
            case SLOT_TOP -> !this.topLocked && this.pendingOutput == null && this.getStackInSlot(SLOT_MIDDLE) == null;
            case SLOT_BOTTOM -> !this.bottomLocked && this.pendingOutput == null
                    && this.getStackInSlot(SLOT_MIDDLE) == null;
            default -> false;
        };
    }

    @Override
    public int[] getAccessibleSlotsBySide(final ForgeDirection d) {
        return ACCESSIBLE_SLOTS;
    }

    @Override
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc, final ItemStack removed,
            final ItemStack added) {
        if (mc != InvOperation.markDirty) {
            this.wakeDevice();
        }
    }

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(TickRates.Inscriber.getMin(), TickRates.Inscriber.getMax(), !this.hasWork(), false);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        if (this.pendingOutput == null) {
            this.tryStartJob();
        }

        boolean keepTicking = false;
        if (this.pendingOutput != null) {
            if (this.processingTime >= MAX_PROCESSING_TIME) {
                keepTicking = this.tryOutput();
            } else {
                this.tryProgress(ticksSinceLastCall);
                keepTicking = true;
                if (this.processingTime >= MAX_PROCESSING_TIME) {
                    keepTicking = this.tryOutput();
                }
            }
        } else {
            this.active = false;
        }

        this.updateActiveState();
        return keepTicking ? TickRateModulation.URGENT : TickRateModulation.SLEEP;
    }

    private boolean hasWork() {
        return this.pendingOutput != null || this.getTask() != null;
    }

    private void tryStartJob() {
        final IInscriberRecipe recipe = this.getTask();
        if (recipe == null) {
            this.processingTime = 0;
            this.active = false;
            return;
        }

        this.pendingOutput = recipe.getOutput().copy();
        this.processingTime = 0;
        this.active = true;
        this.decrStackSize(SLOT_MIDDLE, 1);

        if (recipe.getProcessType() == InscriberProcessType.Press) {
            this.decrStackSize(SLOT_TOP, 1);
            this.decrStackSize(SLOT_BOTTOM, 1);
        }

        this.markDirty();
        this.markForUpdate();
    }

    private void tryProgress(final int ticksSinceLastCall) {
        try {
            final IEnergyGrid energyGrid = this.getProxy().getEnergy();
            IEnergySource source = this;
            final int speedFactor = this.getSpeedFactor();
            final int powerConsumption = BASE_POWER_PER_TICK * speedFactor;
            final double powerThreshold = powerConsumption - 0.01;
            double extracted = this.extractAEPower(powerConsumption, Actionable.SIMULATE, PowerMultiplier.CONFIG);

            if (extracted <= powerThreshold) {
                source = energyGrid;
                extracted = energyGrid.extractAEPower(powerConsumption, Actionable.SIMULATE, PowerMultiplier.CONFIG);
            }

            if (extracted > powerThreshold) {
                source.extractAEPower(powerConsumption, Actionable.MODULATE, PowerMultiplier.CONFIG);
                this.processingTime += Math.max(1, ticksSinceLastCall) * speedFactor;
                if (this.processingTime > MAX_PROCESSING_TIME) {
                    this.processingTime = MAX_PROCESSING_TIME;
                }
                this.active = true;
                return;
            }
        } catch (final GridAccessException ignored) {
            // No network power available this tick.
        }

        this.active = false;
    }

    private int getSpeedFactor() {
        return 1 + this.upgrades.getInstalledUpgrades(Upgrades.SPEED);
    }

    private boolean tryOutput() {
        final ItemStack currentOutput = this.getStackInSlot(SLOT_OUTPUT);

        if (currentOutput == null) {
            this.setInventorySlotContents(SLOT_OUTPUT, this.pendingOutput.copy());
            return this.finishOutput();
        }

        if (this.matches(currentOutput, this.pendingOutput)
                && currentOutput.stackSize + this.pendingOutput.stackSize <= currentOutput.getMaxStackSize()) {
            currentOutput.stackSize += this.pendingOutput.stackSize;
            return this.finishOutput();
        }

        this.active = false;
        return false;
    }

    private boolean finishOutput() {
        this.pendingOutput = null;
        this.processingTime = 0;
        this.markDirty();
        this.markForUpdate();

        final boolean hasNextTask = this.getTask() != null;
        this.active = hasNextTask;
        return hasNextTask;
    }

    @Nullable
    public IInscriberRecipe getTask() {
        final ItemStack middle = this.getStackInSlot(SLOT_MIDDLE);

        if (middle == null) {
            return null;
        }

        for (final IInscriberRecipe recipe : AEApi.instance().registries().inscriber().getRecipes()) {
            if (this.isMatchingFullRecipe(recipe)) {
                return recipe;
            }
        }

        return null;
    }

    private boolean isMatchingFullRecipe(final IInscriberRecipe recipe) {
        final ItemStack middle = this.getStackInSlot(SLOT_MIDDLE);
        if (middle == null || !this.matches(recipe.getTopOptional().orNull(), this.getStackInSlot(SLOT_TOP))
                || !this.matches(recipe.getBottomOptional().orNull(), this.getStackInSlot(SLOT_BOTTOM))) {
            return false;
        }

        return this.matchesAny(recipe.getInputs(), middle);
    }

    private boolean isValidPartialRecipe(final ItemStack top, final ItemStack middle, final ItemStack bottom) {
        for (final IInscriberRecipe recipe : AEApi.instance().registries().inscriber().getRecipes()) {
            if (this.isMatchingPartialRecipe(recipe, top, middle, bottom)) {
                return true;
            }
        }

        return false;
    }

    private boolean isMatchingPartialRecipe(final IInscriberRecipe recipe, final ItemStack top, final ItemStack middle,
            final ItemStack bottom) {
        if (top != null
                && (!recipe.getTopOptional().isPresent() || !this.matches(recipe.getTopOptional().get(), top))) {
            return false;
        }

        if (middle != null && !this.matchesAny(recipe.getInputs(), middle)) {
            return false;
        }

        return bottom == null
                || (recipe.getBottomOptional().isPresent() && this.matches(recipe.getBottomOptional().get(), bottom));
    }

    private boolean matchesAny(final Collection<ItemStack> stacks, final ItemStack input) {
        for (final ItemStack stack : stacks) {
            if (this.matches(stack, input)) {
                return true;
            }
        }

        return false;
    }

    private boolean matches(@Nullable final ItemStack expected, @Nullable final ItemStack actual) {
        if (expected == null || actual == null) {
            return expected == null && actual == null;
        }
        return Platform.isSameItemPrecise(expected, actual);
    }

    private void wakeDevice() {
        try {
            this.getProxy().getTick().wakeDevice(this.getProxy().getNode());
        } catch (final GridAccessException ignored) {
            // The node may not be attached yet.
        }
    }

    @Override
    public void gridChanged() {
        this.wakeDevice();
    }

    @Override
    public void markDirty() {
        this.wakeDevice();
        super.markDirty();
    }

    @Override
    public boolean shouldRefresh(final Block oldBlock, final Block newBlock, final int oldMeta, final int newMeta,
            final World world, final int x, final int y, final int z) {
        return oldBlock != newBlock;
    }

    private void updateActiveState() {
        if (this.worldObj == null || !this.worldObj.blockExists(this.xCoord, this.yCoord, this.zCoord)) {
            return;
        }

        final int meta = this.active ? 1 : 0;
        if (this.worldObj.getBlockMetadata(this.xCoord, this.yCoord, this.zCoord) != meta) {
            this.worldObj.setBlockMetadataWithNotify(this.xCoord, this.yCoord, this.zCoord, meta, 3);
        }
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.settings;
    }

    @Override
    public IInventory getInventoryByName(final String name) {
        if (name.equals(NBT_INV)) {
            return this.inv;
        }

        if (name.equals(NBT_UPGRADES)) {
            return this.upgrades;
        }

        return null;
    }

    @Override
    public int getInstalledUpgrades(final Upgrades u) {
        return this.upgrades.getInstalledUpgrades(u);
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {}

    public int getMaxProcessingTime() {
        return MAX_PROCESSING_TIME;
    }

    public int getProcessingTime() {
        return this.processingTime;
    }

    public boolean isTopLocked() {
        return this.topLocked;
    }

    public boolean isBottomLocked() {
        return this.bottomLocked;
    }

    public void setTopLocked(final boolean topLocked) {
        this.topLocked = topLocked;
        this.markDirty();
    }

    public void setBottomLocked(final boolean bottomLocked) {
        this.bottomLocked = bottomLocked;
        this.markDirty();
    }

    @Override
    protected boolean isInventoryPersistent() {
        return false;
    }
}
