package Model;

/**
 * One player's turn, as an undoable command. The turn itself is carried out by
 * {@link TurnHandler}; this command remembers the full game state from before it.
 */
public class MoveCommand implements Command {
    private final MonopolyGame game;
    private final Player player;
    private final GameSnapshot before;

    public MoveCommand(MonopolyGame game, Player player) {
        this.game = game;
        this.player = player;
        this.before = GameSnapshot.capture(game);
    }

    /** The player whose turn this was. */
    public Player getPlayer() {
        return player;
    }

    @Override
    public void execute() {
        // Intentionally empty: TurnHandler performs the turn.
    }

    @Override
    public void undo() {
        before.restore(game);
    }
}
