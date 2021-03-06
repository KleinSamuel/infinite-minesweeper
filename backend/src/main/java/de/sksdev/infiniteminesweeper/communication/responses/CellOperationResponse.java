package de.sksdev.infiniteminesweeper.communication.responses;

import de.sksdev.infiniteminesweeper.communication.requests.CellOperationRequest;

public class CellOperationResponse {

    private int chunkX;
    private int chunkY;
    private int cellX;
    private int cellY;
    private boolean hidden;
    private long user;
    private int value;
    private long factor;

    public CellOperationResponse() {
    }

    public CellOperationResponse(CellOperationRequest message, boolean hidden, int value, long factor) {
        this(message.getChunkX(), message.getChunkY(), message.getCellX(), message.getCellY(), message.getUser(), hidden, value, factor);
    }

    public CellOperationResponse(int chunkX, int chunkY, int cellX, int cellY, long user, boolean hidden, int value, long factor) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.cellX = cellX;
        this.cellY = cellY;
        this.user = user;
        this.hidden = hidden;
        this.value = value;
        this.factor = factor;
    }

    public int getChunkX() {
        return chunkX;
    }

    public void setChunkX(int chunkX) {
        this.chunkX = chunkX;
    }

    public int getChunkY() {
        return chunkY;
    }

    public void setChunkY(int chunkY) {
        this.chunkY = chunkY;
    }

    public int getCellX() {
        return cellX;
    }

    public void setCellX(int cellX) {
        this.cellX = cellX;
    }

    public int getCellY() {
        return cellY;
    }

    public void setCellY(int cellY) {
        this.cellY = cellY;
    }

    public long getUser() {
        return user;
    }

    public void setUser(long user) {
        this.user = user;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public long getFactor() {
        return factor;
    }

    public void setFactor(long factor) {
        this.factor = factor;
    }
}
