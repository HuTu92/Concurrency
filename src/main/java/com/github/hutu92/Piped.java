package com.github.hutu92;

import java.io.IOException;
import java.io.PipedReader;
import java.io.PipedWriter;

/**
 * Created by liuchunlong on 2018/7/17.
 * <p>
 * 管道输入/输出流
 */
public class Piped {

    public static void main(String[] args) throws IOException {
        PipedReader reader = new PipedReader();
        PipedWriter writer = new PipedWriter();
        reader.connect(writer);

        Thread printThread = new Thread(new Print(reader));
        printThread.start();

        int receive = 0;

        while ((receive = System.in.read()) != -1) {
            writer.write(receive);
        }
    }

    private static class Print implements Runnable {

        private PipedReader reader;

        public Print(PipedReader reader) {
            this.reader = reader;
        }

        public void run() {

            int receive = 0;

            try {
                // 从此管道流中读取下一个数据字符。如果由于到达流末尾而没有可用字符，则返回值-1。
                // 此方法将阻塞，直到输入数据可用、检测到流的末尾或抛出异常。
                while ((receive = reader.read()) != -1) {
                    System.out.println((char) receive);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }
}
