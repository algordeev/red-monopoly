package Model;

public class GoToJailTile extends Tile {

    public GoToJailTile(String name, int position) {
        super(name, position);
    }

    @Override
    public void action(Player player) {
        System.out.println("Player " + player.getName() + " landed on " + name + " and is sent to the Gulag.");
        player.goToJail();
    }
}
