package io.github.augmentwild.paper;

import io.github.augmentwild.paper.CombatCinematics.Form;
import io.github.augmentwild.augment.AugmentBalance;
import io.github.augmentwild.augment.AugmentCombinations;
import io.github.augmentwild.augment.AugmentEvolution;
import io.github.augmentwild.augment.SkillSlots;
import io.github.augmentwild.augment.AugmentProgress;
import io.github.augmentwild.augment.AugmentResonance;
import io.github.augmentwild.augment.ResonanceShield;
import io.github.augmentwild.augment.SynergyDraftWeights;
import io.github.augmentwild.augment.AugmentRegistry;
import io.github.augmentwild.augment.DamageChain;
import io.github.augmentwild.augment.CombatAudit;
import io.github.augmentwild.augment.CombatReaction;
import io.github.augmentwild.augment.ReactionRules;
import io.github.augmentwild.augment.CombatReaction.Action;
import io.github.augmentwild.augment.CombatAudit.Metric;
import io.github.augmentwild.augment.LinkedDamageBudget;
import io.github.augmentwild.augment.NaturalOreTracker;
import io.github.augmentwild.augment.MovementProgress;
import io.github.augmentwild.augment.ReviveResolver;
import io.github.augmentwild.domain.MatchTelemetry;
import io.github.augmentwild.domain.MatchSimulationClock;
import io.github.augmentwild.match.MatchState;
import java.util.*;
import java.util.function.*;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import io.github.augmentwild.domain.HudMessages.Layer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

final class PaperOfficialCombat implements Listener {
    private static final Set<String> THOUSAND_SWORDS = AugmentCombinations.UNIQUE.get("thousand_swords");
    private static final Set<String> HELL_LORD = AugmentCombinations.UNIQUE.get("hell_lord");
    private static final Set<String> INFINITE_BARRAGE = AugmentCombinations.UNIQUE.get("infinite_barrage");
    private static final Set<String> STEEL_KING = AugmentCombinations.UNIQUE.get("steel_king");
    private static final Set<String> CALAMITY_TRAIN = AugmentCombinations.UNIQUE.get("calamity_train");
    private final JavaPlugin plugin;
    private final OfficialAugments catalog;
    private final ResonanceSettings resonance;
    private final int dinosaurMultiplier;
    private final Map<EntityDamageEvent, PaperResonanceShield.Receipt> shieldSpends = new WeakHashMap<>();
    private final Set<EntityDamageEvent> defended = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<EntityDamageEvent> countedWounds = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<PlayerMoveEvent> countedMovement = Collections.newSetFromMap(new WeakHashMap<>());
    private final Supplier<MatchSimulationClock> clock;
    private final Supplier<MatchState> phase;
    private final Predicate<Player> active, protectedPlayer;
    private final Predicate<UUID> member;
    private final Predicate<Location> arena, safe;
    private final BiPredicate<Player, Location> teleport;
    private final BiConsumer<UUID, Integer> protection;
    private final Function<UUID, Set<String>> owned;
    private final DamageChain chain;
    private final CombatAudit audit;
    private final boolean auditEnabled;
    private final ReactionPotions reactionPotions = new ReactionPotions();
    private final NaturalOreTracker ores;
    private final PaperErosion erosion;
    private final Map<UUID, Fighter> fighters = new HashMap<>();
    private final Map<EntityDamageByEntityEvent, Map<String, CombatReaction>> empowers = new WeakHashMap<>();
    private final Map<EntityDamageEvent, CombatReaction> reservedWards = new WeakHashMap<>();
    private final Map<EntityDamageEvent, Double> rawDamage = new WeakHashMap<>();
    private final Map<UUID, Integer> splitArrows = new HashMap<>();
    private final Deque<Deferred> deferred = new ArrayDeque<>();
    private BukkitTask deferredTask;
    private int expiredArrowsAt = Integer.MIN_VALUE;
    private final Map<EntityDamageEvent, Double> splitEvents = new WeakHashMap<>();
    private final Set<EntityDamageEvent> counted = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<EntityDeathEvent> countedKills = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<UUID> deadMobs = new HashSet<>();
    private Random random;
    private UUID match;
    private DamageChain.Root originalHit;
    private long budgetOffers, budgetOver, budgetHits, budgetReduced, budgetRoots;
    private double budgetRaw, budgetFinal;
    private boolean syntheticConfirmed;
    private double syntheticFinalDamage;

    private record Deferred(UUID match, Player player, DamageChain.Root origin, Runnable action) { }
    private record ArrowPath(Location position, org.bukkit.util.Vector velocity, double damage, int expires,
                             DamageChain.Root root) { }
    private record PoisonCloud(Location center, long expiresAt, Set<UUID> affected, DamageChain.Root root) { }
    private record Zone(String augment, Location start, Location end, long expiresAt, double damage) { }
    private record MissileShot(PaperMissiles.Missile missile, DamageChain.Root root) { }

    private static final class Fighter {
        final UUID owner;
        Fighter(UUID owner) { this.owner = owner; }

        final Map<String, CombatReaction> reactions = new HashMap<>();
        final List<Pending> pending = new ArrayList<>();
        int skillUses;
        final SkillSlots slots = new SkillSlots();
        final Set<String> growthNotices = new HashSet<>();
        String completingMember;
        final Set<String> coolingSlots = new HashSet<>();
        final Map<String,Integer> cooldownLengths = new HashMap<>();
        long hudSecond = -1, bastionVisualSecond = -1;
        boolean hudAvailable;
        final Map<String, Long> lastVisual = new HashMap<>();
        final AugmentProgress progress = new AugmentProgress();
        final ResonanceShield shield = new ResonanceShield();
        final Set<String> lateChecked = new HashSet<>();
        AugmentResonance.State resonance;
        double shieldShownPoints;
        long shieldShownSeconds;
        double overgrowthAward;
        long visibleGrowthStep = -1;
        boolean giantApplied;
        long acquiredGrowth, lastBerserkHit = -1, lastSecond = -1;
        final MovementProgress movement = new MovementProgress();
        double stormDistance;
        final Set<Material> food = EnumSet.noneOf(Material.class);
        long immortalUntil;
        final List<MissileShot> missiles = new ArrayList<>();
        final List<ArrowPath> arrows = new ArrayList<>();
        final List<PoisonCloud> poisonClouds = new ArrayList<>();
        long particleSecond = -1;
        int particles;
        boolean barrage, hellLord, thousandSwords;
        final PaperAugmentBurns burns = new PaperAugmentBurns();
        double burned, woundDamage;
        long naturalRegenBlockedUntil;
        UUID frenzyTarget;
        long frenzyAt;
        boolean instinctPending, veinGranted, transformedSmall;
        long acquiredMad, lastDemonUntil;
        long traceUntil;
        UUID traceTarget, bountyTarget;
        long bountyUntil;
        boolean counterReady;
        long shadowUntil, lastZoneSecond = -1, duelUntil;
        UUID duelTarget;
        Snowball bomb;
        int bombLaunchTick;
        final Deque<PaperAugmentMovement.History> history = new ArrayDeque<>();
        final List<Zone> zones = new ArrayList<>();
        final Map<UUID, Long> poisoned = new HashMap<>();
        int forcedAt;
        boolean hadVelocity, king, train, apple;
    }

    private final MatchTelemetry telemetry;
    private final PaperHud hud;
    private final PaperArt art;

    PaperOfficialCombat(JavaPlugin plugin, AugmentRegistry registry, Supplier<MatchSimulationClock> clock,
                        Supplier<MatchState> phase, Predicate<UUID> member, Predicate<Player> active, Predicate<Player> protectedPlayer,
                        Predicate<Location> arena, Predicate<Location> safe, BiPredicate<Player, Location> teleport,
                        BiConsumer<UUID, Integer> protection,
                        Function<UUID, Set<String>> owned, int depth, MatchTelemetry telemetry, PaperHud hud, PaperArt art) {
        this.plugin = plugin;
        this.telemetry = telemetry;
        this.hud = hud;
        this.art = art;
        this.clock = clock;
        this.phase = phase;
        this.member = member;
        this.active = active;
        this.protectedPlayer = protectedPlayer;
        this.arena = arena;
        this.safe = safe;
        this.teleport = teleport;
        this.protection = protection;
        this.owned = owned;
        catalog = new OfficialAugments(plugin, registry);
        registry.all().forEach(art::register);
        for (String id : catalog.rules.keySet()) {
            String path = path(id);
            art.configure(id,catalog.config.getBoolean(path + "particles",true),catalog.config.getBoolean(path + "sound",true));
        }
        resonance = ResonanceSettings.load(plugin);
        dinosaurMultiplier = AugmentResonance.multiplier(Math.clamp(rule("stack_dinosaur").amount(), 1, 10));
        ores = new NaturalOreTracker(catalog.count("ore-sites"));
        erosion = new PaperErosion(plugin, catalog.setting("erosion-max-damage"));
        auditEnabled = plugin.getConfig().getBoolean("dev.combat-audit.enabled");
        audit = new CombatAudit(plugin.getConfig().getInt("dev.combat-audit.max-events"), plugin.getLogger()::info);
        chain = new DamageChain(depth, catalog.count("max-hits-per-target"));
    }

    void start(UUID id, boolean experiment) {
        match = id;
        if (auditEnabled || experiment) audit.start(id);
        budgetOffers = budgetOver = budgetHits = budgetReduced = budgetRoots = 0;
        budgetRaw = budgetFinal = 0;
        random = new Random(id.getMostSignificantBits() ^ id.getLeastSignificantBits());
    }
    SynergyDraftWeights draftWeights() {
        return new SynergyDraftWeights(AugmentCombinations.UNIQUE.entrySet().stream()
                .filter(entry -> catalog.synergies.getBoolean(entry.getKey() + ".enabled"))
                .map(Map.Entry::getValue).toList(), resonance.oneWeight(), resonance.twoWeight());
    }

    void selected(UUID player, String id, String kind) {
        audit.add(id, Metric.SELECTED, 1);
        if (audit.sampleAvailable()) audit.sample(now(), "select actor=" + audit.actor(player) + " effect=" + id + " kind=" + kind);
    }

    private final Map<EntityDamageEvent, UUID> reviewOrigins = new WeakHashMap<>();
    private final Map<EntityDamageEvent, Double> reviewShields = new WeakHashMap<>();
    private final Set<EntityDamageEvent> reviewHeavyFist = Collections.newSetFromMap(new WeakHashMap<>());

    record DamageTrace(UUID origin, String augment, boolean synthetic, double shield, boolean critical) { }

    DamageTrace trace(EntityDamageEvent event) {
        double shield = reviewShields.getOrDefault(event, 0.0);
        var context = chain.current();
        var source = event.getDamageSource().getCausingEntity();
        if (context != null && source != null && context.attacker().equals(source.getUniqueId())
                && context.victim().equals(event.getEntity().getUniqueId())) {
            return new DamageTrace(context.original(), context.augment(), true, shield, context.critical());
        }
        return new DamageTrace(reviewOrigins.get(event), reviewHeavyFist.contains(event) ? "heavy_fist" : null, false, shield, false);
    }

    List<String> scoutingCombinations(UUID id) {
        var state = fighters.get(id);
        if (state == null) return List.of();
        var list = new ArrayList<String>();
        if (state.king) list.add(catalog.synergies.getString("steel_king.name"));
        if (state.train) list.add(catalog.synergies.getString("calamity_train.name"));
        if (state.barrage) list.add(catalog.synergies.getString("infinite_barrage.name"));
        if (state.hellLord) list.add(catalog.synergies.getString("hell_lord.name"));
        if (state.thousandSwords) list.add(catalog.synergies.getString("thousand_swords.name"));
        var families = resonance.families();
        if (state.resonance != null && state.resonance.familyMultiplier() > AugmentResonance.BASE) list.add(families.stacking().name());
        if (currentResonance(id, owned.apply(id)).shieldPoints() > 0) list.add(families.revival().name());
        return List.copyOf(list);
    }

    boolean synthetic() { return chain.current() != null; }
    double rawDamage(EntityDamageEvent event) { return rawDamage.getOrDefault(event, event.getDamage()); }
    private long now() { return clock.get().elapsedSeconds(); }
    private OfficialAugments.Rule rule(String id) { return catalog.rule(id); }
    private boolean owns(Player player, String id) { return owned.apply(player.getUniqueId()).contains(id); }
    private boolean valid(Player player) {
        return match != null && player.isOnline() && !player.isDead() && active.test(player) && !protectedPlayer.test(player) && !clock.get().isPaused();
    }
    private boolean opponent(Player player, LivingEntity target) {
        if (target.equals(player) || target.isDead() || !target.getWorld().equals(player.getWorld())) return false;
        if (target instanceof Player rival) return valid(rival);
        return target instanceof Enemy && arena.test(target.getLocation());
    }

    private Fighter fighter(UUID id) { return fighters.computeIfAbsent(id, Fighter::new); }

