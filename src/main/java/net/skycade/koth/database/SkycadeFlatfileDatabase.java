package net.skycade.koth.database;

import net.skycade.koth.SkycadeKoth;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

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
public class SkycadeFlatfileDatabase {

    /** Config file instance. */
    private File file;
    /** {@link FileConfiguration} instance. */
    private FileConfiguration fileConfiguration;

    /**
     * Create a new instance of a flatfile database.
     * @param plugin - plugin instance.
     * @param fileName - File name.
     */
    public SkycadeFlatfileDatabase(SkycadeKoth plugin, String fileName) {
        plugin.saveResource(fileName, false);
        this.file = new File(plugin.getDataFolder(), fileName);
        this.fileConfiguration = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Save the config.
     */
    public void saveConfig() {

        try {
            fileConfiguration.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get the file instance.
     * @return File
     */
    public File getFile() {
        return file;
    }

    /**
     * Get the configuration instance.
     * @return
     */
    public FileConfiguration getFileConfiguration() {
        return fileConfiguration;
    }
}
