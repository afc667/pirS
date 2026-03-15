package com.sovereignty.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles the {@code /war} command and its subcommands.
 *
 * <h3>Subcommands</h3>
 * <ul>
 *   <li>{@code declare <province>} — Declare war on a rival province.</li>
 *   <li>{@code status}             — View active wars.</li>
 *   <li>{@code surrender}          — Surrender in the current war.</li>
 * </ul>
 */
public final class WarCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private static final List<String> SUBCOMMANDS = List.of("declare", "status", "surrender");

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MINI.deserialize("<red>This command can only be used by players.</red>"));
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "declare" -> {
                if (args.length < 2) {
                    player.sendMessage(MINI.deserialize(
                            "<red>Usage: /war declare <province></red>"));
                    yield true;
                }
                player.sendMessage(MINI.deserialize(
                        "<red>⚔ <bold>WAR DECLARED</bold> against <white>"
                                + args[1] + "</white>!</red>\n"
                                + "<gray>A 24-hour preparation phase has begun.</gray>"));
                yield true;
            }
            case "status" -> {
                player.sendMessage(MINI.deserialize(
                        "\n<red><bold>═══ Active Wars ═══</bold></red>\n"
                                + "<gray>No active wars. Use <white>/war declare</white> "
                                + "to start one (requires Casus Belli).</gray>\n"));
                yield true;
            }
            case "surrender" -> {
                player.sendMessage(MINI.deserialize(
                        "<gray>You are not currently in a war.</gray>"));
                yield true;
            }
            default -> {
                sendUsage(player);
                yield true;
            }
        };
    }

    private void sendUsage(Player player) {
        player.sendMessage(MINI.deserialize(
                "\n<red><bold>═══ War Commands ═══</bold></red>\n"
                        + "<white>/war declare <province></white> <gray>— Declare war (requires Casus Belli)</gray>\n"
                        + "<white>/war status</white> <gray>— View active wars</gray>\n"
                        + "<white>/war surrender</white> <gray>— Surrender in a war</gray>\n"
        ));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
