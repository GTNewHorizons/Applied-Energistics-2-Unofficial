package appeng.items.contents;

import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.AEApi;
import appeng.api.features.ILocatable;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkBootingStatusChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.parts.IInterfaceTerminal;
import appeng.api.util.AECableType;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.helpers.ICustomButtonDataObject;
import appeng.helpers.ICustomButtonProvider;
import appeng.helpers.ICustomButtonSource;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class WirelessInterfaceTerminalGuiObject
        implements IInterfaceTerminal, IGuiItemObject, ICustomButtonProvider, IInventorySlotAware {

    private boolean needsUpdate;
    private final IGridNode node;
    private final ItemStack is;
    private final int slotIndex;

    public WirelessInterfaceTerminalGuiObject(final IWirelessTermHandler wh, final ItemStack is, final int slotIndex) {
        this.is = is;
        this.slotIndex = slotIndex;

        String encryptionKey = wh.getEncryptionKey(is);
        ILocatable obj = null;

        try {
            final long encKey = Long.parseLong(encryptionKey);
            obj = AEApi.instance().registries().locatable().getLocatableBy(encKey);
        } catch (final NumberFormatException err) {
            // :P
        }

        if (obj instanceof IGridHost gh) {
            this.node = gh.getGridNode(ForgeDirection.UNKNOWN);
        } else {
            this.node = null;
        }

        if (getItemStack().getItem() instanceof ICustomButtonSource icbs) {
            customButtonDataObject = icbs.getCustomDataObject(this);
            customButtonDataObject.readData(getItemStack().getTagCompound());
        }
    }

    @Override
    public IGridNode getActionableNode() {
        return this.node;
    }

    @Override
    public IGridNode getGridNode(ForgeDirection dir) {
        return this.node;
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection dir) {
        return null;
    }

    @Override
    public void securityBreak() {}

    @Override
    public boolean needsUpdate() {
        boolean ret = needsUpdate;

        needsUpdate = false;
        return ret;
    }

    @MENetworkEventSubscribe
    public void onNetworkBootingChanged(MENetworkBootingStatusChange event) {
        if (!event.isBooting) {
            this.needsUpdate = true;
        }
    }

    @Override
    public ItemStack getItemStack() {
        return this.is;
    }

    private ICustomButtonDataObject customButtonDataObject;

    @Override
    public void writeCustomButtonData() {
        this.customButtonDataObject.writeData(this.getItemStack().getTagCompound());
    }

    @Override
    public void readCustomButtonData() {
        this.customButtonDataObject.readData(this.getItemStack().getTagCompound());
    }

    @Override
    public void initCustomButtons(int guiLeft, int guiTop, int xSize, int ySize, int xOffset, int yOffset,
            List<GuiButton> buttonList) {
        if (customButtonDataObject != null)
            customButtonDataObject.initCustomButtons(guiLeft, guiTop, xSize, ySize, xOffset, yOffset, buttonList);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean actionPerformedCustomButtons(final GuiButton btn) {
        return customButtonDataObject != null && customButtonDataObject.actionPerformedCustomButtons(btn);
    }

    @Override
    public ICustomButtonDataObject getDataObject() {
        return customButtonDataObject;
    }

    @Override
    public void setDataObject(ICustomButtonDataObject dataObject) {
        customButtonDataObject = dataObject;
    }

    @Override
    public int getInventorySlot() {
        return this.slotIndex;
    }
}
