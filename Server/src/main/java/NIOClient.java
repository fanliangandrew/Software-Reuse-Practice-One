import com.alibaba.fastjson.JSON;

import javax.swing.plaf.basic.BasicScrollPaneUI;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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
    private long mLastSendTime = 0;

    private int localValidLogin = 0;
    private int localInvalidLogin = 0;
    private int localReceiveMsgNum = 0;
    private int localIgnoreMsgNum = 0;
    private int localForwardMsgNum = 0;

    public int getLocalValidLogin() {
        return localValidLogin;
    }

    public int getLocalInvalidLogin() {
        return localInvalidLogin;
    }

    public int getLocalReceiveMsgNum() {
        return localReceiveMsgNum;
    }

    public int getLocalIgnoreMsgNum() {
        return localIgnoreMsgNum;
    }

    public int getLocalForwardMsgNum() {
        return localForwardMsgNum;
    }

    public static ArrayList<NIOClient> getClients() {
        return clients;
    }



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

        HashMap<String,String> meg = JSON.parseObject(message, HashMap.class);

        if (meg.get("event").equals("reg")) {
            /*
             * 触发OnRegister事件
             */
            OnRegister(meg);
        } else if (meg.get("event").equals("login")) {
            /*
             * 触发OnLogin事件
             */
            OnLogin(meg);
        } else if(meg.get("event").equals("send")) {
            /*
             * 触发OnSend事件
             */
            OnSend(meg);
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
        String jsonString = JSON.toJSONString(message);
        ByteBuffer buf = ByteBuffer.allocate(2048);
        buf.put(jsonString.getBytes());
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

    private void OnConnect() {
        clients.add(this);
    }

    private void OnDisconnect() throws IOException {
        System.out.format("Stopped listening to the client %s%n", mSocketChannel.getRemoteAddress());
        clients.remove(this);
        mSocketChannel.close();
    }

    private void OnRegister(Map<String,String> meg) {
        /*
         * 1.判断是否已经注册
         * 2.判断密码是否大于6位
         * 3.加密存储
         * 4.成功则自动登陆
         * 5.失败则返回错误信息
         */

        String username = meg.get("username");
        String password = meg.get("password");
        if (DatabaseUtils.isExisted(username)) {
            sendMessage("fail|Id already exists.");
        } else if (password.length() < 6) {
            sendMessage("fail|The password is too short (at least six).");
        } else {
            String encryptedPass = StringUtils.md5Hash(password);
            boolean b = DatabaseUtils.createAccount(username, encryptedPass);
            if (b) {
                mUsername = username;
                mPassword = encryptedPass;
                mStatus = Settings.Status.LOGIN;
                sendMessage("reg|success");
            } else {
                sendMessage("fail|Registration failed due to an unexpected error.");
            }
        }
    }

    private void OnLogin(Map<String,String> meg) {
        /*
         * 1.判断用户名和密码
         * 2.判断是否已经登陆
         * 3.成功修改状态
         * 4.失败返回错误信息
         */

        String username = meg.get("username");
        String encryptedPass = StringUtils.md5Hash(meg.get("password"));
        if (DatabaseUtils.isValid(username, encryptedPass)) {
            for (NIOClient client : clients) {
                if (client != this && client.mUsername != null && client.mUsername.equals(username)) {
                    localInvalidLogin ++;
                    HashMap<String,String> meg = new HashMap<String, String>();
                    meg.put("event","login");
                    meg.put("result","fail");
                    meg.put("message","Already login on another terminal.");
                    String jsonString = JSON.toJSONString(meg);
                    sendMessage(jsonString);
                    return;
                }
            }
            if (mStatus == Settings.Status.LOGIN || mStatus == Settings.Status.RELOGIN) {
                localInvalidLogin ++;
                HashMap<String,String> meg = new HashMap<String, String>();
                meg.put("event","login");
                meg.put("result","fail");
                meg.put("message","Already login");
                String jsonString = JSON.toJSONString(meg);
                sendMessage(meg);
            } else if (mStatus == Settings.Status.LOGOUT) {
                localValidLogin ++;
                HashMap<String,String> meg = new HashMap<String, String>();
                meg.put("event","login");
                meg.put("result","success");
                String jsonString = JSON.toJSONString(meg);
                sendMessage(jsonString);
                mStatus = Settings.Status.LOGIN;
                mUsername = username;
                mPassword = encryptedPass;
            }
        } else {
            localInvalidLogin ++;
            HashMap<String,String> meg = new HashMap<String, String>();
            meg.put("event","login");
            meg.put("result","fail");
            meg.put("message","Invalid account.");
            String jsonString = JSON.toJSONString(meg);
            sendMessage(jsonString);
        }
    }

    private void OnSend(Map<String,String> meg) {
        /*
         * 1.判断用户状态
         * 2.发送消息
         * 3.更新用户状态
         */

        long currentSendTime = System.currentTimeMillis() / 1000;
        if (mLastSendTime == currentSendTime) {
            mMsgPerSecond ++;
            if (mMsgPerSecond >= Settings.maxNumberPerSecond) {
                mStatus = Settings.Status.IGNORE;
            }
        } else {
            mMsgPerSecond = 0;
            if (mStatus == Settings.Status.IGNORE)
                mStatus = Settings.Status.LOGIN;
        }
        mLastSendTime = currentSendTime;

        localReceiveMsgNum ++;

        if (mStatus == Settings.Status.LOGOUT || mStatus == Settings.Status.IGNORE) {
            localIgnoreMsgNum ++;
        } else {
            mMsgSinceLogin ++;
            if (mMsgSinceLogin >= Settings.maxNumberPerSession) {
                mStatus = Settings.Status.LOGOUT;
                mUsername = null;
                mPassword = null;
                //mMsgPerSecond = 0;
                mMsgSinceLogin = 0;
                //mLastSendTime = 0;
                HashMap<String,String> mSt = new HashMap<String, String>();
                mSt.put("event","Redo login");
                String jsonStringMeg = JSON.toJSONString(mSt);
                sendMessage(jsonStringMeg);
            } else {
                HashMap<String,String> mSt = new HashMap<String, String>();
                mSt.put("event","send");
                mSt.put("result","success");
                String jsonStringMeg = JSON.toJSONString(mSt);
                sendMessage(jsonStringMeg);
            }

            String message = meg.get("message");
            for (NIOClient client : clients) {
                if (client != this &&
                        (client.mStatus == Settings.Status.LOGIN || client.mStatus == Settings.Status.RELOGIN)) {
                    HashMap<String,String> mSt = new HashMap<String, String>();
                    mSt.put("event","forward");
                    mSt.put("from",this.mUsername);
                    mSt.put("message", message);
                    String jsonString = JSON.toJSONString(mSt);

                    client.sendMessage(jsonString);
                    localForwardMsgNum ++;
                } else {
                    //发给自己的
                    HashMap<String,String> mSt = new HashMap<String, String>();
                    mSt.put("event","forward");
                    mSt.put("from","你");
                    mSt.put("message",message);
                    String jsonString = JSON.toJSONString(mSt);
                    sendMessage(jsonString);
                }
            }
        }
    }

    private void OnError() {
        HashMap<String,String> mSt = new HashMap<String, String>();
        mSt.put("event","error");
        mSt.put("reason","error");
        String jsonString = JSON.toJSONString(mSt);
        sendMessage(jsonString);
    }

}
