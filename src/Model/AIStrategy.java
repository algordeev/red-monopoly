package Model;
import Controller.Controller;

public interface AIStrategy {
    /**
     * Defines how the AI player takes a turn.
     * @param aiPlayer The AI-controlled player.
     * @param game The Monopoly game instance.
     * @param controller The game controller (to use existing logic for dice and movement).
     */
    void playTurn(AIPlayer aiPlayer, MonopolyGame game, Controller controller);

    /**
     * Plays the turn with dice that were already rolled (and shown to the user),
     * instead of rolling new ones.
     */
    default void playTurn(AIPlayer aiPlayer, MonopolyGame game, Controller controller, int[] diceValues) {
        System.out.println(controller.movePlayerAfterDiceRoll(diceValues));
    }
    boolean shouldBuyTile(AIPlayer aiPlayer, Tile tile);

}
