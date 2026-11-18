package io.github.augmentwild.paper;

import io.github.augmentwild.augment.*;
import io.github.augmentwild.augment.AugmentDefinition.Effect;
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
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
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
        final Set<String> owned = new LinkedHashSet<>();
        final AugmentTimers timers = new AugmentTimers();
        final Set<String> modifiers = new HashSet<>();
        int level, lastKnownLevel, shown, grantedRound;
        AugmentExchange exchange;
        String exchangeId;
        boolean exchangeShown;
    }

    private static final class SummaryMenu implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class ExchangeMenu implements InventoryHolder {
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

    private static final class Menu implements InventoryHolder {
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

    PaperAugments(JavaPlugin plugin, Supplier<MatchSimulationClock> clock, Predicate<UUID> member,
                  Predicate<Player> active, Predicate<Player> protectedPlayer, Supplier<MatchState> phase,
                  Predicate<Location> arena, Predicate<Location> safe, BiPredicate<Player, Location> teleport,
                  BiConsumer<UUID, Integer> protection, int maxDepth) {
        this.plugin = plugin;
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
                }, maxDepth);
        roller = new AugmentRoller(registry, random);
        guard = new ProcGuard(maxDepth);
    }

    String cast(Player player, String id) { return official.cast(player, id); }

    Listener officialListener() { return official; }

    void start(UUID id, MatchSimulationClock timer) {
        match = id;
        official.start(id);
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
            result.put(playerId, roll(profile, last, List.of()));
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
        if (timer == null || profile == null) return;
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
        if (EXCHANGES.contains(definition.id())) {
            startExchange(profile, definition.id());
            return;
        }
        boolean added = profile.owned.add(definition.id());
        if (added) official.acquired(id, definition.id());
        plugin.getLogger().info("Augment " + choice.status() + " match=" + match + " player=" + id
                + " round=" + round + " id=" + definition.id() + " added=" + added);
        var player = plugin.getServer().getPlayer(id);
        if (player != null) {
            player.sendMessage(definition.name() + (added ? " 증강을 획득했습니다." : " 증강은 이미 활성화돼 있습니다."));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .45f,
                    definition.rarity() == AugmentRarity.PRISMATIC ? 1.35f : 1f);
        }
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
            showSummary(player, profile);
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
        player.sendActionBar(Component.text("증강을 선택하세요. 경험치는 소모되지 않습니다.", NamedTextColor.GOLD));
    }

    private void showSummary(Player player, Profile profile) {
        var menu = new SummaryMenu();
        menu.inventory = plugin.getServer().createInventory(menu, 27, Component.text("보유 증강"));
        int slot = 0;
        for (String id : profile.owned) {
            if (slot >= menu.inventory.getSize()) break;
            menu.inventory.setItem(slot++, augmentItem(player, profile, registry.get(id)));
        }
        player.openInventory(menu.inventory);
    }

    private ItemStack augmentItem(Player player, Profile profile, AugmentDefinition augment) {
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
                Component.text(augment.rarity().name(), color), Component.text("조건: " + augment.condition()),
                Component.text(String.format(Locale.ROOT, "효과: %.2f%s", shown, augment.unit())),
                Component.text("지속 " + (augment.duration() == 0 ? "상시 또는 즉시" : augment.duration() + "초")
                        + " · 쿨다운 " + augment.cooldown() + "초"),
                Component.text(profile.owned.contains(augment.id()) ? "보유 중" : "획득 가능")));
        official.details(player.getUniqueId(), augment.id()).forEach(line -> lore.add(Component.text(line)));
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
        profile.owned.addAll(commit.get().awarded());
        profile.owned.add(exchangeId);
        for (String lost : commit.get().removed()) {
            var definition = registry.get(lost);
            if (profile.modifiers.contains(lost)) setModifier(player, profile, definition, attribute(definition.effect()), false);
            profile.timers.remove(lost);
            official.lost(player.getUniqueId(), lost);
        }
        commit.get().awarded().forEach(gained -> official.acquired(player.getUniqueId(), gained));
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
        if (event.getView().getTopInventory().getHolder() instanceof SummaryMenu) {
            event.setCancelled(true);
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof ExchangeMenu exchange) {
            boolean alreadyCancelled = event.isCancelled();
            event.setCancelled(true);
            if (!alreadyCancelled && event.getWhoClicked() instanceof Player player
                    && (event.getClick() == org.bukkit.event.inventory.ClickType.LEFT
                        || event.getClick() == org.bukkit.event.inventory.ClickType.RIGHT)) {
                chooseExchange(player, exchange, event.getRawSlot());
            }
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        boolean alreadyCancelled = event.isCancelled();
        event.setCancelled(true);
        if (alreadyCancelled || !(event.getWhoClicked() instanceof Player player)
                || !menu.owner.equals(player.getUniqueId()) || !menu.match.equals(match) || !active.test(player)
                || (event.getClick() != org.bukkit.event.inventory.ClickType.LEFT
                    && event.getClick() != org.bukkit.event.inventory.ClickType.RIGHT)) return;
        var timer = clock.get();
        var round = timer == null ? null : timer.selection().orElse(null);
        if (round == null || round.round() != menu.round || !round.matchId().equals(menu.match)) return;
        var choice = round.choices().get(menu.owner);
        if (choice == null || !choice.offers().equals(menu.offers) || choice.status() != SelectionRound.Status.PENDING) return;
        int slot = event.getRawSlot();
        var profile = profiles.get(menu.owner);
        if (slot == REROLL) {
            if (!timer.hasRerolled(menu.owner)) timer.reroll(menu.owner, menu.round, roll(profile, round.prismaticOnly(), menu.offers));
            return;
        }
        for (int i = 0; i < SLOTS.length && i < menu.offers.size(); i++) {
            if (slot == SLOTS[i] && timer.choose(menu.owner, menu.round, menu.offers.get(i).id())) {
                grant(menu.round, menu.owner, new SelectionRound.Choice(menu.offers, menu.offers.get(i).id(), SelectionRound.Status.SELECTED));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu
                || event.getView().getTopInventory().getHolder() instanceof ExchangeMenu
                || event.getView().getTopInventory().getHolder() instanceof SummaryMenu) event.setCancelled(true);
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
        official.clear();
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
                player.setHealth(Math.min(maxHealth(player), player.getHealth() + a.amount()));
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
