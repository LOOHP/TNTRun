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

package tntrun.utils;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.google.common.collect.Streams;

import tntrun.TNTRun;
import tntrun.messages.Messages;

public class Stats {

	private TNTRun plugin;
	private File file;
	private int position;
	private String lbentry;
	private String lbplaceholdername;
	private String lbplaceholdervalue;

	private static HashMap<String, Integer> pmap = new HashMap<String, Integer>();
	private static HashMap<String, Integer> wmap = new HashMap<String, Integer>();

	public Stats(TNTRun plugin) {
		this.plugin = plugin;
		file = new File(plugin.getDataFolder(), "stats.yml");
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		loadStats();
	}

	/**
	 * Loads the player stats into 2 maps representing games played and games won.
	 */
	private void loadStats() {
		if (plugin.isFile()) {
			getStatsFromFile();
			return;
		}
		getWinsFromDB();
		getPlayedFromDB();
	}

	/**
	 * Increment the number of played games in the map, and save to file.
	 * @param player
	 * @param value
	 */
	public void addPlayedGames(Player player, int value) {
		String uuid = getPlayerUUID(player);
		if (pmap.containsKey(uuid)) {
			pmap.put(uuid, pmap.get(uuid) + value);

		} else {
			pmap.put(uuid, value);
		}
		saveStats(player, "played");
	}

	/**
	 * Increment the number of wins for the player in the map, and save to file.
	 * @param player
	 * @param value
	 */
	public void addWins(Player player, int value) {
		String uuid = getPlayerUUID(player);
		if (wmap.containsKey(uuid)) {
			wmap.put(uuid, wmap.get(uuid) + value);
	
		} else {
			wmap.put(uuid, value);
		}
		saveStats(player, "wins");
	}

	public int getLosses(Player player) {
		return getPlayedGames(player) - getWins(player);
	}

	public int getPlayedGames(OfflinePlayer player) {
		String uuid = getPlayerUUID(player);
		return pmap.containsKey(uuid) ? pmap.get(uuid) : 0;
	}

	public int getWins(OfflinePlayer player) {
		String uuid = getPlayerUUID(player);
		return wmap.containsKey(uuid) ? wmap.get(uuid) : 0;
	}

	public void getLeaderboard(CommandSender sender, int entries) {
		position = 0;
		wmap.entrySet().stream()
			.sorted(Entry.comparingByValue(Comparator.reverseOrder()))
			.limit(entries)
			.forEach(e -> {
			if (Bukkit.getOnlineMode()) {
				OfflinePlayer p = Bukkit.getOfflinePlayer(UUID.fromString(e.getKey()));
				if (!p.hasPlayedBefore()) {
					// continue to next entry
					return;
				}
				lbentry = p.getName();
			} else {
				lbentry = e.getKey();
			}
			position++;
			Messages.sendMessage(sender, Messages.leaderboard
					.replace("{POSITION}", String.valueOf(position))
					.replace("{PLAYER}", lbentry)
					.replace("{WINS}", String.valueOf(e.getValue())));
			});	   
		return; 
	}

	private static boolean isValidUuid(String uuid) {
		try {
			UUID.fromString(uuid);
		} catch (IllegalArgumentException ex){
			return false;
		}
		return true;
	}

	private void getStatsFromFile() {
		FileConfiguration config = YamlConfiguration.loadConfiguration(file);
		ConfigurationSection stats = config.getConfigurationSection("stats");

		if (stats != null) {
			if (Bukkit.getOnlineMode()) {
				for (String uuid : stats.getKeys(false)) {
					// validate UUID as file could contain player names if its been in offline mode
					if (!isValidUuid(uuid)) {
						continue;
					}
					wmap.put(uuid, config.getInt("stats." + uuid + ".wins", 0));
					pmap.put(uuid, config.getInt("stats." + uuid + ".played", 0));
				}
			} else {
				for (String playerName : stats.getKeys(false)) {
					if (isValidUuid(playerName)) {
						continue;
					}
					wmap.put(playerName, config.getInt("stats." + playerName + ".wins", 0));
					pmap.put(playerName, config.getInt("stats." + playerName + ".played", 0));
				}
			}
		}
	}

