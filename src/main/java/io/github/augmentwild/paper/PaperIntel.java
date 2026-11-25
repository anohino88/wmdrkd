package io.github.augmentwild.paper;

import io.github.augmentwild.domain.CombatIntel;
import io.github.augmentwild.domain.CombatIntel.Damage;
import io.github.augmentwild.domain.CombatIntel.Kind;
import io.github.augmentwild.domain.MatchSimulationClock;
import io.github.augmentwild.domain.Tracker;
import io.github.augmentwild.domain.MatchTelemetry;
import io.github.augmentwild.domain.MatchTelemetry.Counter;
import java.util.function.Consumer;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import io.github.augmentwild.domain.HudMessages.Layer;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

final class PaperIntel implements Listener {
    private static final int PAGE_SIZE = 27;
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
    private final MatchTelemetry stats;
    private final PaperHud hud;
    private final Consumer<Location> nearbyPvp;
    private final Map<UUID, Integer> shownProgress = new HashMap<>();
    private final Tracker targetFinder = new Tracker();
    private final Map<UUID, CombatIntel.Tracking> tracks = new HashMap<>();
    private final Map<UUID, Long> trackUpdates = new HashMap<>();
    private final Map<UUID, Long> scoutCooldown = new HashMap<>();
    private final Map<UUID, CombatIntel.Dwell> dwell = new HashMap<>();
    private final Map<UUID, Report> scouts = new HashMap<>();
    private final Map<UUID, Report> deaths = new HashMap<>();
    private final Map<UUID, Long> lateDeathViews = new HashMap<>();
    private final Map<UUID, CombatIntel.History> histories = new HashMap<>();
    private final Set<EntityDamageEvent> recorded = Collections.newSetFromMap(new WeakHashMap<>());
    private UUID match;

    record Card(Material icon, Component name, List<Component> lore) {
        Card { lore = List.copyOf(lore); }
    }
    private record Report(UUID matchId, String title, CombatIntel.TimedReport<Card> data) {
        Report(UUID matchId, String title, List<Card> cards, long deadline) {
            this(matchId, title, new CombatIntel.TimedReport<>(cards, deadline));
        }
        boolean scout() { return title.equals("망원경 정찰"); }
        boolean valid() { return data.valid(System.nanoTime()); }
        int summaryCount() {
            if (scout()) return 1;
            return Math.min(cards().size(),cards().getFirst().icon() == Material.PAPER ? 1 : 2);
        }
        List<Card> cards() { return data.values(); }
        long deadline() { return data.deadline(); }
    }
    private static final class Menu implements PaperMenu {
        final UUID owner;
        final Report report;
        int page;
        Inventory inventory;

