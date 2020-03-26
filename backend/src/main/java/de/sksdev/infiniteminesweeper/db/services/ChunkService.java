package de.sksdev.infiniteminesweeper.db.services;


import de.sksdev.infiniteminesweeper.Config;
import de.sksdev.infiniteminesweeper.MineFieldGenerator;
import de.sksdev.infiniteminesweeper.db.entities.Chunk;
import de.sksdev.infiniteminesweeper.db.entities.Ids.ChunkId;
import de.sksdev.infiniteminesweeper.db.entities.Ids.TileId;
import de.sksdev.infiniteminesweeper.db.entities.Tile;
import de.sksdev.infiniteminesweeper.db.repositories.TileRepository;
import org.hibernate.annotations.Synchronize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;

@Service
public class ChunkService {


    final
    TileRepository tileRepository;

    final
    ChunkBuffer chunkBuffer;

    private HashSet<ChunkId> inGeneration;

    @Autowired
    public ChunkService(TileRepository tileRepository, ChunkBuffer chunkBuffer) {
        this.tileRepository = tileRepository;
        this.chunkBuffer = chunkBuffer;
        this.inGeneration = new HashSet<>();
    }

    //    TODO use objectgetter for cached entries?
    public synchronized Chunk getOrCreateChunk(ChunkId id) {
        return chunkBuffer.findById(id).orElseGet(() -> {
            Chunk c = save(newChunk(id));
            return c;
        });
    }


    public Chunk getOrCreateChunkContent(long x, long y, boolean persist) {
        Chunk c = getOrCreateChunk(new ChunkId(x, y));
        return c.isFilled() ? c : generateContent(c);
    }

    public Tile getTile(long x, long y, int x_tile, int y_tile) {
        return tileRepository.findById(new TileId(x, y, x_tile, y_tile)).get();
    }

    private Chunk newChunk(long x, long y) {
        return new Chunk(x, y);
    }

    private Chunk newChunk(ChunkId id) {
        return new Chunk(id);
    }


    private Chunk generateContent(Chunk c) {
        Chunk[][] neighborhood = getNeighborhood(c);
        generateField(neighborhood);
        c.setFilled(true);
        return c;
    }

    private Chunk[][] getNeighborhood(Chunk c) {
        Chunk[][] neighborhood = new Chunk[3][3];
        neighborhood[1][1] = c;
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                if (!(x == 1 & y == 1))
                    neighborhood[y][x] = getOrCreateChunk(new ChunkId(x + c.getX() - 1, y + c.getY() - 1));
            }
        }
        return neighborhood;
    }

    private void generateField(Chunk[][] n) {
        Tile[][] field = new Tile[Config.CHUNK_SIZE + 2][Config.CHUNK_SIZE + 2];

        n[1][1].getTiles().forEach(t -> field[t.getY_tile() + 1][t.getX_tile() + 1] = t);

        field[0][0] = n[0][0].getT_lower_right();
        field[0][Config.CHUNK_SIZE + 1] = n[0][2].getT_lower_left();
        field[Config.CHUNK_SIZE + 1][0] = n[2][0].getT_upper_right();
        field[Config.CHUNK_SIZE + 1][Config.CHUNK_SIZE + 1] = n[2][2].getT_upper_left();

        n[0][1].getR_last().forEach(t -> field[0][t.getX_tile() + 1] = t);
        n[1][0].getC_last().forEach(t -> field[t.getY_tile() + 1][0] = t);
        n[2][1].getR_first().forEach(t -> field[Config.CHUNK_SIZE + 1][t.getX_tile() + 1] = t);
        n[1][2].getC_first().forEach(t -> field[t.getY_tile() + 1][Config.CHUNK_SIZE + 1] = t);

        MineFieldGenerator.determineValues(field);
        saveAllNeighbors(n);
    }

    public void saveAllNeighbors(Chunk[][] n) {
        LinkedList<Chunk> cs = new LinkedList<>();
        for (Chunk[] chunks : n) {
            cs.addAll(Arrays.asList(chunks));
        }
        chunkBuffer.saveAll(cs);
    }


    public Chunk save(Chunk c) {
        chunkBuffer.save(c);
        return c;
    }

    public boolean flushBuffer() {
        return chunkBuffer.flush();
    }


}
