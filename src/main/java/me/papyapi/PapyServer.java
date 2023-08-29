package me.papyapi;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.net.*;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class PapyServer implements Runnable {

    private final PapyRequestDispatcher dispatcher;

    public final Thread serverThread;
    public Thread dispatcherThread;
    public Thread responseThread;

    private final BlockingQueue<PapyResponse> responseQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<PapyRequest> requestQueue = new LinkedBlockingQueue<>();

    public final InetSocketAddress bindAddress;

    public String state = "";

    public PapyServer(String addr, int port, PapyRequestDispatcher requestDispatcher) {
        this(new InetSocketAddress(addr, port), requestDispatcher);
    }

    public PapyServer(InetSocketAddress address, PapyRequestDispatcher requestDispatcher) {
        this.dispatcher = requestDispatcher;
        this.bindAddress = address;
        this.serverThread = new Thread(this, "PapyServer");
        dispatcher.log_info("PapyServer listening on " + address.toString());
        this.serverThread.start();
        this.state = "Starting";
        this.dispatcherThread = createDispatcherThread(requestDispatcher);
        this.dispatcherThread.start();
    }

    private Thread createDispatcherThread(PapyRequestDispatcher dispatcher) {
        return new Thread(() -> {
            dispatcher.log_debug("Starting dispatcher thread!");
            while (!Thread.interrupted()) {
                try {
                    PapyRequest request = requestQueue.take();

                    state = "Dispatching request";


                    PapyResponse response = null;
                    try {
                        response = dispatcher.dispatch(request);
                    } catch (InterruptedException e) {
                        response = new PapyResponse(false, "Interrupted");
                    }

                    responseQueue.put(response);

                } catch (InterruptedException e) {
                    break;
                }
            }
            dispatcher.log_debug("Stopping dispatcher thread!");
        }, "PapyDispatcher");
    }

    private Thread createResponseThread(PapyRequestDispatcher dispatcher, OutputStream outputStream) {
        return new Thread(() -> {
            dispatcher.log_debug("Starting response thread!");
            final String successStatus = "SUCCESS";
            final String failureStatus = "FAILURE";
            while (!Thread.interrupted()) {
                try {
                    PapyResponse response = responseQueue.take();

                    StringBuilder responseText = new StringBuilder();

                    if (response.success)
                        responseText.append(successStatus);
                    else
                        responseText.append(failureStatus);

                    responseText.append('\n');
                    responseText.append(response.message);
                    responseText.append('\n');

                    int totalLength = responseText.length() + 4;

                    byte[] lengthBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(totalLength).array();
                    dispatcher.log_debug("Sending: " + Arrays.toString(lengthBytes) + Arrays.toString((responseText.toString().getBytes(StandardCharsets.US_ASCII))));
                    outputStream.write(lengthBytes);
                    outputStream.write(responseText.toString().getBytes(StandardCharsets.US_ASCII));

                    if (response.close_connection) {
                        dispatcher.log_debug("Client disconnected.");
                        return;
                    }
                } catch (InterruptedException | SocketException ex) {
                    try {
                        outputStream.close();
                    } catch (IOException ignore){}
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
            }
            dispatcher.log_debug("Stopping response thread!");
        }, "PapyResponseThread");
    }

    private Socket clientSocket = null;
    private ServerSocket serverSocket = null;
    private boolean isServerRunning = true;
    @Override
    public void run() {


        while (isServerRunning) {
            try {
                if (dispatcherThread == null || !dispatcherThread.isAlive()) {
                    dispatcherThread = createDispatcherThread(dispatcher);
                    dispatcherThread.start();
                }

                if (serverSocket != null)
                    serverSocket.close();
                serverSocket = new ServerSocket();

                // Let's do some recycling :)
                serverSocket.setReuseAddress(true);

                // We must reuse the port too or "Address already in use" will haunt us forever
                serverSocket.setOption(StandardSocketOptions.SO_REUSEPORT, true);
                serverSocket.setPerformancePreferences(0, 1, 0);
                serverSocket.bind(bindAddress, 1);
                //serverSocket.setReceiveBufferSize(512);

                AtomicBoolean hasTheHandBeenShaken = new AtomicBoolean(false);

                Thread responseThread = null;
                int request_count = 0;
                while (!Thread.interrupted()) {

                    this.state = "Waiting for client";
                    clientSocket = serverSocket.accept();
                    dispatcher.log_info("Client connected: " + clientSocket.getInetAddress());

                    // While the streams are flowing
                    while (!(clientSocket.isInputShutdown() || clientSocket.isInputShutdown())) {
                        InputStream is = clientSocket.getInputStream();
                        OutputStream os = clientSocket.getOutputStream();

                        // 40 ms delay? Unacceptable.
                        clientSocket.setTcpNoDelay(true);

                        if (responseThread == null || !responseThread.isAlive()) {
                            responseThread = createResponseThread(dispatcher, os);
                            responseThread.start();
                        }


                        // It's pretty obvious what this checks
                        if (!hasTheHandBeenShaken.get()) {
                            this.state = "Waiting for handshake";
                            byte[] handshake = "PapyAPI\n".getBytes(StandardCharsets.US_ASCII);
                            byte[] received = is.readNBytes(8);

                            if (!Arrays.equals(handshake, received)) {
                                dispatcher.log_debug("Did not receive handshake, received this:");
                                dispatcher.log_debug(Arrays.toString(received));
                                dispatcher.log_debug("instead of:");
                                dispatcher.log_debug(Arrays.toString(handshake));
                                break;
                            }

                            os.write("SUCCESS\n".getBytes(StandardCharsets.US_ASCII));
                            hasTheHandBeenShaken.set(true);
                        }

                        // Read request length
                        this.state = "Reading request length";
                        byte[] buffer = is.readNBytes(2);
                        if (buffer.length < 2)
                            break;

                        dispatcher.log_debug("Reading: " + Arrays.toString(buffer));

                        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
                        if (byteBuffer.array().length != 2)
                            break;

                        short length = 0;
                        length = byteBuffer.getShort();

                        // Read whole request
                        String requestBody = "";
                        this.state = "Reading request";
                        try {
                            requestBody = (new String(is.readNBytes(length), StandardCharsets.US_ASCII));
                        } catch (Exception e) {
                            e.printStackTrace();
                            dispatcher.log_error("HERE??");
                            break;
                        }

                        this.state = "Parsing and queuing request";
                        // Parse and queue the request
                        PapyRequest request = new PapyRequest(requestBody);
                        request.number = request_count;
                        request_count++;
                        requestQueue.put(request);

                    }
                    hasTheHandBeenShaken.set(false);
                    if (responseThread != null) {
                        responseThread.interrupt();
                        responseThread.join();
                    }
                    clientSocket.close();

                }
                _stop();

            } catch (SocketException e) {
                try {
                    _stop();
                } catch (InterruptedException ignore) {}
            } catch (Exception e) {
                e.printStackTrace();
                dispatcher.log_error("HERE!!");
            }
        }
    }

    private void _stop() throws InterruptedException {
        dispatcher.log_debug("Stopping threads...");
        dispatcherThread.interrupt();
        dispatcherThread.join();
        if (responseThread != null && responseThread.isAlive()) {
            responseThread.interrupt();
            responseThread.join();
        }
    }

    public void stop() {
        try {
            //serverThread.interrupt();
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            isServerRunning = false;
            serverThread.interrupt();


            dispatcher.log_info("Waiting for the server to stop...");
            serverThread.join();
            dispatcher.log_info("Server stopped.");

        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }

    }

}
