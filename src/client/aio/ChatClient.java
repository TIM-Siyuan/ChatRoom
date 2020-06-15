package client.aio;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class ChatClient {
    private static final String LOCALHOST = "localhost";
    private static final int DEFAULT_PORT = 8888;
    private static final String QUIT = "quit";
    private static final int BUFFER = 1024;
    private String host;
    private int port;
    private AsynchronousSocketChannel clientChannel;
    private Charset charset = Charset.forName("UTF-8");

    public ChatClient(){
        this(LOCALHOST, DEFAULT_PORT);
    }

    public ChatClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * 关闭此流并释放与之相关联的任何系统资源，如果流已经关闭，则调用此方法将不起作用。
     * @param closeable
     */
    private void close(Closeable closeable){
        if(closeable != null){
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 判断客户端是否准备退出
     * @param msg
     * @return
     */
    public boolean readyToQuit(String msg){
        return QUIT.equals(msg);
    }

    private void start(){
        try {
            // 创建channel
            clientChannel = AsynchronousSocketChannel.open();
            Future<Void> future = clientChannel.connect(new InetSocketAddress(host, port));
            future.get();// 阻塞式调用

            //启动一个新的线程，处理用户的输入
            new Thread(new UserInputHandler(this)).start();

            ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER);
            // 读取服务器转发的消息
            while(true){
                //启动异步读操作，以从该通道读取到给定的缓冲器字节序列
                Future<Integer> readResult = clientChannel.read(byteBuffer);
                //Future的get方法返回读取的字节数或-1如果没有字节可以读取，因为通道已经到达流终止。
                int result = readResult.get();
                if(result <= 0){
                    // 服务器异常
                    System.out.println("服务器断开");
                    close(clientChannel);
                    // 0是正常退出，非0是不正常退出
                    System.exit(1);
                }else {
                    byteBuffer.flip(); //准备读取
                    String msg = String.valueOf(charset.decode(byteBuffer));
                    byteBuffer.clear();
                    System.out.println(msg);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    public void send(String msg){
        if (msg.isEmpty()){
            //没有必要向服务器发送空白的消息从而占用资源
            return;
        }
        ByteBuffer byteBuffer = charset.encode(msg);
        Future<Integer> writeResult = clientChannel.write(byteBuffer);
        try {
            writeResult.get();
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("消息发送失败");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        ChatClient chatClient = new ChatClient("127.0.0.1", 7777);
        chatClient.start();
    }
}
