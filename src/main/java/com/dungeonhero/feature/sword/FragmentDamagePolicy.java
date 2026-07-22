package com.dungeonhero.feature.sword;

/** Immutable fragment-cap rules; malformed values are normalized before they enter combat. */
public final class FragmentDamagePolicy {

    private static final double[] DEFAULT_CAPS = {0, 10, 20, 35, 55, 80, 110, 145, 185, 230, 280};
    private final double[] caps;
    private final double maximumStoredDamage;

    public FragmentDamagePolicy(double[] configuredCaps, double maximumStoredDamage) {
        this.caps = DEFAULT_CAPS.clone();
        if (configuredCaps != null) {
            for (int rank = 1; rank < Math.min(caps.length, configuredCaps.length); rank++) {
                if (Double.isFinite(configuredCaps[rank])) {
                    caps[rank] = Math.max(0, configuredCaps[rank]);
                }
            }
        }
        this.maximumStoredDamage = Math.max(280,
                Double.isFinite(maximumStoredDamage) ? maximumStoredDamage : 100000);
    }

    public static FragmentDamagePolicy defaults() {
        return new FragmentDamagePolicy(null, 100000);
    }

    public double cap(int rank) {
        int safeRank = Math.max(1, Math.min(caps.length - 1, rank));
        return caps[safeRank];
    }

    public double sanitizeTotal(double total) {
        return Math.max(0, Math.min(maximumStoredDamage, Double.isFinite(total) ? total : 0));
    }

    public double effective(double total, int rank) {
        return Math.min(sanitizeTotal(total), cap(rank));
    }

    public double overflow(double total, int rank) {
        return Math.max(0, sanitizeTotal(total) - effective(total, rank));
    }
}
