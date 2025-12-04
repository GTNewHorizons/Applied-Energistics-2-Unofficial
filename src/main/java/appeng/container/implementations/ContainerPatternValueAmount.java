package appeng.container.implementations;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ICrafting;

import appeng.api.storage.ITerminalHost;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.implementations.GuiPatternItemRenamer;
import appeng.client.gui.implementations.GuiPatternValueAmount;
import appeng.container.ContainerSubGui;
import appeng.container.interfaces.IVirtualSlotHolder;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketVirtualSlot;
import appeng.util.Platform;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

public class ContainerPatternValueAmount extends ContainerSubGui implements IVirtualSlotHolder {

    private StorageName invName;
    private IAEStack<?> aes;
    private int slotIndex;

    public ContainerPatternValueAmount(final InventoryPlayer ip, final ITerminalHost te) {
        super(ip, te);
    }

    @Override
    public void receiveSlotStacks(StorageName invName, Int2ObjectMap<IAEStack<?>> slotStacks) {
        var entry = slotStacks.int2ObjectEntrySet().iterator().next();
        this.invName = invName;
        this.aes = entry.getValue();
        this.slotIndex = entry.getIntKey();

        if (Platform.isServer()) {
            for (ICrafting crafter : this.crafters) {
                final EntityPlayerMP emp = (EntityPlayerMP) crafter;
                NetworkHandler.instance.sendTo(new PacketVirtualSlot(invName, slotIndex, aes), emp);
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
