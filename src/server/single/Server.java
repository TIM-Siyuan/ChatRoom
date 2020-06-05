package server.single;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Server{
    public static void main(String[] args){
        final String QUIT = "quit";
        final int DEFALUT_PORT = 8888;
        ServerSocket serverSocket = null;

        try{
            //创建serverSocket并监听端口
            serverSocket = new ServerSocket(DEFALUT_PORT);
            System.out.println("启动服务器, 监听端口:" + DEFALUT_PORT);
            //等待客户端连接(阻塞式)
            Socket socket = serverSocket.accept();
            System.out.println("客户端" + socket.getPort() + "已连接");
            //创建IO流(装饰器模式)
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            //读取信息
            String msg = null;
            while ((msg = reader.readLine()) != null){
                System.out.println(socket.getPort() + ": " + msg);
                //回复客户端发送的消息
                writer.write("服务器收到" + socket.getPort() + "的消息:" + msg + "\n");
                writer.flush();

                if(QUIT.equals(msg)){
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(serverSocket != null){
                try{
                    System.out.println("服务器关闭");
                    serverSocket.close();
                } catch (IOException e){
                    e.printStackTrace();
                }
            }
        }
    }
}


