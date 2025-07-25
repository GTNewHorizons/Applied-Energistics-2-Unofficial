/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.core;

import java.io.File;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.common.config.Property.Type;

import appeng.api.config.CellType;
import appeng.api.config.CondenserOutput;
import appeng.api.config.CraftingSortOrder;
import appeng.api.config.CraftingStatus;
import appeng.api.config.PinsState;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnits;
import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.StringOrder;
import appeng.api.config.TerminalFontSize;
import appeng.api.config.TerminalStyle;
import appeng.api.config.YesNo;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.core.features.AEFeature;
import appeng.core.settings.TickRates;
import appeng.items.materials.MaterialType;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class AEConfig extends Configuration implements IConfigurableObject, IConfigManagerHost {

    public static double TUNNEL_POWER_LOSS = 0.05;
    public static final String VERSION = BuildTags.VERSION;
    public static final String PACKET_CHANNEL = "AE";
    public static AEConfig instance;
    public final IConfigManager settings = new ConfigManager(this);
    public final EnumSet<AEFeature> featureFlags = EnumSet.noneOf(AEFeature.class);
    public final int[] craftByStacks = { 1, 10, 100, 1000 };
    public final int[] priorityByStacks = { 1, 10, 100, 1000 };
    public final int[] levelByStacks = { 1, 10, 100, 1000 };
    private final double WirelessHighWirelessCount = 64;
    private final File configFile;
    public int storageBiomeID = -1;
    public int storageProviderID = -1;
    public int formationPlaneEntityLimit = 128;
    public double networkBytesUpdateFrequency = 1.0d;
    public float spawnChargedChance = 0.92f;
    public int quartzOresPerCluster = 4;
    public int quartzOresClusterAmount = 15;
    public final int chargedChange = 4;
    @Deprecated
    public int quartzKnifeInputLength = 32;
    public String[] minMeteoriteDistance = { "0=707" };
    public double spatialPowerExponent = 1.35;
    public double spatialPowerMultiplier = 1250.0;
    public String[] grinderOres = {
            // Vanilla Items
            "Obsidian", "Ender", "EnderPearl", "Coal", "Iron", "Gold", "Charcoal", "NetherQuartz",
            // Common Mod Ores
            "Copper", "Tin", "Silver", "Lead", "Bronze",
            // AE
            "CertusQuartz", "Wheat", "Fluix",
            // Other Mod Ores
            "Brass", "Platinum", "Nickel", "Invar", "Aluminium", "Electrum", "Osmium", "Zinc" };
    public double oreDoublePercentage = 90.0;
    public boolean enableEffects = true;
    public boolean useColoredCraftingStatus;
    public boolean preserveSearchBar = true;
    public boolean showOnlyInterfacesWithFreeSlotsInInterfaceTerminal = false;
    public int MEMonitorableSmallSize = 6;
    public int InterfaceTerminalSmallSize = 6;

    public boolean debugLogTiming = false;
    public boolean debugPathFinding = false;
    public boolean captureGAEStacks = false;
    public int wirelessTerminalBattery = 1600000;
    public int entropyManipulatorBattery = 200000;
    public int matterCannonBattery = 200000;
    public int portableCellBattery = 20000;
    public int colorApplicatorBattery = 20000;
    public int chargedStaffBattery = 8000;
    public boolean disableColoredCableRecipesInNEI = true;
    public boolean updatable = false;
    public String[] meteoriteSpawnChance = { "0=0.3" };
    public String[] meteoriteDimensionWhitelist = { "0, DEFAULT, DEFAULT, DEFAULT, DEFAULT, DEFAULT" };
    public String[] meteoriteValidBlocks = { "examplemod:example_block" };
    public String[] meteoriteInvalidBlocks = { "examplemod:example_block" };
    public int craftingCalculationTimePerTick = 5;
    PowerUnits selectedPowerUnit = PowerUnits.AE;
    CellType selectedCellType = CellType.ITEM;
    private double WirelessBaseCost = 8;
    private double WirelessCostMultiplier = 1;
    private double WirelessTerminalDrainMultiplier = 1;
    private double WirelessBaseRange = 16;
    private double WirelessBoosterRangeMultiplier = 1;
    private double WirelessBoosterExp = 1.5;
    public int levelEmitterDelay = 40;
    public int craftingCalculatorVersion = 2;
    public int maxCraftingSteps = 2_000_000;
    public int maxCraftingTreeVisualizationSize = 32 * 1024 * 1024; // 32 MiB
    public boolean limitCraftingCPUSpill = true;

    public AEConfig(final File configFile) {
        super(configFile);
        this.configFile = configFile;

        FMLCommonHandler.instance().bus().register(new EventHandler());

        final double DEFAULT_MEKANISM_EXCHANGE = 0.2;
        PowerUnits.MK.conversionRatio = this.get("PowerRatios", "Mekanism", DEFAULT_MEKANISM_EXCHANGE)
                .getDouble(DEFAULT_MEKANISM_EXCHANGE);
        final double DEFAULT_IC2_EXCHANGE = 2.0;
        PowerUnits.EU.conversionRatio = this.get("PowerRatios", "IC2", DEFAULT_IC2_EXCHANGE)
                .getDouble(DEFAULT_IC2_EXCHANGE);
        final double DEFAULT_RTC_EXCHANGE = 1.0 / 11256.0;
        PowerUnits.WA.conversionRatio = this.get("PowerRatios", "RotaryCraft", DEFAULT_RTC_EXCHANGE)
                .getDouble(DEFAULT_RTC_EXCHANGE);
        final double DEFAULT_RF_EXCHANGE = 0.5;
        PowerUnits.RF.conversionRatio = this.get("PowerRatios", "ThermalExpansion", DEFAULT_RF_EXCHANGE)
                .getDouble(DEFAULT_RF_EXCHANGE);
        final double DEFAULT_TUNNEL_POWER_LOSS = 0.05;
        TUNNEL_POWER_LOSS = this.get("PowerRatios", "TunnelPowerLoss", DEFAULT_TUNNEL_POWER_LOSS)
                .getDouble(DEFAULT_TUNNEL_POWER_LOSS);
        if (TUNNEL_POWER_LOSS < 0 || TUNNEL_POWER_LOSS >= 1) TUNNEL_POWER_LOSS = DEFAULT_TUNNEL_POWER_LOSS;

        final double usageEffective = this.get("PowerRatios", "UsageMultiplier", 1.0).getDouble(1.0);
        PowerMultiplier.CONFIG.multiplier = Math.max(0.01, usageEffective);

        CondenserOutput.MATTER_BALLS.requiredPower = this.get("Condenser", "MatterBalls", 256).getInt(256);
        CondenserOutput.SINGULARITY.requiredPower = this.get("Condenser", "Singularity", 256000).getInt(256000);

        this.grinderOres = this.get("GrindStone", "grinderOres", this.grinderOres).getStringList();
        this.oreDoublePercentage = this.get("GrindStone", "oreDoublePercentage", this.oreDoublePercentage)
                .getDouble(this.oreDoublePercentage);

        this.settings.registerSetting(Settings.SEARCH_TOOLTIPS, YesNo.YES);
        this.settings.registerSetting(Settings.TERMINAL_STYLE, TerminalStyle.TALL);
        this.settings.registerSetting(Settings.HIDE_STORED, YesNo.NO);
        this.settings.registerSetting(Settings.SEARCH_MODE, SearchBoxMode.AUTOSEARCH);
        this.settings.registerSetting(Settings.SAVE_SEARCH, YesNo.NO);
        this.settings.registerSetting(Settings.CRAFTING_STATUS, CraftingStatus.TILE);
        this.settings.registerSetting(Settings.CRAFTING_SORT_BY, CraftingSortOrder.NAME);
        this.settings.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);
        this.settings.registerSetting(Settings.TERMINAL_FONT_SIZE, TerminalFontSize.SMALL);
        this.settings.registerSetting(Settings.INTERFACE_TERMINAL_SECTION_ORDER, StringOrder.NATURAL);
        this.settings.registerSetting(Settings.PINS_STATE, PinsState.DISABLED);

        this.spawnChargedChance = (float) (1.0
                - this.get("worldGen", "spawnChargedChance", 1.0 - this.spawnChargedChance)
                        .getDouble(1.0 - this.spawnChargedChance));
        this.minMeteoriteDistance = this.get("worldGen", "minMeteoriteDistance", this.minMeteoriteDistance)
                .getStringList();
        this.meteoriteSpawnChance = this.get("worldGen", "meteoriteSpawnChance", this.meteoriteSpawnChance)
                .getStringList();
        this.meteoriteDimensionWhitelist = this
                .get("worldGen", "meteoriteDimensionWhitelist", this.meteoriteDimensionWhitelist).getStringList();
        this.addCustomCategoryComment(
                "worldGen",
                "The meteorite dimension whitelist list can be used alone or in unison with the meteorite (in)valid blocks whitelist. \n"
                        + "Default debris is the following (in this order) Stone, Cobblestone, biomeFillerBlock (what's under the top block, usually dirt), Gravel, biomeTopBlock (usually grass) \n"
                        + "Format: dimensionID, modID:blockID:metadata, modID:blockID:metadata, modID:blockID:metadata, modID:blockID:metadata, modID:blockID:metadata \n"
                        + "--------------------------------------------------------------------------------------------------------# \n"
                        + "The meteorite (in)valid spawn blocks list can be used alone or in unison with the meteorite dimension whitelist. Format: modId:blockID, modId:blockID, etc. ");
        this.meteoriteValidBlocks = this.get("worldGen", "meteoriteValidSpawnBlocks", this.meteoriteValidBlocks)
                .getStringList();
        this.meteoriteInvalidBlocks = this.get("worldGen", "meteoriteInvalidSpawnBlocks", this.meteoriteInvalidBlocks)
                .getStringList();
        this.quartzOresPerCluster = this.get("worldGen", "quartzOresPerCluster", this.quartzOresPerCluster)
                .getInt(this.quartzOresPerCluster);
        this.quartzOresClusterAmount = this.get("worldGen", "quartzOresClusterAmount", this.quartzOresClusterAmount)
                .getInt(this.quartzOresClusterAmount);

        // this.minMeteoriteDistanceSq = this.minMeteoriteDistance * this.minMeteoriteDistance;

        this.addCustomCategoryComment(
                "wireless",
                "Range= WirelessBaseRange + WirelessBoosterRangeMultiplier * Math.pow( boosters, WirelessBoosterExp )\nPowerDrain= WirelessBaseCost + WirelessCostMultiplier * Math.pow( boosters, 1 + boosters / WirelessHighWirelessCount )");

        this.WirelessBaseCost = this.get("wireless", "WirelessBaseCost", this.WirelessBaseCost)
                .getDouble(this.WirelessBaseCost);
        this.WirelessCostMultiplier = this.get("wireless", "WirelessCostMultiplier", this.WirelessCostMultiplier)
                .getDouble(this.WirelessCostMultiplier);
        this.WirelessBaseRange = this.get("wireless", "WirelessBaseRange", this.WirelessBaseRange)
                .getDouble(this.WirelessBaseRange);
        this.WirelessBoosterRangeMultiplier = this
                .get("wireless", "WirelessBoosterRangeMultiplier", this.WirelessBoosterRangeMultiplier)
                .getDouble(this.WirelessBoosterRangeMultiplier);
        this.WirelessBoosterExp = this.get("wireless", "WirelessBoosterExp", this.WirelessBoosterExp)
                .getDouble(this.WirelessBoosterExp);
        this.WirelessTerminalDrainMultiplier = this
                .get("wireless", "WirelessTerminalDrainMultiplier", this.WirelessTerminalDrainMultiplier)
                .getDouble(this.WirelessTerminalDrainMultiplier);

        this.formationPlaneEntityLimit = this
                .get("automation", "formationPlaneEntityLimit", this.formationPlaneEntityLimit)
                .getInt(this.formationPlaneEntityLimit);
        this.networkBytesUpdateFrequency = this
                .get("automation", "networkBytesUpdateFrequency", this.networkBytesUpdateFrequency)
                .getDouble(this.networkBytesUpdateFrequency);
        if (this.networkBytesUpdateFrequency == 0) {
            throw new IllegalArgumentException("networkBytesUpdateFrequency can not be 0!");
        }
        this.get(
                "automation",
                "networkBytesUpdateFrequency",
                this.networkBytesUpdateFrequency).comment = "#Network bytes information update Frequency(s) default:1.0";

        this.wirelessTerminalBattery = this.get("battery", "wirelessTerminal", this.wirelessTerminalBattery)
                .getInt(this.wirelessTerminalBattery);
        this.chargedStaffBattery = this.get("battery", "chargedStaff", this.chargedStaffBattery)
                .getInt(this.chargedStaffBattery);
        this.entropyManipulatorBattery = this.get("battery", "entropyManipulator", this.entropyManipulatorBattery)
                .getInt(this.entropyManipulatorBattery);
        this.portableCellBattery = this.get("battery", "portableCell", this.portableCellBattery)
                .getInt(this.portableCellBattery);
        this.colorApplicatorBattery = this.get("battery", "colorApplicator", this.colorApplicatorBattery)
                .getInt(this.colorApplicatorBattery);
        this.matterCannonBattery = this.get("battery", "matterCannon", this.matterCannonBattery)
                .getInt(this.matterCannonBattery);

        this.levelEmitterDelay = this.get("tickrates", "LevelEmitterDelay", this.levelEmitterDelay)
                .getInt(this.levelEmitterDelay);
        this.debugLogTiming = this.get("debug", "LogTiming", this.debugLogTiming).getBoolean(this.debugLogTiming);
        this.debugPathFinding = this.get("debug", "LogPathFinding", this.debugPathFinding)
                .getBoolean(this.debugPathFinding);
        this.craftingCalculatorVersion = this.get("debug", "CraftingCalculatorVersion", this.craftingCalculatorVersion)
                .getInt(this.craftingCalculatorVersion);
        this.craftingCalculatorVersion = Math.max(1, Math.min(this.craftingCalculatorVersion, 2));
        this.captureGAEStacks = this.get("debug", "CaptureGridAccessExceptionStacks", false).getBoolean();
        this.maxCraftingSteps = this.get("misc", "MaxCraftingSteps", this.maxCraftingSteps)
                .getInt(this.maxCraftingSteps);
        this.maxCraftingTreeVisualizationSize = this
                .get("misc", "MaxCraftingTreeVisualizationSize", this.maxCraftingTreeVisualizationSize)
                .getInt(this.maxCraftingTreeVisualizationSize);
        // Clamp to 4kiB..1GiB
        this.maxCraftingTreeVisualizationSize = Math
                .max(4096, Math.min(this.maxCraftingTreeVisualizationSize, 1024 * 1024 * 1024));
        this.limitCraftingCPUSpill = this.get("misc", "LimitCraftingCPUSpill", this.limitCraftingCPUSpill)
                .getBoolean(this.limitCraftingCPUSpill);
        this.clientSync();

        for (final AEFeature feature : AEFeature.values()) {
            if (feature.isVisible()) {
                if (this.get("Features." + feature.category, feature.name(), feature.defaultValue)
                        .getBoolean(feature.defaultValue)) {
                    this.featureFlags.add(feature);
                }
            } else {
                this.featureFlags.add(feature);
            }
        }

        final ModContainer imb = cpw.mods.fml.common.Loader.instance().getIndexedModList().get("ImmibisCore");
        if (imb != null) {
            final List<String> version = Arrays.asList("59.0.0", "59.0.1", "59.0.2");
            if (version.contains(imb.getVersion())) {
                this.featureFlags.remove(AEFeature.AlphaPass);
            }
        }

        try {
            this.selectedPowerUnit = PowerUnits.valueOf(
                    this.get(
                            "Client",
                            "PowerUnit",
                            this.selectedPowerUnit.name(),
                            this.getListComment(this.selectedPowerUnit)).getString());
        } catch (final Throwable t) {
            this.selectedPowerUnit = PowerUnits.AE;
        }

        for (final TickRates tr : TickRates.values()) {
            tr.Load(this);
        }

        if (this.isFeatureEnabled(AEFeature.SpatialIO)) {
            this.storageBiomeID = this.get("spatialio", "storageBiomeID", this.storageBiomeID)
                    .getInt(this.storageBiomeID);
            this.storageProviderID = this.get("spatialio", "storageProviderID", this.storageProviderID)
                    .getInt(this.storageProviderID);
            this.spatialPowerMultiplier = this.get("spatialio", "spatialPowerMultiplier", this.spatialPowerMultiplier)
                    .getDouble(this.spatialPowerMultiplier);
            this.spatialPowerExponent = this.get("spatialio", "spatialPowerExponent", this.spatialPowerExponent)
                    .getDouble(this.spatialPowerExponent);
        }

        if (this.isFeatureEnabled(AEFeature.CraftingCPU)) {
            this.craftingCalculationTimePerTick = this
                    .get("craftingCPU", "craftingCalculationTimePerTick", this.craftingCalculationTimePerTick)
                    .getInt(this.craftingCalculationTimePerTick);
        }

        this.updatable = true;
    }

    private void clientSync() {
        this.disableColoredCableRecipesInNEI = this.get("Client", "disableColoredCableRecipesInNEI", true)
                .getBoolean(true);
        this.enableEffects = this.get("Client", "enableEffects", true).getBoolean(true);
        this.useColoredCraftingStatus = this.get("Client", "useColoredCraftingStatus", true).getBoolean(true);
        this.preserveSearchBar = this.get("Client", "preserveSearchBar", true).getBoolean(true);
        this.showOnlyInterfacesWithFreeSlotsInInterfaceTerminal = this
                .get("Client", "showOnlyInterfacesWithFreeSlotsInInterfaceTerminal", false).getBoolean(false);
        this.MEMonitorableSmallSize = this.get("Client", "MEMonitorableSmallSize", 6).getInt(6);
        this.InterfaceTerminalSmallSize = this.get("Client", "InterfaceTerminalSmallSize", 6).getInt(6);
        // load buttons..
        for (int btnNum = 0; btnNum < 4; btnNum++) {
            final Property cmb = this.get("Client", "craftAmtButton" + (btnNum + 1), this.craftByStacks[btnNum]);
            final Property pmb = this.get("Client", "priorityAmtButton" + (btnNum + 1), this.priorityByStacks[btnNum]);
            final Property lmb = this.get("Client", "levelAmtButton" + (btnNum + 1), this.levelByStacks[btnNum]);

            final int buttonCap = (int) (Math.pow(10, btnNum + 1) - 1);

            this.craftByStacks[btnNum] = Math.abs(cmb.getInt(this.craftByStacks[btnNum]));
            this.priorityByStacks[btnNum] = Math.abs(pmb.getInt(this.priorityByStacks[btnNum]));
            this.levelByStacks[btnNum] = Math.abs(pmb.getInt(this.levelByStacks[btnNum]));

            cmb.comment = "Controls buttons on Crafting Screen : Capped at " + buttonCap;
            pmb.comment = "Controls buttons on Priority Screen : Capped at " + buttonCap;
            lmb.comment = "Controls buttons on Level Emitter Screen : Capped at " + buttonCap;

            this.craftByStacks[btnNum] = Math.min(this.craftByStacks[btnNum], buttonCap);
            this.priorityByStacks[btnNum] = Math.min(this.priorityByStacks[btnNum], buttonCap);
            this.levelByStacks[btnNum] = Math.min(this.levelByStacks[btnNum], buttonCap);
        }

        for (final Settings e : this.settings.getSettings()) {
            final String Category = "Client"; // e.getClass().getSimpleName();
            Enum<?> value = this.settings.getSetting(e);

            final Property p = this.get(Category, e.name(), value.name(), this.getListComment(value));

            try {
                value = Enum.valueOf(value.getClass(), p.getString());
            } catch (final IllegalArgumentException er) {
                AELog.info(
                        "Invalid value '" + p
                                .getString() + "' for " + e.name() + " using '" + value.name() + "' instead");
            }

            this.settings.putSetting(e, value);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Enum<T>> String getListComment(final Enum<T> value) {
        StringBuilder comment = new StringBuilder();

        if (value != null) {
            final EnumSet<T> set = EnumSet.allOf((Class<T>) value.getClass());

            for (final T eg : set) {
                if (comment.length() == 0) {
                    comment.append("Possible Values: ").append(eg.name());
                } else {
                    comment.append(", ").append(eg.name());
                }
            }
        }

        return comment.toString();
    }

    public boolean isFeatureEnabled(final AEFeature f) {
        return this.featureFlags.contains(f);
    }

    public double wireless_getDrainRate(final double range) {
        return this.WirelessTerminalDrainMultiplier * range;
    }

    public double wireless_getMaxRange(final int boosters) {
        return this.WirelessBaseRange
                + this.WirelessBoosterRangeMultiplier * Math.pow(boosters, this.WirelessBoosterExp);
    }

    public double wireless_getPowerDrain(final int boosters) {
        return this.WirelessBaseCost
                + this.WirelessCostMultiplier * Math.pow(boosters, 1 + boosters / this.WirelessHighWirelessCount);
    }

    @Override
    public Property get(final String category, final String key, final String defaultValue, final String comment,
            final Property.Type type) {
        final Property prop = super.get(category, key, defaultValue, comment, type);

        if (prop != null) {
            if (!category.equals("Client")) {
                prop.setRequiresMcRestart(true);
            }
        }

        return prop;
    }

    @Override
    public Property get(String category, String key, String[] defaultValues) { // compatibility for old configs
        Property prop = super.get(category, key, defaultValues, null, false, -1, null);
        if (prop.getType() != Type.STRING) {
            ConfigCategory cat = getCategory(category.toLowerCase());
            cat.remove(key);
            prop = super.get(category, key, defaultValues, null, false, -1, null);
            save();
        }
        return prop;
    }

    @Override
    public void save() {
        if (this.isFeatureEnabled(AEFeature.SpatialIO)) {
            this.get("spatialio", "storageBiomeID", this.storageBiomeID).set(this.storageBiomeID);
            this.get("spatialio", "storageProviderID", this.storageProviderID).set(this.storageProviderID);
        }

        this.get("Client", "PowerUnit", this.selectedPowerUnit.name(), this.getListComment(this.selectedPowerUnit))
                .set(this.selectedPowerUnit.name());

        if (this.hasChanged()) {
            super.save();
        }
    }

    public class EventHandler {

        @SubscribeEvent
        public void onConfigChanged(final ConfigChangedEvent.OnConfigChangedEvent eventArgs) {
            if (eventArgs.modID.equals(AppEng.MOD_ID)) {
                AEConfig.this.clientSync();
            }
        }
    }

    public boolean disableColoredCableRecipesInNEI() {
        return this.disableColoredCableRecipesInNEI;
    }

    public String getFilePath() {
        return this.configFile.toString();
    }

    public boolean useAEVersion(final MaterialType mt) {
        if (this.isFeatureEnabled(AEFeature.WebsiteRecipes)) {
            return true;
        }

        this.setCategoryComment(
                "OreCamouflage",
                "AE2 Automatically uses alternative ores present in your instance of MC to blend better with its surroundings, if you prefer you can disable this selectively using these flags; Its important to note, that some if these items even if enabled may not be craftable in game because other items are overriding their recipes.");
        final Property p = this.get("OreCamouflage", mt.name(), true);
        p.comment = "OreDictionary Names: " + mt.getOreName();

        return !p.getBoolean(true);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void updateSetting(final IConfigManager manager, final Enum setting, final Enum newValue) {
        for (final Settings e : this.settings.getSettings()) {
            if (e == setting) {
                final String Category = "Client";
                final Property p = this
                        .get(Category, e.name(), this.settings.getSetting(e).name(), this.getListComment(newValue));
                p.set(newValue.name());
            }
        }

        if (this.updatable) {
            this.save();
        }
    }

    public int getFreeMaterial(final int varID) {
        return this.getFreeIDSLot(varID, "materials");
    }

    public int getFreeIDSLot(final int varID, final String category) {
        boolean alreadyUsed = false;
        int min = 0;

        for (final Property p : this.getCategory(category).getValues().values()) {
            final int thisInt = p.getInt();

            if (varID == thisInt) {
                alreadyUsed = true;
            }

            min = Math.max(min, thisInt + 1);
        }

        if (alreadyUsed) {
            if (min < 16383) {
                min = 16383;
            }

            return min;
        }

        return varID;
    }

    public int getFreePart(final int varID) {
        return this.getFreeIDSLot(varID, "parts");
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.settings;
    }

    /**
     * @deprecated Use the new method getTerminalFontSize() to get more options
     * @return True if terminal large font is used, false otherwise
     */
    @Deprecated
    public boolean useTerminalUseLargeFont() {
        return getTerminalFontSize() == TerminalFontSize.LARGE;
    }

    public TerminalFontSize getTerminalFontSize() {
        return (TerminalFontSize) settings.getSetting(Settings.TERMINAL_FONT_SIZE);
    }

    public int craftItemsByStackAmounts(final int i) {
        return this.craftByStacks[i];
    }

    public int priorityByStacksAmounts(final int i) {
        return this.priorityByStacks[i];
    }

    public int levelByStackAmounts(final int i) {
        return this.levelByStacks[i];
    }

    public Enum getSetting(final String category, final Class<? extends Enum> class1, final Enum myDefault) {
        final String name = class1.getSimpleName();
        final Property p = this.get(category, name, myDefault.name());

        try {
            return (Enum) class1.getField(p.toString()).get(class1);
        } catch (final Throwable t) {
            // :{
        }

        return myDefault;
    }

    public void setSetting(final String category, final Enum s) {
        final String name = s.getClass().getSimpleName();
        this.get(category, name, s.name()).set(s.name());
        this.save();
    }

    public PowerUnits selectedPowerUnit() {
        return this.selectedPowerUnit;
    }

    public CellType selectedCellType() {
        return this.selectedCellType;
    }

    public void nextPowerUnit(final boolean backwards) {
        this.selectedPowerUnit = Platform
                .rotateEnum(this.selectedPowerUnit, backwards, Settings.POWER_UNITS.getPossibleValues());
        this.save();
    }

    public void nextCellType(final boolean backwards) {
        this.selectedCellType = Platform
                .rotateEnum(this.selectedCellType, backwards, Settings.CELL_TYPE.getPossibleValues());
    }

}
