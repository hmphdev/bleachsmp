package dev.bleachsmp.item;

import dev.bleachsmp.BleachSmpPlugin;
import dev.bleachsmp.faction.Faction;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class StarterItems {
    private final BleachSmpPlugin plugin;

    public StarterItems(BleachSmpPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack create(Faction faction) {
        ItemStack item = new ItemStack(faction.starterMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.customName(Component.text(faction.starterName(), faction.color())
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(faction.description()).decoration(TextDecoration.ITALIC, false),
                Component.text("Path: " + faction.displayName(), faction.color())
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click to use your faction ability", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(plugin.itemTypeKey(), PersistentDataType.STRING, "starter_weapon");
        meta.getPersistentDataContainer().set(plugin.itemFactionKey(), PersistentDataType.STRING, faction.name());
        item.setItemMeta(meta);
        return item;
    }

    public void give(Player player, Faction faction) {
        ItemStack item = create(faction);
        var leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }
}
