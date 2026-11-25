package com.cookietps.model;

public record PanelSlotLayout(
    int spatialIndex,
    int containerSlotIndex,
    boolean topInventory,
    boolean closeButton,
    float localX,
    float localY,
    float localZ
) {}
