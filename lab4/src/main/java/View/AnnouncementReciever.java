package View;

import Control.Controller;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.table.DefaultTableModel;
import proto.SnakesProto;
import proto.SnakesProto.GameConfig;

/**
 *
 * @author bratizgut
 */
public class AnnouncementReciever extends Thread {

    private final MulticastSocket announcemenSocket;

    private final HashMap<InetSocketAddress, Integer> gamesMap;
    private final HashMap<Integer, Long> lastMsgTime;
    private final HashMap<Integer, GameConfig> configMap;

    private javax.swing.JTable gamesTable;

    private int gameId;

    public AnnouncementReciever(HashMap<Integer, GameConfig> configMap, javax.swing.JTable gamesTable) throws IOException {
        announcemenSocket = new MulticastSocket(9192);
        announcemenSocket.joinGroup(InetAddress.getByName("239.192.0.4"));

        gameId = 0;

        gamesMap = new HashMap<>();
        lastMsgTime = new HashMap<>();
        this.configMap = configMap;
        this.gamesTable = gamesTable;
    }

    @Override
    public void run() {
        try {
            byte[] buf = new byte[256];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            while (!isInterrupted()) {
                try {
                    announcemenSocket.setSoTimeout(500);

                    announcemenSocket.receive(packet);

                    byte[] msgBuf = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
                    SnakesProto.GameMessage announcementMsg = SnakesProto.GameMessage.parseFrom(msgBuf);
                    InetSocketAddress senderAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());

                    if (!gamesMap.containsKey(senderAddress)) {
                        GameConfig gameConfig = announcementMsg.getAnnouncement().getConfig();
                        gamesMap.put(senderAddress, gameId);

                        configMap.put(gameId, gameConfig);

                        DefaultTableModel dtm = (DefaultTableModel) gamesTable.getModel();

                        dtm.addRow(new Object[]{
                            senderAddress,
                            gameId,
                            gameConfig.getWidth() + "X" + gameConfig.getWidth(),
                            gameConfig.getFoodStatic() + "+" + gameConfig.getFoodPerPlayer() + "x",
                            new JButton("=>")
                        });

                        gameId++;
                    } else {
                        for (int id : gamesMap.values()) {
                            if (gamesMap.get(senderAddress) == id) {
                                lastMsgTime.put(id, System.currentTimeMillis());
                            }
                        }
                    }

                } catch (SocketTimeoutException ignored) {
                }
                checkTime();
            }
        } catch (SocketException ex) {
            Logger.getLogger(Controller.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Controller.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void checkTime() {
        DefaultTableModel dtm = (DefaultTableModel) gamesTable.getModel();
        lastMsgTime.forEach((id, time) -> {
            if (System.currentTimeMillis() - time > 1500) {
                for (int i = 0; i < dtm.getRowCount(); i++) {
                    if (dtm.getValueAt(i, 1).equals(id)) {
                        dtm.removeRow(i);
                    }
                    configMap.remove(id);
                }
            }
        });
    }

    public SnakesProto.GameConfig getGameConfig(int id) {
        return configMap.get(id);
    }

    public InetSocketAddress getSocketAddress(int id) {
        for (Entry<InetSocketAddress, Integer> entry : gamesMap.entrySet()) {
            if (Objects.equals(id, entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    public void exit() {
        this.interrupt();
        try {
            this.join();
            announcemenSocket.close();
        } catch (InterruptedException ex) {
            Logger.getLogger(AnnouncementReciever.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
