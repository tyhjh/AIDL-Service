package com.yorhp.interprocesscommunication.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import com.yorhp.interprocesscommunication.IMyAidlInterface;

import androidx.annotation.Nullable;

/**
 * @author tyhj
 * @date 2020/3/16
 * @Description: java类作用描述
 */

public class AIDLService extends Service {

    IMyAidlInterface.Stub stub=new IMyAidlInterface.Stub() {
        @Override
        public void basicTypes(int anInt, long aLong, boolean aBoolean, float aFloat, double aDouble, String aString) throws RemoteException {

        }

        @Override
        public String getName(String nickName) throws RemoteException {
            return "aidl is "+nickName;
        }
    };


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return stub;
    }
}
