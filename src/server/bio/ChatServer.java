package server.bio;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class ChatServer {
    private int DEFAULT_PORT = 8888;
    private final String QUIT = "quit";

    private ExecutorService executorService;
    private ServerSocket serverSocket = null;
    //建立客户列表 ——> 后续转发信息使用
    private Map<Integer, Writer> connectedClients;

    public ChatServer(){
        executorService = Executors.newFixedThreadPool(3);
        connectedClients = new HashMap<>();
    }

    //线程安全
    public synchronized void addClient(Socket socket) throws IOException{
        if(socket != null) {
            int port = socket.getPort();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            connectedClients.put(port, writer);
            System.out.println("客户端[" + port + "]已连接");
        }
    }

    public synchronized void removeClient(Socket socket) throws IOException{
        if(socket != null){
            int port = socket.getPort();
            if(connectedClients.containsKey(port)){
                connectedClients.get(port).close();
            }
            connectedClients.remove(port);
            System.out.println("客户端[" + port + "]已断开连接");
        }
    }

    public synchronized void forwardMessage(Socket socket, String fwdMsg) throws IOException{
        for(Integer id : connectedClients.keySet()){
            if(!id.equals(socket.getPort())){
                Writer writer = connectedClients.get(id);
                writer.write(fwdMsg);
                writer.flush();
            }
        }
    }

    public boolean readyToQuit(String msg){
        return QUIT.equals(msg);
    }

    public synchronized void close(){
        if(serverSocket != null){
            try {
                serverSocket.close();
                System.out.println("关闭serverSocket");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void start(){
        //绑定监听端口;
        try{
            serverSocket = new ServerSocket(DEFAULT_PORT);
            System.out.println("启动服务器, 监听端口:" + DEFAULT_PORT);

            while (true){
                //等待客户端连接
                Socket socket = serverSocket.accept();
                //伪异步聊天室 线程池
                executorService.execute(new ChatHandler(this, socket));
                //创建ChatHandler线程
                //new Thread(new ChatHandler(this, socket)).start();
            }
        } catch (IOException e){
            e.printStackTrace();
        } finally {
            close();
        }
    }

    public static void main(String[] args){
        ChatServer chatServer = new ChatServer();
        chatServer.start();
    }
}
