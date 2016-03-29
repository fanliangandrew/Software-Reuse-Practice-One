package test;

import client.Client;
import server.Server;
import utils.MessageBuilder;

import java.io.Console;
import java.util.HashMap;

/**
 * Created by summer on 3/29/16.
 */
public class SendNumLimitTest {
    public static void main(String args[])
    {
        Server.DEBUG_MODE(true);
        Client.DEBUG_MODE(true);
        Server server = new Server();
        String msgToSend = "test";
        Client client = new Client() {
            @Override
            public void OnConnect(HashMap<String, String> args) {
                System.out.println("HHH");
            }

            @Override
            public void OnSend(HashMap<String, String> msg)
            {
                assert("event" == "send");
                assert("message" == msgToSend);
            }
        };

        MessageBuilder msgBuilder = new MessageBuilder();
        msgBuilder.add("event","login");
        msgBuilder.add("username","funcTest");
        msgBuilder.add("password","123456");
        String msg = msgBuilder.build();
        client.sendMessage(msg);

        for(int i = 1; i <= 6; ++i)
        {
            MessageBuilder msgToSendBuilder = new MessageBuilder();
            msgToSendBuilder.add("event","send");
            msgToSendBuilder.add("message",msgToSend);
            String message = msgToSendBuilder.build();
            client.sendMessage(message);
        }
    }
}