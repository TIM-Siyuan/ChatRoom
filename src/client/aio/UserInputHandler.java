package client.aio;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class UserInputHandler implements Runnable{
    private ChatClient chatClient;

    public UserInputHandler(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public void run() {
        try {
            //等待用户输入消息
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            while (true){
                String input = br.readLine();
                //向服务器发送消息
                chatClient.send(input);
                //检查用户是否准备退出
                if (chatClient.readyToQuit(input)){
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
