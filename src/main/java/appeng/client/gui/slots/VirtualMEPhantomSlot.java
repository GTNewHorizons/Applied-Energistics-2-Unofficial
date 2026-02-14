package appeng.client.gui.slots;

import static appeng.server.ServerHelper.CONTAINER_INTERACTION_KEY;
import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import appeng.api.storage.StorageName;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.core.AEConfig;
import appeng.core.localization.ButtonToolTips;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketVirtualSlot;
import appeng.integration.IntegrationRegistry;
import appeng.integration.IntegrationType;
import appeng.integration.modules.NEI;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.item.AEItemStack;

public class VirtualMEPhantomSlot extends VirtualMESlot {

    @FunctionalInterface
    public interface TypeAcceptPredicate {

        boolean test(VirtualMEPhantomSlot slot, IAEStackType<?> type, int mouseButton);
    }

    private final IAEStackInventory inventory;
    private final TypeAcceptPredicate acceptType;

    public VirtualMEPhantomSlot(int x, int y, IAEStackInventory inventory, int slotIndex,
            TypeAcceptPredicate acceptType) {
        super(x, y, slotIndex);
        this.inventory = inventory;
        this.showAmount = false;
        this.acceptType = acceptType;
    }

    @Nullable
    @Override
    public IAEStack<?> getAEStack() {
        return this.inventory.getAEStackInSlot(this.getSlotIndex());
    }

    public StorageName getStorageName() {
        return this.inventory.getStorageName();
    }

    /**
     * @param itemStack holding item stack
     */
    public void handleMouseClicked(@Nullable ItemStack itemStack, boolean isExtraAction, int mouseButton) {
        IAEStack<?> currentStack = this.getAEStack();
        final ItemStack hand = itemStack != null ? itemStack.copy() : null;

        if (hand != null && !this.showAmount) {
            hand.stackSize = 1;
        }

        final List<IAEStackType<?>> acceptTypes = new ArrayList<>();
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            if (this.acceptType.test(this, type, mouseButton)) {
                acceptTypes.add(type);
            }
        }

        // need always convert display fluid stack from nei or nothing.
        if (hand != null) {
            for (IAEStackType<?> type : acceptTypes) {
                IAEStack<?> converted = type.convertStackFromItem(hand);
                if (converted != null) {
                    currentStack = converted;
                    acceptTypes.clear();
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

                    IAEStack<?> stackForHand = null;
                    if (acceptExtra && (!acceptItem || isExtraAction)) {
                        if (stackFromContainer != null) {
                            stackForHand = stackFromContainer;
                        }
                    } else if (acceptItem) {
                        stackForHand = AEItemStack.create(hand);
                    }

                    if (stackForHand != null && this.showAmount
                            && acceptTypes.contains(stackForHand.getStackType())
                            && stackForHand.equals(currentStack)) {
                        currentStack.decStackSize(-1);
                    } else {
                        currentStack = stackForHand;
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

    @Override
    public void addTooltip(List<String> lines) {
        if (!AEConfig.instance.showContainerInteractionTooltips) {
            return;
        }

        final ItemStack phantom = IntegrationRegistry.INSTANCE.isEnabled(IntegrationType.NEI)
                ? NEI.instance.getDraggingPhantomItem()
                : null;
        final ItemStack hand = phantom != null ? phantom : Minecraft.getMinecraft().thePlayer.inventory.getItemStack();
        if (hand == null) return;

        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            if (this.acceptType.test(this, type, 0) && type.isContainerItemForType(hand)) {
                IAEStack<?> stack = type.getStackFromContainerItem(hand);
                if (stack != null && stack.getStackSize() > 0) {
                    lines.add(
                            ButtonToolTips.RegisterContainerContent.getLocal(
                                    Keyboard.getKeyName(CONTAINER_INTERACTION_KEY.getKeyCode()),
                                    stack.getDisplayName()));
                    return;
                }
            }
        }
    }
}
