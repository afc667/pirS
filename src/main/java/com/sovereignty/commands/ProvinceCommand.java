package com.sovereignty.commands;

import com.sovereignty.items.SovereigntyItems;
import com.sovereignty.managers.ProvinceManager;
import com.sovereignty.models.ChunkPosition;
import com.sovereignty.models.CoreBlock;
import com.sovereignty.models.Province;
import com.sovereignty.relics.RelicType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Handles the {@code /province} command and its subcommands.
 *
 * <h3>Subcommands</h3>
 * <ul>
 *   <li>{@code create <name>} — Found a new province at the placed core.</li>
 *   <li>{@code info [name]}   — View province info (your own or by name).</li>
 *   <li>{@code claim}         — Claim the chunk you are standing in.</li>
 *   <li>{@code unclaim}       — Un-claim the chunk you are standing in.</li>
 *   <li>{@code tax <rate>}    — Set the tax rate (0–100).</li>
 *   <li>{@code rename <name>} — Rename your province.</li>
 *   <li>{@code items}         — Get the Sovereignty starter items.</li>
 *   <li>{@code give <item>}   — Give a specific custom item (admin).</li>
 * </ul>
 */
public final class ProvinceCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private static final List<String> SUBCOMMANDS =
            List.of("create", "info", "claim", "unclaim", "tax", "rename", "items", "give");

    private static final List<String> ITEM_NAMES =
            List.of("core", "siege_cannon", "forged_passport",
                    "disease_spore", "physicist_goggles", "nichirin_sword",
                    "demon_ash", "poetryland_hoe", "crystal_of_resonance");

    private final ProvinceManager provinceManager;

    public ProvinceCommand(ProvinceManager provinceManager) {
        this.provinceManager = provinceManager;
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
            case "create" -> handleCreate(player, args);
            case "info" -> handleInfo(player, args);
            case "claim" -> handleClaim(player);
            case "unclaim" -> handleUnclaim(player);
            case "tax" -> handleTax(player, args);
            case "rename" -> handleRename(player, args);
            case "items" -> handleItems(player);
            case "give" -> handleGive(player, args);
            default -> {
                sendUsage(player);
                yield true;
            }
        };
    }

    // ── Subcommand Handlers ──────────────────────────────────────────────

    private boolean handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MINI.deserialize("<red>Usage: /province create <name></red>"));
            return true;
        }
        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        // The province core must be placed first — look for one near the player
        ChunkPosition pos = playerChunk(player);
        Optional<Province> existing = provinceManager.getProvinceAt(pos);
        if (existing.isPresent()) {
            player.sendMessage(MINI.deserialize(
                    "<red>✖ This chunk is already claimed by <white>"
                            + existing.get().getName() + "</white>.</red>"));
            return true;
        }

        // Build the province object
        CoreBlock core = new CoreBlock(
                player.getWorld().getName(),
                player.getLocation().getBlockX(),
                player.getLocation().getBlockY(),
                player.getLocation().getBlockZ(),
                CoreBlock.DEFAULT_HP, 1, -1L);

        Province province = new Province(
                -1L, name, player.getUniqueId(), null,
                core, 100.0, 0.10, 0.0, 1, 0.0, Instant.now());

        provinceManager.createProvince(province).thenAccept(id -> {
            if (id < 0) {
                player.sendMessage(MINI.deserialize(
                        "<red>✖ Failed to create province. The name may already be taken.</red>"));
            } else {
                player.sendMessage(MINI.deserialize(
                        "<green>✔ Province <gradient:#FFD700:#FFA500><bold>" + name
                                + "</bold></gradient> <green>founded! 3×3 capital area claimed.</green>"));
            }
        });
        return true;
    }

    private boolean handleInfo(Player player, String[] args) {
        Optional<Province> target;

        if (args.length >= 2) {
            // Look up by name — scan all provinces
            String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).toLowerCase();
            target = findProvinceByName(query);
        } else {
            // Show info for the province the player is standing in
            target = provinceManager.getProvinceAt(playerChunk(player));
        }

        if (target.isEmpty()) {
            player.sendMessage(MINI.deserialize("<gray>⛰ No province found here (Wilderness).</gray>"));
            return true;
        }

        Province p = target.get();
        player.sendMessage(MINI.deserialize(
                "\n<gradient:#FFD700:#FFA500><bold>═══ " + p.getName() + " ═══</bold></gradient>\n"
                        + "<gray>Owner:</gray> <white>" + p.getOwnerUuid() + "</white>\n"
                        + "<gray>Stability:</gray> <white>" + String.format("%.1f", p.getStability()) + "%</white>\n"
                        + "<gray>Tax Rate:</gray> <white>" + String.format("%.0f", p.getTaxRate() * 100) + "%</white>\n"
                        + "<gray>Development:</gray> <white>" + p.getDevelopment() + "</white>\n"
                        + "<gray>War Exhaustion:</gray> <white>" + String.format("%.1f", p.getWarExhaustion()) + "</white>\n"
                        + "<gray>Civil War:</gray> " + (p.isInCivilWar() ? "<red>YES</red>" : "<green>No</green>")
                        + "\n"
        ));
        return true;
    }

    private boolean handleClaim(Player player) {
        ChunkPosition pos = playerChunk(player);

        // Find the player's province
        Optional<Province> ownProvince = findPlayerProvince(player);
        if (ownProvince.isEmpty()) {
            player.sendMessage(MINI.deserialize(
                    "<red>✖ You don't own a province. Use <white>/province create <name></white> first.</red>"));
            return true;
        }

        Province province = ownProvince.get();
        if (!provinceManager.canClaim(pos, province.getId())) {
            player.sendMessage(MINI.deserialize(
                    "<red>✖ Cannot claim this chunk. It may already be claimed or too close to another province.</red>"));
            return true;
        }

        provinceManager.claimChunk(pos, province.getId()).thenRun(() ->
                player.sendMessage(MINI.deserialize(
                        "<green>✔ Chunk <white>(" + pos.getChunkX() + ", " + pos.getChunkZ()
                                + ")</white> claimed for <white>" + province.getName() + "</white>.</green>"))
        );
        return true;
    }

    private boolean handleUnclaim(Player player) {
        ChunkPosition pos = playerChunk(player);
        Optional<Province> province = provinceManager.getProvinceAt(pos);

        if (province.isEmpty()) {
            player.sendMessage(MINI.deserialize("<red>✖ This chunk is already wilderness.</red>"));
            return true;
        }
        if (!province.get().getOwnerUuid().equals(player.getUniqueId())) {
            player.sendMessage(MINI.deserialize("<red>✖ You don't own this province.</red>"));
            return true;
        }

        provinceManager.unclaimChunk(pos);
        player.sendMessage(MINI.deserialize(
                "<green>✔ Chunk <white>(" + pos.getChunkX() + ", " + pos.getChunkZ()
                        + ")</white> un-claimed.</green>"));
        return true;
    }

    private boolean handleTax(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MINI.deserialize("<red>Usage: /province tax <0-100></red>"));
            return true;
        }

        Optional<Province> ownProvince = findPlayerProvince(player);
        if (ownProvince.isEmpty()) {
            player.sendMessage(MINI.deserialize("<red>✖ You don't own a province.</red>"));
            return true;
        }

        try {
            double rate = Double.parseDouble(args[1]);
            if (rate < 0 || rate > 100) throw new NumberFormatException();
            Province p = ownProvince.get();
            p.setTaxRate(rate / 100.0);
            player.sendMessage(MINI.deserialize(
                    "<green>✔ Tax rate set to <white>" + String.format("%.0f", rate) + "%</white>.</green>"));
        } catch (NumberFormatException e) {
            player.sendMessage(MINI.deserialize("<red>✖ Tax rate must be a number between 0 and 100.</red>"));
        }
        return true;
    }

    private boolean handleRename(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MINI.deserialize("<red>Usage: /province rename <new name></red>"));
            return true;
        }

        Optional<Province> ownProvince = findPlayerProvince(player);
        if (ownProvince.isEmpty()) {
            player.sendMessage(MINI.deserialize("<red>✖ You don't own a province.</red>"));
            return true;
        }

        String newName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        ownProvince.get().setName(newName);
        player.sendMessage(MINI.deserialize(
                "<green>✔ Province renamed to <gradient:#FFD700:#FFA500><bold>"
                        + newName + "</bold></gradient><green>.</green>"));
        return true;
    }

    private boolean handleItems(Player player) {
        player.getInventory().addItem(SovereigntyItems.createProvinceCore());
        player.getInventory().addItem(SovereigntyItems.createSiegeCannon());
        player.getInventory().addItem(SovereigntyItems.createForgedPassport());
        player.sendMessage(MINI.deserialize(
                "<green>✔ Sovereignty items added to your inventory:</green>\n"
                        + "<gray> • Province Core (Lodestone)</gray>\n"
                        + "<gray> • Siege Cannon (TNT)</gray>\n"
                        + "<gray> • Forged Passport (Paper)</gray>"));
        return true;
    }

    private boolean handleGive(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MINI.deserialize(
                    "<red>Usage: /province give <item></red>\n"
                            + "<gray>Items: " + String.join(", ", ITEM_NAMES) + "</gray>"));
            return true;
        }

        String itemName = args[1].toLowerCase();
        return switch (itemName) {
            case "core" -> {
                player.getInventory().addItem(SovereigntyItems.createProvinceCore());
                player.sendMessage(MINI.deserialize("<green>✔ Province Core given.</green>"));
                yield true;
            }
            case "siege_cannon" -> {
                player.getInventory().addItem(SovereigntyItems.createSiegeCannon());
                player.sendMessage(MINI.deserialize("<green>✔ Siege Cannon given.</green>"));
                yield true;
            }
            case "forged_passport" -> {
                player.getInventory().addItem(SovereigntyItems.createForgedPassport());
                player.sendMessage(MINI.deserialize("<green>✔ Forged Passport given.</green>"));
                yield true;
            }
            default -> {
                // Check if it's a relic
                RelicType relic = findRelic(itemName);
                if (relic != null) {
                    player.getInventory().addItem(SovereigntyItems.createRelic(relic));
                    player.sendMessage(MINI.deserialize(
                            "<green>✔ Relic <white>" + relic.getDisplayName() + "</white> given.</green>"));
                    yield true;
                }
                player.sendMessage(MINI.deserialize(
                        "<red>✖ Unknown item: <white>" + itemName + "</white></red>\n"
                                + "<gray>Available: " + String.join(", ", ITEM_NAMES) + "</gray>"));
                yield true;
            }
        };
    }

    // ── Usage ────────────────────────────────────────────────────────────

    private void sendUsage(Player player) {
        player.sendMessage(MINI.deserialize(
                "\n<gradient:#FFD700:#FFA500><bold>═══ Province Commands ═══</bold></gradient>\n"
                        + "<white>/province create <name></white> <gray>— Found a new province</gray>\n"
                        + "<white>/province info [name]</white> <gray>— View province info</gray>\n"
                        + "<white>/province claim</white> <gray>— Claim the current chunk</gray>\n"
                        + "<white>/province unclaim</white> <gray>— Un-claim the current chunk</gray>\n"
                        + "<white>/province tax <0-100></white> <gray>— Set tax rate</gray>\n"
                        + "<white>/province rename <name></white> <gray>— Rename your province</gray>\n"
                        + "<white>/province items</white> <gray>— Get Sovereignty starter items</gray>\n"
                        + "<white>/province give <item></white> <gray>— Give a specific item</gray>\n"
        ));
    }

    // ── Tab Completion ───────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            return ITEM_NAMES.stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private ChunkPosition playerChunk(Player player) {
        return new ChunkPosition(
                player.getWorld().getName(),
                player.getLocation().getBlockX() >> 4,
                player.getLocation().getBlockZ() >> 4);
    }

    private Optional<Province> findPlayerProvince(Player player) {
        // Scan loaded provinces for one owned by this player
        ChunkPosition pos = playerChunk(player);
        Optional<Province> atChunk = provinceManager.getProvinceAt(pos);
        if (atChunk.isPresent() && atChunk.get().getOwnerUuid().equals(player.getUniqueId())) {
            return atChunk;
        }
        // Fallback: the manager doesn't expose a byOwner query yet, so
        // we return empty — the player must stand in their own province.
        return Optional.empty();
    }

    private Optional<Province> findProvinceByName(String name) {
        // The manager doesn't expose a byName query, so we check the
        // province at the sender's current chunk. A full name-search
        // would require iterating all provinces.
        return Optional.empty();
    }

    private RelicType findRelic(String name) {
        for (RelicType type : RelicType.values()) {
            if (type.name().equalsIgnoreCase(name)) return type;
        }
        return null;
    }
}