    void acquired(UUID id, String augment) {
        if (!catalog.rules.containsKey(augment) || !rule(augment).definition().implemented()) return;
        var state = fighter(id);
        if (ACTIVE_SKILLS.contains(augment)) {
            Player owner = plugin.getServer().getPlayer(id);
            if (owner != null && PlayerUi.enabled(owner, PlayerUi.Option.AUTO_BIND)) {
                int slot = state.slots.autoBind(augment);
                if (slot > 0) owner.sendMessage(rule(augment).definition().name() + " · " + slot + "번 슬롯 등록");
                else if (state.slots.at(1) != null && state.slots.at(2) != null && state.slots.at(3) != null)
                    owner.sendMessage(net.kyori.adventure.text.Component.text("스킬 슬롯이 가득 찼습니다 · [교체할 슬롯 선택]")
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/aw skill menu")));
                state.hudSecond = -1;
            }
        }
        if (augment.equals("overgrowth")) {
            state.acquiredGrowth = now();
            state.overgrowthAward = now() >= catalog.count("overgrowth-late-second")
                    ? catalog.setting("overgrowth-late-health") : rule(augment).amount();
        }
        if (augment.equals("mad_scientist")) state.acquiredMad = now();
        if (augment.equals("endless_evolution") && now() >= catalog.count("endless-evolution-late-second")) {
            state.progress.add(augment, catalog.count("endless-evolution-late-stacks"), rule(augment).cap());
        }
        if (resonance.referenceStacks().containsKey(augment) && state.lateChecked.add(augment)) {
            int adjustment = resonance.lateGrant(augment, now());
            if (resonance.lateEnabled() && adjustment > 0) {
                int old = state.progress.value(augment);
                int updated = state.progress.add(augment, adjustment, rule(augment).cap());
                Player player = plugin.getServer().getPlayer(id);
                if (player != null && augment.equals("steel_heart")) {
                    int threshold = catalog.count(AugmentEvolution.STEEL.goal);
                    if (old < threshold && updated >= threshold) evolution(player, state, augment);
                }
                if (player != null && augment.equals("tank_engine")) {
                    int threshold = catalog.count(AugmentEvolution.TANK.goal);
                    if (old < threshold && updated >= threshold) evolution(player, state, augment);
                }
            }
            if (resonance.lateLog()) plugin.getLogger().info("late-growth " + augment + " second=" + now()
                    + " proposed=" + adjustment + " enabled=" + resonance.lateEnabled());
        }
        state.completingMember = augment;
        try { refreshSynergies(id, state); }
        finally { state.completingMember = null; }
    }

    void lost(UUID id, String augment, boolean experiment) {
        var state = fighters.get(id);
        if (state == null) return;
        reactionPotions.clear(id, augment);
        state.reactions.remove(augment);
        if (augment.equals("overgrowth")) state.visibleGrowthStep = -1;
        if (augment.equals("gigantification")) state.giantApplied = false;
        state.pending.removeIf(task -> task.id().equals(augment));
        art.cancelAura(id,augment);
        state.slots.remove(augment);
        state.coolingSlots.remove(augment);
        state.cooldownLengths.remove(augment);
        state.hudSecond = -1;
        hud.remove(id, Layer.SKILL);
        state.progress.reset(augment);
        state.progress.reset("hits:" + augment);
        state.progress.reset("revive:" + augment);
        state.progress.resetCooldown(augment);
        state.progress.resetCooldown("follow:" + augment);
        if (experiment) {
            state.lateChecked.remove(augment);
            if (augment.equals("vein_overload")) state.veinGranted = false;
            if (augment.equals("mineral_devourer")) state.apple = false;
        }
        state.zones.removeIf(zone -> zone.augment().equals(augment));
        deferred.removeIf(effect -> effect.player().getUniqueId().equals(id));
        if (augment.equals("blood_god")) state.progress.reset("blood_god_kills");
        if (augment.equals("tank_engine")) state.movement.reset();
        switch (augment) {
            case "iron_stomach" -> state.food.clear();
            case "storm_chaser" -> state.stormDistance = 0;
            case "blood_trace" -> state.traceTarget = null;
            case "wound_record" -> state.woundDamage = 0;
            case "magic_missile" -> {
                state.progress.reset(AugmentEvolution.MISSILE.counter);
                state.missiles.clear();
            }
            case "sword_wind" -> state.progress.reset(AugmentEvolution.SWORD.counter);
            case "infernal_conductor" -> {
                state.burned = 0;
                state.burns.clear();
            }
            case "berserk_engine" -> {
                state.progress.reset(AugmentEvolution.BERSERK.counter);
                state.lastBerserkHit = -1;
            }
            case "center_of_universe" -> state.progress.resetCooldown("orbit");
            case "venom_swarm" -> {
                state.poisonClouds.clear();
                state.poisoned.clear();
            }
            case "split_bow" -> state.arrows.clear();
            case "bomb_alchemy" -> removeBomb(state);
            case "world_erosion" -> erosion.remove(id);
            case "time_rewind" -> state.history.clear();
            case "mad_scientist" -> state.transformedSmall = false;
            case "immortal_berserker" -> state.immortalUntil = 0;
            case "forbidden_reconstruction" -> state.naturalRegenBlockedUntil = 0;
            case "shadow_dash" -> state.shadowUntil = 0;
            case "duel_contract" -> state.duelTarget = null;
            case "bounty_hunter" -> state.bountyTarget = null;
            case "last_demon" -> state.lastDemonUntil = 0;
            case "frenzy_rhythm" -> state.frenzyTarget = null;
            case "counter_shield" -> state.counterReady = false;
            default -> { }
        }
        refreshSynergies(id, state);
    }

    void refreshAttributes(Player player) {
        var state = fighters.get(player.getUniqueId());
        if (state != null) applyAttributes(player, state, now());
    }

    long labCooldown(UUID player, String id) {
        var state = fighters.get(player);
        String key = id.equals("center_of_universe") ? "orbit" : id;
        return state == null ? 0 : state.progress.cooldownRemaining(key, now());
    }

    String labFightReport(UUID player) {
        var state = fighters.get(player);
        double shield = state == null ? 0 : state.shield.remaining(clock.get().elapsedNanos());
        return audit.actor(player) + " · 생명의 잔향 보호막 " + shield;
    }

    String labProgress(Player player, String id, int value) {
        var state = fighters.get(player.getUniqueId());
        if (state == null) return "진행도 설정을 지원하지 않는 증강입니다.";
        if (id.equals("infernal_conductor")) {
            boolean evolved = burnEvolved(state);
            state.burned = Math.min(value, catalog.setting(AugmentEvolution.BURN.goal));
            if (!evolved && burnEvolved(state)) evolution(player, state, id);
            return player.getName() + " 진행도 " + id + "=" + state.burned;
        }
        var evolution = AugmentEvolution.of(id);
        String key = evolution == null ? id : evolution.counter;
        int threshold = evolution == null ? 0 : catalog.count(evolution.goal);
        boolean permanent = AugmentResonance.PERMANENT.contains(id);
        if (!permanent && threshold == 0) return "진행도 설정을 지원하지 않는 증강입니다.";
        int before = state.progress.value(key);
        int after = state.progress.replace(key, value, permanent ? rule(id).cap() : threshold);
        if (threshold > 0 && before < threshold && after >= threshold) evolution(player, state, id);
        applyAttributes(player, state, now());
        return player.getName() + " 진행도 " + id + "=" + after;
    }

    int exchangeCount(String id) { return rule(id).threshold(); }
    int exchangeOptions() { return catalog.count("exchange-choice-options"); }

    void exchangeCommitted(Player player, String id) {
        var state = fighter(player.getUniqueId());
        if (id.equals("forbidden_reconstruction")) {
            player.setHealth(Math.max(.01, player.getHealth() * (1 - rule(id).amount())));
            state.naturalRegenBlockedUntil = now() + rule(id).duration();
        }
        applyAttributes(player, state, now());
        art.cue(player,id);
    }

    private void refreshSynergies(UUID id, Fighter state) {
        var previous = state.resonance;
        state.resonance = resonance.families().state(owned.apply(id), dinosaurMultiplier);
        int previousGrowth = previous == null ? AugmentResonance.BASE : previous.familyMultiplier();
        if (resonance.announce() && state.resonance.familyMultiplier() > previousGrowth) {
            announce(id, resonance.families().stacking().name() + " · 영구 중첩 획득량 +"
                    + percent(state.resonance.familyMultiplier() - AugmentResonance.BASE), resonance.sound());
            Player player = plugin.getServer().getPlayer(id);
            if (player != null && PlayerUi.enabled(player,PlayerUi.Option.GROWTH)) art.growth(player,"stacking",resonance.particles());
        }
        double previousShield = previous == null ? 0 : previous.shieldPoints();
        if (resonance.announce() && state.resonance.shieldPoints() > previousShield) {
            announce(id, resonance.families().revival().name() + " · 치명 피해 방지 시 보호막 "
                    + state.resonance.shieldPoints() / 2 + "하트", resonance.sound());
        }
        if (state.resonance.shieldPoints() == 0) state.shield.clear();
        boolean king = catalog.synergies.getBoolean("steel_king.enabled") && owned.apply(id).containsAll(STEEL_KING);
        if (king && !state.king) announceSynergy(id, "steel_king");
        state.king = king;
        boolean train = catalog.synergies.getBoolean("calamity_train.enabled")
                && owned.apply(id).containsAll(CALAMITY_TRAIN);
        if (train && !state.train) announceSynergy(id, "calamity_train");
        state.train = train;
        boolean barrage = catalog.synergies.getBoolean("infinite_barrage.enabled") && owned.apply(id).containsAll(INFINITE_BARRAGE);
        if (barrage && !state.barrage) announceSynergy(id, "infinite_barrage");
        state.barrage = barrage;
        boolean hellLord = catalog.synergies.getBoolean("hell_lord.enabled") && owned.apply(id).containsAll(HELL_LORD);
        if (hellLord && !state.hellLord) announceSynergy(id, "hell_lord");
        state.hellLord = hellLord;
        boolean swords = catalog.synergies.getBoolean("thousand_swords.enabled") && owned.apply(id).containsAll(THOUSAND_SWORDS);
        if (swords && !state.thousandSwords) announceSynergy(id, "thousand_swords");
        state.thousandSwords = swords;
    }

    void remove(UUID id, Player player) {
        reactionPotions.clear(id, null);
        deferred.removeIf(effect -> effect.player().getUniqueId().equals(id));
        if (player != null) resetAttributes(player);
        var state = fighters.remove(id);
        if (state != null) {
            removeBomb(state);
            state.shield.clear();
            hud.remove(id, Layer.SHIELD);
            hud.remove(id, Layer.SKILL);
        }
        erosion.remove(id);
    }

    void clear() {
        reactionPotions.clear(null, null);
        for (UUID id : fighters.keySet()) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) resetAttributes(player);
        }
        fighters.clear();
        ores.clear();
        erosion.clear();
        deadMobs.clear();
        rawDamage.clear();
        counted.clear();
        splitEvents.clear();
        splitArrows.clear();
        deferred.clear();
        if (deferredTask != null) {
            deferredTask.cancel();
            deferredTask = null;
        }
        countedKills.clear();
        audit.finish();
        if (match != null && resonance.measureBudget()) {
            plugin.getLogger().info("linked-pvp-damage roots=" + budgetRoots + " offers=" + budgetOffers
                    + " over=" + budgetOver + " limited=" + budgetReduced + " hits=" + budgetHits
                    + " requestedPoints=" + budgetRaw + " healthDamagePoints=" + budgetFinal);
        }
        empowers.clear();
        reservedWards.clear();
        wardSpent.clear();
        shieldSpends.clear();
        reviewOrigins.clear();
        reviewShields.clear();
        reviewHeavyFist.clear();
        defended.clear();
        countedWounds.clear();
        countedMovement.clear();
        match = null;
    }

    void disconnected(Player player) {
        Fighter state = fighters.get(player.getUniqueId());
        if (state != null) {
            clearReactions(player, state);
            state.shieldShownPoints = 0;
            hud.remove(player.getUniqueId(), Layer.SHIELD);
            state.missiles.clear();
            state.arrows.clear();
            state.poisonClouds.clear();
            state.burns.clear();
            state.history.clear();
            state.zones.clear();
            removeBomb(state);
        }
        erosion.remove(player.getUniqueId());
        resetAttributes(player);
    }

    int cooldown(Player player, int base) {
        if (base == 0 || !owns(player, "eureka")) return base;
        var rule = rule("eureka");
        return (int) Math.ceil(base * (1 - Math.min(.95, rule.amount()
                * Math.min(rule.cap(), player.getLevel() / rule.threshold()))));
    }

    List<String> candidateRelations(UUID player, String augment, Set<String> owned) {
        var lines = new ArrayList<String>();
        AugmentCombinations.UNIQUE.forEach((key, members) -> {
            if (!members.contains(augment) || !catalog.synergies.getBoolean(key + ".enabled")) return;
            long count = members.stream().filter(owned::contains).count();
            lines.add(catalog.synergies.getString(key + ".name") + (!owned.contains(augment) && count+1 == members.size()
                    ? " · 마지막 구성원" : " · " + count + "/" + members.size()));
        });
        var evolution = AugmentEvolution.of(augment);
        if (evolution != null) lines.add(evolutionLine(player,evolution));
        return lines;
    }

    List<String> details(UUID id, String augment) {
        if (!catalog.rules.containsKey(augment)) return List.of();
        var state = fighters.get(id);
        var rule = rule(augment);
        int stacks = state == null ? 0 : state.progress.value(augment);
        var lines = new ArrayList<String>();
        int cap = augment.equals("berserk_engine") && state != null
                && evolved(state, AugmentEvolution.BERSERK.counter, AugmentEvolution.BERSERK.goal)
                ? catalog.count("berserk-evolved-cap") : rule.cap();
        if (state != null) {
            lines.add("재사용 대기 " + labCooldown(id, augment) + "초");
            var r = state.reactions.get(augment);
            if (r != null) {
                if (augment.equals("ore_ward")) lines.add("방벽 충전 " + r.charges() + "/" + cap);
                if (r.charged(millis())) lines.add("충전 활성");
                String progress = reactionProgress(id, augment, r);
                if (progress != null) lines.add(progress);
                if (r.remaining(millis()) > 0) lines.add("반응 대기 " + (r.remaining(millis()) + 999) / 1000 + "초");
            }
            if (follow(augment)) {
                lines.add(catalog.config.getString("followups." + augment + ".description", ""));
                lines.add("추가 발동 대기 " + (state.progress.cooldownRemaining("follow:" + augment, millis()) + 999) / 1000 + "초");
            }
        }
        if (cap > 0 && !augment.equals("ore_ward") && !augment.equals("ricochet_arrow")) lines.add("중첩 " + stacks + "/" + cap);
        if (Set.of("immortal_berserker", "phoenix_heart", "russian_doll").contains(augment)) {
            int limit = augment.equals("immortal_berserker") ? rule.cap() : rule.threshold();
            int used = state == null ? 0 : state.progress.value("revive:" + augment);
            lines.add("치명 피해 방지 잔여 " + (limit - used) + "/" + limit);
        }
        var evolution = AugmentEvolution.of(augment);
        if (evolution != null) lines.add(evolutionLine(id, evolution));
        if (Set.of("tank_engine", "calamity_giant", "berserk_engine").contains(augment)) {
            lines.add("재앙의 기관차 · " + (state != null && state.train ? "활성" : "미활성"));
        }
        if (THOUSAND_SWORDS.contains(augment)) lines.add("천 개의 검 · " + (state != null && state.thousandSwords ? "활성" : "미활성"));
        if (HELL_LORD.contains(augment)) lines.add("지옥의 군주 · " + (state != null && state.hellLord ? "활성" : "미활성"));
        if (INFINITE_BARRAGE.contains(augment)) lines.add("무한 포격 · " + (state != null && state.barrage ? "활성" : "미활성"));
        if (STEEL_KING.contains(augment)) lines.add("강철의 왕 · " + (state != null && state.king ? "활성" : "미활성"));
        return lines;
    }

    private double evolutionThreshold(AugmentEvolution evolution) {
        return evolution == AugmentEvolution.BURN ? catalog.setting(evolution.goal) : catalog.count(evolution.goal);
    }

    private String evolutionLine(UUID id, AugmentEvolution evolution) {
        var state = fighters.get(id);
        boolean owned = ownsId(id, evolution.augment);
        double progress = state == null || !owned ? 0 : evolution.progress(state.progress, state.burned);
        double threshold = evolutionThreshold(evolution);
        String status = !owned ? "미보유" : evolution.complete(progress, threshold) ? "달성" : "진행 중";
        return String.format(Locale.ROOT, "%s %.1f/%.1f · %s", evolution.unit, Math.min(progress, threshold), threshold, status);
    }

    private boolean ownsId(UUID id, String augment) { return owned.apply(id).contains(augment); }

    List<PaperIntel.Card> codexStatus(UUID id) {
        var cards = new ArrayList<PaperIntel.Card>();
        Set<String> actual = owned.apply(id);
        for (var entry : new TreeMap<>(AugmentCombinations.UNIQUE).entrySet()) {
            String key = entry.getKey();
            long count = entry.getValue().stream().filter(actual::contains).count();
            var lore = new ArrayList<String>();
            String status = !catalog.synergies.getBoolean(key + ".enabled") ? "비활성화"
                    : count == entry.getValue().size() ? "활성" : "미완성";
            lore.add(count + "/" + entry.getValue().size() + " · " + status);
            for (String member : new TreeSet<>(entry.getValue())) {
                lore.add((actual.contains(member) ? "✓ " : "○ ") + rule(member).definition().name());
            }
            cards.add(PaperIntel.card(Material.NETHER_STAR, catalog.synergies.getString(key + ".name", key), lore.toArray(String[]::new)));
        }
        for (var evolution : AugmentEvolution.values()) {
            cards.add(PaperIntel.card(Material.EXPERIENCE_BOTTLE, rule(evolution.augment).definition().name() + " 진화",
                    evolutionLine(id, evolution)));
        }
        var families = resonance.families();
        var current = currentResonance(id, actual);
        var state = fighters.get(id);
        var timer = clock.get();
        double shield = state == null || timer == null ? 0 : state.shield.remaining(timer.elapsedNanos());
        cards.add(PaperIntel.card(Material.ANVIL, families.stacking().name(),
                families.enabled() && families.stacking().enabled() ? "보유 " + current.stackingCount() + "종 · 계열 "
                        + percent(current.familyMultiplier()) : "비활성화",
                "영구 중첩 획득 합계 " + percent(current.totalMultiplier())));
        cards.add(PaperIntel.card(Material.GOLDEN_APPLE, families.revival().name(),
                families.enabled() && families.revival().enabled() ? "인정되는 방지 증강 " + current.revivalCount()
                        + "종 · 발동 보호막 " + current.shieldPoints() / 2 + "하트" : "비활성화",
                "현재 보호막 " + shield / 2 + "하트 · 기본 지속 " + families.revival().durationSeconds() + "초"));
        return cards;
    }

    private static String percent(int scaled) {
        return String.format(Locale.ROOT, "%.2f%%", scaled / 100.0).replace(".00%", "%");
    }

    private AugmentResonance.State currentResonance(UUID id, Set<String> augments) {
        var state = fighters.get(id);
        if (resonance.includeSpent() || state == null) return resonance.families().state(augments, dinosaurMultiplier);
        var available = new HashSet<>(augments);
        for (String revive : AugmentResonance.DEATH_PREVENTION) {
            if (state.progress.value("revive:" + revive) >= reviveLimit(revive)) available.remove(revive);
        }
        return resonance.families().state(available, dinosaurMultiplier);
    }

    private int reviveLimit(String id) {
        return id.equals("immortal_berserker") ? rule(id).cap() : rule(id).threshold();
    }

    List<String> resonanceDetails(UUID id, String candidate) {
        var families = resonance.families();
        if (!families.enabled()) return List.of();
        Set<String> actual = owned.apply(id);
        var before = currentResonance(id, actual);
        var offered = new HashSet<>(actual);
        offered.add(candidate);
        var after = currentResonance(id, offered);
        if (families.stacking().enabled() && families.stacking().members().contains(candidate)) {
            var lines = new ArrayList<String>();
            lines.add("[영구 중첩형] " + families.stacking().name() + " · 보유 " + before.stackingCount() + "종");
            lines.add("획득 " + percent(before.familyMultiplier()) + (before.familyMultiplier() == after.familyMultiplier()
                    ? "" : " → " + percent(after.familyMultiplier())));
            if (actual.contains("stack_dinosaur")) lines.add("중첩 공룡 합계 " + percent(before.totalMultiplier())
                    + (before.totalMultiplier() == after.totalMultiplier() ? "" : " → " + percent(after.totalMultiplier())));
            return lines;
        }
        if (candidate.equals("stack_dinosaur") && before.stackingCount() > 0
                && before.totalMultiplier() != after.totalMultiplier()) {
            return List.of("영구 중첩 총 획득 " + percent(before.totalMultiplier()) + " → " + percent(after.totalMultiplier()));
        }
        if (families.revival().enabled() && families.revival().members().contains(candidate)) {
            return List.of("[사망 방지형] " + families.revival().name() + " · 보유 " + before.revivalCount() + "종",
                    "치명 피해 방지 시 " + before.shieldPoints() / 2 + (before.shieldPoints() == after.shieldPoints()
                    ? "" : " → " + after.shieldPoints() / 2) + "하트 · " + families.revival().durationSeconds() + "초");
        }
        return List.of();
    }

    private void updateShieldDisplay(Player player, Fighter state) {
        double points = state.shield.remaining(clock.get().elapsedNanos());
        UUID id = player.getUniqueId();
        if (points <= 0) {
            if (state.shieldShownPoints > 0) {
                hud.proc(player, "생명의 잔향 보호막 종료");
                if (active.test(player)) art.ring(player,player.getLocation().add(0,.8,0),.85,Particle.END_ROD,true);
            }
            hud.remove(id, Layer.SHIELD);
            state.shieldShownPoints = 0;
        } else {
            long seconds = Math.ceilDiv(state.shield.timeLeft(clock.get().elapsedNanos()),1_000_000_000L);
            if (points == state.shieldShownPoints && seconds == state.shieldShownSeconds) return;
            if (state.shieldShownPoints != points)
                art.ring(player,player.getLocation().add(0,.8,0),.65,Particle.ENCHANTED_HIT,false);
            state.shieldShownSeconds = seconds;
            state.shieldShownPoints = points;
            hud.set(id, Layer.SHIELD, net.kyori.adventure.text.Component.text(resonance.families().revival().name()
                    + " · " + String.format(Locale.ROOT, "%.1f", points / 2) + "하트 · " + seconds + "초"));
        }
    }

    private boolean evolved(Fighter state, String id, String key) {
        return state.progress.value(id) >= catalog.count(key);
    }

    private int grant(Fighter state, String id, int raw) {
        return grant(state, id, id, raw, rule(id).cap());
    }

    private int grant(Fighter state, String id, String key, int raw, int cap) {
        var settings = resonance.families().stacking();
        int rate = state.resonance.totalMultiplier();
        // Dinosaur still applies to permanent augments whose family membership was disabled.
        if (!settings.members().contains(id)) rate = state.resonance.dinosaurOnlyMultiplier();
        int previous = state.progress.value(key);
        int updated = state.progress.addPermanent(key, raw, Math.max(AugmentResonance.BASE, rate), cap);
        audit.add(id, Metric.STACKS, updated - previous);
        if (updated > previous) {
            if (raw > 0 && state.resonance.dinosaurOnlyMultiplier() > AugmentResonance.BASE)
                art.cue(state.owner,"stack_dinosaur");
            art.cue(state.owner,id);
        }
        return updated;
    }

    private double maxHealth(LivingEntity entity) {
        var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? entity.getHealth() : attribute.getValue();
    }

    private void gainHealth(Player player, double amount, String effect) {
        double previous = player.getHealth();
        player.setHealth(Math.min(maxHealth(player), previous + amount));
        hud.heal(player,previous,player.getHealth());
        audit.add(effect, Metric.HEALED, player.getHealth() - previous);
        if (player.getHealth() > previous) art.cue(player,effect);
    }

    private void modifier(Player player, Attribute attribute, String key, double amount, Operation operation) {
        var instance = player.getAttribute(attribute);
        if (instance != null) PaperAugmentAttribute.update(instance, new NamespacedKey(plugin, key), amount, operation, amount != 0);
    }

    private void resetAttributes(Player player) {
        double before = maxHealth(player);
        modifier(player, Attribute.MAX_HEALTH, "official_health", 0, Operation.ADD_NUMBER);
        modifier(player, Attribute.MAX_HEALTH, "official_doll_health", 0, Operation.MULTIPLY_SCALAR_1);
        modifier(player, Attribute.MOVEMENT_SPEED, "official_speed", 0, Operation.ADD_SCALAR);
        modifier(player, Attribute.SCALE, "official_scale", 0, Operation.MULTIPLY_SCALAR_1);
        modifier(player, Attribute.ENTITY_INTERACTION_RANGE, "official_reach", 0, Operation.ADD_NUMBER);
        double after = maxHealth(player);
        if (!player.isDead() && before != after && player.getHealth() > after) player.setHealth(after);
    }

    private void clearReactions(Player player, Fighter state) {
        reactionPotions.clear(player.getUniqueId(), null);
        state.pending.clear();
        state.hudSecond = -1;
        state.reactions.values().forEach(CombatReaction::clearTransient);
        state.progress.reset("hits:ore_ward");
        deferred.removeIf(task -> task.player().equals(player));
    }

    void sync(Player player) {
        Fighter state = fighters.get(player.getUniqueId());
        if (state == null) return;
        updateSkillHud(player, state);
        if (!active.test(player)) {
            clearReactions(player, state);
            state.shield.clear();
            updateShieldDisplay(player, state);
            resetAttributes(player);
            state.progress.reset("berserk_engine");
            state.lastBerserkHit = -1;
            state.lastSecond = -1;
            state.giantApplied = false;
            state.immortalUntil = 0;
            state.progress.reset("frenzy_rhythm");
            state.frenzyTarget = null;
            state.instinctPending = false;
            state.lastDemonUntil = 0;
            state.traceTarget = null;
            state.bountyTarget = null;
            state.poisoned.clear();
            state.counterReady = false;
            state.shadowUntil = 0;
            state.history.clear();
            art.forget(player.getUniqueId());
            state.zones.clear();
            state.missiles.clear();
            state.arrows.clear();
            state.poisonClouds.clear();
            state.burns.clear();
            return;
        }
        long second = now();
        updateShieldDisplay(player, state);
        art.aura(player,"shadow_dash",second,state.shadowUntil-second);
        art.aura(player,"duel_contract",second,state.duelTarget == null ? 0 : state.duelUntil-second);
        art.aura(player,"immortal_berserker",second,state.immortalUntil-second);
        art.aura(player,"last_demon",second,state.lastDemonUntil-second);
        erosion.tick(second);
        int tick = Bukkit.getCurrentTick();
        if (expiredArrowsAt != tick) {
            expiredArrowsAt = tick;
            splitArrows.values().removeIf(expires -> tick - expires >= 0);
        }
        recordTopSpeed(player, state, second);
        boolean transform = owns(player, "mad_scientist") && small(state, second) != state.transformedSmall;
        double healthFraction = transform ? player.getHealth() / maxHealth(player) : 0;
        applyAttributes(player, state, second);
        if (owns(player,"gigantification") && !state.giantApplied) {
            state.giantApplied = true;
            art.cue(player,"gigantification");
        }
        if (owns(player,"overgrowth")) {
            long step = (second - state.acquiredGrowth) / rule("overgrowth").threshold();
            if (step != state.visibleGrowthStep) {
                state.visibleGrowthStep = step;
                art.cue(player,"overgrowth");
            }
        }
        if (transform) {
            state.transformedSmall = small(state, second);
            player.setHealth(Math.clamp(healthFraction * maxHealth(player), .01, maxHealth(player)));
            art.cue(player,"mad_scientist");
        }
        if (valid(player)) {
            tickReactions(player, state);
            advanceMissiles(player, state);
            advanceSplitArrows(player, state);
            tickPoisonClouds(player, state);
            tickBurns(player, state, second);
            tickZones(player, state, second);
            remember(player, state, second);
            worldErosion(player, state);
            if (owns(player,"calamity_giant") && player.isSprinting() && player.isOnGround() && tick % 12 == 0)
                art.spark(player,player.getLocation(),Particle.CLOUD);
            if (state.bomb != null && state.bomb.isValid())
                art.sustain(player,"bomb_alchemy",state.bomb.getLocation(),true);
            for (var zone : state.zones)
                if (zone.augment().equals("void_lord") && second < zone.expiresAt() && safe.test(zone.start()))
                    art.sustain(player,"void_lord",zone.start(),false);
            if (state.bomb != null && state.bomb.isValid() && tick % 3 == 0)
                art.spark(player,state.bomb.getLocation(),Particle.WITCH);
            if (state.duelUntil > now() && tick % 10 == 0 && state.duelTarget != null) {
                var rival = plugin.getServer().getPlayer(state.duelTarget);
                if (rival != null && opponent(player,rival) && player.canSee(rival) && player.hasLineOfSight(rival))
                    art.link(player,player.getEyeLocation(),rival.getEyeLocation(),Particle.ENCHANTED_HIT);
            }
            if (state.bomb != null && Bukkit.getCurrentTick() - state.bombLaunchTick >= catalog.count("bomb-lifetime-ticks")) removeBomb(state);
            if (state.instinctPending) {
                state.instinctPending = false;
                reactAtLowHealth(player, state);
            }
            if (owns(player, "center_of_universe")) orbit(player, state, second);
        }
        if (!state.veinGranted && owns(player, "vein_overload")
                && (phase.get() == MatchState.PVP || phase.get() == MatchState.SUDDEN_DEATH)) {
            state.veinGranted = true;
            grantAbsorption(player, "vein-absorption");
            art.cue(player,"vein_overload");
        }
        if (!state.apple && owns(player, "mineral_devourer")
                && (phase.get() == MatchState.PVP || phase.get() == MatchState.SUDDEN_DEATH)
                && player.getInventory().firstEmpty() >= 0) {
            state.apple = player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE)).isEmpty();
            if (state.apple) art.cue(player,"mineral_devourer");
        }
    }

    private void recordTopSpeed(Player player, Fighter state, long second) {
        long previous = state.lastSecond;
        state.lastSecond = second;
        if (state.lastBerserkHit < 0) return;
        var rule = rule("berserk_engine");
        long expires = state.lastBerserkHit + rule.duration();
        int limit = catalog.count(AugmentEvolution.BERSERK.goal);
        if (previous >= 0 && state.progress.value("berserk_engine") >= rule.cap()) {
            int credited = (int) Math.clamp(Math.min(second, expires) - previous, 0, limit);
            int past = state.progress.value(AugmentEvolution.BERSERK.counter);
            if (credited > 0 && past < limit && state.progress.add(AugmentEvolution.BERSERK.counter, credited, limit) == limit) {
                evolution(player, state, "berserk_engine");
            }
        }
        if (second >= expires) {
            state.progress.reset("berserk_engine");
            state.lastBerserkHit = -1;
        }
    }

    private boolean small(Fighter state, long second) {
        return (second - state.acquiredMad) / rule("mad_scientist").duration() % 2 == 1;
    }

    private void applyAttributes(Player player, Fighter state, long second) {
        var ids = owned.apply(player.getUniqueId());
        var progress = state.progress;
        double health = 0, speed = 0, scale = 0;
        for (String id : List.of("blood_record", "iron_stomach", "wound_record", "heart_harvester", "endless_evolution")) {
            if (ids.contains(id)) health += progress.value(id) * rule(id).amount();
        }
        if (ids.contains("steel_heart")) health += resonance.balance().steelHealth(progress.value("steel_heart"), rule("steel_heart").amount());
        if (ids.contains("forbidden_deal")) health -= catalog.setting("forbidden-deal-health-cost");
        if (ids.contains("blood_god")) health += progress.value("blood_god_kills") * catalog.setting("blood-god-kill-health");
        if (ids.contains("overgrowth")) {
            health += state.overgrowthAward + (second - state.acquiredGrowth) / rule("overgrowth").threshold()
                    * catalog.setting("overgrowth-growth");
        }
        if (ids.contains("berserk_engine")) speed += progress.value("berserk_engine") * rule("berserk_engine").amount();
        if (ids.contains("gigantification")) {
            health += rule("gigantification").amount();
            speed += catalog.setting("gigantification-speed");
            scale += catalog.setting("gigantification-scale");
        }
        if (ids.contains("vein_overload")) speed += progress.value("vein_overload") * rule("vein_overload").amount();
        if (ids.contains("mad_scientist")) {
            if (small(state, second)) {
                speed += catalog.setting("mad-small-speed");
                scale += catalog.setting("mad-small-scale");
            } else {
                health += rule("mad_scientist").amount();
                scale += catalog.setting("mad-giant-scale");
            }
        }
        if (ids.contains("tank_engine")) {
            health += progress.value("tank_engine") * rule("tank_engine").amount();
            scale += progress.value("tank_engine") * catalog.setting("tank-scale");
        }
        if (ids.contains("immortal_berserker") && state.immortalUntil > second) {
            speed += catalog.setting("immortal-berserker-speed");
        }
        if (ids.contains("calamity_giant")) {
            health += rule("calamity_giant").amount();
            scale += catalog.setting("calamity-scale");
            speed += catalog.setting("calamity-speed");
        }
        modifier(player, Attribute.ENTITY_INTERACTION_RANGE, "official_reach",
                ids.contains("calamity_giant") ? catalog.setting("calamity-reach") : 0, Operation.ADD_NUMBER);
        int dolls = progress.value("revive:russian_doll");
        if (ids.contains("russian_doll")) speed += dolls * rule("russian_doll").amount();
        modifier(player, Attribute.MAX_HEALTH, "official_health", Math.clamp(health, -catalog.setting("forbidden-deal-health-cost"), 400), Operation.ADD_NUMBER);
        double dollFactor = ids.contains("russian_doll") ? Math.pow(1 - rule("russian_doll").amount(), dolls) - 1 : 0;
        modifier(player, Attribute.MAX_HEALTH, "official_doll_health", dollFactor, Operation.MULTIPLY_SCALAR_1);
        modifier(player, Attribute.MOVEMENT_SPEED, "official_speed", Math.clamp(speed, -.5, 2), Operation.ADD_SCALAR);
        modifier(player, Attribute.SCALE, "official_scale", Math.clamp(scale, -.75, 3), Operation.MULTIPLY_SCALAR_1);
        if (player.getHealth() > maxHealth(player)) player.setHealth(maxHealth(player));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void increaseDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Snowball bomb && trackedBomb(bomb)) {
            event.setCancelled(true);
            return;
        }
        if (synthetic() || !(event.getEntity() instanceof LivingEntity target)) return;
        Player attacker = attacker(event);
        if (attacker == null || !valid(attacker) || !opponent(attacker, target) || event.getDamage() <= 0) return;
        double base = event.getDamage();
        rawDamage.put(event, base);
        Fighter state = fighters.get(attacker.getUniqueId());
        if (state == null) return;
        boolean melee = event.getDamager().equals(attacker);
        var ids = owned.apply(attacker.getUniqueId());
        double ratio = ids.contains("immortal_berserker") && state.immortalUntil > now()
                ? rule("immortal_berserker").amount() : 0;
        if (ids.contains("mineral_addiction")) ratio += state.progress.value("mineral_addiction") * rule("mineral_addiction").amount();
        if (ids.contains("forbidden_deal")) ratio += rule("forbidden_deal").amount();
        if (state.duelUntil > now() && target.getUniqueId().equals(state.duelTarget)) ratio += rule("duel_contract").amount();
        if (ids.contains("shadow_dash") && state.shadowUntil > now()) ratio += rule("shadow_dash").amount();
        if (melee && state.counterReady && ids.contains("counter_shield")) ratio += rule("counter_shield").amount();
        if (ids.contains("bounty_hunter") && state.bountyUntil > now() && target.getUniqueId().equals(state.bountyTarget)) {
            ratio += rule("bounty_hunter").amount();
        }
        if (event.getDamager() instanceof AbstractArrow arrow && splitArrows.containsKey(arrow.getUniqueId())) {
            ratio += rule("split_bow").amount() - 1;
        }
        if (state.lastDemonUntil > now() && ids.contains("last_demon")) ratio += rule("last_demon").amount();
        if (ids.contains("blood_trace") && state.traceUntil > now() && target.getUniqueId().equals(state.traceTarget)) {
            ratio += rule("blood_trace").amount();
        }
        if (ids.contains("blood_god")) ratio += state.progress.value("blood_god") * catalog.setting("blood-god-bonus-damage");
        if (ids.contains("endless_evolution")) ratio += state.progress.value("endless_evolution") * catalog.setting("endless-evolution-damage");
        if (ids.contains("frenzy_rhythm") && target.getUniqueId().equals(state.frenzyTarget)
                && now() - state.frenzyAt < rule("frenzy_rhythm").duration()) {
            ratio += state.progress.value("frenzy_rhythm") * rule("frenzy_rhythm").amount();
        }
        if (ids.contains("giant_slayer")) ratio += Math.clamp((maxHealth(target) / maxHealth(attacker) - 1)
                * catalog.setting("giant-relative-scaling"), 0, rule("giant_slayer").amount());
        if (melee) {
            for (String id : List.of("muscle_memory", "mineral_devourer")) {
                if (ids.contains(id)) ratio += state.progress.value(id) * rule(id).amount();
            }
            if (ids.contains("gigantification")) ratio += catalog.setting("gigantification-damage");
        }
        boolean split = ids.contains("split_attack") && state.progress.value("split_charge") > 0;
        double flat = melee && ids.contains("heavy_fist") ? resonance.balance().heavyFist(maxHealth(attacker), rule("heavy_fist").amount(), target instanceof Player) : 0;
        boolean directMelee = melee && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK;
        boolean projectile = event.getDamager() instanceof Projectile && event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE;
        boolean chargedBonus = false;
        if (directMelee || projectile) {
            for (String id : List.of("reflex", "sword_wind", "afterimage_stride", "kinetic_reversal", "triad_reactor")) {
                var r = state.reactions.get(id);
                if (r == null || !r.charged(millis()) || !directMelee && !id.equals("triad_reactor")) continue;
                if (!r.reserve(event)) continue;
                empowers.computeIfAbsent(event, ignored -> new LinkedHashMap<>()).put(id, r);
                chargedBonus = true;
                if (id.equals("reflex")) flat += base * option(id, "ratio");
                else flat += id.equals("sword_wind") ? option(id, "damage") : rule(id).amount();
            }
        }
        if (ratio > 0 || flat > 0 || split) {
            var damage = AugmentBalance.attackParts(base, base * ratio, flat, catalog.setting("max-extra-damage"),
                    split ? rule("split_attack").amount() : 1);
            if (split) splitEvents.put(event, damage.repeat());
            if (damage.original() > damage.repeat() && ids.contains("heavy_fist") && !chargedBonus) reviewHeavyFist.add(event);
            event.setDamage(damage.original());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void hit(EntityDamageByEntityEvent event) {
        try {
            if (!event.isCancelled()) confirmedHit(event);
        } finally {
            var reserved = empowers.remove(event);
            if (reserved != null) reserved.values().forEach(charge -> charge.release(event));
        }
    }

    private void confirmedHit(EntityDamageByEntityEvent event) {
        if (synthetic()) {
            var context = chain.current();
            var cause = event.getDamageSource().getCausingEntity();
            if (cause != null && context.attacker().equals(cause.getUniqueId())
                    && context.victim().equals(event.getEntity().getUniqueId()) && event.getFinalDamage() > 0) {
                syntheticConfirmed = true;
                syntheticFinalDamage = Math.min(((LivingEntity) event.getEntity()).getHealth(), event.getFinalDamage());
            }
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target) || event.getFinalDamage() <= 0) return;
        Player attacker = attacker(event);
        if (attacker == null || !valid(attacker) || !opponent(attacker, target) || !counted.add(event)) return;
        Fighter state = fighters.get(attacker.getUniqueId());
        if (state == null) return;
        double base = rawDamage(event);
        if (!Double.isFinite(base) || !Double.isFinite(event.getDamage()) || !Double.isFinite(event.getFinalDamage())) return;
        var attack = event.getDamager().equals(attacker) ? DamageChain.Attack.MELEE : DamageChain.Attack.PROJECTILE;
        var origin = new DamageChain.Origin(attack, base, event.getDamage() - base,
                Math.max(0, event.getDamage() - event.getFinalDamage()));
        DamageChain.Root previous = originalHit;
        LinkedDamageBudget budget = null;
        if (target instanceof Player && (resonance.measureBudget() || resonance.enforceBudget())) {
            budgetRoots++;
            budget = new LinkedDamageBudget(Math.max(resonance.minimumDamage(), base * resonance.damageRatio()),
                    resonance.enforceBudget(), target.getUniqueId(), Math.max(0, origin.extraDamage()));
        }
        originalHit = new DamageChain.Root(origin, budget);
        reviewOrigins.put(event, originalHit.id());
        if (origin.extraDamage() > 0 && owns(attacker,"giant_slayer") && maxHealth(target) > maxHealth(attacker)
                && rule("giant_slayer").amount() > 0 && catalog.setting("giant-relative-scaling") > 0)
            art.cue(attacker,"giant_slayer");
        if (reviewHeavyFist.contains(event)) announceProc(attacker, "heavy_fist");
        if (audit.sampleAvailable()) audit.sample(now(), "root=" + originalHit.id()
                + " attacker=" + audit.actor(attacker.getUniqueId())
                + " victim=" + (target instanceof Player ? audit.actor(target.getUniqueId()) : target.getType())
                + " base=" + base + " observed_bonus=" + origin.extraDamage() + " mitigation=" + origin.mitigation()
                + " final_event_damage=" + event.getFinalDamage() + " health_before=" + target.getHealth()
                + " owned=" + owned.apply(attacker.getUniqueId()).stream().sorted().toList());
        try {
            attackReactions(event, attacker, target, state);
            applyHitEffects(event, attacker, target, state);
        } finally {
            originalHit = previous;
        }
    }

    private void applyHitEffects(EntityDamageByEntityEvent event, Player attacker, LivingEntity target, Fighter state) {
        boolean direct = event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE;
        if (owns(attacker, "frenzy_rhythm")) {
            if (!target.getUniqueId().equals(state.frenzyTarget) || now() - state.frenzyAt >= rule("frenzy_rhythm").duration()) {
                state.progress.reset("frenzy_rhythm");
            }
            state.frenzyTarget = target.getUniqueId();
            state.frenzyAt = now();
            int cap = rule("frenzy_rhythm").cap();
            int before = state.progress.value("frenzy_rhythm");
            if (ReactionRules.reachedMaximum(before, state.progress.add("frenzy_rhythm", 1, cap), cap) && direct && followReady(state, "frenzy_rhythm")) {
                speed(attacker, "frenzy_rhythm");
                visual(attacker, state, "frenzy_rhythm", attacker.getLocation(), Particle.CLOUD, Sound.BLOCK_AMETHYST_BLOCK_CHIME);
                announceProc(attacker, "frenzy_rhythm");
            }
        }
        if (state.shadowUntil > now()) state.shadowUntil = 0;
        if (owns(attacker, "bounty_hunter") && ready(attacker, state, "bounty_hunter")) {
            state.bountyTarget = target.getUniqueId();
            state.bountyUntil = now() + rule("bounty_hunter").duration();
            art.cue(attacker,"bounty_hunter");
        }
        if (owns(attacker, "time_thief") && ready(attacker, state, "time_thief")) stealTime(attacker, target);
        if (owns(attacker, "blood_trace")) {
            if (ReactionRules.marked(target.getUniqueId(), state.traceTarget, now(), state.traceUntil)) {
                if (direct && followReady(state, "blood_trace")) later(state, "blood_trace", interval("blood_trace", "delay"),
                        () -> afterRecovery(attacker, target, () -> followHit(attacker, target, state, "blood_trace")));
                state.traceTarget = null;
            } else if (ready(attacker, state, "blood_trace")) {
                state.traceTarget = target.getUniqueId();
                state.traceUntil = now() + rule("blood_trace").duration();
                target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, rule("blood_trace").duration() * 20, 0));
                art.cue(attacker,"blood_trace");
            }
        }
        if (owns(attacker, "blood_god") && target instanceof Player && every(state, "blood_god")) {
            grant(state, "blood_god", 1);
        }
        if (owns(attacker, "immortal_berserker")) {
            if (state.immortalUntil > now()) {
                double heal = Math.min(target.getHealth(), event.getFinalDamage()) * catalog.setting("immortal-berserker-lifesteal");
                gainHealth(attacker, heal, "immortal_berserker");
            } else if (every(state, "immortal_berserker")) {
                state.immortalUntil = now() + rule("immortal_berserker").duration();
            }
        }
        if (owns(attacker, "berserk_engine")) {
            recordTopSpeed(attacker, state, now());
            boolean upgraded = evolved(state, AugmentEvolution.BERSERK.counter, AugmentEvolution.BERSERK.goal);
            state.progress.add("berserk_engine", 1, upgraded ? catalog.count("berserk-evolved-cap") : rule("berserk_engine").cap());
            state.lastBerserkHit = now();
            art.cue(attacker,"berserk_engine");
        }
        if (owns(attacker, "infernal_conductor")) {
            ignite(state, target, burnEvolved(state) ? catalog.count("burn-evolved-add") : 1);
        }
        if (owns(attacker, "magic_missile") && every(state, "magic_missile")) {
            afterDamage(attacker, () -> launchMissiles(attacker, target, state));
        }
        if (owns(attacker, "storm_chaser") && state.stormDistance >= rule("storm_chaser").threshold()
                && ready(attacker, state, "storm_chaser")) {
            state.stormDistance = 0;
            afterDamage(attacker, () -> {
                particle(attacker, state, Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0));
                if (syntheticHit(attacker, target, "storm_chaser", rule("storm_chaser").amount()) && followReady(state, "storm_chaser")) {
                    Location center = target.getLocation();
                    for (int pulse = 0; pulse < option("storm_chaser", "duration"); pulse++)
                        later(state, "storm_chaser", pulse * 1000L, () -> {
                            visual(attacker, state, "storm_chaser", center, Particle.ELECTRIC_SPARK, Sound.BLOCK_AMETHYST_BLOCK_CHIME);
                            for (var enemy : opponents(attacker, center, option("storm_chaser", "radius")))
                                reactionPotions.apply(attacker.getUniqueId(), "storm_chaser", enemy, new PotionEffect(PotionEffectType.SLOWNESS, 20, 0));
                        });
                }
            });
        }
        if (event.getDamager() instanceof AbstractArrow && owns(attacker, "explosive_arrow")
                && every(state, "explosive_arrow") && ready(attacker, state, "explosive_arrow")) {
            var center = target.getLocation();
            afterDamage(attacker, () -> area(attacker, center, catalog.setting("arrow-explosion-range"),
                    "explosive_arrow", rule("explosive_arrow").amount(), victim -> { }));
        }
        if (owns(attacker, "split_attack")) {
            Double damage = splitEvents.remove(event);
            if (damage != null) {
                state.progress.reset("split_charge");
                afterDamage(attacker, () -> {
                    for (int i = 0; i < catalog.count("split-extra-hits"); i++) {
                        afterRecovery(attacker, target, () -> syntheticHit(attacker, target, "split_attack", damage));
                    }
                });
            } else if (every(state, "split_attack")) {
                state.progress.add("split_charge", 1, 1);
            }
        }
        if (!event.getDamager().equals(attacker)) {
            if (event.getDamager() instanceof AbstractArrow) phantom(attacker, target, state);
            return;
        }
        state.counterReady = false;
        if (owns(attacker, "venom_swarm")) applyVenom(state, target);
        double lifesteal = owns(attacker, "blood_god") ? rule("blood_god").amount()
                : owns(attacker, "blood_crown") && ready(attacker, state, "blood_crown") ? rule("blood_crown").amount() : 0;
        if (owns(attacker, "last_demon") && state.lastDemonUntil > now()) lifesteal = Math.max(lifesteal, catalog.setting("last-demon-lifesteal"));
        if (lifesteal > 0) {
            String effect = owns(attacker, "blood_god") ? "blood_god" : "blood_crown";
            if (owns(attacker, "last_demon") && state.lastDemonUntil > now()
                    && lifesteal == catalog.setting("last-demon-lifesteal")) effect = "last_demon";
            gainHealth(attacker, Math.min(event.getFinalDamage(), target.getHealth()) * lifesteal, effect);
        }
        if (owns(attacker, "storm_blade") && every(state, "storm_blade") && ready(attacker, state, "storm_blade")) {
            Location center = target.getLocation();
            afterDamage(attacker, () -> {
                syntheticHit(attacker, target, "storm_blade", rule("storm_blade").amount());
                for (var secondary : opponents(attacker, center, catalog.setting("storm-blade-radius"))) {
                    if (!target.equals(secondary)) syntheticHit(attacker, secondary, "storm_blade", catalog.setting("storm-blade-secondary-damage"));
                }
                particle(attacker, state, Particle.ELECTRIC_SPARK, center.add(0, 1, 0));
            });
        }
        meleeEffects(attacker, target, state, false);
        if (!state.king && !state.thousandSwords && !owns(attacker, "sword_wind") && !owns(attacker, "clone_hand")
                && !owns(attacker, "ultimate_hydra") && !owns(attacker, "dual_wielder") && !owns(attacker, "draw_the_sword")) return;
        double base = rawDamage(event);
        var offhand = attacker.getInventory().getItemInOffHand().clone();
        afterDamage(attacker, () -> {
            if (owns(attacker, "ultimate_hydra")) hydra(attacker, target, state, base);
            if (owns(attacker, "sword_wind") && every(state, "sword_wind")) sword(attacker, state);
            if (state.thousandSwords) swords(attacker, state);
            if (owns(attacker, "draw_the_sword")) drawSword(attacker, state);
            if (owns(attacker, "dual_wielder") && offhand.equals(attacker.getInventory().getItemInOffHand())) {
                afterRecovery(attacker, target, () -> {
                    if (offhand.equals(attacker.getInventory().getItemInOffHand())) offhand(attacker, target);
                });
            }
            if (owns(attacker, "clone_hand") && every(state, "clone_hand") && ready(attacker, state, "clone_hand")) {
                afterRecovery(attacker, target,
                        () -> syntheticHit(attacker, target, "clone_hand", base * rule("clone_hand").amount()));
            }
            if (state.king && maxHealth(attacker) >= catalog.synergies.getDouble("steel_king.minimum-health")) {
                double radius = catalog.synergies.getDouble("steel_king.radius");
                attacker.getWorld().getNearbyEntities(attacker.getLocation(), radius, radius, radius).stream()
                        .filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast)
                        .filter(other -> other.getLocation().distanceSquared(attacker.getLocation()) <= radius * radius)
                        .forEach(other -> syntheticHit(attacker, other, "steel_king", catalog.synergies.getDouble("steel_king.damage")));
                if (catalog.synergies.getBoolean("steel_king.particles")) {
                    particle(attacker, state, Particle.SWEEP_ATTACK, attacker.getLocation().add(0, 1, 0));
                }
            }
        });
    }

    private static final Set<String> ACTIVE_SKILLS = Set.of("shadow_dash", "earthquake_mage", "time_rewind",
            "void_lord", "bomb_alchemy", "duel_contract", "calamity_giant");

    static boolean activeSkill(String id) { return ACTIVE_SKILLS.contains(id); }

    boolean resonanceMember(String id) {
        return resonance.families().stacking().members().contains(id) || resonance.families().revival().members().contains(id);
    }

    private record Warning(Location center, double radius, Player target) {}
    private record Pending(String id, long due, DamageChain.Root origin, Runnable action, Warning warning) {}
    private long millis() { return clock.get().elapsedNanos() / 1_000_000L; }
    private double option(String id, String key) {
        return catalog.config.getDouble(path(id) + key);
    }
    private String path(String id) {
        return (catalog.config.contains("followups." + id) ? "followups." : "augments.") + id + ".";
    }
    private boolean follow(String id) { return catalog.config.getBoolean("followups." + id + ".enabled"); }
    private long interval(String id, String key) { return Math.round(option(id, key) * 1000); }
    private CombatReaction reaction(Fighter state, String id) {
        return state.reactions.computeIfAbsent(id, ignored -> new CombatReaction());
    }
    private void visual(Player player, Fighter state, String id, Location at, Particle kind, Sound sound) {
        if (catalog.config.getBoolean(path(id) + "particles")) {
            particle(player, state, kind, at);
            art.cue(player,id);
        }
        if (catalog.config.getBoolean(path(id) + "sound") && PlayerUi.enabled(player,PlayerUi.Option.SOUND))
            player.playSound(at,sound,.34f,1.2f);
    }
    private void charge(Player player, Fighter state, String id) {
        CombatReaction r = reaction(state, id);
        r.charge(millis(), interval(id, "duration"), interval(id, "cooldown"));
        hud.proc(player, rule(id).definition().name() + " 충전 완료");
        visual(player, state, id, player.getLocation().add(0, 1, 0), Particle.CRIT, Sound.BLOCK_AMETHYST_BLOCK_CHIME);
    }
    private void later(Fighter state, String id, long delay, Runnable action) {
        later(state,id,delay,action,null);
    }

    private Pending later(Fighter state, String id, long delay, Runnable action, Warning warning) {
        if (state.pending.size() >= catalog.count("max-deferred-effects")) return null;
        DamageChain.Root root = chain.currentRoot() != null ? chain.currentRoot() : originalHit;
        var pending = new Pending(id,millis()+delay,root == null ? new DamageChain.Root() : root,action,warning);
        state.pending.add(pending);
        return pending;
    }

    private void warn(Player player,Pending pending) {
        var warning = pending.warning();
        if (warning == null) return;
        art.combat(player,warning.target(),pending.origin().id(),pending.id(),Form.DANGER,warning.center(),warning.radius());
    }
    private void tickReactions(Player player, Fighter state) {
        for (var task : List.copyOf(state.pending)) {
            if (millis() < task.due()) {
                if (Bukkit.getCurrentTick() % 8 == 0 && owns(player,task.id())) warn(player,task);
                continue;
            }
            if (!state.pending.remove(task)) continue;
            if (task.warning() != null) art.cancelWarning(player.getUniqueId(),task.id(),task.origin().id());
            if (valid(player) && owns(player, task.id())) chain.root(task.origin(), task.action());
        }
        var bastion = state.reactions.get("last_bastion");
        if (bastion != null && bastion.charged(millis()) && state.bastionVisualSecond != now()) {
            state.bastionVisualSecond = now();
            if (catalog.config.getBoolean("augments.last_bastion.particles"))
                particle(player, state, Particle.END_ROD, player.getLocation().add(0, 1, 0));
        }
    }
    private void push(Player source, LivingEntity victim, double strength) {
        var vector = victim.getLocation().toVector().subtract(source.getLocation().toVector()).setY(0);
        if (vector.lengthSquared() > 0) victim.setVelocity(vector.normalize().multiply(strength).setY(.2));
    }
    private void triad(Player player, Fighter state, Action type) {
        String id = "triad_reactor";
        if (!owns(player, id)) return;
        CombatReaction r = reaction(state, id);
        long time = millis();
        if (!r.action(type, time, interval(id, "window"))) return;
        charge(player, state, id);
    }

    private void attackReactions(EntityDamageByEntityEvent event, Player player, LivingEntity victim, Fighter state) {
        boolean melee = event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK && event.getDamager().equals(player);
        boolean ranged = event.getDamager() instanceof Projectile && event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE;
        if (!melee && !ranged) return;
        long t = millis();
        UUID target = victim.getUniqueId();
        double points = Math.min(event.getFinalDamage(), victim.getHealth());
        for (var entry : empowers.getOrDefault(event, Map.of()).entrySet()) {
            String id = entry.getKey();
            var charge = entry.getValue();
            if (state.reactions.get(id) != charge || !charge.spend(t)) continue;
            switch (id) {
                case "kinetic_reversal" -> push(player, victim, option(id, "push"));
                case "afterimage_stride" -> speed(player, id);
                case "triad_reactor" -> {
                    visual(player, state, id, victim.getLocation(), Particle.EXPLOSION, Sound.ENTITY_GENERIC_EXPLODE);
                    var center = victim.getLocation();
                    later(state, id, 0, () -> {
                        for (var other : opponents(player, center, option(id, "radius")))
                            if (!other.equals(victim)) syntheticHit(player, other, id, option(id, "splash"));
                    });
                }
                default -> { }
            }
        }
        triad(player, state, melee ? Action.MELEE : Action.PROJECTILE);
        if (melee && state.counterReady && follow("counter_shield")) {
            push(player, victim, option("counter_shield", "push"));
            visual(player, state, "counter_shield", victim.getLocation(), Particle.CRIT, Sound.ITEM_SHIELD_BLOCK);
        }
        String arrow = "ricochet_arrow";
        if (event.getDamager() instanceof org.bukkit.entity.Arrow && owns(player, arrow)
                && state.progress.ready(arrow, now())) {
            var center = victim.getLocation();
            var next = opponents(player, center, option(arrow, "radius")).stream().filter(other -> !other.equals(victim))
                    .min(Comparator.comparingDouble(other -> other.getLocation().distanceSquared(center)));
            if (next.isPresent() && ready(player, state, arrow)) {
                later(state, arrow, 0, () -> {
                    if (syntheticHit(player, next.get(), arrow, ReactionRules.ricochet(points, rule(arrow).amount(), rule(arrow).cap())))
                        visual(player, state, arrow, next.get().getLocation(), Particle.CRIT, Sound.ENTITY_ARROW_HIT);
                });
            }
        }
        for (String id : List.of("frost_bind", "delayed_blast")) {
            if (!owns(player, id) || !state.progress.ready(id, now())) continue;
            boolean frost = id.equals("frost_bind");
            if (!reaction(state, id).accumulate(frost ? target : player.getUniqueId(), t, interval(id, "window"),
                    frost ? 1 : points, rule(id).threshold())) continue;
            ready(player, state, id);
            var center = victim.getLocation();
            if (frost) {
                later(state, id, 0, () -> afterRecovery(player, victim, () -> {
                    if (syntheticHit(player, victim, id, rule(id).amount())) {
                        reactionPotions.apply(player.getUniqueId(), id, victim, new PotionEffect(PotionEffectType.SLOWNESS, rule(id).duration() * 20, 0));
                        visual(player, state, id, victim.getLocation(), Particle.SNOWFLAKE, Sound.BLOCK_GLASS_BREAK);
                    }
                }));
            } else {
                visual(player, state, id, center, Particle.FLAME, Sound.ENTITY_TNT_PRIMED);
                double radius = option(id,"radius");
                var signal = center.clone().add(0,.2,0);
                var pending = later(state,id,interval(id,"duration"),() -> {
                    art.combat(player,null,chain.currentRoot().id(),id,Form.BLAST,signal,radius);
                    visual(player, state, id, center, Particle.EXPLOSION, Sound.ENTITY_GENERIC_EXPLODE);
                    area(player,center,radius,id,rule(id).amount(),other -> {});
                },new Warning(signal,radius,victim instanceof Player targetPlayer ? targetPlayer : null));
                if (pending != null) warn(player,pending);
            }
        }
    }

    private void followHit(Player player, LivingEntity target, Fighter state, String id) {
        if (syntheticHit(player, target, id, option(id, "damage")))
            visual(player, state, id, target.getLocation(), Particle.CRIT, Sound.ENTITY_GENERIC_EXPLODE);
    }

    private boolean followReady(Fighter state, String id) {
        return follow(id) && state.progress.cooldown("follow:" + id, millis(), interval(id, "cooldown"));
    }

    private void speed(Player player, String id) {
        reactionPotions.apply(player.getUniqueId(), id, player, new PotionEffect(PotionEffectType.SPEED, (int) option(id, "speed-ticks"), 0));
    }

    List<String> timedStates(UUID id) {
        var state = fighters.get(id);
        if (state == null) return List.of();
        var rows = new ArrayList<String>();
        addTimed(rows,"그림자 질주",state.shadowUntil);
        addTimed(rows,"불멸의 광전사",state.immortalUntil);
        addTimed(rows,"결투 계약",state.duelUntil);
        addTimed(rows,"최후의 악마",state.lastDemonUntil);
        addTimed(rows,"자연 회복 제한",state.naturalRegenBlockedUntil);
        return rows;
    }

    private void addTimed(List<String> lines, String label, long until) {
        if (until > now()) lines.add(label + " " + (until-now()) + "초");
    }

    int skillUses(UUID id) { var f = fighters.get(id); return f == null ? 0 : f.skillUses; }
    List<String> availableSkills(Player player) {
        if (fighter(player.getUniqueId()) == null) return List.of();
        return ACTIVE_SKILLS.stream().filter(id -> owns(player, id)).sorted().toList();
    }

    void resetSkillCooldowns(Player player) {
        var state = fighters.get(player.getUniqueId());
        if (state == null) return;
        for (String id : ACTIVE_SKILLS) state.progress.resetCooldown(id);
        state.hudSecond = -1;
    }

    String skill(Player player, String[] args) {
        String op = args[1].toLowerCase(Locale.ROOT);
        if (op.equals("input")) {
            if (args.length == 2) return PlayerUi.skillInput(player).label;
            if (args.length != 3) return "/aw skill input <hotbar|cycle|menu>";
            try {
                var input = PlayerUi.SkillInput.valueOf(args[2].toUpperCase(Locale.ROOT));
                PlayerUi.skillInput(player,input);
                return input.label;
            } catch (IllegalArgumentException invalid) { return "/aw skill input <hotbar|cycle|menu>"; }
        }
        var state = fighters.get(player.getUniqueId());
        if (state == null) return "지정한 발동형 스킬이 없습니다.";
        var slots = state.slots;
        String usage = "/aw skill bind [1-3] <id> | select <1-3> | next | unbind | use | status";
        if (op.equals("bind") && (args.length == 3 || args.length == 4)) {
            int slot = args.length == 3 ? slots.selected() : parseSlot(args[2]);
            String id = args[args.length-1].toLowerCase(Locale.ROOT);
            if (!ACTIVE_SKILLS.contains(id) || !owns(player, id)) return "보유한 발동형 증강을 지정하세요.";
            if (!slots.bind(slot,id)) return usage;
        } else if (op.equals("select") && args.length == 3) {
            if (!slots.select(parseSlot(args[2]))) return usage;
        } else if (args.length == 2) {
            switch (op) {
                case "next" -> slots.next();
                case "unbind" -> slots.remove(slots.current());
                case "use" -> { return slots.current() == null ? "선택 슬롯이 비었습니다." : cast(player,slots.current()); }
                case "status" -> { }
                default -> { return usage; }
            }
        } else return usage;
        state.hudSecond = -1;
        return String.join(" | ", slotLines(player,state,true));
    }

    private static int parseSlot(String value) {
        return switch (value) { case "1" -> 1; case "2" -> 2; case "3" -> 3; default -> 0; };
    }

    private List<String> slotLines(Player player, Fighter state, boolean includeEmpty) {
        var lines = new ArrayList<String>();
        for (int slot = 1; slot <= SkillSlots.COUNT; slot++) {
            String id = state.slots.at(slot);
            if (id != null || includeEmpty) lines.add((slot == state.slots.selected() ? "▶" : "") + slot + " "
                    + (id == null ? "비어 있음" : skillStatus(player,state,id)));
        }
        return lines;
    }

    private void updateSkillHud(Player player, Fighter state) {
        boolean available = valid(player);
        if (state.hudSecond == now() && state.hudAvailable == available) return;
        state.hudAvailable = available;
        state.hudSecond = now();
        var lines = slotLines(player,state,true);
        var stillBound = new HashSet<String>();
        for (int slot = 1; slot <= SkillSlots.COUNT; slot++) {
            String id = state.slots.at(slot);
            if (id == null) continue;
            stillBound.add(id);
            if (!state.progress.ready(id,now())) state.coolingSlots.add(id);
            else if (state.coolingSlots.remove(id) && available && PlayerUi.enabled(player,PlayerUi.Option.SKILL)) {
                hud.proc(player,rule(id).definition().name() + " 준비 완료");
                if (PlayerUi.enabled(player,PlayerUi.Option.SOUND))
                    player.playSound(player.getLocation(),Sound.BLOCK_NOTE_BLOCK_CHIME,.23f,1.5f);
            }
        }
        state.coolingSlots.retainAll(stillBound);
        if (active.test(player)) {
            checkGrowth(player,state);
            var automatic = new ArrayList<String>();
            for (String id : List.of("triad_reactor","ore_ward","afterimage_stride","last_bastion","delayed_blast")) {
                if (!owns(player,id)) continue;
                var r = state.reactions.get(id);
                if (r == null) r = new CombatReaction();
                String progress = reactionProgress(player.getUniqueId(),id,r);
                String name = rule(id).definition().name();
                if (r.charged(millis())) automatic.add(name + " 충전 완료");
                else if (r.remaining(millis()) > 0) automatic.add(name + " 재사용 " + (r.remaining(millis())+999)/1000 + "초");
                else if (id.equals("ore_ward")) automatic.add(name + " " + r.charges() + "/" + rule(id).cap()
                        + " · 광석 " + state.progress.value("hits:ore_ward") + "/" + rule(id).threshold());
                else if (progress != null) automatic.add(progress);
                if (automatic.size() == 3) break;
            }
            if (!automatic.isEmpty()) hud.set(player.getUniqueId(),Layer.AUTO,net.kyori.adventure.text.Component.text("자동 · " + String.join(" | ",automatic)));
            else hud.remove(player.getUniqueId(),Layer.AUTO);
        }
        if (state.slots.at(1) == null && state.slots.at(2) == null && state.slots.at(3) == null) hud.remove(player.getUniqueId(), Layer.SKILL);
        else {
            var message = net.kyori.adventure.text.Component.text();
            for (int slot = 1; slot <= SkillSlots.COUNT; slot++) {
                if (slot > 1) message.append(net.kyori.adventure.text.Component.text(" · "));
                String id = state.slots.at(slot);
                if (id != null) {
                    long remaining = state.progress.cooldownRemaining(id,now());
                    var icon = art.slotIcon(player,id);
                    if (remaining > 0) icon = icon.color(net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY);
                    if (slot == state.slots.selected())
                        icon = net.kyori.adventure.text.Component.text("[",net.kyori.adventure.text.format.NamedTextColor.GOLD)
                                .append(icon).append(net.kyori.adventure.text.Component.text("]",net.kyori.adventure.text.format.NamedTextColor.GOLD));
                    message.append(icon);
                }
                message.append(net.kyori.adventure.text.Component.text(lines.get(slot - 1)));
            }
            hud.set(player.getUniqueId(),Layer.SKILL,message.build());
        }
    }

    private String reactionProgress(UUID owner, String id, CombatReaction state) {
        if (id.equals("triad_reactor")) return "삼중 반응로 " + state.actionCount(millis(), interval(id, "window")) + "/3";
        if (Set.of("afterimage_stride", "last_bastion", "delayed_blast").contains(id))
            return String.format(Locale.ROOT, "%s %.1f/%d", rule(id).definition().name(),
                    state.progress(owner, millis(), interval(id, "window")), rule(id).threshold());
        return null;
    }

    private String skillStatus(Player player, Fighter state, String id) {
        long remaining = state.progress.cooldownRemaining(id, now());
        String status;
        if (clock.get().isPaused()) status = "경기 일시정지";
        else if (protectedPlayer.test(player)) status = "부활 보호 중";
        else if (!valid(player) || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) status = "경기 참여 불가";
        else if (remaining > 0) {
            int duration = state.cooldownLengths.getOrDefault(id,0);
            int filled = duration > 0 ? Math.clamp((duration - remaining) * 4 / duration,0,4) : 0;
            status = "▰".repeat(filled) + "▱".repeat(4-filled) + " " + remaining + "초";
        }
        else status = "사용 가능";
        if (remaining == 0 && status.equals("사용 가능")) {
            if (id.equals("calamity_giant") && !player.isOnGround()) status = "지상 전용";
            if (id.equals("bomb_alchemy") && state.bomb != null) status = "폭탄 비행 중";
            if (id.equals("time_rewind") && state.history.stream().noneMatch(h -> h.second() == now()-rule(id).threshold())) status = "기록 대기";
        }
        return rule(id).definition().name() + " · " + status;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void selectWithHotbar(org.bukkit.event.player.PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking() || !valid(player) || PlayerUi.skillInput(player) != PlayerUi.SkillInput.HOTBAR) return;
        Fighter state = fighters.get(player.getUniqueId());
        int slot = event.getNewSlot() + 1;
        if (state == null || state.slots.selected() == slot || state.slots.at(slot) == null) return;
        state.slots.select(slot);
        state.hudSecond = -1;
        hud.skillInput(player, slot + " · " + skillStatus(player, state, state.slots.current()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void swapSkill(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        Fighter state = fighters.get(player.getUniqueId());
        if (!player.isSneaking() || state == null || !valid(player)) return;
        if (PlayerUi.skillInput(player) == PlayerUi.SkillInput.CYCLE) {
            int previous = state.slots.selected();
            state.slots.next();
            if (state.slots.selected() == previous) return;
            event.setCancelled(true);
            state.hudSecond = -1;
            hud.skillInput(player,state.slots.selected() + " · " + skillStatus(player,state,state.slots.current()));
            return;
        }
        if (state.slots.current() == null || !owns(player,state.slots.current())) return;
        event.setCancelled(true);
        String result = cast(player, state.slots.current());
        hud.skillInput(player,result);
    }

    String cast(Player player, String id) {
        if (!ACTIVE_SKILLS.contains(id)) return "사용할 수 있는 발동형 증강 ID가 아닙니다.";
        if (match != null && owns(player,id) && clock.get().isPaused()) return "경기 일시정지 중에는 사용할 수 없습니다.";
        if (!valid(player) || player.getGameMode() == org.bukkit.GameMode.SPECTATOR || !owns(player, id)) return "현재 이 증강을 발동할 수 없습니다.";
        Fighter state = fighter(player.getUniqueId());
        if (!state.progress.ready(id, now())) return "재사용 대기 " + state.progress.cooldownRemaining(id,now()) + "초";
        Location before = player.getLocation();
        boolean applied = switch (id) {
            case "shadow_dash", "earthquake_mage" -> {
                var direction = before.getDirection();
                Location target = PaperAugmentMovement.toward(player, direction,
                        catalog.setting(id.equals("shadow_dash") ? "shadow-dash-distance" : "quake-dash-distance"), safe);
                if (target == null || !teleport.test(player, target)) yield false;
                if (id.equals("shadow_dash")) state.shadowUntil = now() + rule(id).duration();
                else state.zones.add(new Zone(id, before, target, now() + rule(id).duration(), rule(id).amount()));
                yield true;
            }
            case "time_rewind" -> rewind(player, state);
            case "calamity_giant" -> {
                if (!player.isOnGround()) yield false;
                yield afterDamage(player, () -> {
                    Location center = player.getLocation();
                    double radius = catalog.setting("calamity-slam-radius");
                    area(player,center,radius,id,catalog.setting("calamity-slam-damage"),target -> { });
                    art.combat(player,null,null,id,Form.BLAST,center.clone().add(0,.2,0),radius);
                });
            }
            case "duel_contract" -> {
                var eye = player.getEyeLocation();
                var rival = ray(player, eye, eye.getDirection(), catalog.setting("duel-range"));
                if (!(rival instanceof Player)) yield false;
                state.duelTarget = rival.getUniqueId();
                state.duelUntil = now() + rule(id).duration();
                var glow = new PotionEffect(PotionEffectType.GLOWING, rule(id).duration() * 20, 0);
                player.addPotionEffect(glow);
                rival.addPotionEffect(glow);
                yield true;
            }
            case "bomb_alchemy" -> {
                if (state.bomb != null) yield false;
                state.bomb = player.launchProjectile(Snowball.class);
                state.bombLaunchTick = Bukkit.getCurrentTick();
                if (state.bomb.isValid()) yield true;
                removeBomb(state);
                yield false;
            }
            case "void_lord" -> {
                state.zones.add(new Zone(id, before, before, now() + rule(id).duration(), rule(id).amount()));
                yield true;
            }
            default -> false;
        };
        if (!applied) return switch (id) {
            case "duel_contract" -> "조준한 유효 상대가 없습니다.";
            case "bomb_alchemy" -> "폭탄이 이미 비행 중이거나 발사할 수 없습니다.";
            case "calamity_giant" -> player.isOnGround() ? "효과 처리 대기열이 가득 찼습니다." : "지상에서 사용하세요.";
            case "time_rewind" -> "복귀 가능한 위치 기록이 없습니다.";
            default -> "안전한 이동 위치를 확보하지 못했습니다.";
        };
        ready(player, state, id);
        state.hudSecond = -1;
        art.cast(player,id,before);
        announceProc(player, id);
        state.skillUses++;
        return rule(id).definition().name() + " 발동";
    }

    private void remember(Player player, Fighter state, long second) {
        if (!owns(player, "time_rewind") || state.history.peekLast() != null && state.history.peekLast().second() == second) return;
        state.history.addLast(new PaperAugmentMovement.History(second, player.getLocation(), player.getHealth()));
        int memory = rule("time_rewind").threshold() + 2;
        while (state.history.size() > memory) state.history.removeFirst();
    }

    private boolean rewind(Player player, Fighter state) {
        long past = now() - rule("time_rewind").threshold();
        var record = state.history.stream().filter(item -> item.second() == past).findFirst().orElse(null);
        if (record == null || !safe.test(record.location()) || !SafeArenaSurface.valid(record.location())
                || !teleport.test(player, record.location())) return false;
        for (var sample : state.history)
            if (sample.second() >= past) art.spark(player,sample.location().clone().add(0,.8,0),Particle.END_ROD);
        if (record.health() > player.getHealth()) {
            gainHealth(player,Math.min(record.health()-player.getHealth(),rule("time_rewind").amount()),"time_rewind");
        }
        return true;
    }

    private void tickZones(Player player, Fighter state, long second) {
        if (state.zones.isEmpty() || state.lastZoneSecond == second) return;
        state.lastZoneSecond = second;
        var iterator = state.zones.iterator();
        while (iterator.hasNext()) {
            Zone zone = iterator.next();
            if (!safe.test(zone.start())) {
                if (zone.augment().equals("void_lord")) art.cancelWarning(player.getUniqueId(),zone.augment(),null);
                iterator.remove();
                continue;
            }
            if (zone.augment().equals("bomb_alchemy")) {
                if (second <= zone.expiresAt()) {
                    chain.root(() -> area(player, zone.start(), catalog.setting("bomb-area-radius"), zone.augment(),
                            catalog.setting("bomb-fire-damage"), target -> { }));
                    particle(player, state, Particle.FLAME, zone.start());
                }
            } else if (zone.augment().equals("void_lord")) {
                if (second <= zone.expiresAt()) chain.root(() -> pulseVoid(player, state, zone));
            } else if (second < zone.expiresAt()) {
                particle(player, state, Particle.FLAME, zone.start().clone().add(0, .25, 0));
            } else if (zone.augment().equals("earthquake_mage")) {
                chain.root(() -> eruptPath(player, state, zone));
            } else {
                chain.root(() -> area(player, zone.start(), catalog.setting("judgment-radius"), zone.augment(),
                        zone.damage(), target -> { }));
                particle(player, state, Particle.EXPLOSION, zone.start().clone().add(0, 1, 0));
            }
            boolean expires = second >= zone.expiresAt();
            if (zone.augment().equals("void_lord")) {
                double radius = catalog.setting("void-radius");
                if (expires) art.cancelWarning(player.getUniqueId(),zone.augment(),null);
                art.combat(player,null,null,zone.augment(),expires ? Form.VOID : Form.DANGER,
                        zone.start().clone().add(0,.2,0),expires ? 0 : radius);
            }
            if (expires) iterator.remove();
        }
    }

    private void pulseVoid(Player player, Fighter state, Zone zone) {
        double damage = zone.damage() / rule("void_lord").duration();
        area(player, zone.start(), catalog.setting("void-radius"), zone.augment(), damage, target -> {
            var direction = zone.start().toVector().subtract(target.getLocation().toVector());
            if (direction.lengthSquared() > 0) target.setVelocity(direction.normalize().multiply(catalog.setting("void-pull-velocity")));
        });
        particle(player, state, Particle.PORTAL, zone.start().clone().add(0, 1, 0));
    }

    private void eruptPath(Player player, Fighter state, Zone zone) {
        var start = zone.start().toVector();
        var delta = zone.end().toVector().subtract(start);
        double width = catalog.setting("quake-path-radius");
        double lengthSquared = delta.lengthSquared();
        if (lengthSquared <= 0) return;
        for (var target : opponents(player, zone.start(), delta.length() + width)) {
            var offset = target.getLocation().toVector().subtract(start);
            double progress = Math.clamp(offset.dot(delta) / lengthSquared, 0, 1);
            if (offset.distanceSquared(delta.clone().multiply(progress)) <= width * width) {
                syntheticHit(player, target, zone.augment(), zone.damage());
            }
        }
        art.link(player,zone.start(),zone.end(),Particle.CRIT);
        art.ring(player,zone.end(),1.1,Particle.CRIT,false);
        particle(player, state, Particle.EXPLOSION, zone.end());
    }

    private void worldErosion(Player player, Fighter state) {
        if (!owns(player, "world_erosion") || !ready(player, state, "world_erosion")) return;
        var rule = rule("world_erosion");
        double absorption = 0;
        for (var target : opponents(player, player.getLocation(), catalog.setting("erosion-radius"))) {
            absorption += erosion.drain(player.getUniqueId(), target, now(), rule.amount(), rule.duration());
        }
        if (absorption > 0) {
            addAbsorption(player, absorption * catalog.setting("erosion-absorption-ratio"),
                    catalog.setting("erosion-absorption-cap"));
            art.cue(player,"world_erosion");
        }
    }

    private boolean trackedBomb(Snowball projectile) {
        if (!(projectile.getShooter() instanceof Player player)) return false;
        var state = fighters.get(player.getUniqueId());
        return state != null && projectile.equals(state.bomb);
    }

    private void removeBomb(Fighter state) {
        if (state.bomb == null) return;
        state.bomb.remove();
        state.bomb = null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void bombHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball bomb) || !trackedBomb(bomb)
                || !(bomb.getShooter() instanceof Player player)) return;
        var state = fighter(player.getUniqueId());
        Location point = bomb.getLocation();
        event.setCancelled(true);
        removeBomb(state);
        if (!valid(player) || !safe.test(point)) return;
        art.combat(player,null,bomb.getUniqueId(),"bomb_alchemy",Form.BLAST,point,catalog.setting("bomb-area-radius"));
        state.zones.add(new Zone("bomb_alchemy", point, point, now() + rule("bomb_alchemy").duration(),
                rule("bomb_alchemy").amount()));
        afterDamage(player, () -> area(player, point, catalog.setting("bomb-area-radius"), "bomb_alchemy",
                rule("bomb_alchemy").amount(), victim -> { }));
    }

    private void stealTime(Player player, LivingEntity target) {
        int reduce = catalog.count("time-thief-reduce-seconds") * 20;
        for (var effect : target.getActivePotionEffects()) {
            if (effect.isInfinite() || effect.getType().getEffectCategory() != PotionEffectType.Category.BENEFICIAL) continue;
            target.removePotionEffect(effect.getType());
            int remaining = effect.getDuration() - reduce;
            if (remaining > 0) target.addPotionEffect(new PotionEffect(effect.getType(), remaining, effect.getAmplifier(),
                    effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
        }
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, catalog.count("time-thief-slow-seconds") * 20, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, rule("time_thief").duration() * 20, 1));
        art.cue(player,"time_thief");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void potionChanged(EntityPotionEffectEvent event) {
        if (event.getAction() != EntityPotionEffectEvent.Action.CHANGED || event.isOverride())
            reactionPotions.changed(event.getEntity().getUniqueId(), event.getModifiedType());
    }

    private void applyVenom(Fighter state, LivingEntity target) {
        long now = now();
        state.poisoned.values().removeIf(until -> until <= now);
        if (state.poisoned.size() >= catalog.count("burn-max-targets")) return;
        int length = rule("venom_swarm").duration();
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, length * 20, 1));
        state.poisoned.put(target.getUniqueId(), now + length);
        art.cue(state.owner,"venom_swarm");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void fireBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getProjectile() instanceof AbstractArrow original)
                || !valid(player) || !owns(player, "split_bow")) return;
        Fighter state = fighter(player.getUniqueId());
        if (!every(state, "split_bow") || !ready(player, state, "split_bow")) return;
        splitArrows.put(original.getUniqueId(), Bukkit.getCurrentTick() + catalog.count("missile-lifetime-ticks"));
        art.cue(player,"split_bow");
        double angle = catalog.setting("split-bow-angle");
        var start = player.getEyeLocation();
        var direction = original.getVelocity();
        if (direction.lengthSquared() == 0) return;
        double damage = original.getDamage() * catalog.setting("split-bow-ratio");
        afterDamage(player, () -> {
            int reserve = owns(player, "bomb_alchemy") ? 1 : 0;
            for (double yaw : new double[] {-angle, angle}) {
                if (state.arrows.size() + state.missiles.size() >= catalog.count("missile-active-cap") - reserve) break;
                var path = direction.clone().rotateAroundY(yaw);
                state.arrows.add(new ArrowPath(start.clone(), path, damage,
                        Bukkit.getCurrentTick() + catalog.count("split-bow-lifetime-ticks"), chain.currentRoot()));
            }
        });
    }

    private void advanceSplitArrows(Player player, Fighter state) {
        var iterator = state.arrows.iterator();
        while (iterator.hasNext()) {
            var path = iterator.next();
            Location position = path.position();
            var velocity = path.velocity();
            double travel = velocity.length();
            if (Bukkit.getCurrentTick() - path.expires() >= 0 || travel <= .01 || !arena.test(position)) {
                iterator.remove();
                continue;
            }
            var direction = velocity.clone().normalize();
            var victim = ray(player, position, direction, travel);
            if (victim != null) {
                chain.root(path.root(), () -> syntheticHit(player, victim, "split_bow", path.damage()));
                iterator.remove();
            } else if (position.getWorld().rayTraceBlocks(position, direction, travel, FluidCollisionMode.NEVER, true) != null) {
                iterator.remove();
            } else {
                position.add(velocity);
                velocity.multiply(catalog.setting("split-bow-drag")).setY(velocity.getY() - catalog.setting("split-bow-gravity"));
                particle(player, state, Particle.CRIT, position);
            }
        }
    }

    private void phantom(Player attacker, LivingEntity target, Fighter state) {
        if (owns(attacker, "phantom_weapon")) meleeEffects(attacker, target, state, true);
    }

    private void meleeEffects(Player attacker, LivingEntity target, Fighter state, boolean phantom) {
        var allowlist = catalog.config.getStringList("settings.phantom-melee-effects");
        if (owns(attacker, "steel_heart") && (!phantom || allowlist.contains("steel_heart")) && ready(attacker, state, "steel_heart")) {
            int limit = catalog.count(AugmentEvolution.STEEL.goal), before = state.progress.value("steel_heart");
            int after = grant(state, "steel_heart", before >= limit ? 2 : 1);
            if (after > before) {
                if (phantom) art.cue(attacker,"phantom_weapon");
                announceProc(attacker,"steel_heart");
            }
            if (before < limit && after >= limit) evolution(attacker, state, "steel_heart");
        }
        if (owns(attacker, "predator") && (!phantom || allowlist.contains("predator")) && every(state, "predator") && ready(attacker, state, "predator")) {
            if (phantom) art.cue(attacker,"phantom_weapon");
            double extra = ReactionRules.overheal(attacker.getHealth(), maxHealth(attacker), rule("predator").amount(),
                    option("predator", "ratio"), option("predator", "cap"));
            gainHealth(attacker,rule("predator").amount(),"predator");
            if (follow("predator") && extra > 0 && attacker.getAbsorptionAmount() == 0
                    && !attacker.hasPotionEffect(PotionEffectType.ABSORPTION)) {
                if (reactionPotions.apply(attacker.getUniqueId(), "predator", attacker, new PotionEffect(PotionEffectType.ABSORPTION,
                        (int) (option("predator", "duration") * 20), (int) Math.ceil(extra / 4) - 1))) {
                    attacker.setAbsorptionAmount(extra);
                    visual(attacker, state, "predator", attacker.getLocation(), Particle.HEART, Sound.BLOCK_AMETHYST_BLOCK_CHIME);
                    announceProc(attacker, "predator");
                }
            }
        }
        if (owns(attacker, "chain_impact") && (!phantom || allowlist.contains("chain_impact"))
                && every(state, "chain_impact") && ready(attacker, state, "chain_impact")) {
            var effect = new PotionEffect(PotionEffectType.SLOWNESS, rule("chain_impact").duration() * 20, 0);
            afterDamage(attacker, () -> area(attacker, attacker.getLocation(), catalog.setting("chain-impact-range"), "chain_impact",
                    rule("chain_impact").amount(), victim -> {
                        victim.addPotionEffect(effect);
                        if (phantom) art.cue(attacker,"phantom_weapon");
                    }));
        }
    }

    private void offhand(Player source, LivingEntity target) {
        var item = source.getInventory().getItemInOffHand();
        var type = item.getType().name();
        if (!type.endsWith("_SWORD") && !type.endsWith("_AXE") && !type.equals("TRIDENT") && !type.equals("MACE")
                && !type.endsWith("_SPEAR")) return;
        var meta = item.getItemMeta();
        var custom = meta == null ? null : meta.getAttributeModifiers(EquipmentSlot.HAND);
        var modifiers = custom == null ? item.getType().getDefaultAttributeModifiers(EquipmentSlot.HAND) : custom;
        double damage = modifiers.get(Attribute.ATTACK_DAMAGE).stream()
                .filter(modifier -> modifier.getOperation() == Operation.ADD_NUMBER)
                .mapToDouble(modifier -> modifier.getAmount()).sum();
        int sharpness = item.getEnchantmentLevel(Enchantment.SHARPNESS);
        if (sharpness > 0) damage += catalog.setting("offhand-sharpness-base") + sharpness * catalog.setting("offhand-sharpness-per-level");
        if (!syntheticHit(source, target, "dual_wielder", damage * rule("dual_wielder").amount())) return;
        int fire = item.getEnchantmentLevel(Enchantment.FIRE_ASPECT);
        if (fire > 0 && !target.isDead()) target.setFireTicks(Math.max(target.getFireTicks(), fire * catalog.count("offhand-fire-ticks")));
        int knockback = item.getEnchantmentLevel(Enchantment.KNOCKBACK);
        var push = target.getLocation().toVector().subtract(source.getLocation().toVector()).setY(0);
        if (knockback > 0 && push.lengthSquared() > 0 && !target.isDead()) {
            target.setVelocity(push.normalize().multiply(knockback * catalog.setting("offhand-knockback-velocity")));
        }
        source.damageItemStack(EquipmentSlot.OFF_HAND, catalog.count("offhand-durability-cost"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void shield(PlayerInteractEvent event) {
        if (active.test(event.getPlayer()) && (owns(event.getPlayer(), "dual_wielder") || owns(event.getPlayer(), "draw_the_sword"))
                && event.hasItem() && event.getItem().getType() == Material.SHIELD) {
            event.setUseItemInHand(Event.Result.DENY);
            if (event.getPlayer().getActiveItem().getType() == Material.SHIELD) event.getPlayer().clearActiveItem();
        }
    }

    private boolean burnEvolved(Fighter state) {
        return state.burned >= catalog.setting(AugmentEvolution.BURN.goal);
    }

    private void ignite(Fighter state, LivingEntity target, int gain) {
        var targets = state.burns.targets();
        if (targets.size() >= catalog.count("burn-max-targets") && !targets.containsKey(target.getUniqueId())) return;
        var rule = rule("infernal_conductor");
        state.burns.ignite(target, gain, burnEvolved(state) ? catalog.count("burn-evolved-cap") : rule.cap(), now(), rule.duration());
    }

    private void tickBurns(Player player, Fighter state, long second) {
        var targets = state.burns.targets();
        for (var burn : List.copyOf(targets.values())) {
            var target = burn.target();
            var id = target.getUniqueId();
            if (burn.expiresAt() < second || !opponent(player, target)) {
                targets.remove(id);
            } else if (burn.nextPulse() <= second) {
                targets.put(id, new PaperAugmentBurns.Burn(target, burn.stacks(), burn.expiresAt(), second + 1));
                chain.root(() -> {
                    if (syntheticHit(player, target, "infernal_conductor", burn.stacks() * rule("infernal_conductor").amount())
                            && !burnEvolved(state)) {
                        state.burned = Math.min(state.burned + syntheticFinalDamage, catalog.setting(AugmentEvolution.BURN.goal));
                        if (burnEvolved(state)) evolution(player, state, "infernal_conductor");
                    }
                });
            }
        }
    }

    private void orbit(Player player, Fighter state, long second) {
        var orb = rule("center_of_universe");
        int tick = Bukkit.getCurrentTick();
        double radius = catalog.setting("orb-radius");
        double reach = catalog.setting("orb-hit-radius");
        Location center = player.getLocation().add(0, catalog.setting("orb-height"), 0);
        boolean display = Math.floorMod(tick, catalog.count("orb-particle-tick-interval")) == 0
                && (!state.hellLord || catalog.synergies.getBoolean("hell_lord.particles"));
        boolean attack = state.progress.cooldown("orbit", second, cooldown(player, orb.cooldown()));
        if (!display && !attack) return;
        var candidates = attack ? opponents(player, center, radius + reach) : List.<LivingEntity>of();
        chain.root(() -> {
            for (int i = 0; i < orb.threshold(); i++) {
                double theta = tick * catalog.setting("orb-radians-per-tick") + i * Math.TAU / orb.threshold();
                var location = center.clone().add(radius * Math.cos(theta), 0, radius * Math.sin(theta));
                if (display) particle(player, state, Particle.END_ROD, location);
                candidates.stream()
                        .filter(enemy -> enemy.getLocation().add(0, enemy.getHeight() / 2, 0).distanceSquared(location) <= reach * reach)
                        .filter(player::hasLineOfSight)
                        .findFirst()
                        .ifPresent(enemy -> {
                            if (syntheticHit(player, enemy, "center_of_universe", orb.amount()) && state.hellLord) {
                                ignite(state, enemy, catalog.synergies.getInt("hell_lord.orb-burn-stacks"));
                            }
                        });
            }
        });
    }

    private void hydra(Player source, LivingEntity primary, Fighter state, double base) {
        boolean shockwave = every(state, "ultimate_hydra");
        int repeats = repetitions(source, state);
        for (int i = 0; i < repeats; i++) {
            for (var target : opponents(source, primary.getLocation(), catalog.setting("hydra-radius"))) {
                if (!target.equals(primary)) syntheticHit(source, target, "ultimate_hydra", base * rule("ultimate_hydra").amount());
            }
            if (shockwave) area(source, source.getLocation(), catalog.setting("hydra-shockwave-radius"),
                    "ultimate_hydra", catalog.setting("hydra-shockwave-damage"), target -> { });
        }
    }

    private int repetitions(Player player, Fighter state) {
        if (!owns(player, "infinite_repeat")) return 1;
        int count = every(state, "infinite_repeat") ? catalog.count("repeat-third-count")
                : (int) rule("infinite_repeat").amount();
        if (count > 1) art.cue(player,"infinite_repeat");
        return count;
    }

    private void launchMissiles(Player player, LivingEntity primary, Fighter state) {
        LivingEntity target = opponent(player, primary) ? primary
                : opponents(player, player.getLocation(), catalog.setting("missile-target-range")).stream().findFirst().orElse(null);
        if (target == null) return;
        int threshold = catalog.count(AugmentEvolution.MISSILE.goal);
        int before = state.progress.value(AugmentEvolution.MISSILE.counter);
        int baseCount = catalog.count(before >= threshold ? "missile-evolved-count" : "missile-count");
        int multiplier = state.barrage ? catalog.synergies.getInt("infinite_barrage.missile-multiplier") : 1;
        int reserved = owns(player, "bomb_alchemy") ? 1 : 0;
        int count = Math.min(baseCount * multiplier * repetitions(player, state),
                catalog.count("missile-active-cap") - reserved - state.missiles.size() - state.arrows.size());
        int bounces = state.barrage ? catalog.synergies.getInt("infinite_barrage.bounces") : 0;
        for (int i = 0; i < count; i++) {
            var start = player.getEyeLocation().add(0, (i % 5 - 2) * .08, 0);
            var missile = new PaperMissiles.Missile(start, target, catalog.count("missile-lifetime-ticks") + i % 5, bounces);
            state.missiles.add(new MissileShot(missile, chain.currentRoot()));
        }
        if (count > 0 && before < threshold && state.progress.add(AugmentEvolution.MISSILE.counter, count, threshold) == threshold) {
            evolution(player, state, "magic_missile");
        }
    }

    private void advanceMissiles(Player player, Fighter state) {
        var iterator = state.missiles.iterator();
        while (iterator.hasNext()) {
            var shot = iterator.next();
            var missile = shot.missile();
            boolean reached = missile.advance(player, catalog.setting("missile-speed"), this::opponent);
            if (!state.barrage || catalog.synergies.getBoolean("infinite_barrage.particles")) {
                if (Bukkit.getCurrentTick() % 4 == 0)
                    art.combat(player,null,shot.root() == null ? null : shot.root().id(),"magic_missile",Form.TRAIL,missile.position,0);
                particle(player, state, Particle.END_ROD, missile.position);
            }
            if (reached) {
                LivingEntity struck = missile.target;
                missile.struck.add(struck.getUniqueId());
                chain.root(shot.root(), () -> {
                    String id = "magic_missile";
                    if (syntheticHit(player, struck, id, rule(id).amount()) && follow(id)
                            && reaction(state, id).accumulate(struck.getUniqueId(), millis(), interval(id, "window"), 1, option(id, "threshold"))
                            && followReady(state, id))
                        later(state, id, 0, () -> afterRecovery(player, struck, () -> followHit(player, struck, state, "magic_missile")));
                });
                if (missile.bounces > 0) {
                    var next = opponents(player, missile.position, catalog.synergies.getDouble("infinite_barrage.bounce-radius")).stream()
                            .filter(other -> !missile.struck.contains(other.getUniqueId()))
                            .min(Comparator.comparingDouble(other -> other.getLocation().distanceSquared(missile.position)));
                    if (next.isPresent()) {
                        missile.target = next.get();
                        missile.bounces--;
                        continue;
                    }
                }
            }
            if (reached || missile.remainingTicks <= 0) iterator.remove();
        }
    }

    private void particle(Player player, Fighter state, Particle kind, Location location) {
        if (particleAvailable(state)) art.spark(player,location,kind);
    }

    private boolean particleAvailable(Fighter state) {
        long second = System.nanoTime() / 1_000_000_000L;
        if (state.particleSecond != second) {
            state.particleSecond = second;
            state.particles = 0;
        }
        if (state.particles >= catalog.count("particles-per-second")) return false;
        state.particles++;
        return true;
    }

    private boolean afterDamage(Player source, Runnable action) {
        if (deferred.size() >= catalog.count("max-deferred-effects")) return false;
        DamageChain.Root root = chain.currentRoot() != null ? chain.currentRoot() : originalHit;
        deferred.addLast(new Deferred(match, source, root == null ? new DamageChain.Root() : root, action));
        if (deferredTask == null) deferredTask = plugin.getServer().getScheduler().runTask(plugin, this::applyDeferred);
        return true;
    }

    private void afterRecovery(Player source, LivingEntity target, Runnable action) {
        afterRecovery(source, target, Bukkit.getCurrentTick() + resonance.followUpTicks(), action);
    }

    private void afterRecovery(Player source, LivingEntity target, int deadline, Runnable action) {
        if (!valid(source) || !opponent(source, target)) return;
        if (target.getNoDamageTicks() <= target.getMaximumNoDamageTicks() / 2) action.run();
        else if (Bukkit.getCurrentTick() - deadline < 0) {
            afterDamage(source, () -> afterRecovery(source, target, deadline, action));
        }
    }

    private void applyDeferred() {
        deferredTask = null;
        var scheduled = List.copyOf(deferred);
        deferred.clear();
        for (var effect : scheduled) {
            if (effect.match().equals(match) && valid(effect.player())) chain.root(effect.origin(), effect.action());
        }
    }

    private List<LivingEntity> opponents(Player source, Location center, double radius) {
        return center.getWorld().getNearbyEntities(center, radius, radius, radius).stream()
                .filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast)
                .filter(target -> opponent(source, target) && target.getLocation().distanceSquared(center) <= radius * radius)
                .toList();
    }

    private void area(Player source, Location center, double radius, String id, double damage, Consumer<LivingEntity> after) {
        for (LivingEntity target : opponents(source, center, radius)) {
            if (syntheticHit(source, target, id, damage)) after.accept(target);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void velocity(PlayerVelocityEvent event) {
        Fighter state = fighters.get(event.getPlayer().getUniqueId());
        if (state != null) {
            state.hadVelocity = true;
            state.forcedAt = Bukkit.getCurrentTick();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (event instanceof org.bukkit.event.player.PlayerTeleportEvent || !valid(player) || clock.get().isPaused()
                || player.isInsideVehicle() || player.isFlying() || player.isGliding() || player.isRiptiding()
                || !event.getFrom().getWorld().equals(event.getTo().getWorld())) return;
        Fighter state = fighters.get(player.getUniqueId());
        if (state == null || (state.hadVelocity && Bukkit.getCurrentTick() - state.forcedAt < catalog.count("movement-forced-ticks"))) return;
        double step = MovementProgress.horizontal(event.getTo().getX() - event.getFrom().getX(),
                event.getTo().getZ() - event.getFrom().getZ(), catalog.setting("movement-max-step"));
        if (step == 0 || !countedMovement.add(event)) return;
        String stride = "afterimage_stride";
        if (owns(player, stride)) {
            CombatReaction r = reaction(state, stride);
            if (r.ready(millis()) && r.accumulate(player.getUniqueId(), millis(),
                    interval(stride, "window"), step, rule(stride).threshold())) charge(player, state, stride);
        }
        if (owns(player, "storm_chaser")) state.stormDistance = Math.min(rule("storm_chaser").threshold(), state.stormDistance + step);
        if (owns(player, "tank_engine")) {
            int earned = state.movement.add(step, rule("tank_engine").threshold());
            if (earned > 0) {
                int past = state.progress.value("tank_engine");
                int next = grant(state, "tank_engine", earned);
                int evolution = catalog.count(AugmentEvolution.TANK.goal);
                if (past < evolution && next >= evolution) evolution(player, state, "tank_engine");
            }
        }
        boolean tank = owns(player, "tank_engine") && evolved(state, "tank_engine", AugmentEvolution.TANK.goal);
        boolean giant = owns(player, "calamity_giant") && player.isSprinting();
        boolean train = state.train && player.isSprinting();
        if (!tank && !giant && !train) return;
        int wait = train ? catalog.synergies.getInt("calamity_train.cooldown") : catalog.count("tank-contact-cooldown");
        double radius = train ? catalog.synergies.getDouble("calamity_train.radius") : catalog.setting("contact-radius");
        var nearby = opponents(player, event.getTo(), radius);
        if (nearby.isEmpty() || !state.progress.cooldown("contact", now(), cooldown(player, wait))) return;
        afterDamage(player, () -> {
            if (train && catalog.synergies.getBoolean("calamity_train.particles")) {
                particle(player, state, Particle.SWEEP_ATTACK, player.getLocation().add(0, 1, 0));
            }
            for (var target : nearby) {
                if (!opponent(player, target) || target.getLocation().distanceSquared(player.getLocation()) > radius * radius) continue;
                if (train || tank) syntheticHit(player, target, train ? "calamity_train" : "tank_engine",
                        train ? catalog.synergies.getDouble("calamity_train.damage") : catalog.setting("tank-contact-damage"));
                if (giant) {
                    var push = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
                    if (push.lengthSquared() > 0) target.setVelocity(push.normalize().multiply(catalog.setting("calamity-knockback")));
                }
            }
        });
    }

    private Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) return player;
        return event.getDamager() instanceof Projectile arrow && arrow.getShooter() instanceof Player player ? player : null;
    }

    private boolean ready(Player player, Fighter state, String id) {
        int base = rule(id).cooldown();
        int adjusted = cooldown(player,base);
        boolean started = state.progress.cooldown(id,now(),adjusted);
        if (started && ACTIVE_SKILLS.contains(id)) state.cooldownLengths.put(id,adjusted);
        if (started && adjusted < base) art.cue(player,"eureka");
        return started;
    }

    private boolean every(Fighter state, String id) {
        int required = rule(id).threshold();
        String key = "hits:" + id;
        if (state.progress.add(key, 1, required) < required) return false;
        state.progress.reset(key);
        return true;
    }

    private LivingEntity ray(Player player, Location start, org.bukkit.util.Vector direction, double reach) {
        var block = player.getWorld().rayTraceBlocks(start, direction, reach, FluidCollisionMode.NEVER, true);
        if (block != null) reach = Math.max(0, block.getHitPosition().distance(start.toVector()) - .01);
        if (reach == 0) return null;
        var entity = player.getWorld().rayTraceEntities(start, direction, reach, catalog.setting("sword-ray-size"),
                candidate -> candidate instanceof LivingEntity living && opponent(player, living));
        return entity != null && entity.getHitEntity() instanceof LivingEntity victim ? victim : null;
    }

    private void sword(Player player, Fighter state) {
        var start = player.getEyeLocation();
        var forward = start.getDirection();
        boolean evolved = evolved(state, AugmentEvolution.SWORD.counter, AugmentEvolution.SWORD.goal);
        var rays = evolved ? List.of(forward.clone().rotateAroundY(.18).setY(forward.getY() + .18).normalize(),
                forward.clone().rotateAroundY(-.18).setY(forward.getY() - .18).normalize()) : List.of(forward);
        var hit = new HashSet<UUID>();
        int confirmed = 0;
        for (var direction : rays) {
            var victim = ray(player, start, direction, catalog.setting("sword-reach"));
            if (victim != null && hit.add(victim.getUniqueId()) && syntheticHit(player, victim, "sword_wind", rule("sword_wind").amount())) {
                confirmed++;
                int limit = catalog.count(AugmentEvolution.SWORD.goal);
                if (state.progress.value(AugmentEvolution.SWORD.counter) < limit && state.progress.add(AugmentEvolution.SWORD.counter, 1, limit) == limit) {
                    evolution(player, state, "sword_wind");
                }
                phantom(player, victim, state);
            }
            particle(player, state, Particle.SWEEP_ATTACK, start.clone().add(direction));
        }
        if (confirmed >= 2 && followReady(state, "sword_wind")) charge(player, state, "sword_wind");
    }

    private void drawSword(Player player, Fighter state) {
        Location start = player.getEyeLocation();
        var direction = start.getDirection();
        var victim = ray(player, start, direction, catalog.setting("draw-sword-reach"));
        double damage = catalog.setting("draw-sword-base-damage") *
                (1 + state.progress.value("draw_the_sword") * rule("draw_the_sword").amount());
        if (victim != null && syntheticHit(player, victim, "draw_the_sword", damage)) {
            grant(state, "draw_the_sword", 1);
            phantom(player, victim, state);
        }
        particle(player, state, Particle.SWEEP_ATTACK, start.add(direction));
    }

    private void swords(Player player, Fighter state) {
        var start = player.getEyeLocation();
        var forward = start.getDirection();
        double angle = catalog.synergies.getDouble("thousand_swords.side-angle-radians");
        for (double offset : new double[] {0, -angle, angle}) {
            var direction = forward.clone().rotateAroundY(offset);
            var victim = ray(player, start, direction, catalog.synergies.getDouble("thousand_swords.reach"));
            if (victim != null && syntheticHit(player, victim, "thousand_swords", catalog.synergies.getDouble("thousand_swords.damage"))) {
                phantom(player, victim, state);
            }
            if (catalog.synergies.getBoolean("thousand_swords.particles")) particle(player, state, Particle.SWEEP_ATTACK, start.clone().add(direction));
        }
    }

    private boolean syntheticHit(Player source, LivingEntity victim, String id, double damage) {
        if (!valid(source) || !opponent(source, victim)) return false;
        Fighter state = fighters.get(source.getUniqueId());
        boolean crit = owns(source, "jeweled_gauntlet")
                && (!id.equals("infernal_conductor") || state.hellLord) && random.nextDouble() < catalog.setting("crit-chance");
        double points = Math.min(catalog.setting("max-extra-damage"), crit ? damage * rule("jeweled_gauntlet").amount() : damage);
        var root = chain.currentRoot();
        var budget = root != null && victim instanceof Player ? root.budget() : null;
        double admitted = points;
        if (budget != null) {
            var offer = budget.offer(victim.getUniqueId(), points);
            admitted = offer.allowed();
            budgetOffers++;
            budgetRaw += points;
            if (offer.overBudget()) budgetOver++;
            if (admitted < points) budgetReduced++;
        }
        double appliedPoints = admitted;
        boolean hit = chain.hit(match, source.getUniqueId(), victim.getUniqueId(), id, appliedPoints, crit, () -> {
            syntheticConfirmed = false;
            syntheticFinalDamage = 0;
            try {
                if (id.equals("ricochet_arrow")) {
                    var arrow = org.bukkit.damage.DamageSource.builder(DamageType.ARROW)
                            .withCausingEntity(source).withDirectEntity(source).build();
                    victim.damage(appliedPoints, arrow);
                } else victim.damage(appliedPoints, source);
                return syntheticConfirmed;
            } finally {
                if (audit.sampleAvailable()) audit.sample(now(), "root=" + chain.current().original()
                        + " attacker=" + audit.actor(source.getUniqueId())
                        + " victim=" + (victim instanceof Player ? audit.actor(victim.getUniqueId()) : victim.getType())
                        + " effect=" + id + " depth=" + chain.current().depth() + " raw=" + appliedPoints
                        + " confirmed=" + syntheticConfirmed + " final_event_damage=" + syntheticFinalDamage
                        + " health_after=" + victim.getHealth());
            }
        });
        if (hit) {
            var form = switch (id) {
                case "sword_wind","draw_the_sword","thousand_swords","storm_blade","dual_wielder","ultimate_hydra" -> Form.ARC;
                case "magic_missile","ricochet_arrow","split_bow","storm_chaser","infinite_barrage" -> Form.BOLT;
                case "earthquake_mage","calamity_giant","calamity_train","steel_king","bomb_alchemy","delayed_blast" -> Form.BLAST;
                case "void_lord","hell_lord" -> Form.VOID;
                default -> Form.HIT;
            };
            var impact = victim.getLocation().add(0,Math.min(1.1,victim.getHeight() * .65),0);
            var approach = impact.toVector().subtract(source.getLocation().toVector());
            if (!approach.isZero()) impact.setDirection(approach);
            art.combat(source,victim instanceof Player target ? target : null,root == null ? null : root.id(),id,form,impact,0);
            if (!id.equals("triad_reactor")) triad(source, state, Action.REACTION);
            if (crit) art.cue(source,"jeweled_gauntlet");
            audit.add(id, Metric.PROC, 1);
            announceProc(source, id);
            var evolution = AugmentEvolution.of(id);
            if (evolution != null && evolution.complete(evolution.progress(state.progress,state.burned),evolutionThreshold(evolution))
                    && source.hasLineOfSight(victim))
                art.impact(source,id,victim.getLocation().add(0,.6,0));
            audit.add(id, Metric.DAMAGE, syntheticFinalDamage);
        }
        if (budget != null) {
            if (hit) {
                budgetHits++;
                budgetFinal += syntheticFinalDamage;
            } else budget.refund(victim.getUniqueId(), appliedPoints);
        }
        return hit;
    }

    private void announceProc(Player player, String id) {
        var definition = catalog.rules.get(id);
        String name = definition != null ? definition.definition().name() : catalog.synergies.getString(id + ".name");
        if (name != null) hud.activated(player,name);
        art.cue(player,id);
        var state = fighters.get(player.getUniqueId());
        if (state == null) return;
        String synergy = switch (id) {
            case "magic_missile" -> state.barrage ? "infinite_barrage" : null;
            case "center_of_universe", "infernal_conductor" -> state.hellLord ? "hell_lord" : null;
            default -> AugmentCombinations.UNIQUE.containsKey(id) ? id : null;
        };
        if (synergy != null) synergyVisual(player, state, synergy);
    }

    private void synergyVisual(Player player, Fighter state, String synergy) {
        long time = millis();
        if (time - state.lastVisual.getOrDefault(synergy, -1000L) < 1000) return;
        state.lastVisual.put(synergy, time);
        int color = catalog.synergies.getInt(synergy + ".color");
        Location center = player.getLocation().add(0, 1, 0);
        if (catalog.synergies.getBoolean(synergy + ".particles")) {
            art.growth(player,synergy,true);
            var dust = new Particle.DustOptions(org.bukkit.Color.fromRGB(color), 1);
            for (int i = 0; i < 8 && particleAvailable(state); i++) {
                double angle = i * Math.PI / 4;
                var point = center.clone().add(Math.cos(angle),0,Math.sin(angle));
                if (PlayerUi.enabled(player,PlayerUi.Option.PARTICLES)) player.spawnParticle(Particle.DUST,point,1,dust);
            }
        }
        if (PlayerUi.enabled(player,PlayerUi.Option.SOUND) && catalog.synergies.getBoolean(synergy + ".sound"))
            player.playSound(center, catalog.synergies.getString(synergy + ".sound-key"), .55f, 1.2f);
    }

    private void announceSynergy(UUID id, String synergy) {
        audit.add(synergy, Metric.SYNERGY, 1);
        telemetry.synergy(id, synergy);
        Player player = plugin.getServer().getPlayer(id);
        if (player == null) return;
        var state = fighter(id);
        if (!state.growthNotices.add(synergy) || !PlayerUi.enabled(player,PlayerUi.Option.GROWTH)) return;
        synergyVisual(player, state, synergy);
        String message = "시너지 활성화 · " + catalog.synergies.getString(synergy + ".name", synergy);
        if (state.completingMember != null && AugmentCombinations.UNIQUE.get(synergy).contains(state.completingMember))
            player.sendMessage(message + " · 마지막 구성원 " + rule(state.completingMember).definition().name());
        else player.sendMessage(message);
        if (catalog.synergies.getBoolean("announcements.broadcast")) {
            for (Player recipient : plugin.getServer().getOnlinePlayers())
                if (!recipient.equals(player) && PlayerUi.enabled(recipient,PlayerUi.Option.GROWTH)) recipient.sendMessage(player.getName() + " · " + message);
        }
    }

    private void checkGrowth(Player player, Fighter state) {
        for (var evo : AugmentEvolution.values()) {
            if (!owns(player,evo.augment) || state.growthNotices.contains(evo.augment)) continue;
            double goal = evolutionThreshold(evo);
            double progress = evo.progress(state.progress,state.burned);
            if (progress < goal && progress >= .9 * goal && state.growthNotices.add("near:" + evo.augment)
                    && PlayerUi.enabled(player,PlayerUi.Option.GROWTH))
                player.sendMessage("진화 임박 · " + rule(evo.augment).definition().name() + " · " + evolutionLine(player.getUniqueId(),evo));
        }
    }

    private void evolution(Player player, Fighter state, String id) {
        telemetry.evolution(player.getUniqueId(), id, clock.get().elapsedSeconds());
        if (!state.growthNotices.add(id) || !PlayerUi.enabled(player,PlayerUi.Option.GROWTH)) return;
        announce(player.getUniqueId(), "증강 진화 · " + rule(id).definition().name(),false);
        if (catalog.config.getBoolean("settings.evolution-sound")) art.sound(player,"evolve",Sound.ENTITY_PLAYER_LEVELUP);
        if (catalog.config.getBoolean("settings.evolution-particles")) {
            art.growth(player,id,true);
            particle(player, state, Particle.END_ROD, player.getLocation().add(0, 1, 0));
        }
    }

    private void announce(UUID id, String message, boolean sound) {
        Player player = plugin.getServer().getPlayer(id);
        if (player == null) return;
        player.sendMessage(message);
        if (sound && PlayerUi.enabled(player,PlayerUi.Option.SOUND)) player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .6f, 1.3f);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void mobDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player fallen) {
            var state = fighters.get(fallen.getUniqueId());
            if (state != null) clearReactions(fallen, state);
        }
        Player killer = event.getEntity().getKiller();
        if (killer != null && valid(killer)) {
            Fighter state = fighters.get(killer.getUniqueId());
            if (state != null) onMarkedKill(killer, event.getEntity(), state);
            var burn = state == null ? null : state.burns.remove(event.getEntity().getUniqueId());
            if (burn != null && burn.expiresAt() >= now()) {
                double range = catalog.setting(burnEvolved(state) ? "burn-evolved-spread-radius" : "burn-spread-radius");
                for (var target : opponents(killer, event.getEntity().getLocation(), range)) {
                    if (!event.getEntity().equals(target)) ignite(state, target, burn.stacks());
                }
            }
        }
        if (killer != null && event.getEntity() instanceof Player fallen && valid(killer)
                && !killer.equals(fallen) && member.test(fallen.getUniqueId()) && arena.test(fallen.getLocation()) && countedKills.add(event)) {
            var state = fighters.get(killer.getUniqueId());
            if (state != null) {
                if (owns(killer, "heart_harvester")) {
                    grant(state, "heart_harvester", catalog.count("heart-harvester-player-stacks"));
                    gainHealth(killer,catalog.setting("heart-harvester-player-heal"),"heart_harvester");
                }
                if (owns(killer, "endless_evolution")) {
                    grant(state, "endless_evolution", catalog.count("endless-evolution-player-stacks"));
                }
                if (owns(killer, "blood_god")) grant(state, "blood_god", "blood_god_kills", 1, catalog.count("blood-god-kill-cap"));
                if (owns(killer, "blood_crown")) {
                    grantAbsorption(killer, "blood-crown-absorption");
                }
            }
        }
        if (match == null || synthetic() || !(event.getEntity() instanceof Enemy)
                || event.getEntity().getEntitySpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        LivingEntity monster = event.getEntity();
        Player player = monster.getKiller();
        if (player == null || !valid(player) || !arena.test(monster.getLocation()) || deadMobs.size() >= 65_536
                || !deadMobs.add(monster.getUniqueId())) return;
        for (String id : List.of("blood_record", "muscle_memory", "heart_harvester", "endless_evolution")) {
            if (owns(player, id)) progress(player, id);
        }
    }

    private void onMarkedKill(Player killer, LivingEntity killed, Fighter state) {
        UUID id = killed.getUniqueId();
        if (killed instanceof Player && !member.test(id) || !arena.test(killed.getLocation())) return;
        if (owns(killer, "bounty_hunter") && id.equals(state.bountyTarget) && state.bountyUntil > now()) {
            state.bountyTarget = null;
            gainHealth(killer,catalog.setting("bounty-heal"),"bounty_hunter");
            killer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, catalog.count("bounty-speed-seconds") * 20, 1));
        }
        Long poisonedUntil = state.poisoned.remove(id);
        if (owns(killer, "venom_swarm") && poisonedUntil != null && poisonedUntil > now() && ready(killer, state, "venom_swarm")) {
            Location center = killed.getLocation();
            afterDamage(killer, () -> state.poisonClouds.add(new PoisonCloud(center,
                    now() + catalog.count("venom-cloud-duration"), new HashSet<>(), chain.currentRoot())));
        }
    }

    private void tickPoisonClouds(Player player, Fighter state) {
        for (var cloud : List.copyOf(state.poisonClouds)) {
            if (cloud.expiresAt() <= now() || !arena.test(cloud.center())) {
                state.poisonClouds.remove(cloud);
                continue;
            }
            chain.root(cloud.root(), () -> {
                for (var target : opponents(player, cloud.center(), catalog.setting("venom-radius"))) {
                    if (!cloud.affected().add(target.getUniqueId())) continue;
                    if (syntheticHit(player, target, "venom_swarm", rule("venom_swarm").amount())) applyVenom(state, target);
                }
            });
            particle(player, state, Particle.WITCH, cloud.center().clone().add(0, .3, 0));
        }
    }

    private void progress(Player player, String id) {
        var state = fighter(player.getUniqueId());
        if (every(state, id)) grant(state, id, 1);
    }

    private boolean ore(Block block) {
        return block.getType().name().endsWith("_ORE") || block.getType() == Material.ANCIENT_DEBRIS;
    }

    private NaturalOreTracker.Site site(Block block) {
        return new NaturalOreTracker.Site(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    private void invalidate(Block block) {
        if (match != null && arena.test(block.getLocation()) && ore(block)) ores.invalidate(site(block));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void place(BlockPlaceEvent event) { invalidate(event.getBlockPlaced()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void breakOre(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (match == null || !arena.test(block.getLocation()) || !ore(block) || !ores.consume(site(block))) return;
        Player player = event.getPlayer();
        if (!valid(player) || !event.isDropItems() || player.getGameMode() != org.bukkit.GameMode.SURVIVAL) return;
        if (owns(player, "ore_ward")) {
            var s = fighter(player.getUniqueId());
            var ward = reaction(s, "ore_ward");
            if (ward.charges() < rule("ore_ward").cap() && every(s, "ore_ward")) {
                ward.store(rule("ore_ward").cap());
                hud.proc(player, "광맥 방벽 · " + ward.charges());
                art.cue(player,"ore_ward");
            }
        }
        for (String id : List.of("mineral_addiction", "mineral_devourer", "vein_overload")) {
            if (owns(player, id)) progress(player, id);
        }
    }

    private void shift(List<Block> blocks, org.bukkit.block.BlockFace facing) {
        for (Block block : blocks) {
            if (match == null || !ore(block)) continue;
            for (Block candidate : List.of(block, block.getRelative(facing), block.getRelative(facing.getOppositeFace()))) {
                if (arena.test(candidate.getLocation())) ores.invalidate(site(candidate));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void extend(BlockPistonExtendEvent event) { shift(event.getBlocks(), event.getDirection()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void retract(BlockPistonRetractEvent event) { shift(event.getBlocks(), event.getDirection()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void explode(EntityExplodeEvent event) { event.blockList().forEach(this::invalidate); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void explode(BlockExplodeEvent event) { event.blockList().forEach(this::invalidate); }

    private void grantAbsorption(Player player, String setting) {
        double amount = catalog.setting(setting);
        addAbsorption(player, amount, amount);
    }

    private void addAbsorption(Player player, double points, double cap) {
        double previous = player.getAbsorptionAmount();
        double total = Math.max(previous, Math.min(cap, previous + points));
        int amplifier = Math.max(0, (int) Math.ceil(total / 4) - 1);
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, catalog.count("blood-crown-absorption-seconds") * 20, amplifier));
        player.setAbsorptionAmount(total);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void defendAndRevive(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !valid(player) || !defended.add(event)) return;
        evadeOrResist(event);
        if (event.isCancelled()) return;
        if (event.getDamageSource().getDamageType() != DamageType.OUTSIDE_BORDER
                && event.getDamageSource().getDamageType() != DamageType.OUT_OF_WORLD) {
            var state = fighters.get(player.getUniqueId());
            if (state != null && !shieldSpends.containsKey(event)) {
                defendReactions(event, player, state);
                var spent = PaperResonanceShield.protect(event, state.shield, clock.get().elapsedNanos());
                if (spent != null) {
                    shieldSpends.put(event, spent);
                    wardSpent.computeIfPresent(event, (ignored, base) -> spent.protectedBase());
                }
            }
        }
        revive(event);
    }

    private final Map<EntityDamageEvent, Double> wardSpent = new WeakHashMap<>();

    private void defendReactions(EntityDamageEvent event, Player player, Fighter state) {
        if (event instanceof EntityDamageByEntityEvent hit && event.getFinalDamage() > 0) {
            Player source = attacker(hit);
            boolean enemy = source != null && valid(source) && opponent(source, player)
                    || hit.getDamageSource().getCausingEntity() instanceof Enemy mob && arena.test(mob.getLocation());
            if (enemy) {
                var bastion = state.reactions.get("last_bastion");
                if (bastion != null && bastion.charged(millis()))
                    PaperResonanceShield.reduce(event, event.getFinalDamage() * rule("last_bastion").amount());
                var ward = state.reactions.get("ore_ward");
                if (ward != null && ward.charges() > 0 && ward.ready(millis()) && event.getFinalDamage() > 0 && ward.reserve(event)) {
                    reservedWards.put(event, ward);
                    var reduced = PaperResonanceShield.reduce(event, rule("ore_ward").amount());
                    if (reduced != null) wardSpent.put(event, reduced.protectedBase());
                }
            }
        }
    }

    private void evadeOrResist(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !valid(player) || event.getFinalDamage() <= 0) return;
        var state = fighters.get(player.getUniqueId());
        if (state == null) return;
        if (event instanceof EntityDamageByEntityEvent hit) {
            var attacker = attacker(hit);
            boolean threat = attacker != null && valid(attacker) && opponent(attacker, player)
                    || hit.getDamager() instanceof Enemy && arena.test(hit.getDamager().getLocation());
            if (threat && owns(player, "reflex") && state.progress.ready("reflex", now())) {
                Location from = attacker == null ? hit.getDamager().getLocation() : attacker.getLocation();
                var direction = from.toVector().subtract(player.getLocation().toVector());
                var target = PaperAugmentMovement.toward(player, direction, catalog.setting("reflex-max-distance"), safe);
                if ((target != null && teleport.test(player, target)) || catalog.config.getBoolean("settings.reflex-fallback-dodge")) {
                    ready(player, state, "reflex");
                    art.cue(player,"reflex");
                    if (follow("reflex")) charge(player, state, "reflex");
                    triad(player, state, Action.REACTION);
                    event.setCancelled(true);
                    return;
                }
            }
            if (threat && owns(player, "mad_scientist") && small(state, now()) && random.nextDouble() < catalog.setting("mad-small-dodge-chance")) {
                event.setCancelled(true);
                return;
            }
        }
        if (state.duelTarget != null && state.duelUntil > now()) {
            event.setDamage(event.getDamage() * (1 + catalog.setting("duel-damage-taken")));
        }
        if (owns(player, "immortal_armor") && ready(player, state, "immortal_armor")
                && event.getDamageSource().getDamageType() != DamageType.OUTSIDE_BORDER
                && event.getDamageSource().getDamageType() != DamageType.OUT_OF_WORLD) {
            double absorption = Math.min(event.getFinalDamage() * rule("immortal_armor").amount(),
                    catalog.setting("immortal-armor-max"));
            event.setDamage(Math.max(0, event.getDamage() * (1 - absorption / event.getFinalDamage())));
            afterDamage(player, () -> {
                double previous = player.getAbsorptionAmount();
                addAbsorption(player,absorption,catalog.setting("immortal-armor-max"));
                if (player.getAbsorptionAmount() > previous) art.cue(player,"immortal_armor");
            });
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && owns(player, "falling_shockwave")
                && player.getFallDistance() >= rule("falling_shockwave").threshold() && ready(player, state, "falling_shockwave")) {
            event.setDamage(event.getDamage() * (1 - catalog.setting("fall-shock-reduction")));
            afterDamage(player, () -> area(player, player.getLocation(), catalog.setting("fall-shock-radius"),
                    "falling_shockwave", rule("falling_shockwave").amount(), other -> {
                        var direction = other.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
                        if (direction.lengthSquared() > 0) other.setVelocity(direction.normalize().multiply(catalog.setting("fall-shock-knockback")));
                    }));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void damageTaken(EntityDamageEvent event) {
        var reserved = reservedWards.remove(event);
        if (reserved != null) reserved.release(event);
        var spent = shieldSpends.remove(event);
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player) || !valid(player)) return;
        var state = fighters.get(player.getUniqueId());
        if (state == null || !countedWounds.add(event)) return;
        if (spent != null) {
            double confirmed = spent.confirm(event);
            state.shield.consume(clock.get().elapsedNanos(), confirmed);
            if (confirmed > 0) {
                reviewShields.put(event, confirmed);
                if (state.shield.remaining(clock.get().elapsedNanos()) == 0)
                    art.impact(player,"ward",player.getLocation().add(0,.85,0));
            }
            audit.add("resonance_revival", Metric.SHIELDED, confirmed);
            if (audit.sampleAvailable()) audit.sample(now(), "shield actor=" + audit.actor(player.getUniqueId())
                    + " reserved=" + spent.reserved() + " absorbed=" + confirmed);
            updateShieldDisplay(player, state);
        }
        Double wardBase = wardSpent.remove(event);
        if (wardBase != null && event.getDamage() == wardBase && state.reactions.get("ore_ward") == reserved) {
            var ward = reaction(state, "ore_ward");
            ward.spendWard(millis(), interval("ore_ward", "cooldown"));
            visual(player, state, "ore_ward", player.getLocation(), Particle.CRIT, Sound.ITEM_SHIELD_BLOCK);
            art.combat(player,null,null,"ore_ward",Form.GUARD,player.getEyeLocation(),0);
            hud.proc(player, "광맥 방벽 · 잔여 " + ward.charges());
            triad(player, state, Action.REACTION);
        }
        if (!synthetic() && event instanceof EntityDamageByEntityEvent hit && event.getFinalDamage() > 0) {
            Player source = attacker(hit);
            boolean melee = event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK && hit.getDamager() == source;
            if (source != null && valid(source) && opponent(source, player)
                    && (melee || event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE)) {
                String id = "kinetic_reversal";
                if (melee && owns(player, id) && Math.min(player.getHealth(), event.getFinalDamage()) >= rule(id).threshold()
                        && reaction(state, id).ready(millis())) charge(player, state, id);
                String fort = "last_bastion";
                if (owns(player, fort) && reaction(state, fort).ready(millis())
                        && reaction(state, fort).accumulate(player.getUniqueId(), millis(), interval(fort, "window"), 1, rule(fort).threshold())) {
                    charge(player, state, fort);
                    later(state, fort, interval(fort, "duration"), () -> {
                        visual(player, state, fort, player.getLocation(), Particle.EXPLOSION, Sound.ENTITY_GENERIC_EXPLODE);
                        art.combat(player,null,chain.currentRoot().id(),fort,Form.BLAST,player.getLocation().add(0,.2,0),option(fort,"radius"));
                        area(player, player.getLocation(), option(fort, "radius"), fort, option(fort, "pulse"),
                                other -> push(player, other, option(fort, "push")));
                    });
                }
            }
        }
        if (owns(player, "counter_shield") && event.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)
                && event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING) < 0 && ready(player, state, "counter_shield")) {
            state.counterReady = true;
            art.cue(player,"counter_shield");
            triad(player, state, Action.REACTION);
            afterDamage(player, () -> gainHealth(player,catalog.setting("shield-heal"),"counter_shield"));
        }
        if (event.getFinalDamage() <= 0) return;
        if (owns(player, "wound_record")) {
            var rule = rule("wound_record");
            state.woundDamage += Math.min(event.getFinalDamage(), player.getHealth());
            int stacks = (int) (state.woundDamage / rule.threshold());
            if (stacks > 0) {
                state.woundDamage %= rule.threshold();
                grant(state, "wound_record", stacks);
            }
        }
        if (player.getHealth() > event.getFinalDamage()) state.instinctPending = true;
    }

    private void reactAtLowHealth(Player player, Fighter state) {
        double fraction = player.getHealth() / maxHealth(player);
        if (owns(player, "final_judgment") && fraction <= catalog.setting("judgment-low-health") && ready(player, state, "final_judgment")) {
            Location point = player.getLocation();
            var rule = rule("final_judgment");
            state.zones.add(new Zone("final_judgment", point, point, now() + rule.duration(), (1 - fraction) * rule.amount()));
            art.cue(player,"final_judgment");
            player.sendMessage("최후의 심판 · " + rule.duration() + "초 후 발동");
            for (var enemy : opponents(player, point, catalog.setting("judgment-radius"))) {
                if (enemy instanceof Player nearby) nearby.sendMessage("최후의 심판 예정 지역입니다. " + rule.duration() + "초 후 발동합니다.");
            }
        }
        if (owns(player, "wound_record") && fraction <= catalog.setting("wound-low-health") && ready(player, state, "wound_record")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, rule("wound_record").duration() * 20, 0));
        }
        if (owns(player, "last_demon") && fraction <= catalog.setting("last-demon-low-health") && ready(player, state, "last_demon")) {
            int seconds = rule("last_demon").duration();
            state.lastDemonUntil = now() + seconds;
            art.cue(player,"last_demon");
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, seconds * 20, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, seconds * 20, 0));
        }
        if (owns(player, "survival_instinct") && fraction <= catalog.setting("instinct-low-health") && ready(player, state, "survival_instinct")) {
            gainHealth(player,rule("survival_instinct").amount(),"survival_instinct");
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, rule("survival_instinct").duration() * 20, 1));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void eat(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Material food = event.getItem().getType();
        if (!valid(player) || !food.isEdible() || !owns(player, "iron_stomach")) return;
        var state = fighter(player.getUniqueId());
        if (state.food.add(food) && state.food.size() % rule("iron_stomach").threshold() == 0) {
            grant(state, "iron_stomach", 1);
        }
        if (ready(player, state, "iron_stomach")) {
            gainHealth(player,catalog.setting("iron-stomach-heal"),"iron_stomach");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void naturalRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player) || !active.test(player)) return;
        var state = fighters.get(player.getUniqueId());
        if (state != null && state.naturalRegenBlockedUntil > now()
                && (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED
                    || event.getRegainReason() == EntityRegainHealthEvent.RegainReason.REGEN)) event.setCancelled(true);
    }

    private void revive(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !valid(player) || phase.get() == MatchState.SUDDEN_DEATH
                || event.getDamageSource().getDamageType() == DamageType.OUTSIDE_BORDER
                || event.getDamageSource().getDamageType() == DamageType.OUT_OF_WORLD
                || event.getFinalDamage() < player.getHealth()) return;
        var options = new ArrayList<ReviveResolver.Option>(3);
        for (String id : List.of("immortal_berserker", "phoenix_heart", "russian_doll")) {
            if (owns(player, id)) options.add(new ReviveResolver.Option(id,
                    id.equals("immortal_berserker") ? rule(id).cap() : rule(id).threshold()));
        }
        if (options.isEmpty()) return;
        var state = fighter(player.getUniqueId());
        UUID id = player.getUniqueId();
        var shield = currentResonance(id, owned.apply(id));
        String applied = ReviveResolver.resolve(state.progress, options, candidate -> {
            if (!active.test(player) || player.getHealth() <= 0) return false;
            double restore = switch (candidate) {
                case "phoenix_heart" -> maxHealth(player) * rule(candidate).amount();
                case "russian_doll" -> maxHealth(player) * (1 - rule(candidate).amount());
                default -> 1;
            };
            player.setHealth(Math.clamp(restore, 1, maxHealth(player)));
            return player.getHealth() > 0;
        });
        if (applied == null) return;
        hud.rescued(player,rule(applied).definition().name());
        audit.add(applied, Metric.REVIVED, 1);
        announceProc(player, applied);
        art.combat(player,null,null,applied,Form.RESCUE,player.getLocation().add(0,.6,0),0);
        if (audit.sampleAvailable()) audit.sample(now(), "revive actor=" + audit.actor(id) + " effect=" + applied
                + " health_after=" + player.getHealth() + " shield_grant=" + shield.shieldPoints());
        event.setCancelled(true);
        shieldSpends.remove(event);
        if (shield.shieldPoints() > 0) {
            state.shield.grant(clock.get().elapsedNanos(), shield.shieldPoints(), shield.shieldNanos());
            hud.record(player,"생명의 잔향 보호막 +" + shield.shieldPoints() + " HP");
            updateShieldDisplay(player, state);
            if (resonance.announce() && PlayerUi.enabled(player,PlayerUi.Option.GROWTH)) art.growth(player,"life_echo",resonance.particles());
            if (resonance.announce() && resonance.sound() && PlayerUi.enabled(player,PlayerUi.Option.SOUND)) player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .45f, 1.4f);
            if (resonance.announce() && resonance.particles()) particle(player, state, Particle.END_ROD, player.getLocation().add(0, 1, 0));
        }
        if (applied.equals("immortal_berserker")) {
            state.immortalUntil = Math.max(now(), state.immortalUntil) + catalog.count("immortal-berserker-revive-extension");
        } else if (applied.equals("phoenix_heart")) {
            protection.accept(id, rule(applied).duration());
            double radius = catalog.setting("revive-knockback-range");
            for (var entity : player.getNearbyEntities(radius, radius, radius)) {
                if (!(entity instanceof Player other) || !opponent(player, other)) continue;
                var direction = other.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
                if (direction.lengthSquared() > 0 && direction.lengthSquared() <= radius * radius) {
                    other.setVelocity(direction.normalize().multiply(catalog.setting("revive-knockback-strength")).setY(.25));
                }
            }
        } else {
            applyAttributes(player, state, now());
        }
        announce(id, "치명 피해 방지 · " + rule(applied).definition().name(), true);
    }
}
