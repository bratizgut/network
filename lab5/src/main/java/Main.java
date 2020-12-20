

import proxy.Server;

import java.io.IOException;

public class Main {

    public static void main(String[] args) {
        if (args.length == 1) {
            try {
                int port = Integer.parseInt(args[0]);
                if (port > 0 && port <= 65535) {
                    try {
                        Server server = new Server(port);
                        server.start();
                    } catch (IOException e) {
                        System.err.println(e.getMessage());
                    }
                } else {
                    System.err.println("Port must be in range of [0, 65535");
                }
            } catch (NumberFormatException ex) {
                System.err.println("Required a number");
            }
        } else {
            System.err.println("Need 1 argument: port");
        }

    }
}
