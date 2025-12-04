package appeng.integration.modules;

import net.minecraft.tileentity.TileEntity;

import appeng.api.AEApi;
import appeng.api.exceptions.ModNotInstalled;
import appeng.helpers.Reflected;
import appeng.integration.IIntegrationModule;
import appeng.integration.IntegrationHelper;
import appeng.integration.IntegrationRegistry;
import appeng.integration.IntegrationType;
import appeng.integration.abstraction.IGT;
import cpw.mods.fml.common.Loader;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

public class GT implements IIntegrationModule, IGT {

    @Reflected
    public static GT instance;

    @Reflected
    public GT() throws Throwable {
        IntegrationHelper.testClassExistence(this, gregtech.api.interfaces.tileentity.IEnergyConnected.class);
        String ver = Loader.instance().getIndexedModList().get("gregtech").getVersion();
        if (!ver.equals("MC1710")) throw new ModNotInstalled("gregtech");
    }

    @Override
    public void init() throws Throwable {
        if (IntegrationRegistry.INSTANCE.isEnabled(IntegrationType.GT)) {
            AEApi.instance().partHelper().registerNewLayer(
                    "appeng.parts.layers.LayerIEnergyConnected",
                    "gregtech.api.interfaces.tileentity.IEnergyConnected");
        }
    }

    @Override
    public void postInit() {}

    @Override
    public boolean isGTMachine(TileEntity te) {
        return te instanceof IGregTechTileEntity;
    }

    @Override
    public int getGTMachineHash(TileEntity te) {
        IGregTechTileEntity igte = (IGregTechTileEntity) te;

        // PartStorageBus uses 0 to represent 'no hash' so we offset the numbers up one here. They just need to be
        // distinct. This controls when the item IO is reset.
        return igte.canAccessData() ? 2 : 1;
    }
}
