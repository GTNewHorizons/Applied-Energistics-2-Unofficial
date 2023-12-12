package appeng.integration.modules.waila.tile;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import appeng.api.config.LockCraftingMode;
import appeng.api.storage.data.IAEItemStack;
import appeng.core.localization.WailaText;
import appeng.helpers.IInterfaceHost;
import appeng.integration.modules.waila.BaseWailaDataProvider;
import appeng.util.item.AEItemStack;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

public final class InterfaceDataProvider extends BaseWailaDataProvider {

    private static final String NBT_LOCK_REASON = "craftingLockReason";
    private static final String NBT_LOCK_STACKS = "craftingLockStacks";

    @Override
    public List<String> getWailaBody(ItemStack itemStack, List<String> currentToolTip, IWailaDataAccessor accessor,
            IWailaConfigHandler config) {
        if (accessor.getTileEntity() instanceof IInterfaceHost) {
            if (accessor.getNBTData().hasKey(NBT_LOCK_REASON)) {
                String lockReasonText = accessor.getNBTData().getString(NBT_LOCK_REASON);
                LockCraftingMode lockReason = LockCraftingMode.valueOf(lockReasonText);

                switch (lockReason) {
                    case LOCK_UNTIL_PULSE -> currentToolTip.add(WailaText.CraftingLockedUntilPulse.getLocal());
                    case LOCK_WHILE_HIGH -> currentToolTip.add(WailaText.CraftingLockedByRedstoneSignal.getLocal());
                    case LOCK_WHILE_LOW -> currentToolTip
                            .add(WailaText.CraftingLockedByLackOfRedstoneSignal.getLocal());
                    case LOCK_UNTIL_RESULT -> {
                        currentToolTip.add(WailaText.CraftingLockedUntilResult.getLocal());

                        if (accessor.getNBTData().hasKey(NBT_LOCK_STACKS)) {
                            NBTTagList stackList = accessor.getNBTData().getTagList(NBT_LOCK_STACKS, 10);
                            for (int index = 0; index < stackList.tagCount(); index++) {
                                NBTTagCompound stackTag = stackList.getCompoundTagAt(index);
                                AEItemStack stack = (AEItemStack) AEItemStack.loadItemStackFromNBT(stackTag);

                                if (stack != null) {
                                    currentToolTip.add("> " + stack.getStackSize() + " " + stack.getDisplayName());
                                } else {
                                    currentToolTip.add("ERROR");
                                }
                            }
                        } else {
                            currentToolTip.add("ERROR");
                        }
                    }
                }
            }
        }

        return currentToolTip;
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world, int x,
            int y, int z) {
        if (te instanceof IInterfaceHost) {
            var interfaceDuality = ((IInterfaceHost) te).getInterfaceDuality();

            tag.setString(NBT_LOCK_REASON, interfaceDuality.getCraftingLockedReason().name());
            LockCraftingMode lock = interfaceDuality.getCraftingLockedReason();
            if (lock == LockCraftingMode.LOCK_UNTIL_RESULT) {
                List<IAEItemStack> unlockStacks = interfaceDuality.getUnlockStacks();
                if (unlockStacks != null && !unlockStacks.isEmpty()) {
                    NBTTagList stackList = new NBTTagList();
                    for (IAEItemStack stack : unlockStacks) {
                        NBTTagCompound stackTag = new NBTTagCompound();
                        stack.writeToNBT(stackTag);
                        stackList.appendTag(stackTag);
                    }
                    tag.setTag(NBT_LOCK_STACKS, stackList);
                }
            }
        }
        return tag;
    }
}
