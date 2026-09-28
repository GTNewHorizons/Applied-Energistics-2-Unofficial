package appeng.gametests.network.p2p;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.core.AppEng;
import appeng.parts.AEBasePart;
import appeng.parts.p2p.PartP2PGT5Power;
import gregtech.api.interfaces.tileentity.IEnergyConnected;

@GameTestHolder(AppEng.MOD_ID)
public class GTEUP2PTests {

    @GameTest
    public static void outputWithoutChannelDoesNotInjectEU(GameTestHelper helper) throws Exception {
        PartP2PGT5Power output = new PartP2PGT5Power(new ItemStack(Blocks.stone));
        RecordingReceiver receiver = new RecordingReceiver();

        output.output = true;
        setField(AEBasePart.class, output, "side", ForgeDirection.EAST);
        setField(PartP2PGT5Power.class, output, "cachedTarget", receiver);
        setField(PartP2PGT5Power.class, output, "isCachedTargetValid", true);

        helper.assertFalse(output.getProxy().isActive(), "Test output unexpectedly has a channel");

        Method doOutput = PartP2PGT5Power.class.getDeclaredMethod("doOutput", long.class, long.class);
        doOutput.setAccessible(true);
        long usedAmperes = (long) doOutput.invoke(output, 32L, 1L);

        helper.assertEquals(0L, usedAmperes, "Output without a channel accepted EU");
        helper.assertEquals(0L, receiver.receivedAmperes, "Output without a channel injected EU into its neighbor");
        helper.succeed();
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class RecordingReceiver extends TileEntity implements IEnergyConnected {

        private long receivedAmperes;

        @Override
        public long injectEnergyUnits(ForgeDirection side, long voltage, long amperage) {
            receivedAmperes += amperage;
            return amperage;
        }

        @Override
        public boolean inputEnergyFrom(ForgeDirection side) {
            return true;
        }

        @Override
        public boolean outputsEnergyTo(ForgeDirection side) {
            return false;
        }

        @Override
        public byte getColorization() {
            return -1;
        }

        @Override
        public byte setColorization(byte color) {
            return color;
        }
    }
}
