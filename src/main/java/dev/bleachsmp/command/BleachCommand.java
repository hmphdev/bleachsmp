package dev.bleachsmp.command;

import dev.bleachsmp.BleachSmpPlugin;
import dev.bleachsmp.faction.Faction;
import dev.bleachsmp.faction.FactionMenu;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BleachCommand implements CommandExecutor, TabCompleter {
    private final BleachSmpPlugin plugin;
    private final FactionMenu factionMenu;

    public BleachCommand(BleachSmpPlugin plugin, FactionMenu factionMenu) {
        this.plugin = plugin;
        this.factionMenu = factionMenu;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("bankai")) {
            plugin.awakenings().bankai().command(sender, args);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("boss")) {
            handleBossCommand(sender, args);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("garganta")) {
            plugin.huecoMundo().activateGarganta(player);
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            showStatus(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("awakenings") && args.length == 1) {
            plugin.awakenings().showProgress(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("release")) {
            if (args.length == 1) {
                plugin.awakenings().release(player, null);
            } else if (args.length == 2 && args[1].equalsIgnoreCase("first")) {
                plugin.awakenings().release(player, false);
            } else if (args.length == 2 && args[1].equalsIgnoreCase("advanced")) {
                plugin.awakenings().release(player, true);
            } else {
                player.sendMessage(Component.text("Usage: /bleach release [first|advanced]", NamedTextColor.RED));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("choose")) {
            if (plugin.getFaction(player.getUniqueId()) != null) {
                player.sendMessage(Component.text("Your faction is already chosen. A switching ritual is not available yet.",
                        NamedTextColor.RED));
            } else {
                factionMenu.open(player);
            }
            return true;
        }

        player.sendMessage(Component.text("Usage: /bleach [status|choose|awakenings|release|garganta|boss]",
                NamedTextColor.RED));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return matching(sender.hasPermission("bleachsmp.admin")
                    ? List.of("status", "choose", "awakenings", "release", "garganta", "boss", "bankai")
                    : List.of("status", "choose", "awakenings", "release", "garganta", "boss"), args[0]);
        }
        if (args.length > 1 && args[0].equalsIgnoreCase("bankai")) {
            return plugin.awakenings().bankai().complete(sender, args);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("release")) {
            return matching(List.of("first", "advanced"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("boss")) {
            return matching(List.of("mark", "unmark", "vasto", "tear"), args[1]);
        }
        return List.of();
    }

    private void showStatus(Player player) {
        Faction faction = plugin.getFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(Component.text("Choose your path to begin.", NamedTextColor.YELLOW));
            factionMenu.open(player);
            return;
        }

        int rank = plugin.getRank(player.getUniqueId());
        player.sendMessage(Component.text("Path: " + faction.displayName(), faction.color()));
        player.sendMessage(Component.text("Rank " + rank + " - " + plugin.getFactionRankTitle(player.getUniqueId()),
                NamedTextColor.GOLD));
        player.sendMessage(Component.text("Rank points: " + plugin.getRankPoints(player.getUniqueId())
                + (rank == 1 ? " (maximum rank)" : " (" + plugin.getPointsToNextRank(player.getUniqueId())
                + " to Rank " + (rank - 1) + ")"), NamedTextColor.GRAY));
        player.sendMessage(Component.text("Weekly boss challenge: " + plugin.activeBossChallenge(),
                NamedTextColor.DARK_AQUA));
        player.sendMessage(Component.text("Check your playstyle and forms with /bleach awakenings.", NamedTextColor.GRAY));
    }

    private void handleBossCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bleachsmp.admin")) {
            sender.sendMessage(Component.text("You do not have permission to manage custom bosses.",
                    NamedTextColor.RED));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Boss marking must be used in-game while looking at an entity.");
            return;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("tear")) {
            if (!plugin.huecoMundo().summonTear(player)) {
                player.sendMessage(Component.text("Stand under open sky outside Hueco Mundo, with no tear already open.",
                        NamedTextColor.RED));
            }
            return;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("vasto")) {
            if (!plugin.huecoMundo().summonVastoLorde(player)) {
                player.sendMessage(Component.text("Stand on open ground in Hueco Mundo, with no Vasto Lorde already active.",
                        NamedTextColor.RED));
            }
            return;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("unmark")) {
            Entity target = player.getTargetEntity(32);
            if (!(target instanceof LivingEntity living) || target instanceof Player) {
                player.sendMessage(Component.text("Look at a living non-player entity within 32 blocks.",
                        NamedTextColor.RED));
                return;
            }
            if (!plugin.unmarkBoss(living)) {
                player.sendMessage(Component.text("That entity is not marked as a custom boss.",
                        NamedTextColor.YELLOW));
                return;
            }
            player.sendMessage(Component.text("Removed its custom boss mark.", NamedTextColor.GREEN));
            return;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("mark")) {
            Entity target = player.getTargetEntity(32);
            if (!(target instanceof LivingEntity living) || target instanceof Player) {
                player.sendMessage(Component.text("Look at a living non-player entity within 32 blocks.",
                        NamedTextColor.RED));
                return;
            }
            String bossId = args[2].toLowerCase(Locale.ROOT);
            if (!plugin.markBoss(living, bossId)) {
                player.sendMessage(Component.text("Boss IDs may use lowercase letters, numbers, '_' and '-'.",
                        NamedTextColor.RED));
                return;
            }
            player.sendMessage(Component.text("Marked entity as custom boss '" + bossId + "'.", NamedTextColor.GREEN));
            player.sendMessage(Component.text("Players need meaningful damage contribution for rank credit.",
                    NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("Usage: /bleach boss mark <id>, /bleach boss unmark /bleach boss vasto or /bleach boss tear",
                NamedTextColor.RED));
    }

    private List<String> matching(List<String> choices, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return choices.stream().filter(choice -> choice.startsWith(normalized)).toList();
    }
}
