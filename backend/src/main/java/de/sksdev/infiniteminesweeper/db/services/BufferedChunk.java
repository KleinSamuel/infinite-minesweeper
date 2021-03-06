package de.sksdev.infiniteminesweeper.db.services;

import de.sksdev.infiniteminesweeper.db.entities.Chunk;

public class BufferedChunk implements Comparable<BufferedChunk> {

    private Chunk chunk;

    private long timestamp;

    public BufferedChunk(Chunk c) {
        this.chunk = c;
        update();
    }

    public void update() {
        timestamp = System.currentTimeMillis();
    }

    public Chunk getChunk() {
        return chunk;
    }

    public void setChunk(Chunk chunk) {
        this.chunk = chunk;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public int compareTo(BufferedChunk other) {
        long complete_dist = this.getTimestamp() - other.getTimestamp();
        if (complete_dist != 0)
            return Long.signum(complete_dist)/* * Integer.MAX_VALUE*/;
        else
            return Integer.signum(this.getChunk().getId().compareTo(other.getChunk().getId()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BufferedChunk that = (BufferedChunk) o;
        return chunk.equals(that.chunk);
    }

    @Override
    public int hashCode() {
        return chunk.hashCode();
    }
}
