package com.cookietps.model;

public record ScreenProfile(
    String id,
    ScreenType screenType,
    ViewMode viewMode,
    boolean enabled,
    RenderTransform firstPersonTransform,
    RenderTransform thirdPersonTransform,
    ThirdPersonSettings thirdPersonSettings,
    int columns,
    int rows,
    float slotSpacing,
    boolean showBackground,
    boolean showTooltips,
    boolean includePlayerInventory
) {
    public ScreenProfile withViewMode(ViewMode viewMode) {
        return new ScreenProfile(
            id,
            screenType,
            viewMode,
            enabled,
            firstPersonTransform,
            thirdPersonTransform,
            thirdPersonSettings,
            columns,
            rows,
            slotSpacing,
            showBackground,
            showTooltips,
            includePlayerInventory
        );
    }

    public ScreenProfile withEnabled(boolean enabled) {
        return new ScreenProfile(
            id,
            screenType,
            viewMode,
            enabled,
            firstPersonTransform,
            thirdPersonTransform,
            thirdPersonSettings,
            columns,
            rows,
            slotSpacing,
            showBackground,
            showTooltips,
            includePlayerInventory
        );
    }

    public ScreenProfile withFirstPersonTransform(RenderTransform firstPersonTransform) {
        return new ScreenProfile(
            id,
            screenType,
            viewMode,
            enabled,
            firstPersonTransform,
            thirdPersonTransform,
            thirdPersonSettings,
            columns,
            rows,
            slotSpacing,
            showBackground,
            showTooltips,
            includePlayerInventory
        );
    }

    public ScreenProfile withThirdPersonTransform(RenderTransform thirdPersonTransform) {
        return new ScreenProfile(
            id,
            screenType,
            viewMode,
            enabled,
            firstPersonTransform,
            thirdPersonTransform,
            thirdPersonSettings,
            columns,
            rows,
            slotSpacing,
            showBackground,
            showTooltips,
            includePlayerInventory
        );
    }

    public ScreenProfile withThirdPersonSettings(ThirdPersonSettings thirdPersonSettings) {
        return new ScreenProfile(
            id,
            screenType,
            viewMode,
            enabled,
            firstPersonTransform,
            thirdPersonTransform,
            thirdPersonSettings,
            columns,
            rows,
            slotSpacing,
            showBackground,
            showTooltips,
            includePlayerInventory
        );
    }

    public ScreenProfile withShowBackground(boolean showBackground) {
        return new ScreenProfile(
            id,
            screenType,
            viewMode,
            enabled,
            firstPersonTransform,
            thirdPersonTransform,
            thirdPersonSettings,
            columns,
            rows,
            slotSpacing,
            showBackground,
            showTooltips,
            includePlayerInventory
        );
    }

    public ScreenProfile withShowTooltips(boolean showTooltips) {
        return new ScreenProfile(
            id,
            screenType,
            viewMode,
            enabled,
            firstPersonTransform,
            thirdPersonTransform,
            thirdPersonSettings,
            columns,
            rows,
            slotSpacing,
            showBackground,
            showTooltips,
            includePlayerInventory
        );
    }

    public ScreenProfile withIncludePlayerInventory(boolean includePlayerInventory) {
        return new ScreenProfile(
            id,
            screenType,
            viewMode,
            enabled,
            firstPersonTransform,
            thirdPersonTransform,
            thirdPersonSettings,
            columns,
            rows,
            slotSpacing,
            showBackground,
            showTooltips,
            includePlayerInventory
        );
    }
}
