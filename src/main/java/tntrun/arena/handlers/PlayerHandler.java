/**
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */

package tntrun.arena.handlers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.SkullType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;

import tntrun.TNTRun;
import tntrun.arena.Arena;
import tntrun.arena.structure.StructureManager.TeleportDestination;
import tntrun.utils.Bars;
import tntrun.utils.TitleMsg;
import tntrun.utils.Utils;
import tntrun.messages.Messages;

public class PlayerHandler {

	private TNTRun plugin;
	private Arena arena;
	private String version = Bukkit.getBukkitVersion().split("-")[0];
	private Map<String, Scoreboard> prejoinScoreboards = new HashMap<String, Scoreboard>();

	public PlayerHandler(TNTRun plugin, Arena arena) {
		this.plugin = plugin;
		this.arena = arena;
	}

	// check if player can join the arena
	public boolean checkJoin(Player player) {
		if (arena.getStructureManager().getWorld() == null) {
			Messages.sendMessage(player, Messages.arenawolrdna);
			return false;
		}
		if (!arena.getStatusManager().isArenaEnabled()) {
			Messages.sendMessage(player, Messages.arenadisabled);
			return false;
		}
		if (arena.getStatusManager().isArenaRunning()) {
			Messages.sendMessage(player, Messages.arenarunning);
			return false;
		}
		if (arena.getStatusManager().isArenaRegenerating()) {
			Messages.sendMessage(player, Messages.arenaregenerating);
			return false;
		}
		if (!player.hasPermission("tntrun.join")) {
			Messages.sendMessage(player, Messages.nopermission);
			return false;
		}
		if (player.isInsideVehicle()) {
			Messages.sendMessage(player, Messages.arenavehicle);
			return false;
		}
		if (arena.getPlayersManager().getPlayersCount() == arena.getStructureManager().getMaxPlayers()) {
			Messages.sendMessage(player, Messages.limitreached);
			return false;
		}
		if (plugin.amanager.getPlayerArena(player.getName()) != null) {
			Messages.sendMessage(player, Messages.arenajoined);
			return false;
		}
		return true;
	}

	// spawn player on arena
	public void spawnPlayer(final Player player, String msgtoplayer, String msgtoarenaplayers) {
		// teleport player to arena
		plugin.pdata.storePlayerLocation(player);
		player.teleport(arena.getStructureManager().getSpawnPoint());
		// set player visible to everyone
		for (Player aplayer : Bukkit.getOnlinePlayers()) {
			if (!version.contains("1.12.2")) {
				aplayer.showPlayer(player);
			} else {
				aplayer.showPlayer(plugin, player);
			}
		}
		arena.getPlayersManager().add(player);
		if (Utils.debug()) {
			plugin.getLogger().info("Player " + player.getName() + " joined arena " + arena.getArenaName());
			plugin.getLogger().info("Players in arena: " + arena.getPlayersManager().getPlayersCount());
		}
		// change player status
		plugin.pdata.storePlayerGameMode(player);
		plugin.pdata.storePlayerFlight(player);
		player.setFlying(false);
		player.setAllowFlight(false);
		plugin.pdata.storePlayerLevel(player);
		plugin.pdata.storePlayerInventory(player);
		plugin.pdata.storePlayerArmor(player);
		plugin.pdata.storePlayerPotionEffects(player);
		plugin.pdata.storePlayerHunger(player);
		// update inventory
		player.updateInventory();
		storePrejoinScoreboard(player);
		//set full countdown
		if(!arena.getStatusManager().isArenaStarting()){
			arena.getGameHandler().count = arena.getStructureManager().getCountdown();
		}
		// send message to player
		if(plugin.getConfig().getBoolean("special.UseTitle") == false){
			Messages.sendMessage(player, msgtoplayer);
		}	
		// send message to other players
		for (Player oplayer : arena.getPlayersManager().getPlayers()) {
			msgtoarenaplayers = msgtoarenaplayers.replace("{PLAYER}", player.getName()).replace("{RANK}", Utils.getRank(player));
			Messages.sendMessage(oplayer, msgtoarenaplayers);
			// send title for players
			TitleMsg.sendFullTitle(oplayer, TitleMsg.join.replace("{PLAYER}", player.getName()), TitleMsg.subjoin.replace("{PLAYER}", player.getName()), 10, 20, 20, plugin);
		}
		// start cooldown and add leave item
		Bukkit.getScheduler().runTaskLater(plugin, new Runnable(){
			public void run(){
				if (plugin.getConfig().getBoolean("items.leave.use")) {
					addLeaveItem(player);
				}
				if (plugin.getConfig().getBoolean("items.vote.use")) {
					addVoteDiamond(player);
				}
				if (plugin.getConfig().getBoolean("items.shop.use")) {
					addShop(player);
				}
				if (plugin.getConfig().getBoolean("items.info.use")) {
					addInfo(player);
				}
				if (plugin.getConfig().getBoolean("items.stats.use")) {
					addStats(player);
				}
				if (plugin.getConfig().getBoolean("items.effects.use")) {
					if (Bukkit.getPluginManager().getPlugin("TNTRun-Effects") != null) {
						addEffects(player);
					}
				}
			}
		}, 5L);
		// send message about arena player count
		if(plugin.getConfig().getBoolean("special.UseBarApi") == false || Bukkit.getPluginManager().getPlugin("BarAPI") == null){
			String message = Messages.playerscountinarena;
			message = message.replace("{COUNT}", String.valueOf(arena.getPlayersManager().getPlayersCount()));
			Messages.sendMessage(player, message);
		}
		// modify signs
		plugin.signEditor.modifySigns(arena.getArenaName());
		// create scoreboard
		arena.getGameHandler().createWaitingScoreBoard();
		// modify bars
		if (!arena.getStatusManager().isArenaStarting()) {
			for (Player oplayer : arena.getPlayersManager().getPlayers()) {
				Bars.setBar(oplayer, Bars.waiting, arena.getPlayersManager().getPlayersCount(), 0, arena.getPlayersManager().getPlayersCount() * 100 / arena.getStructureManager().getMinPlayers(), plugin);
				plugin.getSoundHandler().playPlingSound(oplayer, 5, 2);
			}
		}
		// check for game start
		if (!arena.getStatusManager().isArenaStarting() && arena.getPlayersManager().getPlayersCount() == arena.getStructureManager().getMinPlayers()) {
		arena.getGameHandler().runArenaCountdown();
		}
	} 

