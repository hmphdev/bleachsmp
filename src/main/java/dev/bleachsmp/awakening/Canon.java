package dev.bleachsmp.awakening;

import dev.bleachsmp.awakening.ReleaseService.Mechanic;
import dev.bleachsmp.faction.Faction;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;

/**
 * The real roster: every released form is a zanpakutō, resurrección, Schrift or Fullbring from the series. A player's
 * first-form playstyle picks one from that playstyle's pool (stable per player), and their advanced form is the same
 * weapon's Bankai, Segunda Etapa, Vollständig or Complete Fullbring. Q is the form's signature ability; F in the
 * advanced form is the advanced form's defining ability.
 */
final class Canon {
    /** What a signature or advanced ability does. Legacy entries reuse an existing implementation. */
    enum Move {
        // Existing signature implementations, by their old table key.
        FERAL_HUNT(100), THOUSAND_PETALS(11), METEOR_LANCE(2), SONIDO_STORM(103), THUNDER_STEP(13), BONE_CAGE(114),
        IRON_CONSTRUCT(304), SKY_STEPS(303), UPDRAFT(3), REWIND(310),
        // Existing advanced ultimates.
        U_CATACLYSM, U_PHANTOM, U_STARFALL, U_TEMPEST, U_WORLD_BREAKER, U_SANCTUM,
        // Soul Reaper
        JUJISHO, TENSA, JOKAKU_ENJO, ZANKA, BERSERKER, KANZEN_SAIMIN, MUGEN_KYOKA, IROONI, KARAMATSU,
        SAKANADE, HAPPO_FUSAGARI, HYOTEN, DAIGUREN, KAGEYOSHI, KAMISHINI, TSUKISHIRO, HAKKA, HIGA_ZEKKO,
        HIKOTSU, HAINEKO, ASH_STORM, NIGEKI, JAKUHO, TENKEN, MYOO, WABISUKE, WEIGHT_FIELD, TACHIKAZE,
        TEKKEN, MINAZUKI, MINAZUKI_BANKAI, SOGYO, KAMIKAKE, ASHISOGI, KONJIKI,
        // Hollow
        DESGARRON, IRA, FURIA, HIERRO, SIX_ARMS, AMOR, AMOR_MASS, DEVOUR, BANQUET, VOODOO, GABRIEL,
        LUZ_LANZA, LANZA_RELAMPAGO, LOBOS, METRALLETA, CASCADA, GRAN_CAIDA, REMOLINO, GOLONDRINA, BULL_CHARGE,
        RESPIRA, SENESCENCIA, DRAGON_RUGIR, CERO_DOBLE, LANZADOR, TREPADORA, TENTACLE_STORM, TRES_BESTIAS,
        // Quincy
        BURNER3, BURNER4, OVERKILL, KILL_COUNT, POWER_PUNCH, POWER_QUAKE, FEAR, TATARFORAS, VISIONARY, METEOR,
        LOVE_KISS, LOVE_RAIN, X_AXIS, JILLIEL, ANTITHESIS, SPRENGER, GALVANO_BLAST, GALVANO_JAVELIN, WIND,
        WIND_STORM, STAR_CHEER, STAR_ROCKET, EXPLODE, EXPLODE_BARRAGE, EISERN, MIRACLE, HOHEIT, GLUTTON_MAW,
        BISKIEL, BALANCE, AVARON, ZOMBIE, ZOMBIE_ARMY,
        // Fullbringer
        JACKPOT, TRIPLE_SEVEN, EL_DIRECTO, GIGANTE, INSERT, REWRITE, LA_MUERTE, CROSS_STEAL, CROSS_GETSUGA,
        DIRTY_BOOTS, FILTHY_STOMP, DOLLHOUSE, BRING_IN, INVADERS, FINAL_STAGE, SOTEN, SHITEN, TIME_BET, TIME_LIMIT;

        /** The old signature table key this reuses, or -1. */
        final int legacy;

        Move() {
            this(-1);
        }

        Move(int legacy) {
            this.legacy = legacy;
        }
    }

    record Kit(String owner, Faction faction, Playstyle style, int index, String name, String command,
            String advancedName, String advancedCommand, Color color, TextColor text, BossBar.Color bar,
            Element element, Mechanic main, String mainName, Mechanic second, String secondName,
            Move signature, String signatureName, String signatureText,
            Move special, String specialName, String specialText) {
    }

    private static final Map<Faction, Map<Playstyle, List<Kit>>> ROSTER = new EnumMap<>(Faction.class);

    private Canon() {
    }

    static List<Kit> pool(Faction faction, Playstyle style) {
        return ROSTER.get(faction).get(style);
    }

    static List<Kit> all() {
        List<Kit> list = new ArrayList<>();
        for (var styles : ROSTER.values()) {
            for (var kits : styles.values()) {
                list.addAll(kits);
            }
        }
        return list;
    }

    /** Forms set by an admin for testing (player → form name). They win over the rolled form. */
    static final Map<UUID, String> OVERRIDES = new java.util.concurrent.ConcurrentHashMap<>();

    /** A player's kit for a playstyle: stable per player, spread evenly across the pool. */
    static Kit kit(UUID id, Faction faction, Playstyle style) {
        String override = OVERRIDES.get(id);
        if (override != null) {
            for (Kit kit : all()) {
                if (kit.faction() == faction && kit.name().equals(override)) {
                    return kit;
                }
            }
        }
        List<Kit> pool = pool(faction, style);
        return pool.get((int) Math.floorMod(ReleaseService.hash(id, style.ordinal() + 1), (long) pool.size()));
    }

