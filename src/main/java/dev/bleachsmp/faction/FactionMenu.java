package dev.bleachsmp.faction;

import dev.bleachsmp.BleachSmpPlugin;
import dev.bleachsmp.item.StarterItems;
import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class FactionMenu implements Listener {
    private static final int[] FACTION_SLOTS = {10, 12, 14, 16};

    private final BleachSmpPlugin plugin;
    private final StarterItems starterItems;
    private final Map<Integer, Faction> factionsBySlot = new HashMap<>();

    public FactionMenu(BleachSmpPlugin plugin, StarterItems starterItems) {
        this.plugin = plugin;
        this.starterItems = starterItems;
        Faction[] factions = Faction.values();
        for (int index = 0; index < factions.length; index++) {
            factionsBySlot.put(FACTION_SLOTS[index], factions[index]);
        }
    }

    public void open(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder) {
            return;
        }
        MenuHolder holder = new MenuHolder();
        Inventory inventory = holder.getInventory();
        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        for (Map.Entry<Integer, Faction> entry : factionsBySlot.entrySet()) {
            Faction faction = entry.getValue();
            ItemStack choice = namedItem(faction.starterMaterial(),
                    Component.text(faction.displayName(), faction.color())
                            .decoration(TextDecoration.ITALIC, false));
            ItemMeta meta = choice.getItemMeta();
            meta.lore(java.util.List.of(
                    Component.text(faction.description()).decoration(TextDecoration.ITALIC, false),
                    Component.text("Click to begin this path", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)));
            choice.setItemMeta(meta);
            inventory.setItem(entry.getKey(), choice);
        }

        player.openInventory(inventory);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getFaction(player.getUniqueId()) == null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && plugin.getFaction(player.getUniqueId()) == null) {
                    open(player);
                }
            }, 1L);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        Faction faction = factionsBySlot.get(event.getSlot());
        if (faction == null) {
            return;
        }

        if (!plugin.assignFaction(player, faction)) {
            player.sendMessage(Component.text("Your path could not be saved. Please try again.",
                    NamedTextColor.RED));
            return;
        }

        player.closeInventory();
        player.sendMessage(Component.text("You have chosen the " + faction.displayName() + " path.", faction.color()));
        player.sendMessage(Component.text("Your " + faction.starterName() + " is ready.", NamedTextColor.GRAY));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder)
                || !(event.getPlayer() instanceof Player player)
                || plugin.getFaction(player.getUniqueId()) != null) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && plugin.getFaction(player.getUniqueId()) == null) {
                open(player);
            }
        }, 1L);
    }

    private ItemStack namedItem(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.customName(name);
        item.setItemMeta(meta);
        return item;
    }

    private static final class MenuHolder implements InventoryHolder {
        private final Inventory inventory;

        private MenuHolder() {
            inventory = Bukkit.createInventory(this, 27, Component.text("Choose your path", NamedTextColor.DARK_RED));
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