	// move to spectators
	public void spectatePlayer(final Player player, String msgtoplayer, String msgtoarenaplayers) {
		// if existing spectator leaves bounds, send back to spectator spawn
		if (arena.getPlayersManager().isSpectator(player.getName())) {
			player.teleport(arena.getStructureManager().getSpectatorSpawn());
			return;
		}
		// remove form players
		arena.getPlayersManager().remove(player);
		// add to lostPlayers
		arena.getGameHandler().lostPlayers++;
		// remove scoreboard
		removeScoreboard(player);
		// teleport to spectators spawn
		player.teleport(arena.getStructureManager().getSpectatorSpawn());
		// clear inventory
		player.getInventory().clear();
		player.getInventory().setArmorContents(new ItemStack[4]);
		// allow flight
		player.setAllowFlight(true);
		player.setFlying(true);
		// hide from others
		for (Player oplayer : Bukkit.getOnlinePlayers()) {
			if (!version.contains("1.12.2")) {
				oplayer.hidePlayer(player);
			} else {
				oplayer.hidePlayer(plugin, player);
			}
		}
		// send message to player
		Messages.sendMessage(player, msgtoplayer);
		// modify signs
		plugin.signEditor.modifySigns(arena.getArenaName());
		// send message to other players and update bars
		for (Player oplayer : arena.getPlayersManager().getAllParticipantsCopy()) {
			msgtoarenaplayers = msgtoarenaplayers.replace("{PLAYER}", player.getName()).replace("{RANK}", Utils.getRank(player));
			Messages.sendMessage(oplayer, msgtoarenaplayers);
		}
		// add to spectators
		arena.getPlayersManager().addSpectator(player);
		// start cooldown and add leave item
		Bukkit.getScheduler().runTaskLater(plugin, new Runnable(){
			public void run(){
				ItemStack item = new ItemStack(Material.getMaterial(plugin.getConfig().getString("items.leave.material")));
				ItemMeta im = item.getItemMeta();
				im.setDisplayName(plugin.getConfig().getString("items.leave.name").replace("&", "§"));
				item.setItemMeta(im);
				
				player.getInventory().setItem(8, item);
				
				if(plugin.getConfig().getBoolean("items.info.use")){
					addInfo(player);
				}
				if(plugin.getConfig().getBoolean("items.stats.use")){
					addStats(player);
				}
			}
		}, 5L);
	}

