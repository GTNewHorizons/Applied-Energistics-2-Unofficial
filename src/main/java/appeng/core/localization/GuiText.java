/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.core.localization;

public enum GuiText implements Localization {

    inventory("container"), // mc's default Inventory localization.

    Chest,
    StoredEnergy,
    Of,
    Condenser,
    Drive,
    GrindStone,
    SkyChest,

    VibrationChamber,
    SpatialIOPort,
    LevelEmitter,
    Terminal,

    Interface,
    Config,
    StoredItems,
    StoredStacks,
    Patterns,
    ImportBus,
    ExportBus,

    CellWorkbench,
    NetworkDetails,
    StorageCells,
    IOBuses,

    IOPort,
    BytesUsed,
    TimeUsed,
    Types,
    QuantumLinkChamber,
    PortableCell,
    CraftName,
    Remains,
    Progress,

    NetworkTool,
    AdvancedNetworkTool,
    PowerUsageRate,
    PowerInputRate,
    Installed,
    EnergyDrain,

    StorageBus,
    Priority,
    Security,
    Encoded,
    Blank,
    Unlinked,
    Linked,

    SecurityCardEditor,
    NoPermissions,
    WirelessTerminal,
    Wireless,

    CraftingTerminal,
    FormationPlane,
    Inscriber,
    QuartzCuttingKnife,

    PatternOptimizer,
    StepsPerCraft,
    PatternsAffected,
    Multiplied,
    MultipliedBy,
    CurrentPatternOutput,
    NewPatternOutput,
    Optimize,

    // tunnel names
    METunnel,
    ItemTunnel,
    RedstoneTunnel,
    EUTunnel,
    FluidTunnel,
    OCTunnel,
    LightTunnel,
    SoundTunnel,
    RFTunnel,
    PressureTunnel,
    GTTunnel,
    IFACETunnel,

    StoredSize,
    CopyMode,
    CopyModeDesc,
    PatternTerminal,

    // Pattern tooltips
    CraftingPattern,
    ProcessingPattern,
    Crafts,
    HoldShift,
    Result,
    Results,
    Ingredients,
    Ingredient,
    Creates,
    And,
    With,
    Substitute,
    BeSubstitute,
    Yes,
    No,
    EncodedBy,
    PatternView,

    MolecularAssembler,

    StoredPower,
    MaxPower,
    RequiredPower,
    Efficiency,
    InWorldCrafting,

    inWorldFluix,
    inWorldPurificationCertus,
    inWorldPurificationNether,

    inWorldPurificationFluix,
    inWorldSingularity,
    ChargedQuartz,

    NoSecondOutput,
    OfSecondOutput,
    MultipleOutputs,

    Stores,
    Next,
    SelectAmount,
    IncreaseAmount,
    DecreaseAmount,
    Lumen,
    Empty,

    ConfirmCrafting,
    Stored,
    Crafting,
    Scheduled,
    CraftingStatus,
    RemainingOperations,
    AddToBookmark,
    Cancel,
    Suspend,
    Resume,
    ETA,
    ETAFormat,
    SwitchCraftingSimulationDisplayMode,

    FromStorage,
    FromStoragePercent,
    ToCraft,
    ToCraftRequests,
    CraftingPlan,
    CalculatingWait,
    Start,
    StartWithFollow,
    Merge,
    Bytes,
    Set,

    CraftingCPU,
    Automatic,
    CoProcessors,
    Simulation,
    Missing,
    CraftingStepLimitExceeded,
    CraftingSizeLimitExceeded,
    NoCraftingTreeReceived,
    RequestedItem,
    SimulationIncomplete,

    InterfaceTerminal,
    NoCraftingCPUs,
    Clean,
    InvalidPattern,
    UnknownItem,

    InterfaceTerminalHint,
    PatternOptimization,
    PatternOptimizationHint,
    Range,
    TransparentFacades,
    TransparentFacadesHint,

    NoCraftingJobs,
    CPUs,
    FacadeCrafting,
    inWorldCraftingPresses,
    ChargedQuartzFind,

    Included,
    Excluded,
    Partitioned,
    PartitionedOre,
    Precise,
    Fuzzy,
    Filter,
    Sticky,
    Contains,

    // Used in a terminal to indicate that an item is craftable
    SmallFontCraft,
    LargeFontCraft,

    // processing pattern terminal
    PatternTerminalEx,

    // View Cell
    ViewCellToggleKey,

    // renaming GUI label
    Renamer,

    // oredictionary filter GUI label
    OreFilterLabel,

