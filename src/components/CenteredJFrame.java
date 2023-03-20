package components;

import javax.swing.*;
import java.awt.*;

public abstract class CenteredJFrame extends JFrame {
    public CenteredJFrame() {}
    public CenteredJFrame(final Dimension size) {
        setSize(size);
        alignToDesktopCenter();
    }

    public void alignToDesktopCenter() {
        final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        final Point centeredPosition = new Point((int)(screenSize.width / 2f - getWidth() / 2f), (int)(screenSize.height / 2f - getHeight() / 2f));
        setLocation(centeredPosition);
    }
}
