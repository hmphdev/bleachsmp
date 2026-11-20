package dev.bleachsmp.awakening;

import dev.bleachsmp.faction.Faction;
import dev.bleachsmp.fx.Vfx;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Admin previews of the ability effects: release any faction's form without unlocking it, fire its techniques,
 * or play every effect in a row. Nothing here grants progress.
 */
public final class VfxPreview {
    private final AwakeningService service;

    VfxPreview(AwakeningService service) {
        this.service = service;
    }

    public void command(Player player, String[] args) {
        if (!player.hasPermission("bleachsmp.admin")) {
            player.sendMessage(Component.text("You do not have permission to preview effects.", NamedTextColor.RED));
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "release" -> {
                boolean advanced = args.length > 2 && args[2].equalsIgnoreCase("advanced");
                Faction faction = args.length > 3 ? parse(args[3]) : service.faction(player);
                Playstyle style = args.length > 4 ? style(args[4]) : Playstyle.VANGUARD;
                if (faction == null || style == null) {
                    usage(player);
                    return;
                }
                service.releases().release(player, faction, style, null, advanced);
            }
            case "use" -> {
                String move = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
                switch (move) {
                    case "main" -> service.releases().useTechnique(player, false);
                    case "second" -> service.releases().useTechnique(player, true);
                    case "ultimate" -> service.releases().ultimate(player);
                    default -> usage(player);
                }
            }
            case "seal" -> service.releases().seal(player, true);
            case "demo" -> demo(player);
            default -> usage(player);
        }
    }

    public List<String> complete(String[] args) {
        return switch (args.length) {
            case 2 -> List.of("release", "use", "seal", "demo");
            case 3 -> args[1].equalsIgnoreCase("release") ? List.of("first", "advanced")
                    : args[1].equalsIgnoreCase("use") ? List.of("main", "second", "ultimate") : List.of();
            case 4 -> args[1].equalsIgnoreCase("release")
                    ? java.util.Arrays.stream(Faction.values()).map(f -> f.name().toLowerCase(Locale.ROOT)).toList()
                    : List.of();
            case 5 -> args[1].equalsIgnoreCase("release")
                    ? java.util.Arrays.stream(Playstyle.values()).map(p -> p.name().toLowerCase(Locale.ROOT)).toList()
                    : List.of();
            default -> List.of();
        };
    }

    /** Every base effect in a row in front of the player. */
    private void demo(Player player) {
        Location base = player.getLocation();
        Vector forward = base.getDirection().setY(0).normalize();
        Vector right = forward.getCrossProduct(new Vector(0, 1, 0)).normalize();
        Color blue = Color.fromRGB(90, 170, 255);
        Color red = Color.fromRGB(230, 40, 50);
        Location ahead = base.clone().add(forward.clone().multiply(7));
        Vfx.arc(ahead.clone().add(right.clone().multiply(-5)).add(0, 1.4, 0), forward, 1.8, blue, 1.8f, 0);
        Vfx.shockwave(service.plugin(), ahead, red, 5, 14);
        Vfx.pillar(ahead.clone().add(right.clone().multiply(5)), Color.fromRGB(150, 90, 255), 1.4, 8, 14, 30);
        Vfx.line(base.clone().add(0, 1.4, 0).add(right.clone().multiply(-2)),
                ahead.clone().add(right.clone().multiply(-2)).add(forward.clone().multiply(6)).add(0, 1.4, 0), red, 3f, 2);
        Vfx.burst(ahead.clone().add(0, 2.5, 0), Color.fromRGB(255, 200, 80), 40, 0.8, 1.8f);
        Vfx.bolt(ahead.clone().add(right.clone().multiply(2.5)).add(forward.clone().multiply(3)), Color.WHITE, 14, .45);
        Vfx.crater(ahead, 6, 120);
        for (int i = 0; i < 5; i++) {
            Vfx.flame(ahead.clone().add(right.clone().multiply(-3 + i * .4)).add(forward.clone().multiply(-2)),
                    Color.fromRGB(80, 210, 230), 1.6 + i * .2, 30);
        }
    }

    private static Faction parse(String name) {
        try {
            return Faction.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Playstyle style(String name) {
        try {
            return Playstyle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static void usage(Player player) {
        player.sendMessage(Component.text("Usage: /bleach vfx release <first|advanced> [faction] [playstyle] | "
                + "use <main|second|ultimate> | seal | demo", NamedTextColor.RED));
    }
}
