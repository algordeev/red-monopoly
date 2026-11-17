package View;

import Controller.Controller;
import Model.Player;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the player tokens on top of the board. Tokens sharing a tile are laid out in a
 * grid inside that tile and scaled to fit, so none hide each other or spill onto
 * neighbouring tiles.
 */
public class TokenOverlayPanel extends JPanel {
    private static final String DEFAULT_AVATAR = "/players/player1.png";
    private static final Color CURRENT_PLAYER_RING = new Color(255, 215, 0);
    /** Same token size as before the overlay rewrite. */
    private static final int TOKEN_SIZE = 60;
    /** Largest fraction of a token that another token on the same tile may cover. */
    private static final double MAX_OVERLAP = 0.6;

    private final Controller controller;
    private final BoardPanel boardPanel;
    private final Map<String, BufferedImage> avatarCache = new HashMap<>();
    /** Where to draw a token while it is being animated, instead of its real position. */
    private final Map<Player, Integer> displayPositions = new IdentityHashMap<>();

    public TokenOverlayPanel(Controller controller, BoardPanel boardPanel) {
        this.controller = controller;
        this.boardPanel = boardPanel;
        setOpaque(false);
        loadPlayerIcons();
    }

    /** Preloads avatars for the current players. */
    public void loadPlayerIcons() {
        for (Player p : controller.getAllPlayers()) {
            avatarFor(p);
        }
        repaint();
    }

    public void setDisplayPosition(Player player, int tileIndex) {
        displayPositions.put(player, tileIndex);
        repaint();
    }

    public void clearDisplayPosition(Player player) {
        displayPositions.remove(player);
        repaint();
    }

    private BufferedImage avatarFor(Player player) {
        String path = player.getAvatarPath() == null ? DEFAULT_AVATAR : player.getAvatarPath();
        return avatarCache.computeIfAbsent(path, p -> {
            URL url = getClass().getResource(p);
            if (url == null) url = getClass().getResource(DEFAULT_AVATAR);
            try {
                return url == null ? null : ImageIO.read(url);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        List<Player> players = controller.getAllPlayers();
        if (players.isEmpty()) return;

        Player current = controller.isGameOver() ? null : controller.getCurrentPlayer();

        // Group players by the tile they are drawn on, keeping turn order within a tile.
        Map<Integer, List<Player>> byTile = new LinkedHashMap<>();
        for (Player p : players) {
            int tile = displayPositions.getOrDefault(p, p.getPosition());
            byTile.computeIfAbsent(tile, k -> new ArrayList<>()).add(p);
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (Map.Entry<Integer, List<Player>> entry : byTile.entrySet()) {
            JLabel tileLabel = boardPanel.getTileLabel(entry.getKey());
            if (tileLabel == null) continue;
            Rectangle tile = SwingUtilities.convertRectangle(boardPanel, tileLabel.getBounds(), this);
            drawTokens(g2, tile, entry.getValue(), current);
        }
        g2.dispose();
    }

    private void drawTokens(Graphics2D g2, Rectangle tile, List<Player> tokens, Player current) {
        int n = tokens.size();
        int size = TOKEN_SIZE;
        // Tokens keep their full size; when several share a tile they fan out and overlap
        // partly, but never by more than MAX_OVERLAP, so each one stays visible.
        double minStep = size * (1 - MAX_OVERLAP);

        // Pick the grid shape whose spacing is closest to full-size tokens side by side.
        int bestCols = 1;
        double bestStep = -1;
        for (int cols = 1; cols <= n; cols++) {
            int rows = (n + cols - 1) / cols;
            double step = Math.min(stepFor(tile.width, cols, size), stepFor(tile.height, rows, size));
            if (step > bestStep) {
                bestStep = step;
                bestCols = cols;
            }
        }
        int cols = bestCols;
        int rows = (n + cols - 1) / cols;
        double step = Math.max(bestStep, minStep);

        double gridW = size + (cols - 1) * step, gridH = size + (rows - 1) * step;
        double x0 = tile.getCenterX() - gridW / 2;
        double y0 = tile.getCenterY() - gridH / 2;

        for (int i = 0; i < n; i++) {
            Player p = tokens.get(i);
            int row = i / cols, col = i % cols;
            // Centre a partially filled last row.
            int inRow = (row == rows - 1) ? n - row * cols : cols;
            double rowOffset = (cols - inRow) * step / 2;
            int x = (int) (x0 + rowOffset + col * step);
            int y = (int) (y0 + row * step);

            if (p == current) {
                g2.setColor(CURRENT_PLAYER_RING);
                g2.setStroke(new BasicStroke(3f));
                g2.drawOval(x - 1, y - 1, size + 2, size + 2);
            }
            BufferedImage img = avatarFor(p);
            if (img != null) {
                g2.drawImage(img, x, y, size, size, null);
            }
        }
    }

    /** Distance between token origins so {@code count} tokens fit in {@code length}, up to a full token apart. */
    private static double stepFor(int length, int count, int size) {
        if (count <= 1) return size;
        return Math.min(size, (length - size) / (double) (count - 1));
    }
}
