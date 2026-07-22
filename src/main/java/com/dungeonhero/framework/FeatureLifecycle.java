package com.dungeonhero.framework;

/** Lifecycle state of a registered gameplay feature. */
public enum FeatureLifecycle {
    REGISTERED,
    LOADED,
    STARTED,
    DISABLED,
    FAILED,
    STOPPED
}
