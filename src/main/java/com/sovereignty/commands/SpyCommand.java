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
 * Handles the {@code /spy} command and its subcommands.
 *
 * <h3>Subcommands</h3>
 * <ul>
 *   <li>{@code sabotage} — Begin a sabotage operation (requires Forged Passport).</li>
 *   <li>{@code info}     — View espionage information for the current location.</li>
 * </ul>
 */
public final class SpyCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private static final List<String> SUBCOMMANDS = List.of("sabotage", "info");

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
            case "sabotage" -> {
                player.sendMessage(MINI.deserialize(
                        "<dark_purple>🕵 To begin sabotage, sneak near an enemy Province Core "
                                + "while holding a <white>Forged Passport</white>.</dark_purple>\n"
                                + "<gray>The operation takes 15 seconds and drains enemy stability.</gray>"));
                yield true;
            }
            case "info" -> {
                player.sendMessage(MINI.deserialize(
                        "\n<dark_purple><bold>═══ Espionage Info ═══</bold></dark_purple>\n"
                                + "<gray>Forged Passports hide you from Dynmap and suppress border alerts.\n"
                                + "Sabotage drains enemy stability when sneaking near their Core.\n"
                                + "Obtain passports with <white>/province give forged_passport</white>.</gray>\n"));
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
                "\n<dark_purple><bold>═══ Espionage Commands ═══</bold></dark_purple>\n"
                        + "<white>/spy sabotage</white> <gray>— Begin a sabotage operation</gray>\n"
                        + "<white>/spy info</white> <gray>— View espionage information</gray>\n"
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
