package com.flyaway.skipper;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class NightSkipper extends JavaPlugin implements Listener {

    private EssentialsHook essentialsHook;
    private final Set<String> recentlySkipped = new HashSet<>();
    private final Map<String, BukkitRunnable> actionBarTasks = new HashMap<>();

    // Настройки
    private long dayTime = 1000;
    private long nightTime = 13000;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        essentialsHook = new EssentialsHook();
        getLogger().info("NightSkipper включен!");
    }

    @Override
    public void onDisable() {
        recentlySkipped.clear();
        Bukkit.getScheduler().cancelTasks(this); // отменяем все таски плагина
        getLogger().info("NightSkipper выключен!");
    }

    @EventHandler
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        if (!isNightTime(world) && !world.isThundering()) return;
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!nightSkipped(world)) {
                scheduleTempActionBar(world);
            }
        }, 5L);
    }

    @EventHandler
    public void onPlayerBedLeave(PlayerBedLeaveEvent event) {
        scheduleTempActionBar(event.getPlayer().getWorld());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        scheduleTempActionBar(event.getPlayer().getWorld());
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
                    for (Player player : world.getPlayers()) {
                        player.sendActionBar("");
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

        int needed = Math.max(0, (int) Math.ceil(totalPlayers * 0.5) - sleepingCount);

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
        String worldName = world.getName();
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
        return player.getGameMode() == org.bukkit.GameMode.SPECTATOR ||
               player.hasPermission("essentials.sleepingignored");
    }

    private void startSkippingNight(World world) {
        String worldName = world.getName();
        recentlySkipped.add(worldName);

        // Сначала отправляем сообщение игрокам
        for (Player player : world.getPlayers()) {
            player.sendMessage("§aНочь пропускается...");
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
        }, 40L); // 40 тиков = 2 секунды

        // Через 10 секунд снимаем защиту recentlySkipped
        Bukkit.getScheduler().runTaskLater(this, () -> recentlySkipped.remove(worldName), 200L);
    }

    private void updateSleepMessages(World world) {
        int totalPlayers = getTotalPlayers(world);
        int sleepingCount = getSleepingPlayersCount(world);
        int needed = Math.max(0, (int) Math.ceil(totalPlayers * 0.5) - sleepingCount);

        String message = String.format("§eСпят: %d/%d | Нужно ещё: %d",
                sleepingCount, totalPlayers, needed);

        for (Player player : world.getPlayers()) {
            if (player.isOnline()) player.sendActionBar(message);
        }
    }

    private boolean isNightTime(World world) {
        long time = world.getTime();
        return time >= nightTime || time < dayTime;
    }
}
