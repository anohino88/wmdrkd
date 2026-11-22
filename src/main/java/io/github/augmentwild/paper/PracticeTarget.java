package io.github.augmentwild.paper;

import java.util.Locale;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Husk;
import org.bukkit.entity.Player;

/** One nonpersistent hostile target, owned exclusively by an isolated solo lab. */
final class PracticeTarget {
    private Husk body;
    private Location anchor;
    private int ticks;
    private boolean moving;
    private double totalLoss;
    private double startingPoints;
    private int firstHit;

    String create(Player owner, String mode, Predicate<Location> inside) {
        var point = owner.getLocation().add(owner.getLocation().getDirection().setY(0).multiply(3));
        var spawn = SafeArenaSurface.at(owner.getWorld(),point.getBlockX(),point.getBlockZ());
        if (spawn == null || !inside.test(spawn)) return "가까운 평지에서 다시 시도하세요.";
        double health = mode.equals("high") ? 200 : 40;
        double shield = mode.equals("shield") ? 16 : 0;
        var next = owner.getWorld().spawn(spawn,Husk.class,entity -> {
            entity.setPersistent(false);
            entity.setAI(false);
            entity.setSilent(true);
            entity.setCanPickupItems(false);
            entity.setShouldBurnInDay(false);
            entity.setAdult();
            entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
            entity.setHealth(health);
            entity.getAttribute(Attribute.MAX_ABSORPTION).setBaseValue(shield);
            entity.setAbsorptionAmount(shield);
            entity.setCustomName("AugmentWild 연습 표적");
        });
        clear();
        body = next;
        anchor = spawn;
        moving = mode.equals("moving");
        startingPoints = health + shield;
        ticks = 0;
        totalLoss = 0;
        firstHit = -1;
        return "연습 표적 설치 · " + mode;
    }

    boolean owns(org.bukkit.entity.Entity entity) { return body != null && body.equals(entity); }

    void tick(Predicate<Location> inside, boolean paused) {
        if (body == null) return;
        if (!body.isValid() || !inside.test(body.getLocation())) { clear(); return; }
        if (paused) return;
        ticks++;
        double points = body.getHealth() + body.getAbsorptionAmount();
        if (points < startingPoints) {
            if (firstHit < 0) firstHit = ticks;
            totalLoss += startingPoints - points;
        }
        if (ticks % 20 == 0) body.setHealth(body.getAttribute(Attribute.MAX_HEALTH).getValue());
        startingPoints = body.getHealth() + body.getAbsorptionAmount();
        if (moving && ticks % 5 == 0) {
            var next = anchor.clone().add(2 * Math.sin(ticks * .04),0,0);
            if (inside.test(next) && SafeArenaSurface.valid(next)) body.teleport(next);
        }
    }

    void died() {
        if (firstHit < 0) firstHit = ticks;
        totalLoss += startingPoints;
        body = null;
    }

    String report() {
        return String.format(Locale.ROOT,"표적 체력·흡수 순손실 %.1f HP · 첫 피격 이후 %.1f HP/경기초",
                totalLoss,firstHit < 0 ? 0 : totalLoss * 20 / Math.max(1,ticks - firstHit + 1));
    }

    void clear() {
        if (body != null) body.remove();
        body = null;
        anchor = null;
        totalLoss = 0;
        firstHit = -1;
    }
}
