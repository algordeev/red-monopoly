package Model;


/** Charges a percentage of the player's current balance. */
public class TaxTile extends Tile {
    /** Percentage of the balance to pay, e.g. 10 means 10 %. */
    private int taxAmount;

    public TaxTile(String name, int position, int taxAmount) {
        super(name, position);
        this.taxAmount = taxAmount;
    }

    public int calculateTax(Player player) {
        return Math.max(0, player.getMoney()) * taxAmount / 100;
    }

    @Override
    public void action(Player player) {
        int tax = calculateTax(player);
        Prompter.get().inform("Tax Tile", player.getName() + " landed on " + name + ". Pay " + taxAmount
                        + " % of your balance (" + tax + " ₽).");
        player.deductMoney(tax);
    }

    public int getTaxAmount() {
        return this.taxAmount;
    }
}
