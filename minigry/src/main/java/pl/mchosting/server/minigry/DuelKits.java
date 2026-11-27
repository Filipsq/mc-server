package pl.mchosting.server.minigry;

import java.util.Map;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * The items of each {@link DuelKit}. Everything is unbreakable, so long duels do not end with
 * broken armour.
 */
final class DuelKits {

    private DuelKits() {}

    /** Icon of the kit in the menu. */
    static Material icon(DuelKit kit) {
        return switch (kit) {
            case MIECZ -> Material.DIAMOND_SWORD;
            case LUK -> Material.BOW;
            case MACE -> Material.MACE;
            case NETHERYT -> Material.NETHERITE_CHESTPLATE;
            case UHC -> Material.GOLDEN_APPLE;
        };
    }

    /** Clears the player and gives them the kit. */
    static void give(DuelKit kit, Player player) {
        Lobby.prepare(player, GameMode.SURVIVAL);
        PlayerInventory inventory = player.getInventory();
        switch (kit) {
            case MIECZ -> {
                armour(
                        inventory,
                        Material.DIAMOND_HELMET,
                        Material.DIAMOND_CHESTPLATE,
                        Material.DIAMOND_LEGGINGS,
                        Material.DIAMOND_BOOTS,
                        2);
                inventory.setItem(0, item(Material.DIAMOND_SWORD, 1, Map.of(Enchantment.SHARPNESS, 2)));
                inventory.setItem(1, item(Material.GOLDEN_APPLE, 3, Map.of()));
            }
            case LUK -> {
                armour(
                        inventory,
                        Material.IRON_HELMET,
                        Material.IRON_CHESTPLATE,
                        Material.IRON_LEGGINGS,
                        Material.IRON_BOOTS,
                        1);
                inventory.setItem(0, item(Material.BOW, 1, Map.of(Enchantment.POWER, 2, Enchantment.INFINITY, 1)));
                inventory.setItem(1, item(Material.WOODEN_SWORD, 1, Map.of()));
                inventory.setItem(9, item(Material.ARROW, 1, Map.of()));
            }
            case MACE -> {
                armour(
                        inventory,
                        Material.DIAMOND_HELMET,
                        Material.DIAMOND_CHESTPLATE,
                        Material.DIAMOND_LEGGINGS,
                        Material.DIAMOND_BOOTS,
                        2);
                inventory.setItem(0, item(Material.MACE, 1, Map.of(Enchantment.DENSITY, 3, Enchantment.WIND_BURST, 1)));
                inventory.setItem(1, item(Material.DIAMOND_SWORD, 1, Map.of(Enchantment.SHARPNESS, 1)));
                inventory.setItem(2, item(Material.WIND_CHARGE, 16, Map.of()));
                inventory.setItem(3, item(Material.GOLDEN_APPLE, 4, Map.of()));
                inventory.setItemInOffHand(item(Material.SHIELD, 1, Map.of()));
            }
            case NETHERYT -> {
                armour(
                        inventory,
                        Material.NETHERITE_HELMET,
                        Material.NETHERITE_CHESTPLATE,
                        Material.NETHERITE_LEGGINGS,
                        Material.NETHERITE_BOOTS,
                        4);
                inventory.setItem(0, item(Material.NETHERITE_SWORD, 1, Map.of(Enchantment.SHARPNESS, 5)));
                inventory.setItem(1, item(Material.NETHERITE_AXE, 1, Map.of(Enchantment.SHARPNESS, 5)));
                inventory.setItem(2, item(Material.GOLDEN_APPLE, 8, Map.of()));
                inventory.setItem(3, item(Material.BOW, 1, Map.of(Enchantment.POWER, 5)));
                inventory.setItem(9, item(Material.ARROW, 16, Map.of()));
                inventory.setItemInOffHand(item(Material.SHIELD, 1, Map.of()));
            }
            case UHC -> {
                inventory.setHelmet(item(Material.DIAMOND_HELMET, 1, Map.of(Enchantment.PROTECTION, 1)));
                inventory.setChestplate(item(Material.DIAMOND_CHESTPLATE, 1, Map.of(Enchantment.PROTECTION, 2)));
                inventory.setLeggings(item(Material.IRON_LEGGINGS, 1, Map.of(Enchantment.PROTECTION, 2)));
                inventory.setBoots(item(Material.DIAMOND_BOOTS, 1, Map.of(Enchantment.PROTECTION, 1)));
                inventory.setItem(0, item(Material.DIAMOND_SWORD, 1, Map.of(Enchantment.SHARPNESS, 2)));
                inventory.setItem(1, item(Material.BOW, 1, Map.of(Enchantment.POWER, 1)));
                inventory.setItem(2, item(Material.WATER_BUCKET, 1, Map.of()));
                inventory.setItem(3, item(Material.LAVA_BUCKET, 1, Map.of()));
                inventory.setItem(4, item(Material.GOLDEN_APPLE, 6, Map.of()));
                inventory.setItem(5, item(Material.COBBLESTONE, 64, Map.of()));
                inventory.setItem(6, item(Material.DIAMOND_AXE, 1, Map.of()));
                inventory.setItem(7, item(Material.WATER_BUCKET, 1, Map.of()));
                inventory.setItem(8, item(Material.LAVA_BUCKET, 1, Map.of()));
                inventory.setItem(9, item(Material.ARROW, 16, Map.of()));
                inventory.setItem(10, item(Material.OAK_PLANKS, 64, Map.of()));
            }
        }
    }

    private static void armour(
            PlayerInventory inventory, Material helmet, Material chest, Material legs, Material boots, int protection) {
        Map<Enchantment, Integer> enchants = Map.of(Enchantment.PROTECTION, protection);
        inventory.setHelmet(item(helmet, 1, enchants));
        inventory.setChestplate(item(chest, 1, enchants));
        inventory.setLeggings(item(legs, 1, enchants));
        inventory.setBoots(item(boots, 1, enchants));
    }

    private static ItemStack item(Material material, int amount, Map<Enchantment, Integer> enchants) {
        ItemStack stack = ItemStack.of(material, amount);
        stack.editMeta(meta -> {
            enchants.forEach((enchantment, level) -> meta.addEnchant(enchantment, level, true));
            // Harmless on items without durability, so every kit item gets it.
            meta.setUnbreakable(true);
        });
        return stack;
    }
}
