package io.github.augmentwild.paper;

import io.github.augmentwild.augment.*;
import io.github.augmentwild.augment.AugmentDefinition.Effect;
import io.github.augmentwild.domain.MatchTelemetry;
import io.github.augmentwild.domain.MatchTelemetry.Counter;
import io.github.augmentwild.domain.MatchSimulationClock;
import io.github.augmentwild.domain.SelectionRound;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import io.github.augmentwild.match.MatchState;
import org.bukkit.Location;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

final class PaperAugments implements Listener {
    private static final Set<String> EXCHANGES = Set.of("forbidden_reconstruction", "forbidden_deal");
    private static final int[] SLOTS = {10, 13, 16};
    private static final int REROLL = 22;
    private record ProcKey(UUID player, String effect) { }

    private final JavaPlugin plugin;
    private final Supplier<MatchSimulationClock> clock;
    private final Predicate<UUID> member;
    private final Predicate<Player> active;
    private final Predicate<Player> protectedPlayer;
    private final AugmentRegistry registry;
    private final AugmentRoller roller;
    private final Random random = new Random();
    private final Map<UUID, Profile> profiles = new HashMap<>();
    private final ProcGuard guard;
    private final double lowHealth, strongHit, rearDot;
    private int completed;
    private UUID match;

    private static final class Profile {
        final Set<String> acquired = new LinkedHashSet<>();
        final Set<String> owned = new LinkedHashSet<>();
        final AugmentTimers timers = new AugmentTimers();
        final Set<String> modifiers = new HashSet<>();
        int level, lastKnownLevel, shown, grantedRound;
        AugmentExchange exchange;
        String exchangeId;
        boolean exchangeShown;
    }

    private static final int CODEX_CARDS = 45;

