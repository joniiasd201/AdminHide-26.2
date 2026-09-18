package de.adminhide;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

@SuppressWarnings("deprecation")
public final class AdminHide extends JavaPlugin implements Listener, CommandExecutor {

    private final Set<UUID> hidden = new CopyOnWriteArraySet<>();

    @Override
    public void onEnable() {
        hidden.clear();
        for (String id : getConfig().getStringList("hidden")) {
            try {
                hidden.add(UUID.fromString(id));
            } catch (IllegalArgumentException ignored) {
            }
        }

        getCommand("hide").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("AdminHide aktiviert!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler koennen diesen Befehl nutzen!");
            return true;
        }
        if (!player.hasPermission("adminhide.use")) {
            player.sendMessage(ChatColor.RED + "Du hast keine Rechte dafuer!");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (hidden.contains(p.getUniqueId())) names.add(p.getName());
            }
            player.sendMessage(ChatColor.GOLD + "Versteckte Spieler: "
                    + (names.isEmpty() ? ChatColor.GRAY + "keine"
                    : ChatColor.YELLOW + String.join(", ", names)));
            return true;
        }

        if (hidden.contains(player.getUniqueId())) {
            hidden.remove(player.getUniqueId());
            saveHidden();
            applyVisibleState(player);
            player.sendMessage(ChatColor.GREEN + "Du bist jetzt wieder sichtbar!");
            sendFakeMessage(player, ChatColor.YELLOW + player.getName() + " hat den Server betreten");
        } else {
            hidden.add(player.getUniqueId());
            saveHidden();
            applyHiddenState(player);
            player.sendMessage(ChatColor.GREEN + "Du bist jetzt komplett versteckt!");
            player.sendMessage(ChatColor.GRAY + "Unsichtbar in der Welt und Tab-Liste.");
            sendFakeMessage(player, ChatColor.YELLOW + player.getName() + " hat den Server verlassen");
        }
        return true;
    }

    private void applyHiddenState(Player target) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(target)) continue;
            if (online.hasPermission("adminhide.see")) continue;
            online.hidePlayer(this, target);
        }
    }

    private void applyVisibleState(Player target) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(this, target);
        }
    }

    private void sendFakeMessage(Player except, String message) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(except) && !online.hasPermission("adminhide.see")) {
                online.sendMessage(message);
            }
        }
    }

    private void saveHidden() {
        List<String> ids = new ArrayList<>();
        for (UUID id : hidden) ids.add(id.toString());
        getConfig().set("hidden", ids);
        saveConfig();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joiner = event.getPlayer();

        if (hidden.contains(joiner.getUniqueId())) {
            event.setJoinMessage(null);
            applyHiddenState(joiner);
            Bukkit.getScheduler().runTask(this, () -> {
                if (joiner.isOnline() && hidden.contains(joiner.getUniqueId())) {
                    applyHiddenState(joiner);
                }
            });
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(joiner)
                    && hidden.contains(online.getUniqueId())
                    && !joiner.hasPermission("adminhide.see")) {
                joiner.hidePlayer(this, online);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (hidden.contains(event.getPlayer().getUniqueId())) {
            event.setQuitMessage(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p && hidden.contains(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player p && hidden.contains(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
