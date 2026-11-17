package Controller;

import Model.*;
import Model.GameState;
import Model.TurnHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.stream.Collectors;
import javax.swing.JOptionPane;

public class Controller {
    private MonopolyGame _game;
    private int lastDie1;
    private int lastDie2;
    private String aiDifficulty = "Easy";
    private Stack<Command> commandHistory = new Stack<>();

    // Delegate all turn/movement logic to TurnHandler.
    private TurnHandler turnHandler;

    public Controller(MonopolyGame game) {
        this._game = game;
        this.turnHandler = new TurnHandler(game);
    }

    // --- AI Difficulty and Player Setup ---
    public void setAIDifficulty(String difficulty) {
        aiDifficulty = difficulty;
    }

    public String getAIDifficulty() {
        return aiDifficulty;
    }

    public void setNumberOfPlayers(int count, List<String> playerNames, List<String> playerAvatars) {

        if (count < 1) count = 1;
        if (count > 8) count = 8;

        _game.resetForNewGame();
        _game.getPlayers().clear();
        commandHistory.clear();

        for (int i = 0; i < count; i++) {
            String name = playerNames.get(i).trim();
            Player p;
            if(name.equalsIgnoreCase("ai")) {
                AIStrategy strategy;
                switch(aiDifficulty.toLowerCase()){
                    case "easy":
                        strategy = new Model.EasyAIStrategy();
                        break;
                    case "hard":
                        strategy = new Model.HardAIStrategy();
                        break;
                    case "medium":
                    default:
                        strategy = new Model.MidAIStrategy();
                        break;
                }
                System.out.println("Creating AI player at index " + i);
                p = new Model.AIPlayer(name, strategy);
            } else {
                System.out.println("Creating Human player '" + name + "' at index " + i);
                p = new Model.Player(name);
            }

            // Set avatar from selection
            p.setAvatarPath(playerAvatars.get(i));

            _game.getPlayers().add(p);
        }
    }

    public String getCurrentPlayerName() {
        return _game.getCurrentPlayer().getName();
    }

    public int getCurrentPlayerBalance() {
        return _game.getCurrentPlayer().getMoney();
    }

    // --- Dice Rolling and Movement ---
    public int[] rollDice() {
        int die1 = _game.rollSingleDie();
        int die2 = _game.rollSingleDie();
        lastDie1 = die1;
        lastDie2 = die2;
        return new int[]{die1, die2};
    }

    /**
     * Plays the current player's whole turn at once (rules in TurnHandler).
     * Used by the network server and the AI strategies; the local GUI uses
     * {@link #startTurn} and {@link #finishTurn} so it can animate in between.
     *
     * @param dice the two dice values as an array.
     * @return a message describing the outcome of the turn.
     */
    public String movePlayerAfterDiceRoll(int[] dice) {
        return finishTurn(startTurn(dice));
    }

    /** Phase 1: records an undo point, applies jail rules and moves the current player. */
    public TurnHandler.MovePlan startTurn(int[] dice) {
        Player player = _game.getCurrentPlayer();
        executeCommand(new MoveCommand(_game, player));
        return turnHandler.startTurn(player, dice);
    }

    /** Phase 2: resolves the tile the player landed on and passes the turn on. */
    public String finishTurn(TurnHandler.MovePlan plan) {
        return turnHandler.finishTurn(plan);
    }

    public boolean isGameOver() {
        return _game.isGameOver();
    }

    // --- Command Pattern Support ---
    public void executeCommand(Command command) {
        command.execute();
        commandHistory.push(command);
    }

    public boolean canUndo() {
        return !commandHistory.isEmpty();
    }

    public void undoLastCommand() {
        if (!commandHistory.isEmpty()) {
            Command command = commandHistory.pop();
            command.undo();
        } else {
            System.out.println("No commands to undo.");
        }
    }

    /**
     * Undoes the most recent turn. When the game has human players, AI turns are rolled
     * back too, so the game returns to the start of the last human turn.
     */
    public void undoLastTurn() {
        boolean hasHumans = commandHistory.stream()
                .anyMatch(c -> c instanceof MoveCommand m && !(m.getPlayer() instanceof AIPlayer));
        while (!commandHistory.isEmpty()) {
            Command command = commandHistory.pop();
            command.undo();
            boolean aiTurn = command instanceof MoveCommand m && m.getPlayer() instanceof AIPlayer;
            if (!hasHumans || !aiTurn) break;
        }
    }

    public Stack<Command> getCommandHistory() {
        return commandHistory;
    }

