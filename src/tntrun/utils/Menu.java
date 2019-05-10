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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.google.common.base.Enums;

import tntrun.FormattingCodesParser;
import tntrun.TNTRun;
import tntrun.arena.Arena;
import tntrun.messages.Messages;

public class Menu {

	private final TNTRun plugin;

	public Menu(TNTRun plugin) {
		this.plugin = plugin;
	}

	public void buildMenu(Player player) {
		List<String> lores = new ArrayList<String>();
		int size = 0; 
		int keyPos = 9;

		ItemStack is = new ItemStack(getMenuItem());
		ItemMeta im = is.getItemMeta();	

		Collection<Arena> arenas = plugin.amanager.getArenas();
		if (arenas.size() < 8){
			size = 27;
		} else if (arenas.size() < 15){
			size = 36;
		} else if (arenas.size() < 22){
			size = 45;
		} else if (arenas.size() < 29){
			size = 54;
		}

		Inventory inv = Bukkit.createInventory(player, size, FormattingCodesParser.parseFormattingCodes(Messages.menutitle));

		for (Arena arena : arenas) {
			lores = new ArrayList<String>();
			im.setDisplayName(ChatColor.GREEN + arena.getArenaName());

			lores.add("Players: " + getArenaCount(arena));
			im.setLore(lores);
			is.setItemMeta(im);

			// put the arenas in the centre rows of the inventory
			switch (keyPos) {
				case 16 : case 25 : case 34 : case 43 :
					keyPos+=3;
					break;
				default :  keyPos++;
			}
			inv.setItem(keyPos,is);
		}
		fillEmptySlots(inv, size);
		player.openInventory(inv);
	}

	private void fillEmptySlots(Inventory inv, Integer size) {
		ItemStack is = new ItemStack(getPane());
		if (is.getType() == Material.AIR) {
			return;
		}
		for (int i = 0; i < size; i++) {
			if (inv.getItem(i) == null) {
				inv.setItem(i, is);
			}
		}
	}

	private Material getPane() {
		String colour = plugin.getConfig().getString("menu.panecolor", "LIGHT_BLUE").toUpperCase();
		if (colour == "NONE" || colour == "AIR" || Enums.getIfPresent(DyeColor.class, colour).orNull() == null) {
			return Material.AIR;
		}
		return Material.getMaterial(colour + "_STAINED_GLASS_PANE");
	}

	private Material getMenuItem() {
		String item = plugin.getConfig().getString("menu.item", "TNT").toUpperCase();
		return Material.getMaterial(item) != null ? Material.getMaterial(item) : Material.TNT;
	}

	private String getArenaCount(Arena arena) {
		int maxPlayers = arena.getStructureManager().getMaxPlayers();
		int players = arena.getPlayersManager().getPlayersCount();
		return players + " / " + maxPlayers;
	}
}
