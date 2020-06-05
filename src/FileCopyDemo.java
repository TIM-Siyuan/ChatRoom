import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

interface FileCopyRunner{
    void copyFile(File source, File target);
}
public class FileCopyDemo {

    private static final int ROUNDS = 5;

    private static void benchmark(FileCopyRunner test, File source, File target){
        long elapsed = 0L;
        for(int i = 0; i < ROUNDS; i++){
            long startTime = System.currentTimeMillis();
            test.copyFile(source, target);
            elapsed += System.currentTimeMillis();
            target.delete();
        }
        System.out.println(test + ":" + elapsed / ROUNDS);
    }
    private static void close(Closeable closeable){
        if(closeable != null){
            try{
                closeable.close();
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args){
        FileCopyRunner noBufferStreamCopy = new FileCopyRunner() {
            @Override
            public void copyFile(File source, File target) {
                InputStream fin = null;
                OutputStream fout = null;
                try {
                    fin = new FileInputStream(source);
                    fout = new FileOutputStream(target);

                    int result;
                    //阻塞式调用
                    while ((result = fin.read()) != -1) {
                        fout.write(result);
                    }

                } catch (FileNotFoundException e){
                    e.printStackTrace();
                } catch (IOException e){
                    e.printStackTrace();
                } finally {
                    close(fin);
                    close(fout);
                }
            }

            @Override
            public String toString() {
                return "noBufferStreamCopy";
            }
        };
        FileCopyRunner bufferedStreamCopy = new FileCopyRunner() {
            @Override
            public void copyFile(File source, File target) {
                InputStream fin = null;
                OutputStream fout = null;
                try{
                    fin = new BufferedInputStream(new FileInputStream(source));
                    fout = new BufferedOutputStream(new FileOutputStream(target));

                    //缓冲区大小
                    byte[] buffer = new byte[1024];

                    int result;
                    //read每次读一个buffer中字节数, 返回值是此次在buffer中所读字节的数量, 如果已经到达文件底返回-1
                    while ((result = fin.read(buffer)) != -1){
                        fout.write(buffer, 0, result);
                    }
                } catch (FileNotFoundException e){
                    e.printStackTrace();
                } catch (IOException e){
                    e.printStackTrace();
                } finally {
                    close(fin);
                    close(fout);
                }
            }

            @Override
            public String toString() {
                return "bufferedStreamCopy";
            }
        };
        //非直接缓冲区
        FileCopyRunner nioBufferCopy = new FileCopyRunner() {
            @Override
            public void copyFile(File source, File target) {
                FileChannel fin = null;
                FileChannel fout = null;

                try{
                    fin = new FileInputStream(source).getChannel();
                    fout = new FileOutputStream(target).getChannel();

                    ByteBuffer buffer = ByteBuffer.allocate(1024);
                    while (fin.read(buffer) != -1){
                        //read -> write
                        buffer.flip();
                        while (buffer.hasRemaining()){
                            //因为write函数不一定能够全部读完buffer中所有字节, 所以多加一层while
                            fout.write(buffer);
                        }
                        //write -> read
                        buffer.clear();
                    }

                } catch (FileNotFoundException e){
                    e.printStackTrace();
                } catch (IOException e){
                    e.printStackTrace();
                } finally{
                    close(fin);
                    close(fout);
                }
            }

            @Override
            public String toString() {
                return "nioBufferCopy";
            }
        };
        //通道之间的数据传输(直接缓冲区的模式)
        FileCopyRunner nioTransferCopy = new FileCopyRunner() {
            @Override
            public void copyFile(File source, File target) {
                FileChannel fin = null;
                FileChannel fout = null;
                try{
                    fin = new FileInputStream(source).getChannel();
                    fout = new FileOutputStream(target).getChannel();

                    long transferred = 0L;
                    long size = fin.size();
                    while (transferred != size){
                        transferred += fin.transferTo(0, size, fout);
                    }

                } catch (FileNotFoundException e){
                    e.printStackTrace();
                } catch (IOException e){
                    e.printStackTrace();
                } finally {
                    close(fin);
                    close(fout);
                }
            }

            @Override
            public String toString() {
                return "nioTransferCopy";
            }
        };
    }
}
