package com.sovereignty.items;

import com.sovereignty.relics.RelicType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Factory for creating vanilla {@link ItemStack}s that represent Sovereignty
 * custom items. These items are tagged via the {@link PersistentDataContainer}
 * so that the plugin recognises them regardless of whether ItemsAdder is installed.
 *
 * <p>If ItemsAdder <b>is</b> installed the IA-provided textures will be used
 * instead; these vanilla items serve as a fully-functional fallback.
 */
public final class SovereigntyItems {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private static NamespacedKey ITEM_ID_KEY;

    /** Intentionally uses the same "itemsadder_id" key so that vanilla-tagged
     *  items are recognised by ItemsAdderListener and vice-versa. */
    private static final String PDC_KEY_NAME = "itemsadder_id";

    private SovereigntyItems() { /* utility class */ }

    /**
     * Must be called once during {@code onEnable} to initialise the PDC key.
     *
     * @param plugin the owning plugin instance
     */
    public static void initKeys(Plugin plugin) {
        ITEM_ID_KEY = new NamespacedKey(plugin, PDC_KEY_NAME);
    }

    /** Returns the {@link NamespacedKey} used to tag custom items. */
    public static NamespacedKey getItemIdKey() {
        return ITEM_ID_KEY;
    }

    // ── Core Items ───────────────────────────────────────────────────────

    /**
     * Creates a Province Core block — the physical anchor for a new Province.
     */
    public static ItemStack createProvinceCore() {
        ItemStack item = new ItemStack(Material.LODESTONE, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MINI.deserialize(
                "<gradient:#FFD700:#FFA500><bold>Province Core</bold></gradient>")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Place this block to establish", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("your Province capital.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Use ", NamedTextColor.DARK_GRAY)
                        .append(Component.text("/province create <name>", NamedTextColor.WHITE))
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("after placement.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ITEM_ID_KEY, PersistentDataType.STRING, "sovereignty:government_stone");
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Creates a Siege Cannon item — the only explosive that can damage
     * claimed territory.
     */
    public static ItemStack createSiegeCannon() {
        ItemStack item = new ItemStack(Material.TNT, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MINI.deserialize(
                "<red><bold>Siege Cannon</bold></red>")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("A powerful explosive capable of", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("damaging Province Cores.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Only works in claimed territory", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("during active sieges.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ITEM_ID_KEY, PersistentDataType.STRING, "sovereignty:siege_cannon");
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Creates a Forged Passport — enables espionage mechanics.
     */
    public static ItemStack createForgedPassport() {
        ItemStack item = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MINI.deserialize(
                "<dark_purple><bold>Forged Passport</bold></dark_purple>")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("A counterfeit travel document.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Hides you from Dynmap and", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("suppresses border alerts.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Consumed on sabotage activation.", NamedTextColor.DARK_RED)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ITEM_ID_KEY, PersistentDataType.STRING, "sovereignty:forged_passport");
        item.setItemMeta(meta);
        return item;
    }

    // ── Relics ───────────────────────────────────────────────────────────

    /**
     * Creates a vanilla representation of a Relic.
     *
     * @param type the relic type
     * @return the relic item
     */
    public static ItemStack createRelic(RelicType type) {
        Material material = relicMaterial(type);
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MINI.deserialize(
                "<gradient:#FF6EC7:#FFD700><bold>" + type.getDisplayName() + "</bold></gradient>")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(type.getLoreTitle(), NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.ITALIC, true),
                Component.empty(),
                relicDescription(type),
                Component.empty(),
                Component.text("⚠ Drops on death — one per server", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ITEM_ID_KEY, PersistentDataType.STRING, type.getNamespace());
        item.setItemMeta(meta);
        return item;
    }

    /** Maps each relic to an appropriate vanilla material. */
    private static Material relicMaterial(RelicType type) {
        return switch (type) {
            case DISEASE_SPORE -> Material.FERMENTED_SPIDER_EYE;
            case PHYSICIST_GOGGLES -> Material.LEATHER_HELMET;
            case NICHIRIN_SWORD -> Material.GOLDEN_SWORD;
            case DEMON_ASH -> Material.GUNPOWDER;
            case POETRYLAND_HOE -> Material.GOLDEN_HOE;
            case CRYSTAL_OF_RESONANCE -> Material.END_CRYSTAL;
        };
    }

    /** Returns a one-line description of the relic effect. */
    private static Component relicDescription(RelicType type) {
        String desc = switch (type) {
            case DISEASE_SPORE -> "AoE Poison II + Weakness II during sieges.";
            case PHYSICIST_GOGGLES -> "Grants Glowing to enemies within 30 blocks.";
            case NICHIRIN_SWORD -> "Deals 3× structural damage to Province Cores.";
            case DEMON_ASH -> "Smoke screen — suppresses alerts for 5 min.";
            case POETRYLAND_HOE -> "3× crop yield, −50% high-tax penalty.";
            case CRYSTAL_OF_RESONANCE -> "100% spy immunity, 2× Core HP, 3× Influence.";
        };
        return Component.text(desc, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    // ── Utility ──────────────────────────────────────────────────────────

    /**
     * Reads the Sovereignty custom item ID from an {@link ItemStack}.
     *
     * @param item the item to inspect
     * @return the custom ID string, or {@code null} if not a Sovereignty item
     */
    public static String getCustomId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(ITEM_ID_KEY, PersistentDataType.STRING);
    }

    /**
     * Checks whether an {@link ItemStack} is a specific Sovereignty item.
     *
     * @param item      the item to check
     * @param namespace the expected custom ID (e.g. "sovereignty:government_stone")
     * @return {@code true} if the item matches
     */
    public static boolean isItem(ItemStack item, String namespace) {
        return namespace.equals(getCustomId(item));
    }
}
