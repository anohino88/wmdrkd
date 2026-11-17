package io.github.augmentwild.domain;

/** Validated immutable snapshot; distance -1 means unlimited. */
public record MatchRules(long gatheringSeconds, long pvpStartSeconds, long respawnSeconds, int lives,
 int arenaSize, int maxArenaSize, int spawnDistance, double borderStart, double borderFinal,
 long shrinkSeconds, int trackerCost, double trackerMaxDistance, long selectionInterval,
 int choices, int silverWeight, int goldWeight, int prismWeight, int procDepth) {
    public MatchRules {
        if (gatheringSeconds<=0 || pvpStartSeconds<gatheringSeconds || respawnSeconds<0 || lives<1
            || maxArenaSize<1 || arenaSize<1 || arenaSize>maxArenaSize || spawnDistance<0
            || !Double.isFinite(borderStart) || !Double.isFinite(borderFinal) || borderFinal<=0 || borderStart<borderFinal || borderStart>arenaSize
            || shrinkSeconds<=0 || trackerCost<0 || !Double.isFinite(trackerMaxDistance) || (trackerMaxDistance<0 && trackerMaxDistance!=-1)
            || selectionInterval<=0 || choices<1 || silverWeight<0 || goldWeight<0 || prismWeight<0
            || (long)silverWeight+goldWeight+prismWeight<=0 || procDepth<0) throw new IllegalArgumentException("Invalid match rules");
        Math.addExact(pvpStartSeconds, shrinkSeconds);
    }
}