    private static final class SummaryMenu implements PaperMenu {
        final UUID owner, match;
        final Map<Integer,String> favoriteButtons = new HashMap<>();
        int page;
        long updated = -1;
        Inventory inventory;
        SummaryMenu(UUID owner, UUID match) { this.owner = owner; this.match = match; }
        @Override public boolean availableWhileRestricted() { return true; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class ExchangeMenu implements PaperMenu {
        final UUID match, owner;
        final AugmentExchange exchange;
        Inventory inventory;

        ExchangeMenu(UUID match, UUID owner, AugmentExchange exchange) {
            this.match = match;
            this.owner = owner;
            this.exchange = exchange;
        }

        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class Menu implements PaperMenu {
        final UUID match, owner;
        final int round;
        final List<SelectionRound.Offer> offers;
        Inventory inventory;
        long lastSeconds = -1;

        Menu(UUID match, UUID owner, int round, List<SelectionRound.Offer> offers) {
            this.match = match;
            this.owner = owner;
            this.round = round;
            this.offers = offers;
        }

        @Override public Inventory getInventory() { return inventory; }
    }

    private final PaperOfficialCombat official;
    private final MatchTelemetry telemetry;
    private final PaperHud hud;
    private final Map<EntityDamageByEntityEvent, Map<Player, String>> observedProcs = new WeakHashMap<>();

    PaperAugments(JavaPlugin plugin, Supplier<MatchSimulationClock> clock, Predicate<UUID> member,
                  Predicate<Player> active, Predicate<Player> protectedPlayer, Supplier<MatchState> phase,
                  Predicate<Location> arena, Predicate<Location> safe, BiPredicate<Player, Location> teleport,
                  BiConsumer<UUID, Integer> protection, int maxDepth, MatchTelemetry telemetry, PaperHud hud) {
        this.plugin = plugin;
        this.telemetry = telemetry;
        this.hud = hud;
        this.clock = clock;
        this.member = member;
        this.active = active;
        this.protectedPlayer = protectedPlayer;
        var config = plugin.getConfig();
        lowHealth = config.getDouble("augments.conditions.low-health-fraction");
        strongHit = config.getDouble("augments.conditions.strong-hit-points");
        rearDot = config.getDouble("augments.conditions.rear-facing-dot");
        if (!(lowHealth > 0 && lowHealth < 1 && strongHit > 0 && Double.isFinite(strongHit)
                && rearDot >= -1 && rearDot <= 0)) throw new IllegalArgumentException("Invalid augment trigger settings");
        registry = AugmentCatalog.load(config);
        official = new PaperOfficialCombat(plugin, registry, clock, phase, member, active, protectedPlayer, arena, safe, teleport,
                protection, player -> {
                    var profile = profiles.get(player);
                    return profile == null ? Set.of() : profile.owned;
                }, maxDepth, telemetry, hud);
        registry.all().stream().filter(a -> AugmentRole.of(a.id()) == AugmentRole.UNKNOWN).forEach(a ->
                plugin.getLogger().warning("Unclassified augment draft role: " + a.id()));
        roller = new AugmentRoller(registry, random, official.draftWeights(), new AugmentRoller.Quality(
                config.getBoolean("augments.draft.earlier-diversity", false),
                config.getBoolean("augments.draft.final-diversity", true),
                config.getBoolean("augments.draft.exclude-final-xp", true)));
        guard = new ProcGuard(maxDepth);
    }

    String augmentName(String id) {
        var definition = registry.get(id);
        return definition == null ? id : definition.name();
    }

    PaperOfficialCombat.DamageTrace trace(org.bukkit.event.entity.EntityDamageEvent event) {
        return official.trace(event);
    }

    List<PaperIntel.Card> scoutCards(UUID target) {
        var profile = profiles.get(target);
        var ids = profile == null ? List.<String>of() : List.copyOf(profile.owned);
        var cards = new ArrayList<PaperIntel.Card>();
        cards.add(PaperIntel.card(Material.SPYGLASS, "확인한 증강 " + ids.size() + "개"));
        for (String combination : official.scoutingCombinations(target)) {
            cards.add(PaperIntel.card(Material.NETHER_STAR, combination, "활성 조합"));
        }
        for (String id : ids) {
            var definition = registry.get(id);
            var color = switch (definition.rarity()) {
                case SILVER -> NamedTextColor.GRAY;
                case GOLD -> NamedTextColor.GOLD;
                case PRISMATIC -> NamedTextColor.LIGHT_PURPLE;
            };
            cards.add(new PaperIntel.Card(Material.AMETHYST_SHARD, Component.text(definition.name(), color),
                    List.of(Component.text(definition.rarity().name(), color), Component.text(definition.condition()))));
        }
        return List.copyOf(cards);
    }

    int skillUses(UUID id) { return official.skillUses(id); }
    List<String> timedStates(UUID id) {
        var rows = new ArrayList<>(official.timedStates(id));
        var profile = profiles.get(id);
        if (profile != null) {
            long now = clock.get().elapsedSeconds();
            for (String augment : profile.owned) {
                long left = profile.timers.remainingActive(augment,now);
                if (left > 0) rows.add(registry.get(augment).name() + " " + left + "초");
            }
        }
        return rows;
    }
    String skill(Player player, String[] args) { return official.skill(player, args); }
    String effectName(String id) { var def = id == null ? null : registry.get(id); return def == null ? "증강" : def.name(); }
    List<String> ownedNames(UUID id) { var p = profiles.get(id); return p == null ? List.of() : p.acquired.stream().map(this::effectName).toList(); }
    String cast(Player player, String id) { return official.cast(player, id); }

    Listener officialListener() { return official; }

    void start(UUID id, MatchSimulationClock timer, boolean experiment) {
        match = id;
        official.start(id, experiment);
        timer.setOfferProvider(this::offers);
    }

    private Map<UUID, List<SelectionRound.Offer>> offers(UUID id, int round, Set<UUID> players) {
        var result = new HashMap<UUID, List<SelectionRound.Offer>>();
        boolean last = clock.get().isFinalRound(round);
        for (UUID playerId : players) {
            if (!id.equals(match) || !member.test(playerId)) {
                result.put(playerId, List.of());
                continue;
            }
            var profile = profiles.computeIfAbsent(playerId, ignored -> new Profile());
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) profile.lastKnownLevel = Math.clamp(player.getLevel(), 0, 50);
            profile.level = profile.lastKnownLevel;
            var offers = roll(profile, last, List.of());
            if (!offers.isEmpty()) telemetry.count(Counter.DRAFTS);
            result.put(playerId, offers);
        }
        return result;
    }

    private List<SelectionRound.Offer> roll(Profile profile, boolean last, List<SelectionRound.Offer> previous) {
        var ids = new HashSet<String>();
        previous.forEach(offer -> ids.add(offer.id()));
        return roller.roll(profile.level, last, profile.owned, ids, a -> canOffer(profile, a.id())).stream()
                .map(a -> new SelectionRound.Offer(a.id(), a.rarity() == AugmentRarity.PRISMATIC, a.implemented()))
                .toList();
    }

    private boolean canOffer(Profile profile, String id) {
        if (profile.exchange != null && profile.exchange.reserves(id)) return false;
        if (!EXCHANGES.contains(id)) return true;
        return !profile.owned.contains(id) && profile.exchange == null
                && sacrifice(profile, id).size() == (id.equals("forbidden_reconstruction") ? official.exchangeCount(id) : 0)
                && rewards(profile, id).size() >= official.exchangeCount(id);
    }

    private List<String> sacrifice(Profile profile, String id) {
        if (!id.equals("forbidden_reconstruction")) return List.of();
        return profile.owned.stream().filter(owned -> registry.get(owned).rarity() == AugmentRarity.SILVER)
                .limit(official.exchangeCount(id)).toList();
    }

    private List<String> rewards(Profile profile, String id) {
        var rarity = id.equals("forbidden_reconstruction") ? AugmentRarity.PRISMATIC : AugmentRarity.GOLD;
        return registry.selectable().stream().filter(a -> a.rarity() == rarity && !profile.owned.contains(a.id())
                && !EXCHANGES.contains(a.id())).map(AugmentDefinition::id).toList();
    }

    private void startExchange(Profile profile, String id) {
        if (!canOffer(profile, id)) return;
        var pool = new ArrayList<>(rewards(profile, id));
        Collections.shuffle(pool, random);
        profile.exchange = new AugmentExchange(sacrifice(profile, id),
                pool.subList(0, Math.min(official.exchangeOptions(), pool.size())), official.exchangeCount(id));
        profile.exchangeId = id;
        profile.exchangeShown = false;
    }

    void sync(Player player) {
        UUID id = player.getUniqueId();
        var profile = profiles.get(id);
        var timer = clock.get();
        if (timer == null) return;
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof SummaryMenu codex) {
            if (!Objects.equals(codex.match, match)) player.closeInventory();
            else if (codex.updated != timer.elapsedSeconds()) renderCodex(player, codex);
        }
        if (profile == null) return;
        if (!member.test(id)) {
            remove(id);
            return;
        }
        var round = timer.selection().orElse(null);
        var choice = round == null ? null : round.choices().get(id);
        if (choice != null) grant(round.round(), id, choice);
        Menu menu = ownedMenu(player);
        if (choice == null || choice.status() != SelectionRound.Status.PENDING || !active.test(player)) {
            if (menu != null) player.closeInventory();
        } else if (menu != null && menu.round == round.round() && menu.match.equals(match)) {
            if (!menu.offers.equals(choice.offers())) show(player, round, choice, profile);
            else setCountdown(menu, timer.selectionRemainingSeconds());
        } else if (profile.shown != round.round()) {
            show(player, round, choice, profile);
        }
        syncEffects(player, profile, timer.elapsedSeconds());
        official.sync(player);
        if (profile.exchange != null && !profile.exchangeShown && active.test(player)
                && (choice == null || choice.status() != SelectionRound.Status.PENDING)) showExchange(player, profile);
    }

