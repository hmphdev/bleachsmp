package dev.bleachsmp.faction;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

public enum Faction {
    SOUL_REAPER("Soul Reaper", "Sealed Zanpakuto", Material.IRON_SWORD, NamedTextColor.WHITE,
            "A sealed soul-cutting blade."),
    HOLLOW("Hollow", "Hollow Claw", Material.STONE_SWORD, NamedTextColor.RED,
            "A crude weapon from your first hollow form."),
    QUINCY("Quincy", "Quincy Longbow", Material.BOW, NamedTextColor.AQUA,
            "A basic bow for a Quincy archer."),
    FULLBRINGER("Fullbringer", "Fullbring Focus", Material.AMETHYST_SHARD, NamedTextColor.LIGHT_PURPLE,
            "A personal object through which your power will grow.");

    private final String displayName;
    private final String starterName;
    private final Material starterMaterial;
    private final NamedTextColor color;
    private final String description;

    Faction(String displayName, String starterName, Material starterMaterial, NamedTextColor color,
            String description) {
        this.displayName = displayName;
        this.starterName = starterName;
        this.starterMaterial = starterMaterial;
        this.color = color;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String starterName() {
        return starterName;
    }

    public Material starterMaterial() {
        return starterMaterial;
    }

    public NamedTextColor color() {
        return color;
    }

    public String description() {
        return description;
    }
}
