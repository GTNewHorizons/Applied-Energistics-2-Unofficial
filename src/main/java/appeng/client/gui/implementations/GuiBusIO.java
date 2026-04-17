package appeng.client.gui.implementations;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import org.lwjgl.input.Mouse;

import appeng.api.config.SchedulingMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.container.implementations.ContainerBusIO;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.parts.automation.PartBaseExportBus;
import appeng.parts.automation.PartSharedItemBus;
import appeng.tile.inventory.IAEStackInventory;

public class GuiBusIO extends GuiUpgradeable {

    protected final VirtualMEPhantomSlot[] virtualSlots = new VirtualMEPhantomSlot[9];
    protected GuiImgButton schedulingMode;
    private final PartSharedItemBus<?> bus;
    private static final int[] slotSequence = new int[] { 5, 3, 6, 1, 0, 2, 7, 4, 8 };

    public GuiBusIO(final InventoryPlayer inventoryPlayer, final PartSharedItemBus<?> te) {
        super(new ContainerBusIO(inventoryPlayer, te));
        this.bus = te;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.initVirtualSlots();
    }

    @Override
    protected void addButtons() {
        super.addButtons();

        if (this.bus instanceof PartBaseExportBus<?>) {
            this.schedulingMode = new GuiImgButton(
                    this.guiLeft - 18,
                    this.guiTop + 68,
                    Settings.SCHEDULING_MODE,
                    SchedulingMode.DEFAULT);
            this.buttonList.add(this.schedulingMode);
        }

        initCustomButtons(this.guiLeft - 18, 88);
    }

    private void initVirtualSlots() {
        final IAEStackInventory inputInv = this.bus.getAEInventoryByName(StorageName.CONFIG);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                VirtualMEPhantomSlot slot = new VirtualMEPhantomSlot(
                        62 + 18 * x,
                        22 + 18 * (y % (3)),
                        inputInv,
                        slotSequence[x + y * 3],
                        this::acceptType);
                this.virtualSlots[slotSequence[x + y * 3]] = slot;
                this.registerVirtualSlots(slot);
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton btn) {
        super.actionPerformed(btn);

        final boolean backwards = Mouse.isButtonDown(1);

        if (btn == this.schedulingMode) {
            NetworkHandler.instance.sendToServer(new PacketConfigButton(this.schedulingMode.getSetting(), backwards));
            return;
        }

        actionPerformedCustomButtons(btn);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);

        if (this.schedulingMode != null) {
            this.schedulingMode.set(this.cvb.getSchedulingMode());
        }
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawBG(offsetX, offsetY, mouseX, mouseY);

        final int capacity = this.cvb.getUpgradeable().getInstalledUpgrades(Upgrades.CAPACITY);
        final boolean hasOreFilter = this.cvb.getUpgradeable().getInstalledUpgrades(Upgrades.ORE_FILTER) != 0;
        final boolean hasFirstTier = capacity > 0;
        final boolean hasSecondTier = capacity > 1;

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                final int slotTextureX = hasOreFilter // true == inactive
                        || (!hasSecondTier && slotSequence[x + y * 3] > 4)
                        || (!hasFirstTier && slotSequence[x + y * 3] != 0) ? 79 : 61;
                this.drawTexturedModalRect(offsetX + 61 + (18 * x), offsetY + 21 + (18 * y), slotTextureX, 21, 18, 18);
            }
        }
    }

    @Override
    protected void handleButtonVisibility() {
        super.handleButtonVisibility();

        final int capacity = this.cvb.getUpgradeable().getInstalledUpgrades(Upgrades.CAPACITY);
        final boolean hasOreFilter = this.cvb.getUpgradeable().getInstalledUpgrades(Upgrades.ORE_FILTER) != 0;
        final boolean firstTier = capacity > 0 && !hasOreFilter;
        final boolean secondTier = capacity > 1 && !hasOreFilter;

        this.virtualSlots[0].setHidden(hasOreFilter);

        this.virtualSlots[1].setHidden(!firstTier);
        this.virtualSlots[2].setHidden(!firstTier);
        this.virtualSlots[3].setHidden(!firstTier);
        this.virtualSlots[4].setHidden(!firstTier);

        this.virtualSlots[5].setHidden(!secondTier);
        this.virtualSlots[6].setHidden(!secondTier);
        this.virtualSlots[7].setHidden(!secondTier);
        this.virtualSlots[8].setHidden(!secondTier);

        if (this.schedulingMode != null) {
            this.schedulingMode.setVisibility(this.bc.getInstalledUpgrades(Upgrades.CAPACITY) > 0);
        }
    }

    @Override
    protected String getBackground() {
        return "guis/bus.png";
    }

    @Override
    protected String getName() {
        return this.bus.getBusName();
    }

    private boolean acceptType(VirtualMEPhantomSlot slot, IAEStackType<?> type, int mouseButton) {
        return type == this.bus.getStackType();
    }
}
