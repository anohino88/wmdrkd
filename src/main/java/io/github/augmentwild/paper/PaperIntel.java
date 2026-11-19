package io.github.augmentwild.paper;

import io.github.augmentwild.domain.CombatIntel;
import io.github.augmentwild.domain.CombatIntel.Damage;
import io.github.augmentwild.domain.CombatIntel.Kind;
import io.github.augmentwild.domain.MatchSimulationClock;
import io.github.augmentwild.domain.Tracker;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

final class PaperIntel implements Listener {
    private static final int PAGE_SIZE = 36;
    private static final Set<String> PERIODIC = Set.of("infernal_conductor", "venom_swarm", "void_lord");
    private static final Set<String> AREA = Set.of("steel_king", "chain_impact", "explosive_arrow", "earthquake_mage",
            "final_judgment", "bomb_alchemy", "falling_shockwave", "calamity_giant");
    private final JavaPlugin plugin;
    private final IntelSettings settings;
    private final Supplier<MatchSimulationClock> clock;
    private final Supplier<Set<UUID>> participants;
    private final Predicate<Player> active;
    private final Predicate<Location> safe;
    private final PaperAugments augments;
    private final Tracker targetFinder = new Tracker();
    private final Map<UUID, CombatIntel.Tracking> tracks = new HashMap<>();
    private final Map<UUID, Long> trackUpdates = new HashMap<>();
    private final Map<UUID, Long> scoutCooldown = new HashMap<>();
    private final Map<UUID, CombatIntel.Dwell> dwell = new HashMap<>();
    private final Map<UUID, Report> scouts = new HashMap<>();
    private final Map<UUID, Report> deaths = new HashMap<>();
    private final Map<UUID, CombatIntel.History> histories = new HashMap<>();
    private final Set<EntityDamageEvent> recorded = Collections.newSetFromMap(new WeakHashMap<>());
    private UUID match;

    record Card(Material icon, Component name, List<Component> lore) {
        Card { lore = List.copyOf(lore); }
    }
    private record Report(String title, CombatIntel.TimedReport<Card> data) {
        Report(String title, List<Card> cards, long deadline) {
            this(title, new CombatIntel.TimedReport<>(cards, deadline));
        }
        boolean valid() { return data.valid(System.nanoTime()); }
        List<Card> cards() { return data.values(); }
        long deadline() { return data.deadline(); }
    }
    private static final class Menu implements InventoryHolder {
        final UUID owner;
        final Report report;
        int page;
        Inventory inventory;

