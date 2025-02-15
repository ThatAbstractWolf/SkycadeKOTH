package net.skycade.koth.game;

import com.massivecraft.factions.entity.FactionColl;
import com.massivecraft.factions.entity.MPlayer;
import net.skycade.koth.SkycadeKoth;
import net.skycade.koth.events.phase.PhaseChangeEvent;
import net.skycade.koth.events.zone.EnterCaptureZoneEvent;
import net.skycade.koth.events.zone.ExitCaptureZoneEvent;
import net.skycade.koth.game.arena.Arena;
import net.skycade.koth.game.countdown.Countdown;
import net.skycade.koth.utils.LocationUtil;
import net.skycade.koth.utils.messages.MessageUtil;
import net.skycade.koth.utils.placeholder.Placeholder;
import net.skycade.koth.utils.placeholder.PlaceholderManager;
import net.skycade.koth.utils.scoreboard.Scoreboard;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**************************************************************************************************
 *     Copyright 2018 Jake Brown                                                                  *
 *                                                                                                *
 *     Licensed under the Apache License, Version 2.0 (the "License");                            *
 *     you may not use this file except in compliance with the License.                           *
 *     You may obtain a copy of the License at                                                    *
 *                                                                                                *
 *         http://www.apache.org/licenses/LICENSE-2.0                                             *
 *                                                                                                *
 *     Unless required by applicable law or agreed to in writing, software                        *
 *     distributed under the License is distributed on an "AS IS" BASIS,                          *
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.                   *
 *     See the License for the specific language governing permissions and                        *
 *     limitations under the License.                                                             *
 **************************************************************************************************/
public class KOTHGame implements Listener {

    /** Instance for {@link SkycadeKoth} plugin. */
    private SkycadeKoth plugin;

    /** ID for the game.. typically a name. */
    private String gameId;

    /** List of players in the KOTH gamemode */
    private List<UUID> activePlayers;

    /** Current arena being used in the game. */
    private Arena currentArena;

    /** Current phase of the game */
    private GamePhase currentPhase;

    /** Queue of players capturing the area. */
    private Queue<UUID> currentlyWithinBoundaries;

    /** Current duration for capturing at the current time. */
    private int currentCaptureTime;

    /** Runnable to shrink the time to capture a zone. */
    private BukkitRunnable captureTimeShrinkerRunnable;

    /** How many times the capture time has shrunk. */
    private int captureTimeRanAmount;

    /**
     * Create a new instance of {@link KOTHGame}
     * @param plugin - plugin instance/
     * @param gameId - ID for the specific game.
     * @param currentArena - Current arena for this specific {@link KOTHGame}
     */
    public KOTHGame(SkycadeKoth plugin, String gameId, Arena currentArena) {
        this.plugin = plugin;
        this.gameId = gameId;
        this.currentArena = currentArena;

        this.activePlayers = new ArrayList<>();

        currentlyWithinBoundaries = new LinkedList<>();

        currentCaptureTime = currentArena.getStartingDuration();

        setCurrentPhase(GamePhase.WAITING);
    }