	/**
	 * If the winner attempts to leave, teleport to arena spawn.
	 * For other players, if we have a spectator spawn then we will move player to spectators, otherwise we will remove player from arena.
	 * @param player
	 */
	public void dispatchPlayer(Player player) {
		if (arena.getPlayersManager().getPlayersCount() == 1) {
			player.teleport(arena.getStructureManager().getSpawnPoint());
		} else if (arena.getStructureManager().getSpectatorSpawnVector() != null) {
			spectatePlayer(player, Messages.playerlosttoplayer, Messages.playerlosttoothers);
		} else {
			leavePlayer(player, Messages.playerlosttoplayer, Messages.playerlosttoothers);
		}
	}

	// remove player from arena
	public void leavePlayer(Player player, String msgtoplayer, String msgtoarenaplayers) {
		// reset spectators
		boolean spectator = arena.getPlayersManager().isSpectator(player.getName());
		if (spectator) {
			arena.getPlayersManager().removeSpecator(player.getName());
			for (Player oplayer : Bukkit.getOnlinePlayers()) {
				if (!version.contains("1.12.2")) {
					oplayer.showPlayer(player);
				} else {
					oplayer.showPlayer(plugin, player);
				}
			}
		} else if(arena.getStatusManager().isArenaRunning()) {
			arena.getGameHandler().lostPlayers++;
		}
		// disable flight for winner as well as spectators
		player.setAllowFlight(false);
		player.setFlying(false);

		// remove scoreboard
		removeScoreboard(player);
		// remove player from arena and restore his state
		removePlayerFromArenaAndRestoreState(player, false);
		// should not send messages and other things when player is a spectator
		if (spectator) {
			return;
		}
		// send message to player
		Messages.sendMessage(player, msgtoplayer);
		// modify signs
		plugin.signEditor.modifySigns(arena.getArenaName());
		// update scoreboard for remaining players
		if(!arena.getStatusManager().isArenaRunning()){
			arena.getGameHandler().createWaitingScoreBoard();
		}
		// send message to other players and update bars
		for (Player oplayer : arena.getPlayersManager().getAllParticipantsCopy()) {
			msgtoarenaplayers = msgtoarenaplayers.replace("{PLAYER}", player.getName());
			Messages.sendMessage(oplayer, msgtoarenaplayers);
			if (!arena.getStatusManager().isArenaStarting() && !arena.getStatusManager().isArenaRunning()) {
				Bars.setBar(oplayer, Bars.waiting, arena.getPlayersManager().getPlayersCount(), 0, arena.getPlayersManager().getPlayersCount() * 100 / arena.getStructureManager().getMinPlayers(), plugin);
			}
		}
	}

	protected void leaveWinner(Player player, String msgtoplayer) {
		removeScoreboard(player);
		player.setFlying(false);
		// remove player from arena and restore his state
		removePlayerFromArenaAndRestoreState(player, true);
		// send message to player
		Messages.sendMessage(player, msgtoplayer);
		// modify signs
		plugin.signEditor.modifySigns(arena.getArenaName());
	}
	