    void collectCompleted() {
        var timer = clock.get();
        if (timer == null) return;
        var rounds = timer.completedSelections();
        while (completed < rounds.size()) {
            int round = completed + 1;
            rounds.get(completed++).forEach((player, choice) -> grant(round, player, choice));
        }
    }

    private void grant(int round, UUID id, SelectionRound.Choice choice) {
        var profile = profiles.get(id);
        if (profile == null || !member.test(id) || choice.selected() == null || profile.grantedRound >= round) return;
        var definition = registry.get(choice.selected());
        if (definition == null || !definition.selectable()) return;
        profile.grantedRound = round;
        telemetry.count(choice.status() == SelectionRound.Status.AUTOMATIC ? Counter.AUTO_PICKS : Counter.MANUAL_PICKS);
        if (EXCHANGES.contains(definition.id())) {
            startExchange(profile, definition.id());
            official.selected(id, definition.id(), "exchange_offer");
            return;
        }
        boolean added = acquire(profile, id, definition, choice.status().name());
        var player = plugin.getServer().getPlayer(id);
        if (player != null) {
            player.sendMessage(definition.name() + (added ? " 증강을 획득했습니다." : " 증강은 이미 활성화돼 있습니다."));
            if (PlayerUi.enabled(player,PlayerUi.Option.SOUND)) player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .45f,
                    definition.rarity() == AugmentRarity.PRISMATIC ? 1.35f : 1f);
        }
    }

    private boolean acquire(Profile profile, UUID player, AugmentDefinition definition, String source) {
        if (!profile.owned.add(definition.id())) return false;
        profile.acquired.add(definition.id());
        official.selected(player, definition.id(), source);
        official.acquired(player, definition.id());
        return true;
    }

    String labGrant(Player player, String id) {
        var definition = registry.get(id);
        if (definition == null || !definition.selectable()) return "사용 가능한 증강 ID를 지정하세요.";
        UUID playerId = player.getUniqueId();
        var profile = profiles.computeIfAbsent(playerId, ignored -> new Profile());
        if (profile.owned.contains(id)) return "이미 보유한 증강입니다.";
        if (EXCHANGES.contains(id)) {
            if (!canOffer(profile, id)) return "교환 자격을 충족하지 않았습니다.";
            startExchange(profile, id);
            showExchange(player, profile);
            return "교환 선택 시작 " + id;
        }
        acquire(profile, playerId, definition, "LAB");
        if (definition.effect() == Effect.SPEED) setModifier(player, profile, definition, Attribute.MOVEMENT_SPEED, true);
        official.refreshAttributes(player);
        refreshLabMenu(player, profile);
        return player.getName() + " 부여 " + id;
    }

    String labPreset(Player player, String id) {
        var members = AugmentCombinations.PRESETS.get(id);
        if (members == null) return "실험 프리셋 " + AugmentCombinations.PRESETS.keySet();
        if (members.stream().map(registry::get).anyMatch(a -> a == null || !a.selectable())) {
            return "사용할 수 없는 증강이 있어 프리셋을 적용하지 않았습니다.";
        }
        UUID owner = player.getUniqueId();
        var profile = profiles.computeIfAbsent(owner, ignored -> new Profile());
        if (profile.exchange != null) return "진행 중인 증강 교환을 먼저 마치세요.";
        var added = new ArrayList<String>();
        for (String member : new TreeSet<>(members)) {
            if (acquire(profile, owner, registry.get(member), "LAB_PRESET")) added.add(member);
        }
        official.refreshAttributes(player);
        refreshLabMenu(player, profile);
        return player.getName() + " 프리셋 " + id + (added.isEmpty() ? " · 모두 보유 중" : " · 추가 " + added);
    }

