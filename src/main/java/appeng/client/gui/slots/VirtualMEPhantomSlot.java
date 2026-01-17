package appeng.client.gui.slots;

import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import java.util.Collection;
import java.util.Collections;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketVirtualSlot;
import appeng.integration.IntegrationRegistry;
import appeng.integration.IntegrationType;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.item.AEItemStack;
import codechicken.nei.ItemPanels;

public class VirtualMEPhantomSlot extends VirtualMESlot {

    private final IAEStackInventory inventory;
    private ItemStack shiftClickStack = null;

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

    public void handleMouseClicked(Collection<IAEStackType<?>> acceptTypes, boolean isExtraAction) {
        handleMouseClicked(acceptTypes, isExtraAction, 0);
    }

    // try nei dragNDrop make frind with regular interaction, unfinished
    public void handleMouseClicked(Collection<IAEStackType<?>> acceptTypes, boolean isExtraAction, int mouseButton) {
        IAEStack<?> currentStack = this.getAEStack();
        final ItemStack hand = getTargetStack();

        if (hand != null && !this.showAmount) {
            hand.stackSize = 1;
        }

        // need always convert display fluid stack from nei or nothing.
        if (hand != null) {
            for (IAEStackType<?> type : acceptTypes) {
                IAEStack<?> converted = type.convertStackFromItem(hand);
                if (converted != null) {
                    currentStack = converted;
                    acceptTypes = Collections.EMPTY_LIST;
                    isExtraAction = false;
                    break;
                }
            }
        }

        final boolean acceptItem = acceptTypes.contains(ITEM_STACK_TYPE);
        boolean acceptExtra = false;
        for (IAEStackType<?> type : acceptTypes) {
            if (type != ITEM_STACK_TYPE) {
                acceptExtra = true;
                break;
            }
        }

        switch (mouseButton) {
            case 0 -> { // left click
                if (hand != null) {
                    if (acceptExtra && (!acceptItem || isExtraAction)) {
                        for (IAEStackType<?> type : acceptTypes) {
                            IAEStack<?> stackFromContainer = type.getStackFromContainerItem(hand);
                            if (stackFromContainer != null) {
                                currentStack = stackFromContainer;
                                break;
                            }
                        }
                    } else if (acceptItem) {
                        currentStack = AEItemStack.create(hand);
                    }
                } else {
                    currentStack = null;
                }
            }
            case 1 -> { // right click
                if (hand != null) {
                    hand.stackSize = 1;

                    IAEStack<?> stackFromContainer = null;
                    for (IAEStackType<?> type : acceptTypes) {
                        stackFromContainer = type.getStackFromContainerItem(hand);
                        if (stackFromContainer != null) {
                            break;
                        }
                    }

                    if (acceptExtra && (!acceptItem || isExtraAction)) {
                        if (stackFromContainer != null) {
                            currentStack = stackFromContainer;
                        }
                    } else if (acceptItem) {
                        currentStack = AEItemStack.create(hand);
                    }

                    if (currentStack != null && stackFromContainer != null
                            && acceptTypes.contains(stackFromContainer.getStackType())
                            && currentStack.equals(this.getAEStack())) {
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
        if (this.shiftClickStack == null) {
            ItemStack is = Minecraft.getMinecraft().thePlayer.inventory.getItemStack();
            if (is == null && IntegrationRegistry.INSTANCE.isEnabled(IntegrationType.NEI)) {
                is = ItemPanels.bookmarkPanel.draggedStack;
                if (is == null) is = ItemPanels.itemPanel.draggedStack;
            }

            return is != null ? is.copy() : null;
        }

        final ItemStack is = this.shiftClickStack;
        this.shiftClickStack = null;
        return is;
    }

    public void setShiftClickStack(ItemStack shiftClickStack) {
        this.shiftClickStack = shiftClickStack;
    }
}