	private void removePlayerFromArenaAndRestoreState(Player player, boolean winner) {
		// remove vote
		votes.remove(player.getName());
		// remove bar
		Bars.removeBar(player);
		// remove player on arena data
		arena.getPlayersManager().remove(player);
		// remove all potion effects
		for (PotionEffect effect : player.getActivePotionEffects()) {
			player.removePotionEffect(effect.getType());
		}
		if (Utils.debug()) {
			plugin.getLogger().info("Player " + player.getName() + " left arena " + arena.getArenaName());
			plugin.getLogger().info("Players in arena: " + arena.getPlayersManager().getPlayersCount());
			plugin.getLogger().info("Spectators in arena: " + arena.getPlayersManager().getSpectators().size());
		}
		// restore player status
		plugin.pdata.restorePlayerHunger(player);
		plugin.pdata.restorePlayerPotionEffects(player);
		plugin.pdata.restorePlayerArmor(player);
		plugin.pdata.restorePlayerInventory(player);
		plugin.pdata.restorePlayerLevel(player);
		// add player damage resistance
		player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 80, 80, true), true);
		restorePrejoinScoreboard(player);
		// restore location or teleport to lobby
		if (arena.getStructureManager().getTeleportDestination() == TeleportDestination.LOBBY && plugin.globallobby.isLobbyLocationWorldAvailable()) {
			player.teleport(plugin.globallobby.getLobbyLocation());
			plugin.pdata.clearPlayerLocation(player);
		} else {
			plugin.pdata.restorePlayerLocation(player);
		}
		// reward player before restoring gamemode if player is winner
		if (winner) {
			arena.getStructureManager().getRewards().rewardPlayer(player);
		}
		plugin.pdata.restorePlayerGameMode(player);
		// update inventory
		player.updateInventory();
		
		plugin.pdata.restorePlayerFlight(player);
		
		if(player.getGameMode() == GameMode.CREATIVE){
			player.setAllowFlight(true);
		}		
		
		// check if arena has 0 players
		if (arena.getStatusManager().isArenaRunning() && arena.getPlayersManager().getPlayersCount() == 0) {
			if (Utils.debug()) {
				plugin.getLogger().info("PH calling stopArena...");
			}
			arena.getGameHandler().stopArena();
		}
	}

	// vote for game start
	private HashSet<String> votes = new HashSet<String>();

	public boolean vote(Player player) {
		if (!votes.contains(player.getName())) {
			votes.add(player.getName());
			if (!arena.getStatusManager().isArenaStarting() && forceStart()) {
				arena.getGameHandler().runArenaCountdown();
			}
			return true;
		}
		return false;
	}
	
	public boolean forceStart() {
		if (arena.getPlayersManager().getPlayersCount() > 1 && votes.size() >= arena.getPlayersManager().getPlayersCount() * arena.getStructureManager().getVotePercent()) {
			return true;
		}
		return false;
	}
	
	private void addInfo(Player p){
		ItemStack item = new ItemStack(Material.getMaterial(plugin.getConfig().getString("items.info.material")));	     
	    ItemMeta meta = item.getItemMeta();
	    meta.setDisplayName(plugin.getConfig().getString("items.info.name").replace("&", "§"));
	    item.setItemMeta(meta);
	    
	    p.getInventory().addItem(item);
	    
	}
	
	private void addVoteDiamond(Player p){
		ItemStack item = new ItemStack(Material.getMaterial(plugin.getConfig().getString("items.vote.material")));     
	    ItemMeta meta = item.getItemMeta();
	    meta.setDisplayName(plugin.getConfig().getString("items.vote.name").replace("&", "§"));
	    item.setItemMeta(meta);
	    
	    p.getInventory().addItem(item);
	}
	
	private void addShop(Player p){
		ItemStack item = new ItemStack(Material.getMaterial(plugin.getConfig().getString("items.shop.material"))); 
	    ItemMeta meta = item.getItemMeta();
	    meta.setDisplayName(plugin.getConfig().getString("items.shop.name").replace("&", "§"));
	    item.setItemMeta(meta);
	    
	    p.getInventory().addItem(item);
	}
	
	private void addStats(Player p){
		Material statsMaterial = Material.getMaterial(plugin.getConfig().getString("items.stats.material"));
		ItemStack item = null;

		if (statsMaterial == Material.SKULL_ITEM) {
			item = new ItemStack(statsMaterial, 1, (short) SkullType.PLAYER.ordinal());
			SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
			skullMeta.setOwner(p.getName());
			item.setItemMeta(skullMeta);
		} else {
			item = new ItemStack(statsMaterial);
		}
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(plugin.getConfig().getString("items.stats.name").replace("&", "§"));
		item.setItemMeta(meta);

		p.getInventory().addItem(item);
	}

	private void addEffects(Player p){
		ItemStack item = new ItemStack(Material.getMaterial(plugin.getConfig().getString("items.effects.material")));
	    ItemMeta meta = item.getItemMeta();
	    meta.setDisplayName(plugin.getConfig().getString("items.effects.name").replace("&", "§"));
	    item.setItemMeta(meta);
	    
	    p.getInventory().addItem(item);
	}

	private void addLeaveItem(Player player) {
		ItemStack item = new ItemStack(Material.getMaterial(plugin.getConfig().getString("items.leave.material")));
		ItemMeta im = item.getItemMeta();
		im.setDisplayName(plugin.getConfig().getString("items.leave.name").replace("&", "§"));
		item.setItemMeta(im);
	
		player.getInventory().setItem(8, item);
	}

	private void removeScoreboard(Player player) {
		if(!plugin.getConfig().getBoolean("special.UseScoreboard")) {
			return;
		}
		player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
	}

	public void storePrejoinScoreboard(Player player) {
		if (!plugin.getConfig().getBoolean("special.UseScoreboard")) {
			return;
		}
		prejoinScoreboards.put(player.getName(), player.getScoreboard());
	}

	public void restorePrejoinScoreboard(Player player) {
		if(!plugin.getConfig().getBoolean("special.UseScoreboard")) {
			return;
		}
		if (prejoinScoreboards.get(player.getName()) != null) {
			player.setScoreboard(prejoinScoreboards.remove(player.getName()));
		}
	}
}
