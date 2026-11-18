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
    private static final Set<String> STEEL_KING = Set.of("steel_heart", "stack_dinosaur", "heavy_fist");
    private static final List<Attribute> ATTRIBUTES = List.of(Attribute.MAX_HEALTH, Attribute.MOVEMENT_SPEED, Attribute.SCALE);
    private final JavaPlugin plugin;
    private final OfficialAugments catalog;
    private final Supplier<MatchSimulationClock> clock;
    private final Supplier<MatchState> phase;
    private final Predicate<Player> active, protectedPlayer;
    private final Predicate<Location> arena;
    private final BiConsumer<UUID, Integer> protection;
    private final Function<UUID, Set<String>> owned;
    private final DamageChain chain;
    private final NaturalOreTracker ores;
    private final Map<UUID, Fighter> fighters = new HashMap<>();
    private final Map<EntityDamageEvent, Double> rawDamage = new WeakHashMap<>();
    private final Set<EntityDamageEvent> counted = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<UUID> deadMobs = new HashSet<>();
    private Random random;
    private UUID match;
    private boolean syntheticConfirmed;

    private static final class Fighter {
        final AugmentProgress progress = new AugmentProgress();
        int overgrowthAward;
        long acquiredGrowth, lastBerserkHit = -1, lastSecond = -1;
        final MovementProgress movement = new MovementProgress();
        double stormDistance;
        final Set<Material> food = EnumSet.noneOf(Material.class);
        long immortalUntil;
        int forcedAt;
        boolean hadVelocity, king, train, apple;
    }

    PaperOfficialCombat(JavaPlugin plugin, AugmentRegistry registry, Supplier<MatchSimulationClock> clock,
                        Supplier<MatchState> phase, Predicate<Player> active, Predicate<Player> protectedPlayer,
                        Predicate<Location> arena, BiConsumer<UUID, Integer> protection,
                        Function<UUID, Set<String>> owned, int depth) {
        this.plugin = plugin;
        this.clock = clock;
        this.phase = phase;
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
    private boolean valid(Player player) { return match != null && active.test(player) && !protectedPlayer.test(player); }
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
        boolean king = catalog.synergies.getBoolean("steel_king.enabled") && owned.apply(id).containsAll(STEEL_KING);
        if (king && !state.king) announce(id, "시너지 활성화 · 강철의 왕", catalog.synergies.getBoolean("steel_king.sound"));
        state.king = king;
        boolean train = catalog.synergies.getBoolean("calamity_train.enabled")
                && owned.apply(id).containsAll(Set.of("tank_engine", "calamity_giant", "berserk_engine"));
        if (train && !state.train) announce(id, "시너지 활성화 · 재앙의 기관차", catalog.synergies.getBoolean("calamity_train.sound"));
        state.train = train;
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
        match = null;
    }

    void disconnected(Player player) { resetAttributes(player); }

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
        applyAttributes(player, state, second);
        if (!state.apple && owns(player, "mineral_devourer")
                && (phase.get() == MatchState.PVP || phase.get() == MatchState.SUDDEN_DEATH)
                && player.getInventory().firstEmpty() >= 0) {
            state.apple = player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE)).isEmpty();
        }
    }

    private void applyAttributes(Player player, Fighter state, long second) {
        var ids = owned.apply(player.getUniqueId());
        var progress = state.progress;
        double health = 0, speed = 0, scale = 0;
        for (String id : List.of("blood_record", "steel_heart", "iron_stomach")) {
            if (ids.contains(id)) health += progress.value(id) * rule(id).amount();
        }
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
        modifier(player, Attribute.SCALE, "official_scale", Math.clamp(scale, 0, 3), Operation.MULTIPLY_SCALAR_1);
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
        if (ids.contains("giant_slayer")) ratio += Math.clamp((maxHealth(target) / maxHealth(attacker) - 1)
                * catalog.setting("giant-relative-scaling"), 0, rule("giant_slayer").amount());
        if (melee) {
            for (String id : List.of("muscle_memory", "mineral_devourer")) {
                if (ids.contains(id)) ratio += state.progress.value(id) * rule(id).amount();
            }
            if (ids.contains("gigantification")) ratio += catalog.setting("gigantification-damage");
        }
        double flat = melee && ids.contains("heavy_fist") ? rule("heavy_fist").amount() * maxHealth(attacker) : 0;
        if (ratio > 0 || flat > 0) event.setDamage(base + Math.min(catalog.setting("max-extra-damage"), (base * ratio) + flat));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void hit(EntityDamageByEntityEvent event) {
        if (synthetic()) {
            var context = chain.current();
            if (context.victim().equals(event.getEntity().getUniqueId()) && event.getFinalDamage() > 0) syntheticConfirmed = true;
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target) || event.getFinalDamage() <= 0) return;
        Player attacker = attacker(event);
        if (attacker == null || !valid(attacker) || !opponent(attacker, target) || !counted.add(event)) return;
        Fighter state = fighters.get(attacker.getUniqueId());
        if (state == null) return;
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
        if (owns(attacker, "storm_chaser") && state.stormDistance >= rule("storm_chaser").threshold()
                && ready(attacker, state, "storm_chaser")) {
            state.stormDistance = 0;
            afterDamage(attacker, () -> {
                target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 1);
                syntheticHit(attacker, target, "storm_chaser", rule("storm_chaser").amount());
            });
        }
        if (event.getDamager() instanceof AbstractArrow && owns(attacker, "explosive_arrow")
                && every(state, "explosive_arrow") && ready(attacker, state, "explosive_arrow")) {
            var center = target.getLocation();
            afterDamage(attacker, () -> area(attacker, center, catalog.setting("arrow-explosion-range"),
                    "explosive_arrow", rule("explosive_arrow").amount(), victim -> { }));
        }
        if (!event.getDamager().equals(attacker)) return;
        if (owns(attacker, "steel_heart") && ready(attacker, state, "steel_heart")) {
            int limit = catalog.count("steel-evolution-stacks"), before = state.progress.value("steel_heart");
            int after = state.progress.add("steel_heart", gain(attacker) * (before >= limit ? 2 : 1), rule("steel_heart").cap());
            if (before < limit && after >= limit) announce(attacker.getUniqueId(), "증강 진화 · 강철심장", true);
        }
        if (!state.king && !owns(attacker, "chain_impact") && !owns(attacker, "sword_wind")
                && !owns(attacker, "predator") && !owns(attacker, "clone_hand")) return;
        double base = rawDamage(event);
        afterDamage(attacker, () -> {
            if (owns(attacker, "chain_impact") && every(state, "chain_impact") && ready(attacker, state, "chain_impact")) {
                var effect = new PotionEffect(PotionEffectType.SLOWNESS, rule("chain_impact").duration() * 20, 0);
                area(attacker, attacker.getLocation(), catalog.setting("chain-impact-range"), "chain_impact",
                        rule("chain_impact").amount(), victim -> victim.addPotionEffect(effect));
            }
            if (owns(attacker, "sword_wind") && every(state, "sword_wind")) sword(attacker, state);
            if (owns(attacker, "predator") && every(state, "predator") && ready(attacker, state, "predator")) {
                attacker.setHealth(Math.min(maxHealth(attacker), attacker.getHealth() + rule("predator").amount()));
            }
            if (owns(attacker, "clone_hand") && every(state, "clone_hand") && ready(attacker, state, "clone_hand")) {
                syntheticHit(attacker, target, "clone_hand", base * rule("clone_hand").amount());
            }
            if (state.king && maxHealth(attacker) >= catalog.synergies.getDouble("steel_king.minimum-health")) {
                double radius = catalog.synergies.getDouble("steel_king.radius");
                attacker.getWorld().getNearbyEntities(attacker.getLocation(), radius, radius, radius).stream()
                        .filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast)
                        .filter(other -> other.getLocation().distanceSquared(attacker.getLocation()) <= radius * radius)
                        .forEach(other -> syntheticHit(attacker, other, "steel_king", catalog.synergies.getDouble("steel_king.damage")));
                if (catalog.synergies.getBoolean("steel_king.particles")) attacker.getWorld().spawnParticle(Particle.SWEEP_ATTACK,
                        attacker.getLocation().add(0, 1, 0), 1);
            }
        });
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

    private void sword(Player player, Fighter state) {
        var start = player.getEyeLocation();
        var forward = start.getDirection();
        boolean evolved = evolved(state, "sword_hits", "sword-evolution-hits");
        var rays = evolved ? List.of(forward.clone().rotateAroundY(.18).setY(forward.getY() + .18).normalize(),
                forward.clone().rotateAroundY(-.18).setY(forward.getY() - .18).normalize()) : List.of(forward);
        var hit = new HashSet<UUID>();
        for (var direction : rays) {
            double reach = catalog.setting("sword-reach");
            var block = player.getWorld().rayTraceBlocks(start, direction, reach, FluidCollisionMode.NEVER, true);
            if (block != null) reach = Math.max(0, block.getHitPosition().distance(start.toVector()) - .01);
            if (reach == 0) continue;
            var entity = player.getWorld().rayTraceEntities(start, direction, reach, catalog.setting("sword-ray-size"),
                    candidate -> candidate instanceof LivingEntity living && opponent(player, living));
            if (entity != null && entity.getHitEntity() instanceof LivingEntity victim && hit.add(victim.getUniqueId())
                    && syntheticHit(player, victim, "sword_wind", rule("sword_wind").amount())) {
                int limit = catalog.count("sword-evolution-hits");
                if (state.progress.value("sword_hits") < limit && state.progress.add("sword_hits", 1, limit) == limit) {
                    announce(player.getUniqueId(), "증강 진화 · 검풍", true);
                }
            }
            player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, start.clone().add(direction), 1);
        }
    }

    private boolean syntheticHit(Player source, LivingEntity victim, String id, double damage) {
        if (!valid(source) || !opponent(source, victim)) return false;
        boolean crit = owns(source, "jeweled_gauntlet") && random.nextDouble() < catalog.setting("crit-chance");
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
        if (match == null || synthetic() || !(event.getEntity() instanceof Monster monster)
                || monster.getEntitySpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        Player player = monster.getKiller();
        if (player == null || !valid(player) || !arena.test(monster.getLocation()) || deadMobs.size() >= 65_536
                || !deadMobs.add(monster.getUniqueId())) return;
        for (String id : List.of("blood_record", "muscle_memory")) {
            if (owns(player, id)) progress(player, id);
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
        for (String id : List.of("mineral_addiction", "mineral_devourer")) {
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
        var options = new ArrayList<ReviveResolver.Option>(2);
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
