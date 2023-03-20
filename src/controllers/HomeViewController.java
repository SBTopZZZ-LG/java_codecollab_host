package controllers;

import components.PropertyChangeSupported;
import models.ProtocolModel;
import models.StorageEntity;
import models.Synchronized;
import utils.RequestHandlers;
import utils.TCPServer;
import views.HomeView;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class HomeViewController extends PropertyChangeSupported {
    public final Synchronized<HomeView> parent;
    private Thread hostThread = null;

    public HomeViewController(final HomeView parent) {
        this.parent = new Synchronized<>(parent);
        
        setupListeners(parent);
    }

    private void setupListeners(final HomeView parent) {
        parent.hostNameCopyBtn.addActionListener((__) -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(parent.hostNameLabel.getText()), new StringSelection(parent.hostNameLabel.getText())));
        parent.hostPortCopyBtn.addActionListener((__) -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(parent.hostPortLabel.getText()), new StringSelection(parent.hostPortLabel.getText())));

        parent.hostBtn.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (hostThread != null) {
                    ProtocolModel.instance.server.mustStop.set(__ -> true);
                    ProtocolModel.instance = null;

                    parent.hostBtn.setText("Host");
                    return;
                }

                final JFileChooser chooser = new JFileChooser() {{
                    setDialogTitle("Choose the host project directory");
                    setCurrentDirectory(new java.io.File("."));
                    setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    setAcceptAllFileFilterUsed(false);
                }};

                if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
                    final String path = chooser.getSelectedFile().getAbsolutePath();
                    if (!Files.exists(Path.of(path))) {
                        JOptionPane.showMessageDialog(parent,
                                "Specified directory \"" + path + "\" does not exist or you have insufficient permissions to the directory.",
                                "Error",
                                JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    if (hostThread == null)
                        setupHost(path);
                }
            }
        });
    }

    private void setupHost(final String path) {
        hostThread = new Thread(() -> {
            StorageEntity.instance = new Synchronized<>(new StorageEntity(path));

            final List<RequestHandlers.GetRequestHandler> getHandlers = List.of(
                    RequestHandlers::getName,
                    RequestHandlers::getBinary,
                    RequestHandlers::getClients,
                    RequestHandlers::getList,
                    RequestHandlers::getMd5
            );
            final List<RequestHandlers.PostRequestHandler> postHandlers = List.of(
                    RequestHandlers::postName,
                    RequestHandlers::postBinary,
                    RequestHandlers::postChatMessage,
                    RequestHandlers::postUpdatedBinary,
                    RequestHandlers::postVerifyMd5
            );

            ProtocolModel.instance = new ProtocolModel();
            ProtocolModel.instance.init(new ProtocolModel.ProtocolListener() {
                @Override
                public void onInit() {
                    System.out.println("[Initiated]");

                    parent.get(parent -> {
                        parent.hostBtn.setEnabled(false);
                        parent.hostBtn.setText("Terminate");
                        return null;
                    });
                }

                @Override
                public void onClientStatusUpdate(ProtocolModel.Options options, ProtocolModel.ClientStatusUpdateMode mode) {
                    System.out.println("Client " + (mode == ProtocolModel.ClientStatusUpdateMode.Connected ? "Connected" : "Disconnected") + " (" + options.getClient().getRemoteSocketAddress() + ")");
                }

                @Override
                public void onGetData(String requestId, String params, TCPServer.Options options) {
                    System.out.println("\nGet RequestId: " + requestId);
                    System.out.println("Params: " + params);

                    for (final RequestHandlers.GetRequestHandler handler : getHandlers)
                        if (handler.handler(requestId, params, options)) break;
                }

                @Override
                public void onPostData(String requestId, String params, List<String> payloads, TCPServer.Options options) {
                    System.out.println("\nPost RequestId: " + requestId);
                    System.out.println("Params: " + params);
                    System.out.println("Payloads:");
                    payloads.forEach(payload -> System.out.println("- \"" + payload + "\""));

                    for (final RequestHandlers.PostRequestHandler handler : postHandlers)
                        if (handler.handler(requestId, params, payloads, options)) break;
                }
            });
        });

        hostThread.start();
    }
}
