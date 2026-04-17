package appeng.client.gui.slots;

import static appeng.server.ServerHelper.CONTAINER_INTERACTION_KEY;
import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IDisplayRepo;
import appeng.core.AEConfig;
import appeng.core.localization.ButtonToolTips;
import it.unimi.dsi.fastutil.objects.ObjectLongPair;

public class VirtualMEMonitorableSlot extends VirtualMESlot {

    @FunctionalInterface
    public interface TypeFilterChecker {

        boolean check(IAEStackType<?> type);
    }

    protected final IDisplayRepo repo;
    protected final TypeFilterChecker typeFilterChecker;

    public VirtualMEMonitorableSlot(int x, int y, IDisplayRepo repo, int slotIndex, TypeFilterChecker checker) {
        super(x, y, slotIndex);
        this.repo = repo;
        this.typeFilterChecker = checker;
        this.showAmountAlways = true;
        this.showCraftableText = true;
        this.showCraftableIcon = true;
    }

    @Override
    public @Nullable IAEStack<?> getAEStack() {
        return this.repo.getReferenceStack(this.slotIndex);
    }

    @Override
    protected void drawNEIOverlay() {}

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addTooltip(List<String> lines) {
        if (!AEConfig.instance.showContainerInteractionTooltips) {
            return;
        }

        ItemStack hand = Minecraft.getMinecraft().thePlayer.inventory.getItemStack();
        if (hand == null) return;

        boolean added = false;

        IAEStack<?> stackInSlot = this.getAEStack();
        if (stackInSlot != null) {
            IAEStackType type = stackInSlot.getStackType();
            if (type.isContainerItemForType(hand) && this.typeFilterChecker.check(type)) {
                ObjectLongPair<ItemStack> result = type.fillContainer(hand.copy(), stackInSlot);
                if (result.rightLong() > 0) {
                    lines.add(
                            ButtonToolTips.ExtractFromNetworkToContainer
                                    .getLocal(
                                            this.typeFilterChecker.check(ITEM_STACK_TYPE)
                                                    ? Keyboard.getKeyName(CONTAINER_INTERACTION_KEY.getKeyCode())
                                                            + " + "
                                                    : "",
                                            stackInSlot.getDisplayName()));
                    added = true;
                }
            }
        }

        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            if (type.isContainerItemForType(hand) && this.typeFilterChecker.check(type)) {
                IAEStack<?> stack = type.getStackFromContainerItem(hand);
                if (stack != null && stack.getStackSize() > 0) {
                    lines.add(
                            ButtonToolTips.InsertFromContainerToNetwork
                                    .getLocal(
                                            this.typeFilterChecker.check(ITEM_STACK_TYPE)
                                                    ? Keyboard.getKeyName(CONTAINER_INTERACTION_KEY.getKeyCode())
                                                            + " + "
                                                    : "",
                                            stack.getDisplayName()));
                    added = true;
                }

                break;
            }
        }

        if (added) {
            lines.add(ButtonToolTips.HoldShiftProcessStack.getLocal());
        }
    }
}
