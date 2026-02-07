package appeng.me.storage;

import static appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import org.jetbrains.annotations.NotNull;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.StorageFilter;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageBusMonitor;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.core.AELog;
import appeng.util.item.AEFluidStack;

public class MEMonitorIFluidHandler implements IStorageBusMonitor<IAEFluidStack> {

    private static boolean WrongFluidRemovedWarnIssued = false;

    private final IFluidHandler handler;
    private final ForgeDirection side;
    private IItemList<IAEFluidStack> cache = AEApi.instance().storage().createFluidList();
    private final HashMap<IMEMonitorHandlerReceiver, Object> listeners = new HashMap<>();
    private BaseActionSource mySource;
    private StorageFilter mode;
    private boolean init = false;

    public MEMonitorIFluidHandler(IFluidHandler handler, ForgeDirection side) {
        this.mode = StorageFilter.EXTRACTABLE_ONLY;
        this.handler = handler;
        this.side = side;
    }

    public MEMonitorIFluidHandler(IFluidHandler handler) {
        this.mode = StorageFilter.EXTRACTABLE_ONLY;
        this.handler = handler;
        this.side = ForgeDirection.UNKNOWN;
    }

    public void addListener(IMEMonitorHandlerReceiver l, Object verificationToken) {
        this.listeners.put(l, verificationToken);
    }

    public void removeListener(IMEMonitorHandlerReceiver l) {
        this.listeners.remove(l);
    }

    public IAEFluidStack injectItems(IAEFluidStack input, Actionable type, BaseActionSource src) {
        if (!this.handler.canFill(this.side, input.getFluid())) {
            return input;
        }

        int filled = this.handler.fill(this.side, input.getFluidStack(), type == Actionable.MODULATE);

        if (type == Actionable.MODULATE) {
            this.onTick();
        }

        if ((long) filled == input.getStackSize()) {
            return null;
        }

        IAEFluidStack o = input.copy();
        o.setStackSize(input.getStackSize() - (long) filled);
        return o;
    }

