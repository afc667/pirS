package com.sovereignty.commands;

import com.sovereignty.managers.ProvinceManager;
import com.sovereignty.mercenary.MercenaryManager;
import com.sovereignty.models.ChunkPosition;
import com.sovereignty.models.Province;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Handles the {@code /hire} command.
 *
 * <p>Usage: {@code /hire <player> <amount> <hours>}
 *
 * <p>Only province Lords can hire mercenaries. The target player must
 * have mercenary mode enabled ({@code /mercenary on}).
 */
public final class HireCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final MercenaryManager mercenaryManager;
    private final ProvinceManager provinceManager;

    public HireCommand(MercenaryManager mercenaryManager, ProvinceManager provinceManager) {
        this.mercenaryManager = mercenaryManager;
        this.provinceManager = provinceManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MINI.deserialize("<red>This command can only be used by players.</red>"));
            return true;
        }

        if (args.length < 3) {
            player.sendMessage(MINI.deserialize(
                    "<red>Usage: /hire <player> <amount> <hours></red>\n"
                            + "<gray>Example: /hire Steve 500 24</gray>"));
            return true;
        }

        // Find the hiring Lord's province
        Optional<Province> province = findPlayerProvince(player);
        if (province.isEmpty()) {
            player.sendMessage(MINI.deserialize(
                    "<red>✖ You must own a province to hire mercenaries.</red>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(MINI.deserialize(
                    "<red>✖ Player <white>" + args[0] + "</white> is not online.</red>"));
            return true;
        }

        double amount;
        int hours;
        try {
            amount = Double.parseDouble(args[1]);
            hours = Integer.parseInt(args[2]);
            if (amount <= 0 || hours <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage(MINI.deserialize(
                    "<red>✖ Amount and hours must be positive numbers.</red>"));
            return true;
        }

        mercenaryManager.proposeHire(player, target, amount, hours, province.get());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return null; // Default player name completion
        }
        if (args.length == 2) {
            return List.of("<amount>");
        }
        if (args.length == 3) {
            return List.of("<hours>");
        }
        return Collections.emptyList();
    }

    private Optional<Province> findPlayerProvince(Player player) {
        ChunkPosition pos = new ChunkPosition(
                player.getWorld().getName(),
                player.getLocation().getBlockX() >> 4,
                player.getLocation().getBlockZ() >> 4);
        Optional<Province> atChunk = provinceManager.getProvinceAt(pos);
        if (atChunk.isPresent() && atChunk.get().getOwnerUuid().equals(player.getUniqueId())) {
            return atChunk;
        }
        return Optional.empty();
    }
}
