package com.flyaway.skipper;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NightSkipperCommand implements CommandExecutor, TabCompleter {
    private final NightSkipper plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public NightSkipperCommand(NightSkipper plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("nskipper.admin")) {
            String msg = plugin.getMessage("no-permission");
            sender.sendMessage(miniMessage.deserialize(msg));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reload();
            String msg = plugin.getMessage("reload-success");
            sender.sendMessage(miniMessage.deserialize(msg));
            return true;
        }

        String msg = plugin.getMessage("reload-usage");
        sender.sendMessage(miniMessage.deserialize(msg));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("nskipper.admin")) {
            List<String> completions = new ArrayList<>();
            if ("reload".startsWith(args[0].toLowerCase())) {
                completions.add("reload");
            }
            return completions;
        }

        return Collections.emptyList();
    }
}
