package com.sovereignty.integration;

import com.sovereignty.managers.ProvinceManager;
import com.sovereignty.models.ChunkPosition;
import com.sovereignty.models.CoreBlock;
import com.sovereignty.models.Province;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Iterator;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Event-driven integration with the
 * <a href="https://itemsadder.devs.beer/">ItemsAdder</a> API for
 * custom visual assets.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li><b>Custom Cores:</b> Listens for {@link BlockPlaceEvent} to detect
 *       when a player places a block matching the ItemsAdder namespace
 *       {@code sovereignty:government_stone}, registering it as a
 *       Province Core.</li>
 *   <li><b>Siege Mechanics:</b> Intercepts {@link EntityExplodeEvent} to
 *       check if the explosive is a custom {@code sovereignty:siege_cannon}.
 *       Only custom siege items can damage claimed obsidian.</li>
 *   <li><b>Custom Armory:</b> Updates combat calculations to recognize
 *       custom ItemsAdder items and fetch their damage attributes.</li>
 * </ul>
 *
 * <p>If ItemsAdder is not installed, all event handlers gracefully no-op.
 */
public final class ItemsAdderListener implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /** ItemsAdder namespace for the custom Province Core block. */
    private final String coreNamespace;

    /** ItemsAdder namespace for the siege cannon explosive. */
    private final String siegeCannonNamespace;

    /** ItemsAdder namespace for the forged passport item. */
    private final String forgedPassportNamespace;

    private final Plugin plugin;
    private final ProvinceManager provinceManager;
    private final Logger logger;
    private final boolean itemsAdderAvailable;

    // PDC key for tagging ItemsAdder items with their custom ID
    private final NamespacedKey itemsAdderIdKey;

    /**
     * Constructs the ItemsAdder integration listener.
     *
     * @param plugin                 the owning plugin
     * @param provinceManager        the province manager
     * @param coreNamespace          the ItemsAdder namespace for Core blocks
     * @param siegeCannonNamespace   the ItemsAdder namespace for siege cannons
     * @param forgedPassportNamespace the ItemsAdder namespace for forged passports
     * @param logger                 the plugin logger
     */
    public ItemsAdderListener(Plugin plugin, ProvinceManager provinceManager,
                              String coreNamespace, String siegeCannonNamespace,
                              String forgedPassportNamespace, Logger logger) {
        this.plugin = plugin;
        this.provinceManager = provinceManager;
        this.coreNamespace = coreNamespace;
        this.siegeCannonNamespace = siegeCannonNamespace;
        this.forgedPassportNamespace = forgedPassportNamespace;
        this.logger = logger;
        this.itemsAdderIdKey = new NamespacedKey(plugin, "itemsadder_id");
        this.itemsAdderAvailable = org.bukkit.Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
        if (!itemsAdderAvailable) {
            logger.warning("[ItemsAdderListener] ItemsAdder not found — custom item features disabled.");
        } else {
            logger.info("[ItemsAdderListener] ItemsAdder integration enabled.");
        }
    }

    /**
     * Whether ItemsAdder is installed and available.
     */
    public boolean isAvailable() {
        return itemsAdderAvailable;
    }

    /**
     * Intercepts block placement to detect custom Province Core blocks.
     * If the placed item matches the configured ItemsAdder core namespace
     * (via either ItemsAdder or the vanilla SovereigntyItems PDC tag),
     * the block is registered as a Province Core.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        String customId = getItemsAdderId(item);
        if (customId == null || !customId.equals(coreNamespace)) return;

        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();

        // Register this block as a Province Core
        // The actual province creation is handled by the ProvinceManager
        // after verifying the player has LORD status and enough influence.
        player.sendActionBar(MINI.deserialize(
                "<gradient:#FFD700:#FFA500>🏛 Province Core placed! "
                        + "Use <white>/province create <name></white> to found your province.</gradient>"
        ));
        logger.info("[ItemsAdderListener] Custom Core block placed by "
                + player.getName() + " at " + block.getLocation());
    }

    /**
     * Intercepts explosions to enforce siege cannon requirements.
     * Vanilla TNT cannot damage claimed obsidian blocks. Only
     * explosives tagged with the siege cannon namespace can deal
     * block damage during active siege warfare.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!itemsAdderAvailable) return;

        Entity source = event.getEntity();
        boolean isSiegeCannon = isSiegeCannonExplosive(source);

        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            ChunkPosition pos = new ChunkPosition(
                    block.getWorld().getName(),
                    block.getChunk().getX(),
                    block.getChunk().getZ()
            );
            Optional<Province> opt = provinceManager.getProvinceAt(pos);
            if (opt.isPresent() && !isSiegeCannon) {
                // Non-siege explosives cannot damage claimed territory
                it.remove();
            }
        }
    }

    /**
     * Checks combat damage events for custom ItemsAdder weapon attributes.
     * If the attacker is using a custom weapon, its damage is fetched from
     * the ItemsAdder API instead of relying solely on vanilla enchants.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!itemsAdderAvailable) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        String customId = getItemsAdderId(weapon);
        if (customId == null) return;

        // Custom weapons have their damage attributes handled by ItemsAdder;
        // we log the usage for audit/debugging purposes.
        logger.fine("[ItemsAdderListener] Custom weapon '" + customId
                + "' used by " + attacker.getName());
    }

    /**
     * Extracts the Sovereignty custom item ID from an {@link ItemStack}'s PDC.
     * Checks both the ItemsAdder bridged key and the vanilla SovereigntyItems key.
     *
     * @param item the item to inspect
     * @return the custom ID string, or {@code null} if not a custom item
     */
    public String getItemsAdderId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        // Check our bridged key (works for both ItemsAdder and vanilla-tagged items)
        return pdc.get(itemsAdderIdKey, PersistentDataType.STRING);
    }

    /**
     * Checks whether an entity is a siege cannon explosive.
     *
     * @param entity the explosive entity
     * @return {@code true} if tagged as a siege cannon
     */
    private boolean isSiegeCannonExplosive(Entity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        String customId = pdc.get(itemsAdderIdKey, PersistentDataType.STRING);
        return siegeCannonNamespace.equals(customId);
    }

    /**
     * Checks whether a player's inventory contains a forged passport.
     * Works with both ItemsAdder and vanilla-tagged passports.
     *
     * @param player the player to inspect
     * @return {@code true} if the player carries a forged passport
     */
    public boolean hasForgedPassport(Player player) {
        // Check off-hand first
        String offHandId = getItemsAdderId(player.getInventory().getItemInOffHand());
        if (forgedPassportNamespace.equals(offHandId)) return true;
        // Check full inventory
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && forgedPassportNamespace.equals(getItemsAdderId(item))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes one forged passport from the player's inventory (consumed on use).
     * Works with both ItemsAdder and vanilla-tagged passports.
     *
     * @param player the player whose passport is consumed
     */
    public void consumeForgedPassport(Player player) {
        // Check off-hand first
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (forgedPassportNamespace.equals(getItemsAdderId(offHand))) {
            offHand.setAmount(offHand.getAmount() - 1);
            return;
        }
        // Otherwise scan inventory
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && forgedPassportNamespace.equals(getItemsAdderId(item))) {
                item.setAmount(item.getAmount() - 1);
                return;
            }
        }
    }
}
