package com.example.hardcoreplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.Random;

public class HardcorePlugin extends JavaPlugin implements Listener, CommandExecutor {

    private int sharedLives;
    private Location newSpawnLocation;

    @Override
    public void onEnable() {
        getLogger().info("HardcorePlugin has been enabled!");

        createConfig();
        loadSharedLives();

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("setmaxlives").setExecutor(this);
        getCommand("lives").setExecutor(this);
//        getCommand("resetworld").setExecutor(this); // not finished, dont work correctly
        getCommand("resetspawn").setExecutor(this);
    }

    @Override
    public void onDisable() {
        getLogger().info("HardcorePlugin has been disabled!");
    }

    private void createConfig() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }
    }

    private void loadSharedLives() {
        FileConfiguration config = getConfig();
        sharedLives = config.getInt("maxLives", 1);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean isHardcore = isHardcoreEnabled();
        player.sendMessage("Welcome, " + player.getName() + "! This is " + (isHardcore ? "a hardcore" : "not a hardcore") + " server.");
        player.sendMessage("The server has " + sharedLives + " shared lives.");

        if (sharedLives > 0) {
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        } else {
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            player.sendMessage(ChatColor.RED + "You are in spectator mode.");
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        
        if (isHardcoreEnabled()) {
            if (sharedLives > 1) { // Check if more than one life is left
                sharedLives--;
                Bukkit.broadcastMessage("Shared lives remaining: " + sharedLives);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.spigot().respawn();
                    }
                }.runTaskLater(this, 1L);
            } else {
                // The last life has been spent, put all players in spectator mode
                sharedLives = 0; // Reset the sharedLives counter
                Bukkit.broadcastMessage(ChatColor.RED + "All players have run out of shared lives, and are now in spectator mode.");
                Bukkit.getOnlinePlayers().forEach(onlinePlayer -> onlinePlayer.setGameMode(org.bukkit.GameMode.SPECTATOR));
            }
        } else {
            if (sharedLives > 1) { // Check if more than one life is left
                sharedLives--;
                Bukkit.broadcastMessage("Shared lives remaining: " + sharedLives);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.spigot().respawn();
                    }
                }.runTaskLater(this, 1L);
            } else {
                // The last life has been spent, put all players in spectator mode
                sharedLives = 0; // Reset the sharedLives counter
                Bukkit.broadcastMessage(ChatColor.RED + "All players have run out of shared lives, and are now in spectator mode.");
                Bukkit.getOnlinePlayers().forEach(onlinePlayer -> onlinePlayer.setGameMode(org.bukkit.GameMode.SPECTATOR));
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("setmaxlives")) {
            if (sender instanceof Player && sender.isOp()) {
                if (args.length != 1) {
                    sender.sendMessage(ChatColor.RED + "Usage: /setmaxlives <number>");
                    return true;
                }

                try {
                    int newMaxLives = Integer.parseInt(args[0]);
                    if (newMaxLives < 1) {
                        sender.sendMessage(ChatColor.RED + "Shared lives must be a positive integer.");
                        return true;
                    }

                    sharedLives = newMaxLives;
                    getConfig().set("maxLives", sharedLives);
                    saveConfig();
                    Bukkit.getOnlinePlayers().forEach(onlinePlayer -> onlinePlayer.sendMessage(ChatColor.GREEN + "Shared lives for the server set to " + sharedLives + "."));
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid input. Please specify a positive integer.");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("lives")) {
            sender.sendMessage(ChatColor.GREEN + "Shared lives for the entire server: " + sharedLives);
        } else if (command.getName().equalsIgnoreCase("resetworld")) {
            if (sender instanceof Player && sender.isOp()) {
            	//sender.sendMessage(ChatColor.GREEN + "This command is not working currently.");
                // Implement your world reset logic here
                resetWorld(sender);
                //sender.sendMessage(ChatColor.GREEN + "World reset in progress...");
            } else {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("resetspawn")) {
            if (sender instanceof Player && sender.isOp()) {
                Player player = (Player) sender;

                Bukkit.broadcastMessage(ChatColor.YELLOW + "Spawn reset in progress try not to move!...");
                
                // Determine the new spawn location
                determineNewSpawnLocation(player.getWorld());

                // Teleport the player to the new spawn location
                player.teleport(newSpawnLocation);

                // Set the new world spawn for all players
                setNewWorldSpawn(newSpawnLocation);

                // Teleport all other players to random locations around the first player
                teleportAllPlayersAroundNewSpawn(player);

                // Inform players about the spawn reset
                Bukkit.broadcastMessage(ChatColor.GREEN + "Spawn has been reset!");
            } else {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            }
            return true;
        }
        return false;
    }

    private boolean isHardcoreEnabled() {
        File serverPropertiesFile = new File("server.properties");
        Properties serverProperties = new Properties();

        try (FileInputStream inputStream = new FileInputStream(serverPropertiesFile)) {
            serverProperties.load(inputStream);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return "true".equals(serverProperties.getProperty("hardcore"));
    }

    private void determineNewSpawnLocation(World world) {
        Random random = new Random();
        int chunkX = random.nextInt(10000) - 5000;
        int chunkZ = random.nextInt(10000) - 5000;
        
        int x = chunkX * 16 + random.nextInt(16);
        int z = chunkZ * 16 + random.nextInt(16);
        int y = world.getHighestBlockYAt(x, z);

        newSpawnLocation = new Location(world, x, y, z);
    }

    private void setNewWorldSpawn(Location location) {
        location.getWorld().setSpawnLocation(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private void teleportAllPlayersAroundNewSpawn(Player firstPlayer) {
        World world = firstPlayer.getWorld();
        Location firstPlayerLocation = firstPlayer.getLocation();
        int radius = 20; // Adjust the radius as needed
        
        for (Player player : world.getPlayers()) {
            if (!player.equals(firstPlayer)) {
                Location randomLocation = getRandomLocationAround(firstPlayerLocation, radius);
                player.teleport(randomLocation);
            }
        }
    }

    private Location getRandomLocationAround(Location location, int radius) {
        Random random = new Random();
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = random.nextDouble() * radius;
        double x = location.getX() + distance * Math.cos(angle);
        double z = location.getZ() + distance * Math.sin(angle);
        int y = location.getWorld().getHighestBlockYAt((int) x, (int) z);
        
        return new Location(location.getWorld(), x, y, z);
    }
    
    private void resetWorld(CommandSender sender) {
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Initiating world reset. Please wait...");

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            // Iterate through all loaded worlds
            for (World world : Bukkit.getWorlds()) {
                int centerX = 0; // Set the center X coordinate of the reset area for each world
                int centerZ = 0; // Set the center Z coordinate of the reset area for each world
                int radius = 50; // Set the radius of the reset area (adjust as needed)

                int chunkRadius = radius / 16; // Calculate the chunk radius
                int startX = centerX - chunkRadius;
                int startZ = centerZ - chunkRadius;
                int endX = centerX + chunkRadius;
                int endZ = centerZ + chunkRadius;

                // Unload and reload chunks within the defined area on the main thread for each world
                Bukkit.getScheduler().runTask(this, () -> {
                    for (int x = startX; x <= endX; x++) {
                        for (int z = startZ; z <= endZ; z++) {
                            world.unloadChunk(x, z, true); // Unload the chunk
                            world.loadChunk(x, z, true); // Load the chunk again
                        }
                    }

                    // Simulate a delay to represent the reset process
                    try {
                        Thread.sleep(5000); // Simulating a 5-second reset process
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    // Once reset actions are completed, move players and notify them on the main thread
                    Bukkit.getScheduler().runTask(this, () -> {
                        movePlayersToRandomLocation(world);
                        Bukkit.broadcastMessage(ChatColor.GREEN + "World reset complete for " + world.getName() + "!");
                    });
                });
            }
        });
    }

    // Method to move players to random locations within their respective worlds
    private void movePlayersToRandomLocation(World world) {
        Random random = new Random();
        for (Player player : world.getPlayers()) {
            Location randomLocation;
            if (world.getEnvironment() == World.Environment.NORMAL) {
                // For overworld, find a random location within a specified range
                randomLocation = getRandomLocation(world, random, player.getLocation(), 100);
            } else if (world.getEnvironment() == World.Environment.NETHER) {
                // For nether, find a random location within a specified range (scaled for the nether)
                randomLocation = getRandomLocation(world, random, player.getLocation(), 50);
            } else {
                // For other dimensions (like the End), find a random location within a specified range
                randomLocation = getRandomLocation(world, random, player.getLocation(), 200);
            }
            player.teleport(randomLocation);
        }
    }

    // Method to get a random location around a center location within a specified range
    private Location getRandomLocation(World world, Random random, Location center, int range) {
        int x = center.getBlockX() + random.nextInt(range * 2) - range;
        int z = center.getBlockZ() + random.nextInt(range * 2) - range;
        int y = world.getHighestBlockYAt(x, z);
        return new Location(world, x, y, z);
    }
}
