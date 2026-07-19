package appeng.container.implementations;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import appeng.api.config.BooleanOperation;
import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.api.parts.IAdvancedLevelEmitter;
import appeng.api.storage.StorageName;
import appeng.client.gui.IGuiSub;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.AEBaseContainer;
import appeng.container.PrimaryGui;
import appeng.container.interfaces.IContainerSubGui;
import appeng.container.slot.SlotInaccessible;
import appeng.container.sync.SyncRegistrar;
import appeng.container.sync.handlers.AEStackInventorySyncHandler;
import appeng.container.sync.handlers.BooleanSyncHandler;
import appeng.container.sync.handlers.ConfigEnumSyncHandler;
import appeng.container.sync.handlers.LongSyncHandler;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ContainerAdvancedLevelEmitter extends AEBaseContainer implements IContainerSubGui {

    private final IAdvancedLevelEmitter lvlEmitter;

    @SideOnly(Side.CLIENT)
    private final MEGuiTextField[] textFields = new MEGuiTextField[IAdvancedLevelEmitter.SLOT_COUNT];

    private final LongSyncHandler[] amountSync = new LongSyncHandler[IAdvancedLevelEmitter.SLOT_COUNT];
    private final BooleanSyncHandler[] activeSync = new BooleanSyncHandler[IAdvancedLevelEmitter.SLOT_COUNT];
    private final BooleanSyncHandler[] invertedSync = new BooleanSyncHandler[IAdvancedLevelEmitter.SLOT_COUNT];
    private final ConfigEnumSyncHandler<BooleanOperation> logicModeSync;
    public final AEStackInventorySyncHandler configSync;

    public ContainerAdvancedLevelEmitter(final InventoryPlayer ip, final IAdvancedLevelEmitter te) {
        super(ip, te);
        this.lvlEmitter = te;

        final SyncRegistrar sync = this.syncRegistrar();

        for (int i = 0; i < IAdvancedLevelEmitter.SLOT_COUNT; i++) {
            final int slot = i;

            this.amountSync[slot] = sync.longSync("amount" + slot).onClientChange((oldValue, newValue) -> {
                final MEGuiTextField field = this.textFields[slot];
                if (field != null) {
                    field.setText(String.valueOf(newValue));
                    field.setCursorPositionEnd();
                }
            }).onServerChange((oldValue, newValue) -> this.lvlEmitter.setReportingValue(slot, newValue));

            this.activeSync[slot] = sync.booleanSync("active" + slot)
                    .onServerChange((oldValue, newValue) -> this.lvlEmitter.setSlotActive(slot, newValue));

            this.invertedSync[slot] = sync.booleanSync("inverted" + slot)
                    .onServerChange((oldValue, newValue) -> this.lvlEmitter.setSlotInverted(slot, newValue));
        }

        this.logicModeSync = sync.configEnum(
                "logicMode",
                Settings.ADVANCED_LEVEL_EMITTER_LOGIC,
                BooleanOperation.class,
                this.lvlEmitter.getConfigManager());

        this.configSync = sync.aeStackInventory("config", this.lvlEmitter.getAEInventoryByName(StorageName.CONFIG));

        // sub gui copy paste
        this.primaryGuiButtonIcon = new SlotInaccessible(new AppEngInternalInventory(null, 1), 0, 0, -9000);
        this.addSlotToContainer(this.primaryGuiButtonIcon);

        this.bindPlayerInventory(ip, -1, 153);
    }

    @SideOnly(Side.CLIENT)
    public void setTextField(final int slot, final MEGuiTextField field) {
        this.textFields[slot] = field;
    }

    @Override
    public void detectAndSendChanges() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);

        if (Platform.isServer()) {
            for (int slot = 0; slot < IAdvancedLevelEmitter.SLOT_COUNT; slot++) {
                this.amountSync[slot].set(this.lvlEmitter.getReportingValue(slot));
                this.activeSync[slot].set(this.lvlEmitter.isSlotActive(slot));
                this.invertedSync[slot].set(this.lvlEmitter.isSlotInverted(slot));
            }
            this.logicModeSync.syncFromConfig();
        }

        super.detectAndSendChanges();
    }

    public long getReportingValue(final int slot) {
        return this.amountSync[slot].get();
    }

    public void setLevel(final int slot, final long value) {
        this.amountSync[slot].set(value);
    }

    public boolean isSlotActive(final int slot) {
        return this.activeSync[slot].get();
    }

    public void setSlotActive(final int slot, final boolean active) {
        this.activeSync[slot].set(active);
    }

    public boolean isSlotInverted(final int slot) {
        return this.invertedSync[slot].get();
    }

    public void setSlotInverted(final int slot, final boolean inverted) {
        this.invertedSync[slot].set(inverted);
    }

    public BooleanOperation getLogicMode() {
        return this.logicModeSync.get();
    }

    public void rotateLogicMode(final boolean backwards) {
        this.logicModeSync.rotate(backwards);
    }

    // for level terminal
    // sub gui copypaste
    private final Slot primaryGuiButtonIcon;

    @SideOnly(Side.CLIENT)
    private IGuiSub guiLink;

    @Override
    public void onSlotChange(final Slot s) {
        if (Platform.isClient() && this.primaryGuiButtonIcon == s && this.primaryGuiButtonIcon.getHasStack()) {
            this.guiLink.initPrimaryGuiButton();
        }
    }

    @Override
    public void setPrimaryGui(final PrimaryGui primaryGui) {
        super.setPrimaryGui(primaryGui);
        this.primaryGuiButtonIcon.putStack(primaryGui.getIcon());
    }

    @SideOnly(Side.CLIENT)
    public ItemStack getPrimaryGuiIcon() {
        return this.primaryGuiButtonIcon.getStack();
    }

    @SideOnly(Side.CLIENT)
    public void setGuiLink(final IGuiSub gs) {
        this.guiLink = gs;
    }
}
