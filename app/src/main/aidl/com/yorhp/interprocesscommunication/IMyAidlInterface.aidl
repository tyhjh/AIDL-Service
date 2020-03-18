// IMyAidlInterface.aidl
package com.yorhp.interprocesscommunication;
//这个包名必须是User.aidl文件的路径
import com.yorhp.interprocesscommunication.bean.User;
import com.yorhp.interprocesscommunication.IOnUserChangedListener;

// Declare any non-default types here with import statements

interface IMyAidlInterface {

    /**
     *
     *获取姓名
     */
     String getName(String nickName);


    /**
     *获取用户
     */
     User getUserById(int id);


    /**
     *
     *注册监听
     */
     void registerListener(IOnUserChangedListener listener);

    /**
     *
     *取消监听
     */
     void unRegisterListener(IOnUserChangedListener listener);

}
