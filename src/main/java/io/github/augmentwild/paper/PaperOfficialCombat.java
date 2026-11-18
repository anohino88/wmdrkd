package io.github.augmentwild.paper;

import io.github.augmentwild.augment.AugmentProgress;
import io.github.augmentwild.augment.AugmentRegistry;
import io.github.augmentwild.augment.DamageChain;
import io.github.augmentwild.augment.NaturalOreTracker;
import io.github.augmentwild.augment.MovementProgress;
import io.github.augmentwild.augment.ReviveResolver;
import io.github.augmentwild.domain.MatchSimulationClock;
import io.github.augmentwild.match.MatchState;
import java.util.*;
import java.util.function.*;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
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
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
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

final class PaperOfficialCombat implements Listener {
    private static final Set<String> THOUSAND_SWORDS = Set.of("sword_wind", "ultimate_hydra", "dual_wielder");
    private static final Set<String> HELL_LORD = Set.of("infernal_conductor", "center_of_universe", "jeweled_gauntlet");
    private static final Set<String> INFINITE_BARRAGE = Set.of("magic_missile", "eureka", "infinite_repeat");
    private static final Set<String> STEEL_KING = Set.of("steel_heart", "stack_dinosaur", "heavy_fist");
    private static final List<Attribute> ATTRIBUTES = List.of(Attribute.MAX_HEALTH, Attribute.MOVEMENT_SPEED, Attribute.SCALE);
    private final JavaPlugin plugin;
    private final OfficialAugments catalog;
    private final Supplier<MatchSimulationClock> clock;
    private final Supplier<MatchState> phase;
    private final Predicate<Player> active, protectedPlayer;
    private final Predicate<UUID> member;
    private final Predicate<Location> arena;
    private final BiConsumer<UUID, Integer> protection;
    private final Function<UUID, Set<String>> owned;
    private final DamageChain chain;
    private final NaturalOreTracker ores;
    private final Map<UUID, Fighter> fighters = new HashMap<>();
    private final Map<EntityDamageEvent, Double> rawDamage = new WeakHashMap<>();
    private final Set<UUID> splitArrows = new HashSet<>();
    private final Set<EntityDamageEvent> splitEvents = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<EntityDamageEvent> counted = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<EntityDeathEvent> countedKills = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<UUID> deadMobs = new HashSet<>();
    private Random random;
    private UUID match;
    private boolean syntheticConfirmed;
    private double syntheticFinalDamage;

    private record MissileShot(PaperMissiles.Missile missile, DamageChain.Root root) { }

    private static final class Fighter {
        final AugmentProgress progress = new AugmentProgress();
        int overgrowthAward;
        long acquiredGrowth, lastBerserkHit = -1, lastSecond = -1;
        final MovementProgress movement = new MovementProgress();
        double stormDistance;
        final Set<Material> food = EnumSet.noneOf(Material.class);
        long immortalUntil;
        final List<MissileShot> missiles = new ArrayList<>();
        long particleSecond = -1;
        int particles;
        boolean barrage, hellLord, thousandSwords;
        final PaperAugmentBurns burns = new PaperAugmentBurns();
        double burned, woundDamage;
        UUID frenzyTarget;
        long frenzyAt;
        boolean instinctPending, veinGranted, transformedSmall;
        long acquiredMad, lastDemonUntil;
        long traceUntil;
        UUID traceTarget, bountyTarget;
        long bountyUntil;
        boolean counterReady;
        final Map<UUID, Long> poisoned = new HashMap<>();
        int forcedAt;
        boolean hadVelocity, king, train, apple;
    }

    PaperOfficialCombat(JavaPlugin plugin, AugmentRegistry registry, Supplier<MatchSimulationClock> clock,
                        Supplier<MatchState> phase, Predicate<UUID> member, Predicate<Player> active, Predicate<Player> protectedPlayer,
                        Predicate<Location> arena, BiConsumer<UUID, Integer> protection,
                        Function<UUID, Set<String>> owned, int depth) {
        this.plugin = plugin;
        this.clock = clock;
        this.phase = phase;
        this.member = member;
        this.active = active;
        this.protectedPlayer = protectedPlayer;
        this.arena = arena;
        this.protection = protection;
        this.owned = owned;
        catalog = new OfficialAugments(plugin, registry);
        ores = new NaturalOreTracker(catalog.count("ore-sites"));
        chain = new DamageChain(depth, catalog.count("max-hits-per-target"));
    }