    String labRemove(Player player, String id) {
        var profile = profiles.get(player.getUniqueId());
        if (profile == null || !profile.owned.remove(id)) return "보유한 증강이 아닙니다.";
        release(player, profile, id, true);
        profile.exchange = null;
        profile.exchangeId = null;
        profile.exchangeShown = false;
        official.refreshAttributes(player);
        refreshLabMenu(player, profile);
        return player.getName() + " 제거 " + id;
    }

    private void release(Player player, Profile profile, String id, boolean experiment) {
        var definition = registry.get(id);
        if (profile.modifiers.contains(id)) setModifier(player, profile, definition, attribute(definition.effect()), false);
        profile.timers.remove(id);
        official.lost(player.getUniqueId(), id, experiment);
    }

    String labClear(Player player, boolean reset) {
        UUID id = player.getUniqueId();
        var old = profiles.get(id);
        remove(id);
        if (old != null) {
            var updated = new Profile();
            updated.grantedRound = old.grantedRound;
            updated.lastKnownLevel = old.lastKnownLevel;
            profiles.put(id, updated);
        }
        if (reset) {
            player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
            player.setAbsorptionAmount(0);
            player.setFireTicks(0);
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        }
        return player.getName() + (reset ? " 증강·전투 상태 초기화" : " 증강 전체 제거");
    }

    String labProgress(Player player, String id, int value) {
        var profile = profiles.get(player.getUniqueId());
        if (profile == null || !profile.owned.contains(id)) return "진행도를 설정할 증강을 먼저 부여하세요.";
        String applied = official.labProgress(player, id, value);
        refreshLabMenu(player, profile);
        return applied;
    }

    String labReport(Player player, boolean detailed) {
        UUID id = player.getUniqueId();
        var profile = profiles.get(id);
        var ids = profile == null ? Set.<String>of() : profile.owned;
        if (!detailed) return player.getName() + " 보유 증강 " + ids;
        var lines = new ArrayList<String>();
        lines.add(String.format(Locale.ROOT, "%s · 체력 %.2f/%.2f · 흡수 %.2f · 이동속도 %.3f · 크기 %.3f · 경기 %d초",
                player.getName(), player.getHealth(), player.getAttribute(Attribute.MAX_HEALTH).getValue(),
                player.getAbsorptionAmount(), player.getAttribute(Attribute.MOVEMENT_SPEED).getValue(),
                player.getAttribute(Attribute.SCALE).getValue(), clock.get().elapsedSeconds()));
        lines.add("활성 조합 " + official.scoutingCombinations(id) + " · " + official.labFightReport(id));
        for (String augment : ids) {
            var progress = new ArrayList<>(official.details(id, augment));
            progress.addAll(official.resonanceDetails(id, augment));
            progress.add("쿨다운 " + Math.max(official.labCooldown(id, augment),
                    profile.timers.remainingCooldown(augment, clock.get().elapsedSeconds())) + "초");
            lines.add(augment + " " + String.join(" · ", progress));
        }
        return String.join("\n", lines);
    }

    private void refreshLabMenu(Player player, Profile profile) {
        var holder = player.getOpenInventory().getTopInventory().getHolder();
        if (holder instanceof SummaryMenu menu) renderCodex(player, menu);
        else if (holder instanceof ExchangeMenu && profile.exchange == null) player.closeInventory();
    }

    String open(Player player) {
        var timer = clock.get();
        var round = timer == null ? null : timer.selection().orElse(null);
        UUID id = player.getUniqueId();
        var choice = round == null ? null : round.choices().get(id);
        var profile = profiles.get(id);
        if (active.test(player) && profile != null && profile.exchange != null
                && (choice == null || choice.status() != SelectionRound.Status.PENDING)) {
            showExchange(player, profile);
            return "교환 보상 선택지를 열었습니다.";
        }
        if (member.test(id) && profile != null && !profile.owned.isEmpty()
                && (choice == null || choice.status() != SelectionRound.Status.PENDING)) {
            openCodex(player);
            return "보유 증강을 열었습니다.";
        }
        if (!active.test(player) || choice == null || choice.status() != SelectionRound.Status.PENDING) {
            return "현재 선택할 증강이 없습니다.";
        }
        show(player, round, choice, profiles.get(id));
        return "증강 선택지를 열었습니다.";
    }

