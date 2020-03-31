package de.sksdev.infiniteminesweeper.communication;

import de.sksdev.infiniteminesweeper.db.entities.Ids.TileId;
import de.sksdev.infiniteminesweeper.db.entities.User;
import de.sksdev.infiniteminesweeper.db.entities.UserSettings;
import de.sksdev.infiniteminesweeper.db.entities.UserStats;

import java.util.UUID;

public class LoginResponse {

    private String hash;
    private long id;
    private TileId hometile;
    private UserSettings userSettings;
    private UserStats userStats;

    public LoginResponse(User u, UserSettings userSettings, UserStats userStats) {
        this.id = u.getId();
        hash = UUID.randomUUID().toString();
        setHometile(u.getHome());
        this.userSettings = userSettings;
        this.userStats = userStats;
    }

    public String getHash() {
        return hash;
    }

//    public void setHash(String hash) {
//        this.hash = hash;
//    }

    public long getId() {
        return id;
    }

//    public void setId(long id) {
//        this.id = id;
//    }

    public void setHometile(TileId id) {
        hometile = id;
    }

    public TileId getHometile() {
        return hometile;
    }

    public UserSettings getUserSettings() {
        return userSettings;
    }

    public void setUserSettings(UserSettings userSettings) {
        this.userSettings = userSettings;
    }

    public UserStats getUserStats() {
        return userStats;
    }

    public void setUserStats(UserStats userStats) {
        this.userStats = userStats;
    }
}
