package Model;

import javax.swing.JOptionPane;
import javax.swing.UIManager;
import java.awt.Font;

/**
 * How game rules talk to the person playing: announcements and yes/no questions.
 * The default shows Swing dialogs; tests and simulations install their own.
 */
public interface Prompter {

    /** Shows an informational message. */
    void inform(String title, String message);

    /** Asks a yes/no question and returns true for yes. */
    boolean confirm(String title, String message);

    /** Shows a message about something that went wrong (e.g. not enough money). */
    default void error(String title, String message) {
        inform(title, message);
    }

    /** Active prompter. Replace with {@link #set(Prompter)}. */
    static Prompter get() {
        return Holder.current;
    }

    static void set(Prompter prompter) {
        Holder.current = prompter == null ? SWING : prompter;
    }

    Prompter SWING = new Prompter() {
        @Override
        public void inform(String title, String message) {
            JOptionPane.showMessageDialog(null, message, title, JOptionPane.INFORMATION_MESSAGE);
        }

        @Override
        public boolean confirm(String title, String message) {
            UIManager.put("OptionPane.messageFont", new Font("Arial", Font.PLAIN, 18));
            UIManager.put("OptionPane.buttonFont", new Font("Arial", Font.PLAIN, 16));
            return JOptionPane.showConfirmDialog(null, message, title, JOptionPane.YES_NO_OPTION)
                    == JOptionPane.YES_OPTION;
        }

        @Override
        public void error(String title, String message) {
            JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
        }
    };

    final class Holder {
        private static volatile Prompter current = SWING;

        private Holder() {
        }
    }
}
