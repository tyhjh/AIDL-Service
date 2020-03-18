package com.yorhp.interprocesscommunication.service;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author tyhj
 * @date 2020/3/16
 * @Description: java类作用描述
 */

public class MessengerService extends Service {

    /**
     *客户端的信使
     */
    private Messenger clientMessenger;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        //返回信使
        return messenger.getBinder();
    }


    private Handler handler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                //接受客户端的信使
                case 1:
                    clientMessenger = msg.replyTo;
                    break;
                //使用客户端的信使发送消息
                case 2:
                    if (clientMessenger != null) {
                        try {
                            String name=msg.getData().getString("name");
                            Bundle bundle = new Bundle();
                            bundle.putString("name", "messenger is "+name);
                            Message message = new Message();
                            message.what=2;
                            message.setData(bundle);
                            //发送消息
                            clientMessenger.send(message);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    };

    /**
     * 服务本地信使
     */
    private Messenger messenger = new Messenger(handler);

}
