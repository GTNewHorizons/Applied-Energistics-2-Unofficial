package appeng.parts.p2p;

import java.lang.reflect.Method;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.IIcon;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.core.AEConfig;
import appeng.me.GridAccessException;
import cofh.api.energy.IEnergyReceiver;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTech_API;
import gregtech.api.enums.GT_Values;
import gregtech.api.interfaces.tileentity.IEnergyConnected;
import gregtech.api.util.GT_Utility;
import ic2.api.energy.tile.IEnergySink;

public class PartP2PGT5Power extends PartP2PTunnelNormal<PartP2PGT5Power> implements IPartGT5Power {

    private TileEntity cachedTarget;
    private boolean isCachedTargetValid;

    public PartP2PGT5Power(ItemStack is) {
        super(is);
    }

    private static IChatComponent chatComponent(String title, String value) {
        ChatComponentText cct = new ChatComponentText(title);
        cct.getChatStyle().setColor(EnumChatFormatting.GOLD);
        ChatComponentText ccv = new ChatComponentText(value);
        ccv.getChatStyle().setColor(EnumChatFormatting.YELLOW);
        cct.appendSibling(ccv);
        return cct;
    }

    @Override
    public void onTunnelNetworkChange() {
        super.onTunnelNetworkChange();
        this.isCachedTargetValid = false;
        this.cachedTarget = null;
    }