    /**
     * Start the countdown for the actual game.
     */
    public void startCountdown() {

        setCurrentPhase(GamePhase.COUNTDOWN);

        // Phase countdown

        plugin.getCountdownManager().startCountdown(
                new Countdown(plugin, "start_game", 60, new int[] { 1, 2, 3, 4, 5, 10, 15, 30, 60 }),
                currentValue -> getActivePlayers().forEach(uuid -> MessageUtil.sendMessage(Bukkit.getPlayer(uuid), "countdown", Collections.singletonList(new Placeholder("%seconds%", currentValue)))), finished -> {

                    // Start the game and set it to in progress.
                    setCurrentPhase(GamePhase.IN_PROGRESS);

                    getActivePlayers().forEach(uuid -> {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null) {
                            player.teleport(currentArena.getSpawnLocation());
                            MessageUtil.sendMessage(player, "gamestarted", Arrays.asList(
                                    new Placeholder("%gamename%", gameId),
                                    new Placeholder("%arenaname%", currentArena.getArenaName())
                            ));

                            plugin.getScoreboardManager().addScoreboard(new Scoreboard(player, "&b&lSky&f&lCade &3&lKOTH",
                                    new String[] {
                                            "",
                                            "&7Goodluck &b" + player.getName(),
                                            " ",
                                            ChatColor.GREEN.toString(), // Capturing identifier
                                            ChatColor.DARK_PURPLE.toString(), // Capturing Faction identifier
                                            "  ",
                                            ChatColor.RED.toString(), // Remaining time identifier
                                            ChatColor.GRAY.toString(), // Automatic Shutdown identifier
                                            "   ",
                                    }));

                            int minutes = (currentCaptureTime % 3600) / 60;
                            int seconds = currentCaptureTime % 60;

                            plugin.getScoreboardManager().getScoreboard(Bukkit.getPlayer(uuid)).updateTeam("capturing", ChatColor.GREEN.toString(), "&7Capturing: ", "&aNone");
                            plugin.getScoreboardManager().getScoreboard(Bukkit.getPlayer(uuid)).updateTeam("capturingfac", ChatColor.DARK_PURPLE.toString(), "&7Faction: ", "&aNone");
                            plugin.getScoreboardManager().getScoreboard(Bukkit.getPlayer(uuid)).updateTeam("remainingtime", ChatColor.RED.toString(), "&7Capturing: ", "&c" + minutes + ":" + String.format("%02d", seconds));

                            captureTimeShrinkerRunnable = new BukkitRunnable() {

                                @Override
                                public void run() {

                                    // Shrink the capture time..
                                    switch (captureTimeRanAmount) {
                                        case 2: // decrease from X by 5 so 15 = 10 etc
                                            currentCaptureTime = (((currentCaptureTime / 60) - 5) * 60);
                                            break;
                                        case 3: // decrease from X by 4 so 10 = 6 etc.
                                            currentCaptureTime = (((currentCaptureTime / 60) - 4) * 60);
                                            break;
                                        case 4: // decrease from X by 3 so 6 = 3 etc.
                                            currentCaptureTime = (((currentCaptureTime / 60) - 3) * 60);
                                            break;
                                        default:
                                            break;
                                    }

                                    if (captureTimeRanAmount > 1) {

                                        int minutes = (currentCaptureTime % 3600) / 60;

                                        getActivePlayers().forEach(uuid -> {
                                            Player player = Bukkit.getPlayer(uuid);
                                            MessageUtil.sendMessage(player, "capturetimedecrease", Collections.singletonList(
                                                    new Placeholder("%minutes%", minutes)
                                            ));
                                        });
                                    }

                                    captureTimeRanAmount += 1;
                                }
                            };

                            captureTimeShrinkerRunnable.runTaskTimer(plugin, 0L, getCurrentArena().getShrinkDuration()* 20);
                        }
                    });

                    // Will reset the game after 60 minutes and end it automatically.
                    plugin.getCountdownManager().startCountdown(new Countdown(plugin, "automaticend", (60 * 60), true), intervals -> {

                        int minutes = (intervals % 3600) / 60;
                        int seconds = intervals % 60;

                        getActivePlayers().forEach(active -> plugin.getScoreboardManager().getScoreboard(Bukkit.getPlayer(active)).updateTeam("autoend", ChatColor.GRAY.toString(), "&7Auto End: ", "&b" + minutes + ":" + String.format("%02d", seconds)));
                    }, autoFinish -> {
                        
                        // Automatically stops the game due to the time running out..
                        setCurrentPhase(GamePhase.FINISHED);

                        getActivePlayers().forEach(activePlayer -> {
                            Player player = Bukkit.getPlayer(activePlayer);
                            if (plugin.getScoreboardManager().getScoreboard(player) != null) {
                                plugin.getScoreboardManager().getScoreboard(player).remove();
                                plugin.getScoreboardManager().removeScoreboard(player);
                            }

                            player.teleport(Bukkit.getWorld("world").getSpawnLocation());
                            MessageUtil.sendMessageToPlayer(player, "The time ran out.. no one won the game.");
                        });

                        captureTimeShrinkerRunnable.cancel();
                        plugin.getGameManager().endGame(this);
                    });
                }
        );
    }

    /**
     * Reset the capturing zone countdown (allows for resetting each time a player leaves the zone).
     */
    private void resetZoneCountdown() {

        if (plugin.getCountdownManager().getCountdown("capture_" + gameId) != null) {
            Countdown countdown = plugin.getCountdownManager().getCountdown("capture_" + gameId);
            countdown.stop();
            plugin.getCountdownManager().removeCountdown("capture_" + gameId);
        }

        plugin.getCountdownManager().startCountdown(new Countdown(plugin, "capture_" + gameId, currentCaptureTime + 1, true), intervals -> {
            getActivePlayers().forEach(uuid -> {

                int minutes = (intervals % 3600) / 60;
                int seconds = intervals % 60;

                Player capturing = Bukkit.getPlayer(currentlyWithinBoundaries.peek());
                String faction = MPlayer.get(capturing.getUniqueId()).getFactionName();

                plugin.getScoreboardManager().getScoreboard(Bukkit.getPlayer(uuid)).updateTeam("capturing", ChatColor.GREEN.toString(), "&7Capturing: ", capturing.getName());
                plugin.getScoreboardManager().getScoreboard(Bukkit.getPlayer(uuid)).updateTeam("capturingfac", ChatColor.DARK_PURPLE.toString(), "&7Faction: ", "&a" + (faction.length() < 1 ? "None" : faction));
                plugin.getScoreboardManager().getScoreboard(Bukkit.getPlayer(uuid)).updateTeam("remainingtime", ChatColor.RED.toString(), "&7Capturing: ", "&c" + minutes + ":" + String.format("%02d", seconds));
            });

            if (intervals > 60 && intervals % 60 == 0) {
                getActivePlayers().forEach(uuid -> {
                    Player player = Bukkit.getPlayer(uuid);

                    MessageUtil.sendMessage(player, "currentlycapturing", Arrays.asList(
                            new Placeholder("%capturing%", (currentlyWithinBoundaries.peek() == null ? "No one" : Bukkit.getPlayer(currentlyWithinBoundaries.peek()).getName())),
                            new Placeholder("%minutes%", (intervals / 60))
                    ));
                });
            }
        }, finished -> {

            setCurrentPhase(GamePhase.FINISHED);

            Player winner = Bukkit.getPlayer(currentlyWithinBoundaries.peek());
            getActivePlayers().forEach(uuid -> {
                Player player = Bukkit.getPlayer(uuid);

                MessageUtil.sendMessage(player, "capturedzone", Arrays.asList(
                        new Placeholder("%winner%", winner.getName()),
                        new Placeholder("%faction%", (MPlayer.get(winner.getUniqueId()).getFactionName().length() < 1 ? "None" : MPlayer.get(winner.getUniqueId()).getFactionName()))
                ));

                plugin.getScoreboardManager().getScoreboard(player).remove();
                plugin.getScoreboardManager().removeScoreboard(player);
            });

            getCurrentArena().getLootCommands().forEach(command -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), PlaceholderManager.replaceCustomPlaceholder(command, "%name%", winner.getName())));
            captureTimeShrinkerRunnable.cancel();
            plugin.getGameManager().endGame(this);
        });
    }

    /**
     * Get the games id.
     * @return GameID
     */
    public String getGameId() {
        return gameId;
    }

    /**
     * Get the current phase of the game.
     * @return Current phase
     */
    public GamePhase getCurrentPhase() {
        return currentPhase;
    }

    /**
     * Add a player to the {@link KOTHGame}
     * @param player - player
     */
    public void addActivePlayer(Player player) {
        if (activePlayers.contains(player.getUniqueId())) return;
        activePlayers.add(player.getUniqueId());
    }

    /**
     * Remove a player from the KOTH game.
     * @param player - player
     */
    public void removeActivePlayer(Player player) {
        activePlayers.remove(player.getUniqueId());
    }

    /**
     * Get the players participating in the {@link KOTHGame}
     * @return Active players.
     */
    public List<UUID> getActivePlayers() {
        return activePlayers;
    }

    /**
     * Get the arena used in the {@link KOTHGame}
     * @return Arena
     */
    public Arena getCurrentArena() {
        return currentArena;
    }

    /**
     * Set the current phase of the game.
     * @param newPhease - next phase.
     */
    public void setCurrentPhase(GamePhase newPhease) {
        Bukkit.getPluginManager().callEvent(new PhaseChangeEvent(this, currentPhase, newPhease));
        currentPhase = newPhease;
    }

    /**
     * If a player moves into or out of a capture zone.
     * @param event - event
     */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {

        Player player = event.getPlayer();

        Location from = event.getFrom();
        Location to = event.getTo();

        handleZoneCheck(player, from, to);
    }

    /**
     * When a player teleports into or out of a capture zone.
     * @param event - event
     */
    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {

        Player player = event.getPlayer();

        Location from = event.getFrom();
        Location to = event.getTo();

        handleZoneCheck(player, from, to);
    }

    /**
     * Handles when a player enters the capture zone.
     * @param event - event
     */
    @EventHandler
    public void onEnterCapture(EnterCaptureZoneEvent event) {

        Player player = event.getPlayer();

        if (!currentlyWithinBoundaries.contains(player.getUniqueId())) {

            if (currentlyWithinBoundaries.peek() == null) {
                resetZoneCountdown();
            }

            currentlyWithinBoundaries.add(player.getUniqueId());
            MessageUtil.sendMessage(player, "enterzone");
        }
    }

    /**
     * Handles when a player exists the capture zone.
     * @param event - event
     */
    @EventHandler
    public void onExitCapture(ExitCaptureZoneEvent event) {
        Player player = event.getPlayer();

        MessageUtil.sendMessage(player, "exitzone");

        if (currentlyWithinBoundaries.size() > 1 && currentlyWithinBoundaries.peek() != null && currentlyWithinBoundaries.peek().equals(player.getUniqueId())) {
            currentlyWithinBoundaries.poll();
            getActivePlayers().forEach(uuid -> {
                Player active = Bukkit.getPlayer(uuid);
                MessageUtil.sendMessage(active, "capturechanged", Collections.singletonList(
                        new Placeholder("%capturing%", Bukkit.getPlayer(currentlyWithinBoundaries.peek()).getName())
                ));
            });

            resetZoneCountdown();
            return;
        }

        currentlyWithinBoundaries.remove(player.getUniqueId());

        if (currentlyWithinBoundaries.size() == 0) {
            plugin.getCountdownManager().getCountdown("capture_" + gameId).stop();
            plugin.getCountdownManager().getCountdown("capture_" + gameId).setCurrentTime(currentCaptureTime);

            getActivePlayers().forEach(uuid -> {

                int minutes = (currentCaptureTime % 3600) / 60;
                int seconds = currentCaptureTime % 60;

                plugin.getScoreboardManager().getScoreboard(Bukkit.getPlayer(uuid)).updateTeam("capturing", ChatColor.GREEN.toString(), "&7Capturing: ", "&aNone");
                plugin.getScoreboardManager().getScoreboard(Bukkit.getPlayer(uuid)).updateTeam("capturingfac", ChatColor.DARK_PURPLE.toString(), "&7Faction: ", "&aNone");
                plugin.getScoreboardManager().getScoreboard(Bukkit.getPlayer(uuid)).updateTeam("remainingtime", ChatColor.RED.toString(), "&7Capturing: ", "&c" + minutes + ":" + String.format("%02d", seconds));
            });
        }
    }

    /**
     * Handles when a player establishes a secure connection to the server.
     * @param event - event
     */
    @EventHandler
    public void onConnect(PlayerJoinEvent event) {

        Player player = event.getPlayer();

        if (plugin.getScoreboardManager().getScoreboard(player) != null) {
            plugin.getScoreboardManager().getScoreboard(player).remove();
            plugin.getScoreboardManager().removeScoreboard(player);
        }

        // Stops checking for players not in this game..
        if (!getActivePlayers().contains(player.getUniqueId())) return;

        if (getCurrentPhase() != GamePhase.IN_PROGRESS) return;

        if (LocationUtil.isWithinLocation(player.getLocation(), getCurrentArena().getArenaBoundaryPoint1(), getCurrentArena().getArenaBoundaryPoint2())) {
            Bukkit.getPluginManager().callEvent(new EnterCaptureZoneEvent(player, this));
        }
    }

    /**
     * Handles the disconnection of players to remove scoreboards,
     * Handle leaving the zone etc.
     * @param event - event
     */
    @EventHandler
    public void onDisconnect(PlayerQuitEvent event) {

        Player player = event.getPlayer();

        if (plugin.getScoreboardManager().getScoreboard(player) != null) {
            plugin.getScoreboardManager().getScoreboard(player).remove();
            plugin.getScoreboardManager().removeScoreboard(player);
        }

        // Stops checking for players not in this game..
        if (!getActivePlayers().contains(player.getUniqueId())) return;

        removeActivePlayer(player);

        if (getCurrentPhase() != GamePhase.IN_PROGRESS) return;

        if (LocationUtil.isWithinLocation(player.getLocation(), getCurrentArena().getArenaBoundaryPoint1(), getCurrentArena().getArenaBoundaryPoint2())) {
            Bukkit.getPluginManager().callEvent(new ExitCaptureZoneEvent(player, this));
        }

        if (!getActivePlayers().contains(player.getUniqueId())) {

            plugin.getScoreboardManager().getScoreboard(player).remove();
            plugin.getScoreboardManager().removeScoreboard(player);

            player.teleport(Bukkit.getWorld("world").getSpawnLocation());
        }

        handleEndCheck();
    }

    /**
     * Handle the respawn of a player during a {@link KOTHGame}
     * @param event - event
     */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Stops checking for players not in this game..
        if (!getActivePlayers().contains(player.getUniqueId())) return;

        if (getCurrentPhase() != GamePhase.IN_PROGRESS) return;

        event.setRespawnLocation(getCurrentArena().getSpawnLocation());
    }

    /**
     * Handle the damage of a player during a {@link KOTHGame}
     * @param event - event
     */
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {

        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();

            // Stops checking for players not in this game..
            if (!getActivePlayers().contains(player.getUniqueId())) return;

            if (getCurrentPhase() != GamePhase.IN_PROGRESS) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Handles the checks for if a player enters or leaves the capture zone.
     * Made into a method due to the same code being used more than once.
     * @param player - player checking.
     * @param from - previous location.
     * @param to - current location.
     */
    private void handleZoneCheck(Player player, Location from, Location to) {
        // Stops checking for players not in this game..
        if (!getActivePlayers().contains(player.getUniqueId())) return;

        // Stops on the block checking..
        if (from == to) return;

        if (getCurrentPhase() != GamePhase.IN_PROGRESS) return;

        if (!LocationUtil.isWithinLocation(to, getCurrentArena().getArenaBoundaryPoint1(), getCurrentArena().getArenaBoundaryPoint2()) && LocationUtil.isWithinLocation(from, getCurrentArena().getArenaBoundaryPoint1(), getCurrentArena().getArenaBoundaryPoint2())) {
            Bukkit.getPluginManager().callEvent(new ExitCaptureZoneEvent(player, this));
        } else if (!LocationUtil.isWithinLocation(from, getCurrentArena().getArenaBoundaryPoint1(), getCurrentArena().getArenaBoundaryPoint2()) && LocationUtil.isWithinLocation(to, getCurrentArena().getArenaBoundaryPoint1(), getCurrentArena().getArenaBoundaryPoint2())) {
            Bukkit.getPluginManager().callEvent(new EnterCaptureZoneEvent(player, this));
        }
    }

    /**
     * Handles the check for if a player leaves the game.
     * Made into a method due to code being used more than once.
     * Checks if the active players <= 1 due to no players playing.
     */
    public void handleEndCheck() {

        if (getActivePlayers().size() <= 1) {

            setCurrentPhase(GamePhase.FINISHED);

            Bukkit.getOnlinePlayers().forEach(activePlayer -> {
                if (getActivePlayers().contains(activePlayer.getUniqueId())) {
                    if (plugin.getScoreboardManager().getScoreboard(activePlayer) != null) {
                        plugin.getScoreboardManager().getScoreboard(activePlayer).remove();
                        plugin.getScoreboardManager().removeScoreboard(activePlayer);
                    }

                    activePlayer.teleport(Bukkit.getWorld("world").getSpawnLocation());
                }
                MessageUtil.sendMessageToPlayer(activePlayer, "There were not enough players to continue the game of KOTH.");
            });

            captureTimeShrinkerRunnable.cancel();
            plugin.getGameManager().endGame(this);
        }
    }
}
