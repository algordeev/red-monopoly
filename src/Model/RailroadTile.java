package Model;

public class RailroadTile extends OwnableTile {
    private final int rent;

    public RailroadTile(String name, int position, int rent, int price) {
        super(name, position, price);
        this.rent = rent;
    }

    /** Base rent when the owner has a single railroad. */
    public int getRent() {
        return rent;
    }

    /** Base rent doubles for each additional railroad the owner holds: 25, 50, 100, 200. */
    @Override
    public int calculateRent() {
        Player owner = getOwner();
        if (owner == null) return 0;
        int owned = Math.max(1, owner.getOwnedRailroads().size());
        return rent << (owned - 1);
    }

    @Override
    protected void addToPlayer(Player player) {
        player.addRailroad(this);
    }

    @Override
    protected void removeFromPlayer(Player player) {
        player.getOwnedRailroads().remove(this);
    }

    @Override
    protected String kind() {
        return "railroad";
    }
}
