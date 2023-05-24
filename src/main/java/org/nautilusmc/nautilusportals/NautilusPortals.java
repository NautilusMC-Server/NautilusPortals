package org.nautilusmc.nautilusportals;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public final class NautilusPortals extends JavaPlugin {

    static NautilusPortals INSTANCE;

    public static NamespacedKey PORTAL_NBT;

    public boolean isPortal(ItemStack item) {
        return item != null && item.getType() == Material.CONDUIT && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().get(NautilusPortals.PORTAL_NBT, PersistentDataType.BYTE) == (byte) 1;
    }

    public void placePortal(Location location, String displayName, UUID player) {
        FileConfiguration config = INSTANCE.getConfig();
        List<Map<?,?>> portals = config.getMapList("portals");
        portals.add(Map.of("location", location,"displayName", displayName, "player", player));
        config.set("portals", portals);
        this.saveConfig();
    }

    public void removePortal(Location location) {
        FileConfiguration config = INSTANCE.getConfig();
        List<Map<?,?>> portals = config.getMapList("portals");
        portals.removeIf(portal -> portal.get("location").equals(location));
        config.set("portals", portals);
        this.saveConfig();
    }

    public boolean isPortal(Location location) {
        if (location.getBlock().getType() != Material.CONDUIT) {return false;}
        FileConfiguration config = INSTANCE.getConfig();
        List<Map<?,?>> portals = config.getMapList("portals");
        return portals.stream().anyMatch(portal -> portal.get("location").equals(location));
    }

    @Override
    public void onEnable() {
        INSTANCE = this;
        PORTAL_NBT = new NamespacedKey(NautilusPortals.INSTANCE, "portal");

        Bukkit.getPluginManager().registerEvents(new NautilusPortalsListener(), this);

        getConfig().options().copyDefaults(true);
        this.saveConfig();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    private boolean matchesRange(int num, String range) {
        try {
            // Exact number
            return Integer.parseUnsignedInt(range) == num;
        } catch (NumberFormatException e) {
            // Unbounded range
            if (range.charAt(range.length()-1) == '-') {return Integer.parseInt(range.substring(0, range.length()-1)) <= num;}

            // Bounded range
            String[] endpoints = range.split("-");
            return Integer.parseInt(endpoints[0]) <= num && Integer.parseInt(endpoints[1]) >= num;
        }
    }

    protected int getNumberOfPortals(UUID uuid) {
        FileConfiguration config = this.getConfig();

        if (!config.contains("players." + uuid.toString())) {
            config.set("players." + uuid.toString(), 0);
            this.saveConfig();
        }

        return config.getInt("players." + uuid.toString());
    }

    protected void setNumberOfPortals(UUID uuid, int num) {
        this.getConfig().set("players." + uuid.toString(), num);
    }
    protected List<ItemStack> getPortalRecipe(UUID uuid) {
        int numPortals = getNumberOfPortals(uuid);

        FileConfiguration config = this.getConfig();
        List<Map<?,?>> recipeList = config.getMapList("portalRecipes");
        Map<?,?> recipeMap = recipeList.stream().filter((r) -> matchesRange(numPortals,(String) r.get("existingPortals"))).findFirst().orElse(null);

        if (recipeMap == null) {
            return null;
        }

        List<ItemStack> recipe = new ArrayList<>();
        for (Map<?,?> ingredient : (List<Map<?,?>>) recipeMap.get("recipe")) {
            ItemStack item = new ItemStack(Material.matchMaterial((String) ingredient.get("id")), (int) ingredient.get("count"));
            recipe.add(item);
        }

        return recipe;

    }
}
