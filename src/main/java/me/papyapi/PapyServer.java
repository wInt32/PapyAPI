package me.papyapi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Scanner;

public class PapyServer implements Runnable {

    private final PapyRequestDispatcher dispatcher;
    private final Thread serverThread;

    public final InetSocketAddress bindAddress;

    public String state = "";

    public PapyServer(String addr, int port, PapyRequestDispatcher requestDispatcher) {
        this.dispatcher = requestDispatcher;
        this.bindAddress = new InetSocketAddress(addr, port);
        this.serverThread = new Thread(this, "PapyServer");
        dispatcher.log_info("Starting PapyServer on " + addr + ":" + port);
        this.serverThread.start();
        this.state = "Starting";
    }

    public PapyServer(InetSocketAddress address, PapyRequestDispatcher requestDispatcher) {
        this.dispatcher = requestDispatcher;
        this.bindAddress = address;
        this.serverThread = new Thread(this, "PapyServer");
        dispatcher.log_info("Starting PapyServer on " + address.toString());
        this.serverThread.start();
        this.state = "Starting";
    }

    private Socket clientSocket;
    @Override
    public void run() {

        try (ServerSocket serverSocket = new ServerSocket()) {

            // Let's do some recycling :)
            serverSocket.setReuseAddress(true);

            // We must reuse the port too or "Address already in use" will haunt us forever
            serverSocket.setOption(StandardSocketOptions.SO_REUSEPORT, true);
            serverSocket.bind(bindAddress, 1);

            clientSocket = new Socket();

            boolean connected = false;

            while (!Thread.currentThread().isInterrupted()) {

                // Accept new connections
                if (!clientSocket.isConnected() || clientSocket.isClosed())
                    clientSocket = serverSocket.accept();

                InputStream inputStream = clientSocket.getInputStream();
                OutputStream outputStream = clientSocket.getOutputStream();

                if (!connected) {
                    this.state = "Waiting for handshake";
                    // We don't want any unwanted guests
                    byte[] handshake = "PapyAPI\n".getBytes(StandardCharsets.US_ASCII);
                    byte[] received = inputStream.readNBytes(8);

                    if (!Arrays.equals(handshake, received)) {
                        dispatcher.log_warn("Did not receive handshake, received this:");
                        dispatcher.log_warn(Arrays.toString(received));
                        dispatcher.log_warn("instead of:");
                        dispatcher.log_warn(Arrays.toString(handshake));
                        dispatcher.log_warn("Closing socket,,,");
                        clientSocket.close();
                        continue;
                    }

                    outputStream.write("SUCCESS\n".getBytes(StandardCharsets.US_ASCII));
                    connected = true;
                }


                // Read request length
                this.state = "Reading request length";
                byte[] buffer = inputStream.readNBytes(2);
                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
                short length = byteBuffer.getShort();

                // Read whole request
                String requestBody = "";

                this.state = "Reading request";
                try {
                    requestBody = new String(inputStream.readNBytes(length), StandardCharsets.US_ASCII);
                } catch (Exception e) {
                    connected = false;
                    clientSocket.close();
                    continue;
                }

                PapyRequest request = new PapyRequest(requestBody);

                this.state = "Dispatching request";
                PapyResponse response = dispatcher.dispatch(request);

                if (response.success) {
                    outputStream.write("SUCCESS\n".getBytes(StandardCharsets.US_ASCII));
                } else {
                    outputStream.write("FAILURE\n".getBytes(StandardCharsets.US_ASCII));
                }

                outputStream.write((response.message+'\n').getBytes(StandardCharsets.US_ASCII));

                if (response.close_connection) {
                    connected = false;
                    clientSocket.close();
                    continue;
                }


            }

            clientSocket.close();

            // NOTE: One would think that we need to somehow close our sockets or something
            // but since I have absolutely no idea how to execute any code placed under this,
            // let's just ignore it and hope SO_REUSEADDR and SO_REUSEPORT will save us.

        } catch (IOException ignore) {}

    }

    public void stop() {

        serverThread.interrupt();

        try {
            clientSocket.close();
        } catch (IOException ignore) {}

    }

}
