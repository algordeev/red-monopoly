package View;

import Controller.Controller;
import Controller.NetworkClient;
import Model.GameState;
import Model.Player;
import Model.AIPlayer;
import Model.TurnHandler;
import com.jgoodies.looks.plastic.Plastic3DLookAndFeel;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class GameWindow extends JFrame {
    // Reuse the same click and menu URLs from MainWindow
    public static final URL CLICK_SOUND_URL = MainWindow.CLICK_SOUND_URL;
    public static final URL MENU_MUSIC_URL  = MainWindow.MENU_MUSIC_URL;

    private final Controller    _controller;
    private final MainWindow    _mainWindow;
    private final NetworkClient netClient;
    private final String        playerId;

    private JLabel currentPlayerLabel;
    private JLabel currentBalanceLabel;
    private JButton rollDiceButton;
    private JButton undoButton;
    private MusicPlayer inGameMusic;
    private BoardView boardView;
    private DualDicePanel dualDicePanel;
    private PlayerInfoWindow playerInfoWindow;

    // ── Local turn flow ───────────────────────────────────
    /** Wait for the dice animation (DicePanel runs for 2 s) before moving. */
    private static final int DICE_ANIMATION_MS = 2000;
    /** Pause before an AI player rolls, so humans can follow what is happening. */
    private static final int AI_THINK_MS = 1200;
    /** Time per tile while a token walks along the board. */
    private static final int STEP_MS = 140;

    /** True from the moment a turn is scheduled until it is fully resolved. Guards every control. */
    private boolean turnInProgress;
    private boolean disposed;
    private final List<Timer> activeTimers = new ArrayList<>();
    private final Runnable modelChangeListener = () -> SwingUtilities.invokeLater(this::refreshUIAfterExternalMove);

    public GameWindow(Controller controller,
                      MainWindow mainWindow,
                      NetworkClient netClient,
                      String playerId) {
        super("Game - [RED MONOPOLY]");
        _controller = controller;
        _mainWindow = mainWindow;
        this.netClient = netClient;
        this.playerId = playerId;

        try {
            UIManager.setLookAndFeel(new Plastic3DLookAndFeel());
        } catch (UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }

        setSize(1200, 800);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        initGUI();
        initMusic();
        _controller.getMonopolyGame().addChangeListener(modelChangeListener);

        // Sync the UI before the first AI turn can start.
        updateCurrentPlayerLabel();
        updateCurrentBalanceLabel();
        boardView.refreshBoard();
        boardView.drawPlayerTokens(_controller.getAllPlayers());
        updateControls();

        initNetworkingOrAI();
    }

    private void initGUI() {
        // ── Top info panel ─────────────────────────────────
        JPanel top = new JPanel(new GridLayout(2, 1));
        top.setBackground(new Color(60, 0, 0));

        currentPlayerLabel = new JLabel("Current Player: (loading...)", SwingConstants.CENTER);
        currentPlayerLabel.setFont(new Font("Arial", Font.BOLD, 24));
        currentPlayerLabel.setForeground(Color.WHITE);
        top.add(currentPlayerLabel);

        currentBalanceLabel = new JLabel("Current Balance: –", SwingConstants.CENTER);
        currentBalanceLabel.setFont(new Font("Arial", Font.BOLD, 18));
        currentBalanceLabel.setForeground(Color.WHITE);
        top.add(currentBalanceLabel);

        // ── Bottom buttons ─────────────────────────────────
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        bottom.setBackground(new Color(30, 0, 0));

        rollDiceButton = createImageButton("/dicesRolling.png",
                "Roll the dice to move", e -> onRollDice());
        rollDiceButton.setEnabled(netClient == null);
        bottom.add(makeButtonCard(rollDiceButton));

        bottom.add(makeButtonCard(createImageButton("/goBack.png",
                "Leave Game", e -> onLeave())));
        bottom.add(makeButtonCard(createImageButton("/playerInfoWindow.png",
                "Show player info", e -> onPlayerInfo())));
        undoButton = createImageButton("/undo.png", "Undo last turn", e -> onUndo());
        bottom.add(makeButtonCard(undoButton));

        // ── Board & dice ───────────────────────────────────
        boardView = new BoardView(_controller);
        for (int i = 0; i < 40; i++) {
            URL tileUrl = getClass().getResource("/tiles/" + i + ".png");
            if (tileUrl != null) {
                boardView.getBoardPanel()
                        .setManualTileImage(i, new ImageIcon(tileUrl).getImage());
            }
        }

        dualDicePanel = new DualDicePanel();
        dualDicePanel.setPreferredSize(new Dimension(200, 200));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, boardView, dualDicePanel);
        split.setOneTouchExpandable(true);
        split.setResizeWeight(1.0);
        split.setDividerLocation(0.8);

        setLayout(new BorderLayout());
        add(top,    BorderLayout.NORTH);
        add(split,  BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    private void initMusic() {
        inGameMusic = new MusicPlayer();
        URL inGameUrl = getClass().getResource("/Russia-Theme-Atomic-_Civilization-6-OST_-Kalinka_1.wav");
        if (inGameUrl != null) {
            inGameMusic.playMusic(String.valueOf(inGameUrl));
            inGameMusic.setVolume(0.7f);
        } else {
            System.err.println("In-game music not found!");
        }

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                // Stop any pending turn so AI players don't keep playing after the window is gone.
                disposed = true;
                activeTimers.forEach(Timer::stop);
                activeTimers.clear();
                _controller.getMonopolyGame().removeChangeListener(modelChangeListener);
                inGameMusic.stopMusic();
                if (MENU_MUSIC_URL != null) {
                    _mainWindow.getMusicPlayer().playMusic(String.valueOf(MENU_MUSIC_URL));
                }
                _mainWindow.setVisible(true);
            }
        });
    }

    private void initNetworkingOrAI() {
        if (netClient == null) {
            // Single-player -> AI loop
            checkAndTriggerAITurn();
        } else {
            netClient.setListener(new NetworkClient.GameMessageListener() {
                @Override public void onFullState(GameState state) {
                    SwingUtilities.invokeLater(() -> {
                        _controller.loadGameState(state);
                        updateCurrentPlayerLabel();
                        updateCurrentBalanceLabel();
                        boardView.refreshBoard();
                        boardView.drawPlayerTokens(_controller.getAllPlayers());
                        rollDiceButton.setEnabled(
                                _controller.getCurrentPlayerName().equals(playerId)
                        );
                    });
                }
                @Override public void onMove(String id, int d1, int d2) {
                    SwingUtilities.invokeLater(() ->
                            dualDicePanel.startAnimation(d1, d2)
                    );
                }
                @Override public void onState(String outcome) {
                    if (_controller.getCurrentPlayerName().equals(playerId)) {
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(GameWindow.this, outcome)
                        );
                    }
                }
                @Override public void onTurn(String id) {
                    SwingUtilities.invokeLater(() -> {
                        updateCurrentPlayerLabel();
                        rollDiceButton.setEnabled(id.equals(playerId));
                    });
                }
                @Override public void onBalance(String id, int money, int pos, int props) {
                    if (id.equals(playerId)) {
                        SwingUtilities.invokeLater(() ->
                                currentBalanceLabel.setText("Current Balance: " + money + " ₽")
                        );
                    }
                }
                @Override public void onInfo(String msg) {
                    SwingUtilities.invokeLater(() ->
                            System.out.println("[INFO] " + msg)
                    );
                }
                @Override public void onError(String id, String err) {
                    if (id.equals(playerId)) {
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(GameWindow.this,
                                        "Error: " + err, "Server Error",
                                        JOptionPane.ERROR_MESSAGE)
                        );
                    }
                }
            });
        }
    }

    private void onRollDice() {
        if (netClient != null) {
            rollDiceButton.setEnabled(false);
            try {
                netClient.roll();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Network error: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
            return;
        }
        if (!canHumanAct()) return; // ignore clicks that slip through during a turn
        playLocalTurn();
    }

    /** A local human may roll or undo: no turn running, game not over, and it is a human's turn. */
    private boolean canHumanAct() {
        return netClient == null && !disposed && !turnInProgress && !_controller.isGameOver()
                && !_controller.getAllPlayers().isEmpty()
                && !(_controller.getCurrentPlayer() instanceof AIPlayer);
    }

    private void updateControls() {
        if (netClient != null) {
            undoButton.setEnabled(false); // undo would only change this machine's copy
            return;
        }
        boolean humanCanAct = canHumanAct();
        rollDiceButton.setEnabled(humanCanAct);
        undoButton.setEnabled(humanCanAct && _controller.canUndo());
    }

    /**
     * Plays one turn for the current player, human or AI:
     * dice animation, then the token walks to its tile, then the tile resolves (cards,
     * purchases, rent), then the next turn starts. Only one turn can run at a time.
     */
    private void playLocalTurn() {
        turnInProgress = true;
        updateControls();

        int[] dice = _controller.rollDice();
        dualDicePanel.startAnimation(dice[0], dice[1]);
        schedule(DICE_ANIMATION_MS, () -> {
            TurnHandler.MovePlan plan = _controller.startTurn(dice);
            updateCurrentBalanceLabel();
            animateMove(plan, () -> {
                _controller.finishTurn(plan);
                turnInProgress = false;
                refreshUIAfterExternalMove();
                if (_controller.isGameOver()) {
                    showGameOver();
                } else {
                    checkAndTriggerAITurn();
                }
            });
        });
    }

    /** Walks the token one tile at a time from where it started to where it is now. */
    private void animateMove(TurnHandler.MovePlan plan, Runnable onArrived) {
        if (!plan.moved()) {
            boardView.drawPlayerTokens(_controller.getAllPlayers());
            onArrived.run();
            return;
        }
        Player player = plan.player();
        int boardSize = _controller.getBoardTiles().size();
        int[] step = {0};
        boardView.setTokenDisplayPosition(player, plan.from());

        Timer walker = new Timer(STEP_MS, null);
        walker.addActionListener(e -> {
            step[0]++;
            boardView.setTokenDisplayPosition(player, (plan.from() + step[0]) % boardSize);
            if (step[0] >= plan.steps()) {
                walker.stop();
                activeTimers.remove(walker);
                boardView.clearTokenDisplayPosition(player);
                onArrived.run();
            }
        });
        activeTimers.add(walker);
        walker.start();
    }

    /** Runs the action once after a delay, unless the window has been closed. */
    private void schedule(int delayMs, Runnable action) {
        Timer t = new Timer(delayMs, null);
        t.setRepeats(false);
        t.addActionListener(e -> {
            activeTimers.remove(t);
            if (!disposed) action.run();
        });
        activeTimers.add(t);
        t.start();
    }

    private void showGameOver() {
        updateCurrentPlayerLabel();
        updateControls();
    }

    private void onLeave() {
        if (CLICK_SOUND_URL != null) MusicPlayer.playSoundEffect(CLICK_SOUND_URL);
        int c = JOptionPane.showConfirmDialog(this,
                "Do you want to save before leaving?", "Save Game",
                JOptionPane.YES_NO_CANCEL_OPTION);
        if (c == JOptionPane.CANCEL_OPTION) return;
        if (c == JOptionPane.YES_OPTION) {
            String name = JOptionPane.showInputDialog(this,
                    "Enter save file name:");
            if (name != null && !name.trim().isEmpty()) {
                _controller.saveGame(name.trim());
                JOptionPane.showMessageDialog(this, "Game saved!");
            } else {
                return;
            }
        }
        dispose();
    }

    private void onPlayerInfo() {
        if (CLICK_SOUND_URL != null) MusicPlayer.playSoundEffect(CLICK_SOUND_URL);
        if (playerInfoWindow == null || !playerInfoWindow.isDisplayable()) {
            playerInfoWindow = new PlayerInfoWindow(_controller);
        }
        playerInfoWindow.setVisible(true);
        playerInfoWindow.refreshData();
    }

    private void onUndo() {
        if (!canHumanAct() || !_controller.canUndo()) return;
        _controller.undoLastTurn();
        refreshUIAfterExternalMove();
        checkAndTriggerAITurn();
    }

    private void updateCurrentPlayerLabel() {
        List<Player> ps = _controller.getAllPlayers();
        if (ps.isEmpty()) {
            currentPlayerLabel.setText("Current Player: (loading...)");
        } else if (_controller.isGameOver()) {
            currentPlayerLabel.setText(ps.size() == 1
                    ? "Game Over! Winner: " + ps.get(0).getName()
                    : "Game Over!");
        } else {
            currentPlayerLabel.setText("Current Player: " + _controller.getCurrentPlayerName());
        }
    }

    private void updateCurrentBalanceLabel() {
        List<Player> ps = _controller.getAllPlayers();
        if (ps.isEmpty()) {
            currentBalanceLabel.setText("Current Balance: –");
        } else {
            currentBalanceLabel.setText("Current Balance: " +
                    _controller.getCurrentPlayerBalance() + " ₽");
        }
    }

    public void refreshUIAfterExternalMove() {
        updateCurrentPlayerLabel();
        updateCurrentBalanceLabel();
        boardView.refreshBoard();
        boardView.drawPlayerTokens(_controller.getAllPlayers());
        if (playerInfoWindow != null && playerInfoWindow.isDisplayable()) {
            playerInfoWindow.refreshData();
        }
        updateControls();
    }

    /** If it is an AI player's turn, schedules it. Safe to call any number of times. */
    private void checkAndTriggerAITurn() {
        if (netClient != null || disposed || turnInProgress || _controller.isGameOver()) return;
        List<Player> ps = _controller.getAllPlayers();
        if (ps.isEmpty() || !(_controller.getCurrentPlayer() instanceof AIPlayer)) {
            updateControls();
            return;
        }
        turnInProgress = true; // reserve the turn now so the roll button stays disabled
        updateControls();
        schedule(AI_THINK_MS, this::playLocalTurn);
    }

    private JButton createImageButton(String resourcePath, String tip, ActionListener l) {
        URL imgUrl = getClass().getResource(resourcePath);
        ImageIcon icon = imgUrl != null
                ? new ImageIcon(new ImageIcon(imgUrl).getImage()
                .getScaledInstance(60, 60, Image.SCALE_SMOOTH))
                : new ImageIcon();
        JButton b = new JButton(icon);
        b.setToolTipText(tip);
        b.setBorder(null);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.addActionListener(l);
        return b;
    }

    private JPanel makeButtonCard(JButton btn) {
        JPanel w = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);

                GradientPaint gp = new GradientPaint(
                        0, 0, new Color(60, 0, 0),
                        0, getHeight(), new Color(90, 0, 0)
                );
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(new Color(200, 200, 200, 80));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
                super.paintComponent(g);
            }
        };
        w.setOpaque(false);
        w.setPreferredSize(new Dimension(90, 90));
        w.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        w.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                w.setBorder(BorderFactory.createLineBorder(
                        new Color(255, 255, 255, 90), 2));
            }
            @Override public void mouseExited(MouseEvent e) {
                w.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            }
        });
        w.add(btn, BorderLayout.CENTER);
        return w;
    }
}
