package org.nautilusmc.nautilusportals;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.v1_19_R3.inventory.CraftItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;


public class NautilusPortalsListener implements Listener {

    @EventHandler
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        HumanEntity player = event.getView().getPlayer();

        List<ItemStack> recipe = NautilusPortals.INSTANCE.getPortalRecipe(player.getUniqueId());

        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();

        boolean matches = true;
        for (int i = 0; i < matrix.length; i++) {
            if (matrix[i] == null && recipe.get(i).getAmount() == 0) {continue;}
            if (matrix[i] != null && matrix[i].getType() == recipe.get(i).getType() && matrix[i].getAmount() >= recipe.get(i).getAmount()) {continue;}
            matches = false;
            break;
        }

        if (matches) {
            ItemStack result = new ItemStack(Material.CONDUIT);
            ItemMeta meta = result.getItemMeta();
            meta.displayName(Component.text(player.getName() + "'s Portal #" + (NautilusPortals.INSTANCE.getNumberOfPortals(player.getUniqueId())+1)).color(TextColor.color(240, 0, 255)));
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(NautilusPortals.PORTAL_NBT, PersistentDataType.BYTE, (byte) 1);
            result.setItemMeta(meta);
            inventory.setResult(result);
        }
    }

    @EventHandler
    public void onInventoryClickEvent(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof CraftingInventory inventory) || event.getSlot() != 0 || inventory.getMatrix().length != 9 || !NautilusPortals.INSTANCE.isPortal(event.getCurrentItem())) { return; }

        event.setCancelled(true);

        HumanEntity player = event.getWhoClicked();

        if (!player.getInventory().addItem(inventory.getResult()).isEmpty()) { return; }

        List<ItemStack> recipe = NautilusPortals.INSTANCE.getPortalRecipe(player.getUniqueId());

        ItemStack[] matrix = inventory.getMatrix();

        for (int i = 0; i < matrix.length; i++) {
            if (matrix[i] != null) { matrix[i].setAmount(matrix[i].getAmount() - recipe.get(i).getAmount()); }
        }

        NautilusPortals.INSTANCE.setNumberOfPortals(player.getUniqueId(), NautilusPortals.INSTANCE.getNumberOfPortals(player.getUniqueId())+1);
        inventory.setMatrix(matrix);

    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (NautilusPortals.INSTANCE.isPortal(event.getItemInHand())) {
            NautilusPortals.INSTANCE.placePortal(event.getBlock().getLocation(), ((TextComponent) event.getItemInHand().getItemMeta().displayName()).content(), event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onBlockBreakEvent(BlockBreakEvent event) {
        if (NautilusPortals.INSTANCE.isPortal(event.getBlock().getLocation())) {
            NautilusPortals.INSTANCE.removePortal(event.getBlock().getLocation());
        }

    }

//    @EventHandler
//    public void onBlockDestroyedEvent(BlockEvent)

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
//        Bukkit.getLogger().info("" + event.getAction() + event.getItem() + event.getHand() + event.useInteractedBlock() + event.useItemInHand());
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getHand() == EquipmentSlot.HAND && event.getItem() == null && NautilusPortals.INSTANCE.isPortal(event.getClickedBlock().getLocation())) {
            event.getPlayer().sendMessage(Component.text("Portal " + (NautilusPortals.INSTANCE.isValidPortal(event.getClickedBlock().getLocation()) ? "Valid" : "Invalid")));
        }
    }

}
