package appeng.util.inv;

import java.util.Iterator;

import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import appeng.me.GridAccessException;
import appeng.parts.p2p.PartP2PLiquids;

public class AdaptorP2PFluid extends AdaptorFluidHandler {

    public AdaptorP2PFluid(PartP2PLiquids tank, ForgeDirection direction) {
        super(tank, direction);
    }

    @Override
    public boolean containsItems() {
        try {
            Iterator<PartP2PLiquids> it = ((PartP2PLiquids) fluidHandler).getOutputs().iterator();
            boolean checkedInput = false;
            while (it.hasNext() || !checkedInput) {
                PartP2PLiquids p2p;
                if (it.hasNext()) {
                    p2p = it.next();
                } else {
                    p2p = ((PartP2PLiquids) fluidHandler).getInput();
                    checkedInput = true;
                }
                if (p2p == fluidHandler || p2p == null) continue;
                IFluidHandler target = p2p.getTarget();
                if (target == null) continue;
                FluidTankInfo[] info = target.getTankInfo(p2p.getSide().getOpposite());
                if (info != null) {
                    for (FluidTankInfo tankInfo : info) {
                        FluidStack fluid = tankInfo.fluid;
                        if (fluid != null && fluid.amount > 0) {
                            return true;
                        }
                    }
                }
            }
        } catch (GridAccessException ignore) {}
        return false;
    }
}
