package io.github.augmentwild.paper;

import io.github.augmentwild.augment.AugmentDefinition;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Optional client-side assets backed by bounded, private, non-persistent Paper displays. */
public final class PaperArt implements Listener {
    private static final float[] GROW = {.3f, 1.45f, .25f};
    private static final float[] SWEEP = {.35f, 1.7f, .5f};
    private static final float[] HOLD = {.45f, 1.2f, .85f};
    private static final int STEP_TICKS = 6;
    private static final int MAX_DISPLAYS = 24;
    private final JavaPlugin plugin;
    private final Predicate<Player> playable;
    private final Map<UUID,PlayerResourcePackStatusEvent.Status> reports = new HashMap<>();
    private final Map<UUID,Long> lastOffer = new HashMap<>();
    private final Set<UUID> loaded = new HashSet<>();
    private final Map<UUID, Animation> cues = new HashMap<>();
    private final Map<Location, Marker> supply = new HashMap<>();
    private final Map<UUID, Long> nextCue = new HashMap<>();
    private final Set<String> quiet = new HashSet<>(), noVisual = new HashSet<>();
    private final Map<String,String> labels = new HashMap<>();
    private final Properties screens = new Properties();
    private final Properties motifs = new Properties();
    private final ResourcePackInfo pack;
    private long tick;
    private boolean displaysAvailable = true;

    private record Animation(Player viewer, ItemDisplay entity, Location at, long began, Motion motion) {}
    private record Marker(ItemDisplay entity, Set<UUID> viewers) {}
    private record Motion(float[] scales, float rise, float spin, float travel) {}
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
    Component title(Player p, Screen screen, String label) {
        if (!loaded.contains(p.getUniqueId())) return Component.text(label);
        String panel = "\ue000" + (char)(0xe010 + screen.ordinal()) + "\ue001";
        return Component.text(panel,NamedTextColor.WHITE).font(Key.key("augmentwild:interface"))
                .append(Component.text(label,NamedTextColor.WHITE).font(Key.key("minecraft:default")));
    }

    void button(Player p, Inventory inv, int slot, String key) {
        var item = inv.getItem(slot);
        if (item != null) inv.setItem(slot, icon(p,item,"gui/" + key));
    }

    String packStatus(Player p) {
        if (pack == null) return "팩 배포 미설정 · 바닐라 표시";
        if (loaded.contains(p.getUniqueId())) return "리소스팩 적용 · " + pack.hash().substring(0,8);
        var status = reports.get(p.getUniqueId());
        if (status == null) return "클라이언트 응답 대기";
        return switch (status) {
            case ACCEPTED, DOWNLOADED -> "팩 적용 중";
            case DECLINED -> "팩 거절 · 바닐라 표시";
            default -> "팩 미적용 · " + status.name() + " · 바닐라 표시";
        };
    }

