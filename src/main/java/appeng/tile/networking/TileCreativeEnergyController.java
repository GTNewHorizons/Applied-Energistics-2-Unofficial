package appeng.tile.networking;

public class TileCreativeEnergyController extends TileController {

    @Override
    public double getInternalMaxPower() {
        return Long.MAX_VALUE / 10000;
    }

    @Override
    public double getInternalCurrentPower() {
        return Long.MAX_VALUE / 10000;
    }

    @Override
    public boolean isInfinite() {
        return true;
    }
}
