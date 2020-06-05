package client.single;

import java.io.*;
import java.net.Socket;

public class Client {
    public static void main(String[] args){
        final String QUIT = "quit";
        final String DEFAULT_SERVER_HOST = "127.0.0.1";
        final int DEFAULT_SERVER_PORT = 8888;
        Socket socket = null;
        BufferedWriter writer = null;
        try{
            //创建socket
            System.out.println("客户端已建立, 请输入消息:");
            socket = new Socket(DEFAULT_SERVER_HOST, DEFAULT_SERVER_PORT);
            //创建IO流
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            //创建等待输入流
            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
            //循环等待, 传输消息, 直到客户端主动退出
            while (true){
                String input = consoleReader.readLine();
                //发送消息
                writer.write(input + "\n");
                writer.flush();
                //接收回信
                String msg = reader.readLine();
                System.out.println(msg);

                //客户端退出
                if(QUIT.equals(input)){
                    break;
                }
            }
        }catch (IOException e){
            e.printStackTrace();
        }finally {
            if(writer != null){
                try {
                    //先关闭关闭输出流
                    writer.close();
                    //再关闭socket
                    socket.close();
                    System.out.println("客户端关闭");
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }
    }
}

