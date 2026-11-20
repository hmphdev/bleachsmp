package dev.bleachsmp.awakening;

import dev.bleachsmp.BleachSmpPlugin;
import dev.bleachsmp.faction.Faction;
import dev.bleachsmp.fx.Vfx;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * First-form releases for every faction: Soul Reaper Shikai, Hollow Resurrección, Quincy Schrift and
 * Awakened Fullbring. A release is a timed state with two techniques on the starter item:
 * right-click uses the main technique, sneak + right-click the secondary one.
 *
 * <p>Kits are built so players rarely share one: each playstyle has two form variants (picked per player
 * from their UUID) with different main techniques, and the secondary technique comes from the player's
 * secondary playstyle. With 12 techniques that gives over a hundred kits per faction, and each faction
 * styles every technique with its own colours, particles, sounds and on-hit effect.
 */
public final class ReleaseService implements Listener {
    /** Technique mechanics. Each playstyle owns two, one per form variant. */
    enum Mechanic {
        WAVE("a cutting wave that tears through everything in its path"),
        BLINK("vanish and strike from behind a target up to 12 blocks away, blinding it"),
        BEAM("a long-range blast that bursts on impact"),
        DASH("dash forward, cutting every enemy you pass"),
        SLAM("slam the ground and hurl nearby enemies skyward"),
        WARD("heal and shield yourself and teammates while driving enemies back"),
        FLURRY("lunge at a target and unleash a five-hit combo"),
        SCATTER("scatter into afterimages, hop around the battlefield, and blind nearby enemies"),
        RAIN("call bolts down on the area you aim at"),
        SKYFALL("leap high, then crash down in a shockwave"),
        PILLARS("raise a line of erupting pillars that launch enemies"),
        CHAINS("bind nearby enemies, dragging them in and rooting them");

        final String description;

        Mechanic(String description) {
            this.description = description;
        }

        static Mechanic of(Playstyle style, int variant) {
            return switch (style) {
                case VANGUARD -> variant == 0 ? WAVE : FLURRY;
                case MIRAGE -> variant == 0 ? BLINK : SCATTER;
                case MARKSMAN -> variant == 0 ? BEAM : RAIN;
                case WAYFARER -> variant == 0 ? DASH : SKYFALL;
                case ARTIFICER -> variant == 0 ? SLAM : PILLARS;
                case KEEPER -> variant == 0 ? WARD : CHAINS;
            };
        }
    }

    /** Faction-wide look, sound, passive and on-hit effect for released forms. */
    enum Theme {
        SOUL_REAPER(Particle.ELECTRIC_SPARK, PotionEffectType.SPEED, 0, Material.CALCITE, "Hits slow enemies."),
        HOLLOW(Particle.SOUL_FIRE_FLAME, PotionEffectType.RESISTANCE, 0, Material.BONE_BLOCK,
                "Hits wither enemies and heal you."),
        QUINCY(Particle.END_ROD, PotionEffectType.JUMP_BOOST, 1, Material.LIGHT_BLUE_STAINED_GLASS,
                "Hits mark enemies with Glowing."),
        FULLBRINGER(Particle.ENCHANT, PotionEffectType.HASTE, 0, Material.IRON_BLOCK, "Hits weaken enemies.");

        final Particle accent;
        final PotionEffectType passive;
        final int passiveAmplifier;
        final Material pillar;
        final String onHit;

        Theme(Particle accent, PotionEffectType passive, int passiveAmplifier, Material pillar, String onHit) {
            this.accent = accent;
            this.passive = passive;
            this.passiveAmplifier = passiveAmplifier;
            this.pillar = pillar;
            this.onHit = onHit;
        }

        static Theme of(Faction faction) {
            return valueOf(faction.name());
        }
    }

    /** One released form: its name, what the player calls out, and its colours. */
    record Form(String name, String callout, Color color, TextColor text, BossBar.Color bar) {
        Particle.DustOptions dust(float size) {
            return Vfx.dust(color, size);
        }
    }

    private static Form f(String name, String callout, int r, int g, int b, TextColor text, BossBar.Color bar) {
        return new Form(name, callout, Color.fromRGB(r, g, b), text, bar);
    }

    // ---------------------------------------------------------------- kit tables

    static Form form(Faction faction, Playstyle style, int variant) {
        return variant == 0 ? formA(faction, style) : formB(faction, style);
    }

    private static Form formA(Faction faction, Playstyle style) {
        return switch (faction) {
            case SOUL_REAPER -> switch (style) {
                case VANGUARD -> f("Akagetsu", "Howl, Akagetsu!", 220, 30, 45, NamedTextColor.RED, BossBar.Color.RED);
                case MIRAGE -> f("Kagerō", "Deceive, Kagerō!", 150, 90, 230, NamedTextColor.LIGHT_PURPLE, BossBar.Color.PURPLE);
                case MARKSMAN -> f("Ryūsei", "Pierce, Ryūsei!", 255, 205, 60, NamedTextColor.GOLD, BossBar.Color.YELLOW);
                case WAYFARER -> f("Hayate", "Soar, Hayate!", 120, 235, 200, NamedTextColor.AQUA, BossBar.Color.GREEN);
                case ARTIFICER -> f("Iwagane", "Crush, Iwagane!", 165, 120, 75, NamedTextColor.GOLD, BossBar.Color.WHITE);
                case KEEPER -> f("Mamorigami", "Shelter, Mamorigami!", 110, 190, 255, NamedTextColor.BLUE, BossBar.Color.BLUE);
            };
            case HOLLOW -> switch (style) {
                case VANGUARD -> f("Lobo Carmesí", "Desgarra, Lobo Carmesí!", 170, 15, 25, NamedTextColor.DARK_RED, BossBar.Color.RED);
                case MIRAGE -> f("Espejismo", "Engaña, Espejismo!", 205, 205, 215, NamedTextColor.GRAY, BossBar.Color.WHITE);
                case MARKSMAN -> f("Halcón Negro", "Dispara, Halcón Negro!", 120, 20, 150, NamedTextColor.DARK_PURPLE, BossBar.Color.PURPLE);
                case WAYFARER -> f("Viento Hueco", "Corre, Viento Hueco!", 220, 205, 160, NamedTextColor.YELLOW, BossBar.Color.YELLOW);
                case ARTIFICER -> f("Coloso", "Aplasta, Coloso!", 125, 90, 55, NamedTextColor.GOLD, BossBar.Color.WHITE);
                case KEEPER -> f("Caparazón", "Protege, Caparazón!", 60, 160, 115, NamedTextColor.DARK_GREEN, BossBar.Color.GREEN);
            };
            case QUINCY -> switch (style) {
                case VANGUARD -> f("The Assault", "Schrift A — The Assault!", 110, 170, 255, NamedTextColor.BLUE, BossBar.Color.BLUE);
                case MIRAGE -> f("The Illusion", "Schrift I — The Illusion!", 170, 140, 255, NamedTextColor.LIGHT_PURPLE, BossBar.Color.PURPLE);
                case MARKSMAN -> f("The Starshot", "Schrift S — The Starshot!", 200, 235, 255, NamedTextColor.AQUA, BossBar.Color.BLUE);
                case WAYFARER -> f("The Flight", "Schrift F — The Flight!", 150, 255, 235, NamedTextColor.AQUA, BossBar.Color.GREEN);
                case ARTIFICER -> f("The Bulwark", "Schrift B — The Bulwark!", 190, 200, 225, NamedTextColor.GRAY, BossBar.Color.WHITE);
                case KEEPER -> f("The Haven", "Schrift H — The Haven!", 255, 250, 190, NamedTextColor.YELLOW, BossBar.Color.YELLOW);
            };
            case FULLBRINGER -> switch (style) {
                case VANGUARD -> f("Crimson Knuckle", "Fullbring: Crimson Knuckle!", 255, 115, 40, NamedTextColor.GOLD, BossBar.Color.RED);
                case MIRAGE -> f("Mirror Coat", "Fullbring: Mirror Coat!", 230, 150, 255, NamedTextColor.LIGHT_PURPLE, BossBar.Color.PURPLE);
                case MARKSMAN -> f("Bullet Echo", "Fullbring: Bullet Echo!", 255, 220, 80, NamedTextColor.YELLOW, BossBar.Color.YELLOW);
                case WAYFARER -> f("Sky Runner", "Fullbring: Sky Runner!", 130, 215, 255, NamedTextColor.AQUA, BossBar.Color.BLUE);
                case ARTIFICER -> f("Iron Maker", "Fullbring: Iron Maker!", 175, 175, 190, NamedTextColor.GRAY, BossBar.Color.WHITE);
                case KEEPER -> f("Heart Lock", "Fullbring: Heart Lock!", 255, 140, 185, NamedTextColor.LIGHT_PURPLE, BossBar.Color.PINK);
            };
        };
    }

