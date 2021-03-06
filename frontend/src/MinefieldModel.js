import * as CONFIG from "./Config";
import * as PIXI from "pixi.js";
import {sounds, play} from "./Sounds";
import CellChunk from "./CellChunk";

/**
 * Represents the global minefield which consists of quadratic cell chunks
 * each of which has a dedicated x and y coordinate in this global field.
 *
 * @author Samuel Klein
 */
export default class MinefieldModel extends PIXI.Container {

    /**
     * Sets the coordinates of the player in the global field and
     * gets an instance of the Communicator object to be able to
     * talk to the server and send or receive field updates.
     * @param viewer
     * @param communicator
     * @param chunkX
     * @param chunkY
     */
    constructor(viewer, communicator, chunkX, chunkY) {
        super();
        this.viewer = viewer;
        this.com = communicator;
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.field = {};
    }

    init() {
        console.log("[ INFO ] MinefieldModel initialized");

        let context = this;
        return new Promise(function (resolve, reject) {
            let promiseStack = [];
            for (let i = context.chunkX - CONFIG.BUFFER_ADD; i <= context.chunkX + CONFIG.BUFFER_ADD; i++) {
                for (let j = context.chunkY - CONFIG.BUFFER_ADD; j <= context.chunkY + CONFIG.BUFFER_ADD; j++) {
                    promiseStack.push(context.retrieveChunkFromServer(i, j));
                }
            }
            Promise.all(promiseStack).then(resolve);
        });
    }

    /**
     * Retrieves a cell chunk of given coordinates from the server.
     * Stores alls the cells of this chunk in the local model and adds the cells
     * to the scene so they are visible to the player.
     *
     * @param chunkX x coordinate of the chunk
     * @param chunkY y coordinate of the chunk
     * @returns {Promise}
     */
    retrieveChunkFromServer(chunkX, chunkY) {
        let context = this;

        let clickWrapper = (function (isLeftclick, chunkX, chunkY, cellX, cellY) {
            // disables mouse clicks when menu is open
            if (this.viewer.denyInteractions()) {
                return;
            }
            if (isLeftclick) {
                this.clickCell(chunkX, chunkY, cellX, cellY);
            } else {
                this.flagCell(chunkX, chunkY, cellX, cellY);
            }
        }).bind(this);

        let hoverWrapper = (function (chunkX, chunkY, cellX, cellY) {
            // disables mouse interactions when menu is open
            if (this.viewer.denyInteractions()) {
                return;
            }
            this.hoverCell(chunkX, chunkY, cellX, cellY);
        }).bind(this);

        let updateCellWrapper = (function (chunkX, chunkY, cellX, cellY, hidden, user, value, factor) {
            this.updateCell(chunkX, chunkY, cellX, cellY, hidden, user, value, factor);
        }).bind(this);

        return this.com.requestChunk(chunkX, chunkY).then(function (response) {

            if (response.data.length === 0) {
                context.viewer.logout();
            }

            return new Promise(function (resolve, reject) {
                let chunk = response.data.tiles;

                let c = new CellChunk(chunkX, chunkY);
                c.position.set(chunkX * CONFIG.CHUNK_PIXEL_SIZE, chunkY * CONFIG.CHUNK_PIXEL_SIZE);

                c.initFieldMaps(chunk);

                for (let i = 0; i < c.innerField.length; i++) {
                    for (let j = 0; j < c.innerField[i].length; j++) {
                        let cell = c.innerField[i][j];

                        cell.sprite.on("mousedown", function (event) {
                            this.m_posX = event.data.global.x;
                            this.m_posY = event.data.global.y;
                        });
                        cell.sprite.on("mouseup", function (event) {
                            if (Math.abs(this.m_posX - event.data.global.x) < CONFIG.CELL_PIXEL_SIZE &&
                                Math.abs(this.m_posY - event.data.global.y) < CONFIG.CELL_PIXEL_SIZE) {
                                clickWrapper(true, cell.chunkX, cell.chunkY, cell.cellX, cell.cellY);
                            }
                        });
                        cell.sprite.on("rightdown", function (event) {
                            this.m_posX = event.data.global.x;
                            this.m_posY = event.data.global.y;
                        });
                        cell.sprite.on("rightup", function (event) {
                            if (Math.abs(this.m_posX - event.data.global.x) < CONFIG.CELL_PIXEL_SIZE &&
                                Math.abs(this.m_posY - event.data.global.y) < CONFIG.CELL_PIXEL_SIZE) {
                                clickWrapper(false, cell.chunkX, cell.chunkY, cell.cellX, cell.cellY);
                            }
                        });
                        cell.sprite.on("mouseover", function (event) {
                            hoverWrapper(cell.chunkX, cell.chunkY, cell.cellX, cell.cellY);
                        });
                    }
                }

                context.addChunk(chunkX, chunkY, c);
                context.addChild(c);

                context.com.registerChunk(chunkX, chunkY, updateCellWrapper);
                resolve();
            });
        });
    }