    // --- Save/Load using Gson and RuntimeTypeAdapterFactory for Tile ---
    public void saveGame(String filename) {
        File dir = new File("games");
        if (!dir.exists()) dir.mkdirs();

        File file = new File(dir, filename);

        RuntimeTypeAdapterFactory<Tile> tileAdapterFactory = RuntimeTypeAdapterFactory
                .of(Tile.class, "tileType")
                .registerSubtype(GoTile.class, "GoTile")
                .registerSubtype(PropertyTile.class, "PropertyTile")
                .registerSubtype(ChanceTile.class, "ChanceTile")
                .registerSubtype(JailTile.class, "JailTile")
                .registerSubtype(GoToJailTile.class, "GoToJailTile")
                .registerSubtype(TaxTile.class, "TaxTile")
                .registerSubtype(CommunityChestTile.class, "CommunityChestTile")
                .registerSubtype(FreeParkingTile.class, "FreeParkingTile")
                .registerSubtype(RailroadTile.class, "RailroadTile")
                .registerSubtype(UtilityTile.class, "UtilityTile");

        RuntimeTypeAdapterFactory<Player> playerAdapterFactory = RuntimeTypeAdapterFactory
                .of(Player.class, "type")
                .registerSubtype(Player.class, "Human")
                .registerSubtype(AIPlayer.class, "AI");

        Gson gson = new GsonBuilder()
                .registerTypeAdapterFactory(tileAdapterFactory)
                .registerTypeAdapterFactory(playerAdapterFactory)
                .setPrettyPrinting()
                .create();

        GameState state = new GameState(
                _game.getPlayers(),
                _game.getBoard(),
                _game.getCurrentPlayerIndex()
        );

        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(state, writer);
            System.out.println("Game saved to " + file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void loadGame(String filename) {
        File file = new File(filename);
        if (!file.exists()) {
            file = new File("games", filename);
        }

        RuntimeTypeAdapterFactory<Tile> tileAdapterFactory = RuntimeTypeAdapterFactory
                .of(Tile.class, "tileType")
                .registerSubtype(GoTile.class, "GoTile")
                .registerSubtype(PropertyTile.class, "PropertyTile")
                .registerSubtype(ChanceTile.class, "ChanceTile")
                .registerSubtype(JailTile.class, "JailTile")
                .registerSubtype(GoToJailTile.class, "GoToJailTile")
                .registerSubtype(TaxTile.class, "TaxTile")
                .registerSubtype(CommunityChestTile.class, "CommunityChestTile")
                .registerSubtype(FreeParkingTile.class, "FreeParkingTile")
                .registerSubtype(RailroadTile.class, "RailroadTile")
                .registerSubtype(UtilityTile.class, "UtilityTile");


        RuntimeTypeAdapterFactory<Player> playerAdapterFactory = RuntimeTypeAdapterFactory
                .of(Player.class, "type")
                .registerSubtype(Player.class, "Human")
                .registerSubtype(AIPlayer.class, "AI");

        Gson gson = new GsonBuilder()
                .registerTypeAdapterFactory(tileAdapterFactory)
                .registerTypeAdapterFactory(playerAdapterFactory)
                .create();

        try (FileReader reader = new FileReader(file)) {
            GameState state = gson.fromJson(reader, GameState.class);
            if (state == null || state.getPlayers() == null || state.getPlayers().isEmpty()) {
                System.err.println("Loaded game state is empty. Check file: " + file.getAbsolutePath());
                return;
            }

            restoreState(state);
            System.out.println("Game loaded from " + file.getAbsolutePath());


        } catch (IOException e) {
            e.printStackTrace();
        }

        //debug
        for (Player player : _game.getPlayers()) {
            System.out.println(player.getName() + " owns:");
            for (PropertyTile prop : player.getOwnedProperties()) {
                System.out.println("  - Property: " + prop.getName());
            }
            for (RailroadTile rail : player.getOwnedRailroads()) {
                System.out.println("  - Railroad: " + rail.getName());
            }
        }




    }



    public MonopolyGame getMonopolyGame() {
        return _game;
    }

    public Player getCurrentPlayer(){
        return _game.getCurrentPlayer();
    }

    public List<Tile> getBoardTiles() {
        return _game.getBoard().getTiles();
    }

    public List<Player> getAllPlayers() {
        return _game.getPlayers();
    }

    public void endTurn() {
         _game.nextTurn();
    }

    public void setAITurnDelay(int delayMs) {
        TurnHandler.setGlobalDelay(delayMs);
    }



    public void loadGameState(GameState state) {
        restoreState(state);
    }

    /**
     * Installs a deserialized game state. Saved files store each player's tiles as copies,
     * so ownership is re-linked to the board's own tile objects by name.
     */
    private void restoreState(GameState state) {
        _game.getPlayers().clear();
        _game.getPlayers().addAll(state.getPlayers());
        _game.getBoard().setTiles(state.getBoard().getTiles());
        _game.setCurrentPlayerIndex(state.getCurrentPlayerIndex());
        _game.setGameOver(false);

        for (Player player : _game.getPlayers()) {
            player.setOwnedProperties(relink(player, player.getOwnedProperties(), PropertyTile.class));
            player.setOwnedRailroads(relink(player, player.getOwnedRailroads(), RailroadTile.class));
            if (player instanceof AIPlayer ai) {
                ai.restoreStrategyFromDifficulty();
            }
        }
        commandHistory.clear();
    }

    private <T extends OwnableTile> List<T> relink(Player owner, List<T> saved, Class<T> type) {
        List<T> linked = new ArrayList<>();
        if (saved == null) return linked;
        for (T copy : saved) {
            for (Tile tile : _game.getBoard().getTiles()) {
                if (type.isInstance(tile) && tile.getName().equals(copy.getName())) {
                    T boardTile = type.cast(tile);
                    boardTile.setOwner(owner);
                    linked.add(boardTile);
                    break;
                }
            }
        }
        return linked;
    }
}
