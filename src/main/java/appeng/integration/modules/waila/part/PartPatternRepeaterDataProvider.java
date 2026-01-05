package appeng.integration.modules.waila.part;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import appeng.api.parts.IPart;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.core.localization.WailaText;
import appeng.parts.misc.PartPatternRepeater;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

public class PartPatternRepeaterDataProvider extends BasePartWailaDataProvider {

    private static final String NBT_MODE = "provider";
    private static final String NBT_WAITING_STACKS = "waitingStacks";

    @Override
    public List<String> getWailaBody(IPart part, List<String> currentToolTip, IWailaDataAccessor accessor,
            IWailaConfigHandler config) {
        if (part instanceof PartPatternRepeater) {
            if (accessor.getNBTData().hasKey(NBT_MODE)) {
                currentToolTip.add(accessor.getNBTData().getString(NBT_MODE));

                if (accessor.getNBTData().hasKey(NBT_WAITING_STACKS)) {
                    currentToolTip.add(WailaText.Waiting.getLocal());
                    NBTTagList stackList = accessor.getNBTData().getTagList(NBT_WAITING_STACKS, 10);
                    for (int index = 0; index < stackList.tagCount(); index++) {
                        NBTTagCompound stackTag = stackList.getCompoundTagAt(index);
                        currentToolTip.add(stackTag.getString("q"));
                    }
                }
            }
        }

        return currentToolTip;
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, IPart part, TileEntity te, NBTTagCompound tag, World world,
            int x, int y, int z) {
        if (part instanceof PartPatternRepeater ppr) {
            tag.setString(NBT_MODE, ppr.isProvider() ? WailaText.Provider.getLocal() : WailaText.Accessor.getLocal());

            IItemList<IAEStack<?>> waitingStacks = ppr.getWaitingStacks();
            if (!waitingStacks.isEmpty()) {
                NBTTagList stackList = new NBTTagList();

                for (IAEStack<?> stack : waitingStacks) {
                    NBTTagCompound stackTag = new NBTTagCompound();
                    stackTag.setString("q", "> " + stack.getStackSize() + "x " + stack.getDisplayName());
                    stackList.appendTag(stackTag);
                }

                tag.setTag(NBT_WAITING_STACKS, stackList);
            }
        }
        return tag;
    }
}
