package com.sovereignty.commands;

import com.sovereignty.caravan.CaravanManager;
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
 * Handles the {@code /caravan} command and its subcommands.
 *
 * <h3>Subcommands</h3>
 * <ul>
 *   <li>{@code send <province> <amount>} — Dispatch a trade caravan.</li>
 *   <li>{@code status}                   — View active caravans.</li>
 * </ul>
 */
public final class CaravanCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private static final List<String> SUBCOMMANDS = List.of("send", "status");

    private final CaravanManager caravanManager;

    public CaravanCommand(CaravanManager caravanManager) {
        this.caravanManager = caravanManager;
    }

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
            case "send" -> {
                if (args.length < 3) {
                    player.sendMessage(MINI.deserialize(
                            "<red>Usage: /caravan send <province> <amount></red>"));
                    yield true;
                }
                String target = args[1];
                String amount = args[2];
                player.sendMessage(MINI.deserialize(
                        "<green>✔ Trade caravan dispatched to <white>" + target
                                + "</white> carrying <white>" + amount
                                + "</white> gold.</green>\n"
                                + "<gray>The llama is on its way! Protect it from ambushes.</gray>"));
                yield true;
            }
            case "status" -> {
                player.sendMessage(MINI.deserialize(
                        "\n<gradient:#8B4513:#DAA520><bold>═══ Active Caravans ═══</bold></gradient>\n"
                                + "<gray>No active caravans. Use <white>/caravan send</white> "
                                + "to dispatch one.</gray>\n"));
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
                "\n<gradient:#8B4513:#DAA520><bold>═══ Caravan Commands ═══</bold></gradient>\n"
                        + "<white>/caravan send <province> <amount></white> <gray>— Dispatch a trade caravan</gray>\n"
                        + "<white>/caravan status</white> <gray>— View active caravans</gray>\n"
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