    public IAEFluidStack extractItems(IAEFluidStack request, Actionable type, BaseActionSource src) {
        if (!this.handler.canDrain(this.side, request.getFluid())) {
            return null;
        }
        FluidStack removed = this.handler.drain(this.side, request.getFluidStack(), type == Actionable.MODULATE);
        if (removed != null && removed.amount != 0) {
            // perform a one time log if the removed fluid isn't what's reported since this will create a dupe exploit
            if (!WrongFluidRemovedWarnIssued && !removed.isFluidEqual(request.getFluidStack())) {
                WrongFluidRemovedWarnIssued = true;
                String issuesUrl = "https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/issues/new";
                String msg = String.format(
                        "[AE2FC] MEMonitorIFluidHandler.extractItems got the wrong fluids while extracting from %s. expected %s got %s. Please report this message if seen: ",
                        this.handler.getClass().getSimpleName(),
                        request.getFluidStack().getUnlocalizedName(),
                        removed.getUnlocalizedName());
                // elevate log to error if chat msg is removed to make it easier to find.
                AELog.warn(msg + issuesUrl);

                ChatComponentText chatTxt = new ChatComponentText(msg);
                chatTxt.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED).setBold(true));

                ChatComponentText chatUrl = new ChatComponentText(issuesUrl);
                chatUrl.setChatStyle(
                        new ChatStyle().setUnderlined(true)
                                .setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, issuesUrl))
                                .setChatHoverEvent(
                                        new HoverEvent(
                                                HoverEvent.Action.SHOW_TEXT,
                                                new ChatComponentText("Click to open link"))));

                ChatComponentText chat = new ChatComponentText("");
                chat.appendSibling(chatTxt);
                chat.appendSibling(chatUrl);

                MinecraftServer.getServer().getConfigurationManager().sendChatMsg(chat);
            }
            IAEFluidStack o = request.copy();
            o.setStackSize(removed.amount);
            if (type == Actionable.MODULATE) {
                IAEFluidStack cachedStack = this.cache.findPrecise(request);
                if (cachedStack != null) {
                    cachedStack.decStackSize(o.getStackSize());
                    this.postDifference(Collections.singletonList(o.copy().setStackSize(-o.getStackSize())));
                }
            }
            return o;
        } else {
            return null;
        }
    }

    @Override
    public StorageChannel getChannel() {
        return StorageChannel.FLUIDS;
    }

    @Override
    public @NotNull IAEStackType<?> getStackType() {
        return FLUID_STACK_TYPE;
    }

    // *Decompiled Stuff*//

    @Override
    public TickRateModulation onTick() {
        FluidTankInfo[] tankProperties = this.handler.getTankInfo(this.side);
        IItemList<IAEFluidStack> currentlyOnStorage = AEApi.instance().storage().createFluidList();

        if (tankProperties != null) {
            for (FluidTankInfo tankProperty : tankProperties) {
                if (this.mode != StorageFilter.EXTRACTABLE_ONLY || this.handler.drain(this.side, 1, false) != null) {
                    currentlyOnStorage.add(AEFluidStack.create(tankProperty.fluid));
                }
            }
        }

        // make diff between cache and new contents
        IItemList<IAEStack<?>> changes = AEApi.instance().storage().createAEStackList();
        // using non-enhanced for to prevent concurrency errors
        for (Iterator<IAEFluidStack> iter = this.cache.iterator(); iter.hasNext();) {
            IAEFluidStack copy = iter.next().copy();
            copy.setStackSize(-copy.getStackSize());
            changes.add(copy);
        }
        for (IAEFluidStack is : currentlyOnStorage) {
            changes.add(is);
        }
        // update cache as soon as possible
        this.cache = currentlyOnStorage;
        // remove unchanged values
        for (Iterator<IAEStack<?>> iter = changes.iterator(); iter.hasNext();) {
            if (iter.next().getStackSize() == 0L) {
                iter.remove();
            }
        }

        if (!changes.isEmpty()) {
            this.postDifference(changes);
            return TickRateModulation.URGENT;
        }

        return TickRateModulation.SLOWER;
    }

    private void postDifference(Iterable<IAEStack<?>> a) {
        if (a != null) {
            Iterator<Map.Entry<IMEMonitorHandlerReceiver, Object>> i = this.listeners.entrySet().iterator();

            while (i.hasNext()) {
                Map.Entry<IMEMonitorHandlerReceiver, Object> l = i.next();
                IMEMonitorHandlerReceiver key = l.getKey();
                if (key.isValid(l.getValue())) {
                    key.postChange(this, a, this.getActionSource());
                } else {
                    i.remove();
                }
            }
        }
    }

    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    public boolean isPrioritized(IAEFluidStack input) {
        return false;
    }

    public boolean canAccept(IAEFluidStack input) {
        return true;
    }

    public int getPriority() {
        return 0;
    }

    public int getSlot() {
        return 0;
    }

    public boolean validForPass(int i) {
        return true;
    }

    public IItemList<IAEFluidStack> getAvailableItems(IItemList<IAEFluidStack> out, int iteration) {
        if (!init) {
            this.onTick();
            init = true;
        }
        for (IAEFluidStack fs : this.cache) {
            out.addStorage(fs);
        }

        return out;
    }

    @Override
    public IAEFluidStack getAvailableItem(@Nonnull IAEFluidStack request, int iteration) {
        if (!init) {
            this.onTick();
            init = true;
        }
        return this.cache.findPrecise(request);
    }

    public IItemList<IAEFluidStack> getStorageList() {
        return this.cache;
    }

    @Override
    public void setMode(StorageFilter mode) {
        this.mode = mode;
    }

    private BaseActionSource getActionSource() {
        return this.mySource;
    }

    @Override
    public void setActionSource(BaseActionSource mySource) {
        this.mySource = mySource;
    }
}
