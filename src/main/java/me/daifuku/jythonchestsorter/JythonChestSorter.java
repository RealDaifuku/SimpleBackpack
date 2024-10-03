package me.daifuku.jythonchestsorter;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class JythonChestSorter extends JavaPlugin implements Listener, CommandExecutor {

    // HashMap to store each player's backpack inventory
    private final Map<UUID, Inventory> playerBackpacks = new HashMap<>();
    private static final String SORT_BUTTON_NAME = "§aSort Items";
    private static final List<String> SORT_BUTTON_LORE = Arrays.asList("Click to sort your items.", "§7[SortButton]");

    @Override
    public void onEnable() {
        // Register this class as the event listener
        Bukkit.getPluginManager().registerEvents(this, this);

        // Register the /backpack command
        this.getCommand("backpack").setExecutor(this);

        System.out.println("Plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        System.out.println("Plugin has been disabled!");
    }

    @EventHandler
    public void onPlayerWalk(PlayerMoveEvent event) {
        // Check if the player actually moved a block (to avoid triggering on every small motion)
        if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                event.getFrom().getBlockY() != event.getTo().getBlockY() ||
                event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {

            // Get the player and the world they are in
            Player player = event.getPlayer();
            World world = player.getWorld();

            // Spawn flame particles at the player's location
            world.spawnParticle(Particle.FLAME, player.getLocation(), 10, 0.2, 0.2, 0.2, 0.01);

            // Check if the player is not at full health
            if (player.getHealth() < player.getMaxHealth()) {
                // Heal the player by 1 health point (half a heart)
                player.setHealth(Math.min(player.getHealth() + 1, player.getMaxHealth()));
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if the command is "/backpack" and the sender is a player
        if (command.getName().equalsIgnoreCase("backpack") && sender instanceof Player) {
            Player player = (Player) sender;
            UUID playerUUID = player.getUniqueId();

            // Retrieve the player's backpack if it exists, otherwise create a new one
            Inventory backpack = playerBackpacks.getOrDefault(playerUUID, Bukkit.createInventory(player, 27, "Backpack")); // 27 slots for a larger backpack

            // Check if the "Sort" button is already in the first slot to prevent duplicates
            ItemStack firstSlotItem = backpack.getItem(0);
            if (!isSortButton(firstSlotItem)) {
                // Create the "Sort" button item and set it in the first slot
                ItemStack sortButton = new ItemStack(Material.CLOCK);
                ItemMeta meta = sortButton.getItemMeta();
                meta.setDisplayName(SORT_BUTTON_NAME); // Green name with §a for color code
                meta.setLore(SORT_BUTTON_LORE); // Unique identifier in the lore

                // Add a dummy enchantment and hide its attributes to make it look enchanted
                meta.addEnchant(Enchantment.LUCK, 1, true);  // Enchant with LUCK (dummy enchantment)
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);   // Hide the enchantment attributes

                sortButton.setItemMeta(meta);
                backpack.setItem(0, sortButton);
            }

            // Store the backpack in the map in case it's a new one
            playerBackpacks.put(playerUUID, backpack);

            // Open the backpack for the player
            player.openInventory(backpack);

            return true; // Command was successfully executed
        }
        return false;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Save the contents of the backpack when the player closes it
        if (event.getView().getTitle().equals("Backpack")) {
            Player player = (Player) event.getPlayer();
            UUID playerUUID = player.getUniqueId();
            Inventory backpack = event.getInventory();

            // Save the backpack inventory in the HashMap
            playerBackpacks.put(playerUUID, backpack);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Check if the clicked inventory is the backpack and if the clicked slot is the "Sort" button
        if (event.getView().getTitle().equals("Backpack")) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && isSortButton(clickedItem)) {
                // Prevent the player from taking the sort button
                event.setCancelled(true);

                // Get the inventory to sort
                Inventory inventory = event.getInventory();

                // Merge items before sorting
                mergeItems(inventory);

                // Sort the items in the backpack (excluding slot 0)
                sortBackpackInventory(inventory);

                // Send a message to the player
                Player player = (Player) event.getWhoClicked();
                player.sendMessage("§aYour backpack has been sorted and items have been merged!");
            }
        }
    }

    // Function to merge items in the backpack inventory (excluding the first slot for the sort button)
    private void mergeItems(Inventory inventory) {
        Map<String, ItemStack> mergedItems = new HashMap<>();

        // Iterate through the inventory to merge items, excluding the "Sort" button in slot 0
        for (int i = 1; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || isSortButton(item)) {
                continue; // Skip empty slots and the "Sort" button
            }

            // Create a unique key for each item based on its type and metadata (to handle similar items)
            String itemKey = generateItemKey(item);

            // If the item is already in the mergedItems map, increase the amount
            if (mergedItems.containsKey(itemKey)) {
                ItemStack existingItem = mergedItems.get(itemKey);
                int newAmount = existingItem.getAmount() + item.getAmount();

                // Check if newAmount exceeds the max stack size and handle overflow
                if (newAmount <= item.getMaxStackSize()) {
                    existingItem.setAmount(newAmount); // Combine into a single stack
                } else {
                    // Set the existing stack to max and keep the remainder in a new stack
                    existingItem.setAmount(item.getMaxStackSize());
                    item.setAmount(newAmount - item.getMaxStackSize());
                    mergedItems.put(itemKey + "-overflow", item.clone());
                }
            } else {
                // If the item is not yet in the map, add it
                mergedItems.put(itemKey, item.clone());
            }
        }

        // Clear the inventory (excluding the sort button in slot 0)
        for (int i = 1; i < inventory.getSize(); i++) {
            inventory.setItem(i, null);
        }

        // Add merged items back into the inventory starting from slot 1
        int slot = 1;
        for (ItemStack mergedItem : mergedItems.values()) {
            if (slot < inventory.getSize()) {
                inventory.setItem(slot++, mergedItem);
            }
        }
    }

    // Helper function to generate a unique key for each item based on its type and metadata
    private String generateItemKey(ItemStack item) {
        return item.getType().toString() + item.getDurability() + (item.getItemMeta() != null ? item.getItemMeta().toString() : "");
    }

    // Function to sort the backpack inventory items (excluding the first slot for the sort button)
    private void sortBackpackInventory(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();

        // Create an array to hold the non-null items and exclude the "Sort" button in slot 0
        ItemStack[] itemsToSort = Arrays.stream(contents)
                .filter(item -> item != null && !isSortButton(item)) // Exclude null items and the sort button
                .toArray(ItemStack[]::new);

        // Sort the items using a simple comparator (alphabetical order by type and amount)
        Arrays.sort(itemsToSort, (item1, item2) -> {
            if (item1.getType() == item2.getType()) {
                return Integer.compare(item1.getAmount(), item2.getAmount());
            } else {
                return item1.getType().toString().compareTo(item2.getType().toString());
            }
        });

        // Clear the inventory and put back the sorted items, excluding the sort button
        for (int i = 1; i < inventory.getSize(); i++) {
            inventory.setItem(i, null); // Clear the inventory (skip slot 0)
        }

        // Add the sorted items back, starting from slot 1
        for (int i = 0; i < itemsToSort.length; i++) {
            inventory.setItem(i + 1, itemsToSort[i]);
        }
    }

    // Helper method to check if an item is the "Sort" button based on name and lore
    private boolean isSortButton(ItemStack item) {
        if (item == null || item.getType() != Material.CLOCK) return false;  // Check if the item is not a clock or is null
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return false;  // Check if item has no meta or display name
        if (!item.getItemMeta().getDisplayName().equals(SORT_BUTTON_NAME)) return false;  // Check the display name
        return item.getItemMeta().hasLore() && item.getItemMeta().getLore().equals(SORT_BUTTON_LORE);  // Check if the lore matches the unique identifier
    }
}
