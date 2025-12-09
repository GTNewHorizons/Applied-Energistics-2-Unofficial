package appeng.client.gui.implementations;

import net.minecraft.entity.player.InventoryPlayer;

import org.lwjgl.opengl.GL11;

import appeng.api.config.Upgrades;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.StorageName;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.container.implementations.ContainerBusIO;
import appeng.parts.automation.PartSharedItemBus;
import appeng.tile.inventory.IAEStackInventory;

public class GuiBusIO extends GuiUpgradeable {

    protected VirtualMEPhantomSlot[] virtualSlots;
    private final PartSharedItemBus<?> bus;

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
        this.virtualSlots = new VirtualMEPhantomSlot[9];
        final IAEStackInventory inputInv = this.bus.getAEInventoryByName(StorageName.NONE);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                VirtualMEPhantomSlot slot = new VirtualMEPhantomSlot(
                        62 + 18 * x,
                        22 + 18 * (y % (3)),
                        inputInv,
                        x + y * 3);
                this.virtualSlots[x + y * 3] = slot;
                this.registerVirtualSlots(slot);
            }
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
        this.virtualSlots[3].setHidden(!firstTier);
        this.virtualSlots[5].setHidden(!firstTier);
        this.virtualSlots[7].setHidden(!firstTier);

        this.virtualSlots[0].setHidden(!secondTier);
        this.virtualSlots[2].setHidden(!secondTier);
        this.virtualSlots[6].setHidden(!secondTier);
        this.virtualSlots[8].setHidden(!secondTier);

    }

    @Override
    protected String getBackground() {
        return "guis/bus.png";
    }

    @Override
    protected void handlePhantomSlotInteraction(VirtualMEPhantomSlot slot, int mouseButton) {
        StorageChannel channel = StorageChannel.getStorageChannelByParametrizedClass(this.bus.getClass());
        slot.handleMouseClicked(channel == StorageChannel.ITEMS, channel == StorageChannel.FLUIDS, false);
    }
}
