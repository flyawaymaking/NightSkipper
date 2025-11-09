package com.flyaway.skipper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class NightSkipper extends JavaPlugin implements Listener {

    private EssentialsHook essentialsHook;
    private final Set<String> recentlySkipped = new HashSet<>();
    private final Map<String, BukkitRunnable> actionBarTasks = new HashMap<>();

    // Конфигурация
    private List<String> enabledWorlds;
    private long dayTime;
    private long nightTime;
    private double sleepPercentage;
    private String nightSkippingMessage;
    private String sleepFormatMessage;

    @Override
    public void onEnable() {
        // Загружаем конфиг
        saveDefaultConfig();
        loadConfig();

        getServer().getPluginManager().registerEvents(this, this);
        essentialsHook = new EssentialsHook();
        getLogger().info("NightSkipper включен! Разрешённые миры: " + enabledWorlds);
    }

    @Override
    public void onDisable() {
        recentlySkipped.clear();
        actionBarTasks.values().forEach(BukkitRunnable::cancel);
        actionBarTasks.clear();
        Bukkit.getScheduler().cancelTasks(this);
        getLogger().info("NightSkipper выключен!");
    }

    private void loadConfig() {
        reloadConfig();
        FileConfiguration config = getConfig();

        // Загружаем настройки
        enabledWorlds = config.getStringList("enabled-worlds");
        dayTime = config.getLong("day-time", 1000);
        nightTime = config.getLong("night-time", 13000);
        sleepPercentage = config.getDouble("sleep-percentage", 0.5);
        nightSkippingMessage = config.getString("messages.night-skipping", "<green>Ночь пропускается...");
        sleepFormatMessage = config.getString("messages.sleep-format", "<yellow>Спят: <gold>%d/%d</gold> | Нужно ещё: <red>%d</red>");

        // Валидация значений
        sleepPercentage = Math.max(0.1, Math.min(1.0, sleepPercentage));
    }

    @EventHandler
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        if (!isWorldEnabled(world) || !isNightTime(world) && !world.isThundering()) return;
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!nightSkipped(world)) {
                scheduleTempActionBar(world);
            }
        }, 5L);
    }

    @EventHandler
    public void onPlayerBedLeave(PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        if (!isWorldEnabled(world) || !isNightTime(world) && !world.isThundering()) return;
        scheduleTempActionBar(world);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        if (!isWorldEnabled(world) || !isNightTime(world) && !world.isThundering()) return;
        scheduleTempActionBar(world);
    }

    private boolean isWorldEnabled(World world) {
        return enabledWorlds.contains(world.getName());
    }

    private void scheduleTempActionBar(World world) {
        String worldName = world.getName();
        if (recentlySkipped.contains(worldName)) return;

        if (actionBarTasks.containsKey(worldName)) {
            actionBarTasks.get(worldName).cancel();
        }

        BukkitRunnable runnable = new BukkitRunnable() {
            int ticksPassed = 0;

            @Override
            public void run() {
                updateSleepMessages(world);
                ticksPassed += 10;
                if (ticksPassed >= 40) { // 2 секунды
                    Component empty = Component.empty();
                    for (Player player : world.getPlayers()) {
                        player.sendActionBar(empty);
                    }
                    actionBarTasks.remove(worldName);
                    cancel();
                }
            }
        };

        runnable.runTaskTimer(this, 0L, 10L);
        actionBarTasks.put(worldName, runnable);
    }

    private boolean nightSkipped(World world) {
        String worldName = world.getName();
        if (recentlySkipped.contains(worldName)) return true;

        int totalPlayers = getTotalPlayers(world);
        int sleepingCount = getSleepingPlayersCount(world);

        int needed = Math.max(0, (int) Math.ceil(totalPlayers * sleepPercentage) - sleepingCount);

        if (needed == 0) {
            startSkippingNight(world);
            return true;
        }
        return false;
    }

    private int getTotalPlayers(World world) {
        int count = 0;
        for (Player player : world.getPlayers()) {
            if (!essentialsHook.isVanished(player) && !shouldIgnorePlayer(player)) {
                count++;
            }
        }
        return count;
    }

    private int getSleepingPlayersCount(World world) {
        int count = 0;
        for (Player player : world.getPlayers()) {
            if (!essentialsHook.isVanished(player) && !shouldIgnorePlayer(player)) {
                if (player.isSleeping() || essentialsHook.isAFK(player)) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean shouldIgnorePlayer(Player player) {
        return player.getGameMode() == org.bukkit.GameMode.SPECTATOR || player.hasPermission("essentials.sleepingignored");
    }

    private void startSkippingNight(World world) {
        String worldName = world.getName();
        recentlySkipped.add(worldName);

        // Отправляем сообщение игрокам
        for (Player player : world.getPlayers()) {
            player.sendMessage(nightSkippingMessage);
        }

        // Через 2 секунды выполняем фактический пропуск ночи
        Bukkit.getScheduler().runTaskLater(this, () -> {
            world.setTime(dayTime);
            world.setStorm(false);
            world.setThundering(false);

            // Будим всех спящих
            for (Player player : world.getPlayers()) {
                if (player.isSleeping()) player.wakeup(false);
            }
        }, 40L);

        // Через 10 секунд снимаем защиту recentlySkipped
        Bukkit.getScheduler().runTaskLater(this, () -> recentlySkipped.remove(worldName), 200L);
    }

    private void updateSleepMessages(World world) {
        int totalPlayers = getTotalPlayers(world);
        int sleepingCount = getSleepingPlayersCount(world);
        int needed = Math.max(0, (int) Math.ceil(totalPlayers * sleepPercentage) - sleepingCount);

        String rawMessage = String.format(sleepFormatMessage, sleepingCount, totalPlayers, needed);

        Component component = MiniMessage.miniMessage().deserialize(rawMessage);

        for (Player player : world.getPlayers()) {
            if (player.isOnline()) player.sendActionBar(component);
        }
    }

    private boolean isNightTime(World world) {
        long time = world.getTime();
        return time >= nightTime || time < dayTime;
    }
}
