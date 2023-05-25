package org.nautilusmc.nautilusportals;

import com.mojang.datafixers.util.Pair;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.world.level.block.BeaconBeamBlock;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public final class NautilusPortals extends JavaPlugin {

    public static NautilusPortals INSTANCE;

    public static NamespacedKey PORTAL_NBT;

    public static TextColor TEXT_COLOR_1 = TextColor.color(200,200,255);
    public static TextColor TEXT_COLOR_2 = TextColor.color(150,150,150);
    public static TextColor TEXT_COLOR_3 = TextColor.color(125,125,125);
    public static TextColor TEXT_COLOR_RED = TextColor.color(200,50,50);

    public List<List<Location>> activePortals;

    public boolean isPortal(ItemStack item) {
        return item != null && item.getType() == Material.BEACON && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().get(NautilusPortals.PORTAL_NBT, PersistentDataType.BYTE) == (byte) 1;
    }
    public boolean isLinker(ItemStack item) {
        return item != null && item.getType() == Material.NAUTILUS_SHELL && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(this, "portalWorld"), PersistentDataType.STRING);
    }

    public void placePortal(Location location, String displayName, UUID player) {
        List<Entity> armorStands = new ArrayList<>();
        for (int y = 0; y < 4; y ++) {
            ArmorStand stand = (ArmorStand) (location).getWorld().spawnEntity(location.clone().add(0.5,1.75 - 0.25*y,0.5), EntityType.ARMOR_STAND);
            stand.setMarker(true);
            stand.setInvisible(true);
            stand.setCustomNameVisible(false);
            armorStands.add(stand);
        }
        List<String> uuids = armorStands.stream().map(e->e.getUniqueId().toString()).collect(Collectors.toList());

        FileConfiguration config = this.getConfig();
        List<Map<?,?>> portals = config.getMapList("portals");
        portals.add(Map.of("location", location,"displayName", displayName, "owner", player.toString(), "connections", new ArrayList<>(), "valid", false, "armorStands", uuids, "selection", 0, "editCooldown", 0));
        config.set("portals", portals);
        this.saveConfig();

        updatePortals();
    }

    public void removePortal(Location location) {
        FileConfiguration config = this.getConfig();
        List<Map<?,?>> portals = config.getMapList("portals");
        Map<?,?> portal = portals.stream().filter(p -> p.get("location").equals(location)).findFirst().orElse(null);
        if (portal == null) {return;}

        ((List<String>) portal.get("armorStands")).forEach(uuid -> Bukkit.getEntity(UUID.fromString(uuid)).remove());

        for (Location other : (List<Location>) portal.get("connections")) {
            modifyPortalConnections(other, location, false);
        }

        portals.remove(portal);
        config.set("portals", portals);
        this.saveConfig();

        updatePortals();
    }

    public List<Location> getPortalConnections(Location portalLoc) {
        FileConfiguration config = this.getConfig();
        List<Map<?,?>> portals = config.getMapList("portals");
        Map<?,?> portal = portals.stream().filter(p -> p.get("location").equals(portalLoc)).findFirst().orElse(null);
        if (portal == null) {return null;}
        return (List<Location>) portal.get("connections");
    }

    public UUID getPortalOwner(Location location) {
        FileConfiguration config = this.getConfig();
        List<Map<?,?>> portals = config.getMapList("portals");
        Map<?,?> portal = portals.stream().filter(p -> p.get("location").equals(location)).findFirst().orElse(null);
        if (portal == null) {return null;}
        return UUID.fromString((String) portal.get("owner"));
    }

    public String getPortalName(Location location) {
        FileConfiguration config = this.getConfig();
        List<Map<?,?>> portals = config.getMapList("portals");
        Map<?,?> portal = portals.stream().filter(p -> p.get("location").equals(location)).findFirst().orElse(null);
        if (portal == null) {return null;}
        return (String) portal.get("displayName");
    }

    public boolean isValidPortal(Location location) {
        FileConfiguration config = this.getConfig();
        List<Map<?,?>> portals = config.getMapList("portals");
        Map<?,?> portal = portals.stream().filter(p -> p.get("location").equals(location)).findFirst().orElse(null);
        if (portal == null) {return false;}
        return (boolean) portal.get("valid");
    }

    public void addPortalSelectionIndex(Location location, int delta) {
        FileConfiguration config = this.getConfig();
        List<Map<?,?>> portals = config.getMapList("portals");
        Map<Object, Object> portal = (Map<Object,Object>) portals.stream().filter(p -> p.get("location").equals(location)).findFirst().orElse(null);
        if (portal == null) {return;}
        int selection = (int) portal.get("selection");
        int len = ((List<?>)portal.get("connections")).size();
        portal.put("selection", (selection + delta + len) % len);
        portal.put("editCooldown", 30);
        config.set("portals", portals);
        this.saveConfig();

        updatePortalText();
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
    public void connectPortals(Location loc1, Location loc2) {
        modifyPortalConnections(loc1, loc2, true);
        modifyPortalConnections(loc2, loc1, true);
        updatePortalText();
    }

    public void disconnectPortals(Location loc1, Location loc2) {
        modifyPortalConnections(loc1, loc2, false);
        modifyPortalConnections(loc2, loc1, false);
        updatePortalText();
    }

    public boolean isPortal(Location location) {
        if (location.getBlock().getType() != Material.BEACON) {return false;}
        FileConfiguration config = this.getConfig();
        List<Map<?,?>> portals = config.getMapList("portals");
        return portals.stream().anyMatch(portal -> portal.get("location").equals(location));
    }

    private boolean isLowWater(Location location) {
        return location.getBlock().getType() == Material.WATER && ((Levelled) location.getBlock().getBlockData()).getLevel() != 0;
    }

    private boolean isStaticWater(Location location) {
        return location.getBlock().getType() == Material.WATER
                && !isLowWater(location)
                && !isLowWater(location.clone().add(1,0,0))
                && !isLowWater(location.clone().add(-1,0,0))
                && !isLowWater(location.clone().add(0,0,1))
                && !isLowWater(location.clone().add(0,0,-1));
    }

    private boolean isValidPortalBlock(Location location) {
        return isStaticWater(location)
                && location.clone().add(0,-1,0).getBlock().isSolid()
                && !location.clone().add(0,1,0).getBlock().getType().isOccluding();
    }

    public boolean getActivatingBlocks(Location location, @Nullable List<Location> activatingBlocks) {
        if (activatingBlocks == null) {activatingBlocks = new ArrayList<>();}
        activatingBlocks.clear();
        if (!isPortal(location) || !location.clone().add(0,-1,0).getBlock().isSolid() || location.clone().add(0,1,0).getBlock().getType().isOccluding()) {
            return false;
        }

        Stack<Location> stack = new Stack<>();
        Set<Location> visited = new HashSet<>();

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (!(x == 0 && z == 0) && !isValidPortalBlock(location.clone().add(x,0,z))) {
                    return false;
                }
                stack.push(location.clone().add(x,0, z));
            }
        }

        int maxDistSquared = this.getConfig().getInt("maxPortalRadius");
        maxDistSquared *= maxDistSquared;

        while (!stack.isEmpty()) {
            Location loc = stack.pop();
            if (visited.contains(loc)) {continue;}
            visited.add(loc);

            if (loc.distanceSquared(location) > maxDistSquared || !isValidPortalBlock(loc)) {continue;}

            activatingBlocks.add(loc);

            stack.push(loc.clone().add(1, 0, 0));
            stack.push(loc.clone().add(-1, 0, 0));
            stack.push(loc.clone().add(0, 0, 1));
            stack.push(loc.clone().add(0, 0, -1));
        }

        return true;
    }

    public void updatePlayers() {
        FileConfiguration config = this.getConfig();

        for (Player player : Bukkit.getOnlinePlayers()) {
            Location location = new Location(player.getLocation().getWorld(),player.getLocation().blockX(),player.getLocation().blockY(),player.getLocation().blockZ());
            String dataPath = "players."+player.getUniqueId()+".";

//            Bukkit.getLogger().info(""+location);
            List<Location> portalBlocks = activePortals.stream().filter(p -> p.contains(location)).min(Comparator.comparingDouble(p -> p.get(0).distanceSquared(location))).orElse(null);
            if (portalBlocks == null) {
                config.set(dataPath+"portalTime", 0);
                continue;
            }

            Location portal = portalBlocks.get(0);
            Map<?,?> portalData = config.getMapList("portals").stream().filter(p -> p.get("location").equals(portal)).findFirst().orElse(null);
            if (portalData == null || !(boolean)portalData.get("valid")) {
                config.set(dataPath+"portalTime", 0);
                continue;
            }
            if (config.getInt(dataPath+"portalTime") < 0) {
                player.addPotionEffect(PotionEffectType.CONFUSION.createEffect(80, 0).withIcon(false));
                continue;
            }

            if ((int) portalData.get("editCooldown") > 0) {
                config.set(dataPath+"portalTime", 0);
                continue;
            }


            int portalTime = config.getInt(dataPath+"portalTime") + 1;
            config.set(dataPath+"portalTime", portalTime);

            if (portalTime >= 20) {
                player.addPotionEffect(PotionEffectType.CONFUSION.createEffect(80, 0).withIcon(false));
            }
            if (portalTime >= 160 && getActivatingBlocks(portal, null)) {
                List<Location> connections = ((List<Location>)portalData.get("connections"));
                int selection = (int) portalData.get("selection");
                List<Location> destBlocks = new ArrayList<>();
                if (connections.size() == 0 || !getActivatingBlocks(connections.get((selection+connections.size()) % connections.size()), destBlocks)) {
                    player.sendActionBar(Component.text("Destination invalid!").color(TEXT_COLOR_RED));
                } else {
                    Random rand = new Random();
                    player.teleport(destBlocks.get(rand.nextInt(destBlocks.size())));
                }
                config.set(dataPath+"portalTime", -1);
            }

        }

        List<Map<?,?>> portals = config.getMapList("portals");
        for (int i = 0; i < portals.size(); i++) {
            Map<Object,Object> portal = (Map<Object, Object>) portals.get(i);
            int editCooldown = (int) portal.getOrDefault("editCooldown", 0);
            if (editCooldown > 0) {
                portal.put("editCooldown", editCooldown - 1);
                portals.set(i, portal);
            }
        }
        config.set("portals", portals);

        this.saveConfig();


    }

    @Override
    public void onEnable() {
        INSTANCE = this;
        PORTAL_NBT = new NamespacedKey(this, "portal");

        Bukkit.getPluginManager().registerEvents(new NautilusPortalsListener(), this);

        getConfig().options().copyDefaults(true);
        this.saveConfig();

        activePortals = new ArrayList<>();
        Bukkit.getScheduler().runTaskTimer(this, this::updatePortals, 0, 80);
        Bukkit.getScheduler().runTaskTimer(this, this::animatePortals, 0, 5);
        Bukkit.getScheduler().runTaskTimer(this, this::updatePlayers, 0, 1);

    }

    public void updatePortals() {
        FileConfiguration config = this.getConfig();
        List<Map<?,?>> portals = config.getMapList("portals");

        activePortals.clear();

        for (int i = 0; i < portals.size(); i++) {
            Map<Object,Object> portal = new HashMap<>();
            portal.putAll(portals.get(i));

            Location loc = (Location) portal.get("location");
            List<Location> locs = new ArrayList<>();
            boolean valid = getActivatingBlocks(loc, locs);
            portal.put("valid", valid);
            portals.set(i, portal);

            if (valid) {
                locs.add(0, loc);
                activePortals.add(locs);
            }
        }

        config.set("portals", portals);
        this.saveConfig();

        updatePortalText();

    }

    public void updatePortalText() {
        FileConfiguration config = this.getConfig();
        List<Map<?,?>> portals = config.getMapList("portals");
        portals.forEach(p -> {
            boolean valid = (boolean) p.get("valid");
            List<String> uuids = (List<String>) p.get("armorStands");
            List<ArmorStand> armorStands = uuids.stream().map(UUID::fromString).map(Bukkit::getEntity).map(e -> (ArmorStand) e).collect(Collectors.toList());
            if (valid) {
                armorStands.forEach(e->e.setCustomNameVisible(true));

                List<Location> connections = (List<Location>) p.get("connections");
                int index = (int) p.get("selection");

                armorStands.get(0).customName(Component.text((String)p.get("displayName")).decorate(TextDecoration.BOLD).decorate(TextDecoration.UNDERLINED).color(TEXT_COLOR_1));

                armorStands.get(1).customName(getConnectionText(index-1,connections,portals).decorate(TextDecoration.ITALIC));
                armorStands.get(2).customName(Component.text("-> ").color(TEXT_COLOR_1).decorate(TextDecoration.BOLD).append(getConnectionText(index,connections,portals).decorate(TextDecoration.BOLD)));
                armorStands.get(3).customName(getConnectionText(index+1,connections,portals).decorate(TextDecoration.ITALIC));
            } else {
                armorStands.forEach(e->e.setCustomNameVisible(false));
            }

        });
    }

    public Component getConnectionText(int index, List<Location> connections, List<Map<?,?>> portals) {
        if (connections.size() == 0) {return Component.text("No connections").color(TEXT_COLOR_RED);}
        index = (index + connections.size()) % connections.size();

        Location loc = connections.get(index);


        Map<?,?> portal = portals.stream().filter(p -> ((Location)p.get("location")).equals(loc)).findFirst().orElse(null);
        if (portal == null) {return Component.text("");}

        Component c = Component.text((String) portal.get("displayName")).color(TEXT_COLOR_2).append(Component.text(" (" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ")").color(TEXT_COLOR_3));
        return (boolean) portal.get("valid") ? c : c.color(TextColor.color(TEXT_COLOR_RED)).decorate(TextDecoration.STRIKETHROUGH);
    }

    public void animatePortals() {
        Random rand = new Random();
        for (List<Location> portal : activePortals) {
            int len = portal.size() - 1;
            for (int i = 0; i < len/5; i++) {
                int idx = rand.nextInt(len) + 1;
                Location loc = portal.get(idx);
                loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(Math.random(), 1.4, Math.random()), 1, 0, 0, 0, 0);

            }
//            Location loc = portal.get(0);
//            loc.getWorld().spawnParticle(Particle.NAUTILUS, loc.clone().add(0.5,2,0.5), 4, 0.15, 0.4, 0.15, 0);
        }
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
