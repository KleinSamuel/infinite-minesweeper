package de.sksdev.infiniteminesweeper.db.services;


import de.sksdev.infiniteminesweeper.Config;
import de.sksdev.infiniteminesweeper.MineFieldGenerator;
import de.sksdev.infiniteminesweeper.communication.responses.CellOperationResponse;
import de.sksdev.infiniteminesweeper.db.entities.Chunk;
import de.sksdev.infiniteminesweeper.db.entities.Ids.ChunkId;
import de.sksdev.infiniteminesweeper.db.entities.Ids.TileId;
import de.sksdev.infiniteminesweeper.db.entities.Tile;
import de.sksdev.infiniteminesweeper.db.entities.User;
import de.sksdev.infiniteminesweeper.db.entities.UserStats;
import de.sksdev.infiniteminesweeper.db.repositories.TileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ChunkService {

    private final TileRepository tileRepository;
    private final ChunkBuffer chunkBuffer;
    private final UserService userService;
    private final AsyncService asyncService;
    private final SimpMessagingTemplate template;
    private Random random;

    @Autowired
    public ChunkService(TileRepository tileRepository,
                        ChunkBuffer chunkBuffer,
                        UserService userService,
                        AsyncService asyncService, SimpMessagingTemplate template) {
        this.tileRepository = tileRepository;
        this.chunkBuffer = chunkBuffer;
        this.userService = userService;
        this.asyncService = asyncService;
        this.template = template;
        this.random = new Random(System.currentTimeMillis());
    }

    public synchronized Chunk getOrCreateChunk(ChunkId id) {
        return chunkBuffer.findById(id).orElseGet(() -> save(newChunk(id)));
    }

    public Chunk getOrCreateChunkContent(int x, int y, boolean persist) {
        Chunk c = getOrCreateChunk(new ChunkId(x, y));
        return c.isFilled() ? c : generateContent(c);
    }

    public Tile getTile(int x, int y, int x_tile, int y_tile) {
        return getTile(new TileId(x, y, x_tile, y_tile));
    }

    public Tile getTile(TileId tid) {
        return getOrCreateChunk(new ChunkId(tid.getX(), tid.getY())).getGrid()[tid.getY_tile()][tid.getX_tile()];
    }

    private Chunk newChunk(int x, int y) {
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

    private Chunk saveNow(Chunk c) {
        return chunkBuffer.saveNow(c);
    }


    public Chunk save(Chunk c) {
        chunkBuffer.save(c);
        return c;
    }

    public boolean flushBuffer() {
        return chunkBuffer.flush();
    }


    public boolean registerChunkRequest(ChunkId cid, Long userId) {
        return userService.validateChunkRequest(userId, cid) && userService.registerChunkRequest(cid, userId);
    }

    public Tile registerTileUpdate(TileId tid, Long userId, boolean flag) {
        ChunkId cid = tid.getChunkId();
        if (userService.validateTileRequest(userId, cid)) {
            Tile t = getOrCreateChunk(cid).getGrid()[tid.getY_tile()][tid.getX_tile()];
            if (!flag && t.open(userService.getUser(userId))) {
                return t;
            } else if (t.setUser(userService.getUser(userId)))
                return t;
        }
        return null;
    }

    public Chunk getOrCreateChunkContent(ChunkId cid) {
        Chunk c = getOrCreateChunk(cid);
        return c.isFilled() ? c : generateContent(c);
    }

    public TileId getRandomTileId() {
        return new TileId(random.nextInt(), random.nextInt(), random.nextInt(Config.CHUNK_SIZE), random.nextInt(Config.CHUNK_SIZE));
    }


    public Object openTiles(ChunkId cid, int x_tile, int y_tile, long userId) {

        User u = userService.getUser(userId);
        UserStats stats = userService.loadStatsForUser(userId);

        Chunk chunk = getOrCreateChunkContent(cid);
        Tile t = chunk.getGrid()[y_tile][x_tile];
        HashMap<ChunkId, TreeSet<Tile>> openedTiles = new HashMap<>();


        if (t.isHidden()) {
            if (t.getValue() == 9) {
                // click on bomb
                t.setUser(u);
                t.setHidden(false);
                t.setFactor(Config.getMultiplicator(stats.getStreak()));
                stats.increaseCellsOpened();
                stats.increaseBombsExploded();
                stats.resetStreak();
                return t;
            }
            recOpenTiles(t, openedTiles);
        } else {
            openAdjacentTiles(chunk, t, openedTiles);
        }

        openedTiles.forEach((c, ts) -> {
            userService.registerChunkRequest(c, userId);
            ts.forEach(open -> {
                open.setFactor(Config.getMultiplicator(stats.getStreak()));
                template.convertAndSend("/updates/" + c.getX() + "_" + c.getY(), new CellOperationResponse(c.getX(), c.getY(), open.getX_tile(), open.getY_tile(), u.getId(), false, open.getValue(), open.getFactor()));
                open.setHidden(false);
                open.setUser(u);
                if (open.getValue() != 9) {
                    long s = Config.score(stats.getStreak(), open.getValue());
                    stats.increaseCurrentScore(s);
                    stats.increaseStreak();
                    stats.increaseCellsOpened();
                }
            });
        });

        template.convertAndSend("/stats/id" + userId, stats);

        return t;
    }

    private void openAdjacentTiles(Chunk chunk, Tile t, HashMap<ChunkId, TreeSet<Tile>> openedTiles) {
        for (int yo = -1; yo < 2; yo++) {
            Chunk c = chunk;
            int y = t.getY_tile() + yo;
            Tile[] row;
            try {
                row = c.getGrid()[y];
            } catch (IndexOutOfBoundsException ey) {

                if (y < 0) {
                    c = getOrCreateChunkContent(new ChunkId(t.getX(), t.getY() - 1));
                    row = c.getGrid()[Config.CHUNK_SIZE - 1];
                    y = Config.CHUNK_SIZE - 1;
                } else {
                    c = getOrCreateChunkContent(new ChunkId(t.getX(), t.getY() + 1));
                    row = c.getGrid()[0];
                    y = 0;
                }
            }
            for (int xo = -1; xo < 2; xo++) {
                if (yo == 0 && xo == 0)
                    continue;
                int x = t.getX_tile() + xo;
                Tile adj;
                try {
                    adj = row[x];
                } catch (IndexOutOfBoundsException e) {
                    if (x < 0) {
                        adj = getOrCreateChunkContent(new ChunkId(c.getX() - 1, c.getY())).getGrid()[y][Config.CHUNK_SIZE - 1];
                    } else {
                        adj = getOrCreateChunkContent(new ChunkId(c.getX() + 1, c.getY())).getGrid()[y][0];
                    }
                }
                if (adj.isHidden() & adj.getValue() != 9) {
                    if (adj.getValue() == 0)
                        recOpenTiles(adj, openedTiles);
                    else
                        try {
                            openedTiles.get(adj.getChunk().getId()).add(adj);
                        } catch (NullPointerException e) {
                            openedTiles.put(adj.getChunk().getId(), new TreeSet<>());
                            openedTiles.get(adj.getChunk().getId()).add(adj);
                        }

                }
            }
        }
    }

    public void recOpenTiles(Chunk c, int x_tile, int y_tile, HashMap<ChunkId, TreeSet<Tile>> opened) {
        recOpenTiles(c.getGrid()[y_tile][x_tile], opened);
    }

    public void recOpenTiles(Tile t, HashMap<ChunkId, TreeSet<Tile>> opened) {
        boolean alreadyOpened = false;
        ChunkId cid = t.getChunk().getId();
        try {
            alreadyOpened = !opened.get(cid).add(t);
        } catch (NullPointerException e) {
            opened.put(cid, new TreeSet<>(Collections.singletonList(t)));
        }

        if (!alreadyOpened) {
            if (t.getValue() < 1) {
                Chunk c;
                for (int yo = -1; yo < 2; yo++) {
                    c = t.getChunk();
                    int y = t.getY_tile() + yo;
                    Tile[] row;
                    try {
                        row = c.getGrid()[y];
                    } catch (IndexOutOfBoundsException ey) {

                        if (y < 0) {
                            c = getOrCreateChunkContent(new ChunkId(t.getX(), t.getY() - 1));
                            row = c.getGrid()[Config.CHUNK_SIZE - 1];
                            y = Config.CHUNK_SIZE - 1;
                        } else {
                            c = getOrCreateChunkContent(new ChunkId(t.getX(), t.getY() + 1));
                            row = c.getGrid()[0];
                            y = 0;
                        }
                    }
                    for (int xo = -1; xo < 2; xo++) {
                        if (yo == 0 && xo == 0)
                            continue;

                        int x = t.getX_tile() + xo;
                        try {
                            recOpenTiles(row[x], opened);
                        } catch (IndexOutOfBoundsException e) {
                            if (x < 0) {
                                recOpenTiles(getOrCreateChunkContent(new ChunkId(c.getX() - 1, c.getY())), Config.CHUNK_SIZE - 1, y, opened);
                            } else {
                                recOpenTiles(getOrCreateChunkContent(new ChunkId(c.getX() + 1, c.getY())), 0, y, opened);
                            }
                        }
                    }
                }
            }
        }
    }

}