    /**
     * Checks if a cell chunk of the given coordinates exists in the local buffer.
     *
     * @param chunkX x coordinate of the chunk
     * @param chunkY y coordinate of the chunk
     * @returns {boolean} true if the chunk already exists
     */
    containsChunk(chunkX, chunkY) {
        if (chunkX in this.field) {
            if (chunkY in this.field[chunkX]) {
                return true;
            }
        }
        return false;
    }

    moveX(direction) {
        let context = this;
        return new Promise(function (resolve, reject) {
            let promiseStack = [];

            // calculates new x coordinate
            let genX = context.chunkX + (CONFIG.BUFFER_ADD * direction);

            // calculates all buffered y coordinates for the new chunks
            for (let i = -1 * CONFIG.BUFFER_ADD; i <= CONFIG.BUFFER_ADD; i++) {
                let genY = context.chunkY + i;
                // checks if the chunk is already in buffer
                if (!context.containsChunk(genX, genY)) {
                    // gets and adds the new junk to the buffer
                    promiseStack.push(context.retrieveChunkFromServer(genX, genY));
                }
            }
            Promise.all(promiseStack).then(resolve);
        }).then(function () {
            // computes the chunks that are out of buffer and can be removed
            let toRemove = [];
            for (let i in context.field) {
                for (let j in context.field[i]) {
                    if (Math.abs(context.chunkX - i) > CONFIG.BUFFER_REMOVE) {
                        toRemove.push([i, j]);
                    }
                }
            }
            // removes the identified chunks
            for (let i in toRemove) {
                context.removeChunk(toRemove[i][0], toRemove[i][1]);
                context.com.unregisterChunk(toRemove[i][0], toRemove[i][1]);
            }
        });
    }

    moveY(direction) {
        let context = this;
        return new Promise(function (resolve, reject) {
            let promiseStack = [];

            // calculates new y coordinate
            let genY = context.chunkY + (CONFIG.BUFFER_ADD * direction);

            // calculates all buffered x coordinates for the new chunks
            for (let i = -1 * CONFIG.BUFFER_ADD; i <= CONFIG.BUFFER_ADD; i++) {
                let genX = context.chunkX + i;
                // checks if the chunk is already in buffer
                if (!context.containsChunk(genX, genY)) {
                    // gets and adds the new junk to the buffer
                    promiseStack.push(context.retrieveChunkFromServer(genX, genY));
                }
            }
            Promise.all(promiseStack).then(resolve);
        }).then(function () {
            // computes the chunks that are out of buffer and can be removed
            let toRemove = [];
            for (let i in context.field) {
                for (let j in context.field[i]) {
                    if (Math.abs(context.chunkY - j) > CONFIG.BUFFER_REMOVE) {
                        toRemove.push([i, j]);
                    }
                }
            }
            // removes the identified chunks
            for (let i in toRemove) {
                context.removeChunk(toRemove[i][0], toRemove[i][1]);
                context.com.unregisterChunk(toRemove[i][0], toRemove[i][1]);
            }
        });
    }

    //execute incoming cell updates
    updateCell(chunkX, chunkY, cellX, cellY, hidden, user, value, factor) {
        let cell = this.getChunk(chunkX, chunkY).getCell(cellX, cellY);
        // if (cell.state.hidden !== hidden) {
        cell.setState({hidden: hidden, user: user, value: value});
        if (user === CONFIG.getID())
            cell.showScore(factor, true);
        // }

    }