    @Override
    public void onNeighborChanged() {
        super.onNeighborChanged();
        this.isCachedTargetValid = false;
        this.cachedTarget = null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getTypeTexture() {
        return Blocks.obsidian.getIcon(0, 0);
    }

    @Override
    public boolean onPartActivate(EntityPlayer player, Vec3 pos) {
        if (!super.onPartActivate(player, pos) && !player.worldObj.isRemote
                && player.inventory.getCurrentItem() == null) {
            PartP2PGT5Power input = this.getInput();
            String inputLoc;
            if (input == null) {
                inputLoc = "no input";
            } else {
                TileEntity te = input.getHost().getTile();
                inputLoc = "[" + te.getWorldObj().provider.dimensionId
                        + "]("
                        + te.xCoord
                        + ", "
                        + te.yCoord
                        + ", "
                        + te.zCoord
                        + ")";
            }

            player.addChatMessage(chatComponent("------", ""));
            player.addChatMessage(chatComponent("Input location: ", (input == this ? "this block " : "") + inputLoc));
            player.addChatMessage(
                    chatComponent("Name: ", input != null ? input.getCustomName() : this.getCustomName()));
            player.addChatMessage(chatComponent("Freq: ", "" + this.getFrequency()));
            return true;
        } else {
            return false;
        }
    }

    public long injectEnergyUnits(long voltage, long amperage) {
        if (!this.isOutput() && voltage > 0L && amperage > 0L) {
            long amperesUsed = 0;
            long amperes = amperage;
            long outvoltage = (long) (voltage * (1 - AEConfig.TUNNEL_POWER_LOSS));
            try {
                for (PartP2PGT5Power t : getOutputs()) {
                    long received = t.doOutput(outvoltage, amperes);
                    amperesUsed += received;
                    amperes -= received;
                    if (amperes <= 0L) break;
                }
            } catch (GridAccessException ignored) {}
            return amperesUsed;
        } else {
            return 0L;
        }
    }

    public boolean inputEnergy() {
        return !this.isOutput();
    }

    public boolean outputsEnergy() {
        return this.isOutput();
    }

    @Nullable
    private TileEntity getTarget() {
        TileEntity te;
        if (this.isCachedTargetValid) {
            te = this.cachedTarget;
            if (te == null || !te.isInvalid()) {
                return te;
            }
        }

        this.isCachedTargetValid = true;
        te = this.getTile();
        ForgeDirection side = this.getSide();
        return this.cachedTarget = te.getWorldObj()
                .getTileEntity(te.xCoord + side.offsetX, te.yCoord + side.offsetY, te.zCoord + side.offsetZ);
    }

    private long injectEnergy(IEnergyConnected te, ForgeDirection oppositeSide, long aVoltage, long aAmperage) {
        try {
            return te.injectEnergyUnits(oppositeSide, aVoltage, aAmperage);
        } catch (Throwable e) { // NoSuchMethodException on old GT versions
            Class<?> iEConn = te.getClass();
            try {
                Method injectEU = iEConn.getMethod("injectEnergyUnits", byte.class, long.class, long.class);
                return (long) injectEU.invoke(te, (byte) oppositeSide.ordinal(), aVoltage, aAmperage);
            } catch (Throwable error) {
                return 0L;
            }
        }
    }

    private long doOutput(long aVoltage, long aAmperage) {
        if (!this.isOutput()) {
            return 0L;
        } else {
            TileEntity te = this.getTarget();
            if (te == null) {
                return 0L;
            } else {
                ForgeDirection oppositeSide = this.getSide().getOpposite();
                if (te instanceof IEnergyConnected) {
                    return injectEnergy((IEnergyConnected) te, oppositeSide, aVoltage, aAmperage);
                } else {
                    if (te instanceof IEnergySink) {
                        if (((IEnergySink) te).acceptsEnergyFrom(this.getTile(), oppositeSide)) {
                            long rUsedAmperes = 0L;
                            while (aAmperage > rUsedAmperes && ((IEnergySink) te).getDemandedEnergy() > 0.0D
                                    && ((IEnergySink) te)
                                            .injectEnergy(oppositeSide, (double) aVoltage, (double) aVoltage)
                                            < (double) aVoltage)
                                ++rUsedAmperes;

                            return rUsedAmperes;
                        }
                    } else if (GregTech_API.mOutputRF && te instanceof IEnergyReceiver) {
                        int rfOut = (int) (aVoltage * (long) GregTech_API.mEUtoRF / 100L);
                        if (((IEnergyReceiver) te).receiveEnergy(oppositeSide, rfOut, true) == rfOut) {
                            ((IEnergyReceiver) te).receiveEnergy(oppositeSide, rfOut, false);
                            return 1L;
                        }

                        if (GregTech_API.mRFExplosions && GregTech_API.sMachineExplosions
                                && ((IEnergyReceiver) te).getMaxEnergyStored(oppositeSide) < rfOut * 600
                                && rfOut > 32 * GregTech_API.mEUtoRF / 100) {
                            float tStrength = (long) rfOut < GT_Values.V[0] ? 1.0F
                                    : ((long) rfOut < GT_Values.V[1] ? 2.0F
                                            : ((long) rfOut < GT_Values.V[2] ? 3.0F
                                                    : ((long) rfOut < GT_Values.V[3] ? 4.0F
                                                            : ((long) rfOut < GT_Values.V[4] ? 5.0F
                                                                    : ((long) rfOut < GT_Values.V[4] * 2L ? 6.0F
                                                                            : ((long) rfOut < GT_Values.V[5] ? 7.0F
                                                                                    : ((long) rfOut < GT_Values.V[6]
                                                                                            ? 8.0F
                                                                                            : ((long) rfOut
                                                                                                    < GT_Values.V[7]
                                                                                                            ? 9.0F
                                                                                                            : 10.0F))))))));
                            int tX = te.xCoord;
                            int tY = te.yCoord;
                            int tZ = te.zCoord;
                            World tWorld = te.getWorldObj();
                            GT_Utility.sendSoundToPlayers(
                                    tWorld,
                                    GregTech_API.sSoundList.get(209),
                                    1.0F,
                                    -1.0F,
                                    tX,
                                    tY,
                                    tZ);
                            tWorld.setBlock(tX, tY, tZ, Blocks.air);
                            if (GregTech_API.sMachineExplosions) {
                                tWorld.createExplosion(
                                        null,
                                        (double) tX + 0.5D,
                                        (double) tY + 0.5D,
                                        (double) tZ + 0.5D,
                                        tStrength,
                                        true);
                            }
                        }
                    }

                    return 0L;
                }
            }
        }
    }
}
