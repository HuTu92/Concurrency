package cn.archessay.concurrent.server;

import cn.archessay.concurrent.pool.DefaultThreadPool;
import cn.archessay.concurrent.pool.ThreadPool;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by liuchunlong on 2018/7/29.
 */
public class SimpleHttpServer {

    // 处理HttpRequest的线程池
    private static ThreadPool<HttpRequestHandler> threadPool =
            new DefaultThreadPool<HttpRequestHandler>(1);

    // SimpleHttpServer的根路径
    private static String basePath;
    private static ServerSocket serverSocket;

    // 服务监听端口
    private static int port = 8080;

    public static void setPort(int port) {
        if (port > 0) {
            SimpleHttpServer.port = port;
        }
    }

    public static void setBasePath(String basePath) {
        if (basePath != null
                && new File(basePath).exists()
                && new File(basePath).isDirectory()) {
            SimpleHttpServer.basePath = basePath;
        }
    }

    // 启动SimpleHttpServer
    public static void start() throws IOException {
        serverSocket = new ServerSocket(port);
        Socket socket = null;
        while ((socket = serverSocket.accept()) != null) {
            // 接收到一个客户端的Socket，生成一个HttpRequestHandler，放入线程池中执行
            threadPool.execute(new HttpRequestHandler(socket));
        }

        serverSocket.close();
    }

    static class HttpRequestHandler implements Runnable {

        private Socket socket;

        public HttpRequestHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {

            BufferedReader reader = null; // socket 输入流
            PrintWriter out = null; // socket 输出流

            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String header = reader.readLine();
                // GET /index.html HTTP/1.0
                // 由相对路径计算出请求资源的绝对路径
                String filePath = basePath + header.split(" ")[1];

                out = new PrintWriter(socket.getOutputStream());

                // 如果请求资源的后缀为jpg或者ico，则读取资源并输出
                if (filePath.endsWith("jpg") || filePath.endsWith("ico")) {
                    InputStream in = new FileInputStream(filePath);
                    // 将文件输入流写入到ByteArrayOutputStream，以获取文件的字节数组
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int i = 0;
                    while ((i = in.read()) != -1) {
                        baos.write(i);
                    }
                    byte[] array = baos.toByteArray();

                    out.println("HTTP/1.1 200 OK");
                    out.println("Server: Molly");
                    out.println("Content-Type: image/jpeg");
                    out.println("Content-Length: " + array.length);
                    out.println("");

                    socket.getOutputStream().write(array);
                } else {
                    BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(filePath)));

                    out.println("HTTP/1.1 200 OK");
                    out.println("Server: Molly");
                    out.println("Content-Type: text/html; charset=UTF-8");
                    out.println("");

                    String line = null;
                    while ((line = in.readLine()) != null) {
                        out.println(line);
                    }
                }

                out.flush();
            } catch (IOException e) {
                out.println("HTTP/1.1 500");
                out.println();
                out.flush();
            } finally {
                close(reader, out, socket);
            }
        }
    }

    private static void close(Closeable ... closeables) {
        if (closeables != null) {
            for (Closeable closeable: closeables) {
                try {
                    closeable.close();
                } catch (Exception e) {
                }
            }
        }
    }
}