    private void show(Player player, SelectionRound round, SelectionRound.Choice choice, Profile profile) {
        var menu = new Menu(round.matchId(), player.getUniqueId(), round.round(), choice.offers());
        menu.inventory = plugin.getServer().createInventory(menu, 27, Component.text("증강 선택 · " + round.round() + "회차"));
        for (int i = 0; i < choice.offers().size(); i++) {
            var augment = registry.get(choice.offers().get(i).id());
            menu.inventory.setItem(SLOTS[i], augmentItem(player, profile, augment));
        }
        var odds = AugmentRarity.odds(profile.level);
        menu.inventory.setItem(18, item(Material.EXPERIENCE_BOTTLE, Component.text("추첨 레벨 " + profile.level),
                List.of(Component.text(round.prismaticOnly() ? "최종 회차: 프리즘 100%"
                        : String.format(Locale.ROOT, "실버 %.1f%% / 골드 %.1f%% / 프리즘 %.1f%%",
                                odds.silver(), odds.gold(), odds.prismatic())))));
        boolean rerolled = clock.get().hasRerolled(player.getUniqueId());
        menu.inventory.setItem(REROLL, item(rerolled ? Material.BARRIER : Material.PAPER,
                Component.text(rerolled ? "재추첨 사용 완료" : "재추첨 · 경기당 1회"), List.of()));
        setCountdown(menu, clock.get().selectionRemainingSeconds());
        profile.shown = round.round();
        player.openInventory(menu.inventory);
    }

    String openCodex(Player player) {
        var choice = clock.get() == null ? null : clock.get().selection().map(round -> round.choices().get(player.getUniqueId())).orElse(null);
        if (choice != null && choice.status() == SelectionRound.Status.PENDING && active.test(player)) {
            return "선택을 먼저 마치세요. /aw augment로 선택을 열 수 있습니다.";
        }
        var menu = new SummaryMenu(player.getUniqueId(), match);
        menu.inventory = plugin.getServer().createInventory(menu, 54, Component.text("나의 증강 도감"));
        renderCodex(player, menu);
        player.openInventory(menu.inventory);
        return "증강 도감을 열었습니다.";
    }

    private void renderCodex(Player player, SummaryMenu menu) {
        var stored = profiles.get(menu.owner);
        var profile = stored == null ? new Profile() : stored;
        menu.updated = clock.get() == null ? 0 : clock.get().elapsedSeconds();
        menu.inventory.clear();
        menu.favoriteButtons.clear();
        var favorites = PlayerUi.favorites(player);
        var definitions = registry.all().stream()
                .sorted(Comparator.comparing((AugmentDefinition a) -> !favorites.contains(a.id()))
                        .thenComparing(a -> !profile.owned.contains(a.id()))
                        .thenComparing(AugmentDefinition::rarity).thenComparing(AugmentDefinition::id)).toList();
        int last = (definitions.size() + CODEX_CARDS - 1) / CODEX_CARDS;
        if (menu.page == 0) {
            int slot = 0;
            for (var card : official.codexStatus(menu.owner)) menu.inventory.setItem(slot++, PaperIntel.item(card));
        } else {
            int offset = (menu.page - 1) * CODEX_CARDS;
            for (int i = offset; i < Math.min(offset + CODEX_CARDS, definitions.size()); i++) {
                var definition = definitions.get(i);
                menu.favoriteButtons.put(i - offset, definition.id());
                var card = augmentItem(player, profile, definition, true);
                var meta = card.getItemMeta();
                var lore = new ArrayList<>(meta.lore());
                lore.add(Component.text(favorites.contains(definition.id()) ? "★ 즐겨찾기 해제 · 클릭" : "☆ 즐겨찾기 등록 · 클릭"));
                meta.lore(lore);
                card.setItemMeta(meta);
                menu.inventory.setItem(i - offset, card);
            }
        }
        if (menu.page > 0) menu.inventory.setItem(45, item(Material.ARROW, Component.text("이전 페이지"), List.of()));
        menu.inventory.setItem(49, item(Material.BOOK, Component.text(menu.page == 0 ? "조합 · 진화 · 공명" : "증강 목록 " + menu.page + "/" + last),
                List.of(Component.text("보유 " + profile.owned.size() + "/" + definitions.size()))));
        if (menu.page < last) menu.inventory.setItem(53, item(Material.ARROW, Component.text("다음 페이지"), List.of()));
    }

    private ItemStack augmentItem(Player player, Profile profile, AugmentDefinition augment) {
        return augmentItem(player, profile, augment, false);
    }

