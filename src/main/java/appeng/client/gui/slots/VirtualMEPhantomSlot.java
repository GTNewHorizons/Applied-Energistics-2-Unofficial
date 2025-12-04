package appeng.client.gui.slots;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketVirtualSlot;
import appeng.integration.IntegrationRegistry;
import appeng.integration.IntegrationType;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.FluidUtils;
import appeng.util.Platform;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import codechicken.nei.ItemPanels;
import codechicken.nei.recipe.StackInfo;

public class VirtualMEPhantomSlot extends VirtualMESlot {

    private final IAEStackInventory inventory;

    public VirtualMEPhantomSlot(int x, int y, IAEStackInventory inventory, int slotIndex) {
        super(x, y, slotIndex);
        this.inventory = inventory;
        this.showAmount = false;
    }

    @Nullable
    @Override
    public IAEStack<?> getAEStack() {
        return this.inventory.getAEStackInSlot(this.getSlotIndex());
    }

    public StorageName getStorageName() {
        return this.inventory.getStorageName();
    }

    public void handleMouseClicked(boolean acceptItem, boolean acceptFluid, boolean isExtraAction) {
        handleMouseClicked(acceptItem, acceptFluid, isExtraAction, 0);
    }

    // try nei dragNDrop make frind with regular interaction, unfinished
    public void handleMouseClicked(boolean acceptItem, boolean acceptFluid, boolean isExtraAction, int mouseButton) {
        IAEStack<?> currentStack = this.getAEStack();
        final ItemStack newStack = getTargetStack();

        if (newStack != null && !this.showAmount) {
            newStack.stackSize = 1;
        }

        // need always convert display fluid stack from nei or nothing.
        if (newStack != null && !FluidUtils.isFluidContainer(newStack)
                && StackInfo.getFluid(Platform.copyStackWithSize(newStack, 1)) != null) {
            isExtraAction = true;
            acceptItem = false;
        }

        switch (mouseButton) {
            case 0 -> { // left click
                if (newStack != null) {
                    if (acceptFluid && (!acceptItem || isExtraAction)) {
                        currentStack = AEFluidStack.create(FluidUtils.getFluidFromContainer(newStack));
                    } else if (acceptItem) currentStack = AEItemStack.create(newStack);
                } else currentStack = null;
            }
            case 1 -> { // right click
                if (newStack != null) {
                    newStack.stackSize = 1;

                    if (acceptFluid && (!acceptItem || isExtraAction))
                        currentStack = AEFluidStack.create(FluidUtils.getFluidFromContainer(newStack));
                    else if (acceptItem) currentStack = AEItemStack.create(newStack);

                    if (currentStack != null && currentStack.equals(this.getAEStack())) {
                        currentStack = this.getAEStack();
                        currentStack.decStackSize(-1);
                    }
                } else if (currentStack != null) {
                    currentStack.decStackSize(1);
                    if (currentStack.getStackSize() <= 0) currentStack = null;
                }
            }
        }

        // Set on the client to avoid lag on slow networks
        inventory.putAEStackInSlot(this.getSlotIndex(), currentStack);

        NetworkHandler.instance
                .sendToServer(new PacketVirtualSlot(this.getStorageName(), this.getSlotIndex(), currentStack));
    }

    private ItemStack getTargetStack() {
        ItemStack is = Minecraft.getMinecraft().thePlayer.inventory.getItemStack();
        if (is == null && IntegrationRegistry.INSTANCE.isEnabled(IntegrationType.NEI)) {
            is = ItemPanels.bookmarkPanel.draggedStack;
            if (is == null) is = ItemPanels.itemPanel.draggedStack;
        }

        return is != null ? is.copy() : null;
    }
}