    void start(UUID id) {
        match = id;
        random = new Random(id.getMostSignificantBits() ^ id.getLeastSignificantBits());
    }
    boolean synthetic() { return chain.current() != null; }
    double rawDamage(EntityDamageEvent event) { return rawDamage.getOrDefault(event, event.getDamage()); }
    private long now() { return clock.get().elapsedSeconds(); }
    private OfficialAugments.Rule rule(String id) { return catalog.rule(id); }
    private boolean owns(Player player, String id) { return owned.apply(player.getUniqueId()).contains(id); }
    private boolean valid(Player player) {
        return match != null && player.isOnline() && active.test(player) && !protectedPlayer.test(player) && !clock.get().isPaused();
    }
    private boolean opponent(Player player, LivingEntity target) {
        if (target.equals(player) || target.isDead() || !target.getWorld().equals(player.getWorld())) return false;
        if (target instanceof Player rival) return valid(rival);
        return target instanceof Monster && arena.test(target.getLocation());
    }

    private Fighter fighter(UUID id) { return fighters.computeIfAbsent(id, key -> new Fighter()); }

    void acquired(UUID id, String augment) {
        if (!catalog.rules.containsKey(augment) || !rule(augment).definition().implemented()) return;
        var state = fighter(id);
        if (augment.equals("overgrowth")) {
            state.acquiredGrowth = now();
            state.overgrowthAward = (int) (now() >= catalog.count("overgrowth-late-second")
                    ? catalog.setting("overgrowth-late-health") : rule(augment).amount());
        }
        if (augment.equals("mad_scientist")) state.acquiredMad = now();
        if (augment.equals("endless_evolution") && now() >= catalog.count("endless-evolution-late-second")) {
            state.progress.add(augment, catalog.count("endless-evolution-late-stacks"), rule(augment).cap());
        }
        boolean king = catalog.synergies.getBoolean("steel_king.enabled") && owned.apply(id).containsAll(STEEL_KING);
        if (king && !state.king) announce(id, "시너지 활성화 · 강철의 왕", catalog.synergies.getBoolean("steel_king.sound"));
        state.king = king;
        boolean train = catalog.synergies.getBoolean("calamity_train.enabled")
                && owned.apply(id).containsAll(Set.of("tank_engine", "calamity_giant", "berserk_engine"));
        if (train && !state.train) announce(id, "시너지 활성화 · 재앙의 기관차", catalog.synergies.getBoolean("calamity_train.sound"));
        state.train = train;
        boolean barrage = catalog.synergies.getBoolean("infinite_barrage.enabled") && owned.apply(id).containsAll(INFINITE_BARRAGE);
        if (barrage && !state.barrage) announce(id, "시너지 활성화 · 무한 포격", catalog.synergies.getBoolean("infinite_barrage.sound"));
        state.barrage = barrage;
        boolean hellLord = catalog.synergies.getBoolean("hell_lord.enabled") && owned.apply(id).containsAll(HELL_LORD);
        if (hellLord && !state.hellLord) announce(id, "시너지 활성화 · 지옥의 군주", catalog.synergies.getBoolean("hell_lord.sound"));
        state.hellLord = hellLord;
        boolean swords = catalog.synergies.getBoolean("thousand_swords.enabled") && owned.apply(id).containsAll(THOUSAND_SWORDS);
        if (swords && !state.thousandSwords) announce(id, "시너지 활성화 · 천 개의 검", catalog.synergies.getBoolean("thousand_swords.sound"));
        state.thousandSwords = swords;
    }

    void remove(UUID id, Player player) {
        if (player != null) resetAttributes(player);
        fighters.remove(id);
    }