        Menu(UUID owner, Report report) {
            this.owner = owner;
            this.report = report;
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    PaperIntel(JavaPlugin plugin, Supplier<MatchSimulationClock> clock, Supplier<Set<UUID>> participants,
               Predicate<Player> active, Predicate<Location> safe, PaperAugments augments) {
        this.plugin = plugin;
        this.clock = clock;
        this.participants = participants;
        this.active = active;
        this.safe = safe;
        this.augments = augments;
        settings = IntelSettings.load(plugin.getConfig());
    }

    void start(UUID id) {
        match = id;
        deaths.clear();
    }

    private boolean playable(Player player) {
        return player != null && player.isOnline() && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR
                && active.test(player);
    }

    private boolean enemy(Player source, Player player) {
        return playable(player) && !source.equals(player) && source.getWorld().equals(player.getWorld())
                && safe.test(player.getLocation()) && player.getWorld().getWorldBorder().isInside(player.getLocation());
    }

    private Tracker.Player position(Player player) {
        var location = player.getLocation();
        return new Tracker.Player(player.getUniqueId(), player.getName(), match, player.getWorld().getUID().toString(),
                null, Tracker.Status.ACTIVE, location.getX(), location.getY(), location.getZ());
    }

    String locate(Player player) {
        if (match == null || !playable(player) || clock.get().isPaused()) return "현재 위치 추적을 시작할 수 없습니다.";
        UUID id = player.getUniqueId();
        long now = clock.get().elapsedNanos();
        var tracking = tracks.computeIfAbsent(id, ignored -> new CombatIntel.Tracking());
        if (tracking.busy(now)) return "위치 추적 재사용 대기 중입니다.";
        var eligible = participants.get().stream().map(plugin.getServer()::getPlayer)
                .filter(other -> enemy(player, other)).map(this::position).toList();
        var target = targetFinder.nearest(position(player), eligible)
                .filter(nearest -> settings.trackerRange() < 0 || nearest.distance() <= settings.trackerRange());
        var outcome = tracking.start(target.map(Tracker.Result::id).orElse(null), now, settings.trackDuration(), settings.trackCooldown(),
                () -> spendDiamond(player));
        return switch (outcome) {
            case NO_TARGET -> "안전 구역 안에서 추적할 상대를 찾지 못했습니다.";
            case NO_FUNDS -> "다이아몬드 " + settings.cost() + "개가 필요합니다.";
            case COOLDOWN -> "위치 추적 재사용 대기 중입니다.";
            case STARTED -> {
                trackUpdates.remove(id);
                updateTrack(player, now);
                yield "가장 가까운 적의 방향을 추적합니다.";
            }
        };
    }

    private boolean spendDiamond(Player player) {
        var inventory = player.getInventory();
        var transaction = MatchInventory.remove(inventory.getStorageContents(), inventory.getItemInOffHand(),
                Material.DIAMOND, settings.cost());
        if (transaction == null) return false;
        inventory.setStorageContents(transaction.storage());
        if (transaction.offhand() != null) inventory.setItemInOffHand(transaction.offhand());
        return true;
    }

    private void updateTrack(Player player, long now) {
        UUID id = player.getUniqueId();
        var running = tracks.get(id);
        if (running == null || running.assigned() == null) return;
        UUID targetId = running.target(now);
        Player target = targetId == null ? null : plugin.getServer().getPlayer(targetId);
        if (!playable(player) || !enemy(player, target)) {
            endTrack(id);
            return;
        }
        if (now >= trackUpdates.getOrDefault(id, 0L)) {
            String label = settings.revealName() ? " · " + target.getName() : "";
            player.sendActionBar(Component.text("적 방향 · " + direction(player.getLocation(), target.getLocation())
                    + label, NamedTextColor.AQUA));
            trackUpdates.put(id, now + settings.trackUpdate());
        }
    }

    private static String direction(Location source, Location target) {
        return switch (Tracker.direction(target.getX() - source.getX(), target.getZ() - source.getZ())) {
            case "N" -> "북";
            case "NE" -> "북동";
            case "E" -> "동";
            case "SE" -> "남동";
            case "S" -> "남";
            case "SW" -> "남서";
            case "W" -> "서";
            case "NW" -> "북서";
            default -> "방향 미정";
        };
    }

    private void endTrack(UUID id) {
        var tracking = tracks.get(id);
        if (tracking == null || tracking.assigned() == null) return;
        tracking.stop();
        trackUpdates.remove(id);
        Player player = plugin.getServer().getPlayer(id);
        if (player != null) player.sendActionBar(Component.empty());
    }

    void tick() {
        if (match == null) return;
        scouts.values().removeIf(report -> !report.valid());
        deaths.values().removeIf(report -> !report.valid());
        if (clock.get().isPaused()) {
            dwell.clear();
            return;
        }
        long now = clock.get().elapsedNanos();
        for (UUID id : participants.get()) {
            Player player = plugin.getServer().getPlayer(id);
            if (!playable(player)) {
                dwell.remove(id);
                endTrack(id);
                continue;
            }
            updateTrack(player, now);
            observe(player, now);
        }
    }

    private void observe(Player player, long now) {
        UUID id = player.getUniqueId();
        if (now < scoutCooldown.getOrDefault(id, 0L) || !player.isHandRaised()
                || player.getActiveItem().getType() != Material.SPYGLASS) {
            dwell.remove(id);
            return;
        }
        Location eye = player.getEyeLocation();
        var hit = player.getWorld().rayTrace(eye, eye.getDirection(), settings.scoutRange(), FluidCollisionMode.NEVER,
                true, 0, entity -> entity instanceof Player other && !other.equals(player));
        if (hit == null || !(hit.getHitEntity() instanceof Player target) || !enemy(player, target)) {
            dwell.remove(id);
            return;
        }
        if (!dwell.computeIfAbsent(id, ignored -> new CombatIntel.Dwell()).observe(target.getUniqueId(), now, settings.aim())) return;
        dwell.remove(id);
        scoutCooldown.put(id, now + settings.scoutCooldown());
        var report = new Report("망원경 정찰", augments.scoutCards(target.getUniqueId()),
                System.nanoTime() + settings.resultLifetime());
        scouts.put(id, report);
        target.sendMessage(Component.text("누군가 당신의 증강을 분석했습니다.", NamedTextColor.YELLOW));
        open(player, report);
    }

    String openScout(Player player) {
        if (match == null || !playable(player) || clock.get().isPaused()) return "현재 정찰 결과를 열 수 없습니다.";
        return show(player, scouts.get(player.getUniqueId()), "유효한 정찰 결과가 없습니다.");
    }

    String openDeath(Player player) {
        if (player.isDead() || clock.get() != null && clock.get().isPaused()) return "현재 사망 분석을 열 수 없습니다.";
        return show(player, deaths.get(player.getUniqueId()), "최근 사망 분석이 없습니다.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void damage(EntityDamageEvent event) {
        if (match == null || !(event.getEntity() instanceof Player player)
                || !playable(player) || !recorded.add(event)) return;
        var trace = augments.trace(event);
        double health = Math.min(player.getHealth(), Math.max(0, event.getFinalDamage()));
        double absorption = event.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION)
                ? Math.max(0, -event.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION)) : 0;
        double shield = trace.shield() + absorption;
        if (!Double.isFinite(health) || !Double.isFinite(shield) || health == 0 && shield == 0) return;
        var source = event.getDamageSource().getCausingEntity();
        Player attacker = source instanceof Player p ? p : source instanceof Projectile projectile
                && projectile.getShooter() instanceof Player p ? p : null;
        Kind kind = classify(event, trace.synthetic(), trace.augment());
        histories.computeIfAbsent(player.getUniqueId(), id -> new CombatIntel.History(settings.historySize(), settings.historyWindow()))
                .add(new Damage(clock.get().elapsedNanos(), attacker == null ? null : attacker.getUniqueId(),
                        attacker == null ? null : attacker.getName(), kind, event.getCause().name(),
                        trace.augment(), trace.origin(), health, shield));
    }

