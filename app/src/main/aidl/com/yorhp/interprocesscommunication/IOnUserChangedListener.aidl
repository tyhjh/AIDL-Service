// IOnUserChangedListener.aidl
package com.yorhp.interprocesscommunication;

import com.yorhp.interprocesscommunication.bean.User;
// Declare any non-default types here with import statements

interface IOnUserChangedListener {
   /**
    *用户改变监听
    */
    void onUserChanged(in User user);
}
