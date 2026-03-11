package com.flyaway.skipper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class NightSkipper extends JavaPlugin implements Listener {
    private EssentialsHook essentialsHook;
    private FileConfiguration config;

    private final Set<String> recentlySkipped = new HashSet<>();
    private final Map<String, Integer> oldSleepPercentages = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private List<String> enabledWorlds;
    private long dayTime;
    private long nightTime;
    private double sleepPercentage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = migrateMainConfig();
        loadData();

        for (World world : Bukkit.getWorlds()) {
            if (enabledWorlds.contains(world.getName())) {
                Integer value = world.getGameRuleValue(GameRule.PLAYERS_SLEEPING_PERCENTAGE);
                int oldValue = (value != null) ? value : 100;
                oldSleepPercentages.put(world.getName(), oldValue);

                world.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, 101);
            }
        }

        getServer().getPluginManager().registerEvents(this, this);

        essentialsHook = new EssentialsHook();

        NightSkipperCommand cmd = new NightSkipperCommand(this);
        getCommand("nskipper").setExecutor(cmd);
        getCommand("nskipper").setTabCompleter(cmd);

        getLogger().info("NightSkipper enabled. Worlds: " + enabledWorlds);
    }

    @Override
    public void onDisable() {
        for (World world : Bukkit.getWorlds()) {
            if (enabledWorlds.contains(world.getName())) {
                Integer oldValue = oldSleepPercentages.get(world.getName());
                if (oldValue != null) {
                    world.setGameRule(org.bukkit.GameRule.PLAYERS_SLEEPING_PERCENTAGE, oldValue);
                }
            }
        }
        recentlySkipped.clear();
    }

    public void reload() {
        reloadConfig();
        config = getConfig();
        loadData();
        essentialsHook = new EssentialsHook();
    }

    private void loadData() {
        enabledWorlds = config.getStringList("enabled-worlds");

        dayTime = config.getLong("day-time", 0);
        nightTime = config.getLong("night-time", 12516);

        sleepPercentage = config.getDouble("sleep-percentage", 0.5);
        sleepPercentage = Math.max(0, Math.min(1.0, sleepPercentage));
    }

    public FileConfiguration migrateMainConfig() {
        File configFile = new File(getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            saveDefaultConfig();
        }

        FileConfiguration config = getConfig();

        YamlConfiguration defaultConfig;
        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(getResource("config.yml")),
                StandardCharsets.UTF_8
        )) {
            defaultConfig = YamlConfiguration.loadConfiguration(reader);
        } catch (Exception e) {
            getLogger().severe("Failed to load default config.yml");
            return config;
        }

        int currentVersion = defaultConfig.getInt("version", 1);
        int fileVersion = config.getInt("version", 0);

        if (fileVersion < currentVersion) {
            getLogger().info("Updating config.yml from version "
                    + fileVersion + " to " + currentVersion);

            config.setDefaults(defaultConfig);
            config.options().copyDefaults(true);
            config.set("version", currentVersion);

            saveConfig();
        }

        return config;
    }

    @EventHandler
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;

        Bukkit.getScheduler().runTaskLater(this, () -> updateSleep(event.getPlayer().getWorld()), 1L);
    }

    @EventHandler
    public void onPlayerBedLeave(PlayerBedLeaveEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> updateSleep(event.getBed().getWorld()), 1L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        Bukkit.getScheduler().runTaskLater(this, () -> updateSleep(world), 1L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> updateSleep(event.getPlayer().getWorld()), 1L);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        updateSleep(event.getFrom());

        Bukkit.getScheduler().runTaskLater(this, () -> updateSleep(event.getPlayer().getWorld()), 1L);
    }

    private void updateSleep(World world) {
        if (!isWorldEnabled(world)) return;
        if (!isNightTime(world) && !world.isThundering()) return;

        if (recentlySkipped.contains(world.getName())) return;

        SleepData data = getSleepData(world);

        if (data.total == 0) return;

        int required = (int) Math.ceil(data.total * sleepPercentage);
        int needed = Math.max(0, required - data.sleeping);

        sendSleepMessage(world, data.sleeping, data.total, needed);

        if (data.sleeping >= required) {
            startSkippingNight(world);
        }
    }

    private SleepData getSleepData(World world) {
        int total = 0;
        int sleeping = 0;

        for (Player player : world.getPlayers()) {

            if (essentialsHook.isVanished(player) || shouldIgnorePlayer(player)) {
                continue;
            }

            total++;

            if (player.isSleeping() || essentialsHook.isAFK(player)) {
                sleeping++;
            }
        }

        return new SleepData(total, sleeping);
    }

    private static class SleepData {
        int total;
        int sleeping;

        SleepData(int total, int sleeping) {
            this.total = total;
            this.sleeping = sleeping;
        }
    }

    private void startSkippingNight(World world) {
        String worldName = world.getName();

        recentlySkipped.add(worldName);

        Component component = miniMessage.deserialize(getMessage("night-skipping"));
        if (component != Component.empty()) {
            for (Player player : world.getPlayers()) {
                player.sendMessage(component);
            }
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            world.setTime(dayTime);
            world.setStorm(false);
            world.setThundering(false);

            for (Player player : world.getPlayers()) {
                if (player.isSleeping()) {
                    player.wakeup(false);
                }
            }

        }, 60L);

        Bukkit.getScheduler().runTaskLater(this,
                () -> recentlySkipped.remove(worldName),
                200L
        );
    }

    private void sendSleepMessage(World world, int sleeping, int total, int needed) {
        String template = getMessage("sleep-progress");
        if (template.isEmpty()) return;

        String raw = template
                .replace("{sleeping}", String.valueOf(sleeping))
                .replace("{total}", String.valueOf(total))
                .replace("{needed}", String.valueOf(needed));

        Component component = miniMessage.deserialize(raw);

        for (Player player : world.getPlayers()) {
            player.sendActionBar(component);
        }
    }

    private boolean isWorldEnabled(World world) {
        return enabledWorlds.contains(world.getName());
    }

    private boolean shouldIgnorePlayer(Player player) {
        return player.getGameMode() == GameMode.SPECTATOR
                || player.hasPermission("essentials.sleepingignored");
    }

    private boolean isNightTime(World world) {
        long time = world.getTime();

        return time >= nightTime || time < dayTime;
    }

    public String getMessage(String key) {
        return config.getString("messages." + key, "<red>not found: messages." + key);
    }
}
