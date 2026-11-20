package dev.bleachsmp.awakening;

/** Evidence is capped per habit so repeating one action cannot unlock a form on its own. */
enum Playstyle {
    VANGUARD,
    MIRAGE,
    MARKSMAN,
    WAYFARER,
    ARTIFICER,
    KEEPER;

    // Append new habits at the end; the ordinal is used in saved evidence arrays.
    enum Habit {
        MELEE(1, 22, 2, VANGUARD, 3, KEEPER, 1),
        RUSH(2, 24, 4, VANGUARD, 4, WAYFARER, 1),
        AMBUSH(2, 24, 4, MIRAGE, 4, MARKSMAN, 1),
        RANGED(2, 24, 3, MARKSMAN, 4, MIRAGE, 1),
        GUARD(2, 24, 3, KEEPER, 4, VANGUARD, 1),
        DISCOVERY(2, 24, 4, WAYFARER, 4, MIRAGE, 2),
        MINING(1, 22, 2, ARTIFICER, 3, WAYFARER, 1),
        BUILDING(1, 18, 4, ARTIFICER, 4, KEEPER, 1),
        CRAFTING(2, 18, 6, ARTIFICER, 4, KEEPER, 1),
        ENCHANTING(4, 16, 8, MIRAGE, 4, ARTIFICER, 2),
        HARVEST(1, 22, 3, KEEPER, 4, ARTIFICER, 1),
        FISHING(3, 18, 4, WAYFARER, 3, MARKSMAN, 2),
        SUPPORT(4, 20, 12, KEEPER, 4, MIRAGE, 1),
        BOSS(8, 24, 0, VANGUARD, 2, MARKSMAN, 2);

        final int points;
        final int cap;
        final int intervalSeconds;
        private final Playstyle primary;
        private final int primaryWeight;
        private final Playstyle secondary;
        private final int secondaryWeight;

        Habit(int points, int cap, int intervalSeconds, Playstyle primary, int primaryWeight,
                Playstyle secondary, int secondaryWeight) {
            this.points = points;
            this.cap = cap;
            this.intervalSeconds = intervalSeconds;
            this.primary = primary;
            this.primaryWeight = primaryWeight;
            this.secondary = secondary;
            this.secondaryWeight = secondaryWeight;
        }

        int weight(Playstyle style) {
            if (style == primary) {
                return primaryWeight;
            }
            return style == secondary ? secondaryWeight : 0;
        }
    }

    static int total(int[] evidence) {
        int total = 0;
        for (int points : evidence) {
            total += points;
        }
        return total;
    }

    int affinity(int[] evidence) {
        int score = 0;
        for (Habit habit : Habit.values()) {
            int points = evidence[habit.ordinal()];
            int weight = habit.weight(this);
            if (points > 0 && weight > 0) {
                score += points * weight + 3; // A blend of relevant habits beats one repeated signal in close races.
            }
        }
        return score;
    }

    static Playstyle dominant(int[] evidence) {
        Playstyle best = VANGUARD;
        int bestScore = -1;
        for (Playstyle style : values()) {
            int score = style.affinity(evidence);
            if (score > bestScore) {
                best = style;
                bestScore = score;
            }
        }
        return best;
    }

    static Playstyle secondary(int[] evidence, Playstyle main) {
        Playstyle best = null;
        int bestScore = 0;
        for (Playstyle style : values()) {
            if (style == main) {
                continue;
            }
            int points = 0;
            for (Habit habit : Habit.values()) {
                if (habit.primary == style) {
                    points += evidence[habit.ordinal()];
                }
            }
            int score = style.affinity(evidence);
            // A secondary trait needs its own evidence, not just passive overlap with the main style.
            if (points >= 8 && score > bestScore) {
                best = style;
                bestScore = score;
            }
        }
        return best;
    }

    static boolean ready(int[] evidence, boolean advanced, int rank, int requiredRank) {
        return total(evidence) >= requiredEvidence(advanced) && (!advanced || rank <= requiredRank);
    }

    static int requiredEvidence(boolean advanced) {
        return advanced ? 80 : 36;
    }
}
