package server.bio;

import java.io.*;
import java.net.Socket;

public class ChatHandler implements Runnable{
    private ChatServer server;
    private Socket socket;

    public ChatHandler(ChatServer chatServer, Socket socket){
        this.server = chatServer;
        this.socket = socket;
    }

    @Override
    public void run() {
        try{
            //存储新上线用户
            server.addClient(socket);
            //读取用户发送的消息
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String msg = null;
            while ((msg = reader.readLine()) != null){
                String fwdMsg = "客户端[" + socket.getPort() + "]:" + msg + "\n";
                System.out.println(fwdMsg);
                //将消息转发给聊天室的其他用户
                server.forwardMessage(socket, fwdMsg);
                if(server.readyToQuit(msg)){
                    break;
                }
            }
        } catch (IOException e){
            e.printStackTrace();
        } finally {
            try {
                server.removeClient(socket);
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }

}

