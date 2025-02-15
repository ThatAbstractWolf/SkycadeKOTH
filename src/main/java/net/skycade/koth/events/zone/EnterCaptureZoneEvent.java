package net.skycade.koth.events.zone;

import net.skycade.koth.events.SkycadeEvent;
import net.skycade.koth.game.KOTHGame;
import org.bukkit.entity.Player;

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
public class EnterCaptureZoneEvent extends SkycadeEvent {

    /** Player entering zone. */
    private Player player;
    /** Game the zone is in. */
    private KOTHGame game;

    /**
     * Create a new instance of the {@link EnterCaptureZoneEvent}
     * @param player - player.
     * @param game - game.
     */
    public EnterCaptureZoneEvent(Player player, KOTHGame game) {
        this.player = player;
        this.game = game;
    }

    /**
     * @return Get the player entering the zone.
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * @return Get the game the zone is in.
     */
    public KOTHGame getGame() {
        return game;
    }
}
