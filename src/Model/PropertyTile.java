package Model;

public class PropertyTile extends OwnableTile {
    private final int rent;

    public PropertyTile(String name, int position, int rent, int price) {
        super(name, position, price);
        this.rent = rent;
    }

    public int getRent() {
        return rent;
    }

    @Override
    public int calculateRent() {
        return rent;
    }

    @Override
    protected void addToPlayer(Player player) {
        player.addProperty(this);
    }

    @Override
    protected void removeFromPlayer(Player player) {
        player.getOwnedProperties().remove(this);
    }

    @Override
    protected String kind() {
        return "property";
    }
}
