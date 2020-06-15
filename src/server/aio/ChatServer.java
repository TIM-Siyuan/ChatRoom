package server.aio;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatServer {
    private static final String LOCALHOST = "localhost";
    private static final int DEFAULT_PORT = 8888;
    private static final String QUIT = "quit";
    private static final int BUFFER = 1024;
    private static final int THREADPOOL_SIZE = 8;

    private AsynchronousChannelGroup channelGroup;
    private AsynchronousServerSocketChannel serverChannel;
    private List<ClientHandler> connectedClients;
    private Charset charset = Charset.forName("UTF-8");
    private int port;

    public ChatServer(){
        this(DEFAULT_PORT);
    }

    public ChatServer(int port){
        this.port = port;
        this.connectedClients = new ArrayList<>();
    }

    /**
     * 当输入"quit"时表示客户退出
     * @param msg
     * @return
     */
    private boolean readyToQuit(String msg){
        return QUIT.equals(msg);
    }

    /**
     * 关闭相对应的流并释放与之相关联的任何系统资源,如果流已关闭,则调用此方法将不起任何作用
     * @param closeable
     */
    private void close(Closeable closeable){
        if(closeable != null){
            try{
                closeable.close();
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    /**
     * 添加一个新的客户端进客户端列表(list集合)
     * @param handler
     */
    private synchronized void addClient(ClientHandler handler) {
        connectedClients.add(handler);
        System.out.println(getClientName(handler.clientChannel)+"已经连接到服务器");
    }

    /**
     * 将该客户(下线)从列表中删除
     * @param clientHandler
     */
    private synchronized void removeClient(ClientHandler clientHandler) {
        connectedClients.remove(clientHandler);
        System.out.println(getClientName(clientHandler.clientChannel)+"已断开连接");
        //关闭该客户对应流
        close(clientHandler.clientChannel);
    }

    /**
     * 服务器其实客户端发送的信息,并将该信息进行utf-8解码
     * @param buffer
     * @return
     */
    private synchronized String receive(ByteBuffer buffer) {
        CharBuffer charBuffer = charset.decode(buffer);
        return String.valueOf(charBuffer);
    }

    /**
     * 服务器端转发该客户发送的消息到其他客户控制室上(转发信息)
     * @param clientChannel
     * @param fwdMsg
     */
    private synchronized void forwardMessage(AsynchronousSocketChannel clientChannel, String fwdMsg) {
        for (ClientHandler handler : connectedClients){
            //该信息不用再转发到发送信息的那个人那
            if (!handler.clientChannel.equals(clientChannel)){
                try {
                    //将要转发的信息写入到缓冲区中
                    ByteBuffer buffer = charset.encode(getClientName(handler.clientChannel)+":"+fwdMsg);
                    //将相应的信息写入到用户通道中,用户再通过获取通道中的信息读取到对应转发的内容
                    handler.clientChannel.write(buffer,null,handler);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 获取客户端的端口号并打印出来
     * @param clientChannel
     * @return
     */
    private String getClientName(AsynchronousSocketChannel clientChannel) {
        int clientPort = -1;
        try {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) clientChannel.getRemoteAddress();
            clientPort = inetSocketAddress.getPort();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "客户端["+clientPort+"]";
    }

    private class AcceptHandler implements CompletionHandler<AsynchronousSocketChannel, Object>{

        @Override
        public void completed(AsynchronousSocketChannel clientChannel, Object attachment) {
            if(serverChannel.isOpen()){
                serverChannel.accept(null, this);
            }
            if (clientChannel != null && clientChannel.isOpen()) {
                //为该新连接的用户创建handler,用于读写操作
                ClientHandler handler = new ClientHandler(clientChannel);
                ByteBuffer buffer = ByteBuffer.allocate(BUFFER);
                // 将新用户添加到在线用户列表
                addClient(handler);
                //第一个buffer: 表示从clientChannel中读取的信息写入到buffer缓冲区中
                //第二个buffer: handler回调函数被调用时,buffer会被当做一个attachment参数传入到该回调函数中
                clientChannel.read(buffer, buffer, handler);
            }
        }

        @Override
        public void failed(Throwable exc, Object attachment) {
            System.out.println("连接失败:" + exc);
        }
    }

    private class ClientHandler implements CompletionHandler<Integer, Object>{
        //设置
        private AsynchronousSocketChannel clientChannel;
        public ClientHandler(AsynchronousSocketChannel channel){
            this.clientChannel = channel;
        }

        @Override
        public void completed(Integer result, Object attachment) {
            ByteBuffer buffer = (ByteBuffer) attachment;
            if(buffer != null){
                 if(result <= 0) {
                     //客户端异常
                     //将客户移除出在线客户列表
                     removeClient(this);
                 } else {
                    buffer.flip();
                    String fwdMsg = receive(buffer);
                    System.out.println(getClientName(clientChannel) + ":" + fwdMsg);
                    //转发
                    forwardMessage(clientChannel, fwdMsg);
                    buffer.clear();

                     //检查用户是否退出
                     if (readyToQuit(fwdMsg)){
                         //将客户从在线客户列表中去除
                         removeClient(this);
                     }else {
                         //如果不是则继续等待读取用户输入的信息
                         clientChannel.read(buffer,buffer,this);
                     }
                 }
            }
        }

        @Override
        public void failed(Throwable exc, Object attachment) {
            System.out.println("读写失败:"+exc);
        }
    }

    private void start(){
        try {
            //创建并绑定
            //初始化线程池
            ExecutorService executorService = Executors.newFixedThreadPool(THREADPOOL_SIZE);
            //将线程池加入到异步通道中
            channelGroup = AsynchronousChannelGroup.withThreadPool(executorService);
            //打开通道
            serverChannel = AsynchronousServerSocketChannel.open(channelGroup);
            //为通道绑定本地主机和端口
            serverChannel.bind(new InetSocketAddress(LOCALHOST, port));
            System.out.println("启动服务器, 监听端口" + port);

            while (true){
                //希望使用AcceptHandler的回调函数, 又使用阻塞式调用, 防止服务器提前关闭或者系统资源浪费
                //一直调用accept函数,接收要与服务端建立连接的用户
                serverChannel.accept(null, new AcceptHandler());
                //阻塞式调用,防止占用系统资源
                System.in.read();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(serverChannel);
        }
    }

    public static void main(String[] args) {
        ChatServer server = new ChatServer(7777);
        server.start();
    }
}
