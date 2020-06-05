package client.bio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class UserInputHandler implements Runnable {
    private ChatClient chatClient;

    public UserInputHandler(ChatClient chatClient){
        this.chatClient = chatClient;
    }

    @Override
    public void run() {
        try {
            BufferedReader consoleR = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                String input = consoleR.readLine();

                //向服务器发送消息
                chatClient.send(input);

                if(chatClient.readyToQuit(input)){
                    break;
                }
            }
        }catch (IOException e){
                e.printStackTrace();
        }
    }
}