    private ItemStack augmentItem(Player player, Profile profile, AugmentDefinition augment, boolean codex) {
        var color = switch (augment.rarity()) {
            case SILVER -> NamedTextColor.GRAY;
            case GOLD -> NamedTextColor.GOLD;
            case PRISMATIC -> NamedTextColor.LIGHT_PURPLE;
        };
        var icon = switch (augment.rarity()) {
            case SILVER -> Material.IRON_INGOT;
            case GOLD -> Material.GOLD_INGOT;
            case PRISMATIC -> Material.AMETHYST_SHARD;
        };
        double shown = augment.unit().startsWith("%") ? augment.amount() * 100 : augment.amount();
        List<Component> lore = new ArrayList<>(List.of(
                Component.text(augment.rarity().name() + " · " + AugmentRole.of(augment.id()).label(), color), Component.text("조건: " + augment.condition()),
                Component.text(String.format(Locale.ROOT, "효과: %.2f%s", shown, augment.unit())),
                Component.text("지속 " + (augment.duration() == 0 ? "상시 또는 즉시" : augment.duration() + "초")
                        + " · 쿨다운 " + augment.cooldown() + "초"),
                Component.text(profile.owned.contains(augment.id()) ? "보유 중"
                        : !augment.selectable() ? "비활성화" : codex ? "미보유" : "획득 가능")));
        if (codex) lore.add(Component.text(augment.id(), NamedTextColor.DARK_GRAY));
        if (!codex) {
            official.candidateRelations(player.getUniqueId(), augment.id(), profile.owned).forEach(line -> lore.add(Component.text(line)));
            lore.add(Component.text("상세 정보 · /aw augments"));
        } else if (profile.owned.contains(augment.id())) {
            official.resonanceDetails(player.getUniqueId(), augment.id()).forEach(line -> lore.add(Component.text(line)));
            official.details(player.getUniqueId(), augment.id()).forEach(line -> lore.add(Component.text(line)));
        }
        return item(icon, Component.text(augment.name(), color), lore);
    }

    private void showExchange(Player player, Profile profile) {
        var menu = new ExchangeMenu(match, player.getUniqueId(), profile.exchange);
        menu.inventory = plugin.getServer().createInventory(menu, 27, Component.text(registry.get(profile.exchangeId).name()));
        refreshExchange(menu);
        profile.exchangeShown = true;
        player.openInventory(menu.inventory);
    }

    private void refreshExchange(ExchangeMenu menu) {
        menu.inventory.clear();
        int slot = 9;
        for (String id : menu.exchange.candidates()) {
            var definition = registry.get(id);
            var material = definition.rarity() == AugmentRarity.GOLD ? Material.GOLD_INGOT : Material.AMETHYST_SHARD;
            menu.inventory.setItem(slot++, item(material, Component.text(definition.name()),
                    List.of(Component.text(definition.condition()))));
        }
        menu.inventory.setItem(22, item(Material.CLOCK, Component.text("남은 추가 선택 " + menu.exchange.remaining()), List.of()));
    }

    private void chooseExchange(Player player, ExchangeMenu menu, int rawSlot) {
        var profile = profiles.get(player.getUniqueId());
        if (profile == null || profile.exchange != menu.exchange || !menu.owner.equals(player.getUniqueId())
                || !menu.match.equals(match) || !active.test(player) || clock.get().isPaused()) return;
        int option = rawSlot - 9;
        var candidates = menu.exchange.candidates();
        if (option < 0 || option >= candidates.size() || !menu.exchange.select(candidates.get(option))) return;
        if (menu.exchange.remaining() > 0) {
            refreshExchange(menu);
            return;
        }
        var commit = menu.exchange.finalizeAgainst(profile.owned);
        String exchangeId = profile.exchangeId;
        profile.exchange = null;
        profile.exchangeId = null;
        menu.inventory.clear();
        if (commit.isEmpty()) {
            player.sendMessage("보유 증강이 바뀌어 교환을 취소했습니다.");
            return;
        }
        profile.owned.removeAll(commit.get().removed());
        profile.acquired.addAll(commit.get().awarded());
        profile.acquired.add(exchangeId);
        profile.owned.addAll(commit.get().awarded());
        profile.owned.add(exchangeId);
        for (String lost : commit.get().removed()) release(player, profile, lost, false);
        commit.get().awarded().forEach(gained -> {
            official.selected(player.getUniqueId(), gained, "exchange_reward");
            official.acquired(player.getUniqueId(), gained);
        });
        official.acquired(player.getUniqueId(), exchangeId);
        official.exchangeCommitted(player, exchangeId);
        player.sendMessage(registry.get(exchangeId).name() + " 완료");
    }

    private void setCountdown(Menu menu, long seconds) {
        if (menu.lastSeconds == seconds) return;
        menu.lastSeconds = seconds;
        menu.inventory.setItem(26, item(Material.CLOCK, Component.text("남은 시간 " + seconds + "초"), List.of()));
    }

