/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.parts.reporting;

import static appeng.server.ServerHelper.CONTAINER_INTERACTION_KEY;
import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import appeng.api.implementations.parts.IPartStorageMonitor;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IStackWatcher;
import appeng.api.networking.storage.IStackWatcherHost;
import appeng.api.parts.IPartRenderHelper;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.core.AELog;
import appeng.core.localization.PlayerMessages;
import appeng.helpers.Reflected;
import appeng.hooks.TickHandler;
import appeng.me.GridAccessException;
import appeng.util.IWideReadableNumberConverter;
import appeng.util.Platform;
import appeng.util.ReadableNumberConverter;
import appeng.util.item.AEItemStack;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * A basic subclass for any item monitor like display with an item icon and an amount.
 * <p>
 * It can also be used to extract items from somewhere and spawned into the world.
 *
 * @author AlgorithmX2
 * @author thatsIch
 * @author yueh
 * @version rv3
 * @since rv3
 */
public abstract class AbstractPartMonitor extends AbstractPartDisplay
        implements IPartStorageMonitor, IStackWatcherHost {

    private static final IWideReadableNumberConverter NUMBER_CONVERTER = ReadableNumberConverter.INSTANCE;
    private IAEStack<?> configuredItem;
    private String lastHumanReadableText;
    private boolean isLocked;
    private IStackWatcher myWatcher;

    @SideOnly(Side.CLIENT)
    private boolean updateList;

    @SideOnly(Side.CLIENT)
    private Integer dspList;

    @Reflected
    public AbstractPartMonitor(final ItemStack is) {
        super(is);
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);

        this.isLocked = data.getBoolean("isLocked");

        if (data.hasKey("configuredItem")) {
            this.configuredItem = Platform.readStackNBT(data.getCompoundTag("configuredItem"));
        } else {
            this.configuredItem = null;
        }
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);

        data.setBoolean("isLocked", this.isLocked);

        if (this.configuredItem != null) {
            data.setTag("configuredItem", this.configuredItem.toNBTGeneric());
        }
    }

    @Override
    public void writeToStream(final ByteBuf data) throws IOException {
        super.writeToStream(data);

        data.writeBoolean(this.isLocked);
        data.writeBoolean(this.configuredItem != null);
        if (this.configuredItem != null) {
            Platform.writeStackByte(this.configuredItem, data);
        }
    }

    @Override
    public boolean readFromStream(final ByteBuf data) throws IOException {
        boolean needRedraw = super.readFromStream(data);

        final boolean isLocked = data.readBoolean();
        needRedraw |= this.isLocked != isLocked;

        this.isLocked = isLocked;

        final boolean val = data.readBoolean();
        if (val) {
            this.configuredItem = IAEStack.fromPacketGeneric(data);
        } else {
            this.configuredItem = null;
        }

        this.updateList = true;

        return needRedraw;
    }

    @Override
    public boolean onPartActivate(final EntityPlayer player, final Vec3 pos) {
        return this.onActivate(player, false);
    }

    @Override
    public boolean onPartShiftActivate(EntityPlayer player, Vec3 pos) {
        return this.onActivate(player, true);
    }

    private boolean onActivate(EntityPlayer player, boolean isShiftDown) {
        if (player.worldObj.isRemote) {
            return true;
        }

        if (!this.getProxy().isActive()) {
            return false;
        }

        if (!Platform.hasPermissions(this.getLocation(), player)) {
            return false;
        }

        final TileEntity te = this.getTile();
        final ItemStack hand = player.getCurrentEquippedItem();

        if (Platform.isWrench(player, hand, te.xCoord, te.yCoord, te.zCoord)) {
            this.isLocked = !this.isLocked;
            player.addChatMessage((this.isLocked ? PlayerMessages.isNowLocked : PlayerMessages.isNowUnlocked).toChat());
            this.getHost().markForUpdate();
        } else if (!this.isLocked) {
            if (hand == null) {
                this.configuredItem = null;
            } else if (!CONTAINER_INTERACTION_KEY.isKeyDown(player)) {
                this.configuredItem = AEItemStack.create(hand);
            } else {
                this.configuredItem = null;
                for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
                    if (type == ITEM_STACK_TYPE) continue;
                    if (type.isContainerItemForType(hand)) {
                        this.configuredItem = type.getStackFromContainerItem(hand);
                    } else {
                        this.configuredItem = type.convertStackFromItem(hand);
                    }

                    if (this.configuredItem != null) break;
                }
            }

            this.configureWatchers();
            this.getHost().markForUpdate();
        } else {
            this.onLockedInteraction(player, isShiftDown);
        }

        return true;
    }

    protected void onLockedInteraction(final EntityPlayer player, boolean isShiftDown) {}

    // update the system...
    private void configureWatchers() {
        if (this.myWatcher != null) {
            this.myWatcher.clear();
        }

        try {
            if (this.configuredItem != null) {
                if (this.myWatcher != null) {
                    this.myWatcher.add(this.configuredItem);
                }

                this.updateReportingValue(
                        this.getProxy().getStorage().getMEMonitor(this.configuredItem.getStackType()));
            }
        } catch (final GridAccessException e) {
            AELog.debug(e);
        }
    }

    @Override
    public void updateWatcher(final IStackWatcher newWatcher) {
        this.myWatcher = newWatcher;
        this.configureWatchers();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void updateReportingValue(final IMEMonitor itemInventory) {
        if (this.configuredItem != null && itemInventory != null
                && itemInventory.getStackType() == this.configuredItem.getStackType()) {
            final IAEStack<?> result = itemInventory.getStorageList().findPrecise(this.configuredItem);
            this.updateStackSize(result);
        }
    }

    @Override
    public void onStackChange(final IItemList o, final IAEStack fullStack, final IAEStack diffStack,
            final BaseActionSource src, final StorageChannel chan) {
        if (this.configuredItem != null) {
            this.updateStackSize(fullStack);
        }
    }

    private void updateStackSize(@Nullable IAEStack<?> newStack) {
        if (newStack == null) {
            this.configuredItem.setStackSize(0);
        } else {
            this.configuredItem.setStackSize(newStack.getStackSize());
        }

        final long stackSize = this.configuredItem.getStackSize();
        final String humanReadableText = NUMBER_CONVERTER.toWideReadableForm(stackSize);

        if (!humanReadableText.equals(this.lastHumanReadableText)) {
            this.lastHumanReadableText = humanReadableText;
            this.getHost().markForUpdate();
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected void finalize() throws Throwable {
        super.finalize();

        if (this.dspList != null) {
            TickHandler.INSTANCE.scheduleCallListDelete(dspList);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderDynamic(final double x, final double y, final double z, final IPartRenderHelper rh,
            final RenderBlocks renderer) {
        if (this.dspList == null) {
            this.dspList = GLAllocation.generateDisplayLists(1);
        }

        final Tessellator tess = Tessellator.instance;

        if ((this.getClientFlags() & (PartPanel.POWERED_FLAG | PartPanel.CHANNEL_FLAG))
                != (PartPanel.POWERED_FLAG | PartPanel.CHANNEL_FLAG)) {
            return;
        }

        final IAEStack<?> aes = this.getDisplayed();

        if (aes != null) {
            GL11.glPushMatrix();
            GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);

            if (this.updateList) {
                this.updateList = false;
                GL11.glNewList(this.dspList, GL11.GL_COMPILE_AND_EXECUTE);
                this.tesrRenderScreen(tess, aes);
                GL11.glEndList();
            } else {
                GL11.glCallList(this.dspList);
            }

            GL11.glPopMatrix();
        }
    }

    @Override
    public boolean requireDynamicRender() {
        return true;
    }

    @Override
    public IAEStack<?> getDisplayed() {
        return this.configuredItem;
    }

    private void tesrRenderScreen(final Tessellator tess, final IAEStack<?> aes) {
        final ForgeDirection d = this.getSide();

        GL11.glTranslated(d.offsetX * 0.77, d.offsetY * 0.77, d.offsetZ * 0.77);

        switch (d) {
            case UP -> {
                GL11.glScalef(1.0f, -1.0f, 1.0f);
                GL11.glRotatef(90.0f, 1.0f, 0.0f, 0.0f);
                GL11.glRotatef(this.getSpin() * 90.0F, 0, 0, 1);
            }
            case DOWN -> {
                GL11.glScalef(1.0f, -1.0f, 1.0f);
                GL11.glRotatef(-90.0f, 1.0f, 0.0f, 0.0f);
                GL11.glRotatef(this.getSpin() * -90.0F, 0, 0, 1);
            }
            case EAST -> {
                GL11.glScalef(-1.0f, -1.0f, -1.0f);
                GL11.glRotatef(-90.0f, 0.0f, 1.0f, 0.0f);
            }
            case WEST -> {
                GL11.glScalef(-1.0f, -1.0f, -1.0f);
                GL11.glRotatef(90.0f, 0.0f, 1.0f, 0.0f);
            }
            case NORTH -> GL11.glScalef(-1.0f, -1.0f, -1.0f);
            case SOUTH -> {
                GL11.glScalef(-1.0f, -1.0f, -1.0f);
                GL11.glRotatef(180.0f, 0.0f, 1.0f, 0.0f);
            }
            default -> {}
        }

        try {
            final int br = 16 << 20 | 16 << 4;
            final int var11 = br % 65536;
            final int var12 = br / 65536;
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, var11 * 0.8F, var12 * 0.8F);

            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            tess.setColorOpaque_F(1.0f, 1.0f, 1.0f);

            aes.drawOnBlockFace(this.getTile().getWorldObj());
        } catch (final Exception e) {
            AELog.debug(e);
        } finally {
            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        }

        this.tesrRenderItemNumber(aes);
    }

    public void tesrRenderItemNumber(final IAEStack<?> aes) {
        GL11.glTranslatef(0.0f, 0.14f, -0.24f);
        GL11.glScalef(1.0f / 62.0f, 1.0f / 62.0f, 1.0f / 62.0f);

        final long stackSize = aes.getStackSize();
        final String renderedStackSize = NUMBER_CONVERTER.toWideReadableForm(stackSize);

        final FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        final int width = fr.getStringWidth(renderedStackSize);
        GL11.glTranslatef(-0.5f * width, 0.0f, -1.0f);
        fr.drawString(renderedStackSize, 0, 0, 0);
    }

    @Override
    public boolean isLocked() {
        return this.isLocked;
    }

    @Override
    public boolean showNetworkInfo(final MovingObjectPosition where) {
        return false;
    }
}
