package appeng.client.gui.implementations;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import appeng.api.config.SchedulingMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.StorageName;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.container.implementations.ContainerBusIO;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
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

    private void initVirtualSlots() {
        final IAEStackInventory inputInv = this.bus.getAEInventoryByName(StorageName.NONE);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                VirtualMEPhantomSlot slot = new VirtualMEPhantomSlot(
                        62 + 18 * x,
                        22 + 18 * (y % (3)),
                        inputInv,
                        slotSequence[x + y * 3]);
                this.virtualSlots[slotSequence[x + y * 3]] = slot;
                this.registerVirtualSlots(slot);
            }
        }
    }

    @Override
    protected void addButtons() {
        super.addButtons();

        this.schedulingMode = new GuiImgButton(
                this.guiLeft - 18,
                this.guiTop + 68,
                Settings.SCHEDULING_MODE,
                SchedulingMode.DEFAULT);
        this.buttonList.add(this.schedulingMode);
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
        if (capacity < 1) {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.4F);
            GL11.glEnable(GL11.GL_BLEND);
        }

        this.drawTexturedModalRect(offsetX + 61, offsetY + 39, 79, 39, 18, 18);
        this.drawTexturedModalRect(offsetX + 79, offsetY + 21, 79, 39, 18, 18);
        this.drawTexturedModalRect(offsetX + 97, offsetY + 39, 79, 39, 18, 18);
        this.drawTexturedModalRect(offsetX + 79, offsetY + 57, 79, 39, 18, 18);

        if (capacity < 1) {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopAttrib();
        }

        if (capacity < 2) {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.4F);
            GL11.glEnable(GL11.GL_BLEND);
        }

        this.drawTexturedModalRect(offsetX + 61, offsetY + 21, 79, 39, 18, 18);
        this.drawTexturedModalRect(offsetX + 61, offsetY + 57, 79, 39, 18, 18);
        this.drawTexturedModalRect(offsetX + 97, offsetY + 21, 79, 39, 18, 18);
        this.drawTexturedModalRect(offsetX + 97, offsetY + 57, 79, 39, 18, 18);

        if (capacity < 2) {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopAttrib();
        }

    }

    @Override
    protected void handleButtonVisibility() {
        super.handleButtonVisibility();

        final int capacity = this.cvb.getUpgradeable().getInstalledUpgrades(Upgrades.CAPACITY);
        final boolean firstTier = capacity > 0;
        final boolean secondTier = capacity > 1;

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
    protected void actionPerformed(GuiButton btn) {
        super.actionPerformed(btn);

        final boolean backwards = Mouse.isButtonDown(1);

        if (btn == this.schedulingMode) {
            NetworkHandler.instance.sendToServer(new PacketConfigButton(this.schedulingMode.getSetting(), backwards));
        }
    }

    @Override
    protected void handlePhantomSlotInteraction(VirtualMEPhantomSlot slot, int mouseButton) {
        StorageChannel channel = StorageChannel.getStorageChannelByParametrizedClass(this.bus.getClass());
        slot.handleMouseClicked(channel == StorageChannel.ITEMS, channel == StorageChannel.FLUIDS, false);
    }
}
