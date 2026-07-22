package com.dungeonhero.feature.armor;

/** Pure set-bonus calculations based only on currently equipped Hero Armor pieces. */
public final class ArmorSetBonusPolicy {

  public double damageReduction(int equippedPieces) {
    int safePieces = Math.max(0, equippedPieces);
    return safePieces >= 3 ? 0.05 : safePieces >= 2 ? 0.02 : 0;
  }

  public boolean hasLastStand(int equippedPieces) {
    return equippedPieces >= 4;
  }
}
