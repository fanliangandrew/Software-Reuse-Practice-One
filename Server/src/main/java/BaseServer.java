import utils.Config;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.EventListener;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by sweet on 3/24/16.
 */
public abstract class BaseServer {
    private ScheduledExecutorService sc = null;
    public static void main(String[] args) {
        new Server();
    }

    public BaseServer() {
        try {
            Config.setConfigName("server");
            String host = Config.getConfig().getProperty("host");
            String port = Config.getConfig().getProperty("port");
            InetSocketAddress socketAddress = new InetSocketAddress(host, Integer.parseInt(port));
            AsynchronousServerSocketChannel serverSocketChannel = AsynchronousServerSocketChannel
                    .open()
                    .bind(socketAddress);
            System.out.format("Server is listening at %s%n", socketAddress);
            serverSocketChannel.accept(serverSocketChannel, new ConnectionHandler());
            sc = Executors.newScheduledThreadPool(1);
            sc.scheduleAtFixedRate(new ServerLogger(NIOClient.getClients()), 0, 1, TimeUnit.MINUTES);
            Thread.currentThread().join();
        } catch (IOException e) {
            System.out.format("Server failed to start: %s", e.getMessage());
        } catch (InterruptedException e) {
            System.out.println("Server Stopped");
        }
    }

    class ConnectionHandler implements
            CompletionHandler<AsynchronousSocketChannel, AsynchronousServerSocketChannel> {

        public void completed(AsynchronousSocketChannel clientSock, AsynchronousServerSocketChannel serverSock) {
            new NIOClient(clientSock);
            //处理下一条连接
            serverSock.accept(serverSock, this);
        }

        public void failed(Throwable e, AsynchronousServerSocketChannel asynchronousServerSocketChannel) {
            System.out.println("Fail to connect to client");
        }
    }

}