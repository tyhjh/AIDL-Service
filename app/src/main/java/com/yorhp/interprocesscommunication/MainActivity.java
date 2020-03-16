package com.yorhp.interprocesscommunication;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.widget.Button;
import android.widget.Toast;

import com.yorhp.interprocesscommunication.service.AIDLService;
import com.yorhp.interprocesscommunication.service.MessengerService;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

/**
 * @author tyhj
 */
public class MainActivity extends AppCompatActivity {

    private Button btnTest;
    private IMyAidlInterface myAidlInterface;


    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case 2:
                    String name = msg.getData().getString("name");
                    Toast.makeText(MainActivity.this, name, Toast.LENGTH_SHORT).show();
                    break;
                default:
                    break;
            }
        }
    };


    /**
     * 服务端信使
     */
    private Messenger serviceMessenger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //绑定服务
        bindService(new Intent(MainActivity.this, AIDLService.class), mAIDLServiceConnection, BIND_AUTO_CREATE);
        //AIDL进行通信
        findViewById(R.id.btnAIDL).setOnClickListener(v -> {
            try {
                String name = null;
                name = myAidlInterface.getName("Nick");
                Toast.makeText(MainActivity.this, name, Toast.LENGTH_SHORT).show();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        });
        //绑定服务
        bindService(new Intent(MainActivity.this, MessengerService.class), mMessengerServiceConnection, BIND_AUTO_CREATE);
        //Messenger进行通信
        findViewById(R.id.btnMessenger).setOnClickListener(v -> {
            try {
                Message message = new Message();
                message.what = 2;
                Bundle bundle=new Bundle();
                bundle.putString("name","Tony");
                message.setData(bundle);
                //使用
                serviceMessenger.send(message);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * messenger服务连接监听
     */
    private ServiceConnection mMessengerServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                //获取到服务端信使
                serviceMessenger = new Messenger(service);
                Message message = new Message();
                //将客户端信使传递到服务端
                message.replyTo = new Messenger(handler);;
                message.what = 1;
                //使用服务端信使发送
                serviceMessenger.send(message);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };


    /**
     * AIDL服务连接监听
     */
    private ServiceConnection mAIDLServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            myAidlInterface = IMyAidlInterface.Stub.asInterface(service);
            //Log.i("MainActivity","service connected");
            Toast.makeText(MainActivity.this, "service connected", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };

}
