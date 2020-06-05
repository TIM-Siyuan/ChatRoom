package server.nio;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.Set;

public class ChatServer {
    private static final int DEFAULT_PORT = 8888;
    private static final String QUIT = "quit";
    private static final int BUFFER = 1024;

    private ServerSocketChannel server;
    private Selector selector;
    //通过一个线程selector监听不同的channel;

    private ByteBuffer rBuffer = ByteBuffer.allocate(BUFFER);
    //读入信息的缓冲区

    private ByteBuffer wBuffer = ByteBuffer.allocate(BUFFER);
    //转发给其他客户端的缓冲区

    private Charset charset = Charset.forName("UTF-8");
    //设定编码方式

    private int port;

    public ChatServer(){
        this(DEFAULT_PORT);
    }

    public ChatServer(int port){
        this.port = port;
    }

    private void start() {
        try{
            //初始化为阻塞式的
            server = ServerSocketChannel.open();
            //更改为非阻塞式
            server.configureBlocking(false);
            server.socket().bind(new InetSocketAddress(port));

            selector = Selector.open();
            server.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("启动服务器, 正在监听端口" + port);

            //假如无任何监听数据触发, selector阻塞, 所以需要while持续监听;
            while (true){
                selector.select();
                //获得监听到的事件(一个通道或多个通道); 相关事件已经被Selector捕获的SelectionKey集合
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                for(SelectionKey key : selectionKeys){
                    handles(key);
                }
                selectionKeys.clear();
            }

        } catch (IOException e){
            e.printStackTrace();
        } finally {
            /**
             * 每次迭代末尾的close()调用，Selector不会自己从已选择的SelectionKey集合中
             * 移除SelectionKey实例的，必须在处理完通道时自己移除
             */
             close(selector);
        }
    }

    private void handles(SelectionKey key) throws IOException{
        //ACCEPT事件 -- 和客户端简历了连接
        if(key.isAcceptable()){
            ServerSocketChannel server = (ServerSocketChannel) key.channel();
            // 服务器会为每个新的客户端连接创建一个 SocketChannel
            SocketChannel client = server.accept();
            // 配置为 非阻塞模式
            client.configureBlocking(false);
            // 这个新连接主要用于从客户端读取数据
            client.register(selector, SelectionKey.OP_READ);
            System.out.println(getClientName(client) + "已连接");
        }
        //READ事件 -- 客户端发送了消息
        else if (key.isReadable()){
            SocketChannel client = (SocketChannel) key.channel();
            String fwdMsg = receive(client);
            /**
             * selector在TCP已经断开连接的时候, 依然能够select()出OP_READ状态SocketChannel的SelectedKey
             * 所以需要通过得到的buffer返回值来判断, 即读取的字节数; 假如为0说明无数据可读, -1表示已断开;
             *
             * 此处通过获得的实际信息长度做判断, 假如异常则调用key.cancel(), 则将key放入CancelledKeys中
             * 下一次selector.select时unregister -> 此处用wakeup()函数立即强制刷新释放监听
             */
            if(fwdMsg.isEmpty()){
                //客户端异常
                key.cancel();
                selector.wakeup();
            } else {
                //服务器显示客户端发送的消息
                System.out.println(getClientName(client) + fwdMsg);
                //quit退出
                if(readyToQuit(fwdMsg)){
                    key.cancel();
                    selector.wakeup();
                    System.out.println(getClientName(client) + "已断开");
                }
                //if quit, 编辑fwd信息
                if(QUIT.equals(fwdMsg)){
                    fwdMsg = client.socket().getPort() + "退出群聊";
                }
                //转发消息给其他客户端
                forwardMessage(client, fwdMsg);
            }
        }
    }

    private void forwardMessage(SocketChannel client, String fwdMsg) throws IOException {
        for(SelectionKey key : selector.keys()){
            Channel connectedClient = key.channel();
            if(connectedClient instanceof ServerSocketChannel){
                continue;
            }
            if(key.isValid() && !client.equals(connectedClient)){
                //清空可能存留的字节
                wBuffer.clear();
                //存入需要转发的信息
                wBuffer.put(charset.encode(getClientName(client) + fwdMsg));
                //write -> read; 先往wBuffer中写入数据, 再从中读取数据写入通道
                wBuffer.flip();
                while (wBuffer.hasRemaining()){
                    ((SocketChannel) connectedClient).write(wBuffer);
                }
            }
        }
    }

    private String receive(SocketChannel client) throws IOException {
        rBuffer.clear();
        while (client.read(rBuffer) > 0){ client.read(rBuffer);};
        //read -> write;
        rBuffer.flip();
        return String.valueOf(charset.decode(rBuffer));
    }

    private String getClientName(SocketChannel client){
        return "客户端[" + client.socket().getPort() + "]: ";
    }

    private boolean readyToQuit(String msg){
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
        ChatServer chatServer = new ChatServer(7777);
        chatServer.start();
    }
}
