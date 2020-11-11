package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author bratizgut
 */
public class Client implements AutoCloseable {

    private final Socket socket;
    private final DataOutputStream outputStream;
    private final DataInputStream inputStream;

    private DigestInputStream digestInputStream;

    private final Thread mainThread;

    public Client(String IPaddress, int port) throws UnknownHostException, IOException {
        socket = new Socket(InetAddress.getByName(IPaddress), port);
        outputStream = new DataOutputStream(socket.getOutputStream());
        inputStream = new DataInputStream(socket.getInputStream());
        mainThread = Thread.currentThread();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    socket.close();
                    if (digestInputStream != null) {
                        digestInputStream.close();
                    }
                    mainThread.join();
                } catch (InterruptedException | IOException ex) {
                    Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

        });
    }

    public void sendFile(String pathname) throws IOException, NoSuchAlgorithmException {
        File file = new File(pathname);
        if(!file.exists()) {
            throw new FileNotFoundException();
        }

        outputStream.writeUTF(file.getName());
        outputStream.writeBoolean(file.getName().equals(inputStream.readUTF()));
        outputStream.flush();

        outputStream.writeLong(file.length());
        outputStream.writeBoolean(file.length() == inputStream.readLong());
        outputStream.flush();

        byte[] buffer = new byte[4096];
        FileInputStream fileInputStream = new FileInputStream(file);
        MessageDigest md;
        md = MessageDigest.getInstance("MD5");
        digestInputStream = new DigestInputStream(fileInputStream, md);

        int count;
        while ((count = digestInputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, count);
            outputStream.flush();
        }
        int hashLength = inputStream.readInt();
        byte[] hash = new byte[hashLength];
        inputStream.read(hash, 0, hashLength);
        outputStream.writeBoolean(Arrays.equals(digestInputStream.getMessageDigest().digest(), hash));

        System.out.println("job done");
    }

    @Override
    public void close() throws IOException {
        if (!socket.isClosed()) {
            socket.close();
        }
        if (digestInputStream != null) {
            digestInputStream.close();
        }
    }

}
