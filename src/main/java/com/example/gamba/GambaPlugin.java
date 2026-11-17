package com.example.gamba;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class GambaPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<String, Location> crates = new HashMap<>();
    private final Random random = new Random();

    @Override
    public void onEnable() {
        getCommand("gamba").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);

        loadCrates();
        getLogger().info("GambaPlugin byl úspěšně spuštěn!");
    }

    @Override
    public void onDisable() {
        saveCrates();
    }

    private void loadCrates() {
        if (getConfig().contains("crates")) {
            ConfigurationSection section = getConfig().getConfigurationSection("crates");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    Location loc = getConfig().getLocation("crates." + key);
                    if (loc != null) {
                        crates.put(key.toLowerCase(), loc);
                    }
                }
            }
        }
    }

    private void saveCrates() {
        getConfig().set("crates", null);
        for (Map.Entry<String, Location> entry : crates.entrySet()) {
            getConfig().set("crates." + entry.getKey(), entry.getValue());
        }
        saveConfig();
    }

    private ItemStack createKeyItem(String crateName, int amount) {
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK, amount);
        ItemMeta meta = key.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "🔑 Klíč k bedně: " + ChatColor.YELLOW + crateName);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Klikni tímto klíčem na bednu");
            lore.add(ChatColor.GRAY + "'" + crateName + "' pro otevření!");
            meta.setLore(lore);
            key.setItemMeta(meta);
        }
        return key;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        String matchedCrate = null;
        for (Map.Entry<String, Location> entry : crates.entrySet()) {
            if (entry.getValue().equals(clickedBlock.getLocation())) {
                matchedCrate = entry.getKey();
                break;
            }
        }

        if (matchedCrate == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        String expectedKeyName = ChatColor.GOLD + "🔑 Klíč k bedně: " + ChatColor.YELLOW + matchedCrate;
        if (itemInHand.getType() != Material.TRIPWIRE_HOOK || !itemInHand.hasItemMeta() || 
            !itemInHand.getItemMeta().hasDisplayName() || !itemInHand.getItemMeta().getDisplayName().equals(expectedKeyName)) {
            player.sendMessage(ChatColor.RED + "Na otevření této bedny potřebuješ správný klíč!");
            return;
        }

        itemInHand.setAmount(itemInHand.getAmount() - 1);

        player.playSound(clickedBlock.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 1f);
        
        int chance = random.nextInt(100);
        ItemStack reward;
        String rewardDesc;

        if (chance < 15) {
            reward = new ItemStack(Material.NETHERITE_INGOT, 1);
            rewardDesc = ChatColor.DARK_PURPLE + "1x Netherite Ingot!";
        } else if (chance < 50) {
            reward = new ItemStack(Material.DIAMOND, 8);
            rewardDesc = ChatColor.AQUA + "8x Diamant!";
        } else {
            reward = new ItemStack(Material.GOLDEN_APPLE, 3);
            rewardDesc = ChatColor.GOLD + "3x Zlaté jablko!";
        }

        player.getInventory().addItem(reward);
        player.sendMessage(ChatColor.GREEN + "🎉 Otevřel jsi bednu " + matchedCrate + " a získal: " + rewardDesc);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("gamba.admin")) {
            sender.sendMessage(ChatColor.RED + "Na tento příkaz nemáš práva!");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "--- GambaPlugin Příkazy ---");
            sender.sendMessage(ChatColor.GOLD + "/gamba create <název> " + ChatColor.GRAY + "- Vytvoří bednu na bloku, kam se díváš");
            sender.sendMessage(ChatColor.GOLD + "/gamba givekey <hráč> <název> [počet] " + ChatColor.GRAY + "- Dá klíč hráči");
            return true;
        }

        String action = args[0].toLowerCase();

        if (action.equals("create")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Tento příkaz mohou používat pouze hráči.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Použití: /gamba create <název_bedny>");
                return true;
            }
            Player player = (Player) sender;
            Block block = player.getTargetBlockExact(5);
            if (block == null || block.getType() == Material.AIR) {
                player.sendMessage(ChatColor.RED + "Musíš se dívat na blok, kde chceš mít bednu!");
                return true;
            }

            String crateName = args[1].toLowerCase();
            crates.put(crateName, block.getLocation());
            saveCrates();

            block.setType(Material.ENDER_CHEST);
            player.sendMessage(ChatColor.GREEN + "Úspěšně jsi vytvořil bednu '" + crateName + "'!");
            return true;
        }

        if (action.equals("givekey")) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Použití: /gamba givekey <hráč> <název_bedny> [počet]");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Hráč není online.");
                return true;
            }
            String crateName = args[2].toLowerCase();
            int amount = 1;
            if (args.length > 3) {
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException ignored) {}
            }

            ItemStack key = createKeyItem(crateName, amount);
            target.getInventory().addItem(key);
            sender.sendMessage(ChatColor.GREEN + "Dal jsi " + amount + "x klíč pro bednu '" + crateName + "' hráči " + target.getName());
            target.sendMessage(ChatColor.GREEN + "Dostal jsi klíč k bedně '" + crateName + "'!");
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Neznámý příkaz.");
        return true;
    }
}