    public PaperArt(JavaPlugin plugin, Predicate<Player> playable) {
        this.plugin = plugin;
        this.playable = playable;
        pack = settings(plugin);
        try (var input = plugin.getResource("art-motifs.properties")) {
            if (input != null) motifs.load(input);
            try (var layouts = plugin.getResource("art-screen-samples.properties")) {
                if (layouts != null) screens.load(layouts);
            }
        } catch (IOException failure) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,"Unable to read augment artwork mapping",failure);
        }
    }

    private static ResourcePackInfo settings(JavaPlugin plugin) {
        String url = plugin.getConfig().getString("art.url","");
        if (url.isBlank()) return null;
        try {
            var uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
                throw new IllegalArgumentException("art.url must be a direct HTTPS ZIP URL");
            String hash = plugin.getConfig().getString("art.sha1","");
            byte[] sha1 = HexFormat.of().parseHex(hash);
            if (sha1.length != 20) throw new IllegalArgumentException("art.sha1 must contain 40 hexadecimal digits");
            hash = hash.toLowerCase(Locale.ROOT);
            UUID id = UUID.nameUUIDFromBytes(("augmentwild-art:" + hash).getBytes(StandardCharsets.UTF_8));
            return ResourcePackInfo.resourcePackInfo(id,uri,hash);
        } catch (IllegalArgumentException failure) {
            plugin.getLogger().warning(failure.getMessage());
            return null;
        }
    }

    @EventHandler public void join(PlayerJoinEvent event) { offer(event.getPlayer()); }

    String retry(Player player) {
        if (pack == null || loaded.contains(player.getUniqueId())) return packStatus(player);
        Long last = lastOffer.get(player.getUniqueId());
        if (last != null && System.nanoTime() - last < 10_000_000_000L) return "최근 요청을 보냈습니다. 잠시 후 다시 시도하세요.";
        offer(player);
        return "리소스팩을 다시 요청했습니다.";
    }

    void offer(Player player) {
        if (pack == null) return;
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
            loaded.remove(player);
            forget(player);
        }
    }

    public ItemStack icon(Player viewer, ItemStack item, String key) {
        if (!loaded.contains(viewer.getUniqueId())) return item;
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
        if (tick < nextCue.getOrDefault(owner,0L)) return;
        Player player = plugin.getServer().getPlayer(owner);
        if (player != null) cue(player,id);
    }

    void cue(Player player, String id) {
        String motif = motifs.getProperty(id);
        if (motif == null || !playable.test(player) || tick < nextCue.getOrDefault(player.getUniqueId(),0L)) return;
        nextCue.put(player.getUniqueId(),tick + 12);
        showProc(player,id,motif,false);
    }

    private void showProc(Player player, String id, String motif, boolean simple) {
        var at = near(player);
        if (!noVisual.contains(id) && !simple && loaded.contains(player.getUniqueId()) && displaysAvailable) {
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
        }
        if (quiet.contains(id) || !PlayerUi.enabled(player,PlayerUi.Option.SOUND)) return;
        float pitch = switch (motif) {
            case "impact","blood","void" -> .78f;
            case "time","frost","bolt" -> 1.32f;
            case "growth","ward" -> .95f;
            default -> 1.12f;
        };
        if (!simple && loaded.contains(player.getUniqueId())) player.playSound(at,"augmentwild:cast",.36f,pitch);
        else player.playSound(at,Sound.BLOCK_AMETHYST_BLOCK_CHIME,.32f,pitch);
    }

    void choice(Player p, String id, String rarity) {
        if (!playable.test(p) || !loaded.contains(p.getUniqueId())) return;
        var motion = switch (rarity) { case "PRISMATIC" -> GROWTH.get("magic_missile"); case "GOLD" -> MOTIONS.get("ward"); default -> PULSE; };
        render(p,near(p),"proc/" + id,motion);
    }

    void cast(Player player, String id) {
        String motif = motifs.getProperty(id);
        if (motif != null) {
            nextCue.put(player.getUniqueId(),tick + 12);
            showProc(player,id,motif,false);
        }
    }

    List<String> previewKeys() {
        var entries = new ArrayList<String>();
        motifs.stringPropertyNames().stream().sorted().map(id -> "augment/" + id).forEach(entries::add);
        new TreeSet<>(MOTIONS.keySet()).forEach(id -> entries.add("model/" + id));
        entries.addAll(List.of("model/supply","model/premium"));
        new TreeSet<>(GROWTH.keySet()).forEach(id -> entries.add("growth/" + id));
        entries.addAll(List.of("rarity/silver","rarity/gold","rarity/prismatic","sound/cast","sound/evolve","sound/supply"));
        for (Screen screen : Screen.values()) entries.add("screen/" + screen.name().toLowerCase(Locale.ROOT));
        return List.copyOf(entries);
    }

    void preview(Player p, String key, boolean simple) {
        String[] parts = key.split("/",2);
        String id = parts[1];
        switch (parts[0]) {
            case "augment" -> showProc(p,id,motifs.getProperty(id),simple);
            case "growth" -> {
                var motif = SIGNATURES.getOrDefault(id,motifs.getProperty(id,"growth"));
                if (simple || !loaded.contains(p.getUniqueId())) showProc(p,id,motif,true);
                else { growth(p,id,true); sound(p,"evolve",Sound.ENTITY_PLAYER_LEVELUP); }
            }
            case "model" -> {
                if (simple || !loaded.contains(p.getUniqueId())) showProc(p,id,id,true);
                else render(p,near(p),id,MOTIONS.getOrDefault(id,ARRIVE));
            }
            case "sound" -> sound(p,id,Sound.BLOCK_NOTE_BLOCK_CHIME);
        }
    }

    void growth(Player player, String id, boolean visible) {
        if (!visible || pack == null || !loaded.contains(player.getUniqueId())) return;
        String motif = SIGNATURES.getOrDefault(id,motifs.getProperty(id,"growth"));
        render(player,near(player),motif,GROWTH.getOrDefault(id,PULSE));
    }

    void supply(Location at, boolean premium) {
        for (Player player : at.getNearbyPlayers(32)) {
            if (loaded.contains(player.getUniqueId()) && playable.test(player)) {
                if (PlayerUi.enabled(player,PlayerUi.Option.SOUND)) player.playSound(at,"augmentwild:supply",.65f,1f);
                render(player,supplyCenter(at,1.75),premium ? "premium" : "supply",ARRIVE);
            } else if (PlayerUi.enabled(player,PlayerUi.Option.SOUND)) {
                player.playSound(at,Sound.BLOCK_CHEST_OPEN,1,.8f);
            }
        }
    }

    void supplyMarker(Location at, boolean premium) {
        if (pack == null || !displaysAvailable) return;
        var eligible = new HashMap<UUID,Player>();
        for (Player p : at.getNearbyPlayers(24)) {
            UUID id = p.getUniqueId();
            if (loaded.contains(id) && playable.test(p) && PlayerUi.enabled(p,PlayerUi.Option.PARTICLES)) eligible.put(id,p);
        }
        if (eligible.isEmpty()) {
            removeSupply(at);
            return;
        }
        try {
            Marker marker = supply.get(at);
            if (marker == null || !marker.entity().isValid()) {
                removeSupply(at);
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

    void sound(Player player, String key, Sound fallback) {
        if (!PlayerUi.enabled(player,PlayerUi.Option.SOUND)) return;
        var at = player.getLocation();
        if (loaded.contains(player.getUniqueId())) player.playSound(at,"augmentwild:" + key,.48f,1f);
        else player.playSound(at,fallback,.48f,1f);
    }

    private static Location near(Player player) {
        var at = player.getLocation();
        return at.add(at.getDirection().setY(0).multiply(1.35)).add(0,.8,0);
    }

    private void render(Player viewer, Location at, String motif, Motion motion) {
        UUID id = viewer.getUniqueId();
        if (pack == null || !loaded.contains(id) || !displaysAvailable || !playable.test(viewer)
                || !PlayerUi.enabled(viewer,PlayerUi.Option.PARTICLES)
                || !at.getWorld().equals(viewer.getWorld()) || !at.isChunkLoaded()) return;
        if (!cues.containsKey(id) && cues.size() >= MAX_DISPLAYS) return;
        removeCue(id);
        ItemDisplay display = null;
        try {
            display = spawn(at,motif,pose(at,motion,0));
            cues.put(id,new Animation(viewer,display,at,tick,motion));
            nextCue.put(id,tick + 20);
            viewer.showEntity(plugin,display);
        } catch (RuntimeException failure) {
            if (display != null) display.remove();
            disableDisplays(failure);
        }
    }

    private static ItemDisplay spawn(Location at, String motif, Transformation pose) {
        return at.getWorld().spawn(at,ItemDisplay.class,entity -> {
            entity.setVisibleByDefault(false);
            entity.setPersistent(false);
            entity.setItemStack(model(new ItemStack(Material.AMETHYST_SHARD),motif.startsWith("proc/") ? motif : "fx/" + motif));
            entity.setViewRange(1.0f);
            entity.setTransformation(pose);
        });
    }

    private static Transformation pose(Location at, Motion motion, int frame) {
        float t = frame / 2f;
        var direction = at.getDirection().setY(0);
        return new Transformation(new Vector3f((float)direction.getX()*motion.travel()*t,motion.rise()*t,
                (float)direction.getZ()*motion.travel()*t),
                new Quaternionf().rotationY((float)Math.toRadians(-at.getYaw()) + motion.spin()*t),
                new Vector3f(motion.scales()[frame]), new Quaternionf());
    }

    void tick() {
        tick++;
        if (cues.isEmpty()) return;
        try {
            cues.entrySet().removeIf(entry -> {
                var animation = entry.getValue();
                long age = tick - animation.began();
                var entity = animation.entity();
                Player player = animation.viewer();
                if (age >= STEP_TICKS * 2 + 1 || !entity.isValid() || !player.isOnline() || !playable.test(player)
                        || !player.getWorld().equals(animation.at().getWorld())
                        || !PlayerUi.enabled(player,PlayerUi.Option.PARTICLES)) {
                    entity.remove();
                    return true;
                }
                if (age == 1 || age == STEP_TICKS + 1) {
                    entity.setInterpolationDuration(STEP_TICKS);
                    entity.setInterpolationDelay(0);
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

    private void removeCue(UUID id) {
        var animation = cues.remove(id);
        if (animation != null) animation.entity().remove();
    }

    void forget(UUID id) {
        removeCue(id);
        nextCue.remove(id);
        if (supply.isEmpty()) return;
        Player viewer = plugin.getServer().getPlayer(id);
        for (Marker marker : supply.values()) {
            if (marker.viewers().remove(id) && viewer != null) viewer.hideEntity(plugin,marker.entity());
        }
    }

    void clear() {
        cues.values().forEach(animation -> animation.entity().remove());
        cues.clear();
        nextCue.clear();
        supply.values().forEach(marker -> marker.entity().remove());
        supply.clear();
    }
}
