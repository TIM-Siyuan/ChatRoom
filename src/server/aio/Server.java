package server.aio;

import client.aio.Client;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.HashMap;
import java.util.Map;

public class Server {
    final String LOCALHOST = "127.0.0.1";
    final int DEFAULT_PORT = 8888;
    AsynchronousServerSocketChannel serverChannel;

    private void close(Closeable closeable){
        if(closeable != null){
            try{
                closeable.close();
                System.out.println("关闭" + closeable);
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    public void start(){
        try{
            //  创建新的异步服务器窗口
            serverChannel = AsynchronousServerSocketChannel.open();
            // 绑定监听端口
            // AsynchronousChannelGroup ≈ 线程池
            serverChannel.bind(new InetSocketAddress(LOCALHOST, DEFAULT_PORT));
            System.out.println("启动服务器，监听端口:" + DEFAULT_PORT);
            //因为此处的accept是异步调用, 所以将会马上返回, 但是又需要准备好数据时可返回数据, 所以放入循环中
            while (true){
                //AcceptHandler() --> 实现completionHandler接口; 其在其他线程调用
                serverChannel.accept(null, new AcceptHandler());
                //≈sleep(); 小技巧: read阻塞式调用, 防止accept过于频繁调用, 浪费太多资源
                System.in.read();
            }
        } catch (IOException e){
            e.printStackTrace();
        } finally {
            close(serverChannel);
        }
    }

    private class AcceptHandler implements CompletionHandler<AsynchronousSocketChannel, Object> {

        @Override
        public void completed(AsynchronousSocketChannel result, Object attachment) {
            if(serverChannel.isOpen()){
                serverChannel.accept(null, this);
            }

            //定义局部变量, 增加可读性
            AsynchronousSocketChannel clientChannel = result;
            if(clientChannel != null && clientChannel.isOpen()){
                //处理客户端通道的handler
                ClientHandler handler = new ClientHandler(clientChannel);

                ByteBuffer buffer = ByteBuffer.allocate(1024);
                //判断是读操作还是写操作, 后续加入attachment传送
                Map<String, Object> info = new HashMap<>();
                //默认读操作
                info.put("type", "read");
                info.put("buffer", buffer);

                clientChannel.read(buffer, info, handler);
            }
        }

        @Override
        public void failed(Throwable exc, Object attachment) {
            //处理错误
        }
    }

    private class ClientHandler implements CompletionHandler<Integer, Object>{
        private AsynchronousSocketChannel clientChannel;
        public ClientHandler(AsynchronousSocketChannel channel){
            this.clientChannel = channel;
        }

        @Override
        public void completed(Integer result, Object attachment) {
            //拿到操作类型
            Map<String, Object> info = (Map<String, Object>) attachment;
            String type = (String) info.get("type");

            final String read = "read";
            final String write = "write";
            if(read.equals(type)){
                ByteBuffer buffer = (ByteBuffer) info.get("buffer");
                buffer.flip();
                info.put("type", "write");
                clientChannel.write(buffer, info, this);
            } else if (write.equals(type)){
                ByteBuffer buffer = ByteBuffer.allocate(1024);
                info.put("type", "read");
                info.put("buffer", buffer);
                clientChannel.read(buffer, info, this);
            }
        }

        @Override
        public void failed(Throwable exc, Object attachment) {
            //处理错误
        }
    }

    public static void main(String[] args) {
        Server server = new Server();
        server.start();
    }
}