    static String normalize(String text) {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Finds a form by its name, advanced name or owner, ignoring accents, case and punctuation. */
    static List<Kit> find(String query) {
        String q = normalize(query);
        List<Kit> exact = new ArrayList<>();
        List<Kit> partial = new ArrayList<>();
        for (Kit kit : all()) {
            String name = normalize(kit.name());
            if (name.equals(q) || normalize(kit.advancedName()).equals(q)) {
                exact.add(kit);
            } else if (!q.isEmpty() && (name.contains(q) || normalize(kit.owner()).contains(q)
                    || normalize(kit.advancedName()).contains(q))) {
                partial.add(kit);
            }
        }
        return exact.isEmpty() ? partial : exact;
    }

    private static TextColor readable(int rgb) {
        int r = rgb >> 16 & 0xff;
        int g = rgb >> 8 & 0xff;
        int b = rgb & 0xff;
        double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
        if (luminance < 110) {
            r = (r + 255 * 2) / 3;
            g = (g + 255 * 2) / 3;
            b = (b + 255 * 2) / 3;
        }
        return TextColor.color(r, g, b);
    }

    private static void add(Faction faction, Playstyle style, String owner, String name, String command,
            String advancedName, String advancedCommand, int rgb, BossBar.Color bar, Element element,
            Mechanic main, String mainName, Mechanic second, String secondName,
            Move signature, String signatureName, String signatureText,
            Move special, String specialName, String specialText) {
        List<Kit> list = ROSTER.computeIfAbsent(faction, f -> new EnumMap<>(Playstyle.class))
                .computeIfAbsent(style, s -> new ArrayList<>());
        list.add(new Kit(owner, faction, style, list.size(), name, command, advancedName, advancedCommand,
                Color.fromRGB(rgb), readable(rgb), bar, element, main, mainName, second, secondName,
                signature, signatureName, signatureText, special, specialName, specialText));
    }

    static {
        Faction f = Faction.SOUL_REAPER;
        // ------------------------------------------------------------ Soul Reaper zanpakutō
        add(f, Playstyle.VANGUARD, "Ichigo Kurosaki", "Zangetsu", "Zangetsu!",
                "Tensa Zangetsu", "Bankai! Tensa Zangetsu!", 0x3a1a22, BossBar.Color.RED, Element.SHADOW,
                Mechanic.WAVE, "Getsuga Tenshō", Mechanic.DASH, "Shunpo Cut",
                Move.JUJISHO, "Getsuga Jūjishō", "two crossing Getsuga Tenshō fired as one cross-shaped slash",
                Move.TENSA, "Kuroi Getsuga", "a giant black Getsuga Tenshō, then Bankai speed: every melee hit throws a black getsuga through your target");
        add(f, Playstyle.VANGUARD, "Genryūsai Yamamoto", "Ryūjin Jakka", "Reduce all creation to ash, Ryūjin Jakka!",
                "Zanka no Tachi", "Bankai! Zanka no Tachi!", 0xff5a14, BossBar.Color.RED, Element.FIRE,
                Mechanic.WAVE, "Taimatsu", Mechanic.PILLARS, "Ennetsu Jigoku",
                Move.JOKAKU_ENJO, "Jōkaku Enjō", "a ring of fire walls traps everyone near you; touching the wall burns",
                Move.ZANKA, "Zanka no Tachi", "Higashi: one cut that erases everything in a line — then Nishi: a 15-million-degree aura that scorches everything near you");
        add(f, Playstyle.VANGUARD, "Kenpachi Zaraki", "Nozarashi", "Drink, Nozarashi!",
                "Nozarashi (Bankai)", "Bankai!", 0x9a0f1a, BossBar.Color.RED, Element.BLOOD,
                Mechanic.WAVE, "Nozarashi Cleave", Mechanic.FLURRY, "Kendō Rampage",
                Move.FERAL_HUNT, "Eyepatch Off", "tear off the eyepatch: pay health to frenzy; every melee hit heals and stacks strength",
                Move.BERSERKER, "Demon Form", "become a red demon: unstoppable, every swing cleaves everything in front and hits harder the longer you fight");
        add(f, Playstyle.MIRAGE, "Sōsuke Aizen", "Kyōka Suigetsu", "Shatter, Kyōka Suigetsu.",
                "Kyōka Suigetsu: Kanzen Saimin", "It's an illusion. All of it.", 0x8c5ad2, BossBar.Color.PURPLE, Element.WATER,
                Mechanic.BLINK, "Illusory Strike", Mechanic.SCATTER, "Mirror Flower",
                Move.KANZEN_SAIMIN, "Kanzen Saimin", "complete hypnosis: everyone who saw you release loses sight of you and sees you standing still; your first hit on each lands hard",
                Move.MUGEN_KYOKA, "Mugen Kyōka", "(Aizen never revealed a Bankai) every hypnotised enemy strikes their own side: their attacks land on whoever is nearest them, never you");
        add(f, Playstyle.MIRAGE, "Shunsui Kyōraku", "Katen Kyōkotsu", "Flower Wind Rage and Flower God Roar, Heavenly Wind Rage and Heavenly Demon Sneer — Katen Kyōkotsu!",
                "Katen Kyōkotsu: Karamatsu Shinjū", "Bankai. Katen Kyōkotsu: Karamatsu Shinjū.", 0xe678aa, BossBar.Color.PINK, Element.SHADOW,
                Mechanic.BLINK, "Kageoni", Mechanic.FLURRY, "Takaoni",
                Move.IROONI, "Irooni", "call a colour at an enemy: you both take the colour's damage multiplier from each other — black hits hardest",
                Move.KARAMATSU, "Karamatsu Shinjū", "a tragic play in four acts on one enemy: shared wounds, the pool of shame, the binding thread, and the final beheading");
        add(f, Playstyle.MIRAGE, "Shinji Hirako", "Sakanade", "Collapse, Sakanade.",
                "Sakashima Yokoshima Happō Fusagari", "Bankai. Sakashima Yokoshima Happō Fusagari.", 0xeee68c, BossBar.Color.YELLOW, Element.SOUND,
                Mechanic.SCATTER, "Inverted Haze", Mechanic.BLINK, "Reversed Strike",
                Move.SAKANADE, "Inverted World", "up is down, left is right: nearby enemies spin around and half their swings at you miss",
                Move.HAPPO_FUSAGARI, "Happō Fusagari", "everyone around you loses friend from foe: their hits land on whoever stands near them");
        add(f, Playstyle.MARKSMAN, "Tōshirō Hitsugaya", "Hyōrinmaru", "Sit upon the frozen heavens, Hyōrinmaru!",
                "Daiguren Hyōrinmaru", "Bankai! Daiguren Hyōrinmaru!", 0x6ec8ff, BossBar.Color.BLUE, Element.ICE,
                Mechanic.BEAM, "Hyōryū", Mechanic.CHAINS, "Hyōrinmaru Chain",
                Move.HYOTEN, "Hyōten Hyakkasō", "open the sky over an area; every enemy the snow touches becomes an ice flower",
                Move.DAIGUREN, "Sennen Hyōrō", "ice wings carry you, and a ring of ice pillars closes on the area you aim at, crushing everything inside");
        add(f, Playstyle.MARKSMAN, "Byakuya Kuchiki", "Senbonzakura", "Scatter, Senbonzakura.",
                "Senbonzakura Kageyoshi", "Bankai. Senbonzakura Kageyoshi.", 0xff96be, BossBar.Color.PINK, Element.PETAL,
                Mechanic.WAVE, "Petal Torrent", Mechanic.WARD, "Danku",
                Move.THOUSAND_PETALS, "Senbonzakura", "steer a cloud of a thousand blade petals with your crosshair",
                Move.KAGEYOSHI, "Senkei", "rows of a thousand swords wall in everyone near you; blade petals cut everything trapped inside");
        add(f, Playstyle.MARKSMAN, "Gin Ichimaru", "Shinsō", "Shoot to kill, Shinsō.",
                "Kamishini no Yari", "Bankai. Kamishini no Yari.", 0xc8c8d7, BossBar.Color.WHITE, Element.POISON,
                Mechanic.BEAM, "Shinsō Thrust", Mechanic.FLURRY, "Butō Renjin",
                Move.METEOR_LANCE, "Full Extension", "charge, then extend the blade in a piercing thrust that hits harder the farther it reaches",
                Move.KAMISHINI, "Korose", "a 60-block thrust leaves a sliver of blade inside the first enemy hit; three seconds later it dissolves them from within");
        add(f, Playstyle.MARKSMAN, "Rukia Kuchiki", "Sode no Shirayuki", "Dance, Sode no Shirayuki.",
                "Hakka no Togame", "Bankai. Hakka no Togame.", 0xe6f0ff, BossBar.Color.WHITE, Element.ICE,
                Mechanic.BEAM, "Tsugi no Mai: Hakuren", Mechanic.PILLARS, "San no Mai: Shirafune",
                Move.TSUKISHIRO, "Some no Mai: Tsukishiro", "a circle opens under an enemy; everything inside freezes from the ground to the sky",
                Move.HAKKA, "Hakka no Togame", "drop to absolute zero: a white mist freezes enemies around you, and frozen enemies shatter when hit");
        add(f, Playstyle.WAYFARER, "Renji Abarai", "Zabimaru", "Howl, Zabimaru!",
                "Sōō Zabimaru", "Bankai! Sōō Zabimaru!", 0xc82828, BossBar.Color.RED, Element.FIRE,
                Mechanic.CHAINS, "Zabimaru Lash", Mechanic.DASH, "Snake Fang Rush",
                Move.HIGA_ZEKKO, "Higa Zekkō", "the blade's segments scatter around an enemy and all fang in at once",
                Move.HIKOTSU, "Hikotsu Taihō", "the bone snake opens its jaws and fires a huge reiatsu blast");
        add(f, Playstyle.WAYFARER, "Rangiku Matsumoto", "Haineko", "Growl, Haineko.",
                "Haineko (Bankai)", "Bankai.", 0xe6be8c, BossBar.Color.YELLOW, Element.WIND,
                Mechanic.SCATTER, "Ash Veil", Mechanic.DASH, "Shunpo",
                Move.HAINEKO, "Haineko", "the blade turns to ash that swirls around you, cutting; press Q again to fling it where you aim",
                Move.ASH_STORM, "Ash Storm", "(no Bankai revealed) a storm of blade ash follows you, cutting and blinding every enemy inside");
        add(f, Playstyle.WAYFARER, "Suì-Fēng", "Suzumebachi", "Sting all enemies to death, Suzumebachi.",
                "Jakuhō Raikōben", "Bankai. Jakuhō Raikōben.", 0xffc828, BossBar.Color.YELLOW, Element.LIGHTNING,
                Mechanic.DASH, "Shunpo Sting", Mechanic.FLURRY, "Shunkō",
                Move.NIGEKI, "Nigeki Kessatsu", "your first sting leaves the Hōmonka butterfly; a second sting on the same mark is death in two steps",
                Move.JAKUHO, "Jakuhō Raikōben", "brace, then fire a massive missile at where you aim; the recoil throws you back");
        add(f, Playstyle.ARTIFICER, "Sajin Komamura", "Tenken", "Roar, Tenken!",
                "Kokujō Tengen Myō'ō", "Bankai! Kokujō Tengen Myō'ō!", 0xaa6e3c, BossBar.Color.WHITE, Element.EARTH,
                Mechanic.SLAM, "Tenken Fist", Mechanic.PILLARS, "Earth Split",
                Move.TENKEN, "Tenken", "a giant armoured arm appears overhead and slams down on where you aim",
                Move.MYOO, "Myō'ō", "a towering armoured giant rises behind you, cutting your enemies for the rest of the release");
        add(f, Playstyle.ARTIFICER, "Izuru Kira", "Wabisuke", "Raise your head, Wabisuke.",
                "Wabisuke (Bankai)", "Bankai.", 0x6e6e8c, BossBar.Color.WHITE, Element.SHADOW,
                Mechanic.FLURRY, "Hook Blade", Mechanic.SLAM, "Downward Cut",
                Move.WABISUKE, "Wabisuke", "every hit doubles your target's weight until they're crushed to their knees with their head bowed for the final cut",
                Move.WEIGHT_FIELD, "Bowed Heads", "(no Bankai revealed) the weight of every past cut falls on everyone near you at once");
        add(f, Playstyle.ARTIFICER, "Kensei Muguruma", "Tachikaze", "Blow it away, Tachikaze.",
                "Tekken Tachikaze", "Bankai! Tekken Tachikaze!", 0x96e6aa, BossBar.Color.GREEN, Element.WIND,
                Mechanic.WAVE, "Wind Blade", Mechanic.SLAM, "Tachikaze Burst",
                Move.TACHIKAZE, "Tachikaze", "throw a wind blade that cuts and detonates in a blast of wind on whatever it touches",
                Move.TEKKEN, "Tekken Tachikaze", "steel knuckle dusters: every melee hit explodes and blasts enemies away");
        add(f, Playstyle.KEEPER, "Retsu Unohana", "Minazuki", "Minazuki.",
                "Minazuki (Bankai)", "Bankai. Minazuki.", 0x6ec8aa, BossBar.Color.GREEN, Element.WATER,
                Mechanic.WARD, "Healing Kidō", Mechanic.CHAINS, "Minazuki's Embrace",
                Move.MINAZUKI, "Minazuki", "the great ray swallows your allies' wounds: heals and cleanses everyone near you",
                Move.MINAZUKI_BANKAI, "Bankai: Minazuki", "a pool of blood that rots enemies and heals you and your allies; your blade heals you with every cut");
        add(f, Playstyle.KEEPER, "Jūshirō Ukitake", "Sōgyo no Kotowari", "All waves, rise and become my shield. Lightning, strike and become my blade. Sōgyo no Kotowari!",
                "Kamikake: Mimihagi", "Kamikake.", 0xe6f0ff, BossBar.Color.BLUE, Element.LIGHTNING,
                Mechanic.WARD, "Wave Shield", Mechanic.BEAM, "Returned Lightning",
                Move.SOGYO, "Sōgyo no Kotowari", "absorb the damage of incoming attacks for a few seconds, then return it as lightning around you",
                Move.KAMIKAKE, "Kamikake: Mimihagi", "(no Bankai revealed) Mimihagi stills everything: enemies near you lose their buffs and slow to a crawl; allies recover");
        add(f, Playstyle.KEEPER, "Mayuri Kurotsuchi", "Ashisogi Jizō", "Rip, Ashisogi Jizō.",
                "Konjiki Ashisogi Jizō", "Bankai. Konjiki Ashisogi Jizō.", 0xaa50c8, BossBar.Color.PURPLE, Element.POISON,
                Mechanic.CHAINS, "Ashisogi Hook", Mechanic.RAIN, "Poison Mist",
                Move.ASHISOGI, "Ashisogi Jizō", "each cut paralyses a little more: legs, then arms, then the whole body",
                Move.KONJIKI, "Konjiki Ashisogi Jizō", "the golden giant breathes a lethal poison cloud ahead of you; you carry the antidote");

        // ------------------------------------------------------------ Hollow resurrección
        f = Faction.HOLLOW;
        add(f, Playstyle.VANGUARD, "Grimmjow Jaegerjaquez", "Pantera", "Grind, Pantera!",
                "Pantera — Desgarrón", "Desgarrón!", 0x4696ff, BossBar.Color.BLUE, Element.LIGHTNING,
                Mechanic.FLURRY, "Pantera Claws", Mechanic.BEAM, "Garra de la Pantera",
                Move.SONIDO_STORM, "Pantera Pounce", "flash around a target, clawing it from every side",
                Move.DESGARRON, "Desgarrón", "ten giant claws of reiatsu, five from each hand, rake everything in front of you");
        add(f, Playstyle.VANGUARD, "Yammy Llargo", "Ira", "Rage, Ira!",
                "Ira — Furia", "Rage, Ira!", 0xbe3c28, BossBar.Color.RED, Element.EARTH,
                Mechanic.SLAM, "Ira Stomp", Mechanic.BEAM, "Cero",
                Move.IRA, "Ira", "your rage makes you grow: bigger, tougher and stronger for a while",
                Move.FURIA, "Furia", "grow into a colossus; every blow shakes the ground around you");
        add(f, Playstyle.VANGUARD, "Nnoitra Gilga", "Santa Teresa", "Pray, Santa Teresa.",
                "Santa Teresa — Six Arms", "Santa Teresa!", 0xdcc85a, BossBar.Color.YELLOW, Element.SHADOW,
                Mechanic.FLURRY, "Scythe Flurry", Mechanic.WAVE, "Crescent Scythe",
                Move.HIERRO, "Hierro Más Fuerte", "the strongest Hierro of the Espada: shrug off most damage for a few seconds",
                Move.SIX_ARMS, "Six Arms", "grow four more arms: every melee hit strikes three times");
        add(f, Playstyle.MIRAGE, "Zommari Rureaux", "Brujería", "Pray, Brujería.",
                "Brujería — Amor", "Amor.", 0x5ac878, BossBar.Color.GREEN, Element.SHADOW,
                Mechanic.SCATTER, "Gemelos Sonido", Mechanic.BLINK, "Sonido Strike",
                Move.AMOR, "Amor", "an eye opens on an enemy: you control them — they can't hurt you and stagger toward your other enemies",
                Move.AMOR_MASS, "Fifty Eyes", "fifty eyes open: every enemy near you falls under Amor");
        add(f, Playstyle.MIRAGE, "Aaroniero Arruruerie", "Glotonería", "Devour, Glotonería.",
                "Glotonería — 33,650 Hollows", "Devour, Glotonería.", 0x6a3c8c, BossBar.Color.PURPLE, Element.SHADOW,
                Mechanic.SCATTER, "Shapeshift", Mechanic.CHAINS, "Tentacle Grab",
                Move.DEVOUR, "Devour", "bite an enemy: heal by what you tear off, and steal their buffs",
                Move.BANQUET, "Banquet", "the 33,650 Hollows inside you lash out with tentacles at everyone near you, feeding you");
        add(f, Playstyle.MIRAGE, "Szayelaporro Granz", "Fornicarás", "Sip, Fornicarás.",
                "Fornicarás — Gabriel", "Fornicarás!", 0xe678c8, BossBar.Color.PINK, Element.POISON,
                Mechanic.SCATTER, "Gas Veil", Mechanic.BEAM, "Cero",
                Move.VOODOO, "Voodoo Doll", "make a doll of an enemy; press Q again to crush one of its organs",
                Move.GABRIEL, "Organ Crush", "dolls of every enemy near you, crushed one after another");
        add(f, Playstyle.MARKSMAN, "Ulquiorra Cifer", "Murciélago", "Enclose, Murciélago.",
                "Resurrección: Segunda Etapa", "Resurrección: Segunda Etapa.", 0x3cc86e, BossBar.Color.GREEN, Element.SHADOW,
                Mechanic.BEAM, "Cero Oscuras", Mechanic.BLINK, "Sonido",
                Move.LUZ_LANZA, "Luz de la Luna", "throw a green lance of light that bursts on impact",
                Move.LANZA_RELAMPAGO, "Lanza del Relámpago", "charge a lance of lightning and hurl it; the explosion is enormous");
        add(f, Playstyle.MARKSMAN, "Coyote Starrk", "Los Lobos", "Kick about, Los Lobos.",
                "Los Lobos — Lilynette", "Kick about, Los Lobos.", 0x8cb4ff, BossBar.Color.BLUE, Element.LIGHTNING,
                Mechanic.BEAM, "Cero", Mechanic.RAIN, "Cero Rain",
                Move.LOBOS, "Los Lobos", "a pack of spirit wolves runs at your enemies and explodes on them",
                Move.METRALLETA, "Cero Metralleta", "a thousand ceros: a three-second machine-gun stream of ceros where you aim");
        add(f, Playstyle.MARKSMAN, "Tier Harribel", "Tiburón", "Destroy, Tiburón.",
                "Tiburón — Gran Caída", "Destroy, Tiburón.", 0xe6c850, BossBar.Color.YELLOW, Element.WATER,
                Mechanic.WAVE, "Ola Azul", Mechanic.BEAM, "La Gota",
                Move.CASCADA, "Cascada", "a massive waterfall crashes down on the area you aim at",
                Move.GRAN_CAIDA, "Gran Caída", "a colossal blade of compressed water, then Hirviendo: scalding water around you");
        add(f, Playstyle.WAYFARER, "Dordoni Alessandro Del Socaccio", "Giralda", "Whirl, Giralda!",
                "Giralda — Torbellinos", "Giralda!", 0xf0f0e6, BossBar.Color.WHITE, Element.WIND,
                Mechanic.DASH, "Sonido Kick", Mechanic.SKYFALL, "Air Dive",
                Move.REMOLINO, "Torbellinos", "two tornado drills race forward from your legs, sweeping enemies up",
                Move.U_TEMPEST, "Cero Tornado", "a three-second tornado that drags enemies in and hurls them skyward");
        add(f, Playstyle.WAYFARER, "Cirucci Sanderwicci", "Golondrina", "Tear apart, Golondrina!",
                "Golondrina — Wings of Blades", "Golondrina!", 0xb478dc, BossBar.Color.PURPLE, Element.PALE_PETAL,
                Mechanic.RAIN, "Feather Rain", Mechanic.DASH, "Wing Rush",
                Move.GOLONDRINA, "Golondrina", "take to the air on wings of blades and shower the ground below with razor feathers",
                Move.U_STARFALL, "Hundred Feathers", "a storm of razor feathers over a wide area");
        add(f, Playstyle.WAYFARER, "Ggio Vega", "Tigre Estoque", "Bite off, Tigre Estoque!",
                "Tigre Estoque — Zarpazo", "Tigre Estoque!", 0xff9a32, BossBar.Color.YELLOW, Element.LIGHTNING,
                Mechanic.DASH, "Pounce", Mechanic.FLURRY, "Fang Flurry",
                Move.THUNDER_STEP, "Sonido Tigre", "three sonido flashes; press Q again to step, cutting everything you pass",
                Move.U_PHANTOM, "Zarpazo", "strike up to five enemies in sequence from behind, then return");
        add(f, Playstyle.ARTIFICER, "Barragan Luisenbarn", "Arrogante", "Rot, Arrogante.",
                "Arrogante — Respira", "Rot, Arrogante.", 0x3c2850, BossBar.Color.PURPLE, Element.SHADOW,
                Mechanic.SLAM, "Gran Caída", Mechanic.CHAINS, "Arrogante Grip",
                Move.RESPIRA, "Respira", "breathe a cloud of senescence ahead of you: everything it touches ages and rots",
                Move.SENESCENCIA, "Senescencia", "you become time itself: everything near you rots, and arrows decay before they reach you");
        add(f, Playstyle.ARTIFICER, "Gantenbainne Mosqueda", "Dragra", "Sear, Dragra!",
                "Dragra — Garra de Dragón", "Dragra!", 0xe6782d, BossBar.Color.RED, Element.FIRE,
                Mechanic.FLURRY, "Dragra Fists", Mechanic.SLAM, "Dragon Stomp",
                Move.DRAGON_RUGIR, "Dragon Rugir", "a dragon of reiatsu bursts from your fist and drills through everything ahead",
                Move.U_CATACLYSM, "Garras de Dragón", "two volleys of three giant dragon-claw waves");
        add(f, Playstyle.ARTIFICER, "Tesla Lindocruz", "Verdugo", "Gore, Verdugo!",
                "Verdugo — Stampede", "Verdugo!", 0x8c6e50, BossBar.Color.WHITE, Element.EARTH,
                Mechanic.DASH, "Verdugo Charge", Mechanic.SLAM, "Tusk Slam",
                Move.BULL_CHARGE, "Gore", "charge forward, carry everyone you hit on your tusks, and slam them down",
                Move.U_WORLD_BREAKER, "Stampede", "three expanding rings of erupting earth");
        add(f, Playstyle.KEEPER, "Nelliel Tu Odelschwanck", "Gamuza", "Declare, Gamuza!",
                "Gamuza — Lanzador Verde", "Declare, Gamuza!", 0x78d28c, BossBar.Color.GREEN, Element.LIGHT,
                Mechanic.WARD, "Healing Saliva", Mechanic.DASH, "Gamuza Charge",
                Move.CERO_DOBLE, "Cero Doble", "swallow the damage of incoming attacks, then spit it back as a doubled cero",
                Move.LANZADOR, "Lanzador Verde", "hurl a spinning green lance that drills through everything in a long line");
        add(f, Playstyle.KEEPER, "Luppi Antenor", "Trepadora", "Strangle, Trepadora.",
                "Trepadora — Eight Arms", "Strangle, Trepadora.", 0x8c5ac8, BossBar.Color.PURPLE, Element.POISON,
                Mechanic.CHAINS, "Tentacle Bind", Mechanic.SLAM, "Tentacle Slam",
                Move.TREPADORA, "Trepadora", "eight tentacles seize the nearest enemies, holding and crushing them",
                Move.TENTACLE_STORM, "Spike Barrage", "every tentacle sprouts spikes and stabs everything near you over and over");
        add(f, Playstyle.KEEPER, "Apache, Mila Rose & Sung-Sun", "Tres Bestias", "Quimera Parca!",
                "Ayon", "Quimera Parca!", 0xd2b464, BossBar.Color.YELLOW, Element.EARTH,
                Mechanic.WARD, "Pack Ward", Mechanic.CHAINS, "Anaconda Bind",
                Move.IRON_CONSTRUCT, "Quimera Parca", "summon Ayon, the beast made from three arms, to fight for you",
                Move.TRES_BESTIAS, "Tres Bestias", "Cierva gores, León smashes and Anaconda constricts the three nearest enemies");

        // ------------------------------------------------------------ Quincy Schrift
        f = Faction.QUINCY;
        add(f, Playstyle.VANGUARD, "Bazz-B", "H — The Heat", "The Heat!",
                "The Heat: Vollständig", "Vollständig!", 0xff4614, BossBar.Color.RED, Element.FIRE,
                Mechanic.BEAM, "Burner Finger 1", Mechanic.WAVE, "Burner Finger 2",
                Move.BURNER3, "Burner Finger 3", "three fingers: a wide cone of fire that blasts everything in front",
                Move.BURNER4, "Burner Finger 4", "all four fingers: a colossal blade of fire sweeps all the way around you");
        add(f, Playstyle.VANGUARD, "Driscoll Berci", "O — The Overkill", "The Overkill!",
                "The Overkill: Vollständig", "Vollständig!", 0xa01e28, BossBar.Color.RED, Element.BLOOD,
                Mechanic.FLURRY, "Heilig Bogen Fists", Mechanic.WAVE, "Heilig Pfeil Spread",
                Move.OVERKILL, "The Overkill", "every hit you land makes the next one stronger",
                Move.KILL_COUNT, "Kill Count", "every enemy that falls near you feeds you: full heal and more strength");
        add(f, Playstyle.VANGUARD, "Meninas McAllon", "P — The Power", "The Power!",
                "The Power: Vollständig", "Vollständig!", 0xff78b4, BossBar.Color.PINK, Element.EARTH,
                Mechanic.SLAM, "Power Slam", Mechanic.FLURRY, "Power Rush",
                Move.POWER_PUNCH, "The Power", "one punch with impossible strength sends an enemy flying",
                Move.POWER_QUAKE, "Ground Splitter", "smash the ground so hard everyone around you is launched");
        add(f, Playstyle.MIRAGE, "Äs Nödt", "F — The Fear", "The Fear.",
                "Tatarforas", "Vollständig: Tatarforas.", 0x46283c, BossBar.Color.PURPLE, Element.SHADOW,
                Mechanic.BEAM, "Fear Thorns", Mechanic.SCATTER, "Dread",
                Move.FEAR, "The Fear", "fear thorns pierce an enemy: blind, weak, slow and shaking",
                Move.TATARFORAS, "Tatarforas", "a cage of fear: enemies inside can't see, can't fight back and can't use their powers");
        add(f, Playstyle.MIRAGE, "Gremmy Thoumeaux", "V — The Visionary", "The Visionary.",
                "The Visionary: Vollständig", "I imagine it.", 0xa0c8ff, BossBar.Color.BLUE, Element.LIGHT,
                Mechanic.RAIN, "Imagined Bombs", Mechanic.BLINK, "Imagined Step",
                Move.VISIONARY, "Imagination", "imagine something into being: bombs, quicksand or blades, at random",
                Move.METEOR, "Meteor", "imagine a meteor: it falls on where you aim");
        add(f, Playstyle.MIRAGE, "Pepe Waccabrada", "L — The Love", "The Love!",
                "The Love: Vollständig", "Love Rain!", 0xff82b4, BossBar.Color.PINK, Element.PETAL,
                Mechanic.BEAM, "Love Shot", Mechanic.SCATTER, "Heart Haze",
                Move.LOVE_KISS, "Love Kiss", "a heart that charms the enemy it hits: they can't hurt you and walk to you",
                Move.LOVE_RAIN, "Love Rain", "hearts rain on everyone near you: every enemy is charmed");
        add(f, Playstyle.MARKSMAN, "Lille Barro", "X — The X-Axis", "The X-Axis.",
                "Jilliel", "Vollständig: Jilliel.", 0xf0f5ff, BossBar.Color.WHITE, Element.LIGHT,
                Mechanic.BEAM, "X-Axis Shot", Mechanic.RAIN, "Heilig Pfeil Rain",
                Move.X_AXIS, "The X-Axis", "a shot that pierces everything between the muzzle and the target: walls, shields, everyone",
                Move.JILLIEL, "Holy Rays", "your halo rains X-axis rays over a wide area around where you aim");
        add(f, Playstyle.MARKSMAN, "Uryū Ishida", "A — The Antithesis", "The Antithesis.",
                "Licht Regen", "Vollständig!", 0x78aaff, BossBar.Color.BLUE, Element.LIGHT,
                Mechanic.BEAM, "Heilig Pfeil", Mechanic.RAIN, "Licht Regen",
                Move.ANTITHESIS, "The Antithesis", "reverse what has happened between you and an enemy: your wounds become theirs",
                Move.SPRENGER, "Sprenger", "five Seele Schneider form a pentacle around where you aim, then it explodes");
        add(f, Playstyle.MARKSMAN, "Candice Catnipp", "T — The Thunderbolt", "The Thunderbolt!",
                "The Thunderbolt: Vollständig", "Vollständig!", 0xb4ff50, BossBar.Color.GREEN, Element.LIGHTNING,
                Mechanic.BEAM, "Thunder Shot", Mechanic.DASH, "Lightning Kick",
                Move.GALVANO_BLAST, "Galvano Blast", "call a bolt of lightning down on an enemy",
                Move.GALVANO_JAVELIN, "Galvano Javelin", "a giant javelin of lightning, followed by a storm of bolts");
        add(f, Playstyle.WAYFARER, "Nianzol Weizol", "W — The Wind", "The Wind.",
                "The Wind: Vollständig", "Vollständig!", 0xc8ffe6, BossBar.Color.GREEN, Element.WIND,
                Mechanic.DASH, "Lichtsprung", Mechanic.SCATTER, "Drift",
                Move.WIND, "The Wind", "attacks pass through you like wind: the next three hits on you miss",
                Move.WIND_STORM, "Gale", "a storm wind blasts every enemy around you away and leaves you untouchable for a moment");
        add(f, Playstyle.WAYFARER, "Mask De Masculine", "S — The Superstar", "The Superstar!",
                "The Superstar: Vollständig", "Star Hero!", 0xffd23c, BossBar.Color.YELLOW, Element.LIGHT,
                Mechanic.DASH, "Star Dash", Mechanic.FLURRY, "Hero Punches",
                Move.STAR_CHEER, "Star Cheer", "the crowd cheers: heal and power up",
                Move.STAR_ROCKET, "Star Rocket Dynamite", "fly like a rocket through enemies and explode at the end");
        add(f, Playstyle.WAYFARER, "Bambietta Basterbine", "E — The Explode", "The Explode!",
                "The Explode: Vollständig", "Vollständig!", 0xff8c3c, BossBar.Color.RED, Element.FIRE,
                Mechanic.RAIN, "Reishi Bombs", Mechanic.DASH, "Blast Jump",
                Move.EXPLODE, "The Explode", "anything you hit becomes a bomb that goes off a second later",
                Move.EXPLODE_BARRAGE, "Explode Barrage", "rise into the air and rain explosive reishi all around");
        add(f, Playstyle.ARTIFICER, "Cang Du", "I — The Iron", "The Iron.",
                "The Iron: Vollständig", "Vollständig.", 0xa0a0aa, BossBar.Color.WHITE, Element.EARTH,
                Mechanic.FLURRY, "Iron Claws", Mechanic.WARD, "Iron Guard",
                Move.EISERN, "Eisern", "skin of steel: take far less damage, ignore knockback, and hurt anyone who hits you",
                Move.U_WORLD_BREAKER, "Steel Field", "three expanding rings of erupting iron");
        add(f, Playstyle.ARTIFICER, "Gerard Valkyrie", "M — The Miracle", "The Miracle!",
                "Hoheitsrecht", "Vollständig!", 0xffe68c, BossBar.Color.YELLOW, Element.LIGHT,
                Mechanic.SLAM, "Sword of Victory", Mechanic.WARD, "Shield of the Miracle",
                Move.MIRACLE, "The Miracle", "every wound makes you bigger and stronger",
                Move.HOHEIT, "Hoheitsrecht", "become a giant; every blow sends out a shockwave");
        add(f, Playstyle.ARTIFICER, "Liltotto Lamperd", "G — The Glutton", "The Glutton!",
                "The Glutton: Vollständig", "Itadakimasu!", 0xffa0c8, BossBar.Color.PINK, Element.POISON,
                Mechanic.FLURRY, "Bite Flurry", Mechanic.BEAM, "Spit",
                Move.DEVOUR, "Devour", "a huge mouth bites an enemy: heal by what you eat and steal their buffs",
                Move.GLUTTON_MAW, "The Glutton", "a gigantic mouth swallows everything in front of you, pulling them in");
        add(f, Playstyle.KEEPER, "Quilge Opie", "J — The Jail", "The Jail.",
                "Biskiel", "Vollständig: Biskiel.", 0xf0f0ff, BossBar.Color.WHITE, Element.LIGHT,
                Mechanic.CHAINS, "Jail Chains", Mechanic.WARD, "Blut Vene",
                Move.BONE_CAGE, "The Jail", "lock an enemy inside a cage of light; it cannot leave",
                Move.BISKIEL, "Sklaverei", "drain the reishi out of everything around you: enemies wither and you heal");
        add(f, Playstyle.KEEPER, "Jugram Haschwalth", "B — The Balance", "The Balance.",
                "Avaron", "Vollständig: Avaron.", 0x283246, BossBar.Color.BLUE, Element.SHADOW,
                Mechanic.WARD, "Blut Vene", Mechanic.BEAM, "Heilig Pfeil",
                Move.BALANCE, "The Balance", "half of every misfortune that befalls you is passed to the nearest enemy",
                Move.AVARON, "Freund Schild", "every wound dealt to you is shared out among the enemies around you instead");
        add(f, Playstyle.KEEPER, "Giselle Gewelle", "Z — The Zombie", "The Zombie!",
                "The Zombie: Vollständig", "Vollständig!", 0x8c2850, BossBar.Color.PURPLE, Element.BLOOD,
                Mechanic.BEAM, "Blood Spray", Mechanic.CHAINS, "Zombie Grab",
                Move.ZOMBIE, "The Zombie", "your blood splashes enemies: their attacks on you heal you, and creatures that die rise for you",
                Move.ZOMBIE_ARMY, "Zombie Army", "raise a horde of zombies to fight for you");

        // ------------------------------------------------------------ Fullbring
        f = Faction.FULLBRINGER;
        add(f, Playstyle.VANGUARD, "Shishigawara Moe", "Jackpot Knuckle", "Jackpot Knuckle!",
                "Jackpot Knuckle: Complete", "Jackpot!", 0xffc832, BossBar.Color.YELLOW, Element.LIGHTNING,
                Mechanic.FLURRY, "Slot Punches", Mechanic.SLAM, "Big Win",
                Move.JACKPOT, "Spin", "spin the reels and punch: a jackpot hits like a truck, a bust hits you instead",
                Move.TRIPLE_SEVEN, "Triple Seven", "the machine is rigged: every punch is a jackpot for a while");
        add(f, Playstyle.VANGUARD, "Yasutora Sado", "Brazo Derecha de Gigante", "Brazo Derecha de Gigante!",
                "Brazo Derecha de Gigante: Full", "Brazo Derecha de Gigante!", 0x6e3c96, BossBar.Color.PURPLE, Element.EARTH,
                Mechanic.FLURRY, "Golpe", Mechanic.WARD, "Giant's Guard",
                Move.EL_DIRECTO, "El Directo", "a straight punch that throws a blast of reiatsu ahead",
                Move.GIGANTE, "Gigante", "the giant's arm armours you: shrug off damage and hit back at anyone who strikes you");
        add(f, Playstyle.MIRAGE, "Shūkurō Tsukishima", "Book of the End", "Book of the End.",
                "Book of the End: Complete", "Book of the End.", 0x3c3c50, BossBar.Color.WHITE, Element.SHADOW,
                Mechanic.BLINK, "Bookmark Cut", Mechanic.SCATTER, "Turned Page",
                Move.INSERT, "Insert", "cut an enemy to insert yourself into their past: they remember you as a friend and won't harm you",
                Move.REWRITE, "Rewrite", "insert yourself into the past of every enemy around you at once");
        add(f, Playstyle.MARKSMAN, "Yasutora Sado", "Brazo Izquierda del Diablo", "Brazo Izquierda del Diablo!",
                "La Muerte", "Brazo Izquierda del Diablo!", 0xdc5ac8, BossBar.Color.PINK, Element.SHADOW,
                Mechanic.BEAM, "Diablo Shot", Mechanic.FLURRY, "Diablo Rush",
                Move.LA_MUERTE, "La Muerte", "a skull-shaped blast of reiatsu that explodes at the end of its path",
                Move.U_STARFALL, "Muerte Barrage", "a storm of La Muerte blasts over a wide area");
        add(f, Playstyle.MARKSMAN, "Kūgo Ginjō", "Cross of Scaffold", "Cross of Scaffold.",
                "Cross of Scaffold: Complete", "Complete Fullbring.", 0x5a6e8c, BossBar.Color.BLUE, Element.SHADOW,
                Mechanic.WAVE, "Cross Slash", Mechanic.BEAM, "Fullbring Blast",
                Move.CROSS_STEAL, "Steal", "the cross drinks from an enemy: steal their health and buffs",
                Move.CROSS_GETSUGA, "Stolen Getsuga", "the power you stole from Ichigo: a giant black Getsuga Tenshō");
        add(f, Playstyle.WAYFARER, "Ichigo Kurosaki", "Fullbring", "Fullbring!",
                "Complete Fullbring", "Complete Fullbring!", 0x50463c, BossBar.Color.WHITE, Element.SHADOW,
                Mechanic.DASH, "Bringer Light", Mechanic.WAVE, "Fullbring Getsuga",
                Move.SKY_STEPS, "Bringer Light", "step on the air: jump again and again in mid-air",
                Move.U_PHANTOM, "Bringer Light Rush", "flash through up to five enemies one after another, then return");
        add(f, Playstyle.WAYFARER, "Jackie Tristan", "Dirty Boots", "Dirty Boots!",
                "Dirty Boots: Complete", "Dirty Boots!", 0x785a3c, BossBar.Color.WHITE, Element.EARTH,
                Mechanic.FLURRY, "Boot Kicks", Mechanic.DASH, "Mud Slide",
                Move.DIRTY_BOOTS, "Dirty Boots", "the dirtier you get, the stronger you are: power up for every hit you've taken",
                Move.FILTHY_STOMP, "Filthy Stomp", "a stomp that sends everyone near you flying");
        add(f, Playstyle.ARTIFICER, "Riruka Dokugamine", "Dollhouse", "Dollhouse!",
                "Dollhouse: Complete", "Dollhouse!", 0xff8cc8, BossBar.Color.PINK, Element.PETAL,
                Mechanic.CHAINS, "Love Gun Leash", Mechanic.BEAM, "Love Gun",
                Move.DOLLHOUSE, "Love Gun", "shoot an enemy with the Love Gun: they shrink into a doll and can't fight",
                Move.BRING_IN, "Bring In", "pull everyone near you into the dollhouse at once");
        add(f, Playstyle.ARTIFICER, "Yukio Hans Vorarlberna", "Invaders Must Die", "Invaders Must Die.",
                "Invaders Must Die: Final Stage", "Game start.", 0x50dcff, BossBar.Color.BLUE, Element.LIGHT,
                Mechanic.PILLARS, "Pixel Walls", Mechanic.RAIN, "Invader Fire",
                Move.INVADERS, "Invaders Must Die", "trap an area in a game box; pixel invaders shoot whoever is inside",
                Move.FINAL_STAGE, "Final Stage", "release the boss stage: invader drones dive-bomb every enemy near you");
        add(f, Playstyle.KEEPER, "Orihime Inoue", "Shun Shun Rikka", "Shun Shun Rikka!",
                "Shiten Kōshun", "Shiten Kōshun, I reject!", 0xffb478, BossBar.Color.YELLOW, Element.LIGHT,
                Mechanic.WARD, "Santen Kesshun", Mechanic.BEAM, "Koten Zanshun",
                Move.SOTEN, "Sōten Kisshun", "a dome that rejects what happened: heals and cleanses everyone inside",
                Move.SHITEN, "Shiten Kōshun", "reject every attack: damage enemies deal to you or allies near you is sent back at them");
        add(f, Playstyle.KEEPER, "Giriko Kutsuzawa", "Time Tells No Lies", "Time Tells No Lies.",
                "Time Tells No Lies: Complete", "The contract is set.", 0xc8aa78, BossBar.Color.WHITE, Element.SOUND,
                Mechanic.WARD, "Clockwork Guard", Mechanic.CHAINS, "Contract Chain",
                Move.TIME_BET, "Contract", "bet on an enemy: land three hits within ten seconds and it explodes; fail and you pay",
                Move.TIME_LIMIT, "Time Limit", "a contract with time: overwhelming power for fifteen seconds, and weakness after");
    }
}