    private static Form formB(Faction faction, Playstyle style) {
        return switch (faction) {
            case SOUL_REAPER -> switch (style) {
                case VANGUARD -> f("Homuramaru", "Rage, Homuramaru!", 255, 110, 30, NamedTextColor.GOLD, BossBar.Color.RED);
                case MIRAGE -> f("Oboromaru", "Flicker, Oboromaru!", 90, 110, 200, NamedTextColor.DARK_AQUA, BossBar.Color.BLUE);
                case MARKSMAN -> f("Amagumo", "Rain, Amagumo!", 140, 160, 190, NamedTextColor.GRAY, BossBar.Color.WHITE);
                case WAYFARER -> f("Kumokiri", "Ascend, Kumokiri!", 235, 245, 255, NamedTextColor.WHITE, BossBar.Color.WHITE);
                case ARTIFICER -> f("Iwaoni", "Rise, Iwaoni!", 110, 130, 95, NamedTextColor.DARK_GREEN, BossBar.Color.GREEN);
                case KEEPER -> f("Kusarigami", "Bind, Kusarigami!", 200, 60, 120, NamedTextColor.LIGHT_PURPLE, BossBar.Color.PINK);
            };
            case HOLLOW -> switch (style) {
                case VANGUARD -> f("Tiburón", "Devora, Tiburón!", 40, 90, 160, NamedTextColor.DARK_BLUE, BossBar.Color.BLUE);
                case MIRAGE -> f("Sombra Pálida", "Desvanece, Sombra Pálida!", 160, 150, 180, NamedTextColor.GRAY, BossBar.Color.PURPLE);
                case MARKSMAN -> f("Escorpión", "Llueve, Escorpión!", 200, 90, 20, NamedTextColor.GOLD, BossBar.Color.YELLOW);
                case WAYFARER -> f("Águila Hueca", "Vuela, Águila Hueca!", 245, 240, 225, NamedTextColor.WHITE, BossBar.Color.WHITE);
                case ARTIFICER -> f("Osario", "Brota, Osario!", 235, 225, 195, NamedTextColor.YELLOW, BossBar.Color.WHITE);
                case KEEPER -> f("Araña Blanca", "Atrapa, Araña Blanca!", 215, 215, 230, NamedTextColor.WHITE, BossBar.Color.WHITE);
            };
            case QUINCY -> switch (style) {
                case VANGUARD -> f("The Rampage", "Schrift R — The Rampage!", 70, 120, 255, NamedTextColor.BLUE, BossBar.Color.BLUE);
                case MIRAGE -> f("The Mirror", "Schrift M — The Mirror!", 225, 235, 255, NamedTextColor.WHITE, BossBar.Color.WHITE);
                case MARKSMAN -> f("The Deluge", "Schrift D — The Deluge!", 90, 200, 255, NamedTextColor.AQUA, BossBar.Color.BLUE);
                case WAYFARER -> f("The Zenith", "Schrift Z — The Zenith!", 255, 240, 150, NamedTextColor.YELLOW, BossBar.Color.YELLOW);
                case ARTIFICER -> f("The Pillar", "Schrift P — The Pillar!", 160, 210, 255, NamedTextColor.AQUA, BossBar.Color.WHITE);
                case KEEPER -> f("The Tether", "Schrift T — The Tether!", 130, 255, 190, NamedTextColor.GREEN, BossBar.Color.GREEN);
            };
            case FULLBRINGER -> switch (style) {
                case VANGUARD -> f("Rewind Blade", "Fullbring: Rewind Blade!", 255, 70, 90, NamedTextColor.RED, BossBar.Color.RED);
                case MIRAGE -> f("Shuffle Deck", "Fullbring: Shuffle Deck!", 120, 255, 140, NamedTextColor.GREEN, BossBar.Color.GREEN);
                case MARKSMAN -> f("Echo Rain", "Fullbring: Echo Rain!", 255, 180, 60, NamedTextColor.GOLD, BossBar.Color.YELLOW);
                case WAYFARER -> f("Meteor Boots", "Fullbring: Meteor Boots!", 255, 90, 40, NamedTextColor.RED, BossBar.Color.RED);
                case ARTIFICER -> f("Forge Ground", "Fullbring: Forge Ground!", 205, 125, 70, NamedTextColor.GOLD, BossBar.Color.WHITE);
                case KEEPER -> f("Lifeline", "Fullbring: Lifeline!", 255, 120, 120, NamedTextColor.RED, BossBar.Color.PINK);
            };
        };
    }

    static String techniqueName(Faction faction, Mechanic mechanic) {
        return switch (faction) {
            case SOUL_REAPER -> switch (mechanic) {
                case WAVE -> "Crimson Fang";
                case BLINK -> "Haze Step";
                case BEAM -> "Falling Star";
                case DASH -> "Gale Passage";
                case SLAM -> "Mountain Breaker";
                case WARD -> "Shrine Ward";
                case FLURRY -> "Hundred Moon Flurry";
                case SCATTER -> "Mirror Haze";
                case RAIN -> "Heaven's Rain";
                case SKYFALL -> "Falling Moon";
                case PILLARS -> "Stone Fangs";
                case CHAINS -> "Binding Thread";
            };
            case HOLLOW -> switch (mechanic) {
                case WAVE -> "Garra Roja";
                case BLINK -> "Sonido Fantasma";
                case BEAM -> "Cero Oscuro";
                case DASH -> "Embestida";
                case SLAM -> "Terremoto";
                case WARD -> "Coraza Viva";
                case FLURRY -> "Frenesí";
                case SCATTER -> "Eco Vacío";
                case RAIN -> "Lluvia de Cero";
                case SKYFALL -> "Caída del Rey";
                case PILLARS -> "Colmillos de Hueso";
                case CHAINS -> "Cadenas del Hambre";
            };
            case QUINCY -> switch (mechanic) {
                case WAVE -> "Heiliger Sturm";
                case BLINK -> "Schattenschritt";
                case BEAM -> "Sternregen";
                case DASH -> "Lichtsprung";
                case SLAM -> "Erdbrecher";
                case WARD -> "Lichtschild";
                case FLURRY -> "Klingenwirbel";
                case SCATTER -> "Spiegelbruch";
                case RAIN -> "Pfeilregen";
                case SKYFALL -> "Sternenfall";
                case PILLARS -> "Lichtsäulen";
                case CHAINS -> "Bindungsfaden";
            };
            case FULLBRINGER -> switch (mechanic) {
                case WAVE -> "Knuckle Breaker";
                case BLINK -> "Reflection Swap";
                case BEAM -> "Echo Shot";
                case DASH -> "Ground Skip";
                case SLAM -> "Forge Quake";
                case WARD -> "Locket Ward";
                case FLURRY -> "Rapid Rewind";
                case SCATTER -> "Shuffle Step";
                case RAIN -> "Echo Barrage";
                case SKYFALL -> "Meteor Drop";
                case PILLARS -> "Forge Spikes";
                case CHAINS -> "Tether Line";
            };
        };
    }

    // ---------------------------------------------------------------- advanced forms

    /** Advanced form names, indexed [variant][playstyle ordinal]. */
    private static String[][] advancedNames(Faction faction) {
        return switch (faction) {
            case SOUL_REAPER -> new String[][] {
                {"Akagetsu Tenrō", "Kagerō Mugenkyō", "Ryūsei Gingaō", "Hayate Tenkū", "Iwagane Daichiō", "Mamorigami Kekkaiden"},
                {"Homuramaru Gōen", "Oboromaru Yumeutsutsu", "Amagumo Raiu", "Kumokiri Shippū", "Iwaoni Senzan", "Kusarigami Shinbaku"}};
            case HOLLOW -> new String[][] {
                {"Lobo Carmesí Rey", "Espejismo Eterno", "Halcón Negro Abismal", "Tormenta Hueca", "Coloso Titán", "Caparazón Sagrado"},
                {"Tiburón Abisal", "Sombra Infinita", "Escorpión Rey", "Águila Tempestad", "Osario Imperial", "Araña Reina"}};
            case QUINCY -> new String[][] {
                {"Sturmengel", "Spiegelengel", "Sternengel", "Himmelsflügel", "Bollwerkengel", "Heiligenschein"},
                {"Zornflügel", "Trugbild", "Sintflut", "Zenitflug", "Säulenthron", "Bandengel"}};
            case FULLBRINGER -> new String[][] {
                {"Crimson Knuckle Overdrive", "Mirror Coat Infinite", "Bullet Echo Arsenal", "Sky Runner Ascension",
                        "Iron Maker Foundry", "Heart Lock Sanctuary"},
                {"Rewind Blade Zero", "Shuffle Deck Joker", "Echo Rain Storm", "Meteor Boots Impact",
                        "Forge Ground Colossus", "Lifeline Eternal"}};
        };
    }

    private static String advancedCallout(Faction faction, String name) {
        return switch (faction) {
            case SOUL_REAPER -> "Bankai! " + name + "!";
            case HOLLOW -> "Segunda Etapa — " + name + "!";
            case QUINCY -> "Vollständig: " + name + "!";
            case FULLBRINGER -> "Complete Fullbring: " + name + "!";
        };
    }

    static Form form(Faction faction, Playstyle style, int variant, boolean advanced) {
        Form base = form(faction, style, variant);
        if (!advanced) {
            return base;
        }
        String name = advancedNames(faction)[variant][style.ordinal()];
        return new Form(name, advancedCallout(faction, name), base.color(), base.text(), base.bar());
    }

    /** The advanced form's once-per-release ultimate, set by its playstyle. */
    static String ultimateName(Faction faction, Playstyle style) {
        String[] names = switch (faction) {
            case SOUL_REAPER -> new String[] {"Heaven-Rending Moon", "Thousand Mirage Execution", "Galaxy Fall",
                    "Divine Tempest", "World Breaker", "Sanctum of Light"};
            case HOLLOW -> new String[] {"Desgarro Final", "Ejecución Fantasma", "Lluvia del Abismo", "Huracán Hueco",
                    "Fin del Mundo", "Santuario Vacío"};
            case QUINCY -> new String[] {"Heiliges Gericht", "Phantomhinrichtung", "Sternenflut", "Himmelssturm",
                    "Weltbrecher", "Lichtdom"};
            case FULLBRINGER -> new String[] {"Final Knuckle", "Grand Shuffle", "Full Clip Storm", "Stratosphere Drop",
                    "Foundry Quake", "Lifeline Sanctuary"};
        };
        return names[style.ordinal()];
    }

    static String ultimateDescription(Playstyle style) {
        return switch (style) {
            case VANGUARD -> "two volleys of three giant waves";
            case MIRAGE -> "strike up to five enemies in sequence from behind, then return";
            case MARKSMAN -> "a 26-bolt storm over a wide area";
            case WAYFARER -> "a three-second tornado that drags enemies in and hurls them skyward";
            case ARTIFICER -> "three expanding rings of erupting pillars";
            case KEEPER -> "an eight-second sanctum that heals allies and grinds down enemies inside it";
        };
    }

    // ---------------------------------------------------------------- kit selection

    /**
     * The form variant (0 or 1) a player gets for a playstyle. Stable per player and spread evenly. First and
     * advanced forms roll separately, so a player's Bankai doesn't simply mirror their Shikai.
     */
    static int variant(UUID id, Playstyle style, boolean advanced) {
        return (int) (hash(id, style.ordinal() + 1 + (advanced ? 32 : 0)) & 1);
    }

