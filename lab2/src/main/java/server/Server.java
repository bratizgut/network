package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author bratizgut
 */
public class Server implements AutoCloseable {

    private final ServerSocket serverSocket;
    private Socket socket;
    private final Thread mainThread;

    public Server(int port) throws UnknownHostException, IOException {
        serverSocket = new ServerSocket(port);
        mainThread = Thread.currentThread();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    serverSocket.close();
                    mainThread.join();
                } catch (InterruptedException | IOException ex) {
                    Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
    }

    public void start() {
        try {
            while (true) {
                socket = serverSocket.accept();
                try {
                    RecievingThread recievingThread = new RecievingThread(socket);
                    recievingThread.start();
                } catch (IOException ex) {
                    Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void close() throws IOException {
        if (!serverSocket.isClosed()) {
            serverSocket.close();
        }
    }

    private class RecievingThread extends Thread {

        private final Socket socket;
        private final DataInputStream inputStream;
        private final DataOutputStream outputStream;

        private File outFile;
        private final String ThreadName = this.getName();

        private final Thread thisThread;

        public RecievingThread(Socket socket) throws IOException {
            this.socket = socket;
            inputStream = new DataInputStream(socket.getInputStream());
            outputStream = new DataOutputStream(socket.getOutputStream());

            thisThread = Thread.currentThread();

            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    try {
                        close();
                        thisThread.join();
                    } catch (IOException | InterruptedException ex) {
                        Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            });
        }

        private void createFile(String fileName) throws IOException {
            outFile = new File("uploads//" + fileName);
            if (outFile.exists()) {
                int n = 0;
                while (outFile.exists()) {
                    outFile = new File("uploads//" + n + "_" + fileName);
                    n++;
                }
            }
            outFile.createNewFile();
        }

        @Override
        public void run() {
            try {
                byte[] buffer = new byte[4096];

                String fileName = inputStream.readUTF();
                outputStream.writeUTF(fileName);
                outputStream.flush();
                if (!inputStream.readBoolean()) {
                    Logger.getLogger(Server.class.getName()).log(Level.SEVERE, "File name does not equal.");
                    this.close();
                    return;
                }

                long fileLength = inputStream.readLong();
                outputStream.writeLong(fileLength);
                outputStream.flush();
                if (!inputStream.readBoolean()) {
                    Logger.getLogger(Server.class.getName()).log(Level.SEVERE, "File length does not equal.");
                    this.close();
                    return;
                }

                createFile(fileName);
                FileOutputStream fileOutputStream = new FileOutputStream(outFile);
                MessageDigest md = MessageDigest.getInstance("MD5");
                DigestOutputStream digestOutputStream = new DigestOutputStream(fileOutputStream, md);

                long startTime = System.currentTimeMillis();
                long prevTime = startTime;
                int n = 1;
                long bytesRecieved = 0;
                while (bytesRecieved < fileLength) {
                    int count = inputStream.read(buffer);
                    digestOutputStream.write(buffer, 0, count);
                    bytesRecieved += count;
                    long currTime = System.currentTimeMillis();
                    if (currTime - prevTime >= 3000) {
                        System.out.println(ThreadName + " " + bytesRecieved / ((currTime - prevTime) * n) + "Kb / s.");
                        prevTime = currTime;
                        n++;
                    }
                }

                byte[] hash = digestOutputStream.getMessageDigest().digest();
                outputStream.writeInt(hash.length);
                outputStream.write(hash, 0, hash.length);
                if (!inputStream.readBoolean()) {
                    Logger.getLogger(Server.class.getName()).log(Level.SEVERE, "Hash code does not equal.");
                    this.close();
                    return;
                }
                System.out.println(ThreadName + " total speed: "
                        + bytesRecieved / (System.currentTimeMillis() - startTime) + "Kb / s.");
            } catch (IOException | NoSuchAlgorithmException ex) {
                Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                try {
                    this.close();
                } catch (IOException ex1) {
                    Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex1);
                }
            }
        }

        public void close() throws IOException {
            if (!socket.isClosed()) {
                socket.close();
            }
        }

    }

}
