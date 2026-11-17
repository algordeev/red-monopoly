package Model;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Captures everything a turn can change so undo can put the game back exactly:
 * every player's state (including players eliminated later), every tile owner,
 * the turn order and whose turn it is.
 */
public final class GameSnapshot {

    private record PlayerState(int position, int money, boolean inJail, int jailTurns,
                               boolean skipTurn, boolean extraTurn, int jailCards,
                               List<PropertyTile> properties, List<RailroadTile> railroads) {

        static PlayerState of(Player p) {
            return new PlayerState(p.getPosition(), p.getMoney(), p.isInJail(), p.getJailTurnCount(),
                    p.shouldSkipTurn(), p.hasExtraTurn(), p.getGetOutOfJailCards(),
                    new ArrayList<>(p.getOwnedProperties()), new ArrayList<>(p.getOwnedRailroads()));
        }

        void applyTo(Player p) {
            p.setPosition(position);
            p.setMoney(money);
            p.setInJail(inJail);
            p.setJailTurnCount(jailTurns);
            p.setSkipTurn(skipTurn);
            p.setExtraTurn(extraTurn);
            p.setGetOutOfJailCards(jailCards);
            p.setOwnedProperties(new ArrayList<>(properties));
            p.setOwnedRailroads(new ArrayList<>(railroads));
        }
    }

    private final Board board;
    private final List<Player> players;
    private final Map<Player, PlayerState> playerStates = new IdentityHashMap<>();
    private final Map<OwnableTile, Player> owners = new IdentityHashMap<>();
    private final int currentPlayerIndex;
    private final boolean gameOver;

    private GameSnapshot(MonopolyGame game) {
        board = game.getBoard();
        players = new ArrayList<>(game.getPlayers());
        for (Player p : players) {
            playerStates.put(p, PlayerState.of(p));
        }
        for (Tile t : board.getTiles()) {
            if (t instanceof OwnableTile o) {
                owners.put(o, o.getOwner());
            }
        }
        currentPlayerIndex = game.getCurrentPlayerIndex();
        gameOver = game.isGameOver();
    }

    public static GameSnapshot capture(MonopolyGame game) {
        return new GameSnapshot(game);
    }

    public void restore(MonopolyGame game) {
        game.setBoard(board);
        game.getPlayers().clear();
        game.getPlayers().addAll(players);
        playerStates.forEach((p, s) -> s.applyTo(p));
        owners.forEach(OwnableTile::setOwner);
        game.setCurrentPlayerIndex(currentPlayerIndex);
        game.setGameOver(gameOver);
    }
}
