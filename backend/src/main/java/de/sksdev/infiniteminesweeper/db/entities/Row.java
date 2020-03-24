package de.sksdev.infiniteminesweeper.db.entities;

import javax.persistence.*;
import java.util.Set;
import java.util.TreeSet;


@Entity
@Table(name = "rows")
@IdClass(RowId.class)
public class Row implements Comparable<Row> {


    public Row(){
    }

    public Row(Chunk chunk, int y_tile) {
        this.chunk = chunk;
        this.x = chunk.getX();
        this.y = chunk.getY();
        this.y_tile = y_tile;
    }


    @Id
    private long x;

    @Id
    private long y;

    @Id
    private int y_tile;

    @OneToOne
    @JoinColumns({
            @JoinColumn(name = "x", insertable = false, updatable = false),
            @JoinColumn(name = "y", insertable = false, updatable = false)
    })
    @MapsId
    private Chunk chunk;

    @OneToMany(mappedBy = "row", cascade = CascadeType.ALL)
    private Set<Tile> tiles = new TreeSet<>();

    @Override
    public int compareTo(Row other) {
        return this.y_tile - other.getY_tile();
    }

    public long getX() {
        return x;
    }

    public void setX(long x) {
        this.x = x;
    }

    public long getY() {
        return y;
    }

    public void setY(long y) {
        this.y = y;
    }

    public int getY_tile() {
        return y_tile;
    }

    public void setY_tile(int y_tile) {
        this.y_tile = y_tile;
    }

    public TreeSet<Tile> getTiles() {
        return (TreeSet<Tile>) tiles;
    }

    public void setTiles(Set<Tile> tiles) {
        this.tiles = tiles;
    }


    public void addTile(Tile t) {
        tiles.add(t);
    }

}
