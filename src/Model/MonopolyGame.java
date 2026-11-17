package Model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

public class MonopolyGame {

    public static final int GO_SALARY = 200;

    private static volatile MonopolyGame INSTANCE; // Volatile ensures visibility across threads

    private List<Player> players;
    private Board board;
    private int currentPlayerIndex;
    private Random dice;
    private boolean gameOver;

    /** Notified when state changes mid-turn (e.g. a card moves a player) so the UI can redraw. */
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    private MonopolyGame() {
        players = new ArrayList<>();
        board = new Board();
        currentPlayerIndex = 0;
        dice = new Random();
    }

    public static MonopolyGame getInstance() {
        if (INSTANCE == null) {
            synchronized (MonopolyGame.class) {
                if (INSTANCE == null) {
                    INSTANCE = new MonopolyGame();
                }
            }
        }
        return INSTANCE;
    }

    /** Fresh board and turn order for a new game. Players are set separately. */
    public void resetForNewGame() {
        board = new Board();
        currentPlayerIndex = 0;
        gameOver = false;
    }

    public void setNumberOfPlayers(int count, List<String> playerNames) {
        if (count < 1) count = 1;
        if (count > 8) count = 8;

        players.clear();
        for (int i = 0; i < count; i++) {
            players.add(new Player(playerNames.get(i)));
        }
        currentPlayerIndex = 0;
    }

    public Player getCurrentPlayer() {
        return players.get(currentPlayerIndex);
    }

    public List<Player> getPlayers() {
        return this.players;
    }

    public int rollDice() {
        return (dice.nextInt(6) + 1) + (dice.nextInt(6) + 1);
    }

    public int rollSingleDie() {
        return dice.nextInt(6) + 1;
    }

    /**
     * Moves the player forward without resolving the destination tile.
     * Pays the GO salary when the move passes or lands on GO.
     */
    public void advancePlayer(Player player, int steps) {
        int boardSize = board.getSize();
        int target = player.getPosition() + steps;
        if (target >= boardSize) {
            player.addMoney(GO_SALARY);
        }
        player.setPosition(target % boardSize);
    }

    /** Moves the player forward and resolves the tile they land on. */
    public void movePlayer(Player player, int steps) {
        advancePlayer(player, steps);
        board.getTile(player.getPosition()).action(player);
    }

    /**
     * Moves the player forward to a specific tile (used by cards), paying the GO salary
     * if that means passing GO, then resolves the destination tile.
     */
    public void advancePlayerTo(Player player, int position) {
        int steps = Math.floorMod(position - player.getPosition(), board.getSize());
        advancePlayer(player, steps);
        fireStateChanged();
        board.getTile(player.getPosition()).action(player);
    }

    public void nextTurn() {
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
    }

    public Board getBoard() {
        return board;
    }

    public int getNumberOfPlayers() {
        return players.size();
    }

    public int getCurrentPlayerIndex() {
        return currentPlayerIndex;
    }

    public void setBoard(Board board) {
        this.board = board;
    }

    public void setCurrentPlayerIndex(int currentPlayerIndex) {
        this.currentPlayerIndex = currentPlayerIndex;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public void setGameOver(boolean gameOver) {
        this.gameOver = gameOver;
    }

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    public void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    public void fireStateChanged() {
        for (Runnable l : changeListeners) {
            l.run();
        }
    }
}
