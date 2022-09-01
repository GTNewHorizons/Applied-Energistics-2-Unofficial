package appeng.integration.modules;

import appeng.api.AEApi;
import appeng.api.exceptions.ModNotInstalled;
import appeng.helpers.Reflected;
import appeng.integration.IIntegrationModule;
import appeng.integration.IntegrationHelper;
import appeng.integration.IntegrationRegistry;
import appeng.integration.IntegrationType;
import cpw.mods.fml.common.Loader;

public class GT implements IIntegrationModule {

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
            AEApi.instance()
                    .partHelper()
                    .registerNewLayer(
                            "appeng.parts.layers.LayerIEnergyConnected",
                            "gregtech.api.interfaces.tileentity.IEnergyConnected");
        }
    }

    @Override
    public void postInit() {}
}