    addChunk(cellX, cellY, chunk) {
        if (!this.field["" + cellX]) {
            this.field["" + cellX] = {};
        }
        this.field["" + cellX]["" + cellY] = chunk;
    }

    getChunk(cellX, cellY) {
        if (this.field["" + cellX] && this.field["" + cellX]["" + cellY]) {
            return this.field["" + cellX]["" + cellY];
        }
        return undefined;
    }

    removeChunk(cellX, cellY) {
        // removes the y coordinate dict
        if ("" + cellX in this.field && "" + cellY in this.field["" + cellX]) {
            delete this.field["" + cellX]["" + cellY];
        }
        // checks if the x coordinate dict is empty and removes it if so
        if (Object.keys(this.field["" + cellX]).length === 0) {
            delete this.field["" + cellX];
        }
    }

    hoverCell(chunkX, chunkY, cellX, cellY) {
        let posX = ~~(chunkX) * CONFIG.CHUNK_SIZE + ~~(cellX);
        let posY = ~~(chunkY) * CONFIG.CHUNK_SIZE + ~~(cellY);
        this.viewer.ui.position_x.text = "X: " + posX;
        this.viewer.ui.position_y.text = "Y: " + posY;
        this.viewer.cursor.x = this.x + chunkX * CONFIG.CHUNK_PIXEL_SIZE + cellX * CONFIG.CELL_PIXEL_SIZE;
        this.viewer.cursor.y = this.y + chunkY * CONFIG.CHUNK_PIXEL_SIZE + cellY * CONFIG.CELL_PIXEL_SIZE;
    }

    loadCells(chunkX, chunkY, cellX, cellY, flag) {
        return this.com.requestCell(chunkX, chunkY, cellX, cellY, flag).then(function (response) {
            return response;
        });
    }

    clickCell(chunkX, chunkY, cellX, cellY) {

        let cell = this.getChunk(chunkX, chunkY).getCell(cellX, cellY);
        // do nothing if the cell is flagged
        if (cell.state.hidden && cell.state.user) {
            play("click_no");
            return;
        }
        // do nothing if the cell is already opened and a mine or an empty cell
        if (!cell.state.hidden && (cell.state.value === 0 || cell.state.value === 9)) {
            play("click_no");
            return;
        }

        //check for auto-opening potential
        let autoOpenValid = false;
        if (!cell.state.hidden)
            autoOpenValid = this.allBombsKnown(cell);


        if (cell.state.hidden || autoOpenValid) {
            //send request for update broadcast
            this.loadCells(chunkX, chunkY, cellX, cellY, false).then(function (response) {
                let fullCell = response.data;
                //update requested cell
                cell.setState({hidden: fullCell.hidden, user: fullCell.user, value: fullCell.value});
                if (!autoOpenValid)
                    cell.showScore(fullCell.factor, cell.state.value !== 9);
                // return {hidden:washidden,factor:fullCell.factor}
            }).then(function (data) {
                //requested cell == bomb -> explode
                if (cell.state.value === 9) {
                    play("explosion");
                    return;
                }

                //requested cell != bomb
                play("click_cell");
            });
            return;
        }

        //requested_cell is not hidden but also not auto-open valid
        play("click_no");

    }

    //evaluates for a cell if all adjacent bombs are known and if there are any cells left to open
    allBombsKnown(cell) {
        let count = 0;
        let hidden = 0;

        let cells = this.getAdjacentCells(cell.chunkX, cell.chunkY, cell.cellX, cell.cellY);
        for (let adjCell of cells) {
            if (adjCell.state.hidden) {
                if (adjCell.state.user !== null)
                    count++;
                else
                    hidden++;
            } else {
                if (adjCell.state.value === 9 && !adjCell.state.hidden)
                    count++;
            }
        }
        return (count === cell.state.value && hidden > 0)
    }

    flagCell(chunkX, chunkY, cellX, cellY) {

        let context = this;
        let cell = this.getChunk(chunkX, chunkY).getCell(cellX, cellY);
        // returns if the flagged cell is already opened or flagged
        if (!cell.state.hidden || cell.state.hidden && cell.state.user) {
            play("click_no");
            return;
        }
        this.loadCells(chunkX, chunkY, cellX, cellY, true).then(function (response) {
                let cell = context.getChunk(chunkX, chunkY).getCell(cellX, cellY);
                if (response.data.length === 0) {
                    play("click_error");
                    cell.showScore("100");
                    return;
                }

                //server set ok message for tile flagging
                cell.state.user = 1;
                cell.updateSprite();
                // cell.showScore(response.data.factor);
                play("click_flag");
            }
        );
    }

