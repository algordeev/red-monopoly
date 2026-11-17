package Model;

import java.util.ArrayList;
import java.util.List;

/**
 * Game rules for a single turn, split into two phases so a UI can animate in between:
 * <ol>
 *   <li>{@link #startTurn} decides whether and how far the player moves (jail rules,
 *       GO salary) and updates their position, without resolving the destination;</li>
 *   <li>{@link #finishTurn} resolves the destination tile, removes bankrupt players
 *       and picks whose turn is next.</li>
 * </ol>
 * {@link #processTurn} runs both phases back to back.
 */
public class TurnHandler {
    public static final int JAIL_RELEASE_FEE = 50;
    /** Failed doubles attempts allowed before the fee must be paid (the third roll in jail). */
    private static final int MAX_JAIL_ATTEMPTS = 3;

    private final MonopolyGame game;

    /** Result of {@link #startTurn}: who moved, from where, and how many steps (0 = stayed put). */
    public record MovePlan(Player player, int from, int steps, String message) {
        public boolean moved() {
            return steps > 0;
        }
    }

    public TurnHandler(MonopolyGame game) {
        this.game = game;
    }

    /** Runs a whole turn without pausing between movement and landing. */
    public String processTurn(Player player, int[] dice) {
        return finishTurn(startTurn(player, dice));
    }

    public MovePlan startTurn(Player player, int[] dice) {
        int from = player.getPosition();
        StringBuilder message = new StringBuilder();
        int steps = player.isInJail()
                ? resolveJail(player, dice, message)
                : dice[0] + dice[1];

        if (steps > 0) {
            game.advancePlayer(player, steps);
            message.append(player.getName()).append(" rolled ").append(dice[0]).append(" and ")
                    .append(dice[1]).append(" and moved to ")
                    .append(game.getBoard().getTile(player.getPosition()).getName()).append(".\n");
            if (from + steps >= game.getBoard().getSize()) {
                message.append("Passed GO: collected ").append(MonopolyGame.GO_SALARY).append(" ₽.\n");
            }
        }
        return new MovePlan(player, from, steps, message.toString());
    }

    public String finishTurn(MovePlan plan) {
        Player player = plan.player();
        StringBuilder message = new StringBuilder(plan.message());

        if (plan.moved()) {
            Tile tile = game.getBoard().getTile(player.getPosition());
            tile.action(player);
        }
        message.append(player.getName()).append(" balance: ").append(player.getMoney()).append(" ₽\n");
        message.append(settleEndOfTurn(player));
        return message.toString().trim();
    }

    /** Applies the jail rules for this roll and returns how many steps the player moves (0 = stays). */
    private int resolveJail(Player player, int[] dice, StringBuilder message) {
        int attempt = player.getJailTurnCount() + 1;
        int roll = dice[0] + dice[1];
        String msg = player.getName() + " is in the Gulag (turn " + attempt + ") and rolled "
                + dice[0] + " and " + dice[1] + ". ";

        if (dice[0] == dice[1]) {
            msg += "Doubles! Released from the Gulag.";
        } else if (player.getGetOutOfJailCards() > 0) {
            player.useGetOutOfJailCard();
            msg += "Uses a \"Get Out of Gulag Free\" card.";
        } else if (attempt >= MAX_JAIL_ATTEMPTS) {
            player.deductMoney(JAIL_RELEASE_FEE);
            msg += "No doubles after " + MAX_JAIL_ATTEMPTS + " turns: pays " + JAIL_RELEASE_FEE
                    + " ₽ and leaves.";
        } else {
            player.incrementJailTurn();
            msg += "No doubles. Stays in the Gulag.";
            Prompter.get().inform("Gulag", msg);
            message.append(msg).append('\n');
            return 0;
        }

        player.setInJail(false);
        player.resetJailTurn();
        Prompter.get().inform("Gulag", msg);
        message.append(msg).append('\n');
        return roll;
    }

    /**
     * Removes bankrupt players (returning their property to the bank), detects the end of
     * the game, and hands the turn to the next player, honouring extra-turn and skip-turn cards.
     */
    private String settleEndOfTurn(Player mover) {
        List<Player> players = game.getPlayers();
        List<Player> turnOrder = new ArrayList<>(players);
        StringBuilder message = new StringBuilder();

        List<Player> bankrupt = turnOrder.stream().filter(p -> p.getMoney() < 0).toList();
        if (!bankrupt.isEmpty()) {
            for (Player p : bankrupt) {
                releaseAssets(p);
            }
            players.removeAll(bankrupt);
            String names = String.join(", ", bankrupt.stream().map(Player::getName).toList());
            String msg = "Eliminated players due to negative balance: " + names;
            message.append(msg).append('\n');
            Prompter.get().inform("Eliminations", msg);
        }

        if (players.size() <= 1) {
            game.setGameOver(true);
            game.setCurrentPlayerIndex(0);
            if (players.size() == 1) {
                String msg = "Game Over! Winner: " + players.get(0).getName();
                message.append(msg).append('\n');
                Prompter.get().inform("Game Over", msg);
            }
            return message.toString();
        }

        Player next = chooseNextPlayer(mover, turnOrder, message);
        game.setCurrentPlayerIndex(players.indexOf(next));
        return message.toString();
    }

    private Player chooseNextPlayer(Player mover, List<Player> turnOrder, StringBuilder message) {
        List<Player> players = game.getPlayers();
        if (mover.hasExtraTurn()) {
            mover.resetExtraTurn();
            if (players.contains(mover)) {
                message.append(mover.getName()).append(" takes another turn.\n");
                return mover;
            }
        }

        int start = turnOrder.indexOf(mover);
        // Two laps at most: the first lap may clear skip flags, the second always finds someone.
        for (int i = 1; i <= turnOrder.size() * 2; i++) {
            Player candidate = turnOrder.get((start + i) % turnOrder.size());
            if (!players.contains(candidate)) continue;
            if (candidate.shouldSkipTurn() && candidate != mover) {
                candidate.resetSkipTurn();
                String msg = candidate.getName() + " skips this turn.";
                message.append(msg).append('\n');
                Prompter.get().inform("Turn skipped", msg);
                continue;
            }
            if (candidate == mover) mover.resetSkipTurn();
            return candidate;
        }
        return players.get(0);
    }

    private void releaseAssets(Player player) {
        for (Tile t : game.getBoard().getTiles()) {
            if (t instanceof OwnableTile o && o.getOwner() == player) {
                o.release();
            }
        }
        player.getOwnedProperties().clear();
        player.getOwnedRailroads().clear();
    }

    private static int GLOBAL_DELAY_MS = 1000;

    public static void setGlobalDelay(int ms) {
        GLOBAL_DELAY_MS = ms;
    }

    public static int getGlobalDelay() {
        return GLOBAL_DELAY_MS;
    }
}
