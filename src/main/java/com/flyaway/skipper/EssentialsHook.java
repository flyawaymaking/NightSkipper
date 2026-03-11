package com.flyaway.skipper;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

public class EssentialsHook {
    private final Essentials essentials;
    private boolean enabled = false;

    public EssentialsHook() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("Essentials");
        Logger logger = Logger.getLogger("NightSkipper");
        if (!(plugin instanceof Essentials)) {
            logger.warning("Essentials не найден! AFK игроки учитываться не будут.");
            essentials = null;
            return;
        }

        essentials = (Essentials) plugin;
        enabled = true;
        logger.info("Essentials успешно подключен через API!");
    }

    public boolean isAFK(Player player) {
        if (!enabled) return false;

        User user = essentials.getUser(player);
        if (user == null) return false;

        return user.isAfk();
    }

    public boolean isVanished(Player player) {
        if (!enabled) return false;

        User user = essentials.getUser(player);
        if (user == null) return false;

        return user.isVanished();
    }
}
