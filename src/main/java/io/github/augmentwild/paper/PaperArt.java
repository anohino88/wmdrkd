package io.github.augmentwild.paper;

import io.github.augmentwild.augment.AugmentDefinition;
import io.github.augmentwild.augment.EffectGeometry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Particle;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.inventory.Inventory;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Optional client-side assets backed by bounded, private, non-persistent Paper displays. */
public final class PaperArt implements Listener {
    private static final float[] GROW = {.3f, 1.45f, .25f};
    private static final float[] SWEEP = {.35f, 1.7f, .5f};
    private static final float[] HOLD = {.45f, 1.2f, .85f};
    private long privateParticles, displaySpawns, displayUpdates;
    private int peakDisplays;
    private final Map<UUID,Integer> cueSounds = new HashMap<>();
    private final Map<UUID,Integer> particleUnits = new HashMap<>();
    private static final int MAX_DISPLAYS = 24;
    private final JavaPlugin plugin;
    private final Predicate<Player> playable;
    private final Map<UUID,PlayerResourcePackStatusEvent.Status> reports = new HashMap<>();
    private final Map<UUID,Long> lastOffer = new HashMap<>();
    private final Set<UUID> loaded = new HashSet<>();
    private record PrivateCue(UUID owner, String model) {}
    private final Map<PrivateCue, Animation> cues = new HashMap<>();
    private final Map<Location, Marker> supply = new HashMap<>();
    private final Map<PrivateCue, Long> nextCue = new HashMap<>();
    private final CombatCinematics combat;
    private final Set<String> quiet = new HashSet<>(), noVisual = new HashSet<>();
    private final Map<String,String> labels = new HashMap<>();
    private final Properties screens = new Properties();
    private final Properties motifs = new Properties();
    private final Map<String,ArtSignature> signatures;
    private final boolean matchingPack;
    private final ResourcePackInfo pack;
    private final String settingsIssue;
    private String validation = "서버 다운로드 검증 전";
    private boolean validating;
    private static final NamespacedKey SAFE = new NamespacedKey("augmentwild","art_safe");
    private static final NamespacedKey LAST_PACK = new NamespacedKey("augmentwild","last_pack");
    private long tick;
    private boolean displaysAvailable = true;

    private record Animation(Player viewer, ItemDisplay entity, Location at, long began, Motion motion,
                             List<ItemDisplay> ornaments, ArtSignature signature) {
        void remove() {
            entity.remove();
            ornaments.forEach(ItemDisplay::remove);
        }
    }
    private record Marker(ItemDisplay entity, Set<UUID> viewers) {}
    private record Motion(float[] scales, float rise, float spin, float travel) {
        int attack() { return scales == SWEEP ? 3 : scales == HOLD ? 5 : 4; }
        int release() { return scales == SWEEP ? 5 : scales == HOLD ? 10 : 7; }
    }
    private static final Motion PULSE = new Motion(GROW,.2f,1.4f,0);
    private static final Motion ARRIVE = new Motion(HOLD,-.55f,1.6f,0);
    private static final Map<String, Motion> CASTS = Map.of(
            "shadow_dash", new Motion(SWEEP,.06f,.7f,1.3f),
            "earthquake_mage", new Motion(SWEEP,-.24f,.2f,.65f),
            "time_rewind", new Motion(GROW,.45f,-2.7f,-.35f),
            "calamity_giant", new Motion(SWEEP,-.65f,.05f,0),
            "duel_contract", new Motion(HOLD,.15f,1.4f,1.1f),
            "bomb_alchemy", new Motion(GROW,.65f,1.95f,1.3f),
            "void_lord", new Motion(HOLD,-.35f,-2.2f,0));
    private static final Map<String, Motion> GROWTH = Map.ofEntries(
            Map.entry("steel_heart",new Motion(HOLD,.85f,.35f,0)),
            Map.entry("magic_missile",new Motion(SWEEP,.8f,2.4f,.7f)),
            Map.entry("berserk_engine",new Motion(SWEEP,.2f,.75f,1.5f)),
            Map.entry("infernal_conductor",new Motion(GROW,1f,1.2f,0)),
            Map.entry("tank_engine",new Motion(HOLD,-.3f,1.5f,0)),
            Map.entry("sword_wind",new Motion(SWEEP,.25f,2.65f,.65f)),
            Map.entry("steel_king",new Motion(HOLD,.4f,2.7f,0)),
            Map.entry("calamity_train",new Motion(SWEEP,-.25f,.2f,1.15f)),
            Map.entry("infinite_barrage",new Motion(SWEEP,.35f,2.5f,.9f)),
            Map.entry("hell_lord",new Motion(GROW,1.2f,-2.1f,0)),
            Map.entry("thousand_swords",new Motion(SWEEP,.7f,3f,.45f)),
            Map.entry("stacking",new Motion(HOLD,1.2f,.9f,0)),
            Map.entry("life_echo",new Motion(HOLD,.15f,-1.3f,0)));
    private static final Map<String, String> SIGNATURES = Map.of(
            "steel_king","ward","calamity_train","impact","infinite_barrage","bolt",
            "hell_lord","fire","thousand_swords","blade","stacking","growth","life_echo","ward");

    enum Screen { MENU, CHOICE, DETAIL, CODEX, SLOTS, SETTINGS, LAB, RESULT, SUPPLY, JOURNAL, WARNING, SCOUT }
    private static final Map<String,Motion> MOTIONS = Map.ofEntries(
            Map.entry("blade",new Motion(SWEEP,.22f,2.7f,.6f)), Map.entry("bolt",new Motion(SWEEP,.18f,.45f,1.3f)),
            Map.entry("fire",new Motion(GROW,.8f,1.5f,.13f)), Map.entry("frost",new Motion(HOLD,.25f,-.7f,0)),
            Map.entry("impact",new Motion(SWEEP,-.5f,.15f,0)), Map.entry("ward",new Motion(HOLD,.13f,-.4f,0)),
            Map.entry("rush",new Motion(SWEEP,.15f,.65f,1.1f)), Map.entry("void",new Motion(GROW,-.3f,-2.4f,-.2f)),
            Map.entry("blood",new Motion(HOLD,.4f,.7f,-.3f)), Map.entry("growth",new Motion(GROW,.65f,.4f,0)),
            Map.entry("time",new Motion(HOLD,.28f,-2.5f,-.15f)), Map.entry("rune",PULSE));
    private static final List<String> GLYPHS = List.of("bomb_alchemy","calamity_giant","duel_contract",
            "earthquake_mage","shadow_dash","time_rewind","void_lord");

    Component slotIcon(Player player, String id) {
        int index = GLYPHS.indexOf(id);
        if (index < 0 || !custom(player)) return Component.empty();
        return Component.text(String.valueOf((char)(0xe100 + index))).font(Key.key("augmentwild:skills"));
    }

