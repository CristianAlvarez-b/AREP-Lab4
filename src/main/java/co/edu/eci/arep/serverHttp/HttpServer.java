package co.edu.eci.arep.serverHttp;

import co.edu.eci.arep.appsvr.EciBoot;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public class HttpServer {

    static String staticFilesLocation = "public";
    static Map<String, BiFunction<Request, Response, String>> routes = new HashMap<>();
    private static ServerSocket serverSocket;
    private static boolean running = true;

    public static void staticfiles(String location) {
        staticFilesLocation = location;
    }

    public static void get(String path, BiFunction<Request, Response, String> handler) {
        routes.put(path, handler);
    }

    public static void start(String[] args) throws IOException {
        int port = getPort();
        serverSocket = new ServerSocket(port);
        EciBoot.loadComponents();
        System.out.println("Servidor iniciado en el puerto " + port + "...");

        // Hook de apagado elegante
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                System.out.println("Apagando servidor...");
                running = false;
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
                System.out.println("Servidor apagado correctamente.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            } catch (IOException e) {
                if (!running) {
                    System.out.println("Servidor detenido.");
                } else {
                    e.printStackTrace();
                }
            }
        }
    }

    private static int getPort() {
        if (System.getenv("PORT") != null) {
            return Integer.parseInt(System.getenv("PORT"));
        }
        return 35000; // Puerto por defecto
    }

    private static void handleClient(Socket clientSocket) {
        try (
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))
        ) {
            String inputLine, filePath = "", method = "";
            boolean isFirstLine = true;

            while ((inputLine = in.readLine()) != null) {
                if (isFirstLine) {
                    String[] requestParts = inputLine.split(" ");
                    method = requestParts[0];
                    filePath = requestParts[1];
                    isFirstLine = false;
                }
                if (!in.ready()) break;
            }

            Request req = new Request(filePath, method, in);
            Response res = new Response(out, clientSocket);
            String cleanPath = req.getPath().split("\\?")[0];

            if (cleanPath.startsWith("/app/")) {
                String response = EciBoot.handleRequest(cleanPath, req.getQueryParams());
                res.send(response);
            } else {
                handleStaticFileRequest(cleanPath, out, clientSocket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static void handleStaticFileRequest(String filePath, PrintWriter out, Socket clientSocket) {
        if (filePath.equals("/")) {
            filePath = "/index.html";
        }

        File file = new File(staticFilesLocation + filePath);

        if (file.exists() && !file.isDirectory()) {
            try {
                String mimeType = getMimeType(filePath);
                byte[] fileContent = Files.readAllBytes(file.toPath());

                out.println("HTTP/1.1 200 OK");
                out.println("Content-Type: " + mimeType);
                out.println("Content-Length: " + fileContent.length);
                out.println();
                out.flush();
                clientSocket.getOutputStream().write(fileContent);
                clientSocket.getOutputStream().flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            out.println("HTTP/1.1 404 Not Found");
            out.println("Content-Type: text/html");
            out.println();
            out.println("<html><body><h1>404 Not Found</h1></body></html>");
        }
    }

    static String getMimeType(String filePath) {
        if (filePath.endsWith(".html")) return "text/html";
        if (filePath.endsWith(".css")) return "text/css";
        if (filePath.endsWith(".js")) return "application/javascript";
        if (filePath.endsWith(".png")) return "image/png";
        if (filePath.endsWith(".jpg") || filePath.endsWith(".jpeg")) return "image/jpeg";
        if (filePath.endsWith(".gif")) return "image/gif";
        return "application/octet-stream";
    }
}