    private static Kind classify(EntityDamageEvent event, boolean synthetic, String augment) {
        if (synthetic && PERIODIC.contains(augment)) return Kind.PERIODIC;
        if (synthetic && AREA.contains(augment)) return Kind.AREA;
        if (synthetic) return Kind.AUGMENT;
        return switch (event.getCause()) {
            case PROJECTILE -> Kind.PROJECTILE;
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK -> Kind.MELEE;
            case ENTITY_EXPLOSION, BLOCK_EXPLOSION -> Kind.AREA;
            case FIRE_TICK, POISON, WITHER -> Kind.PERIODIC;
            default -> Kind.ENVIRONMENT;
        };
    }

    void death(Player player, org.bukkit.damage.DamageSource finalSource) {
        UUID id = player.getUniqueId();
        var history = histories.remove(id);
        List<Damage> hits = history == null ? List.of() : history.snapshot(clock.get().elapsedNanos());
        var cards = new ArrayList<Card>();
        String killer = player.getKiller() == null ? "확인되지 않음" : player.getKiller().getName();
        double total = hits.stream().mapToDouble(Damage::health).sum();
        double shield = hits.stream().mapToDouble(Damage::shield).sum();
        var kindTotals = new EnumMap<Kind, Double>(Kind.class);
        var causes = new TreeMap<String, Double>();
        var contributors = new LinkedHashMap<UUID, String>();
        var contributorDamage = new HashMap<UUID, Double>();
        var effects = new TreeSet<String>();
        for (Damage hit : hits) {
            kindTotals.merge(hit.kind(), hit.health(), Double::sum);
            causes.merge(hit.augment() == null || hit.kind() == Kind.MELEE || hit.kind() == Kind.PROJECTILE
                    ? hit.cause() : hit.augment(), hit.health(), Double::sum);
            if (hit.attacker() != null) {
                contributors.put(hit.attacker(), hit.attackerName());
                contributorDamage.merge(hit.attacker(), hit.health(), Double::sum);
            }
            if (hit.augment() != null) effects.add(hit.augment());
        }
        String biggest = causes.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("기록 없음");
        cards.add(card(Material.PAPER, "사망 요약", "최종 원인 " + finalSource.getDamageType().getKey(),
                "처치자 " + killer, "최근 피해 " + points(total) + " · 흡수 " + points(shield), "최대 피해 원인 " + biggest));
        kindTotals.forEach((kind, damage) -> cards.add(card(Material.PAPER, kind.name(), "체력 피해 " + points(damage))));
        contributors.forEach((source, name) -> cards.add(card(Material.PLAYER_HEAD, name,
                "체력 피해 기여 " + points(contributorDamage.get(source)))));
        for (String effect : effects) cards.add(card(Material.AMETHYST_SHARD, augments.augmentName(effect), effect));
        for (int index = hits.size() - 1; index >= 0; index--) {
            Damage hit = hits.get(index);
            long elapsed = hit.time() / 1_000_000_000L;
            cards.add(card(Material.BOOK, String.format(Locale.ROOT, "%02d:%02d · %s", elapsed / 60, elapsed % 60, hit.kind()),
                    "출처 " + (hit.attackerName() == null ? hit.cause() : hit.attackerName()),
                    "체력 피해 " + points(hit.health()) + " · 흡수 " + points(hit.shield())));
        }
        deaths.put(id, new Report("사망 분석", cards, System.nanoTime() + settings.deathDeliveryLifetime()));
        scouts.remove(id);
        dwell.remove(id);
        endTrack(id);
        for (UUID tracker : List.copyOf(tracks.keySet())) if (id.equals(tracks.get(tracker).assigned())) endTrack(tracker);
        player.sendMessage("사망 분석을 저장했습니다. 기본 리스폰 후 /aw death로 볼 수 있습니다.");
    }

