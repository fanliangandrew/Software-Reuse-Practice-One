import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 * Created by sweet on 3/20/16.
 */
public class NIOClient {
    private static ArrayList<NIOClient> clients = new ArrayList<NIOClient>();

    /*
     * mSocketChannel 绑定的socket
     * mUsername 用户名
     * mPassword 密码
     * mStatus 当前状态->Settings.Status
     * mMsgPerSecond最近1秒发送的消息数
     * mMsgSinceLogin自从登陆起发送的消息数
     * mLastSendTime上次发送的时间戳
     *
     */
    private AsynchronousSocketChannel mSocketChannel = null;
    private String mUsername = null;
    private String mPassword = null;
    private Settings.Status mStatus = Settings.Status.LOGOUT;
    private int mMsgPerSecond = 0;
    private int mMsgSinceLogin = 0;
    private int mLastSendTime = 0;

    private StringTokenizer mSt;

    public NIOClient(AsynchronousSocketChannel socketChannel) {
        this.mSocketChannel = socketChannel;
        /*
         * 触发OnConnect事件
         */
        OnConnect();

        //开始接受消息
        readMessage();
    }

    private void readMessage() {
        /*
         * 读消息
         */
        final ByteBuffer buf = ByteBuffer.allocate(2048);

        mSocketChannel.read(buf, mSocketChannel, new CompletionHandler<Integer, AsynchronousSocketChannel>() {

            public void completed(Integer result, AsynchronousSocketChannel socketChannel) {
                if (result == -1) {
                    try {
                        /*
                         * 触发OnDisconnect事件
                         */
                        OnDisconnect();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return;
                }

                String message = StringUtils.bufToString(buf);
                dispatchMessage(message);
                System.out.println(message);

                /*
                 * 处理下一条信息
                 */
                readMessage();
            }

            public void failed(Throwable exc, AsynchronousSocketChannel channel ) {
                System.out.println("fail to read message from client");
            }

        });
    }

    private void dispatchMessage(String message) {
        /*
         * TODO: 使用RxJava注册事件和分发事件
         */
        mSt = new StringTokenizer(message, "|");
        String event = mSt.nextToken();
        if (event.equals("reg")) {
            /*
             * 触发OnRegister事件
             */
            OnRegister();
        } else if (event.equals("login")) {
            /*
             * 触发OnLogin事件
             */
            OnLogin();
        } else if(event.equals("send")) {
            /*
             * 触发OnSend事件
             */
            OnSend();
        } else {
            /*
             * 触发OnError事件
             */
            OnError();
        }
    }

    private void sendMessage(final String message) {
        /*
         * 发消息
         */
        ByteBuffer buf = ByteBuffer.allocate(2048);
        buf.put(message.getBytes());
        buf.flip();
        mSocketChannel.write(buf, mSocketChannel, new CompletionHandler<Integer, AsynchronousSocketChannel>() {

            public void completed(Integer result, AsynchronousSocketChannel channel) {
                //Nothing to do
            }

            public void failed(Throwable exc, AsynchronousSocketChannel channel) {
                System.out.println("Fail to write message to client");
            }

        });
    }

    /*
     * 事件定义
     */

    public void OnConnect() {
        clients.add(this);
    }

    public void OnDisconnect() throws IOException {
        System.out.format("Stopped listening to the client %s%n", mSocketChannel.getRemoteAddress());
        clients.remove(this);
        mSocketChannel.close();
    }

    public void OnRegister() {
        /*
         * TODO:注册
         * 1.判断是否已经注册
         * 2.判断密码是否大于6位
         * 3.加密存储
         * 4.成功则自动登陆
         * 5.失败则返回错误信息
         */

    }

    public void OnLogin() {
        /*
         * TODO:登陆
         * 1.判断用户名和密码
         * 2.判断是否已经登陆
         * 3.成功修改状态
         * 4.失败返回错误信息
         */

    }

    public void OnSend() {
        /*
         * TODO:发送
         * 1.判断用户状态
         * 2.发送消息
         * 3.更新用户状态
         */

        String message = mSt.nextToken();
        for (NIOClient client : clients) {
            if (client != this) client.sendMessage(message);
        }
        sendMessage("success");
    }

    public void OnError() {
        sendMessage("消息非法！");
    }

}
