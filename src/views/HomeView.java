package views;

import components.CenteredJFrame;
import controllers.HomeViewController;
import net.miginfocom.swing.MigLayout;
import utils.Ngrok;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class HomeView extends CenteredJFrame {
    public JLabel hostNameLabel, hostPortLabel;
    public JButton hostNameCopyBtn, hostPortCopyBtn, hostBtn;
    public final HomeViewController controller;

    public HomeView() throws IOException {
        super(new Dimension(340, 180));

        setLayout(new MigLayout("width 100%, height 100%", "[][][]"));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setTitle("CodeCollab Host");
        setResizable(false);

        initComponents();

        controller = new HomeViewController(this);

        setVisible(true);
    }

    private void initComponents() {
        final String[] details = Ngrok.createUrl().split(":");

        add(new JLabel("Host name: "), "spanx 2");

        hostNameLabel = new JLabel(details[0]) {{
            setFont(getFont().deriveFont(Font.BOLD));
        }};
        add(hostNameLabel);

        hostNameCopyBtn = new JButton("Copy");
        add(hostNameCopyBtn, "wrap");

        add(new JLabel("Port: "), "spanx 2");

        hostPortLabel = new JLabel(details[1]) {{
            setFont(getFont().deriveFont(Font.BOLD));
        }};
        add(hostPortLabel);

        hostPortCopyBtn = new JButton("Copy");
        add(hostPortCopyBtn, "wrap");

        hostBtn = new JButton("Host") {{
            setHorizontalAlignment(CENTER);
        }};
        add(hostBtn, "spanx 3, width 100%, gapy 20");
    }
}
