package appeng.container.implementations;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.config.PriorityCardMode;
import appeng.api.config.Settings;
import appeng.api.util.IConfigManager;
import appeng.container.guisync.GuiSync;
import appeng.items.contents.PriorityCardObject;
import appeng.util.Platform;

public class ContainerPriorityCard extends ContainerPriority {

    private final PriorityCardObject host;

    @GuiSync(3)
    public PriorityCardMode cardMode = PriorityCardMode.EDIT;

    public ContainerPriorityCard(final InventoryPlayer ip, final PriorityCardObject host) {
        super(ip, host);
        this.host = host;
        this.lockPlayerInventorySlot(host.getInventorySlot());
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) {
            final IConfigManager cm = this.host.getConfigManager();
            this.setCardMode((PriorityCardMode) cm.getSetting(Settings.PRIORITY_CARD_MODE));
        }

        final ItemStack currentItem = this.getPlayerInv().getStackInSlot(this.host.getInventorySlot());

        if (currentItem != this.host.getItemStack()) {
            if (currentItem != null) {
                if (Platform.isSameItem(this.host.getItemStack(), currentItem)) {
                    this.getPlayerInv()
                            .setInventorySlotContents(this.host.getInventorySlot(), this.host.getItemStack());
                } else {
                    this.setValidContainer(false);
                }
            } else {
                this.setValidContainer(false);
            }
        }

        super.detectAndSendChanges();
    }

    public void setCardMode(PriorityCardMode mode) {
        this.cardMode = mode;
    }

    public PriorityCardMode getCardMode() {
        return this.cardMode;
    }
}
