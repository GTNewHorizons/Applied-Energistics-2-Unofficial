package appeng.parts.automation;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartRenderHelper;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.IConfigManager;
import appeng.client.texture.CableBusTextures;
import appeng.core.sync.GuiBridge;
import appeng.helpers.IPrimaryGuiIconProvider;
import appeng.helpers.IPriorityHost;
import appeng.parts.PartBasicState;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.IIAEStackInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public abstract class PartBaseFormationPlane extends PartUpgradeable
        implements IPriorityHost, IPrimaryGuiIconProvider, IIAEStackInventory {

    protected int priority = 0;
    protected final IAEStackInventory Config = new IAEStackInventory(this, 63);
    protected boolean wasActive = false;
    protected boolean blocked = false;

    public PartBaseFormationPlane(ItemStack is) {
        super(is);
    }

    @Override
    protected int getUpgradeSlots() {
        return 5;
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.Config.readFromNBT(data, "config");
        this.priority = data.getInteger("priority");
        this.updateHandler();
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.Config.writeToNBT(data, "config");
        data.setInteger("priority", this.priority);
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        this.updateHandler();
        this.getHost().markForSave();
    }

    @Override
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc,
            final ItemStack removedStack, final ItemStack newStack) {
        super.onChangeInventory(inv, slot, mc, removedStack, newStack);

        if (inv == this.Config) {
            this.updateHandler();
        }
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void setPriority(final int newValue) {
        this.priority = newValue;
        this.getHost().markForSave();
        this.updateHandler();
    }

    @Override
    public void upgradesChanged() {
        this.updateHandler();
    }

    @Override
    @MENetworkEventSubscribe
    public void powerRender(final MENetworkPowerStatusChange c) {
        final boolean currentActive = this.getProxy().isActive();
        if (this.wasActive != currentActive) {
            this.wasActive = currentActive;
            this.updateHandler();
            this.getHost().markForUpdate();
        }
    }

    @MENetworkEventSubscribe
    public void updateChannels(final MENetworkChannelsChanged changedChannels) {
        final boolean currentActive = this.getProxy().isActive();
        if (this.wasActive != currentActive) {
            this.wasActive = currentActive;
            this.updateHandler();
            this.getHost().markForUpdate();
        }
    }

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        int minX = 1;
        int minY = 1;
        int maxX = 15;
        int maxY = 15;

        final IPartHost host = this.getHost();
        if (host != null) {
            final TileEntity te = host.getTile();

            final int x = te.xCoord;
            final int y = te.yCoord;
            final int z = te.zCoord;

            final ForgeDirection e = bch.getWorldX();
            final ForgeDirection u = bch.getWorldY();

            if (this.isTransitionPlane(
                    te.getWorldObj().getTileEntity(x - e.offsetX, y - e.offsetY, z - e.offsetZ),
                    this.getSide())) {
                minX = 0;
            }

            if (this.isTransitionPlane(
                    te.getWorldObj().getTileEntity(x + e.offsetX, y + e.offsetY, z + e.offsetZ),
                    this.getSide())) {
                maxX = 16;
            }

            if (this.isTransitionPlane(
                    te.getWorldObj().getTileEntity(x - u.offsetX, y - u.offsetY, z - u.offsetZ),
                    this.getSide())) {
                minY = 0;
            }

            if (this.isTransitionPlane(
                    te.getWorldObj().getTileEntity(x + u.offsetX, y + u.offsetY, z + u.offsetZ),
                    this.getSide())) {
                maxY = 16;
            }
        }

        bch.addBox(5, 5, 14, 11, 11, 15);
        bch.addBox(minX, minY, 15, maxX, maxY, 16);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderInventory(final IPartRenderHelper rh, final RenderBlocks renderer) {
        rh.setTexture(
                CableBusTextures.PartPlaneSides.getIcon(),
                CableBusTextures.PartPlaneSides.getIcon(),
                CableBusTextures.PartTransitionPlaneBack.getIcon(),
                this.getItemStack().getIconIndex(),
                CableBusTextures.PartPlaneSides.getIcon(),
                CableBusTextures.PartPlaneSides.getIcon());

        rh.setBounds(1, 1, 15, 15, 15, 16);
        rh.renderInventoryBox(renderer);

        rh.setBounds(5, 5, 14, 11, 11, 15);
        rh.renderInventoryBox(renderer);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderStatic(final int x, final int y, final int z, final IPartRenderHelper rh,
            final RenderBlocks renderer) {
        int minX = 1;

        final ForgeDirection e = rh.getWorldX();
        final ForgeDirection u = rh.getWorldY();

        final TileEntity te = this.getHost().getTile();

        if (this.isTransitionPlane(
                te.getWorldObj().getTileEntity(x - e.offsetX, y - e.offsetY, z - e.offsetZ),
                this.getSide())) {
            minX = 0;
        }

        int maxX = 15;
        if (this.isTransitionPlane(
                te.getWorldObj().getTileEntity(x + e.offsetX, y + e.offsetY, z + e.offsetZ),
                this.getSide())) {
            maxX = 16;
        }

        int minY = 1;
        if (this.isTransitionPlane(
                te.getWorldObj().getTileEntity(x - u.offsetX, y - u.offsetY, z - u.offsetZ),
                this.getSide())) {
            minY = 0;
        }

        int maxY = 15;
        if (this.isTransitionPlane(
                te.getWorldObj().getTileEntity(x + u.offsetX, y + u.offsetY, z + u.offsetZ),
                this.getSide())) {
            maxY = 16;
        }

        final boolean isActive = (this.getClientFlags() & (PartBasicState.POWERED_FLAG | PartBasicState.CHANNEL_FLAG))
                == (PartBasicState.POWERED_FLAG | PartBasicState.CHANNEL_FLAG);

        this.setRenderCache(rh.useSimplifiedRendering(x, y, z, this, this.getRenderCache()));
        rh.setTexture(
                CableBusTextures.PartPlaneSides.getIcon(),
                CableBusTextures.PartPlaneSides.getIcon(),
                CableBusTextures.PartTransitionPlaneBack.getIcon(),
                isActive ? this.getActiveIcon() : this.getItemStack().getIconIndex(),
                CableBusTextures.PartPlaneSides.getIcon(),
                CableBusTextures.PartPlaneSides.getIcon());

        rh.setBounds(minX, minY, 15, maxX, maxY, 16);
        rh.renderBlock(x, y, z, renderer);

        rh.setTexture(
                CableBusTextures.PartMonitorSidesStatus.getIcon(),
                CableBusTextures.PartMonitorSidesStatus.getIcon(),
                CableBusTextures.PartTransitionPlaneBack.getIcon(),
                isActive ? this.getActiveIcon() : this.getItemStack().getIconIndex(),
                CableBusTextures.PartMonitorSidesStatus.getIcon(),
                CableBusTextures.PartMonitorSidesStatus.getIcon());

        rh.setBounds(5, 5, 14, 11, 11, 15);
        rh.renderBlock(x, y, z, renderer);

        this.renderLights(x, y, z, rh, renderer);
    }

    @Override
    public int cableConnectionRenderTo() {
        return 1;
    }

    @Override
    public boolean onPartActivate(final EntityPlayer player, final Vec3 pos) {
        if (!player.isSneaking()) {
            if (Platform.isClient()) {
                return true;
            }

            Platform.openGUI(player, this.getHost().getTile(), this.getSide(), GuiBridge.GUI_FORMATION_PLANE);
            return true;
        }

        return false;
    }

    @Override
    public void onNeighborChanged() {
        final TileEntity te = this.getHost().getTile();
        final World w = te.getWorldObj();
        final ForgeDirection side = this.getSide();

        final int x = te.xCoord + side.offsetX;
        final int y = te.yCoord + side.offsetY;
        final int z = te.zCoord + side.offsetZ;

        this.blocked = !w.getBlock(x, y, z).isReplaceable(w, x, y, z);
    }

    protected boolean isTransitionPlane(final TileEntity blockTileEntity, final ForgeDirection side) {
        if (blockTileEntity instanceof IPartHost) {
            final IPart p = ((IPartHost) blockTileEntity).getPart(side);
            return p instanceof PartBaseFormationPlane;
        }
        return false;
    }

    @Override
    public ItemStack getPrimaryGuiIcon() {
        return this.getItemStack();
    }

    @Override
    public void saveAEStackInv() {
        this.updateHandler();
        this.saveChanges();
    }

    @Override
    public IAEStackInventory getAEInventoryByName(StorageName name) {
        if (name == StorageName.CONFIG) {
            return this.Config;
        }
        return null;
    }

    public abstract IAEStackType<?> getStackType();

    public abstract boolean supportItemDrop();

    public abstract boolean supportFuzzy();

    protected void updateHandler() {}

    public abstract IIcon getActiveIcon();
}
