package com.yorhp.interprocesscommunication.service;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import com.yorhp.interprocesscommunication.IMyAidlInterface;
import com.yorhp.interprocesscommunication.IOnUserChangedListener;
import com.yorhp.interprocesscommunication.bean.User;

import androidx.annotation.Nullable;

/**
 * @author tyhj
 * @date 2020/3/16
 * @Description: java类作用描述
 */

public class AIDLService extends Service {

    private static final String PACKAGE_NAME_VERIFY="com.yorhp";

    /**
     * 监听集合，自动进行线程同步，线程安全
     */
    private RemoteCallbackList<IOnUserChangedListener> listeners = new RemoteCallbackList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        //开启线程模拟用户数据改变，回调
        new Thread(() -> {
            while (true) {
                final int n = listeners.beginBroadcast();
                for (int i = 0; i < n; i++) {
                    try {
                        IOnUserChangedListener listener = listeners.getBroadcastItem(i);
                        //返回用户数据
                        listener.onUserChanged(new User("Tyhj" + System.currentTimeMillis(), 10));
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                listeners.finishBroadcast();
                SystemClock.sleep(2000);
            }
        }).start();
    }

    public IMyAidlInterface.Stub stub = new IMyAidlInterface.Stub() {

        @Override
        public String getName(String nickName) throws RemoteException {
            return "aidl is " + nickName;
        }

        @Override
        public User getUserById(int id) throws RemoteException {
            return new User("Tyhj", 1);
        }

        @Override
        public void registerListener(IOnUserChangedListener listener) throws RemoteException {
            listeners.register(listener);
        }

        @Override
        public void unRegisterListener(IOnUserChangedListener listener) throws RemoteException {
            listeners.unregister(listener);
        }

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            //TODO 在这个地方也可以验证权限
            //权限检查
            int check=checkCallingOrSelfPermission("com.yorhp.service.ACCESS_USER_INFO");
            if(check== PackageManager.PERMISSION_DENIED){
                Log.e("checkPermission","fail");
                return false;
            }

            String packageName=null;
            //获取客户端应用的Uid来验证包名
            String[] packages=getPackageManager().getPackagesForUid(getCallingUid());
            if(packages!=null&&packages.length>0){
                packageName=packages[0];
            }
            if(packageName==null||!packageName.startsWith(PACKAGE_NAME_VERIFY)){
                Log.e("checkPermission","fail");
                return false;
            }
            return super.onTransact(code, data, reply, flags);
        }
    };





    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        //权限检查
        int check=checkCallingOrSelfPermission("com.yorhp.service.ACCESS_USER_INFO");
        if(check== PackageManager.PERMISSION_DENIED){
            Log.e("checkPermission","fail");
            return null;
        }
        return stub;
    }
}