	private void getWinsFromDB() {
		try {
			ResultSet rs;

			rs = plugin.mysql.query("SELECT * FROM `stats` ORDER BY wins DESC LIMIT 99999").getResultSet();

			while (rs.next()) {
				String playerName = rs.getString("username");

				// check if valid uuid
				if (Bukkit.getOnlineMode()) {
					if (!isValidUuid(playerName)) {
						continue;
					}
				} else if (isValidUuid(playerName)) {
					continue;
				}
				wmap.put(playerName, rs.getInt("wins"));
			}
		} catch (SQLException ex) {
			ex.printStackTrace();
		}
	}

	private void getPlayedFromDB() {
		try {
			ResultSet rs;

			rs = plugin.mysql.query("SELECT * FROM `stats` ORDER BY played DESC LIMIT 99999").getResultSet();

			while (rs.next()) {
				String playerName = rs.getString("username");

				// check if valid uuid
				if (Bukkit.getOnlineMode()) {
					if (!isValidUuid(playerName)) {
						continue;
					}
				} else if (isValidUuid(playerName)) {
					continue;
				}
				pmap.put(playerName, rs.getInt("played"));
			}
		} catch (SQLException ex) {
			ex.printStackTrace();
		}
	}

	public HashMap<String, Integer> getWinMap() {
		return wmap;
	}

	private void saveStats(Player player, String statname) {
		if (plugin.isFile()) {
			saveStatsToFile(player, statname);
			return;
		}
		saveStatsToDB(player, statname);
	}

	private void saveStatsToFile(Player player, String statname) {
		FileConfiguration config = YamlConfiguration.loadConfiguration(file);

		String uuid = getPlayerUUID(player);

		if (statname.equalsIgnoreCase("played")) {
			config.set("stats." + uuid + ".played", pmap.get(uuid));

		} else if (statname.equalsIgnoreCase("wins")) {
			config.set("stats." + uuid + ".wins", wmap.get(uuid));
		}
		try {
			config.save(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void saveStatsToDB(Player player, String statname) {
		String uuid = getPlayerUUID(player);

		if (statname.equalsIgnoreCase("played")) {
			updateDB("played", uuid, pmap.get(uuid));

		} else if (statname.equalsIgnoreCase("wins")) {
			updateDB("wins", uuid, wmap.get(uuid));
		}
	}

	private void updateDB(String statname, String player, Integer value) {
		new BukkitRunnable() {
			@Override
			public void run() {
				plugin.mysql.query("UPDATE `stats` SET `" + statname
						+ "`='" + value + "' WHERE `username`='" + player + "';");
			}
		}.runTaskAsynchronously(plugin);
	}

	private String getPlayerUUID(OfflinePlayer player) {
		return Bukkit.getOnlineMode() ? player.getUniqueId().toString() : player.getName();
	}

	/**
	 * Returns the player (and wins) occupying the requested leaderboard position.
	 * @param rank
	 * @return "[player] : [wins]"
	 */
	public String getLeaderboardPosition(Integer rank) {
		if (rank > wmap.size()) {
			return null;
		}
		Optional<Entry<String, Integer>> opt = Streams.findLast(
				wmap.entrySet().stream()
				.sorted(Entry.comparingByValue(Comparator.reverseOrder()))
				.limit(rank));
		opt.ifPresent(x -> {
			lbplaceholdername = opt.get().getKey();
			if (Bukkit.getOnlineMode()) {
				OfflinePlayer p = Bukkit.getOfflinePlayer(UUID.fromString(opt.get().getKey()));
				if (p.hasPlayedBefore()) {
					lbplaceholdername = p.getName();
				}
			}
			lbplaceholdervalue = String.valueOf(opt.get().getValue());
		});
		return lbplaceholdername + " : " + lbplaceholdervalue;
	}
}
