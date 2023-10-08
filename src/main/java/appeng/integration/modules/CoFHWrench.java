package appeng.integration.modules;

import appeng.helpers.Reflected;
import appeng.integration.IIntegrationModule;
import appeng.integration.IntegrationHelper;

public class CoFHWrench implements IIntegrationModule {

    @Reflected
    public static CoFHWrench instance;

    public CoFHWrench() {
        IntegrationHelper.testClassExistence(this, cofh.api.item.IToolHammer.class);
    }

    @Override
    public void init() {}

    @Override
    public void postInit() {}
}
