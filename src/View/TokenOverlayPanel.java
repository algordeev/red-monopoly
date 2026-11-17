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
        int pad = Math.max(2, Math.min(tile.width, tile.height) / 14);
        int w = tile.width - 2 * pad, h = tile.height - 2 * pad;

        // Pick the grid shape that gives the largest cells.
        int bestCols = 1;
        double bestCell = 0;
        for (int cols = 1; cols <= n; cols++) {
            int rows = (n + cols - 1) / cols;
            double cell = Math.min(w / (double) cols, h / (double) rows);
            if (cell > bestCell) {
                bestCell = cell;
                bestCols = cols;
            }
        }
        int cols = bestCols;
        int rows = (n + cols - 1) / cols;
        // One token alone shouldn't cover the whole tile.
        int size = (int) Math.min(bestCell * 0.95, Math.min(w, h) * 0.7);
        if (size < 4) return;

        int gridW = (int) (cols * bestCell), gridH = (int) (rows * bestCell);
        int x0 = tile.x + pad + (w - gridW) / 2;
        int y0 = tile.y + pad + (h - gridH) / 2;

        for (int i = 0; i < n; i++) {
            Player p = tokens.get(i);
            int row = i / cols, col = i % cols;
            // Centre a partially filled last row.
            int inRow = (row == rows - 1) ? n - row * cols : cols;
            double rowOffset = (cols - inRow) * bestCell / 2;
            int cx = (int) (x0 + rowOffset + col * bestCell + bestCell / 2);
            int cy = (int) (y0 + row * bestCell + bestCell / 2);
            int x = cx - size / 2, y = cy - size / 2;

            if (p == current) {
                g2.setColor(CURRENT_PLAYER_RING);
                g2.setStroke(new BasicStroke(Math.max(2f, size / 14f)));
                g2.drawOval(x - 1, y - 1, size + 2, size + 2);
            }
            BufferedImage img = avatarFor(p);
            if (img != null) {
                g2.drawImage(img, x, y, size, size, null);
            }
        }
    }
}
