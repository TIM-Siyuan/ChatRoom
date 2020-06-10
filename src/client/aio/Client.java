package client.aio;

import sun.management.snmp.jvmmib.JVM_MANAGEMENT_MIBOidTable;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class Client {

    final String LOCALHOST = "127.0.0.1";
    final int DEFAULT_PORT = 8888;
    AsynchronousSocketChannel clientChannel;

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
            //创建channel
            clientChannel = AsynchronousSocketChannel.open();
            //Server中用回调函数, 也可用Future;
            Future<Void> future = clientChannel.connect(new InetSocketAddress(LOCALHOST, DEFAULT_PORT));
            //阻塞式调用
            future.get();
            //等待用户的输入
            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
            while(true){
                String input = consoleReader.readLine();
                //返回服务器信息
                byte[] inputBytes = input.getBytes();
                ByteBuffer buffer = ByteBuffer.wrap(inputBytes);
                //write是异步调用
                Future<Integer> writeResult = clientChannel.write(buffer);
                writeResult.get();
                buffer.flip();

                //接受服务器信息
                Future<Integer> readResult = clientChannel.read(buffer);
                readResult.get();
                //为了演示方便, 读取buffer的内容
                String echo = new String(buffer.array());
                buffer.clear();
                System.out.println(echo);
            }
        } catch (IOException e){
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Client client = new Client();
        client.start();
    }
}
