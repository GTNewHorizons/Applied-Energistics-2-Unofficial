package appeng.container.implementations;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ICrafting;

import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.implementations.GuiPatternItemRenamer;
import appeng.client.gui.implementations.GuiPatternValueAmount;
import appeng.container.ContainerSubGui;
import appeng.container.interfaces.IVirtualSlotSource;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketPatternValueSet;
import appeng.util.Platform;

public class ContainerPatternValueAmount extends ContainerSubGui implements IVirtualSlotSource {

    private StorageName invName;
    private IAEStack<?> aes;
    private int slotIndex;

    public ContainerPatternValueAmount(final InventoryPlayer ip, final Object te) {
        super(ip, te);
    }

    @Override
    public void updateVirtualSlot(StorageName invName, int slotId, IAEStack<?> aes) {
        this.invName = invName;
        this.aes = aes;
        this.slotIndex = slotId;

        if (Platform.isServer()) {
            for (ICrafting crafter : this.crafters) {
                final EntityPlayerMP emp = (EntityPlayerMP) crafter;
                NetworkHandler.instance.sendTo(new PacketPatternValueSet(aes, invName, slotId), emp);
            }
        } else {
            final GuiScreen gs = Minecraft.getMinecraft().currentScreen;
            if (gs instanceof GuiPatternValueAmount gpva) {
                gpva.update();
            } else if (gs instanceof GuiPatternItemRenamer gpir) {
                gpir.update();
            }
        }
    }

    public StorageName getInvName() {
        return invName;
    }

    public IAEStack<?> getAEStack() {
        return aes;
    }

    public int getSlotIndex() {
        return slotIndex;
    }
}
