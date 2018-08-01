package com.github.hutu92.server;

import java.io.IOException;

/**
 * Created by liuchunlong on 2018/7/30.
 */
public class Main {

    public static void main(String[] args) throws IOException {
        SimpleHttpServer.setBasePath(System.getProperty("user.dir"));
        SimpleHttpServer.setPort(8080);
        SimpleHttpServer.start();
    }
}
