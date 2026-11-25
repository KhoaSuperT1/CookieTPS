package com.cookietps.model;

public enum ViewMode {
    AUTO,
    FIRST_PERSON,
    THIRD_PERSON,
    THIRD_PERSON_FRONT,
    DISABLED;

    public boolean isThirdPersonFamily() {
        return this == THIRD_PERSON || this == THIRD_PERSON_FRONT;
    }
}