    void onRespawn(Player player) {
        UUID id = player.getUniqueId();
        Report report = deaths.get(id);
        if (report != null) plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && !player.isDead() && deaths.get(id) == report
                    && (clock.get() == null || !clock.get().isPaused()) && report.valid()) open(player, report);
        });
    }

    void remove(UUID id) {
        endTrack(id);
        for (UUID viewer : List.copyOf(tracks.keySet())) if (id.equals(tracks.get(viewer).assigned())) endTrack(viewer);
        dwell.remove(id);
        scouts.remove(id);
        Player player = plugin.getServer().getPlayer(id);
        if (player != null && isReadOnly(player.getOpenInventory().getTopInventory())) player.closeInventory();
    }

    void clear() {
        for (UUID id : List.copyOf(tracks.keySet())) endTrack(id);
        for (UUID id : scouts.keySet()) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu
                    && menu.report == scouts.get(id)) player.closeInventory();
        }
        tracks.clear();
        trackUpdates.clear();
        scoutCooldown.clear();
        dwell.clear();
        scouts.clear();
        histories.clear();
        recorded.clear();
        match = null;
    }

    void shutdown() {
        clear();
        deaths.clear();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (isReadOnly(player.getOpenInventory().getTopInventory())) player.closeInventory();
        }
    }

    private String show(Player player, Report report, String absent) {
        if (report == null || !report.valid()) return absent;
        open(player, report);
        return report.title() + " 화면을 열었습니다.";
    }

    private void open(Player player, Report report) {
        var menu = new Menu(player.getUniqueId(), report);
        menu.inventory = plugin.getServer().createInventory(menu, 45, Component.text(report.title()));
        render(menu);
        player.openInventory(menu.inventory);
        scheduleExpiry(player, menu);
    }

    private void scheduleExpiry(Player player, Menu menu) {
        var report = menu.report;
        long ticks = Math.max(1, (report.deadline() - System.nanoTime() + 49_999_999L) / 50_000_000L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (report.valid()) {
                scheduleExpiry(player, menu);
                return;
            }
            if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == menu) player.closeInventory();
            scouts.remove(menu.owner, report);
            deaths.remove(menu.owner, report);
        }, ticks);
    }

    private static void render(Menu menu) {
        menu.inventory.clear();
        int offset = menu.page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && offset + i < menu.report.cards().size(); i++) {
            menu.inventory.setItem(i, item(menu.report.cards().get(offset + i)));
        }
        if (menu.page > 0) menu.inventory.setItem(36, item(card(Material.ARROW, "이전 페이지")));
        if (offset + PAGE_SIZE < menu.report.cards().size()) menu.inventory.setItem(44, item(card(Material.ARROW, "다음 페이지")));
    }

    static boolean isReadOnly(Inventory inventory) { return inventory.getHolder() instanceof Menu; }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!menu.owner.equals(event.getWhoClicked().getUniqueId()) || !menu.report.valid()) return;
        if (event.getClick() != org.bukkit.event.inventory.ClickType.LEFT) return;
        if (event.getRawSlot() == 36 && menu.page > 0) menu.page--;
        else if (event.getRawSlot() == 44 && (menu.page + 1) * PAGE_SIZE < menu.report.cards().size()) menu.page++;
        else return;
        render(menu);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (isReadOnly(event.getView().getTopInventory())) event.setCancelled(true);
    }

    static Card card(Material icon, String name, String... lore) {
        return new Card(icon, Component.text(name), Arrays.stream(lore).<Component>map(Component::text).toList());
    }

    private static ItemStack item(Card card) {
        var result = new ItemStack(card.icon());
        var meta = result.getItemMeta();
        meta.displayName(card.name());
        meta.lore(card.lore());
        result.setItemMeta(meta);
        return result;
    }

    private static String points(double value) { return String.format(Locale.ROOT, "%.1f", value); }
}