    /**
     * Returns a list of all 8 adjacent cells for a cell at the given coordinates.
     *
     * @param chunkX x coordinate of the chunk
     * @param chunkY y coordinate of the chunk
     * @param cellX coordinate of the cell
     * @param cellY coordinate of the cell
     * @returns {[]} list of adjacent cells
     */
    getAdjacentCells(chunkX, chunkY, cellX, cellY) {
        let adjacentCells = [];
        chunkX = parseInt(chunkX);
        chunkY = parseInt(chunkY);
        cellX = parseInt(cellX);
        cellY = parseInt(cellY);
        adjacentCells.push(this.getTopCell(chunkX, chunkY, cellX, cellY));
        adjacentCells.push(this.getTopRightCell(chunkX, chunkY, cellX, cellY));
        adjacentCells.push(this.getRightCell(chunkX, chunkY, cellX, cellY));
        adjacentCells.push(this.getBottomRightCell(chunkX, chunkY, cellX, cellY));
        adjacentCells.push(this.getBottomCell(chunkX, chunkY, cellX, cellY));
        adjacentCells.push(this.getBottomLeftCell(chunkX, chunkY, cellX, cellY));
        adjacentCells.push(this.getLeftCell(chunkX, chunkY, cellX, cellY));
        adjacentCells.push(this.getTopLeftCell(chunkX, chunkY, cellX, cellY));
        return adjacentCells;
    }

    /**
     * Returns the cell left to the cell of the given coordinates
     *
     * @param chunkX x coordinate of the chunk
     * @param chunkY y coordinate of the chunk
     * @param cellX coordinate of the cell
     * @param cellY coordinate of the cell
     * @returns {Cell}
     */
    getLeftCell(chunkX, chunkY, cellX, cellY) {
        if (cellX === 0) {
            return this.getChunk(chunkX - 1, chunkY).getCell(CONFIG.CHUNK_SIZE - 1, cellY);
        }
        return this.getChunk(chunkX, chunkY).getCell(cellX - 1, cellY);
    }

    /**
     * Returns the cell right to the cell of the given coordinates
     *
     * @param chunkX x coordinate of the chunk
     * @param chunkY y coordinate of the chunk
     * @param cellX coordinate of the cell
     * @param cellY coordinate of the cell
     * @returns {Cell}
     */
    getRightCell(chunkX, chunkY, cellX, cellY) {
        if (cellX === CONFIG.CHUNK_SIZE - 1) {
            return this.getChunk(chunkX + 1, chunkY).getCell(0, cellY);
        }
        return this.getChunk(chunkX, chunkY).getCell(cellX + 1, cellY);
    }

    /**
     * Returns the cell to the top of the cell of the given coordinates
     *
     * @param chunkX x coordinate of the chunk
     * @param chunkY y coordinate of the chunk
     * @param cellX coordinate of the cell
     * @param cellY coordinate of the cell
     * @returns {Cell}
     */
    getTopCell(chunkX, chunkY, cellX, cellY) {
        if (cellY === 0) {
            return this.getChunk(chunkX, chunkY - 1).getCell(cellX, CONFIG.CHUNK_SIZE - 1);
        }
        return this.getChunk(chunkX, chunkY).getCell(cellX, cellY - 1);
    }

    /**
     * Returns the cell to the bottom of the cell of the given coordinates
     *
     * @param chunkX x coordinate of the chunk
     * @param chunkY y coordinate of the chunk
     * @param cellX coordinate of the cell
     * @param cellY coordinate of the cell
     * @returns {Cell}
     */
    getBottomCell(chunkX, chunkY, cellX, cellY) {
        if (cellY === CONFIG.CHUNK_SIZE - 1) {
            return this.getChunk(chunkX, chunkY + 1).getCell(cellX, 0);
        }
        return this.getChunk(chunkX, chunkY).getCell(cellX, cellY + 1);
    }

