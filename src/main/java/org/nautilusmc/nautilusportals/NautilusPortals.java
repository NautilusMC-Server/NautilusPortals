package org.nautilusmc.nautilusportals;

import net.kyori.adventure.text.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.phys.Vec3;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.v1_19_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R3.util.CraftLocation;
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
        FileConfiguration config = this.getConfig();
        List<Map<?,?>> portals = config.getMapList("portals");
        portals.add(Map.of("location", location,"displayName", displayName, "owner", player.toString(), "connections", new ArrayList<>()));
        config.set("portals", portals);
        this.saveConfig();

        makeConduitActive(location);
    }

    public void removePortal(Location location) {
        FileConfiguration config = this.getConfig();
        List<Map<?,?>> portals = config.getMapList("portals");
        portals.removeIf(portal -> portal.get("location").equals(location));
        config.set("portals", portals);
        this.saveConfig();
    }

    public List<Location> getPortalConnections(Location portalLoc) {
        FileConfiguration config = this.getConfig();
        List<Map<?,?>> portals = config.getMapList("portals");
        Map<?,?> portal = portals.stream().filter(p -> p.get("location").equals(portalLoc)).findFirst().orElse(null);
        if (portal == null) {return null;}
        return (List<Location>) portal.get("connections");
    }


    private void modifyPortalConnections(Location portalLoc, Location otherLoc, boolean add) {
        FileConfiguration config = this.getConfig();
        List<Map<?,?>> portals = config.getMapList("portals");
        Map<?,?> portal = portals.stream().filter(p -> p.get("location").equals(portalLoc)).findFirst().orElse(null);
        if (portal == null) {return;}
        List<Location> connections = (List<Location>) portal.get("connections");
        if (add) {
            if (!connections.contains(otherLoc)) connections.add(otherLoc);
        } else {
            connections.remove(otherLoc);
        }
        config.set("portals", portals);
        this.saveConfig();
    }
    public void connectPortal(Location portalLoc, Location newPortalLoc) {
        modifyPortalConnections(portalLoc, newPortalLoc, true);
    }

    public void disconnectPortal(Location portalLoc, Location removedPortalLoc) {
        modifyPortalConnections(portalLoc, removedPortalLoc, false);
    }

    public boolean isPortal(Location location) {
        if (location.getBlock().getType() != Material.CONDUIT) {return false;}
        FileConfiguration config = this.getConfig();
        List<Map<?,?>> portals = config.getMapList("portals");
        return portals.stream().anyMatch(portal -> portal.get("location").equals(location));
    }

    public boolean getActivatingBlocks(Location location, List<Location> activatingBlocks) {
        activatingBlocks.clear();
        if (!location.clone().add(0,-1,0).getBlock().isSolid() || location.clone().add(0,1,0).getBlock().isSolid()) {
            return false;
        }

        Stack<Location> stack = new Stack<>();
        Set<Location> visited = new HashSet<>();

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (!(x == 0 && z == 0) && location.clone().add(x,0,z).getBlock().getType() != Material.WATER) {
                    return false;
                }
                stack.push(location.clone().add(x,0, z));
            }
        }

        while (!stack.isEmpty()) {
            Location loc = stack.pop();
            if (visited.contains(loc)) {continue;}
            visited.add(loc);

//            Bukkit.getLogger().info("Checking: (" + loc.getBlockX() + "," + loc.getBlockY() + ", " + loc.getBlockZ() + ")");

            // TODO: check if too far
            if (loc.distanceSquared(location) > 5*5) {continue;}

            if (loc.getBlock().getType() != Material.WATER) {continue;}

//            Levelled blockData = (Levelled) loc.getBlock().getBlockData();
//            if (blockData.getLevel() != blockData.getMaximumLevel()) {continue;}

            if (!loc.clone().add(0,-1,0).getBlock().isSolid() || loc.clone().add(0,1,0).getBlock().isSolid()) {
                activatingBlocks.clear();
                return false;
            }

            activatingBlocks.add(loc);

            stack.push(loc.clone().add(1, 0, 0));
            stack.push(loc.clone().add(-1, 0, 0));
            stack.push(loc.clone().add(0, 0, 1));
            stack.push(loc.clone().add(0, 0, -1));
        }

        return true;
    }

    public boolean isValidPortal(Location location) {
        if (!isPortal(location)) {return false;}

        Block block = location.getBlock();

        if (!((Waterlogged) block.getBlockData()).isWaterlogged()) {return false;}

        List<Location> activatingBlocks = new ArrayList<>();
        if (!getActivatingBlocks(location, activatingBlocks)) {return false;}

        for (Location loc : activatingBlocks) {
            location.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0.5,1.25,0.5), 1, 0, 0, 0, 0);
        }

        return true;
    }

    public void makeConduitActive(Location location) {
        Level level = ((CraftWorld) location.getWorld()).getHandle();
        BlockPos pos = CraftLocation.toBlockPosition(location);
        ConduitBlockEntity blockEntity = (ConduitBlockEntity) level.getBlockEntity(pos);
        blockEntity = new ConduitBlockEntity(pos, blockEntity.getBlockState()) {
            @Override
            public boolean isActive() {return true;}
        };
        level.setBlockEntity(blockEntity);

        // TODO: send packet to client
    }

    @Override
    public void onEnable() {
        INSTANCE = this;
        PORTAL_NBT = new NamespacedKey(this, "portal");

        Bukkit.getPluginManager().registerEvents(new NautilusPortalsListener(), this);

        getConfig().options().copyDefaults(true);
        this.saveConfig();
    }

//    @Override
//    public void onDisable() {
//        // Plugin shutdown logic
//    }

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

        String path = "players." + uuid.toString() + ".numPortals";
        if (!config.contains(path)) {
            config.set(path, 0);
            this.saveConfig();
            return 0;
        }

        return config.getInt(path);
    }

    protected void setNumberOfPortals(UUID uuid, int num) {
        this.getConfig().set("players." + uuid.toString() + ".numPortals", num);
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
