package io.github.augmentwild.augment;

/** Bindings are independent of the fighter's per-augment cooldown ledger. */
public final class SkillSlots {
    public static final int COUNT = 3;
    private final String[] bindings = new String[COUNT];
    private int selected = 1;

    public int selected() { return selected; }
    public String current() { return at(selected); }
    public String at(int slot) { return bindings[slot - 1]; }

    public boolean select(int slot) {
        if (slot < 1 || slot > COUNT) return false;
        selected = slot;
        return true;
    }

    public boolean bind(int slot, String id) {
        if (slot < 1 || slot > COUNT || id == null) return false;
        remove(id);
        bindings[slot - 1] = id;
        return true;
    }

    public void remove(String id) {
        if (id == null) return;
        for (int i = 0; i < COUNT; i++) if (id.equals(bindings[i])) bindings[i] = null;
    }

    public void next() {
        for (int step = 1; step <= COUNT; step++) {
            int slot = (selected - 1 + step) % COUNT + 1;
            if (at(slot) != null) { selected = slot; return; }
        }
    }
}