    /**
     * Returns the cell to the top left of the cell of the given coordinates
     *
     * @param chunkX x coordinate of the chunk
     * @param chunkY y coordinate of the chunk
     * @param x coordinate of the cell
     * @param cellY coordinate of the cell
     * @returns {Cell}
     */
    getTopLeftCell(chunkX, chunkY, cellX, cellY) {
        if (cellX === 0 && cellY === 0) {
            return this.getChunk(chunkX - 1, chunkY - 1).getCell(CONFIG.CHUNK_SIZE - 1, CONFIG.CHUNK_SIZE - 1);
        } else if (cellX === 0) {
            return this.getChunk(chunkX - 1, chunkY).getCell(CONFIG.CHUNK_SIZE - 1, cellY - 1);
        } else if (cellY === 0) {
            return this.getChunk(chunkX, chunkY - 1).getCell(cellX - 1, CONFIG.CHUNK_SIZE - 1);
        }
        return this.getChunk(chunkX, chunkY).getCell(cellX - 1, cellY - 1);
    }

    /**
     * Returns the cell to the top right of the cell of the given coordinates
     *
     * @param chunkX x coordinate of the chunk
     * @param chunkY y coordinate of the chunk
     * @param cellX coordinate of the cell
     * @param cellY coordinate of the cell
     * @returns {Cell}
     */
    getTopRightCell(chunkX, chunkY, cellX, cellY) {
        if (cellX === CONFIG.CHUNK_SIZE - 1 && cellY === 0) {
            return this.getChunk(chunkX + 1, chunkY - 1).getCell(0, CONFIG.CHUNK_SIZE - 1);
        } else if (cellX === CONFIG.CHUNK_SIZE - 1) {
            return this.getChunk(chunkX + 1, chunkY).getCell(0, cellY - 1);
        } else if (cellY === 0) {
            return this.getChunk(chunkX, chunkY - 1).getCell(cellX + 1, CONFIG.CHUNK_SIZE - 1);
        }
        return this.getChunk(chunkX, chunkY).getCell(cellX + 1, cellY - 1);
    }

    /**
     * Returns the cell to the bottom left of the cell of the given coordinates
     *
     * @param chunkX x coordinate of the chunk
     * @param chunkY y coordinate of the chunk
     * @param cellX coordinate of the cell
     * @param cellY coordinate of the cell
     * @returns {Cell}
     */
    getBottomLeftCell(chunkX, chunkY, cellX, cellY) {
        if (cellX === 0 && cellY === CONFIG.CHUNK_SIZE - 1) {
            return this.getChunk(chunkX - 1, chunkY + 1).getCell(CONFIG.CHUNK_SIZE - 1, 0);
        } else if (cellX === 0) {
            return this.getChunk(chunkX - 1, chunkY).getCell(CONFIG.CHUNK_SIZE - 1, cellY + 1);
        } else if (cellY === CONFIG.CHUNK_SIZE - 1) {
            return this.getChunk(chunkX, chunkY + 1).getCell(cellX - 1, 0);
        }
        return this.getChunk(chunkX, chunkY).getCell(cellX - 1, cellY + 1);
    }

    /**
     * Returns the cell to the bottom right of the cell of the given coordinates
     *
     * @param chunkX x coordinate of the chunk
     * @param chunkY y coordinate of the chunk
     * @param cellX coordinate of the cell
     * @param cellY coordinate of the cell
     * @returns {Cell}
     */
    getBottomRightCell(chunkX, chunkY, cellX, cellY) {
        if (cellX === CONFIG.CHUNK_SIZE - 1 && cellY === CONFIG.CHUNK_SIZE - 1) {
            return this.getChunk(chunkX + 1, chunkY + 1).getCell(0, 0);
        } else if (cellX === CONFIG.CHUNK_SIZE - 1) {
            return this.getChunk(chunkX + 1, chunkY).getCell(0, cellY + 1);
        } else if (cellY === CONFIG.CHUNK_SIZE - 1) {
            return this.getChunk(chunkX, chunkY + 1).getCell(cellX + 1, 0);
        }
        return this.getChunk(chunkX, chunkY).getCell(cellX + 1, cellY + 1);
    }
}