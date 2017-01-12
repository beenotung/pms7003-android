package com.github.beenotung.pms7003_android;
/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.res.AssetManager;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * Implementation of a very basic HTTP server. The contents are loaded from the assets folder. This
 * server handles one request at a time. It only supports GET method.
 */
public class SimpleWebServer implements Runnable {
    final MainActivity.DataLogger dataLogger;
    private static final String TAG = "SimpleWebServer";

    /**
     * The port number we listen to
     */
    private final int mPort;

    /**
     * {@link android.content.res.AssetManager} for loading files to serve.
     */
    private final AssetManager mAssets;

    /**
     * True if the server is running.
     */
    private boolean mIsRunning;

    /**
     * The {@link java.net.ServerSocket} that we listen to.
     */
    private ServerSocket mServerSocket;

    /**
     * WebServer constructor.
     */
    public SimpleWebServer(int port, AssetManager assets, MainActivity.DataLogger dataLogger) {
        mPort = port;
        mAssets = assets;
        this.dataLogger = dataLogger;
    }

    /**
     * This method starts the web server listening to the specified port.
     */
    public void start() {
        mIsRunning = true;
        new Thread(this).start();
    }

    /**
     * This method stops the web server
     */
    public void stop() {
        try {
            mIsRunning = false;
            if (null != mServerSocket) {
                mServerSocket.close();
                mServerSocket = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing the server socket.", e);
        }
    }

    @Override
    public void run() {
        try {
            mServerSocket = new ServerSocket(mPort);
            while (mIsRunning) {
                final Socket socket = mServerSocket.accept();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            handle(socket);
                            socket.close();
                        } catch (IOException e) {
//                            e.printStackTrace();
                            Log.e(TAG, "Web server error.", e);
                        }
                    }
                }).start();
            }
        } catch (SocketException e) {
            // The server was stopped; ignore.
//            Log.i(TAG, "Web server stopped", e);
            Log.i(TAG, "Web server stopped");
        } catch (IOException e) {
            Log.e(TAG, "Web server error.", e);
        }
    }

    public Data data = new Data();

    public static class Data {
        public int roundId;
    }

    /**
     * Respond to a request from a client.
     *
     * @param socket The client socket.
     * @throws IOException
     */
    private void handle(Socket socket) throws IOException {
        for (J8.Consumer<Socket> cb : connectCbs) {
            cb.apply(socket);
        }
        BufferedReader reader = null;
        PrintStream output = null;
        try {
            String route = null;

            // Read HTTP headers and parse out the route.
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line;
            while (!TextUtils.isEmpty(line = reader.readLine())) {
                Log.i(TAG, "readLine: " + line);
                if (line.startsWith("GET /")) {
                    int start = line.indexOf('/') + 1;
                    int end = line.indexOf(' ', start);
                    route = line.substring(start, end);
                    break;
                }
            }
            for (J8.Consumer<Socket> cb : receiveCbs) {
                cb.apply(socket);
            }

            // Output stream that we send the response to
            output = new PrintStream(socket.getOutputStream());

            // Prepare the content to send.
            if (null == route) {
                writeServerError(output);
                return;
            }
            byte[] bytes = loadContent(route);
            if (null == bytes) {
                writeServerError(output);
                return;
            }

            // Send out the content.
            output.println("HTTP/1.0 200 OK");
            output.println("Content-Type: " + detectMimeType(route));
            output.println("Content-Length: " + bytes.length);
            output.println();
            output.write(bytes);
            output.flush();
        } finally {
            if (null != output) {
                output.close();
            }
            if (null != reader) {
                reader.close();
            }
        }
    }

    /**
     * Writes a server error response (HTTP/1.0 500) to the given output stream.
     *
     * @param output The output stream.
     */
    private void writeServerError(PrintStream output) {
        output.println("HTTP/1.0 500 Internal Server Error");
        output.flush();
    }

    Pattern reportPattern = Pattern.compile("^report\\?.+=.+");

    /**
     * Loads all the content of {@code fileName}.
     *
     * @param fileName The name of the file.
     * @return The content of the file.
     * @throws IOException
     */
    private byte[] loadContent(String fileName) throws IOException {
        Log.i(TAG, "loadContent: " + fileName);
        if (fileName.equals("info"))
            return "<Info from android server>".getBytes();
        else if (fileName.contains("report?")) {
            String[] ss = fileName.split("\\?");
            if (ss.length != 2) {
                return "<error,\"no param\">".getBytes();
            }
            ss = ss[1].split("=");
            if (ss.length != 2) {
                return "<error,\"no param value\">".getBytes();
            }
            String key = ss[0];
            String value = ss[1];
            Log.i(TAG, "request " + key + "=" + value);
            if (key.equals("roundId")) {
                dataLogger.writeToFile("roundId=" + data.roundId);
                data.roundId = Integer.parseInt(value);
                return "<ok>".getBytes();
            }
            return "<error,\"unknown param\">".getBytes();
        }
        InputStream input = null;
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            input = mAssets.open(fileName);
            byte[] buffer = new byte[1024];
            int size;
            while (-1 != (size = input.read(buffer))) {
                output.write(buffer, 0, size);
            }
            output.flush();
            return output.toByteArray();
        } catch (FileNotFoundException e) {
            Log.e(TAG, "loadContent: ", e);
            return null;
        } finally {
            if (null != input) {
                input.close();
            }
        }
    }

    /**
     * Detects the MIME type from the {@code fileName}.
     *
     * @param fileName The name of the file.
     * @return A MIME type.
     */
    private String detectMimeType(String fileName) {
        Log.i(TAG, "detectMimeType: " + fileName);
        if (fileName.equals("info")) {
            return "text/plain";
        } else if (TextUtils.isEmpty(fileName)) {
            return null;
        } else if (fileName.endsWith(".html")) {
            return "text/html";
        } else if (fileName.endsWith(".js")) {
            return "application/javascript";
        } else if (fileName.endsWith(".css")) {
            return "text/css";
        } else {
            return "application/octet-stream";
        }
    }

    ArrayList<J8.Consumer<Socket>> connectCbs = new ArrayList<>();
    ArrayList<J8.Consumer<Socket>> receiveCbs = new ArrayList<>();

    void onConnected(J8.Consumer<Socket> cb) {
        connectCbs.add(cb);
    }

    void onReceived(J8.Consumer<Socket> cb) {
        receiveCbs.add(cb);
    }
}