    Component title(Player p, Screen screen, String label) {
        // Inventory text uses the native baseline and layout. Large title glyphs can paint across slots.
        return Component.text(label).font(Key.key("minecraft:default"));
    }

    private boolean custom(Player player) {
        return matchingPack && loaded.contains(player.getUniqueId()) && !safe(player);
    }

    static boolean safe(Player player) {
        return player.getPersistentDataContainer().has(SAFE,PersistentDataType.BYTE);
    }

    String toggleSafe(Player player) {
        if (safe(player)) player.getPersistentDataContainer().remove(SAFE);
        else {
            player.getPersistentDataContainer().set(SAFE,PersistentDataType.BYTE,(byte)1);
            forget(player.getUniqueId());
            restoreMenuIcons(player);
        }
        return packStatus(player);
    }

    private void restoreMenuIcons(Player player) {
        var view = player.getOpenInventory();
        if (view == null || !PaperMenu.protectedInventory(view.getTopInventory())) return;
        var inventory = view.getTopInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            var stack = inventory.getItem(i);
            if (stack == null || !stack.hasItemMeta()) continue;
            var meta = stack.getItemMeta();
            var model = meta.getItemModel();
            if (model == null || !model.getNamespace().equals("augmentwild")) continue;
            meta.setItemModel(null);
            stack.setItemMeta(meta);
            inventory.setItem(i,stack);
        }
    }

    void button(Player p, Inventory inv, int slot, String key) {
        var item = inv.getItem(slot);
        if (item != null) inv.setItem(slot, icon(p,item,"gui/" + key));
    }

    String packStatus(Player p) {
        if (pack == null) return settingsIssue + " · 기본 표시";
        if (!matchingPack) return "팩 SHA-1이 7.7 배포본과 다릅니다 · 기본 표시";
        String mode = safe(p) ? " · 안전 모드" : "";
        if (loaded.contains(p.getUniqueId())) return "클라이언트 로드 응답 · " + pack.hash().substring(0,8) + mode;
        var status = reports.get(p.getUniqueId());
        if (status == null) return (lastOffer.containsKey(p.getUniqueId()) ? "클라이언트 응답 대기" : "요청 전") + mode;
        return switch (status) {
            case ACCEPTED, DOWNLOADED -> "팩 적용 중 · " + status.name();
            case DECLINED -> "팩 거절 · 바닐라 표시";
            default -> "팩 미적용 · " + status.name() + " · 바닐라 표시";
        };
    }

    public PaperArt(JavaPlugin plugin, Predicate<Player> playable) {
        this(plugin,playable,() -> true,at -> at.getNearbyPlayers(24));
    }

    PaperArt(JavaPlugin plugin, Predicate<Player> playable, java.util.function.BooleanSupplier running,
             CombatCinematics.Audience audience) {
        this.plugin = plugin;
        var configured = settings(plugin);
        pack = configured.pack();
        settingsIssue = configured.issue();
        try (var digest = plugin.getResource("art-pack.sha1")) {
            String shipped = digest == null ? "" : new String(digest.readAllBytes(),StandardCharsets.UTF_8).strip();
            matchingPack = pack != null && pack.hash().equals(shipped);
            signatures = ArtSignature.load(plugin.getResource("art-signatures.tsv"));
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read AugmentWild artwork settings",failure);
        }
        this.playable = playable;
        combat = new CombatCinematics(plugin,playable,this::custom,running,
                this::roomForCombat,this::spawn,audience);
        try (var input = plugin.getResource("art-motifs.properties")) {
            if (input != null) motifs.load(input);
            try (var layouts = plugin.getResource("art-screen-samples.properties")) {
                if (layouts != null) screens.load(layouts);
            }
        } catch (IOException failure) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,"Unable to read augment artwork mapping",failure);
        }
    }

    private static final class DiagnosticsMenu implements PaperMenu {
        final UUID owner;
        final Inventory inventory;
        DiagnosticsMenu(Player player) {
            owner = player.getUniqueId();
            inventory = Bukkit.createInventory(this,27,Component.text("리소스팩 진단"));
        }
        @Override public Inventory getInventory() { return inventory; }
        @Override public boolean availableWhileRestricted() { return true; }
    }

    String openDiagnostics(Player player,boolean admin) {
        var menu = new DiagnosticsMenu(player);
        menu.inventory.setItem(4,PaperIntel.item(PaperIntel.card(Material.MAP,"클라이언트 상태",packStatus(player))));
        menu.inventory.setItem(10,PaperIntel.item(PaperIntel.card(Material.LIME_DYE,"리소스팩 다시 요청")));
        menu.inventory.setItem(12,PaperIntel.item(PaperIntel.card(Material.SHIELD,
                "안전 모드 · " + (safe(player) ? "켜짐" : "꺼짐"),"기본 글꼴·아이템·전투 연출 사용")));
        if (admin) {
            menu.inventory.setItem(14,PaperIntel.item(PaperIntel.card(Material.SPYGLASS,"서버 다운로드 검증",validation)));
            menu.inventory.setItem(16,PaperIntel.item(PaperIntel.card(Material.WRITABLE_BOOK,"접속 세션 상태 보고")));
        }
        menu.inventory.setItem(26,PaperIntel.item(PaperIntel.card(Material.ARROW,"메인 메뉴")));
        player.openInventory(menu.inventory);
        return "리소스팩 진단 메뉴를 열었습니다.";
    }

    @EventHandler public void diagnosticsClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof DiagnosticsMenu menu)) return;
        boolean alreadyCancelled = event.isCancelled();
        int slot = PaperMenu.lock(event,false);
        if (alreadyCancelled || !(event.getWhoClicked() instanceof Player player)
                || !menu.owner.equals(player.getUniqueId()) || !player.hasPermission("augmentwild.play")) return;
        boolean admin = player.hasPermission("augmentwild.admin");
        switch (slot) {
            case 10 -> player.sendMessage(retry(player));
            case 12 -> {
                player.sendMessage(toggleSafe(player));
                plugin.getServer().getScheduler().runTask(plugin,() -> {
                    if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == menu)
                        openDiagnostics(player,player.hasPermission("augmentwild.admin"));
                });
            }
            case 14 -> { if (admin) player.sendMessage(validate(player)); }
            case 16 -> { if (admin) player.sendMessage(diagnostics(true)); }
            case 26 -> plugin.getServer().getScheduler().runTask(plugin,() -> {
                if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == menu) player.performCommand("aw menu");
            });
            default -> { }
        }
    }

    @EventHandler public void diagnosticsDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof DiagnosticsMenu) event.setCancelled(true);
    }

    private record Settings(ResourcePackInfo pack,String issue) {}

    private static Settings settings(JavaPlugin plugin) {
        String url = plugin.getConfig().getString("art.url","").strip();
        if (url.isEmpty()) return new Settings(null,"art.url 미설정");
        try {
            var uri = PackInspection.directUri(url);
            String hash = plugin.getConfig().getString("art.sha1","").strip().toLowerCase(Locale.ROOT);
            if (!hash.matches("[0-9a-f]{40}")) return new Settings(null,"art.sha1 오류 · 40자리 16진수 필요");
            UUID id = UUID.nameUUIDFromBytes(("augmentwild-art:" + hash).getBytes(StandardCharsets.UTF_8));
            return new Settings(ResourcePackInfo.resourcePackInfo(id,uri,hash),"");
        } catch (IllegalArgumentException failure) {
            return new Settings(null,"art.url 오류 · 직접 HTTPS ZIP 필요");
        }
    }

    String diagnostics(boolean detail) {
        if (pack == null) return settingsIssue + " · 클라이언트 기본 표시";
        var counts = new EnumMap<PlayerResourcePackStatusEvent.Status,Integer>(PlayerResourcePackStatusEvent.Status.class);
        reports.values().forEach(status -> counts.merge(status,1,Integer::sum));
        String report = "HTTPS 팩 · SHA-1 " + pack.hash() + " · UUID " + pack.id()
                + (matchingPack ? " · 배포본 일치" : " · 배포본 불일치 · 기본 표시")
                + "\n서버 다운로드 · " + validation + "\n접속 세션 응답 · " + counts;
        if (!detail) return report;
        var uri = pack.uri();
        report += "\n" + uri.getScheme() + "://" + uri.getHost() + uri.getPath()
                + (uri.getRawQuery() == null ? "" : " · 쿼리 생략");
        for (Player player : plugin.getServer().getOnlinePlayers())
            report += "\n" + player.getName() + " · " + packStatus(player);
        return report;
    }

    String validate(CommandSender sender) {
        if (pack == null) return settingsIssue;
        if (validating) return "검증 중입니다.";
        validating = true;
        validation = "검증 중";
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,() -> {
            String result;
            try { result = PackInspection.download(pack.uri(),pack.hash()); }
            catch (IOException failure) {
                String message = failure.getMessage();
                // TLS/connection error messages may contain URLs or address details.
                result = "검증 실패 · " + (message != null && message.matches("^[가-힣A-Z0-9].{0,110}$")
                        && !message.contains("://") ? message : failure.getClass().getSimpleName());
            }
            String completed = result;
            if (plugin.isEnabled()) plugin.getServer().getScheduler().runTask(plugin,() -> {
                validation = completed;
                validating = false;
                if (sender.hasPermission("augmentwild.admin")) sender.sendMessage(completed);
            });
        });
        return "서버에서 HTTPS ZIP 검증을 시작했습니다. 결과를 채팅에 표시합니다.";
    }

    @EventHandler public void join(PlayerJoinEvent event) { offer(event.getPlayer()); }

    String retry(Player player) {
        if (!matchingPack) return packStatus(player);
        Long last = lastOffer.get(player.getUniqueId());
        if (last != null && System.nanoTime() - last < 10_000_000_000L) return "최근 요청을 보냈습니다. 잠시 후 다시 시도하세요.";
        offer(player);
        return "리소스팩을 다시 요청했습니다.";
    }

    void offer(Player player) {
        var storage = player.getPersistentDataContainer();
        var previous = storage.get(LAST_PACK,PersistentDataType.STRING);
        if (previous != null) {
            try { player.removeResourcePacks(UUID.fromString(previous)); }
            catch (IllegalArgumentException invalid) { storage.remove(LAST_PACK); }
        }
        if (!matchingPack) {
            storage.remove(LAST_PACK);
            return;
        }
        storage.set(LAST_PACK,PersistentDataType.STRING,pack.id().toString());
        loaded.remove(player.getUniqueId());
        forget(player.getUniqueId());
        restoreMenuIcons(player);
        lastOffer.put(player.getUniqueId(),System.nanoTime());
        reports.remove(player.getUniqueId());
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest().packs(pack)
                .replace(false).required(false).prompt(Component.text("AugmentWild 장식 리소스팩 (선택 사항)")).build());
    }

    @EventHandler public void quit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        loaded.remove(id);
        reports.remove(id);
        lastOffer.remove(id);
        forget(id);
    }

    @EventHandler public void status(PlayerResourcePackStatusEvent event) {
        if (pack == null || !pack.id().equals(event.getID())) return;
        UUID player = event.getPlayer().getUniqueId();
        reports.put(player,event.getStatus());
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) loaded.add(player);
        else {
            boolean previouslyLoaded = loaded.remove(player);
            forget(player);
            if (previouslyLoaded) restoreMenuIcons(event.getPlayer());
        }
    }

    public ItemStack icon(Player viewer, ItemStack item, String key) {
        if (!custom(viewer)) return item;
        if (key.startsWith("augment/") && !motifs.containsKey(key.substring("augment/".length()))) return item;
        return model(item,key);
    }

    private static ItemStack model(ItemStack item, String key) {
        item.editMeta(meta -> meta.setItemModel(new NamespacedKey("augmentwild",key)));
        return item;
    }

    private final Map<String,List<String>> byRarity = new HashMap<>();

    Map<Integer,String> sampleCards(Screen screen) {
        var slots = new LinkedHashMap<Integer,String>();
        String value = screens.getProperty(screen.name().toLowerCase(Locale.ROOT),"");
        if (!value.isBlank()) for (String pair : value.split(" ")) {
            String[] fields = pair.split("=",2);
            slots.put(Integer.parseInt(fields[0]),fields[1]);
        }
        return slots;
    }

    void register(AugmentDefinition definition) {
        labels.put(definition.id(),definition.name());
        byRarity.computeIfAbsent(definition.rarity().name(),ignored -> new ArrayList<>()).add(definition.id());
    }

    String[] rarityCards(String tier) {
        return byRarity.getOrDefault(tier.toUpperCase(Locale.ROOT),List.of()).stream()
                .sorted().limit(3).map(id -> "augment/" + id).toArray(String[]::new);
    }

    String label(String key) {
        String id = key.substring(key.indexOf('/') + 1);
        return labels.getOrDefault(id,id.replace('_',' '));
    }

    void configure(String id, boolean particles, boolean sound) {
        if (!particles) noVisual.add(id);
        if (!sound) quiet.add(id);
    }

    void cue(UUID owner, String id) {
        Player player = plugin.getServer().getPlayer(owner);
        if (player != null) cue(player,id);
    }

    void cue(Player player, String id) {
        String motif = motifs.getProperty(id);
        var key = new PrivateCue(player.getUniqueId(),"proc/" + id);
        if (motif == null || !playable.test(player) || tick < nextCue.getOrDefault(key,0L)) return;
        nextCue.put(key,tick + 12);
        showProc(player,id,motif,false);
    }

    private void showProc(Player player, String id, String motif, boolean simple) {
        showProc(player,id,motif,simple,near(player));
    }

    private void showProc(Player player,String id,String motif,boolean simple,Location at) {
        if (!noVisual.contains(id) && !simple && PlayerUi.quality(player) != PlayerUi.Quality.LOW && custom(player) && displaysAvailable) {
            render(player,at,"proc/" + id,CASTS.getOrDefault(id,MOTIONS.getOrDefault(motif,PULSE)));
        } else if (!noVisual.contains(id) && PlayerUi.enabled(player,PlayerUi.Option.PARTICLES)) {
            Particle particle = switch (motif) {
                case "fire" -> Particle.FLAME;
                case "frost", "time" -> Particle.SNOWFLAKE;
                case "blade", "impact" -> Particle.CRIT;
                case "void" -> Particle.PORTAL;
                case "growth", "blood" -> Particle.HAPPY_VILLAGER;
                default -> Particle.ELECTRIC_SPARK;
            };
            player.spawnParticle(particle,at,4,.17,.16,.17,.01);
            privateParticles++;
        }
        if (quiet.contains(id) || !PlayerUi.enabled(player,PlayerUi.Option.SOUND)) return;
        int used = cueSounds.getOrDefault(player.getUniqueId(),0);
        if (used >= 2) return;
        cueSounds.put(player.getUniqueId(),used+1);
        Sound cue = switch (motif) {
            case "blade","rush" -> Sound.ENTITY_PLAYER_ATTACK_SWEEP;
            case "frost" -> Sound.BLOCK_GLASS_HIT;
            case "fire" -> Sound.ITEM_FIRECHARGE_USE;
            case "void" -> Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE;
            case "time" -> Sound.BLOCK_NOTE_BLOCK_CHIME;
            case "impact" -> Sound.BLOCK_BASALT_HIT;
            case "ward" -> Sound.BLOCK_CHAIN_HIT;
            case "blood" -> Sound.BLOCK_BEACON_AMBIENT;
            case "growth" -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
            case "bolt" -> Sound.BLOCK_COPPER_BULB_TURN_ON;
            default -> Sound.BLOCK_ENCHANTMENT_TABLE_USE;
        };
        player.playSound(at,cue,.28f,1f);
        if (!simple && custom(player) && used == 0
                && PlayerUi.quality(player).ordinal() >= PlayerUi.Quality.HIGH.ordinal())
            player.playSound(at,"augmentwild:cast",.13f,1.15f);

    }

    void choice(Player p, String id, String rarity) {
        if (!playable.test(p)) return;
        if (rarity.equals("PRISMATIC")) {
            locationSound(p,near(p),"prism",cueFallback("prism"));
            if (!custom(p)) spark(p,near(p),Particle.END_ROD);
        }
        if (!custom(p)) return;
        var motion = switch (rarity) { case "PRISMATIC" -> GROWTH.get("magic_missile"); case "GOLD" -> MOTIONS.get("ward"); default -> PULSE; };
        render(p,near(p),"proc/" + id,motion);
    }

    void cast(Player player, String id, Location origin) {
        Location destination = player.getLocation();
        var delta = destination.toVector().subtract(origin.toVector());
        if (id.equals("shadow_dash") && destination.getWorld().equals(origin.getWorld())) {
            int steps = Math.min(12,(int)Math.ceil(delta.length()));
            for (int i = 0; i < steps; i++)
                spark(player,origin.clone().add(delta.clone().multiply((double)i / steps)).add(0,.75,0),Particle.REVERSE_PORTAL);
            ring(player,origin.clone().add(0,.4,0),.47,Particle.REVERSE_PORTAL,true);
        }
        nextCue.put(new PrivateCue(player.getUniqueId(),"proc/" + id),tick + 20);
        var at = switch (id) {
            case "earthquake_mage", "void_lord" -> origin.clone().add(0,.32,0);
            case "shadow_dash","time_rewind" -> destination.clone().add(0,.5,0);
            case "duel_contract" -> destination.clone().add(0,.34,0);
            default -> near(player);
        };
        if ((id.equals("shadow_dash") || id.equals("earthquake_mage")) && delta.lengthSquared() > .01)
            at.setDirection(delta);
        if (id.equals("shadow_dash") && destination.getWorld().equals(origin.getWorld()) && delta.lengthSquared() > .01) {
            var anchor = origin.clone().add(0,.5,0).setDirection(delta);
            signatureAt(player,id,anchor,true,signatures.get(id).along((float)Math.hypot(delta.getX(),delta.getZ()),(float)delta.getY()));
        } else if (!id.equals("calamity_giant")) signatureAt(player,id,at,true);
        var form = switch (id) {
            case "time_rewind" -> CombatCinematics.Form.RETURN;
            case "void_lord" -> CombatCinematics.Form.VOID;
            default -> null;
        };
        if (form != null) combat(player,null,null,id,form,at,0);
    }

    void signatureAt(Player player,String id,Location at,boolean sound) {
        signatureAt(player,id,at,sound,signatures.get(id));
    }

    private void signatureAt(Player player,String id,Location at,boolean sound,ArtSignature signature) {
        String effect = switch (id) {
            case "bomb_impact" -> "bomb_alchemy";
            case "void_collapse" -> "void_lord";
            default -> id;
        };
        if (signature == null || !playable.test(player) || !player.getWorld().equals(at.getWorld()) || !at.isChunkLoaded()
                || noVisual.contains(effect) && (!sound || quiet.contains(effect))) return;
        if (!noVisual.contains(effect) && PlayerUi.enabled(player,PlayerUi.Option.PARTICLES)) {
            if (PlayerUi.quality(player) != PlayerUi.Quality.LOW && custom(player) && !PlayerUi.reducedFlash(player)
                    && displaysAvailable && displayCount() < MAX_DISPLAYS) {
                render(player,at,"sig_" + signature.shape(),MOTIONS.getOrDefault(motifs.getProperty(effect,"rune"),PULSE),signature);
            } else spark(player,at,signatureParticle(effect));
        }
        if (sound && !quiet.contains(effect)) locationSound(player,at,signature.sound(),cueFallback(signature.sound()));
    }

    private static Particle signatureParticle(String effect) {
        return switch (effect) {
            case "shadow_dash","void_lord" -> Particle.REVERSE_PORTAL;
            case "earthquake_mage","calamity_giant" -> Particle.CLOUD;
            case "time_rewind" -> Particle.END_ROD;
            case "duel_contract" -> Particle.ENCHANTED_HIT;
            case "bomb_alchemy" -> Particle.FLAME;
            default -> Particle.ELECTRIC_SPARK;
        };
    }

    private static Sound cueFallback(String key) {
        return switch (key) {
            case "rift","focus" -> Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE;
            case "stone_break" -> Sound.BLOCK_BASALT_BREAK;
            case "ignite" -> Sound.ITEM_FIRECHARGE_USE;
            case "detonate" -> Sound.ENTITY_GENERIC_EXPLODE;
            case "rewind" -> Sound.ENTITY_ENDERMAN_TELEPORT;
            case "seal","prism" -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
            case "shield_form" -> Sound.ITEM_SHIELD_BLOCK;
            case "shield_break" -> Sound.ITEM_SHIELD_BREAK;
            case "sweep" -> Sound.ENTITY_PLAYER_ATTACK_SWEEP;
            default -> Sound.ENTITY_PLAYER_LEVELUP;
        };
    }

    // Owner-only decoration. Never used to resolve a hit or select a target.
    void ring(Player viewer, Location center, double radius, Particle material, boolean closing) {
        int samples = PlayerUi.quality(viewer).samples;
        for (int i = 0; i < samples; i++) {
            double angle = Math.PI * 2 * i / samples + (closing ? -.16 : .16) * tick;
            spark(viewer, center.clone().add(radius * Math.cos(angle), .18, radius * Math.sin(angle)), material);
        }
    }

    private record FieldKey(UUID owner,String effect) {}
    private record Field(ItemDisplay model,Location source,long lastSeen) {}
    private final Map<FieldKey,Field> fields = new HashMap<>();

    /** Call from the live mechanic each running tick. No damage or gameplay timers live here. */
    void sustain(Player viewer,String effect,Location source,boolean moving) {
        var key = new FieldKey(viewer.getUniqueId(),effect);
        var previous = fields.get(key);
        if (pack == null || !displaysAvailable || !custom(viewer) || !PlayerUi.auras(viewer)
                || !PlayerUi.enabled(viewer,PlayerUi.Option.PARTICLES) || noVisual.contains(effect)
                || PlayerUi.quality(viewer) == PlayerUi.Quality.LOW || !viewer.getWorld().equals(source.getWorld())
                || viewer.getLocation().distanceSquared(source)>24*24 || !source.isChunkLoaded()) {
            endField(key);
            return;
        }
        try {
            var at = source.clone().add(0,moving ? 0 : .5,0);
            if (previous == null || !previous.model.isValid() || !previous.source.getWorld().equals(at.getWorld())) {
                endField(key);
                if (displayCount() >= MAX_DISPLAYS) return;
                var model = spawn(at,"proc/" + effect,fieldPose(moving));
                fields.put(key,new Field(model,at,tick));
                viewer.showEntity(plugin,model);
                peakDisplays = Math.max(peakDisplays,displayCount());
                return;
            }
            var model = previous.model;
            Location anchor = previous.source;
            if (previous.source.distanceSquared(at) > .01) {
                model.setTeleportDuration(moving ? 1 : 0);
                if (!model.teleport(at)) {
                    endField(key);
                    return;
                }
                anchor = at;
            }
            if (tick % 4 == 0) {
                model.setInterpolationDelay(0);
                model.setInterpolationDuration(4);
                model.setTransformation(fieldPose(moving));
                displayUpdates++;
            }
            fields.put(key,new Field(model,anchor,tick));
        } catch (RuntimeException failure) {
            disableDisplays(failure);
        }
    }

    private Transformation fieldPose(boolean moving) {
        float angle = (float)(tick % 160) * (float)Math.PI / 80;
        float size = moving ? .55f : .95f + .16f * (float)Math.sin(3 * angle);
        return new Transformation(new Vector3f(),new Quaternionf().rotateY(angle).rotateZ(moving ? angle : .22f),
                new Vector3f(size),new Quaternionf());
    }

    private void endField(FieldKey key) {
        var previous = fields.remove(key);
        if (previous != null) previous.model.remove();
    }

    private record Aura(long second, long remaining) {}
    private final Map<UUID, Map<String,Aura>> auras = new HashMap<>();

    void aura(Player owner, String id, long second, long remaining) {
        var states = auras.get(owner.getUniqueId());
        var previous = states == null ? null : states.get(id);
        if (previous == null && remaining <= 0 || previous != null
                && previous.second == second && previous.remaining == remaining) return;
        if (remaining <= 0) {
            states.remove(id);
            if (states.isEmpty()) auras.remove(owner.getUniqueId());
        } else {
            if (states == null) states = auras.computeIfAbsent(owner.getUniqueId(),unused -> new HashMap<>());
            states.put(id,new Aura(second,remaining));
        }
        if (!PlayerUi.auras(owner) || noVisual.contains(id)) return;
        boolean renewed = previous != null && remaining > previous.remaining;
        if (previous == null || renewed || remaining <= 1)
            ring(owner,owner.getLocation().add(0,.45,0),remaining == 0 ? .25 : .58,
                    remaining == 0 ? Particle.ASH : Particle.ENCHANTED_HIT,remaining <= 1);
        else if (second % 2 == 0)
            spark(owner,owner.getLocation().add(.45*Math.cos(second),.65,.45*Math.sin(second)),Particle.END_ROD);
    }

    void cancelAura(UUID owner,String id) {
        cancelWarning(owner,id,null);
        endField(new FieldKey(owner,id));
        var states = auras.get(owner);
        if (states == null) return;
        states.remove(id);
        if (states.isEmpty()) auras.remove(owner);
    }

    private static EffectGeometry geometry(String motif) {
        return switch (motif) {
            case "blade","rush" -> EffectGeometry.CRESCENT;
            case "bolt","fire" -> EffectGeometry.HELIX;
            case "ward" -> EffectGeometry.FRACTURE;
            case "impact" -> EffectGeometry.SHOCKWAVE;
            case "time" -> EffectGeometry.REWIND;
            case "void","blood" -> EffectGeometry.CONVERGE;
            case "frost" -> EffectGeometry.SHARDS;
            default -> EffectGeometry.ORBIT;
        };
    }

    private void path(Player viewer,Location center,EffectGeometry shape,double size,double t,Particle particle) {
        int count = PlayerUi.quality(viewer).samples;
        double yaw = Math.toRadians(-viewer.getLocation().getYaw());
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        for (int i = 0; i < count; i++) {
            var point = shape.sample(i,count,t);
            spark(viewer,center.clone().add(size*(point.x()*cos + point.z()*sin),size*point.y(),
                    size*(point.z()*cos - point.x()*sin)),particle);
        }
    }

    void impact(Player viewer, String id, Location at) {
        String motif = motifs.getProperty(id,id);
        Particle particle = switch (motif) {
            case "fire" -> Particle.FLAME;
            case "blade","rush","impact" -> Particle.CRIT;
            case "bolt","time" -> Particle.ELECTRIC_SPARK;
            default -> Particle.END_ROD;
        };
        path(viewer,at,geometry(motif),.8,.8,particle);
    }

    void link(Player viewer, Location start, Location end, Particle particle) {
        if (!start.getWorld().equals(end.getWorld())) return;
        var delta = end.toVector().subtract(start.toVector());
        for (int i = 1; i <= 5; i++)
            spark(viewer,start.clone().add(delta.clone().multiply(i / 6d)),particle);
    }

    void spark(Player viewer, Location at, Particle particle) {
        if (!viewer.getWorld().equals(at.getWorld()) || !PlayerUi.enabled(viewer,PlayerUi.Option.PARTICLES)) return;
        Location eye = viewer.getLocation();
        double distance = eye.distanceSquared(at);
        if (distance > 32*32) return;
        if (distance > 8*8 && eye.getDirection().dot(at.toVector().subtract(eye.toVector())) < 0) return;
        int used = particleUnits.getOrDefault(viewer.getUniqueId(),0);
        int budget = PlayerUi.quality(viewer).samples * 2;
        if (PlayerUi.reducedFlash(viewer) || Bukkit.getAverageTickTime() > 45) budget = Math.min(budget,4);
        if (distance > 16*16) budget = Math.max(2,budget/3);
        if (used >= budget) return;
        particleUnits.put(viewer.getUniqueId(),used + 1);
        viewer.spawnParticle(particle,at,1,0,0,0,0);
        privateParticles++;
    }

    List<String> previewKeys() {
        var entries = new ArrayList<String>();
        motifs.stringPropertyNames().stream().sorted().map(id -> "augment/" + id).forEach(entries::add);
        new TreeSet<>(MOTIONS.keySet()).forEach(id -> entries.add("model/" + id));
        entries.addAll(List.of("model/supply","model/premium","model/helix","model/fracture","model/crescent"));
        new TreeSet<>(signatures.values().stream().map(s -> "sig_" + s.shape()).toList()).forEach(id -> entries.add("model/" + id));
        new TreeSet<>(GROWTH.keySet()).forEach(id -> entries.add("growth/" + id));
        entries.addAll(List.of("rarity/silver","rarity/gold","rarity/prismatic","sound/cast","sound/evolve","sound/supply","sound/focus","sound/stone_break","sound/rewind",
                "sound/ignite","sound/detonate","sound/rift","sound/seal","sound/sweep","sound/shield_form",
                "sound/shield_break","sound/evolution","sound/synergy","sound/prism"));
        for (Screen screen : Screen.values()) entries.add("screen/" + screen.name().toLowerCase(Locale.ROOT));
        return List.copyOf(entries);
    }

    boolean canPreview(Player player) { return playable.test(player); }

    void preview(Player p, String key, boolean simple) {
        String[] parts = key.split("/",2);
        String id = parts[1];
        switch (parts[0]) {
            case "augment" -> {
                if (!simple && signatures.containsKey(id)) signatureAt(p,id,near(p),true);
                else showProc(p,id,motifs.getProperty(id),simple);
            }
            case "growth" -> {
                var motif = SIGNATURES.getOrDefault(id,motifs.getProperty(id,"growth"));
                if (simple || !custom(p)) showProc(p,id,motif,true);
                else { growth(p,id,true); sound(p,"evolve",Sound.ENTITY_PLAYER_LEVELUP); }
            }
            case "model" -> {
                if (simple || !custom(p)) showProc(p,id,id,true);
                else render(p,near(p),id,MOTIONS.getOrDefault(id,ARRIVE));
            }
            case "sound" -> sound(p,id,cueFallback(id));
        }
    }

    void growth(Player player, String id, boolean visible) {
        if (!visible) return;
        if (signatures.containsKey(id)) {
            signatureAt(player,id,near(player),false);
            return;
        }
        if (!custom(player)) return;
        String motif = SIGNATURES.getOrDefault(id,motifs.getProperty(id,"growth"));
        render(player,near(player),motif,GROWTH.getOrDefault(id,PULSE));
        if (PlayerUi.quality(player).ordinal() >= PlayerUi.Quality.HIGH.ordinal())
            path(player,near(player),geometry(motif),.7,.6,Particle.END_ROD);
    }

    void supply(Location at, boolean premium) {
        for (Player player : at.getNearbyPlayers(32)) {
            if (custom(player) && playable.test(player)) {
                if (PlayerUi.enabled(player,PlayerUi.Option.SOUND)) player.playSound(at,"augmentwild:supply",.65f,1f);
                render(player,supplyCenter(at,1.75),premium ? "premium" : "supply",ARRIVE);
            } else if (playable.test(player) && PlayerUi.enabled(player,PlayerUi.Option.SOUND)) {
                player.playSound(at,Sound.BLOCK_CHEST_OPEN,1,.8f);
            }
        }
    }

    void supplyMarker(Location at, boolean premium) {
        if (pack == null || !displaysAvailable) return;
        var eligible = new HashMap<UUID,Player>();
        for (Player p : at.getNearbyPlayers(24)) {
            UUID id = p.getUniqueId();
            if (custom(p) && playable.test(p) && PlayerUi.enabled(p,PlayerUi.Option.PARTICLES)) eligible.put(id,p);
        }
        if (eligible.isEmpty()) {
            removeSupply(at);
            return;
        }
        try {
            Marker marker = supply.get(at);
            if (marker == null || !marker.entity().isValid()) {
                removeSupply(at);
                if (displayCount() >= MAX_DISPLAYS) return;
                var center = supplyCenter(at,1.15);
                marker = new Marker(spawn(center,premium ? "premium" : "supply",
                        new Transformation(new Vector3f(),new Quaternionf(),new Vector3f(.7f),new Quaternionf())),new HashSet<>());
                supply.put(at,marker);
            }
            for (UUID previous : Set.copyOf(marker.viewers())) {
                if (eligible.containsKey(previous)) continue;
                Player p = plugin.getServer().getPlayer(previous);
                if (p != null) p.hideEntity(plugin,marker.entity());
                marker.viewers().remove(previous);
            }
            for (var entry : eligible.entrySet()) {
                if (marker.viewers().add(entry.getKey())) entry.getValue().showEntity(plugin,marker.entity());
            }
        } catch (RuntimeException failure) {
            disableDisplays(failure);
        }
    }

    private static Location supplyCenter(Location at, double height) {
        return new Location(at.getWorld(),at.getBlockX() + .5,at.getBlockY() + height,at.getBlockZ() + .5);
    }

    void removeSupply(Location at) {
        var marker = supply.remove(at);
        if (marker != null) marker.entity().remove();
    }

    void confirmedSweep(Player player,Location impact,String augment) {
        if (!quiet.contains(augment) && PlayerUi.quality(player).ordinal() >= PlayerUi.Quality.HIGH.ordinal())
            locationSound(player,impact,"sweep",Sound.ENTITY_PLAYER_ATTACK_SWEEP);
    }

    void sound(Player player, String key, Sound fallback) {
        locationSound(player,player.getLocation(),key,fallback);
    }

    void locationSound(Player player,Location at,String key,Sound fallback) {
        locationSound(player,at,key,org.bukkit.Registry.SOUNDS.getKeyOrThrow(fallback).asString());
    }

    void locationSound(Player player,Location at,String key,String fallback) {
        UUID owner = player.getUniqueId();
        int used = cueSounds.getOrDefault(owner,0);
        if (used >= 2 || !PlayerUi.enabled(player,PlayerUi.Option.SOUND)) return;
        cueSounds.put(owner,used + 1);
        boolean detailed = custom(player) && PlayerUi.quality(player) != PlayerUi.Quality.LOW;
        if (detailed) player.playSound(at,"augmentwild:" + key,.48f,1f);
        else player.playSound(at,fallback,.38f,1f);
    }

    private static Location near(Player player) {
        var at = player.getLocation();
        return at.add(at.getDirection().setY(0).multiply(1.35)).add(0,.8,0);
    }

    void combat(Player source, Player target, UUID origin, String id, CombatCinematics.Form form, Location point, double radius) {
        combat.offer(source,target,origin,id,form,point,radius,!noVisual.contains(id),!quiet.contains(id));
    }

    void cancelWarning(UUID owner,String id,UUID origin) { combat.cancelWarning(owner,id,origin); }

    private boolean roomForCombat() {
        if (!displaysAvailable) return false;
        if (displayCount() < MAX_DISPLAYS) return true;
        for (var animation : cues.values()) {
            if (animation.ornaments().isEmpty()) continue;
            animation.ornaments().removeLast().remove();
            return true;
        }
        var iterator = cues.values().iterator();
        if (!iterator.hasNext()) return false;
        iterator.next().remove();
        iterator.remove();
        return true;
    }

    private void render(Player viewer, Location at, String motif, Motion motion) {
        render(viewer,at,motif,motion,null);
    }

    private void render(Player viewer, Location at, String motif, Motion motion, ArtSignature signature) {
        UUID id = viewer.getUniqueId();
        if (pack == null || !custom(viewer) || !displaysAvailable || !playable.test(viewer)
                || !PlayerUi.enabled(viewer,PlayerUi.Option.PARTICLES)
                || !at.getWorld().equals(viewer.getWorld()) || !at.isChunkLoaded()) return;
        var key = new PrivateCue(id,motif);
        var previous = cues.remove(key);
        if (previous != null) previous.remove();
        if (displayCount() >= MAX_DISPLAYS) return;
        ItemDisplay display = null;
        List<ItemDisplay> ornaments = new ArrayList<>();
        try {
            display = spawn(at,motif,signature == null ? pose(at,motion,0) : signature.frames().getFirst().pose(at));
            var quality = PlayerUi.quality(viewer);
            int layers = quality == PlayerUi.Quality.ULTRA ? 2 : quality == PlayerUi.Quality.HIGH ? 1 : 0;
            if (layers > 0 && !PlayerUi.reducedFlash(viewer) && Bukkit.getAverageTickTime() < 45 && (motif.startsWith("proc/") || signature != null)) {
                String augment = signature == null ? motif.substring(5) : signature.shape();
                int occupied = 1 + displayCount();
                if (occupied + layers <= MAX_DISPLAYS) {
                    String family = motifs.getProperty(augment,"rune");
                    for (int i = 0; i < layers; i++) {
                        String outer = switch (family) {
                            case "blade","rush" -> "crescent";
                            case "ward","impact","frost" -> "fracture";
                            default -> "helix";
                        };
                        var satellite = spawn(at,i == 0 ? outer : "rune",orbitPose(motion,0,i));
                        ornaments.add(satellite);
                        viewer.showEntity(plugin,satellite);
                    }
                }
            }
            cues.put(key,new Animation(viewer,display,at,tick,motion,ornaments,signature));
            if (motif.startsWith("proc/")) nextCue.put(key,tick + 20);
            viewer.showEntity(plugin,display);
            peakDisplays = Math.max(peakDisplays,displayCount());
        } catch (RuntimeException failure) {
            if (display != null) display.remove();
            ornaments.forEach(ItemDisplay::remove);
            disableDisplays(failure);
        }
    }

    private ItemDisplay spawn(Location at, String motif, Transformation pose) {
        Location anchor = at.clone();
        anchor.setYaw(0);
        anchor.setPitch(0);
        var result = at.getWorld().spawn(anchor,ItemDisplay.class,entity -> {
            entity.setVisibleByDefault(false);
            entity.setPersistent(false);
            entity.setItemStack(model(new ItemStack(Material.AMETHYST_SHARD),motif.startsWith("proc/") ? motif : "fx/" + motif));
            entity.setViewRange(1.0f);
            entity.setTransformation(pose);
        });
        displaySpawns++;
        return result;
    }

    private static Transformation pose(Location at, Motion motion, int frame) {
        float t = frame / 2f;
        var direction = at.getDirection().setY(0);
        return new Transformation(new Vector3f((float)direction.getX()*motion.travel()*t,motion.rise()*t,
                (float)direction.getZ()*motion.travel()*t),
                new Quaternionf().rotationY((float)Math.toRadians(-at.getYaw()) + motion.spin()*t),
                new Vector3f(motion.scales()[frame], motion.scales()[frame] * (motion.scales() == SWEEP && frame == 1 ? .55f : 1f), motion.scales()[frame]), new Quaternionf());
    }

    private static Transformation orbitPose(Motion motion, float t, int layer) {
        float phase = (layer == 0 ? 1 : -1) * (t * 4.2f + motion.spin()) + layer * 2;
        float envelope = (float)Math.sin(Math.PI * t);
        float radius = .13f + envelope * (motion.scales() == SWEEP ? .95f : .52f);
        return new Transformation(new Vector3f(radius * (float)Math.cos(phase),
                .22f * layer + envelope * motion.rise(), radius * (float)Math.sin(phase)),
                new Quaternionf().rotateY(-phase).rotateX(envelope * .8f),
                new Vector3f(.08f + envelope * .45f),new Quaternionf());
    }

    private int displayCount() {
        int count = supply.size() + fields.size() + combat.size();
        for (var animation : cues.values()) count += 1 + animation.ornaments().size();
        return count;
    }

    String metrics() {
        return "display_spawns=" + displaySpawns + " display_transforms=" + displayUpdates
                + " peak_displays=" + peakDisplays + " active_displays=" + displayCount()
                + " private_particle_sends=" + privateParticles + " " + combat.metrics();
    }

    void tick() {
        fields.entrySet().removeIf(entry -> {
            if (entry.getValue().lastSeen == tick) return false;
            entry.getValue().model.remove();
            return true;
        });
        tick++;
        particleUnits.clear();
        cueSounds.clear();
        nextCue.values().removeIf(expiry -> expiry <= tick);
        try {
            previews.values().removeIf(preview -> !advancePreview(preview));
            combat.tick();
            peakDisplays = Math.max(peakDisplays,displayCount());
            cues.entrySet().removeIf(entry -> {
                var animation = entry.getValue();
                long age = tick - animation.began();
                var entity = animation.entity();
                Player player = animation.viewer();
                var signature = animation.signature();
                int duration = signature == null ? animation.motion().attack() + animation.motion().release() : signature.duration();
                if (age >= duration + 1 || !entity.isValid() || !player.isOnline() || !playable.test(player) || !custom(player)
                        || !player.getWorld().equals(animation.at().getWorld())
                        || !PlayerUi.enabled(player,PlayerUi.Option.PARTICLES)) {
                    animation.remove();
                    return true;
                }
                if (!animation.ornaments().isEmpty() && (age & 1) == 1) {
                    float t = Math.min(1f,(age + 1f) / duration);
                    for (int i = 0; i < animation.ornaments().size(); i++) {
                        var layer = animation.ornaments().get(i);
                        layer.setInterpolationDuration(2);
                        layer.setInterpolationDelay(0);
                        displayUpdates++;
                        if (signature != null && signature.shape().equals("time_rewind")) {
                            float scale = .2f + .72f * (float)Math.sin(Math.PI*t);
                            layer.setTransformation(new Transformation(new Vector3f(0,.3f,0),
                                    new Quaternionf().rotateY(t * (i == 0 ? 5 : -5)),
                                    new Vector3f(scale,.35f,scale),new Quaternionf()));
                        } else layer.setTransformation(orbitPose(animation.motion(),t,i));
                    }
                }
                if (signature != null) {
                    var frames = signature.frames();
                    for (int i = 0; i < frames.size() - 1; i++) {
                        var start = frames.get(i);
                        if (age != start.tick() + 1) continue;
                        var end = frames.get(i + 1);
                        entity.setInterpolationDuration(end.tick() - start.tick());
                        entity.setInterpolationDelay(0);
                        var pose = end.pose(animation.at());
                        entity.setTransformation(pose);
                        displayUpdates++;
                        if (i == 1 || i == frames.size() - 2) {
                            var shift = start.pose(animation.at()).getTranslation();
                            spark(player,animation.at().clone().add(shift.x,shift.y,shift.z),signatureParticle(signature.shape()));
                        }
                        break;
                    }
                } else if (age == 1 || age == animation.motion().attack() + 1) {
                    entity.setInterpolationDuration(age == 1 ? animation.motion().attack() : animation.motion().release());
                    entity.setInterpolationDelay(0);
                    displayUpdates++;
                    entity.setTransformation(pose(animation.at(),animation.motion(),age == 1 ? 1 : 2));
                }
                return false;
            });
        } catch (RuntimeException failure) {
            disableDisplays(failure);
        }
    }

    private void disableDisplays(RuntimeException failure) {
        displaysAvailable = false;
        clear();
        plugin.getLogger().log(java.util.logging.Level.WARNING,"AugmentWild artwork displays disabled",failure);
    }

    private record Preview(Player player, String demo, java.util.function.BooleanSupplier allowed, Location at, long began) {}
    private final Map<UUID,Preview> previews = new HashMap<>();

    void demo(Player player,String demo,java.util.function.BooleanSupplier allowed) {
        var location = near(player);
        previews.put(player.getUniqueId(),new Preview(player,demo,allowed,location,tick));
    }

    private boolean advancePreview(Preview preview) {
        Player p = preview.player;
        if (!preview.allowed.getAsBoolean() || !p.isOnline() || !p.getWorld().equals(preview.at.getWorld())) return false;
        long age = tick - preview.began;
        switch (preview.demo) {
            case "compare" -> {
                if (age == 1) render(p,preview.at,"proc/sword_wind",MOTIONS.get("blade"));
                if (age == 21) combat(p,null,null,"lab",CombatCinematics.Form.ARC,preview.at,0);
            }
            case "simultaneous" -> {
                if (age == 1) {
                    render(p,preview.at,"proc/steel_heart",MOTIONS.get("ward"));
                    render(p,preview.at,"proc/sword_wind",MOTIONS.get("blade"));
                    combat(p,null,null,"lab",CombatCinematics.Form.GUARD,preview.at,0);
                    combat(p,null,null,"lab",CombatCinematics.Form.BLAST,preview.at,2);
                }
            }
            case "lifetime" -> {
                if (age % 8 == 1 && age < 33) combat(p,null,null,"lab",CombatCinematics.Form.DANGER,preview.at,2);
                if (age == 33) {
                    cancelWarning(p.getUniqueId(),"lab",null);
                    combat(p,null,null,"lab",CombatCinematics.Form.VOID,preview.at,0);
                }
            }
            case "repeat" -> {
                if (age == 1 || age == 13 || age == 25) combat(p,null,null,"lab",CombatCinematics.Form.ARC,preview.at,0);
            }
            default -> { return false; }
        }
        return age < 36;
    }

    void stopPreview(Player player) {
        previews.remove(player.getUniqueId());
        cancelWarning(player.getUniqueId(),"lab",null);
        removeCue(player.getUniqueId());
    }

    private void removeCue(UUID id) {
        cues.entrySet().removeIf(entry -> {
            if (!entry.getKey().owner.equals(id)) return false;
            entry.getValue().remove();
            return true;
        });
    }

    void forget(UUID id) {
        combat.forget(id);
        previews.remove(id);
        auras.remove(id);
        for (var key : Set.copyOf(fields.keySet())) if (key.owner.equals(id)) endField(key);
        removeCue(id);
        nextCue.keySet().removeIf(key -> key.owner.equals(id));
        if (supply.isEmpty()) return;
        Player viewer = plugin.getServer().getPlayer(id);
        for (Marker marker : supply.values()) {
            if (marker.viewers().remove(id) && viewer != null) viewer.hideEntity(plugin,marker.entity());
        }
    }

    void clear() {
        combat.clear();
        combat.resetMetrics();
        previews.clear();
        cues.values().forEach(Animation::remove);
        for (var field : fields.values()) field.model.remove();
        fields.clear();
        cues.clear();
        auras.clear();
        particleUnits.clear();
        cueSounds.clear();
        nextCue.clear();
        supply.values().forEach(marker -> marker.entity().remove());
        supply.clear();
        displaySpawns = displayUpdates = privateParticles = 0;
        peakDisplays = 0;
    }
}