        Menu(UUID owner, Report report) {
            this.owner = owner;
            this.report = report;
        }
        @Override public boolean availableWhileRestricted() { return true; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private final PaperArt art;

    PaperIntel(JavaPlugin plugin, Supplier<MatchSimulationClock> clock, Supplier<Set<UUID>> participants,
               Predicate<Player> active, Predicate<Location> safe, PaperAugments augments, MatchTelemetry stats,
               PaperHud hud, Consumer<Location> nearbyPvp, PaperArt art) {
        this.plugin = plugin;
        this.clock = clock;
        this.participants = participants;
        this.active = active;
        this.safe = safe;
        this.augments = augments;
        this.stats = stats;
        this.hud = hud;
        this.art = art;
        this.nearbyPvp = nearbyPvp;
        settings = IntelSettings.load(plugin.getConfig());
    }

    void start(UUID id) {
        flushLateViews();
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

    List<String> trackingHelp() {
        return List.of("비용 · 유효 대상 확인 후 다이아몬드 " + settings.cost() + "개", "조건 · 경기 중 활동 중인 참가자",
                "정보 · 8방향 · 상대 좌표, 거리, 높이 미표시",
                "지속 " + settings.trackDuration() + "초 · 재사용 " + settings.trackCooldown() + "초");
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
            case NO_TARGET -> {
                stats.count(Counter.TRACK_NO_TARGET);
                yield "안전 구역 안에서 추적할 상대를 찾지 못했습니다.";
            }
            case NO_FUNDS -> "다이아몬드 " + settings.cost() + "개가 필요합니다.";
            case COOLDOWN -> "위치 추적 재사용 대기 중입니다.";
            case STARTED -> {
                stats.count(Counter.TRACK_USES);
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
            long seconds = (running.remaining(now) + 999_999_999) / 1_000_000_000L;
            hud.set(id, Layer.TRACK, Component.text("적 추적 · " + direction(player.getLocation(), target.getLocation())
                    + label + " · 남은 시간 " + seconds + "초", NamedTextColor.AQUA));
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
        hud.remove(id, Layer.TRACK);
    }

    private void clearAim(UUID id) {
        dwell.remove(id);
        shownProgress.remove(id);
        hud.remove(id, Layer.SCOUT);
    }

    void tick() {
        if (match == null) return;
        if (clock.get().isPaused()) {
            for (UUID id : List.copyOf(dwell.keySet())) clearAim(id);
            return;
        }
        long now = clock.get().elapsedNanos();
        for (UUID id : participants.get()) {
            Player player = plugin.getServer().getPlayer(id);
            if (!playable(player)) {
                clearAim(id);
                endTrack(id);
                continue;
            }
            observe(player, now);
            updateTrack(player, now);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void stopUsing(PlayerStopUsingItemEvent event) {
        if (event.getItem().getType() == Material.SPYGLASS) clearAim(event.getPlayer().getUniqueId());
    }

    private void observe(Player player, long now) {
        UUID id = player.getUniqueId();
        if (now < scoutCooldown.getOrDefault(id, 0L) || !player.isHandRaised()
                || player.getActiveItem().getType() != Material.SPYGLASS) {
            clearAim(id);
            return;
        }
        Location eye = player.getEyeLocation();
        var hit = player.getWorld().rayTrace(eye, eye.getDirection(), settings.scoutRange(), FluidCollisionMode.NEVER,
                true, 0, entity -> entity instanceof Player other && !other.equals(player));
        if (hit == null || !(hit.getHitEntity() instanceof Player target) || !enemy(player, target)
                || player.getLocation().distanceSquared(target.getLocation()) > settings.scoutRange() * settings.scoutRange()) {
            clearAim(id);
            return;
        }
        var observation = dwell.computeIfAbsent(id, ignored -> new CombatIntel.Dwell());
        if (!target.getUniqueId().equals(observation.target())) {
            stats.count(Counter.SCOUT_ATTEMPTS);
            shownProgress.remove(id);
        }
        if (!observation.observe(target.getUniqueId(), now, settings.aim())) {
            int progress = observation.progress(now, settings.aim()) / 5 * 5;
            if (shownProgress.getOrDefault(id, -1) != progress) {
                hud.set(id, Layer.SCOUT, Component.text("증강 분석 중 · " + progress + "%", NamedTextColor.AQUA));
                shownProgress.put(id, progress);
            }
            return;
        }
        clearAim(id);
        stats.scout(id, target.getUniqueId(), now + settings.engagementWindow());
        scoutCooldown.put(id, now + settings.scoutCooldown());
        long second = now / 1_000_000_000L;
        var cards = new ArrayList<Card>();
        cards.add(card(Material.CLOCK, "정찰 스냅샷",
                String.format(Locale.ROOT, "경기 %02d:%02d 분석", second / 60, second % 60),
                "분석 당시의 증강과 활성 조합"));
        cards.addAll(augments.scoutCards(target.getUniqueId()));
        var report = new Report(match, "망원경 정찰", cards,
                System.nanoTime() + settings.resultLifetime());
        retain(scouts, id, report);
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
        double shield = trace.shield();
        if (!Double.isFinite(health) || !Double.isFinite(shield) || !Double.isFinite(absorption)
                || health == 0 && shield == 0 && absorption == 0) return;
        var source = event.getDamageSource().getCausingEntity();
        Player attacker = source instanceof Player p ? p : source instanceof Projectile projectile
                && projectile.getShooter() instanceof Player p ? p : null;
        if (attacker != null && !attacker.equals(player) && playable(attacker)) {
            stats.engage(attacker.getUniqueId(), player.getUniqueId(), clock.get().elapsedNanos());
            nearbyPvp.accept(player.getLocation());
        }
        Kind kind = classify(event, trace.synthetic(), trace.augment());
        histories.computeIfAbsent(player.getUniqueId(), id -> new CombatIntel.History(settings.historySize(), settings.historyWindow()))
                .add(new Damage(clock.get().elapsedNanos(), attacker == null ? null : attacker.getUniqueId(),
                        attacker == null ? null : attacker.getName(), kind, event.getCause().name(),
                        trace.augment(), trace.origin(), health, shield, absorption));
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
        double vanilla = hits.stream().mapToDouble(Damage::absorption).sum();
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
                "처치자 " + killer, "최근 체력 피해 " + points(total),
                "생명의 잔향 " + points(shield) + " · 바닐라 흡수 " + points(vanilla), "최대 피해 원인 " + biggest));
        kindTotals.forEach((kind, damage) -> cards.add(card(Material.PAPER, kind.name(), "체력 피해 " + points(damage))));
        contributors.forEach((source, name) -> cards.add(card(Material.PLAYER_HEAD, name,
                "체력 피해 기여 " + points(contributorDamage.get(source)))));
        for (String effect : effects) cards.add(card(Material.AMETHYST_SHARD, augments.augmentName(effect), effect));
        for (int index = hits.size() - 1; index >= 0; index--) {
            Damage hit = hits.get(index);
            long elapsed = hit.time() / 1_000_000_000L;
            cards.add(card(Material.BOOK, String.format(Locale.ROOT, "%02d:%02d · %s", elapsed / 60, elapsed % 60, hit.kind()),
                    "출처 " + (hit.attackerName() == null ? hit.cause() : hit.attackerName()),
                    "체력 피해 " + points(hit.health()) + " · 보호막 " + points(hit.shield()) + " · 흡수 " + points(hit.absorption())));
        }
        retain(deaths, id, new Report(match, "사망 분석", cards, System.nanoTime() + settings.deathDeliveryLifetime()));
        scouts.remove(id);
        clearAim(id);
        endTrack(id);
        for (UUID tracker : List.copyOf(tracks.keySet())) if (id.equals(tracks.get(tracker).assigned())) endTrack(tracker);
        player.sendMessage("사망 분석을 저장했습니다. 기본 리스폰 후 /aw death로 볼 수 있습니다.");
    }

    void finalizeDeath(UUID player,boolean eliminated) {
        var report = deaths.get(player);
        if (report == null) return;
        var cards = new ArrayList<>(report.cards());
        cards.addFirst(card(eliminated ? Material.BARRIER : Material.CLOCK,
                eliminated ? "최종 탈락" : "리스폰 예정",
                eliminated ? "현재 위치와 생존자의 실시간 상태는 공개되지 않습니다." : "리스폰이 끝난 뒤에도 필요할 때 조회할 수 있습니다."));
        retain(deaths,player,new Report(report.matchId(),report.title(),cards,report.deadline()));
    }

    void onRespawn(Player player) {
        var report = deaths.get(player.getUniqueId());
        if (report != null && report.valid())
            player.sendMessage(Component.text("사망 분석 보기 · /aw death")
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/aw death")));
    }

    void remove(UUID id) {
        endTrack(id);
        for (UUID viewer : List.copyOf(tracks.keySet())) if (id.equals(tracks.get(viewer).assigned())) endTrack(viewer);
        clearAim(id);
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
        for (UUID id : List.copyOf(dwell.keySet())) clearAim(id);
        shownProgress.clear();
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
        flushLateViews();
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
        menu.inventory = plugin.getServer().createInventory(menu, 45, art.title(player,report.scout() ? PaperArt.Screen.SCOUT : PaperArt.Screen.DEATH,report.title()));
        render(menu,player);
        player.openInventory(menu.inventory);
        if (deaths.get(menu.owner) == report && player.getOpenInventory().getTopInventory().getHolder() == menu) {
            if (report.matchId().equals(match)) stats.count(Counter.DEATH_VIEWS);
            else lateDeathViews.merge(report.matchId(), 1L, Long::sum);
        }
    }

    private void retain(Map<UUID, Report> reports, UUID owner, Report report) {
        reports.put(owner, report);
        scheduleExpiry(reports, owner, report);
    }

    private void scheduleExpiry(Map<UUID, Report> reports, UUID owner, Report report) {
        long ticks = Math.max(1, (report.deadline() - System.nanoTime() + 49_999_999L) / 50_000_000L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player player = plugin.getServer().getPlayer(owner);
            boolean displayed = player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu
                    && menu.report == report;
            if (report.valid()) {
                if (reports.get(owner) == report || displayed) scheduleExpiry(reports, owner, report);
                return;
            }
            reports.remove(owner, report);
            if (reports == deaths) flushLateViews(report.matchId());
            if (displayed) player.closeInventory();
        }, ticks);
    }

    private void flushLateViews() {
        for (UUID id : List.copyOf(lateDeathViews.keySet())) flushLateViews(id);
    }

    private void flushLateViews(UUID matchId) {
        Long count = lateDeathViews.remove(matchId);
        if (count != null) plugin.getLogger().info("Match feature late totals match=" + matchId + " DEATH_VIEWS=" + count);
    }

    private void render(Menu menu, Player player) {
        var inv = menu.inventory;
        inv.clear();
        var cards = menu.report.cards();
        int summaries = menu.report.summaryCount();
        inv.setItem(4,art.icon(player,item(cards.getFirst()),
                menu.report.scout() ? "gui/scout" : summaries == 2 ? "gui/warning" : "gui/report"));
        if (summaries == 2) inv.setItem(6,art.icon(player,item(cards.get(1)),"gui/report"));
        int offset = summaries + menu.page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && offset + i < cards.size(); i++) inv.setItem(9+i,item(cards.get(offset+i)));
        if (menu.page > 0)
            InterfaceTheme.put(art,player,inv,36,Material.ARROW,"previous","이전 페이지",InterfaceTheme.Tone.NORMAL);
        InterfaceTheme.put(art,player,inv,40,Material.COMPASS,"menu","빠른 메뉴",InterfaceTheme.Tone.NORMAL);
        if (offset + PAGE_SIZE < cards.size())
            InterfaceTheme.put(art,player,inv,44,Material.ARROW,"next","다음 페이지",InterfaceTheme.Tone.NORMAL);
    }

    static boolean isReadOnly(Inventory inventory) { return inventory.getHolder() instanceof Menu; }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        boolean cancelled = event.isCancelled();
        int slot = PaperMenu.lock(event, false);
        if (cancelled) return;
        if (!menu.owner.equals(event.getWhoClicked().getUniqueId()) || !menu.report.valid()) return;
        if (slot == 40 && event.getWhoClicked() instanceof Player viewer) {
            plugin.getServer().getScheduler().runTask(plugin,() -> {
                if (viewer.isOnline() && viewer.getOpenInventory().getTopInventory().getHolder() == menu)
                    viewer.performCommand("aw menu");
            });
            return;
        }
        if (slot == 36 && menu.page > 0) menu.page--;
        else if (slot == 44 && menu.report.summaryCount() + (menu.page + 1) * PAGE_SIZE < menu.report.cards().size()) menu.page++;
        else return;
        if (event.getWhoClicked() instanceof Player player) render(menu,player);
    }

    static Card card(Material icon, String name, String... lore) {
        return new Card(icon, Component.text(name), Arrays.stream(lore).<Component>map(Component::text).toList());
    }

    static ItemStack item(Card card) {
        var result = new ItemStack(card.icon());
        var meta = result.getItemMeta();
        meta.displayName(card.name().decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,false));
        meta.lore(card.lore().stream().map(line -> line.decoration(
                net.kyori.adventure.text.format.TextDecoration.ITALIC,false)).toList());
        result.setItemMeta(meta);
        return result;
    }

    private static String points(double value) { return String.format(Locale.ROOT, "%.1f", value); }
}
