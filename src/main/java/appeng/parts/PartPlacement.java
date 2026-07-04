/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.parts;

import java.util.LinkedList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.Block.SoundType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action;
import net.minecraftforge.event.world.BlockEvent;

import org.jetbrains.annotations.Nullable;

import com.google.common.base.Optional;

import appeng.api.AEApi;
import appeng.api.definitions.IBlockDefinition;
import appeng.api.definitions.IItems;
import appeng.api.parts.IFacadePart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartItemStack;
import appeng.api.parts.SelectedPart;
import appeng.core.AELog;
import appeng.core.CommonHelper;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketClick;
import appeng.core.sync.packets.PacketPartInteraction;
import appeng.core.sync.packets.PacketPartPlacement;
import appeng.facade.IFacadeItem;
import appeng.integration.IntegrationRegistry;
import appeng.integration.IntegrationType;
import appeng.integration.abstraction.IBuildCraftTransport;
import appeng.integration.abstraction.IFMP;
import appeng.integration.abstraction.IImmibisMicroblocks;
import appeng.util.LookDirection;
import appeng.util.Platform;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class PartPlacement {

    private static float eyeHeight = 0.0f;
    // After we do a placement and cancel the event, we will also receive event for RIGHT_CLICK_AIR
    // Only used client side, do not use this server side
    private boolean placing = false;

    // Last 2 arguments are ignored
    public static boolean place(final ItemStack held, final int x, final int y, final int z, final int face,
            final EntityPlayer player, final World world, PlaceType pass, final int depth) {
        final LookDirection dir = Platform.getPlayerRay(player, getEyeOffset(player));
        final Block block = world.getBlock(x, y, z);
        final MovingObjectPosition mop = block.collisionRayTrace(world, x, y, z, dir.getA(), dir.getB());
        if (mop == null || mop.sideHit == -1) return false;
        mop.hitVec.xCoord -= mop.blockX;
        mop.hitVec.yCoord -= mop.blockY;
        mop.hitVec.zCoord -= mop.blockZ;
        return place(held, mop.blockX, mop.blockY, mop.blockZ, mop.sideHit, player, world, mop.hitVec);
    }

    private static boolean place(final ItemStack held, final int x, final int y, final int z, final int face,
            final EntityPlayer player, final World world, final Vec3 hitVec) {

        // analogous to onItemUseFirst

        ForgeDirection side = ForgeDirection.getOrientation(face);

        TileEntity tile = world.getTileEntity(x, y, z);
        IPartHost host = getExistingHost(tile);

        if (host != null) {
            if (Platform.isWrench(player, held, x, y, z) && player.isSneaking()) {
                if (wrenchLogic(player, world, x, y, z, host, selectPart(player, host, hitVec))) {
                    return true;
                }
            }
        }

        if (facadeLogic(held, player, side, host, world.isRemote)) return true;

        if (host == null) {
            host = getOrCreateHost(tile, player, face);
        }

        // analogous to onBlockActivated

        if (host != null) {
            final SelectedPart sPart = selectPart(player, host, hitVec);
            if (sPart != null && sPart.part != null) {
                if (player.isSneaking()) {
                    if (sPart.part.onShiftActivate(player, hitVec)) {
                        NetworkHandler.instance
                                .sendToServer(new PacketPartInteraction(x, y, z, sPart.side, true, false, hitVec));
                        return true;
                    }
                } else {
                    if (sPart.part.onActivate(player, hitVec)) {
                        NetworkHandler.instance
                                .sendToServer(new PacketPartInteraction(x, y, z, sPart.side, true, false, hitVec));
                        return true;
                    }
                }
            }
        }

        // analogous to onItemUse

        if (held == null || !(held.getItem() instanceof IPartItem)) {
            return false;
        }

        // Try to add the part to the target block
        if (host != null && tryPlace(held, player, world, x, y, z, side, host)) return true;

        // If that didn't work, we try to place on the face of the target block
        int tx = x + side.offsetX;
        int ty = y + side.offsetY;
        int tz = z + side.offsetZ;
        ForgeDirection opposite = side.getOpposite();

        TileEntity te = world.getTileEntity(tx, ty, tz);
        IPartHost targetHost = getOrCreateHost(te, player, opposite.ordinal());

        return tryPlace(held, player, world, tx, ty, tz, opposite, targetHost);
    }

    public static boolean wrenchLogic(EntityPlayer player, World world, int x, int y, int z, IPartHost host,
            SelectedPart sp) {
        final Block block = world.getBlock(x, y, z);
        if (host != null) {
            final List<ItemStack> is = new LinkedList<>();

            BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(
                    x,
                    y,
                    z,
                    world,
                    block,
                    world.getBlockMetadata(x, y, z),
                    player);
            MinecraftForge.EVENT_BUS.post(event);
            if (event.isCanceled()) {
                return false;
            }

            if (sp.part != null) {
                is.add(sp.part.getItemStack(PartItemStack.Wrench));
                sp.part.getDrops(is, true);
                host.removePart(sp.side, false);
            }

            if (sp.facade != null) {
                is.add(sp.facade.getItemStack());
                host.getFacadeContainer().removeFacade(host, sp.side);
                Platform.notifyBlocksOfNeighbors(world, x, y, z);
            }

            if (host.isEmpty() && !world.isRemote) {
                host.cleanup();
            }

            if (world.isRemote) {
                NetworkHandler.instance.sendToServer(
                        new PacketPartInteraction(
                                x,
                                y,
                                z,
                                sp.side,
                                sp.part != null,
                                true,
                                Vec3.createVectorHelper(0.5, 0.5, 0.5)));
            } else if (!is.isEmpty()) {
                Platform.spawnDrops(world, x, y, z, is);
            }
            return true;
        }
        return false;
    }

    public static boolean facadeLogic(ItemStack held, EntityPlayer player, ForgeDirection side, IPartHost host,
            boolean isClient) {
        if (held != null) {
            final IFacadePart fp = isFacade(held, side);
            if (fp != null && host != null) {
                if (host.getPart(ForgeDirection.UNKNOWN) == null) {
                    return false;
                }

                if (host.canAddPart(held, side)) {
                    if (host.getFacadeContainer().addFacade(fp)) {
                        host.markForUpdate();
                        decreaseHeldItem(held, player);
                        if (isClient) {
                            NetworkHandler.instance.sendToServer(new PacketPartPlacement(host, side, true));
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean tryPlace(ItemStack held, EntityPlayer player, World world, int x, int y, int z,
            ForgeDirection side, IPartHost host) {
        final IBlockDefinition multiPart = AEApi.instance().definitions().blocks().multiPart();
        if (!world.canMineBlock(player, x, y, z)) {
            return false;
        }

        BlockEvent.PlaceEvent event = new BlockEvent.PlaceEvent(
                BlockSnapshot.getBlockSnapshot(world, x, y, z),
                world.getBlock(x, y, z),
                player);
        MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            return false;
        }

        if (host == null) {
            // No host, try to replace the block with cable host
            final Optional<ItemBlock> maybeMultiPartItemBlock = multiPart.maybeItemBlock();
            if (!maybeMultiPartItemBlock.isPresent()) return false;
            if (!maybeMultiPartItemBlock.get().field_150939_a.canPlaceBlockAt(world, x, y, z)) return false;
            maybeMultiPartItemBlock.get().placeBlockAt(
                    new ItemStack(maybeMultiPartItemBlock.get()),
                    player,
                    world,
                    x,
                    y,
                    z,
                    side.ordinal(),
                    0.5f,
                    0.5f,
                    0.5f,
                    0);
            host = getExistingHost(world.getTileEntity(x, y, z));
            if (host == null) return false;
        }
        final ForgeDirection mySide = host.addPart(held, side, player);
        if (mySide != null) {
            for (final Block multiPartBlock : multiPart.maybeBlock().asSet()) {
                final SoundType ss = multiPartBlock.stepSound;

                world.playSoundEffect(
                        0.5 + x,
                        0.5 + y,
                        0.5 + z,
                        ss.func_150496_b(),
                        (ss.getVolume() + 1.0F) / 2.0F,
                        ss.getPitch() * 0.8F);
            }

            decreaseHeldItem(held, player);
            if (world.isRemote) {
                NetworkHandler.instance.sendToServer(new PacketPartPlacement(host, mySide, false));
            }
            return true;
        }
        return false;
    }

    private static void decreaseHeldItem(ItemStack held, EntityPlayer player) {
        if (!player.capabilities.isCreativeMode) {
            held.stackSize--;
            if (held.stackSize == 0) {
                player.inventory.mainInventory[player.inventory.currentItem] = null;
                MinecraftForge.EVENT_BUS.post(new PlayerDestroyItemEvent(player, held));
            }
        }
    }

    private static float getEyeOffset(final EntityPlayer p) {
        if (p.worldObj.isRemote) {
            return Platform.getEyeOffset(p);
        }

        return getEyeHeight();
    }

    private static SelectedPart selectPart(final EntityPlayer player, final IPartHost host, final Vec3 pos) {
        CommonHelper.proxy.updateRenderMode(player);
        final SelectedPart sp = host.selectPart(pos);
        CommonHelper.proxy.updateRenderMode(null);

        return sp;
    }

    public static IFacadePart isFacade(final ItemStack held, final ForgeDirection side) {
        if (held.getItem() instanceof IFacadeItem) {
            return ((IFacadeItem) held.getItem()).createPartFromItemStack(held, side);
        }

        if (IntegrationRegistry.INSTANCE.isEnabled(IntegrationType.BuildCraftTransport)) {
            final IBuildCraftTransport bc = (IBuildCraftTransport) IntegrationRegistry.INSTANCE
                    .getInstance(IntegrationType.BuildCraftTransport);
            if (bc.isFacade(held)) {
                return bc.createFacadePart(held, side);
            }
        }

        return null;
    }

    private static float getEyeHeight() {
        return eyeHeight;
    }

    public static void setEyeHeight(final float eyeHeight) {
        PartPlacement.eyeHeight = eyeHeight;
    }

    // We use receiveCanceled in order to perform cleanup of placing field and skip further processing
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void cancelSubsequentRightClickAirEvent(final PlayerInteractEvent event) {
        if (!event.entityPlayer.worldObj.isRemote) return;
        if (placing) {
            if (event.action != Action.RIGHT_CLICK_AIR) {
                AELog.error("Unexpected sequence for playerInteract event");
            }
            placing = false;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void playerInteract(final PlayerInteractEvent event) {
        // Strictly client only, we cancel the event and send the packet ourselves
        // Server will handle the packet directly instead of player interaction
        if (!event.entityPlayer.worldObj.isRemote) return;
        if (event.action == Action.RIGHT_CLICK_AIR) {
            final ItemStack held = event.entityPlayer.getHeldItem();
            final IItems items = AEApi.instance().definitions().items();

            boolean supportedItem = items.memoryCard().isSameAs(held);
            supportedItem |= items.colorApplicator().isSameAs(held);

            if (event.entityPlayer.isSneaking() && held != null && supportedItem) {
                event.setCanceled(true);
                NetworkHandler.instance.sendToServer(new PacketClick(event.x, event.y, event.z, event.face, 0, 0, 0));
            }
        } else if (event.action == Action.RIGHT_CLICK_BLOCK) {
            final ItemStack held = event.entityPlayer.getHeldItem();
            if (place(
                    held,
                    event.x,
                    event.y,
                    event.z,
                    event.face,
                    event.entityPlayer,
                    event.world,
                    PlaceType.INTERACT_FIRST_PASS,
                    0)) {
                event.setCanceled(true);
                // Forge won't have right click air event if held is null
                if (held != null) {
                    placing = true;
                }
            }
        }
    }

    @Nullable
    public static IPartHost getExistingHost(@Nullable final TileEntity tile) {
        if (tile == null) {
            return null;
        }

        if (tile instanceof IPartHost host) {
            return host;
        }

        final IFMP fmp = IntegrationRegistry.INSTANCE.getInstanceIfEnabled(IntegrationType.FMP);
        if (fmp != null && fmp.getCableContainer(tile) != null) {
            return fmp.getOrCreateHost(tile);
        }

        return null;
    }

    @Nullable
    public static IPartHost getOrCreateHost(@Nullable final TileEntity tile, final EntityPlayer player,
            final int face) {
        IPartHost host = getExistingHost(tile);

        if (host == null && tile != null) {
            final IFMP fmp = IntegrationRegistry.INSTANCE.getInstanceIfEnabled(IntegrationType.FMP);
            if (fmp != null) {
                host = fmp.getOrCreateHost(tile);
            }
        }

        if (host == null && tile != null) {
            final IImmibisMicroblocks immibis = IntegrationRegistry.INSTANCE
                    .getInstanceIfEnabled(IntegrationType.ImmibisMicroblocks);
            if (immibis != null) {
                host = immibis.getOrCreateHost(player, face, tile);
            }
        }

        return host;
    }

    public enum PlaceType {
        PLACE_ITEM,
        INTERACT_FIRST_PASS,
        INTERACT_SECOND_PASS
    }
}
