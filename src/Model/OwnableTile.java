package Model;

/**
 * A tile that can be bought and charges rent: properties and railroads.
 * Landing logic is identical for humans and AI; only the buy decision differs.
 */
public abstract class OwnableTile extends Tile {
    private transient Player owner;
    private final int price;

    protected OwnableTile(String name, int position, int price) {
        super(name, position);
        this.price = price;
    }

    public Player getOwner() {
        return owner;
    }

    public void setOwner(Player owner) {
        this.owner = owner;
    }

    public int getPrice() {
        return price;
    }

    /** Rent the current owner charges a visitor. Only meaningful when owned. */
    public abstract int calculateRent();

    /** Adds this tile to the matching ownership list on the player. */
    protected abstract void addToPlayer(Player player);

    /** Removes this tile from the matching ownership list on the player. */
    protected abstract void removeFromPlayer(Player player);

    /** Human-readable kind used in dialogs, e.g. "property" or "railroad". */
    protected abstract String kind();

    public void buy(Player player) {
        player.deductMoney(price);
        owner = player;
        addToPlayer(player);
    }

    /** Returns the tile to the bank, e.g. when its owner goes bankrupt. */
    public void release() {
        if (owner != null) {
            removeFromPlayer(owner);
            owner = null;
        }
    }

    @Override
    public void action(Player player) {
        if (owner == null) {
            offerPurchase(player);
        } else if (owner != player) {
            int rent = calculateRent();
            player.deductMoney(rent);
            owner.addMoney(rent);
            Prompter.get().inform("Rent Paid", player.getName() + " landed on " + getName() + " owned by "
                            + owner.getName() + " and paid " + rent + " ₽ rent.");
        } else {
            System.out.println(player.getName() + " landed on their own " + kind() + ": " + getName());
        }
    }

    private void offerPurchase(Player player) {
        if (player instanceof AIPlayer ai) {
            if (ai.getMoney() >= price && ai.getStrategy().shouldBuyTile(ai, this)) {
                buy(ai);
                Prompter.get().inform("AI Purchase", ai.getName() + " bought " + getName() + " for " + price + " ₽");
            } else {
                Prompter.get().inform("AI Decision", ai.getName() + " chose not to buy " + getName() + ".");
            }
            return;
        }

        boolean wantsToBuy = Prompter.get().confirm("Buy " + kind(),
                player.getName() + " landed on " + getName() + ". This " + kind()
                        + " is unowned. Buy for " + price + " ₽?");
        if (!wantsToBuy) {
            System.out.println(player.getName() + " chose not to buy " + getName() + ".");
            return;
        }
        if (player.getMoney() >= price) {
            buy(player);
            Prompter.get().inform("Success", player.getName() + " bought " + getName() + " for " + price + " ₽");
        } else {
            Prompter.get().error("Error", player.getName() + " does not have enough money to buy " + getName() + ".");
        }
    }
}