    PriorityCard,
    PriorityCardTooltip,
    PriorityCardTooltipModeEdit,
    PriorityCardTooltipModeView,
    PriorityCardTooltipModeSet,
    PriorityCardTooltipModeInc,
    PriorityCardTooltipModeDec,

    HoldShiftForTooltip,
    HoldShiftClick_HIGHLIGHT_INTERFACE,

    // Used in a ME Interface when no appropriate TileEntity was detected near it
    Nothing,

    VoidCellTooltip,

    // If a thing is deprecated
    Deprecated,

    // Network bytes status
    NetworkCellStatus,
    NetworkItemCellCount,
    NetworkFluidCellCount,
    NetworkEssentiaCellCount,
    Green,
    Blue,
    Orange,
    Red,
    NetworkBytesDetails,
    Items,
    Fluids,
    Essentias,
    TypesInfo,
    BytesInfo,
    ToFollow,
    ToUnfollow,
    CellRestriction,
    CellRestrictionTips,
    Restricted,
    MaxItems,
    MaxFluid,
    MaxTypes,
    NumberOfItems,
    NumberOfFluids,
    ItemsPerByte,
    FluidsPerByte,
    BytesPerType,

    CellView,
    EncodedPattern,

    Inputs,
    Outputs,
    CPUAllowMode,
    CPUAllowAll,
    CPUOnlyAllowNonPlayer,
    CPUOnlyAllowPlayer,
    CPUSourcePlayer,
    CPUSourceMachineRequested,

    // Storage Scan Report
    StorageScan,
    StorageScanSummary,
    StorageScanCells,
    StorageScanEmpty,
    StorageScanUtil,
    StorageScanUtilization,
    StorageScanBytesAll,
    StorageScanBytesExclSing,
    StorageScanMedian,
    StorageScanFragmentation,
    StorageScanLocked,
    StorageScanWasted,
    StorageScanSingularityExcluded,
    StorageScanExplainLocked,
    StorageScanExplainWasted,
    StorageScanExplainFragmented,
    StorageScanCellTypes,
    StorageScanCellTypesUtilization,
    StorageScanDuplicatePartitions,
    StorageScanLockedTo,
    StorageScanLocations,
    StorageScanMostFragmented,
    StorageScanDrive,
    StorageScanChest,

    // Storage Reshuffle GUI
    StorageReshuffle,
    ReshuffleStatusIdle,
    ReshuffleStatusRunning,
    ReshuffleTotalItems,
    ReshuffleReport,
    ReshuffleStart,
    ReshuffleCancel,
    ReshuffleScan,

    // Storage Reshuffle Report
    ReshuffleReportTitle,
    ReshuffleReportTime,
    ReshuffleReportMode,
    ReshuffleReportModeNone,
    ReshuffleReportVoidLabel,
    ReshuffleReportVoidOn,
    ReshuffleReportVoidOff,
    ReshuffleReportSectionProcessing,
    ReshuffleReportDone,
    ReshuffleReportSkip,
    ReshuffleReportSectionStorageTotals,
    ReshuffleReportLabelTypes,
    ReshuffleReportLabelStacks,
    ReshuffleReportSectionItemChanges,
    ReshuffleReportGainedLabel,
    ReshuffleReportLostLabel,
    ReshuffleReportUnchangedLabel,
    ReshuffleReportSectionTopLost,
    ReshuffleReportSectionTopGained,
    ReshuffleReportSectionSkipped,
    ReshuffleReportNetChanged,
    ReshuffleReportNetChangedReason,
    ReshuffleReportIntegrityOk,
    ReshuffleReportUnknown,
    ReshuffleReportNetworkNotActive,
    ReshuffleReportScanFailed,
    ReshuffleReportActiveCrafts,
    ReshuffleReportActiveCraftsDetail,

    ReshuffleTooltipDesc1,
    ReshuffleTooltipDesc2,
    ReshuffleTooltipDesc3,

    ReshuffleHelpTitle,
    ReshuffleHelpDesc1,
    ReshuffleHelpDesc2,
    ReshuffleHelpDesc3,
    ReshuffleTooltipStartTitle,
    ReshuffleTooltipStartDesc1,
    ReshuffleTooltipStartDesc2,
    ReshuffleTooltipCancelTitle,
    ReshuffleTooltipCancelDesc1,
    ReshuffleTooltipScanTitle,
    ReshuffleTooltipScanDesc1,
    ReshuffleTooltipScanDesc2,
    ReshuffleTooltipScanDesc3;

    private final String root;

    GuiText() {
        this.root = "gui.appliedenergistics2";
    }

    GuiText(final String r) {
        this.root = r;
    }

    public String getUnlocalized() {
        return this.root + '.' + this;
    }
}
