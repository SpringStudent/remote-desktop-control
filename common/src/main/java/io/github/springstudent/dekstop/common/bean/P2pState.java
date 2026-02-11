package io.github.springstudent.dekstop.common.bean;

/**
 * Shared p2p lifecycle state.
 */
public enum P2pState {
    RELAY,
    P2P_NEGOTIATING,
    P2P_ACTIVE,
    RELAY_FALLBACK,
}
