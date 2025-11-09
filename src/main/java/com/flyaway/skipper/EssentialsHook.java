package com.flyaway.skipper;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public class EssentialsHook {
    private final Plugin essentials;
    private Method getUserMethod;
    private Method isAfkMethod;
    private Method isVanishedMethod;
    private boolean enabled = false;

    public EssentialsHook() {
        essentials = Bukkit.getPluginManager().getPlugin("Essentials");
        if (essentials == null) {
            getLogger().warning("Essentials не найден! AFK игроки учитываться не будут.");
            return;
        }

        try {
            // Получаем класс Essentials
            Class<?> essentialsClass = Class.forName("com.earth2me.essentials.Essentials");
            Class<?> userClass = Class.forName("com.earth2me.essentials.User");

            // Получаем методы через рефлексию
            getUserMethod = essentialsClass.getMethod("getUser", Player.class);
            isAfkMethod = userClass.getMethod("isAfk");
            isVanishedMethod = userClass.getMethod("isVanished");

            enabled = true;
            getLogger().info("Essentials успешно подключен через рефлексию!");

        } catch (ClassNotFoundException | NoSuchMethodException e) {
            getLogger().warning("Не удалось найти методы Essentials через рефлексию: " + e.getMessage());
            enabled = false;
        }
    }

    public boolean isAFK(Player player) {
        if (!enabled || essentials == null) return false;

        try {
            Object user = getUserMethod.invoke(essentials, player);
            if (user == null) return false;

            return (Boolean) isAfkMethod.invoke(user);
        } catch (Exception e) {
            // Логируем только при первой ошибке чтобы не спамить в консоль
            if (!e.getMessage().contains("AFK check failed")) {
                getLogger().warning("Ошибка при проверке AFK статуса: " + e.getMessage());
            }
            return false;
        }
    }

    public boolean isVanished(Player player) {
        if (!enabled || essentials == null) return false;

        try {
            Object user = getUserMethod.invoke(essentials, player);
            if (user == null) return false;

            return (Boolean) isVanishedMethod.invoke(user);
        } catch (Exception e) {
            // Логируем только при первой ошибке
            if (!e.getMessage().contains("Vanished check failed")) {
                getLogger().warning("Ошибка при проверке Vanished статуса: " + e.getMessage());
            }
            return false;
        }
    }

    public boolean isEssentialsEnabled() {
        return enabled && essentials != null && essentials.isEnabled();
    }

    private java.util.logging.Logger getLogger() {
        return java.util.logging.Logger.getLogger("NightSkipper");
    }
}
