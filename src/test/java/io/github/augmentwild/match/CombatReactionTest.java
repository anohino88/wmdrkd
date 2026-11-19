package io.github.augmentwild.match;

import io.github.augmentwild.augment.*;
import io.github.augmentwild.augment.CombatReaction.Action;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatReactionTest {
    private final UUID owner = UUID.randomUUID(), target = UUID.randomUUID();

    @Test void kineticChargeIsSingleUseAndSurvivesNeitherExpiryNorDeath() {
        var charge = new CombatReaction();
        charge.charge(1, 4000, 16000);
        assertTrue(charge.spend(4000));
        assertFalse(charge.spend(4000));
        assertFalse(charge.ready(16000));
        assertTrue(charge.ready(16001));
        charge.charge(17000, 4000, 16000);
        assertFalse(charge.spend(21000));
        charge.clearTransient();
        assertEquals(12000, charge.remaining(21000));
    }

    @Test void oreWardCapsChargesAndNeverSpendsDuringLockout() {
        var ward = new CombatReaction();
        assertTrue(ward.store(2));
        assertTrue(ward.store(2));
        assertFalse(ward.store(2));
        assertTrue(ward.spendWard(0, 10000));
        assertFalse(ward.spendWard(9999, 10000));
        assertEquals(1, ward.charges());
        ward.clearTransient();
        assertFalse(ward.spendWard(10000, 10000));
    }

    @Test void strideUsesOnlyDistanceStillInsideTheRollingWindow() {
        var stride = new CombatReaction();
        assertFalse(stride.accumulate(owner, 0, 8000, 10, 24));
        assertFalse(stride.accumulate(owner, 7999, 8000, 13, 24));
        assertFalse(stride.accumulate(owner, 8001, 8000, 1, 24));
        assertTrue(stride.accumulate(owner, 8002, 8000, 10, 24));
        stride.charge(8002, 6000, 14000);
        assertFalse(stride.accumulate(owner, 8003, 8000, 24, 24));
    }

    @Test void ricochetIsBasedOnConfirmedDamageWithCapBeforeCriticalBudget() {
        assertEquals(2.8, ReactionRules.ricochet(8, .35, 4), 1e-8);
        assertEquals(4, ReactionRules.ricochet(40, .35, 4));
    }

    @Test void frostCountsThreeOnOneTargetAndResetsAfterExpiry() {
        var frost = new CombatReaction();
        assertFalse(frost.accumulate(owner, 0, 6000, 1, 3));
        assertFalse(frost.accumulate(target, 100, 6000, 1, 3));
        assertFalse(frost.accumulate(owner, 5999, 6000, 1, 3));
        assertTrue(frost.accumulate(owner, 6000, 6000, 1, 3));
        assertFalse(frost.accumulate(target, 6601, 6000, 2, 3));
    }

    @Test void delayedBlastAccumulatesDamageAcrossTargetsButNotAcrossDeath() {
        var blast = new CombatReaction();
        assertFalse(blast.accumulate(owner, 0, 8000, 11.9, 12));
        blast.clearTransient();
        assertFalse(blast.accumulate(owner, 10, 8000, 7, 12));
        assertTrue(blast.accumulate(owner, 8010, 8000, 5, 12));
    }

    @Test void reactorNeedsThreeDistinctActionsAndASeparateDischarge() {
        var reactor = new CombatReaction();
        assertFalse(reactor.action(Action.REACTION, 0, 8000));
        assertFalse(reactor.action(Action.REACTION, 1000, 8000));
        assertFalse(reactor.action(Action.MELEE, 6000, 8000));
        assertTrue(reactor.action(Action.PROJECTILE, 9000, 8000));
        reactor.charge(9000, 8000, 30000);
        assertFalse(reactor.action(Action.REACTION, 9000, 8000));
        assertTrue(reactor.spend(9001));
        assertFalse(reactor.spend(9001));
    }

    @Test void bastionEndsAtThreeSecondsEvenWhenClockJumps() {
        var fort = new CombatReaction();
        for (int i = 0; i < 3; i++) assertEquals(i == 2, fort.accumulate(owner, i * 3000, 6000, 1, 3));
        fort.charge(6000, 3000, 35000);
        assertTrue(fort.charged(8999));
        assertFalse(fort.charged(9010));
        assertFalse(fort.ready(9010));
    }

    @Test void reflexEmpowersOnlyOriginalMeleeEvenWithSplit() {
        var reflex = new CombatReaction();
        reflex.charge(0, 3000, 0);
        reflex.charge(1, 3000, 0);
        var damage = AugmentBalance.attackParts(10, 0, 10 * .15, 20, .5);
        assertEquals(6.5, damage.original());
        assertEquals(5, damage.repeat());
        assertTrue(reflex.spend(2999));
        assertFalse(reflex.spend(2999));
    }

    @Test void counterShieldAndKineticUseIndependentLedgers() {
        var progress = new AugmentProgress();
        var kinetic = new CombatReaction();
        assertTrue(progress.cooldown("counter_shield", 10, 12));
        kinetic.charge(10000, 4000, 16000);
        assertTrue(kinetic.spend(10001));
        assertEquals(12, progress.cooldownRemaining("counter_shield", 10));
    }

    @Test void bloodTraceRuptureNeedsMatchingLiveMark() {
        assertTrue(ReactionRules.marked(target, target, 4, 5));
        assertFalse(ReactionRules.marked(owner, target, 4, 5));
        assertFalse(ReactionRules.marked(target, target, 5, 5));
    }

    @Test void missileRepeatCountsConfirmedHitsButKeepsRootHitLimit() {
        var chain = new DamageChain(2, 3);
        var root = new DamageChain.Root();
        var missile = new CombatReaction();
        UUID match = UUID.randomUUID();
        chain.root(root, () -> {
            for (int i = 0; i < 3; i++) {
                assertTrue(chain.hit(match, owner, target, "magic_missile", 2, false, () -> true));
                assertEquals(i == 2, missile.accumulate(target, i * 2500, 5000, 1, 3));
            }
            assertFalse(chain.hit(match, owner, target, "magic_missile", 3, false, () -> fail("root cap")));
        });
    }

    @Test void swordWindChargeHasItsOwnExpiryAndCooldown() {
        var blade = new CombatReaction();
        blade.charge(0, 5000, 15000);
        assertFalse(blade.spend(5000));
        assertFalse(blade.ready(14999));
        assertTrue(blade.ready(15000));
    }

    @Test void predatorConvertsOnlyOverhealAndCapsAbsorption() {
        assertEquals(0, ReactionRules.overheal(10, 20, 5, .5, 2));
        assertEquals(1, ReactionRules.overheal(18, 20, 4, .5, 2));
        assertEquals(2, ReactionRules.overheal(20, 20, 8, .5, 2));
    }

    @Test void stormFieldCooldownDoesNotConsumeTheLightningCooldown() {
        var progress = new AugmentProgress();
        assertTrue(progress.cooldown("follow:storm_chaser", 2000, 12000));
        assertTrue(progress.ready("storm_chaser", 2));
        assertFalse(progress.cooldown("follow:storm_chaser", 13999, 12000));
    }

    @Test void frenzyOnlyGrantsSpeedOnTransitionToMaximum() {
        assertTrue(ReactionRules.reachedMaximum(4, 5, 5));
        assertFalse(ReactionRules.reachedMaximum(3, 4, 5));
        assertFalse(ReactionRules.reachedMaximum(5, 5, 5));
    }

    @Test void steelGrowthAndHeavyFistIgnoreOreCharges() {
        var permanent = new AugmentProgress();
        var ward = new CombatReaction();
        var balance = new AugmentBalance(20, 40, 1, .5, 6);
        permanent.addPermanent("steel_heart", 2, AugmentResonance.BASE, 100);
        ward.store(2);
        double maximum = 20 + balance.steelHealth(permanent.value("steel_heart"), 2);
        assertTrue(ward.spendWard(0, 10000));
        assertEquals(maximum * .08, balance.heavyFist(maximum, .08, true));
        assertEquals(2, permanent.value("steel_heart"));
        assertFalse(AugmentResonance.PERMANENT.contains("ore_ward"));
    }

    @Test void bladeFlowDoesNotMultiplyOnHydraAndSplitDescendants() {
        var blade = new CombatReaction();
        blade.charge(0, 5000, 15000);
        var parts = AugmentBalance.attackParts(8, 0, 2, 20, .5);
        assertTrue(blade.spend(20));
        assertEquals(6, parts.original());
        assertEquals(4, parts.repeat());
        assertFalse(blade.spend(21));
        assertFalse(blade.ready(21));
    }

    @Test void delayedBlastAndSplitShareTheOriginalPvpBudget() {
        var budget = new LinkedDamageBudget(6, true, target, 0);
        assertEquals(4, budget.offer(target, 4).allowed());
        assertEquals(2, budget.offer(target, 4).allowed());
        budget.refund(target, 2);
        assertEquals(2, budget.offer(target, 4).allowed());
    }

    @Test void ricochetCriticalStillUsesSecondaryVictimBudget() {
        double base = ReactionRules.ricochet(20, .35, 4);
        var budget = new LinkedDamageBudget(5, true, owner, 3);
        assertEquals(5, budget.offer(target, base * 2).allowed());
        assertEquals(2, budget.offer(owner, base * 2).allowed());
    }

    @Test void bastionAndLifeEchoKeepIndependentClocksAndPools() {
        var bastion = new CombatReaction();
        var echo = new ResonanceShield();
        bastion.charge(0, 3000, 35000);
        echo.grant(0, 4, 6_000_000_000L);
        assertTrue(bastion.charged(2999));
        double reduced = 10 * (1 - .20);
        double absorbed = echo.preview(2_999_000_000L, reduced);
        assertEquals(4, reduced - absorbed);
        echo.consume(2_999_000_000L, absorbed);
        assertEquals(0, echo.remaining(3_000_000_000L));
        assertFalse(bastion.charged(3000));
    }
}
