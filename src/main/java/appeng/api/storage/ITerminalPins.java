package appeng.api.storage;

import net.minecraft.entity.player.EntityPlayer;

import appeng.api.networking.IGrid;
import appeng.items.contents.PinsHandler;

public interface ITerminalPins {

    PinsHandler getPinsHandler(EntityPlayer player);

    IGrid getGrid();
}
