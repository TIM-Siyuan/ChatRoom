package client.nio;

import client.nio.UserInputHandler;
import server.nio.ChatServer;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Set;

public class ChatClient {
    private static final String DEFAULT_SERVER_HOST = "127.0.0.1";
    private static final int DEFAULT_SERVER_PORT = 8888;
    private static final String QUIT = "quit";
    private static final int BUFFER = 1024;

    private String host;
    private int port;
    private SocketChannel client;
    private ByteBuffer rBuffer = ByteBuffer.allocate(BUFFER);
    private ByteBuffer wBuffer = ByteBuffer.allocate(BUFFER);
    private Selector selector;
    private Charset charset = Charset.forName("UTF-8");

    public ChatClient(){
        this(DEFAULT_SERVER_HOST, DEFAULT_SERVER_PORT);
    }

    public ChatClient(String host, int port){
        this.host = host;
        this.port = port;
    }

    private void start(){
        try {
            client = SocketChannel.open();
            client.configureBlocking(false);

            selector = Selector.open();
            client.register(selector, SelectionKey.OP_CONNECT);
            client.connect(new InetSocketAddress(host, port));

            while (true){
                selector.select();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                for(SelectionKey key : selectionKeys){
                    handles(key);
                }
                selectionKeys.clear();
            }
        } catch (IOException e){
            e.printStackTrace();
        } catch (ClosedSelectorException e){
            //用户正常退出关闭了selector, 但是while loop还在继续, 所以会出现java.nio.channels.ClosedSelectorException, catch即可
        } finally {
            close(selector);
        }
    }

    private void handles(SelectionKey key) throws IOException {
        // CONNECT事件 - 连接就绪事件
        if(key.isConnectable()){
            SocketChannel client = (SocketChannel) key.channel();
            if(client.isConnectionPending()){
                client.finishConnect();
                //处理用户的输入
                new Thread(new UserInputHandler(this)).start();
            }
            client.register(selector, SelectionKey.OP_READ);
        }
        // READ事件 - 服务器转发事件
        else if(key.isReadable()){
            SocketChannel client = (SocketChannel) key.channel();
            String msg = receive(client);
            if(msg.isEmpty()){
                //服务器异常
                close(selector);
                System.out.println("服务器异常");
            }else {
                System.out.println(msg);
            }
        }
    }

    private String receive(SocketChannel client) throws IOException {
        rBuffer.clear();
        while (client.read(rBuffer) > 0){client.read(rBuffer);};
        rBuffer.flip();
        return String.valueOf(charset.decode(rBuffer));
    }

    //UserInputHandler转发信息

    public void send(String msg) throws IOException {
        if(msg.isEmpty()){
            return;
        }
        wBuffer.clear();
        wBuffer.put(charset.encode(msg));
        //write -> read;
        wBuffer.flip();
        while (wBuffer.hasRemaining()){
            client.write(wBuffer);
        }

        if(readyToQuit(msg)){
            close(selector);
        }
    }

    public boolean readyToQuit(String msg){
        return QUIT.equals(msg);
    }

    private void close(Closeable closeable){
        if(closeable != null){
            try {
                closeable.close();
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args){
        ChatClient client = new ChatClient("127.0.0.1", 7777);
        client.start();
    }
}