    private ItemStack item(Material material, Component name, List<Component> lore) {
        var stack = new ItemStack(material);
        var meta = stack.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SummaryMenu codex) {
            boolean cancelled = event.isCancelled();
            int slot = PaperMenu.lock(event, false);
            if (cancelled || !(event.getWhoClicked() instanceof Player player) || !codex.owner.equals(player.getUniqueId())
                    || !Objects.equals(codex.match, match)) return;
            if (codex.favoriteButtons.containsKey(slot)) {
                if (!PlayerUi.toggleFavorite(player, codex.favoriteButtons.get(slot))) player.sendMessage("즐겨찾기는 최대 8개입니다.");
            } else if (slot == 45 && codex.page > 0) codex.page--;
            else if (slot == 53 && codex.page < (registry.all().size() + CODEX_CARDS - 1) / CODEX_CARDS) codex.page++;
            else return;
            renderCodex(player, codex);
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof ExchangeMenu exchange) {
            boolean alreadyCancelled = event.isCancelled();
            int slot = PaperMenu.lock(event, true);
            if (!alreadyCancelled && slot >= 0 && event.getWhoClicked() instanceof Player player) chooseExchange(player, exchange, slot);
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        boolean alreadyCancelled = event.isCancelled();
        int slot = PaperMenu.lock(event, true);
        if (alreadyCancelled || slot < 0 || !(event.getWhoClicked() instanceof Player player)
                || !menu.owner.equals(player.getUniqueId()) || !menu.match.equals(match) || !active.test(player)) return;
        var timer = clock.get();
        var round = timer == null ? null : timer.selection().orElse(null);
        if (round == null || round.round() != menu.round || !round.matchId().equals(menu.match)) return;
        var choice = round.choices().get(menu.owner);
        if (choice == null || !choice.offers().equals(menu.offers) || choice.status() != SelectionRound.Status.PENDING) return;
        var profile = profiles.get(menu.owner);
        if (slot == REROLL) {
            if (!timer.hasRerolled(menu.owner) && timer.reroll(menu.owner, menu.round, roll(profile, round.prismaticOnly(), menu.offers))) {
                telemetry.count(Counter.REROLLS);
            }
            return;
        }
        for (int i = 0; i < SLOTS.length && i < menu.offers.size(); i++) {
            if (slot == SLOTS[i] && timer.choose(menu.owner, menu.round, menu.offers.get(i).id())) {
                grant(menu.round, menu.owner, new SelectionRound.Choice(menu.offers, menu.offers.get(i).id(), SelectionRound.Status.SELECTED));
                return;
            }
        }
    }

    private Menu ownedMenu(Player player) {
        return player.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu ? menu : null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        if (!member.test(player.getUniqueId())) return;
        var profile = profiles.computeIfAbsent(player.getUniqueId(), ignored -> new Profile());
        profile.lastKnownLevel = Math.clamp(player.getLevel(), 0, 50);
        profile.shown = 0;
        profile.exchangeShown = false;
        official.disconnected(player);
        removeModifiers(player, profile);
    }

