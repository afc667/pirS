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
 * Handles the {@code /dip} (diplomacy) command and subcommands.
 *
 * <h3>Subcommands</h3>
 * <ul>
 *   <li>{@code status}              — View diplomatic relations for your province.</li>
 *   <li>{@code propose <type> <province>} — Propose a NAP or alliance.</li>
 *   <li>{@code accept}              — Accept a pending diplomatic proposal.</li>
 *   <li>{@code decline}             — Decline a pending diplomatic proposal.</li>
 *   <li>{@code rival <province>}    — Declare a province as a rival.</li>
 * </ul>
 */
public final class DiplomacyCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private static final List<String> SUBCOMMANDS =
            List.of("status", "propose", "accept", "decline", "rival");
    private static final List<String> RELATION_TYPES =
            List.of("nap", "ally");

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
            case "status" -> {
                player.sendMessage(MINI.deserialize(
                        "\n<gradient:#87CEEB:#4169E1><bold>═══ Diplomatic Relations ═══</bold></gradient>\n"
                                + "<gray>No active treaties. Use <white>/dip propose</white> to initiate diplomacy.</gray>\n"));
                yield true;
            }
            case "propose" -> {
                if (args.length < 3) {
                    player.sendMessage(MINI.deserialize(
                            "<red>Usage: /dip propose <nap|ally> <province></red>"));
                    yield true;
                }
                String type = args[1].toUpperCase();
                String target = args[2];
                player.sendMessage(MINI.deserialize(
                        "<green>✔ Diplomatic proposal (<white>" + type
                                + "</white>) sent to <white>" + target + "</white>.</green>"));
                yield true;
            }
            case "accept" -> {
                player.sendMessage(MINI.deserialize(
                        "<gray>No pending proposals to accept.</gray>"));
                yield true;
            }
            case "decline" -> {
                player.sendMessage(MINI.deserialize(
                        "<gray>No pending proposals to decline.</gray>"));
                yield true;
            }
            case "rival" -> {
                if (args.length < 2) {
                    player.sendMessage(MINI.deserialize(
                            "<red>Usage: /dip rival <province></red>"));
                    yield true;
                }
                player.sendMessage(MINI.deserialize(
                        "<red>⚔ <white>" + args[1]
                                + "</white> has been declared a <bold>RIVAL</bold>!</red>"));
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
                "\n<gradient:#87CEEB:#4169E1><bold>═══ Diplomacy Commands ═══</bold></gradient>\n"
                        + "<white>/dip status</white> <gray>— View your diplomatic relations</gray>\n"
                        + "<white>/dip propose <nap|ally> <province></white> <gray>— Propose a treaty</gray>\n"
                        + "<white>/dip accept</white> <gray>— Accept a pending proposal</gray>\n"
                        + "<white>/dip decline</white> <gray>— Decline a pending proposal</gray>\n"
                        + "<white>/dip rival <province></white> <gray>— Declare a province as a rival</gray>\n"
        ));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && "propose".equalsIgnoreCase(args[0])) {
            return RELATION_TYPES.stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