    void clear() {
        for (UUID id : fighters.keySet()) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) resetAttributes(player);
        }
        fighters.clear();
        ores.clear();
        deadMobs.clear();
        rawDamage.clear();
        counted.clear();
        splitEvents.clear();
        splitArrows.clear();
        countedKills.clear();
        match = null;
    }

    void disconnected(Player player) {
        Fighter state = fighters.get(player.getUniqueId());
        if (state != null) {
            state.missiles.clear();
            state.burns.clear();
        }
        resetAttributes(player);
    }

    int cooldown(Player player, int base) {
        if (base == 0 || !owns(player, "eureka")) return base;
        var rule = rule("eureka");
        return (int) Math.ceil(base * (1 - Math.min(.95, rule.amount()
                * Math.min(rule.cap(), player.getLevel() / rule.threshold()))));
    }

    List<String> details(UUID id, String augment) {
        if (!catalog.rules.containsKey(augment)) return List.of();
        var state = fighters.get(id);
        var rule = rule(augment);
        int stacks = state == null ? 0 : state.progress.value(augment);
        var lines = new ArrayList<String>();
        if (rule.cap() > 0) lines.add("중첩 " + stacks + "/" + rule.cap());
        String progress = switch (augment) {
            case "infernal_conductor" -> String.format(Locale.ROOT, "지옥의 전도체 진화 %.1f/%.1f",
                    state == null ? 0 : state.burned, catalog.setting("burn-evolution-damage"));
            case "magic_missile" -> "마법 미사일 진화 " + (state == null ? 0 : state.progress.value("missile_shots"))
                    + "/" + catalog.count("missile-evolution-shots");
            case "tank_engine" -> "탱크 엔진 진화 " + Math.min(stacks, catalog.count("tank-evolution-stacks"))
                    + "/" + catalog.count("tank-evolution-stacks");
            case "steel_heart" -> "강철심장 진화 " + Math.min(stacks, catalog.count("steel-evolution-stacks"))
                    + "/" + catalog.count("steel-evolution-stacks");
            case "sword_wind" -> "검풍 진화 " + (state == null ? 0 : state.progress.value("sword_hits"))
                    + "/" + catalog.count("sword-evolution-hits");
            case "berserk_engine" -> "폭주 기관 진화 " + (state == null ? 0 : state.progress.value("berserk_seconds"))
                    + "/" + catalog.count("berserk-evolution-seconds");
            default -> null;
        };
        if (progress != null) lines.add(progress);
        if (Set.of("tank_engine", "calamity_giant", "berserk_engine").contains(augment)) {
            lines.add("재앙의 기관차 · " + (state != null && state.train ? "활성" : "미활성"));
        }
        if (THOUSAND_SWORDS.contains(augment)) lines.add("천 개의 검 · " + (state != null && state.thousandSwords ? "활성" : "미활성"));
        if (HELL_LORD.contains(augment)) lines.add("지옥의 군주 · " + (state != null && state.hellLord ? "활성" : "미활성"));
        if (INFINITE_BARRAGE.contains(augment)) lines.add("무한 포격 · " + (state != null && state.barrage ? "활성" : "미활성"));
        if (STEEL_KING.contains(augment)) lines.add("강철의 왕 · " + (state != null && state.king ? "활성" : "미활성"));
        return lines;
    }

    private boolean evolved(Fighter state, String id, String key) {
        return state.progress.value(id) >= catalog.count(key);
    }

    private int gain(Player player) {
        return owns(player, "stack_dinosaur") ? (int) Math.clamp(rule("stack_dinosaur").amount(), 1, 10) : 1;
    }

    private double maxHealth(LivingEntity entity) {
        var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? entity.getHealth() : attribute.getValue();
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

    void sync(Player player) {
        Fighter state = fighters.get(player.getUniqueId());
        if (state == null) return;
        if (!active.test(player)) {
            resetAttributes(player);
            state.progress.reset("berserk_engine");
            state.lastBerserkHit = -1;
            state.immortalUntil = 0;
            state.progress.reset("frenzy_rhythm");
            state.frenzyTarget = null;
            state.instinctPending = false;
            state.lastDemonUntil = 0;
            state.traceTarget = null;
            state.bountyTarget = null;
            state.poisoned.clear();
            state.counterReady = false;
            state.missiles.clear();
            state.burns.clear();
            return;
        }
        long second = now();
        if (state.lastBerserkHit >= 0 && second - state.lastBerserkHit >= rule("berserk_engine").duration()) {
            state.progress.reset("berserk_engine");
        }
        if (state.lastSecond != second) {
            state.lastSecond = second;
            if (state.progress.value("berserk_engine") >= rule("berserk_engine").cap()) {
                int limit = catalog.count("berserk-evolution-seconds");
                int past = state.progress.value("berserk_seconds");
                if (past < limit && state.progress.add("berserk_seconds", 1, limit) == limit) {
                    announce(player.getUniqueId(), "증강 진화 · 폭주 기관", true);
                }
            }
        }
        boolean transform = owns(player, "mad_scientist") && small(state, second) != state.transformedSmall;
        double healthFraction = transform ? player.getHealth() / maxHealth(player) : 0;
        applyAttributes(player, state, second);
        if (transform) {
            state.transformedSmall = small(state, second);
            player.setHealth(Math.clamp(healthFraction * maxHealth(player), .01, maxHealth(player)));
        }
        if (valid(player)) {
            advanceMissiles(player, state);
            tickBurns(player, state, second);
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
        }
        if (!state.apple && owns(player, "mineral_devourer")
                && (phase.get() == MatchState.PVP || phase.get() == MatchState.SUDDEN_DEATH)
                && player.getInventory().firstEmpty() >= 0) {
            state.apple = player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE)).isEmpty();
        }
    }

    private boolean small(Fighter state, long second) {
        return (second - state.acquiredMad) / rule("mad_scientist").duration() % 2 == 1;
    }

    private void applyAttributes(Player player, Fighter state, long second) {
        var ids = owned.apply(player.getUniqueId());
        var progress = state.progress;
        double health = 0, speed = 0, scale = 0;
        for (String id : List.of("blood_record", "steel_heart", "iron_stomach", "wound_record", "heart_harvester", "endless_evolution")) {
            if (ids.contains(id)) health += progress.value(id) * rule(id).amount();
        }
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
        modifier(player, Attribute.MAX_HEALTH, "official_health", Math.clamp(health, 0, 400), Operation.ADD_NUMBER);
        double dollFactor = ids.contains("russian_doll") ? Math.pow(1 - rule("russian_doll").amount(), dolls) - 1 : 0;
        modifier(player, Attribute.MAX_HEALTH, "official_doll_health", dollFactor, Operation.MULTIPLY_SCALAR_1);
        modifier(player, Attribute.MOVEMENT_SPEED, "official_speed", Math.clamp(speed, -.5, 2), Operation.ADD_SCALAR);
        modifier(player, Attribute.SCALE, "official_scale", Math.clamp(scale, -.75, 3), Operation.MULTIPLY_SCALAR_1);
        if (player.getHealth() > maxHealth(player)) player.setHealth(maxHealth(player));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void increaseDamage(EntityDamageByEntityEvent event) {
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
        if (melee && state.counterReady && ids.contains("counter_shield")) ratio += rule("counter_shield").amount();
        if (ids.contains("bounty_hunter") && state.bountyUntil > now() && target.getUniqueId().equals(state.bountyTarget)) {
            ratio += rule("bounty_hunter").amount();
        }
        if (event.getDamager() instanceof AbstractArrow arrow && splitArrows.contains(arrow.getUniqueId())) {
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
        if (split) splitEvents.add(event);
        double flat = melee && ids.contains("heavy_fist") ? rule("heavy_fist").amount() * maxHealth(attacker) : 0;
        if (ratio > 0 || flat > 0 || split) {
            double damage = base + Math.min(catalog.setting("max-extra-damage"), (base * ratio) + flat);
            event.setDamage(split ? damage * rule("split_attack").amount() : damage);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void hit(EntityDamageByEntityEvent event) {
        if (synthetic()) {
            var context = chain.current();
            if (context.victim().equals(event.getEntity().getUniqueId()) && event.getFinalDamage() > 0) {
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
        if (owns(attacker, "frenzy_rhythm")) {
            if (!target.getUniqueId().equals(state.frenzyTarget) || now() - state.frenzyAt >= rule("frenzy_rhythm").duration()) {
                state.progress.reset("frenzy_rhythm");
            }
            state.frenzyTarget = target.getUniqueId();
            state.frenzyAt = now();
            state.progress.add("frenzy_rhythm", 1, rule("frenzy_rhythm").cap());
        }
        if (owns(attacker, "bounty_hunter") && ready(attacker, state, "bounty_hunter")) {
            state.bountyTarget = target.getUniqueId();
            state.bountyUntil = now() + rule("bounty_hunter").duration();
        }
        if (owns(attacker, "time_thief") && ready(attacker, state, "time_thief")) stealTime(attacker, target);
        if (owns(attacker, "blood_trace")) {
            if (target.getUniqueId().equals(state.traceTarget) && state.traceUntil > now()) {
                state.traceTarget = null;
            } else if (ready(attacker, state, "blood_trace")) {
                state.traceTarget = target.getUniqueId();
                state.traceUntil = now() + rule("blood_trace").duration();
                target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, rule("blood_trace").duration() * 20, 0));
            }
        }
        if (owns(attacker, "blood_god") && target instanceof Player && every(state, "blood_god")) {
            state.progress.add("blood_god", gain(attacker), rule("blood_god").cap());
        }
        if (owns(attacker, "immortal_berserker")) {
            if (state.immortalUntil > now()) {
                double heal = Math.min(target.getHealth(), event.getFinalDamage()) * catalog.setting("immortal-berserker-lifesteal");
                attacker.setHealth(Math.min(maxHealth(attacker), attacker.getHealth() + heal));
            } else if (every(state, "immortal_berserker")) {
                state.immortalUntil = now() + rule("immortal_berserker").duration();
            }
        }
        if (owns(attacker, "berserk_engine")) {
            boolean upgraded = evolved(state, "berserk_seconds", "berserk-evolution-seconds");
            state.progress.add("berserk_engine", 1, upgraded ? catalog.count("berserk-evolved-cap") : rule("berserk_engine").cap());
            state.lastBerserkHit = now();
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
                syntheticHit(attacker, target, "storm_chaser", rule("storm_chaser").amount());
            });
        }
        if (event.getDamager() instanceof AbstractArrow && owns(attacker, "explosive_arrow")
                && every(state, "explosive_arrow") && ready(attacker, state, "explosive_arrow")) {
            var center = target.getLocation();
            afterDamage(attacker, () -> area(attacker, center, catalog.setting("arrow-explosion-range"),
                    "explosive_arrow", rule("explosive_arrow").amount(), victim -> { }));
        }
        if (owns(attacker, "split_attack")) {
            if (splitEvents.remove(event)) {
                state.progress.reset("split_charge");
                double damage = event.getDamage();
                afterDamage(attacker, () -> {
                    for (int i = 0; i < catalog.count("split-extra-hits"); i++) syntheticHit(attacker, target, "split_attack", damage);
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
            attacker.setHealth(Math.min(maxHealth(attacker), attacker.getHealth()
                    + Math.min(event.getFinalDamage(), target.getHealth()) * lifesteal));
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
        afterDamage(attacker, () -> {
            if (owns(attacker, "ultimate_hydra")) hydra(attacker, target, state, base);
            if (owns(attacker, "sword_wind") && every(state, "sword_wind")) sword(attacker, state);
            if (state.thousandSwords) swords(attacker, state);
            if (owns(attacker, "draw_the_sword")) drawSword(attacker, state);
            if (owns(attacker, "dual_wielder")) offhand(attacker, target);
            if (owns(attacker, "clone_hand") && every(state, "clone_hand") && ready(attacker, state, "clone_hand")) {
                syntheticHit(attacker, target, "clone_hand", base * rule("clone_hand").amount());
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
    }

    private void applyVenom(Fighter state, LivingEntity target) {
        long now = now();
        state.poisoned.values().removeIf(until -> until <= now);
        if (state.poisoned.size() >= catalog.count("burn-max-targets")) return;
        int length = rule("venom_swarm").duration();
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, length * 20, 1));
        state.poisoned.put(target.getUniqueId(), now + length);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void fireBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getProjectile() instanceof AbstractArrow original)
                || !valid(player) || !owns(player, "split_bow")) return;
        Fighter state = fighter(player.getUniqueId());
        if (!every(state, "split_bow") || !ready(player, state, "split_bow")) return;
        splitArrows.add(original.getUniqueId());
        UUID matchId = match;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (matchId.equals(match)) splitArrows.remove(original.getUniqueId());
        }, catalog.count("missile-lifetime-ticks"));
        double angle = catalog.setting("split-bow-angle");
        var start = player.getEyeLocation();
        var direction = original.getVelocity();
        if (direction.lengthSquared() == 0) return;
        double damage = original.getDamage() * catalog.setting("split-bow-ratio");
        afterDamage(player, () -> {
            for (double yaw : new double[] {-angle, angle}) {
                var path = direction.clone().normalize().rotateAroundY(yaw);
                var target = ray(player, start, path, catalog.setting("split-bow-reach"));
                if (target != null) syntheticHit(player, target, "split_bow", damage);
                particle(player, state, Particle.CRIT, start.clone().add(path));
            }
        });
    }

    private void phantom(Player attacker, LivingEntity target, Fighter state) {
        if (owns(attacker, "phantom_weapon")) meleeEffects(attacker, target, state, true);
    }

    private void meleeEffects(Player attacker, LivingEntity target, Fighter state, boolean phantom) {
        var allowlist = catalog.config.getStringList("settings.phantom-melee-effects");
        if (owns(attacker, "steel_heart") && (!phantom || allowlist.contains("steel_heart")) && ready(attacker, state, "steel_heart")) {
            int limit = catalog.count("steel-evolution-stacks"), before = state.progress.value("steel_heart");
            int after = state.progress.add("steel_heart", gain(attacker) * (before >= limit ? 2 : 1), rule("steel_heart").cap());
            if (before < limit && after >= limit) announce(attacker.getUniqueId(), "증강 진화 · 강철심장", true);
        }
        if (owns(attacker, "predator") && (!phantom || allowlist.contains("predator")) && every(state, "predator") && ready(attacker, state, "predator")) {
            attacker.setHealth(Math.min(maxHealth(attacker), attacker.getHealth() + rule("predator").amount()));
        }
        if (owns(attacker, "chain_impact") && (!phantom || allowlist.contains("chain_impact"))
                && every(state, "chain_impact") && ready(attacker, state, "chain_impact")) {
            var effect = new PotionEffect(PotionEffectType.SLOWNESS, rule("chain_impact").duration() * 20, 0);
            afterDamage(attacker, () -> area(attacker, attacker.getLocation(), catalog.setting("chain-impact-range"), "chain_impact",
                    rule("chain_impact").amount(), victim -> victim.addPotionEffect(effect)));
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
        return state.burned >= catalog.setting("burn-evolution-damage");
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
                        state.burned = Math.min(state.burned + syntheticFinalDamage, catalog.setting("burn-evolution-damage"));
                        if (burnEvolved(state)) announce(player.getUniqueId(), "증강 진화 · 지옥의 전도체", true);
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
        return every(state, "infinite_repeat") ? catalog.count("repeat-third-count")
                : (int) rule("infinite_repeat").amount();
    }

    private void launchMissiles(Player player, LivingEntity primary, Fighter state) {
        LivingEntity target = opponent(player, primary) ? primary
                : opponents(player, player.getLocation(), catalog.setting("missile-target-range")).stream().findFirst().orElse(null);
        if (target == null) return;
        int threshold = catalog.count("missile-evolution-shots");
        int before = state.progress.value("missile_shots");
        int baseCount = catalog.count(before >= threshold ? "missile-evolved-count" : "missile-count");
        int multiplier = state.barrage ? catalog.synergies.getInt("infinite_barrage.missile-multiplier") : 1;
        int count = Math.min(baseCount * multiplier * repetitions(player, state),
                catalog.count("missile-active-cap") - state.missiles.size());
        int bounces = state.barrage ? catalog.synergies.getInt("infinite_barrage.bounces") : 0;
        for (int i = 0; i < count; i++) {
            var start = player.getEyeLocation().add(0, (i % 5 - 2) * .08, 0);
            var missile = new PaperMissiles.Missile(start, target, catalog.count("missile-lifetime-ticks") + i % 5, bounces);
            state.missiles.add(new MissileShot(missile, chain.currentRoot()));
        }
        if (count > 0 && before < threshold && state.progress.add("missile_shots", count, threshold) == threshold) {
            announce(player.getUniqueId(), "증강 진화 · 마법 미사일", true);
        }
    }

    private void advanceMissiles(Player player, Fighter state) {
        var iterator = state.missiles.iterator();
        while (iterator.hasNext()) {
            var shot = iterator.next();
            var missile = shot.missile();
            boolean reached = missile.advance(player, catalog.setting("missile-speed"), this::opponent);
            if (!state.barrage || catalog.synergies.getBoolean("infinite_barrage.particles")) {
                particle(player, state, Particle.END_ROD, missile.position);
            }
            if (reached) {
                LivingEntity struck = missile.target;
                missile.struck.add(struck.getUniqueId());
                chain.root(shot.root(), () -> syntheticHit(player, struck, "magic_missile", rule("magic_missile").amount()));
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
        long second = System.nanoTime() / 1_000_000_000L;
        if (state.particleSecond != second) {
            state.particleSecond = second;
            state.particles = 0;
        }
        if (state.particles >= catalog.count("particles-per-second")) return;
        state.particles++;
        location.getWorld().spawnParticle(kind, location, 1);
    }

    private void afterDamage(Player source, Runnable action) {
        UUID currentMatch = match;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (currentMatch.equals(match) && valid(source)) chain.root(action);
        });
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
        if (step == 0) return;
        if (owns(player, "storm_chaser")) state.stormDistance = Math.min(rule("storm_chaser").threshold(), state.stormDistance + step);
        if (owns(player, "tank_engine")) {
            int earned = state.movement.add(step, rule("tank_engine").threshold());
            if (earned > 0) {
                int past = state.progress.value("tank_engine");
                int next = state.progress.add("tank_engine", earned * gain(player), rule("tank_engine").cap());
                int evolution = catalog.count("tank-evolution-stacks");
                if (past < evolution && next >= evolution) announce(player.getUniqueId(), "증강 진화 · 탱크 엔진", true);
            }
        }
        boolean tank = owns(player, "tank_engine") && evolved(state, "tank_engine", "tank-evolution-stacks");
        boolean giant = owns(player, "calamity_giant") && player.isSprinting();
        boolean train = state.train && player.isSprinting();
        if (!tank && !giant && !train) return;
        int wait = train ? catalog.synergies.getInt("calamity_train.cooldown") : catalog.count("tank-contact-cooldown");
        double radius = train ? catalog.synergies.getDouble("calamity_train.radius") : catalog.setting("contact-radius");
        var nearby = opponents(player, event.getTo(), radius);
        if (nearby.isEmpty() || !state.progress.cooldown("contact", now(), cooldown(player, wait))) return;
        afterDamage(player, () -> {
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

    @EventHandler(priority = EventPriority.HIGH)
    public void slam(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_AIR
                || !player.isSneaking() || !player.getInventory().getItemInMainHand().getType().isAir()
                || !valid(player) || clock.get().isPaused() || !owns(player, "calamity_giant")) return;
        Fighter state = fighter(player.getUniqueId());
        if (!ready(player, state, "calamity_giant")) return;
        afterDamage(player, () -> area(player, player.getLocation(), catalog.setting("calamity-slam-radius"),
                "calamity_giant", catalog.setting("calamity-slam-damage"), target -> { }));
    }

    private Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) return player;
        return event.getDamager() instanceof Projectile arrow && arrow.getShooter() instanceof Player player ? player : null;
    }

    private boolean ready(Player player, Fighter state, String id) {
        return state.progress.cooldown(id, now(), cooldown(player, rule(id).cooldown()));
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
        boolean evolved = evolved(state, "sword_hits", "sword-evolution-hits");
        var rays = evolved ? List.of(forward.clone().rotateAroundY(.18).setY(forward.getY() + .18).normalize(),
                forward.clone().rotateAroundY(-.18).setY(forward.getY() - .18).normalize()) : List.of(forward);
        var hit = new HashSet<UUID>();
        for (var direction : rays) {
            var victim = ray(player, start, direction, catalog.setting("sword-reach"));
            if (victim != null && hit.add(victim.getUniqueId()) && syntheticHit(player, victim, "sword_wind", rule("sword_wind").amount())) {
                int limit = catalog.count("sword-evolution-hits");
                if (state.progress.value("sword_hits") < limit && state.progress.add("sword_hits", 1, limit) == limit) {
                    announce(player.getUniqueId(), "증강 진화 · 검풍", true);
                }
                phantom(player, victim, state);
            }
            particle(player, state, Particle.SWEEP_ATTACK, start.clone().add(direction));
        }
    }

    private void drawSword(Player player, Fighter state) {
        Location start = player.getEyeLocation();
        var direction = start.getDirection();
        var victim = ray(player, start, direction, catalog.setting("draw-sword-reach"));
        double damage = catalog.setting("draw-sword-base-damage") *
                (1 + state.progress.value("draw_the_sword") * rule("draw_the_sword").amount());
        if (victim != null && syntheticHit(player, victim, "draw_the_sword", damage)) {
            state.progress.add("draw_the_sword", gain(player), rule("draw_the_sword").cap());
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
        return chain.hit(match, source.getUniqueId(), victim.getUniqueId(), id, points, crit, () -> {
            syntheticConfirmed = false;
            int cooldown = victim.getNoDamageTicks();
            victim.setNoDamageTicks(0);
            try {
                victim.damage(points, source);
                return syntheticConfirmed;
            } finally {
                victim.setNoDamageTicks(Math.max(cooldown, victim.getNoDamageTicks()));
            }
        });
    }

    private void announce(UUID id, String message, boolean sound) {
        Player player = plugin.getServer().getPlayer(id);
        if (player == null) return;
        player.sendMessage(message);
        if (sound) player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .6f, 1.3f);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void mobDeath(EntityDeathEvent event) {
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
                    state.progress.add("heart_harvester", catalog.count("heart-harvester-player-stacks") * gain(killer), rule("heart_harvester").cap());
                    killer.setHealth(Math.min(maxHealth(killer), killer.getHealth() + catalog.setting("heart-harvester-player-heal")));
                }
                if (owns(killer, "endless_evolution")) {
                    state.progress.add("endless_evolution", catalog.count("endless-evolution-player-stacks") * gain(killer), rule("endless_evolution").cap());
                }
                if (owns(killer, "blood_god")) state.progress.add("blood_god_kills", gain(killer), catalog.count("blood-god-kill-cap"));
                if (owns(killer, "blood_crown")) {
                    grantAbsorption(killer, "blood-crown-absorption");
                }
            }
        }
        if (match == null || synthetic() || !(event.getEntity() instanceof Monster monster)
                || monster.getEntitySpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
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
            killer.setHealth(Math.min(maxHealth(killer), killer.getHealth() + catalog.setting("bounty-heal")));
            killer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, catalog.count("bounty-speed-seconds") * 20, 1));
        }
        Long poisonedUntil = state.poisoned.remove(id);
        if (owns(killer, "venom_swarm") && poisonedUntil != null && poisonedUntil > now() && ready(killer, state, "venom_swarm")) {
            Location center = killed.getLocation();
            afterDamage(killer, () -> area(killer, center, catalog.setting("venom-radius"), "venom_swarm",
                    rule("venom_swarm").amount(), victim -> applyVenom(state, victim)));
        }
    }

    private void progress(Player player, String id) {
        var state = fighter(player.getUniqueId());
        if (every(state, id)) state.progress.add(id, gain(player), rule(id).cap());
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

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void evadeOrResist(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !valid(player) || event.getFinalDamage() <= 0) return;
        var state = fighters.get(player.getUniqueId());
        if (state == null) return;
        if (event instanceof EntityDamageByEntityEvent hit && owns(player, "mad_scientist") && small(state, now())) {
            var attacker = attacker(hit);
            boolean threat = attacker != null && valid(attacker) && opponent(attacker, player)
                    || hit.getDamager() instanceof Monster monster && arena.test(monster.getLocation());
            if (threat && random.nextDouble() < catalog.setting("mad-small-dodge-chance")) {
                event.setCancelled(true);
                return;
            }
        }
        if (owns(player, "immortal_armor") && ready(player, state, "immortal_armor")
                && event.getDamageSource().getDamageType() != DamageType.OUTSIDE_BORDER
                && event.getDamageSource().getDamageType() != DamageType.OUT_OF_WORLD) {
            double absorption = Math.min(event.getFinalDamage() * rule("immortal_armor").amount(),
                    catalog.setting("immortal-armor-max"));
            event.setDamage(Math.max(0, event.getDamage() * (1 - absorption / event.getFinalDamage())));
            afterDamage(player, () -> addAbsorption(player, absorption, catalog.setting("immortal-armor-max")));
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void damageTaken(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !valid(player)) return;
        var state = fighters.get(player.getUniqueId());
        if (state == null) return;
        if (owns(player, "counter_shield") && event.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)
                && event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING) < 0 && ready(player, state, "counter_shield")) {
            state.counterReady = true;
            afterDamage(player, () -> player.setHealth(Math.min(maxHealth(player), player.getHealth() + catalog.setting("shield-heal"))));
        }
        if (event.getFinalDamage() <= 0) return;
        if (owns(player, "wound_record")) {
            var rule = rule("wound_record");
            state.woundDamage += Math.min(event.getFinalDamage(), player.getHealth());
            int stacks = (int) (state.woundDamage / rule.threshold());
            if (stacks > 0) {
                state.woundDamage %= rule.threshold();
                state.progress.add("wound_record", stacks * gain(player), rule.cap());
            }
        }
        if (player.getHealth() > event.getFinalDamage()) state.instinctPending = true;
    }

    private void reactAtLowHealth(Player player, Fighter state) {
        double fraction = player.getHealth() / maxHealth(player);
        if (owns(player, "wound_record") && fraction <= catalog.setting("wound-low-health") && ready(player, state, "wound_record")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, rule("wound_record").duration() * 20, 0));
        }
        if (owns(player, "last_demon") && fraction <= catalog.setting("last-demon-low-health") && ready(player, state, "last_demon")) {
            int seconds = rule("last_demon").duration();
            state.lastDemonUntil = now() + seconds;
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, seconds * 20, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, seconds * 20, 0));
        }
        if (owns(player, "survival_instinct") && fraction <= catalog.setting("instinct-low-health") && ready(player, state, "survival_instinct")) {
            player.setHealth(Math.min(maxHealth(player), player.getHealth() + rule("survival_instinct").amount()));
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
            state.progress.add("iron_stomach", gain(player), rule("iron_stomach").cap());
        }
        if (ready(player, state, "iron_stomach")) {
            player.setHealth(Math.min(maxHealth(player), player.getHealth() + catalog.setting("iron-stomach-heal")));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void revive(EntityDamageEvent event) {
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
        event.setCancelled(true);
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