    void remove(UUID id) {
        var profile = profiles.remove(id);
        Player player = plugin.getServer().getPlayer(id);
        official.remove(id, player);
        if (player != null && profile != null) {
            var holder = player.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof Menu || holder instanceof ExchangeMenu || holder instanceof SummaryMenu) {
                player.closeInventory();
            }
            removeModifiers(player, profile);
        }
    }

    void clear() {
        List.copyOf(profiles.keySet()).forEach(this::remove);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof SummaryMenu) player.closeInventory();
        }
        official.clear();
        observedProcs.clear();
        completed = 0;
        match = null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void experience(PlayerExpChangeEvent event) {
        var player = event.getPlayer();
        var profile = activeProfile(player);
        if (profile == null || event.getAmount() <= 0) return;
        strongest(profile, Effect.XP).ifPresent(a -> guard.run(event, new ProcKey(player.getUniqueId(), "xp"),
                () -> event.setAmount(AugmentMath.experience(event.getAmount(), a.amount()))));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void mine(BlockBreakEvent event) {
        var player = event.getPlayer();
        var profile = activeProfile(player);
        String material = event.getBlock().getType().name();
        if (profile == null || player.getGameMode() != org.bukkit.GameMode.SURVIVAL || !event.isDropItems()
                || !(material.endsWith("_ORE") || material.equals("ANCIENT_DEBRIS"))) return;
        trigger(event, player, profile, Effect.MINING, this::activate);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void combat(EntityDamageByEntityEvent event) {
        if (official.synthetic()) return;
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player target)
                || event.getDamage() <= 0 || !active.test(attacker) || !active.test(target)
                || protectedPlayer.test(attacker) || protectedPlayer.test(target)) return;
        double base = official.rawDamage(event);
        var attacking = profiles.get(attacker.getUniqueId());
        var defending = profiles.get(target.getUniqueId());
        if (attacking != null) {
            strongest(attacking, Effect.BERSERK)
                    .filter(a -> attacking.timers.active(a.id(), clock.get().elapsedSeconds()))
                    .ifPresent(a -> guard.run(event, new ProcKey(attacker.getUniqueId(), "berserk_damage"),
                            () -> event.setDamage(AugmentMath.bonusHit(event.getDamage(), base, a.amount()))));
            var backwards = attacker.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
            var facing = target.getLocation().getDirection().setY(0);
            if (backwards.lengthSquared() > 1e-6 && facing.lengthSquared() > 1e-6
                    && backwards.normalize().dot(facing.normalize()) < rearDot) {
                trigger(event, attacker, attacking, Effect.REAR, (p,a) -> event.setDamage(AugmentMath.bonusHit(event.getDamage(), base, a.amount())));
            }
            if (attacker.getFallDistance() > 0 && !attacker.isOnGround()) {
                trigger(event, attacker, attacking, Effect.FALL, (p,a) -> event.setDamage(AugmentMath.bonusHit(event.getDamage(), base, a.amount())));
            }
        }
        if (defending != null && base >= strongHit) {
            trigger(event, target, defending, Effect.DEFENSE, (p,a) -> event.setDamage(AugmentMath.mitigate(event.getDamage(), a.amount())));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterCombat(EntityDamageByEntityEvent event) {
        if (official.synthetic()) return;
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player target)
                || event.getFinalDamage() <= 0 || !active.test(attacker) || !active.test(target)
                || protectedPlayer.test(attacker) || protectedPlayer.test(target)) return;
        var confirmed = observedProcs.remove(event);
        if (confirmed != null) confirmed.forEach(hud::activated);
        var attacking = profiles.get(attacker.getUniqueId());
        var defending = profiles.get(target.getUniqueId());
        if (attacking != null) {
            trigger(event, attacker, attacking, Effect.PURSUIT, this::activate);
            if (attacker.getHealth() <= maxHealth(attacker) * lowHealth) {
                trigger(event, attacker, attacking, Effect.BERSERK, this::activate);
            }
        }
        if (defending != null && target.getHealth() > event.getFinalDamage()) {
            trigger(event, target, defending, Effect.ARMOR, this::activate);
            if (target.getHealth() - event.getFinalDamage() <= maxHealth(target) * lowHealth) {
                trigger(event, target, defending, Effect.HEAL, this::activate);
            }
        }
    }

    private Profile activeProfile(Player player) {
        return active.test(player) ? profiles.get(player.getUniqueId()) : null;
    }

    private Optional<AugmentDefinition> strongest(Profile profile, Effect effect) {
        return profile.owned.stream().map(registry::get).filter(a -> a.effect() == effect)
                .max(Comparator.comparingDouble(AugmentDefinition::amount));
    }

    private void trigger(Object event, Player player, Profile profile, Effect effect,
                         java.util.function.BiConsumer<Player, AugmentDefinition> action) {
        long now = clock.get().elapsedSeconds();
        strongest(profile, effect).ifPresent(a -> {
            if (!profile.timers.ready(a.id(), now)) return;
            guard.run(event, new ProcKey(player.getUniqueId(), a.id()), () -> {
                profile.timers.cooldown(a.id(), now, official.cooldown(player, a.cooldown()));
                action.accept(player, a);
                if (event instanceof EntityDamageByEntityEvent hit) {
                    if (effect == Effect.REAR || effect == Effect.FALL || effect == Effect.DEFENSE) {
                        observedProcs.computeIfAbsent(hit, ignored -> new LinkedHashMap<>()).putIfAbsent(player, a.name());
                    } else hud.activated(player, a.name());
                }
                plugin.getLogger().fine("Augment proc match=" + match + " player=" + player.getUniqueId() + " id=" + a.id());
            });
        });
    }

    private void activate(Player player, AugmentDefinition definition) {
        var profile = profiles.get(player.getUniqueId());
        long now = clock.get().elapsedSeconds();
        profile.timers.activate(definition.id(), now, definition.duration());
    }

    private void syncEffects(Player player, Profile profile, long now) {
        if (!active.test(player)) {
            removeModifiers(player, profile);
            // Acquisition and cooldown persist, but active buffs end on death or elimination.
            profile.timers.clearActive();
            return;
        }
        for (String id : profile.owned) {
            var a = registry.get(id);
            boolean enabled = a.effect() == Effect.SPEED || profile.timers.active(id, now);
            if (a.effect() == Effect.HEAL && profile.timers.periodic(id, now)) {
                double previous = player.getHealth();
                player.setHealth(Math.min(maxHealth(player),previous + a.amount()));
                hud.heal(player,previous,player.getHealth());
            }
            profile.timers.expire(id, now);
            var attribute = attribute(a.effect());
            if (attribute != null && (enabled || profile.modifiers.contains(id))) {
                setModifier(player, profile, a, attribute, enabled);
            }
        }
    }

    private double maxHealth(Player player) {
        var health = player.getAttribute(Attribute.MAX_HEALTH);
        return health == null ? player.getHealth() : health.getValue();
    }

    private Attribute attribute(Effect effect) {
        return switch (effect) {
            case MINING -> Attribute.BLOCK_BREAK_SPEED;
            case SPEED, PURSUIT -> Attribute.MOVEMENT_SPEED;
            case ARMOR -> Attribute.ARMOR;
            default -> null;
        };
    }

    private void setModifier(Player player, Profile profile, AugmentDefinition a, Attribute attribute, boolean enabled) {
        var instance = player.getAttribute(attribute);
        if (instance == null) return;
        var key = new NamespacedKey(plugin, "augment_" + a.id());
        var operation = a.effect() == Effect.ARMOR ? AttributeModifier.Operation.ADD_NUMBER
                : AttributeModifier.Operation.ADD_SCALAR;
        PaperAugmentAttribute.update(instance, key, a.amount(), operation, enabled);
        if (enabled) profile.modifiers.add(a.id());
        else profile.modifiers.remove(a.id());
    }

    private void removeModifiers(Player player, Profile profile) {
        for (String id : List.copyOf(profile.modifiers)) {
            var a = registry.get(id);
            setModifier(player, profile, a, attribute(a.effect()), false);
        }
    }
}