    private static long hash(UUID id, int salt) {
        long h = id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17)
                ^ (salt * 0x9E3779B97F4A7C15L);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return h;
    }

    static Form form(UUID id, Faction faction, Playstyle style, boolean advanced) {
        return form(faction, style, variant(id, style, advanced), advanced);
    }

    static Mechanic primary(UUID id, Playstyle style, boolean advanced) {
        return Mechanic.of(style, variant(id, style, advanced));
    }

    /**
     * Secondary technique: from the secondary playstyle if there is one. Players without one draw a stable
     * pick from all the other techniques, so they don't all share the same fallback.
     */
    static Mechanic secondary(UUID id, Playstyle style, Playstyle trait, boolean advanced) {
        if (trait != null && trait != style) {
            return Mechanic.of(trait, variant(id, trait, advanced));
        }
        Mechanic main = primary(id, style, advanced);
        Mechanic[] all = Mechanic.values();
        int pick = (int) Math.floorMod(hash(id, advanced ? 133 : 101), (long) (all.length - 1));
        return all[pick >= main.ordinal() ? pick + 1 : pick];
    }

    static String description(UUID id, Faction faction, Playstyle style, Playstyle trait, boolean advanced) {
        Form form = form(id, faction, style, advanced);
        Mechanic main = primary(id, style, advanced);
        Mechanic second = secondary(id, style, trait, advanced);
        String text = "\"" + form.callout() + "\" A timed release. Right-click: " + techniqueName(faction, main) + " — "
                + main.description + ". Sneak + right-click: " + techniqueName(faction, second) + " — "
                + second.description + ". " + Theme.of(faction).onHit;
        if (advanced) {
            text += " Techniques hit harder. F (once per release): " + ultimateName(faction, style) + " — "
                    + ultimateDescription(style) + ". Sneak + F seals.";
        }
        return text;
    }

    // ---------------------------------------------------------------- state

    private static final class Release {
        final Faction faction;
        final Playstyle style;
        final boolean advanced;
        final double power;
        final Form form;
        final Theme theme;
        final Mechanic main;
        final Mechanic second;
        final long startedAt;
        final long endsAt;
        final BossBar bar;
        BukkitTask task;
        long mainReadyAt;
        long secondReadyAt;
        boolean ultimateUsed;

        Release(Faction faction, Playstyle style, boolean advanced, double power, Form form, Mechanic main,
                Mechanic second, long startedAt, long endsAt, BossBar bar) {
            this.faction = faction;
            this.style = style;
            this.advanced = advanced;
            this.power = power;
            this.form = form;
            this.theme = Theme.of(faction);
            this.main = main;
            this.second = second;
            this.startedAt = startedAt;
            this.endsAt = endsAt;
            this.bar = bar;
        }
    }

    private final BleachSmpPlugin plugin;
    private final AwakeningService service;
    private final Map<UUID, Release> active = new HashMap<>();
    private final Set<BlockDisplay> displays = new HashSet<>();

    ReleaseService(BleachSmpPlugin plugin, AwakeningService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public boolean isActive(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    boolean isAdvanced(Player player) {
        Release release = active.get(player.getUniqueId());
        return release != null && release.advanced;
    }

    /** Reads a setting from {@code awakenings.release} or {@code awakenings.advanced-release}. */
    private int setting(boolean advanced, String key, int fallback, int min, int max) {
        return Math.clamp(plugin.getConfig().getInt("awakenings." + (advanced ? "advanced-release." : "release.") + key,
                fallback), min, max);
    }

    int durationSeconds(boolean advanced) {
        return setting(advanced, "duration-seconds", advanced ? 40 : 30, 5, 300);
    }

    private double damage(double base) {
        return base * Math.clamp(plugin.getConfig().getDouble("awakenings.release.damage-multiplier", 1.0), 0.0, 5.0);
    }

    // ---------------------------------------------------------------- release

    /** Releases a form: the call-out, the faction's release burst, and the timed released state. */
    void release(Player player, Faction faction, Playstyle style, Playstyle trait, boolean advanced) {
        seal(player, false);
        UUID id = player.getUniqueId();
        Form form = form(id, faction, style, advanced);
        Theme theme = Theme.of(faction);
        long now = System.currentTimeMillis();
        int seconds = durationSeconds(advanced);
        double power = advanced ? Math.clamp(plugin.getConfig().getDouble("awakenings.advanced-release.power", 1.4),
                0.0, 5.0) : 1.0;
        String tier = AwakeningService.formName(faction, advanced);
        BossBar bar = BossBar.bossBar(Component.text(tier + " — " + form.name(), form.text(), TextDecoration.BOLD),
                1.0f, form.bar(), advanced ? BossBar.Overlay.NOTCHED_20 : BossBar.Overlay.NOTCHED_10);
        Release release = new Release(faction, style, advanced, power, form, primary(id, style, advanced),
                secondary(id, style, trait, advanced), now, now + seconds * 1000L, bar);
        active.put(id, release);

        Location loc = player.getLocation();
        Component callout = Component.text(player.getName() + ": ", NamedTextColor.GRAY)
                .append(Component.text("\"" + form.callout() + "\"", form.text(),
                        advanced ? TextDecoration.BOLD : TextDecoration.ITALIC));
        for (Player nearby : loc.getNearbyPlayers(advanced ? 64 : 32)) {
            nearby.sendMessage(callout);
        }
        player.showTitle(Title.title(Component.text(advanced ? tier.toUpperCase(java.util.Locale.ROOT) : form.name(),
                        advanced ? NamedTextColor.WHITE : form.text(), TextDecoration.BOLD),
                Component.text(advanced ? form.name() : tier, advanced ? form.text() : NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(advanced ? 300 : 150), Duration.ofMillis(advanced ? 2200 : 1400),
                        Duration.ofMillis(600))));
        player.sendMessage(Component.text("Right-click: " + techniqueName(faction, release.main)
                + "  |  Sneak + right-click: " + techniqueName(faction, release.second)
                + (advanced ? "  |  F: " + ultimateName(faction, style) + "  |  Sneak + F: seal" : ""), form.text()));
        player.showBossBar(bar);
        player.addPotionEffect(new PotionEffect(theme.passive, seconds * 20, theme.passiveAmplifier + (advanced ? 1 : 0),
                false, false, true));

        if (advanced) {
            advancedEntrance(player, release);
        } else {
            releaseSounds(loc, theme);
            Vfx.shockwave(plugin, loc, form.color(), 5.0, 8);
            // Spiritual pressure: push nearby enemies away without damaging them.
            for (LivingEntity entity : loc.getNearbyLivingEntities(5)) {
                if (service.canStrike(player, entity)) {
                    entity.setVelocity(outward(loc, entity).multiply(0.9).setY(0.35));
                }
            }
            releaseAnimation(player, form, theme);
        }

        release.task = new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                long time = System.currentTimeMillis();
                if (!player.isOnline() || player.isDead() || !AwakeningService.canPlay(player)
                        || time >= release.endsAt) {
                    seal(player, true);
                    return;
                }
                release.bar.progress((float) Math.clamp(
                        (release.endsAt - time) / (double) (release.endsAt - release.startedAt), 0.0, 1.0));
                aura(player, release, tick);
                if (release.advanced) {
                    advancedAura(player, release, tick);
                }
                if (tick % 10 == 0) {
                    showCooldowns(player, release, time);
                }
                tick += 2;
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    private static void showCooldowns(Player player, Release release, long now) {
        player.sendActionBar(cooldown(techniqueName(release.faction, release.main), release.mainReadyAt, now, release)
                .append(Component.text("   |   Sneak: ", NamedTextColor.DARK_GRAY))
                .append(cooldown(techniqueName(release.faction, release.second), release.secondReadyAt, now, release))
                .append(release.advanced ? Component.text("   |   F: ", NamedTextColor.DARK_GRAY)
                        .append(release.ultimateUsed
                                ? Component.text(ultimateName(release.faction, release.style) + " spent", NamedTextColor.GRAY)
                                : Component.text(ultimateName(release.faction, release.style) + " ✔", release.form.text(),
                                        TextDecoration.BOLD))
                        : Component.empty()));
    }

    private static Component cooldown(String name, long readyAt, long now, Release release) {
        return readyAt > now
                ? Component.text(name + " " + ((readyAt - now + 999) / 1000) + "s", NamedTextColor.GRAY)
                : Component.text(name + " ✔", release.form.text());
    }

    private static void releaseSounds(Location loc, Theme theme) {
        switch (theme) {
            case SOUL_REAPER -> {
                Vfx.sound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.6f);
                Vfx.sound(loc, Sound.ITEM_TRIDENT_THUNDER, 0.6f, 1.4f);
                Vfx.sound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.6f);
            }
            case HOLLOW -> {
                Vfx.sound(loc, Sound.ENTITY_WARDEN_ROAR, 0.7f, 1.3f);
                Vfx.sound(loc, Sound.ENTITY_WITHER_AMBIENT, 0.6f, 0.7f);
                Vfx.sound(loc, Sound.PARTICLE_SOUL_ESCAPE, 1.0f, 0.8f);
            }
            case QUINCY -> {
                Vfx.sound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.5f);
                Vfx.sound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.8f);
                Vfx.sound(loc, Sound.BLOCK_CONDUIT_ACTIVATE, 0.8f, 1.6f);
            }
            case FULLBRINGER -> {
                Vfx.sound(loc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 1.2f);
                Vfx.sound(loc, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 0.8f);
                Vfx.sound(loc, Sound.BLOCK_SMITHING_TABLE_USE, 0.8f, 1.1f);
            }
        }
    }

    /** The first second after a release: each faction gets its own rising effect. */
    private void releaseAnimation(Player player, Form form, Theme theme) {
        player.getWorld().spawnParticle(Particle.SONIC_BOOM, player.getLocation().add(0, 1, 0), 1);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || tick > 20) {
                    cancel();
                    return;
                }
                Location base = player.getLocation();
                var world = base.getWorld();
                switch (theme) {
                    case SOUL_REAPER -> {
                        for (int strand = 0; strand < 3; strand++) {
                            double a = tick * 0.6 + strand * (Math.PI * 2 / 3);
                            world.spawnParticle(Particle.DUST, base.clone().add(Math.cos(a) * 1.1, tick * 0.15,
                                    Math.sin(a) * 1.1), 2, 0.02, 0.02, 0.02, 0, form.dust(1.6f));
                        }
                    }
                    case HOLLOW -> {
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, base.clone().add(0, tick * 0.12, 0), 6,
                                0.45, 0.2, 0.45, 0.02);
                        world.spawnParticle(Particle.LARGE_SMOKE, base.clone().add(0, 0.4, 0), 2, 0.6, 0.2, 0.6, 0.01);
                        double r = 1.6 - tick * 0.06;
                        for (int s = 0; s < 2; s++) {
                            double a = tick * 0.8 + s * Math.PI;
                            world.spawnParticle(Particle.DUST, base.clone().add(Math.cos(a) * r, 1.0, Math.sin(a) * r),
                                    3, 0.03, 0.3, 0.03, 0, form.dust(1.7f));
                        }
                        if (tick % 5 == 0) {
                            world.spawnParticle(Particle.SCULK_SOUL, base.clone().add(0, 1.2, 0), 4, 0.4, 0.4, 0.4, 0.05);
                        }
                    }
                    case QUINCY -> {
                        Vfx.ring(base.clone().add(0, 2.6 - tick * 0.05, 0), 0.55, 14, form.color(), 1.0f);
                        for (int p = 0; p < 6; p++) {
                            double a = p * (Math.PI / 3);
                            world.spawnParticle(Particle.END_ROD, base.clone().add(Math.cos(a) * 1.8, tick * 0.13,
                                    Math.sin(a) * 1.8), 1, 0, 0, 0, 0);
                        }
                    }
                    case FULLBRINGER -> {
                        Location hand = base.clone().add(0, 1.2, 0)
                                .add(base.getDirection().getCrossProduct(new Vector(0, 1, 0)).multiply(-0.4));
                        double r = 2.2 - tick * 0.1;
                        for (int s = 0; s < 4; s++) {
                            double a = tick * 0.5 + s * (Math.PI / 2);
                            world.spawnParticle(Particle.DUST, hand.clone().add(Math.cos(a) * r, Math.sin(tick * 0.3) * 0.4,
                                    Math.sin(a) * r), 1, 0, 0, 0, 0, form.dust(1.4f));
                        }
                        world.spawnParticle(Particle.ENCHANT, hand, 10, 0.8, 0.8, 0.8, 0.6);
                        if (tick == 20) {
                            world.spawnParticle(Particle.TOTEM_OF_UNDYING, hand, 40, 0.3, 0.4, 0.3, 0.4);
                        }
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static void aura(Player player, Release release, int tick) {
        Location base = player.getLocation();
        var world = base.getWorld();
        for (int i = 0; i < 2; i++) {
            double a = tick * 0.35 + i * Math.PI;
            world.spawnParticle(Particle.DUST, base.clone().add(Math.cos(a) * 0.75,
                    1.0 + Math.sin(tick * 0.2 + i) * 0.6, Math.sin(a) * 0.75), 1, 0, 0, 0, 0, release.form.dust(0.9f));
        }
        if (tick % 10 == 0) {
            switch (release.theme) {
                case SOUL_REAPER -> world.spawnParticle(Particle.ELECTRIC_SPARK, base.clone().add(0, 1, 0), 3,
                        0.35, 0.5, 0.35, 0.02);
                case HOLLOW -> {
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, base.clone().add(0, 0.2, 0), 2, 0.3, 0.05, 0.3, 0.01);
                    world.spawnParticle(Particle.SMOKE, base.clone().add(0, 1.2, 0), 2, 0.3, 0.4, 0.3, 0.01);
                }
                case QUINCY -> Vfx.ring(base.clone().add(0, 2.3, 0), 0.4, 10, release.form.color(), 0.8f);
                case FULLBRINGER -> world.spawnParticle(Particle.ENCHANT, base.clone().add(0, 1.2, 0), 6,
                        0.4, 0.5, 0.4, 0.4);
            }
        }
    }

    /** Ends a release. Returns whether one was active. */
    boolean seal(Player player, boolean announce) {
        Release release = active.remove(player.getUniqueId());
        if (release == null) {
            return false;
        }
        if (release.task != null) {
            release.task.cancel();
        }
        player.hideBossBar(release.bar);
        if (announce && player.isOnline()) {
            Location loc = player.getLocation();
            Vfx.sound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.4f);
            Vfx.burst(loc.clone().add(0, 1, 0), release.form.color(), 20, 0.5, 1.0f);
            player.sendMessage(Component.text(release.form.name() + " fades back into its sealed form.",
                    NamedTextColor.GRAY));
        }
        return true;
    }

    public void sealAll() {
        for (UUID id : Set.copyOf(active.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                seal(player, false);
            } else {
                Release release = active.remove(id);
                if (release.task != null) {
                    release.task.cancel();
                }
            }
        }
        for (BlockDisplay display : Set.copyOf(displays)) {
            display.remove();
        }
        displays.clear();
    }

    // ---------------------------------------------------------------- techniques

    /**
     * Uses a released form's technique: the main one, or the secondary one when {@code secondary} is set
     * (sneak + right-click). Called by the starter item's right-click ability in place of the sealed ability.
     */
    public void useTechnique(Player player, boolean secondary) {
        Release release = active.get(player.getUniqueId());
        if (release == null) {
            return;
        }
        Mechanic mechanic = secondary ? release.second : release.main;
        String name = techniqueName(release.faction, mechanic);
        long now = System.currentTimeMillis();
        long readyAt = secondary ? release.secondReadyAt : release.mainReadyAt;
        if (readyAt > now) {
            player.sendActionBar(Component.text(name + " ready in " + ((readyAt - now + 999) / 1000) + "s",
                    NamedTextColor.YELLOW));
            return;
        }
        boolean used = switch (mechanic) {
            case WAVE -> wave(player, release);
            case BLINK -> blink(player, release, name);
            case BEAM -> beam(player, release);
            case DASH -> dash(player, release);
            case SLAM -> slam(player, release);
            case WARD -> ward(player, release);
            case FLURRY -> flurry(player, release, name);
            case SCATTER -> scatter(player, release);
            case RAIN -> rain(player, release);
            case SKYFALL -> skyfall(player, release);
            case PILLARS -> pillars(player, release);
            case CHAINS -> chains(player, release, name);
        };
        if (!used) {
            return;
        }
        int cooldown = setting(release.advanced,
                secondary ? "secondary-technique-cooldown-seconds" : "technique-cooldown-seconds",
                release.advanced ? (secondary ? 8 : 5) : (secondary ? 10 : 6), 0, 600);
        if (secondary) {
            release.secondReadyAt = now + cooldown * 1000L;
        } else {
            release.mainReadyAt = now + cooldown * 1000L;
        }
        player.sendActionBar(Component.text(name + "!", release.form.text(), TextDecoration.BOLD));
    }

    /** Damages a target and applies the faction's on-hit effect with a small impact flash. */
    private void hit(Player caster, LivingEntity target, double amount, Release release) {
        target.damage(damage(amount) * release.power, caster);
        Location at = target.getLocation().add(0, target.getHeight() / 2, 0);
        at.getWorld().spawnParticle(release.theme.accent, at, 8, 0.3, 0.3, 0.3, 0.05);
        Vfx.burst(at, release.form.color(), 8, 0.3, 1.2f);
        switch (release.theme) {
            case SOUL_REAPER -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0));
            case HOLLOW -> {
                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
                var max = caster.getAttribute(Attribute.MAX_HEALTH);
                caster.setHealth(Math.min(max == null ? 20.0 : max.getValue(), caster.getHealth() + 1.0));
            }
            case QUINCY -> target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
            case FULLBRINGER -> target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0));
        }
    }

    /** Faction-flavoured cast sound shared by several techniques. */
    private static void castSound(Location at, Theme theme, float pitch) {
        switch (theme) {
            case SOUL_REAPER -> Vfx.sound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, pitch);
            case HOLLOW -> Vfx.sound(at, Sound.ENTITY_RAVAGER_ATTACK, 1.0f, pitch);
            case QUINCY -> Vfx.sound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, pitch);
            case FULLBRINGER -> Vfx.sound(at, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, pitch);
        }
    }

    private boolean wave(Player player, Release release) {
        waveSound(player.getEyeLocation(), release.theme);
        launchWave(player, release, player.getEyeLocation().getDirection(), release.advanced ? 2.6 : 2.0, 8.0);
        return true;
    }

    private static void waveSound(Location origin, Theme theme) {
        switch (theme) {
            case SOUL_REAPER -> {
                Vfx.sound(origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
                Vfx.sound(origin, Sound.ENTITY_WITHER_SHOOT, 0.5f, 1.8f);
            }
            case HOLLOW -> {
                Vfx.sound(origin, Sound.ENTITY_RAVAGER_ATTACK, 1.0f, 0.8f);
                Vfx.sound(origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.4f);
            }
            case QUINCY -> {
                Vfx.sound(origin, Sound.ENTITY_ARROW_SHOOT, 1.0f, 0.6f);
                Vfx.sound(origin, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
            }
            case FULLBRINGER -> {
                Vfx.sound(origin, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.8f);
                Vfx.sound(origin, Sound.ITEM_MACE_SMASH_AIR, 1.0f, 0.9f);
            }
        }
    }

    /** A travelling wave: a crescent (three claw marks for Hollows) that hits each enemy once. */
    private void launchWave(Player player, Release release, Vector direction, double size, double amount) {
        Location origin = player.getEyeLocation().subtract(0, 0.35, 0);
        Vector forward = direction.clone().normalize();
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            int step;

            @Override
            public void run() {
                Location center = origin.clone().add(forward.clone().multiply(1.5 + step * 1.5));
                if (step >= 11 || center.getBlock().getType().isSolid()) {
                    Vfx.burst(center, release.form.color(), 25, 0.6, 1.8f);
                    Vfx.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.7f);
                    cancel();
                    return;
                }
                if (release.theme == Theme.HOLLOW) {
                    for (double y = -0.55; y <= 0.56; y += 0.55) {
                        Vfx.arc(center, forward, size * 0.75, release.form.color(), 1.4f, y);
                    }
                } else {
                    Vfx.arc(center, forward, size, release.form.color(), 1.8f, 0);
                }
                center.getWorld().spawnParticle(release.theme.accent, center, 4, 0.5, 0.3, 0.5, 0.02);
                if (release.theme == Theme.SOUL_REAPER) {
                    center.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center, 1);
                }
                for (LivingEntity entity : center.getNearbyLivingEntities(size + 0.1)) {
                    if (hit.add(entity.getUniqueId()) && service.canStrike(player, entity)) {
                        hit(player, entity, amount, release);
                        entity.setVelocity(forward.clone().multiply(0.6).setY(0.3));
                    }
                }
                step++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private LivingEntity aimedEnemy(Player player, double range) {
        var result = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(),
                range, 0.6, entity -> entity instanceof LivingEntity && !entity.equals(player));
        if (result != null && result.getHitEntity() instanceof LivingEntity target && service.canStrike(player, target)) {
            return target;
        }
        return null;
    }

    private boolean blink(Player player, Release release, String name) {
        LivingEntity target = aimedEnemy(player, 12);
        if (target == null) {
            player.sendActionBar(Component.text(name + " needs an enemy within 12 blocks.", NamedTextColor.GRAY));
            return false;
        }
        Vector facing = target.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 0.01) {
            facing = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
        }
        if (facing.lengthSquared() < 0.01) {
            facing = new Vector(0, 0, 1);
        }
        Location behind = target.getLocation().subtract(facing.normalize().multiply(1.4));
        if (!standable(behind)) {
            player.sendActionBar(Component.text("No room behind your target.", NamedTextColor.GRAY));
            return false;
        }
        behind.setDirection(target.getLocation().toVector().subtract(behind.toVector()));
        Location from = player.getLocation().add(0, 1, 0);
        afterimage(from, release);
        Vfx.line(from, behind.clone().add(0, 1, 0), release.form.color(), 0.8f, 0.6);
        blinkSound(from, release.theme);
        player.teleport(behind);
        hit(player, target, 7.0, release);
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
        target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 2, 0.3, 0.2, 0.3, 0);
        Vfx.sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.8f);
        return true;
    }

    private static void afterimage(Location at, Release release) {
        Vfx.burst(at, release.form.color(), 40, 0.35, 1.5f);
        at.getWorld().spawnParticle(Particle.CLOUD, at, 12, 0.3, 0.6, 0.3, 0.02);
        at.getWorld().spawnParticle(release.theme.accent, at, 10, 0.3, 0.6, 0.3, 0.05);
    }

    private static void blinkSound(Location at, Theme theme) {
        switch (theme) {
            case HOLLOW -> Vfx.sound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.6f);
            case QUINCY -> Vfx.sound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 1.6f);
            case FULLBRINGER -> Vfx.sound(at, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.4f);
            case SOUL_REAPER -> Vfx.sound(at, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.2f);
        }
    }

    private static boolean standable(Location at) {
        return at.getBlock().isPassable() && at.clone().add(0, 1, 0).getBlock().isPassable();
    }

    private boolean beam(Player player, Release release) {
        Location origin = player.getEyeLocation();
        Vector forward = origin.getDirection().normalize();
        boolean cero = release.theme == Theme.HOLLOW;
        switch (release.theme) {
            case HOLLOW -> {
                Vfx.sound(origin, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 1.6f);
                Vfx.sound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.2f);
            }
            case QUINCY -> {
                Vfx.sound(origin, Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.8f);
                Vfx.sound(origin, Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 2.0f);
            }
            case FULLBRINGER -> {
                Vfx.sound(origin, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 0.6f);
                Vfx.sound(origin, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.0f, 0.6f);
            }
            case SOUL_REAPER -> Vfx.sound(origin, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 0.7f);
        }
        Vfx.burst(origin.clone().add(forward), release.form.color(), cero ? 20 : 8, 0.15, 1.6f);
        int speed = cero ? 4 : 3;
        new BukkitRunnable() {
            int step;

            @Override
            public void run() {
                for (int sub = 0; sub < speed; sub++) {
                    Location point = origin.clone().add(forward.clone().multiply(1 + step * speed + sub));
                    point.getWorld().spawnParticle(release.theme.accent, point, 1, 0, 0, 0, 0);
                    point.getWorld().spawnParticle(Particle.DUST, point, cero ? 6 : 2, cero ? 0.22 : 0.08,
                            cero ? 0.22 : 0.08, cero ? 0.22 : 0.08, 0, release.form.dust(cero ? 2.0f : 1.4f));
                    LivingEntity struck = null;
                    for (LivingEntity entity : point.getNearbyLivingEntities(cero ? 1.5 : 1.2)) {
                        if (!entity.equals(player)) {
                            struck = entity;
                            break;
                        }
                    }
                    if (struck != null || point.getBlock().getType().isSolid() || step >= 10) {
                        burst(point, struck);
                        cancel();
                        return;
                    }
                }
                step++;
            }

            private void burst(Location point, LivingEntity struck) {
                point.getWorld().spawnParticle(release.theme.accent, point, 40, 0.2, 0.2, 0.2, 0.25);
                Vfx.burst(point, release.form.color(), 30, 0.9, 1.8f);
                point.getWorld().spawnParticle(Particle.EXPLOSION, point, 1);
                Vfx.sound(point, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 0.8f);
                if (struck != null && service.canStrike(player, struck)) {
                    hit(player, struck, 9.0, release);
                }
                for (LivingEntity entity : point.getNearbyLivingEntities(2.5)) {
                    if (!entity.equals(struck) && service.canStrike(player, entity)) {
                        hit(player, entity, 4.0, release);
                    }
                }
            }
        }.runTaskTimer(plugin, cero ? 4L : 0L, 1L);
        return true;
    }

    private boolean dash(Player player, Release release) {
        Vector forward = horizontal(player);
        player.setVelocity(forward.multiply(2.1).setY(0.15));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 40, 0, false, false, true));
        Location start = player.getLocation();
        switch (release.theme) {
            case HOLLOW -> Vfx.sound(start, Sound.ENTITY_RAVAGER_ROAR, 0.7f, 1.5f);
            case QUINCY -> Vfx.sound(start, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.8f);
            case FULLBRINGER -> Vfx.sound(start, Sound.ENTITY_PHANTOM_FLAP, 1.0f, 0.8f);
            case SOUL_REAPER -> { }
        }
        Vfx.sound(start, Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 1.0f);
        Vfx.ring(start.clone().add(0, 0.1, 0), 1.2, 16, release.form.color(), 1.3f);
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || tick++ >= 9) {
                    cancel();
                    return;
                }
                Location at = player.getLocation().add(0, 1, 0);
                at.getWorld().spawnParticle(Particle.CLOUD, at, 4, 0.3, 0.4, 0.3, 0.02);
                at.getWorld().spawnParticle(release.theme.accent, at, 2, 0.3, 0.4, 0.3, 0.02);
                Vfx.burst(at, release.form.color(), 6, 0.5, 1.3f);
                for (LivingEntity entity : at.getNearbyLivingEntities(1.9)) {
                    if (hit.add(entity.getUniqueId()) && service.canStrike(player, entity)) {
                        hit(player, entity, 6.0, release);
                        entity.getWorld().spawnParticle(Particle.SWEEP_ATTACK, entity.getLocation().add(0, 1, 0), 1);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private boolean slam(Player player, Release release) {
        Location center = player.getLocation();
        groundImpact(player, release, center, 5.0, 6.0, 0.95);
        switch (release.theme) {
            case HOLLOW -> Vfx.sound(center, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.0f, 0.6f);
            case QUINCY -> Vfx.sound(center, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.0f, 0.6f);
            case FULLBRINGER -> Vfx.sound(center, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.5f);
            case SOUL_REAPER -> Vfx.sound(center, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.5f);
        }
        return true;
    }

    /** Shared ground impact: damage and launch enemies in a radius, with rippling debris rings. */
    private void groundImpact(Player player, Release release, Location center, double radius, double amount,
            double launch) {
        Material ground = center.clone().subtract(0, 1, 0).getBlock().getType();
        BlockData debris = (ground.isSolid() ? ground : Material.STONE).createBlockData();
        Vfx.sound(center, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.0f, 0.7f);
        Vfx.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.6f);
        for (LivingEntity entity : center.getNearbyLivingEntities(radius)) {
            if (service.canStrike(player, entity)) {
                hit(player, entity, amount, release);
                entity.setVelocity(outward(center, entity).multiply(0.4).setY(launch));
            }
        }
        new BukkitRunnable() {
            int ring;

            @Override
            public void run() {
                if (ring > 6) {
                    cancel();
                    return;
                }
                double r = 0.6 + ring * (radius - 0.6) / 6;
                for (int i = 0; i < 24; i++) {
                    double a = i * (Math.PI * 2 / 24);
                    Location point = center.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
                    point.getWorld().spawnParticle(Particle.BLOCK, point, 4, 0.1, 0.2, 0.1, 0.1, debris);
                    if (i % 3 == 0) {
                        point.getWorld().spawnParticle(Particle.DUST, point.add(0, 0.4, 0), 1, 0, 0, 0, 0,
                                release.form.dust(1.5f));
                        point.getWorld().spawnParticle(release.theme.accent, point, 1, 0, 0.2, 0, 0.02);
                    }
                }
                ring++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private boolean ward(Player player, Release release) {
        Location center = player.getLocation();
        for (Player ally : center.getNearbyPlayers(6)) {
            if (ally.equals(player) || (AwakeningService.canPlay(ally) && AwakeningPowers.allies(player, ally))) {
                var maxHealth = ally.getAttribute(Attribute.MAX_HEALTH);
                double max = maxHealth == null ? 20.0 : maxHealth.getValue();
                ally.setHealth(Math.min(max, ally.getHealth() + 4.0));
                ally.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 160, 0));
                ally.getWorld().spawnParticle(Particle.HEART, ally.getLocation().add(0, 2, 0), 2, 0.3, 0.1, 0.3, 0);
                Vfx.ring(ally.getLocation().add(0, 0.2, 0), 0.7, 12, release.form.color(), 1.0f);
            }
        }
        for (LivingEntity entity : center.getNearbyLivingEntities(4)) {
            if (service.canStrike(player, entity)) {
                hit(player, entity, 4.0, release);
                entity.setVelocity(outward(center, entity).multiply(1.1).setY(0.3));
            }
        }
        switch (release.theme) {
            case HOLLOW -> Vfx.sound(center, Sound.ENTITY_TURTLE_EGG_CRACK, 1.0f, 0.5f);
            case QUINCY -> Vfx.sound(center, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.2f);
            case FULLBRINGER -> Vfx.sound(center, Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.7f);
            case SOUL_REAPER -> Vfx.sound(center, Sound.BLOCK_BELL_USE, 0.7f, 1.5f);
        }
        Vfx.sound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.7f);
        for (int layer = 0; layer < 3; layer++) {
            Vfx.ring(center.clone().add(0, 0.3 + layer * 0.8, 0), 4 - layer * 0.9, 36 - layer * 8,
                    release.form.color(), 1.3f);
        }
        center.getWorld().spawnParticle(release.theme.accent, center.clone().add(0, 1, 0), 20, 0.5, 0.8, 0.5, 0.05);
        return true;
    }

    /** Lunge at the aimed enemy and land a five-hit combo, each slash at a new angle. */
    private boolean flurry(Player player, Release release, String name) {
        LivingEntity target = aimedEnemy(player, 6);
        if (target == null) {
            player.sendActionBar(Component.text(name + " needs an enemy within 6 blocks.", NamedTextColor.GRAY));
            return false;
        }
        Vector toward = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
        if (toward.lengthSquared() > 0.01) {
            player.setVelocity(toward.normalize().multiply(0.7).setY(0.1));
        }
        new BukkitRunnable() {
            int strike;

            @Override
            public void run() {
                if (!player.isOnline() || !target.isValid() || target.isDead()
                        || target.getLocation().distanceSquared(player.getLocation()) > 49 || strike >= 5) {
                    cancel();
                    return;
                }
                boolean last = strike == 4;
                Location at = target.getLocation().add(0, target.getHeight() * 0.55, 0);
                Vector facing = at.toVector().subtract(player.getLocation().toVector()).setY(0);
                if (facing.lengthSquared() < 0.01) {
                    facing = new Vector(0, 0, 1);
                }
                double tilt = ThreadLocalRandom.current().nextDouble(-0.6, 0.6);
                Vfx.arc(at, facing, last ? 1.8 : 1.2, release.form.color(), last ? 1.7f : 1.2f, tilt);
                at.getWorld().spawnParticle(Particle.SWEEP_ATTACK, at, 1);
                castSound(at, release.theme, 1.0f + strike * 0.18f);
                target.setNoDamageTicks(0);
                hit(player, target, last ? 3.5 : 2.0, release);
                if (last) {
                    target.setVelocity(facing.normalize().multiply(0.8).setY(0.35));
                    at.getWorld().spawnParticle(Particle.CRIT, at, 20, 0.4, 0.4, 0.4, 0.3);
                    Vfx.sound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.7f);
                }
                strike++;
            }
        }.runTaskTimer(plugin, 3L, 3L);
        return true;
    }

    /** Scatter into afterimages: blind and shake nearby enemies, turn invisible, and hop three times. */
    private boolean scatter(Player player, Release release) {
        Location start = player.getLocation();
        for (LivingEntity entity : start.getNearbyLivingEntities(5)) {
            if (service.canStrike(player, entity)) {
                hit(player, entity, 3.0, release);
                entity.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0));
                if (entity instanceof Mob mob && player.equals(mob.getTarget())) {
                    mob.setTarget(null);
                }
            }
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 60, 0, false, false, true));
        service.conceal(player, 3);
        Vfx.ring(start.clone().add(0, 0.2, 0), 5, 40, release.form.color(), 1.2f);
        Vfx.sound(start, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.3f);
        new BukkitRunnable() {
            int hop;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || hop >= 3) {
                    cancel();
                    return;
                }
                hop++;
                Location from = player.getLocation();
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int attempt = 0; attempt < 8; attempt++) {
                    double a = random.nextDouble(Math.PI * 2);
                    double d = random.nextDouble(2.5, 4.5);
                    Location to = from.clone().add(Math.cos(a) * d, 0, Math.sin(a) * d);
                    to.setY(Math.floor(to.getY()));
                    if (standable(to) && to.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) {
                        afterimage(from.clone().add(0, 1, 0), release);
                        blinkSound(from, release.theme);
                        to.setYaw(from.getYaw());
                        to.setPitch(from.getPitch());
                        player.teleport(to);
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 2L, 5L);
        return true;
    }

    /** Call down a storm of bolts on the area the player aims at. */
    private boolean rain(Player player, Release release) {
        Location target = aimPoint(player, 24);
        castSound(player.getLocation(), release.theme, 0.6f);
        Vfx.sound(target, Sound.ITEM_TRIDENT_THUNDER, 0.6f, 1.6f);
        storm(player, release, target, release.advanced ? 4.0 : 3.2, 10, 3.0, 3, 3L);
        return true;
    }

    /** The ground spot the player is aiming at, up to {@code range} blocks away. */
    private static Location aimPoint(Player player, double range) {
        var aim = player.rayTraceBlocks(range);
        Location center = aim != null ? aim.getHitPosition().toLocation(player.getWorld())
                : player.getLocation().add(horizontal(player).multiply(range / 2));
        Location ground = groundAt(center);
        return ground != null ? ground : center;
    }

    /** Drops {@code bolts} bolts at random inside a circle; each entity can be hit at most {@code cap} times. */
    private void storm(Player player, Release release, Location target, double radius, int bolts, double amount,
            int cap, long period) {
        Vfx.ring(target.clone().add(0, 0.15, 0), radius, (int) (radius * 10), release.form.color(), 1.3f);
        Map<UUID, Integer> hits = new HashMap<>();
        new BukkitRunnable() {
            int bolt;

            @Override
            public void run() {
                if (!player.isOnline() || bolt++ >= bolts) {
                    cancel();
                    return;
                }
                ThreadLocalRandom random = ThreadLocalRandom.current();
                double a = random.nextDouble(Math.PI * 2);
                double d = Math.sqrt(random.nextDouble()) * radius;
                Location spot = target.clone().add(Math.cos(a) * d, 0, Math.sin(a) * d);
                Location floor = groundAt(spot);
                Location impact = floor != null ? floor : spot;
                Vfx.line(impact.clone().add(0, 9, 0), impact, release.form.color(), 1.1f, 0.45);
                impact.getWorld().spawnParticle(release.theme.accent, impact, 8, 0.2, 0.1, 0.2, 0.05);
                Vfx.burst(impact.clone().add(0, 0.3, 0), release.form.color(), 10, 0.4, 1.3f);
                Vfx.sound(impact, release.theme == Theme.HOLLOW ? Sound.ENTITY_BLAZE_SHOOT
                        : Sound.ENTITY_ARROW_HIT, 0.8f, 1.2f + random.nextFloat() * 0.4f);
                for (LivingEntity entity : impact.getNearbyLivingEntities(1.5)) {
                    int count = hits.getOrDefault(entity.getUniqueId(), 0);
                    if (count < cap && service.canStrike(player, entity)) {
                        hits.put(entity.getUniqueId(), count + 1);
                        entity.setNoDamageTicks(0);
                        hit(player, entity, amount, release);
                    }
                }
            }
        }.runTaskTimer(plugin, 6L, period);
    }

    /** Leap high, hang for a moment, then dive into a crushing shockwave. No fall damage. */
    private boolean skyfall(Player player, Release release) {
        Vector forward = horizontal(player);
        player.setVelocity(forward.clone().multiply(0.45).setY(1.25));
        Location start = player.getLocation();
        Vfx.ring(start.clone().add(0, 0.1, 0), 1.3, 18, release.form.color(), 1.4f);
        Vfx.sound(start, Sound.ENTITY_BREEZE_JUMP, 1.0f, 0.8f);
        castSound(start, release.theme, 0.7f);
        new BukkitRunnable() {
            int tick;
            boolean diving;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }
                tick++;
                player.setFallDistance(0);
                Location at = player.getLocation();
                Vfx.burst(at.clone().add(0, 1, 0), release.form.color(), 4, 0.3, 1.3f);
                at.getWorld().spawnParticle(release.theme.accent, at, 2, 0.2, 0.2, 0.2, 0.01);
                if (!diving && tick >= 9) {
                    diving = true;
                    player.setVelocity(horizontal(player).multiply(0.6).setY(-1.9));
                    Vfx.sound(at, Sound.ENTITY_PHANTOM_SWOOP, 1.0f, 0.8f);
                }
                boolean landed = at.clone().subtract(0, 0.15, 0).getBlock().getType().isSolid();
                if (diving && (landed || tick > 50)) {
                    groundImpact(player, release, at, 4.5, 7.0, 0.8);
                    Vfx.shockwave(plugin, at, release.form.color(), 5.0, 6);
                    at.getWorld().spawnParticle(Particle.EXPLOSION, at, 2, 0.5, 0.1, 0.5, 0);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** A line of pillars (block displays, no world edits) erupts forward and launches enemies. */
    private boolean pillars(Player player, Release release) {
        Vector forward = horizontal(player);
        Location origin = player.getLocation();
        BlockData block = release.theme.pillar.createBlockData();
        Set<UUID> hit = new HashSet<>();
        castSound(origin, release.theme, 0.6f);
        new BukkitRunnable() {
            int step;

            @Override
            public void run() {
                if (!player.isOnline() || step >= 9) {
                    cancel();
                    return;
                }
                step++;
                Location point = origin.clone().add(forward.clone().multiply(1.2 + step * 1.1));
                Location ground = groundAt(point);
                if (ground == null) {
                    cancel();
                    return;
                }
                raisePillar(ground, block, 1.6 + step * 0.12);
                ground.getWorld().spawnParticle(Particle.BLOCK, ground.clone().add(0, 0.2, 0), 14, 0.4, 0.2, 0.4, 0.1,
                        block);
                ground.getWorld().spawnParticle(release.theme.accent, ground.clone().add(0, 1, 0), 4, 0.2, 0.6, 0.2, 0.02);
                Vfx.sound(ground, Sound.BLOCK_POINTED_DRIPSTONE_LAND, 1.0f, 0.6f + step * 0.05f);
                for (LivingEntity entity : ground.getNearbyLivingEntities(1.5, 2.5)) {
                    if (hit.add(entity.getUniqueId()) && service.canStrike(player, entity)) {
                        hit(player, entity, 6.0, release);
                        entity.setVelocity(new Vector(0, 0.85, 0).add(forward.clone().multiply(0.2)));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
        return true;
    }

    private void raisePillar(Location ground, BlockData block, double height) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location base = ground.clone();
        base.setYaw(0);
        base.setPitch(0);
        Quaternionf tilt = new Quaternionf().rotateXYZ((float) random.nextDouble(-0.25, 0.25), 0f,
                (float) random.nextDouble(-0.25, 0.25));
        BlockDisplay display = base.getWorld().spawn(base, BlockDisplay.class, d -> {
            d.setBlock(block);
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(-0.35f, -0.2f, -0.35f), tilt,
                    new Vector3f(0.7f, 0.05f, 0.7f), new Quaternionf()));
        });
        displays.add(display);
        var scheduler = plugin.getServer().getScheduler();
        scheduler.runTaskLater(plugin, () -> {
            if (display.isValid()) {
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(3);
                display.setTransformation(new Transformation(new Vector3f(-0.35f, -0.2f, -0.35f), tilt,
                        new Vector3f(0.7f, (float) height, 0.7f), new Quaternionf()));
            }
        }, 1L);
        scheduler.runTaskLater(plugin, () -> {
            if (display.isValid()) {
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(6);
                display.setTransformation(new Transformation(new Vector3f(-0.35f, -0.2f, -0.35f), tilt,
                        new Vector3f(0.7f, 0.05f, 0.7f), new Quaternionf()));
            }
        }, 28L);
        scheduler.runTaskLater(plugin, () -> {
            display.remove();
            displays.remove(display);
        }, 35L);
    }

    /** Bind every enemy nearby with spirit chains, dragging them in and rooting them briefly. */
    private boolean chains(Player player, Release release, String name) {
        Location center = player.getLocation();
        Location chest = center.clone().add(0, 1.1, 0);
        int bound = 0;
        for (LivingEntity entity : center.getNearbyLivingEntities(7)) {
            if (!service.canStrike(player, entity) || !player.hasLineOfSight(entity)) {
                continue;
            }
            bound++;
            Location at = entity.getLocation().add(0, entity.getHeight() * 0.5, 0);
            Vfx.line(chest, at, release.form.color(), 1.0f, 0.3);
            Vfx.line(chest.clone().add(0, 0.12, 0), at.clone().add(0, 0.12, 0), Color.fromRGB(80, 80, 90), 0.7f, 0.45);
            hit(player, entity, 3.0, release);
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2));
            Vector pull = center.toVector().subtract(entity.getLocation().toVector());
            double distance = pull.length();
            if (distance > 1.5) {
                entity.setVelocity(pull.normalize().multiply(Math.min(1.3, distance * 0.22)).setY(0.25));
            }
        }
        if (bound == 0) {
            player.sendActionBar(Component.text(name + " needs an enemy in sight within 7 blocks.", NamedTextColor.GRAY));
            return false;
        }
        Vfx.sound(center, Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.6f);
        Vfx.sound(center, Sound.BLOCK_CHAIN_HIT, 1.0f, 0.8f);
        castSound(center, release.theme, 0.8f);
        Vfx.ring(center.clone().add(0, 0.1, 0), 1.0, 14, release.form.color(), 1.2f);
        return true;
    }

    // ---------------------------------------------------------------- advanced: entrance and aura

    /** The advanced transformation: a two-second eruption of reiatsu with a faction-specific signature. */
    private void advancedEntrance(Player player, Release release) {
        Location loc = player.getLocation();
        Color dark = Color.fromRGB(18, 12, 22);
        switch (release.theme) {
            case SOUL_REAPER -> {
                Vfx.sound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.6f);
                Vfx.sound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.4f);
                Vfx.sound(loc, Sound.ITEM_TRIDENT_THUNDER, 1.0f, 0.8f);
            }
            case HOLLOW -> {
                Vfx.sound(loc, Sound.ENTITY_WARDEN_EMERGE, 1.0f, 1.2f);
                Vfx.sound(loc, Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.2f);
            }
            case QUINCY -> {
                Vfx.sound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
                Vfx.sound(loc, Sound.BLOCK_CONDUIT_ACTIVATE, 1.0f, 1.0f);
                Vfx.sound(loc, Sound.ITEM_TRIDENT_THUNDER, 0.8f, 1.4f);
            }
            case FULLBRINGER -> {
                Vfx.sound(loc, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.0f, 0.8f);
                Vfx.sound(loc, Sound.ITEM_TOTEM_USE, 0.8f, 1.2f);
                Vfx.sound(loc, Sound.BLOCK_ANVIL_LAND, 0.7f, 0.6f);
            }
        }
        // Spiritual pressure: enemies are thrown back, and enemy players are briefly swallowed by darkness.
        for (LivingEntity entity : loc.getNearbyLivingEntities(8)) {
            if (service.canStrike(player, entity)) {
                entity.setVelocity(outward(loc, entity).multiply(1.2).setY(0.45));
                if (entity instanceof Player) {
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0));
                }
            }
        }
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || tick > 40) {
                    cancel();
                    return;
                }
                Location base = player.getLocation();
                var world = base.getWorld();
                // A pillar of reiatsu, coloured with a dark core, that grows to ten blocks.
                double top = Math.min(10, tick * 0.5);
                for (double h = 0; h <= top; h += 0.7) {
                    double a = tick * 0.4 + h;
                    world.spawnParticle(Particle.DUST, base.clone().add(Math.cos(a) * 0.7, h, Math.sin(a) * 0.7), 1,
                            0.05, 0.05, 0.05, 0, release.form.dust(1.8f));
                    if (release.theme != Theme.QUINCY) {
                        world.spawnParticle(Particle.DUST, base.clone().add(-Math.cos(a) * 0.4, h, -Math.sin(a) * 0.4), 1,
                                0.05, 0.05, 0.05, 0, Vfx.dust(dark, 1.6f));
                    }
                }
                world.spawnParticle(release.theme.accent, base.clone().add(0, 1, 0), 6, 0.6, 1.0, 0.6, 0.05);
                if (tick == 0) {
                    // A bolt of light from the sky, drawn with particles so it is not heard server-wide.
                    Vfx.line(base.clone().add(0, 18, 0), base, release.theme == Theme.HOLLOW ? dark : Color.WHITE, 1.6f, 0.4);
                    world.spawnParticle(Particle.SONIC_BOOM, base.clone().add(0, 1, 0), 1);
                }
                if (tick == 20) {
                    Vfx.shockwave(plugin, base, release.form.color(), 9.0, 12);
                    Vfx.burst(base.clone().add(0, 1, 0), release.form.color(), 80, 1.2, 2.0f);
                    world.spawnParticle(Particle.EXPLOSION, base.clone().add(0, 1, 0), 3, 0.8, 0.5, 0.8, 0);
                    Vfx.sound(base, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.7f);
                    if (release.theme == Theme.FULLBRINGER) {
                        world.spawnParticle(Particle.TOTEM_OF_UNDYING, base.clone().add(0, 1, 0), 80, 0.6, 0.8, 0.6, 0.6);
                    } else if (release.theme == Theme.HOLLOW) {
                        world.spawnParticle(Particle.SCULK_SOUL, base.clone().add(0, 1, 0), 30, 1.0, 1.0, 1.0, 0.08);
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Extra aura while an advanced form is out: wings, horns, armour plates, or dark rising reiatsu. */
    private static void advancedAura(Player player, Release release, int tick) {
        Location base = player.getLocation();
        var world = base.getWorld();
        double yaw = Math.toRadians(base.getYaw());
        Vector forward = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        Vector right = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
        switch (release.theme) {
            case QUINCY -> {
                if (tick % 4 != 0) {
                    return;
                }
                // Two flapping wings of light behind the shoulders.
                double flap = 1 - 0.18 * Math.sin(tick * 0.25);
                for (int side = -1; side <= 1; side += 2) {
                    for (int row = 0; row < 2; row++) {
                        int points = row == 0 ? 10 : 7;
                        for (int i = 0; i < points; i++) {
                            double t = i / (double) (points - 1);
                            Location point = base.clone().add(0, 1.35 - row * 0.35 + Math.sin(t * Math.PI) * 0.75 - t * 0.35, 0)
                                    .add(forward.clone().multiply(-(0.35 + t * 0.25)))
                                    .add(right.clone().multiply(side * (0.25 + t * (row == 0 ? 1.7 : 1.2) * flap)));
                            world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, release.form.dust(1.0f));
                        }
                    }
                    if (tick % 12 == 0) {
                        world.spawnParticle(Particle.END_ROD, base.clone().add(0, 1.2, 0)
                                .add(right.clone().multiply(side * 1.9 * flap)), 1, 0, 0, 0, 0);
                    }
                }
            }
            case SOUL_REAPER -> {
                // Black-and-colour reiatsu rising off the body like flame.
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int i = 0; i < 3; i++) {
                    double a = random.nextDouble(Math.PI * 2);
                    Location point = base.clone().add(Math.cos(a) * 0.5, random.nextDouble(0, 2.0), Math.sin(a) * 0.5);
                    world.spawnParticle(Particle.DUST, point, 1, 0, 0.1, 0, 0,
                            Vfx.dust(i == 0 ? release.form.color() : Color.fromRGB(20, 20, 25), 1.3f));
                }
                if (tick % 10 == 0) {
                    world.spawnParticle(Particle.ELECTRIC_SPARK, base.clone().add(0, 1, 0), 4, 0.4, 0.6, 0.4, 0.03);
                }
            }
            case HOLLOW -> {
                if (tick % 4 == 0) {
                    // Bone horns above the mask.
                    Color bone = Color.fromRGB(235, 228, 205);
                    Location head = base.clone().add(0, 1.85, 0);
                    for (int side = -1; side <= 1; side += 2) {
                        Vfx.line(head.clone().add(right.clone().multiply(side * 0.18)),
                                head.clone().add(right.clone().multiply(side * 0.38)).add(forward.clone().multiply(0.15))
                                        .add(0, 0.5, 0), bone, 0.6f, 0.1);
                    }
                }
                if (tick % 10 == 0) {
                    Vfx.ring(base.clone().add(0, 0.1, 0), 1.0, 10, Particle.SOUL);
                    world.spawnParticle(Particle.LARGE_SMOKE, base.clone().add(0, 0.3, 0), 2, 0.4, 0.1, 0.4, 0.01);
                }
            }
            case FULLBRINGER -> {
                // Four armour plates in the form's colour orbiting the body.
                for (int plate = 0; plate < 4; plate++) {
                    double a = tick * 0.15 + plate * (Math.PI / 2);
                    for (double h = -0.5; h <= 0.51; h += 0.5) {
                        world.spawnParticle(Particle.DUST, base.clone().add(Math.cos(a) * 1.1, 1.0 + h, Math.sin(a) * 1.1),
                                1, 0, 0, 0, 0, release.form.dust(1.1f));
                    }
                }
                if (tick % 10 == 0) {
                    world.spawnParticle(Particle.ENCHANT, base.clone().add(0, 1.2, 0), 10, 0.5, 0.6, 0.5, 0.5);
                }
            }
        }
    }

    // ---------------------------------------------------------------- advanced: ultimates

    /** Fires the advanced form's ultimate, once per release. */
    void ultimate(Player player) {
        Release release = active.get(player.getUniqueId());
        if (release == null || !release.advanced) {
            return;
        }
        String name = ultimateName(release.faction, release.style);
        if (release.ultimateUsed) {
            player.sendActionBar(Component.text(name + " is spent for this release. Sneak + F to seal.",
                    NamedTextColor.GRAY));
            return;
        }
        boolean used = switch (release.style) {
            case VANGUARD -> cataclysm(player, release);
            case MIRAGE -> phantomExecution(player, release, name);
            case MARKSMAN -> starfall(player, release);
            case WAYFARER -> tempest(player, release);
            case ARTIFICER -> worldBreaker(player, release);
            case KEEPER -> sanctum(player, release);
        };
        if (!used) {
            return;
        }
        release.ultimateUsed = true;
        Location loc = player.getLocation();
        player.showTitle(Title.title(Component.text(name, release.form.text(), TextDecoration.BOLD),
                Component.text(release.form.name(), NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1200), Duration.ofMillis(400))));
        Component callout = Component.text(player.getName() + ": ", NamedTextColor.GRAY)
                .append(Component.text("\"" + name + "!\"", release.form.text(), TextDecoration.BOLD));
        for (Player nearby : loc.getNearbyPlayers(48)) {
            nearby.sendMessage(callout);
        }
        Vfx.sound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
        loc.getWorld().spawnParticle(Particle.SONIC_BOOM, loc.clone().add(0, 1, 0), 1);
    }

    /** Two volleys of three giant waves in a fan. */
    private boolean cataclysm(Player player, Release release) {
        Vector aim = player.getEyeLocation().getDirection();
        for (int volley = 0; volley < 2; volley++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                waveSound(player.getEyeLocation(), release.theme);
                for (int fan = -1; fan <= 1; fan++) {
                    launchWave(player, release, aim.clone().rotateAroundY(Math.toRadians(fan * 22)), 3.0, 9.0);
                }
            }, volley * 10L);
        }
        return true;
    }

    /** Blink behind up to five enemies in turn, striking each, then return to where it began. */
    private boolean phantomExecution(Player player, Release release, String name) {
        Location home = player.getLocation();
        java.util.List<LivingEntity> targets = new java.util.ArrayList<>();
        for (LivingEntity entity : home.getNearbyLivingEntities(12)) {
            if (service.canStrike(player, entity) && player.hasLineOfSight(entity)) {
                targets.add(entity);
            }
        }
        if (targets.isEmpty()) {
            player.sendActionBar(Component.text(name + " needs an enemy in sight within 12 blocks.", NamedTextColor.GRAY));
            return false;
        }
        targets.sort(java.util.Comparator.comparingDouble(e -> e.getLocation().distanceSquared(home)));
        java.util.List<LivingEntity> marks = targets.subList(0, Math.min(5, targets.size()));
        afterimage(home.clone().add(0, 1, 0), release);
        blinkSound(home, release.theme);
        new BukkitRunnable() {
            int index;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }
                if (index >= marks.size()) {
                    afterimage(player.getLocation().add(0, 1, 0), release);
                    player.teleport(home);
                    blinkSound(home, release.theme);
                    Vfx.burst(home.clone().add(0, 1, 0), release.form.color(), 40, 0.5, 1.6f);
                    cancel();
                    return;
                }
                LivingEntity target = marks.get(index++);
                if (!target.isValid() || target.isDead()) {
                    return;
                }
                Vector facing = target.getLocation().getDirection().setY(0);
                if (facing.lengthSquared() < 0.01) {
                    facing = new Vector(0, 0, 1);
                }
                Location behind = target.getLocation().subtract(facing.normalize().multiply(1.3));
                Location from = player.getLocation().add(0, 1, 0);
                if (standable(behind)) {
                    behind.setDirection(target.getLocation().toVector().subtract(behind.toVector()));
                    Vfx.line(from, behind.clone().add(0, 1, 0), release.form.color(), 0.8f, 0.6);
                    afterimage(from, release);
                    player.teleport(behind);
                }
                target.setNoDamageTicks(0);
                hit(player, target, 7.0, release);
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
                Location at = target.getLocation().add(0, target.getHeight() * 0.55, 0);
                Vfx.arc(at, at.toVector().subtract(player.getLocation().toVector()).setY(0), 1.6,
                        release.form.color(), 1.6f, 0.2);
                at.getWorld().spawnParticle(Particle.SWEEP_ATTACK, at, 2, 0.3, 0.2, 0.3, 0);
                Vfx.sound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.7f + index * 0.1f);
            }
        }.runTaskTimer(plugin, 3L, 5L);
        return true;
    }

    /** A wide storm of bolts on the aimed area. */
    private boolean starfall(Player player, Release release) {
        Location target = aimPoint(player, 30);
        Vfx.sound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.0f, 0.8f);
        Vfx.shockwave(plugin, target, release.form.color(), 5.5, 10);
        storm(player, release, target, 5.5, 26, 4.0, 4, 2L);
        return true;
    }

    /** A tornado around the caster that drags enemies in a spiral, then hurls them skyward. */
    private boolean tempest(Player player, Release release) {
        Vfx.sound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 0.5f);
        Vfx.sound(player.getLocation(), Sound.ITEM_ELYTRA_FLYING, 0.6f, 1.2f);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }
                Location center = player.getLocation();
                var world = center.getWorld();
                for (int k = 0; k < 12; k++) {
                    double h = k * 0.5;
                    double r = 0.6 + h * 0.4;
                    double a = tick * 0.5 + k * 0.7;
                    Location point = center.clone().add(Math.cos(a) * r, h, Math.sin(a) * r);
                    world.spawnParticle(Particle.CLOUD, point, 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.DUST, point, 1, 0.05, 0.05, 0.05, 0, release.form.dust(1.4f));
                }
                if (tick >= 60) {
                    for (LivingEntity entity : center.getNearbyLivingEntities(8)) {
                        if (service.canStrike(player, entity)) {
                            entity.setNoDamageTicks(0);
                            hit(player, entity, 4.0, release);
                            entity.setVelocity(new Vector(0, 1.3, 0));
                        }
                    }
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 80, 0, false, false, true));
                    Vfx.sound(center, Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 0.7f);
                    Vfx.burst(center.clone().add(0, 2, 0), release.form.color(), 60, 1.5, 1.8f);
                    cancel();
                    return;
                }
                for (LivingEntity entity : center.getNearbyLivingEntities(8)) {
                    if (!service.canStrike(player, entity)) {
                        continue;
                    }
                    Vector toCenter = center.toVector().subtract(entity.getLocation().toVector()).setY(0);
                    if (toCenter.lengthSquared() < 0.01) {
                        continue;
                    }
                    Vector inward = toCenter.clone().normalize();
                    Vector around = new Vector(-inward.getZ(), 0, inward.getX());
                    entity.setVelocity(inward.multiply(0.25).add(around.multiply(0.4)).setY(0.08));
                    if (tick % 10 == 0) {
                        entity.setNoDamageTicks(0);
                        hit(player, entity, 2.0, release);
                    }
                }
                if (tick % 10 == 0) {
                    Vfx.sound(center, Sound.ENTITY_BREEZE_IDLE_AIR, 1.0f, 0.6f);
                }
                tick += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
        return true;
    }

    /** Three rings of pillars erupt outward from the caster. */
    private boolean worldBreaker(Player player, Release release) {
        Location center = player.getLocation();
        BlockData block = release.theme.pillar.createBlockData();
        Set<UUID> hit = new HashSet<>();
        double[] radii = {2.5, 4.5, 6.5};
        for (int ring = 0; ring < radii.length; ring++) {
            double radius = radii[ring];
            double height = 2.0 + ring * 0.6;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Vfx.sound(center, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.0f, 0.6f);
                Vfx.sound(center, Sound.BLOCK_POINTED_DRIPSTONE_LAND, 1.0f, 0.5f);
                int count = (int) (radius * 2.2);
                for (int i = 0; i < count; i++) {
                    double a = i * (Math.PI * 2 / count);
                    Location ground = groundAt(center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius));
                    if (ground == null) {
                        continue;
                    }
                    raisePillar(ground, block, height);
                    ground.getWorld().spawnParticle(Particle.BLOCK, ground.clone().add(0, 0.2, 0), 10, 0.4, 0.2, 0.4,
                            0.1, block);
                    for (LivingEntity entity : ground.getNearbyLivingEntities(1.7, 2.5)) {
                        if (hit.add(entity.getUniqueId()) && service.canStrike(player, entity)) {
                            hit(player, entity, 7.0, release);
                            entity.setVelocity(outward(center, entity).multiply(0.3).setY(1.0));
                        }
                    }
                }
                Vfx.ring(center.clone().add(0, 0.2, 0), radius, (int) (radius * 8), release.form.color(), 1.4f);
            }, ring * 6L);
        }
        return true;
    }

    /** An eight-second zone: allies inside regenerate and resist, enemies are slowed and worn down. */
    private boolean sanctum(Player player, Release release) {
        Location center = player.getLocation();
        Vfx.sound(center, Sound.BLOCK_BELL_USE, 1.0f, 0.8f);
        Vfx.sound(center, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.8f);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || tick > 160) {
                    cancel();
                    return;
                }
                for (int layer = 0; layer < 4; layer++) {
                    double h = layer * 1.2;
                    double r = Math.sqrt(Math.max(0.5, 36 - h * h * 2.2));
                    Vfx.ring(center.clone().add(0, 0.2 + h, 0), r, (int) (r * 6), release.form.color(), 1.2f);
                }
                center.getWorld().spawnParticle(release.theme.accent, center.clone().add(0, 1, 0), 10, 3, 1.5, 3, 0.02);
                for (Player ally : center.getNearbyPlayers(6)) {
                    if (ally.equals(player) || (AwakeningService.canPlay(ally) && AwakeningPowers.allies(player, ally))) {
                        ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 30, 1, false, true, true));
                        ally.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30, 0, false, true, true));
                    }
                }
                for (LivingEntity entity : center.getNearbyLivingEntities(6)) {
                    if (service.canStrike(player, entity)) {
                        entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1));
                        if (tick % 20 == 0) {
                            entity.setNoDamageTicks(0);
                            hit(player, entity, 2.0, release);
                        }
                    }
                }
                if (tick % 40 == 0) {
                    Vfx.sound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.8f);
                }
                tick += 10;
            }
        }.runTaskTimer(plugin, 0L, 10L);
        return true;
    }

    // ---------------------------------------------------------------- helpers

    private static Vector horizontal(Player player) {
        Vector forward = player.getLocation().getDirection().setY(0);
        return forward.lengthSquared() < 0.01 ? new Vector(0, 0, 1) : forward.normalize();
    }

    /** The standing spot on the ground at or just below/above a point, or null if there is none nearby. */
    private static Location groundAt(Location point) {
        Block block = point.getBlock();
        for (int dy = 2; dy >= -4; dy--) {
            Block candidate = block.getRelative(0, dy, 0);
            if (candidate.getType().isSolid() && candidate.getRelative(0, 1, 0).isPassable()) {
                return candidate.getLocation().add(0.5, 1, 0.5);
            }
        }
        return null;
    }

    private static Vector outward(Location from, LivingEntity entity) {
        Vector out = entity.getLocation().toVector().subtract(from.toVector()).setY(0);
        return out.lengthSquared() < 0.01 ? new Vector(0, 0, 1) : out.normalize();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        seal(event.getPlayer(), false);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        seal(event.getEntity(), false);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        seal(event.getPlayer(), true);
    }
}
