# Android跨进程通信

标签（空格分隔）： Android

---
Android跨进程通信的方式也是比较多的，项目中用的比较多的应该是Messenger和AIDL，主要讲一下两者的实现

## 跨进程通信的方式
1、四大组件间传递Bundle;
2、文件共享，多进程读写一个相同的文件，获取文件内容进行交互；
3、Messenger，利用Handler实现。（适用于多进程、单线程，不需要考虑线程安全），其底层基于AIDL。
4、AIDL(Android Interface Definition Language，Android接口定义语言)，大部分应用程序不应该使用AIDL去创建一个绑定服务，因为它需要多线程能力，并可能导致一个更复杂的实现。
5、ContentProvider，常用于多进程共享数据，比如系统的相册，音乐等，我们也可以通过ContentProvider访问到；
6、Socket传输数据。

## Messenger
Messenger的实现比较简单，底层基于AIDL，适用于多进程、单线程，不需要考虑线程安全；

### 实现思路
Messenger就是信使的意思；在服务端创建一个信使，在客户端创建一个信使，当客户端绑定服务的时候，服务端将信使传递给客户端，客户端就可以通过服务端的信使发送消息给服务端；客户端也可以将自己的信使作为消息发送给服务端，服务端拿到客户端的信使就可以发送消息给客户端了，就实现了双方的通信。

### 具体实现
服务端在`onBind`方法返回自己的信使给客户端，等客户端发送客户端的信使过来后进行保存，然后就可以使用客户端的信使给客户端发消息了；
```java
public class MessengerService extends Service {

    /**
     *客户端的信使
     */
    private Messenger clientMessenger;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        //返回自己的信使
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
```
当然服务需要在AndroidManifest文件中申明为单独进程运行
```xml
        <service android:name=".service.MessengerService"
            android:enabled="true"
            android:exported="true"
            android:process="com.yorhp.messenger.name">
            <intent-filter>
                <action android:name="com.yorhp.messenger.name"/>
            </intent-filter>
        </service>
```
客户端，在服务连接后获取到服务端的信使，将自己的信使发送到服务端

```java
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
```
绑定服务后就可以使用服务端的信使发送消息给服务端了，因为实现借助Handler实现，所以需要制定相应的协议，这里代码发`message.what=1`为传输客户端的信使，`message.what = 2`为请求数据；
```java
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
```
实现还是比较简单的，两个APP间的通信也是一样的实现，只是需要对service进行配置，设置可以被其他APP启动
```xml
        <service android:name=".service.MessengerService"
            android:enabled="true"
            android:exported="true"
            android:process="com.yorhp.messenger.name">
            <intent-filter>
                <action android:name="com.yorhp.messenger.name"/>
            </intent-filter>
        </service>
```
启动方式也有所不同，改为隐式启动，其他都一样
```java
        Intent intentMessenger = new Intent();
        intentMessenger.setAction("com.yorhp.messenger.name");
        intentMessenger.setPackage("com.yorhp.interprocesscommunication");
        bindService(intentMessenger, mMessengerServiceConnection, BIND_AUTO_CREATE);
```

## AIDL
### 实现思路
服务端要创建一个Service用来监听客户端的连接请求，然后创建一个AIDL文件，将暴露给客户端的接口在这个AIDL文件中申明，最后在Service中实现接口即可；

客户端需要绑定这个服务，然后将服务器返回的Binder对象转成AIDL接口所属的类型，然后就可以调用AIDL中的接口了；AIDL的接口方法是在服务端的Binder线程池中执行的，因此当多个客户端同时连接的时候，会存在多个线程同时访问的情形，所以看实现的功能可能需要考虑多线程问题。

### 具体实现
创建AIDL，先在main文件夹下面创建一个`aidl`的文件夹，然后新建一个AIDL文件，里面会有一个默认的接口，在里面新建接口；系统会在`/app/build/generated/aidl_source_output_dir/debug/out/com/yorhp/interprocesscommunication/`下面生成Java文件,如果没有可以rebuild一下

```java
// IMyAidlInterface.aidl
package com.yorhp.interprocesscommunication;
// Declare any non-default types here with import statements

interface IMyAidlInterface {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    void basicTypes(int anInt, long aLong, boolean aBoolean, float aFloat,
            double aDouble, String aString);

    /**
     *
     *获取姓名
     */
     String getName(String nickName);
}
```
然后新建Service，实现这个AIDL接口
```java
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
```
同样，Service申明为单独进程运行
```xml
     <service android:name=".service.AIDLService"
            android:enabled="true"
            android:exported="true"
            android:process="com.yorhp.aidl.test.service">
            <intent-filter>
                <action android:name="com.yorhp.aild.name"/>
            </intent-filter>
        </service>
```
客户端在服务绑定的时候获取到AIDL接口对应的对象，调用接口即可
```java
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
```

如果另一个APP访问这个进程需要将这个aidl文件都复制到另一个APP中，并且包名要一样，然后隐式调用服务就好了
```java
        Intent intent = new Intent();
        intent.setAction("com.yorhp.aild.name");
        intent.setPackage("com.yorhp.interprocesscommunication");
        bindService(intent, bindService, BIND_AUTO_CREATE);
```

AIDL文件支持：

 - 基本数据类型
 - String和CharSequence、
 - List：只支持ArrayList，里面的元素都需要被AIDL支持、
 - Map：只支持HashMap，里面的元素都需要被AIDL支持、
 - Parcelable：所有实现了Parcelable的对象、
 - AIDL:AIDL接口本身也可以；

#### Parcelable对象
自定义的对象需要实现Parcelable接口，举个例子，新建一个User对象，实现Parcelable接口

```java
public class User implements Parcelable {
    /**
     * 姓名
     */
    private String name;
    /**
     * 年龄
     */
    private int age;

    public User(String name, int age) {
        this.name = name;
        this.age = age;
    }

    protected User(Parcel in) {
        name = in.readString();
        age = in.readInt();
    }

    public static final Creator<User> CREATOR = new Creator<User>() {
        @Override
        public User createFromParcel(Parcel in) {
            return new User(in);
        }

        @Override
        public User[] newArray(int size) {
            return new User[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeInt(age);
    }

}

```

在AIDL文件中新增接口，在AIDL中引用Parcelable对象和AIDL对象的时候必须要显式的`import`进来，而且Parcelable对象也需要新建一个同名的AIDL文件，并在其中申明它为parcelable对象；

感觉这里关于包名的设定还是有点坑，如果写写demo把文件都放在一个文件夹下面没什么问题，但是稍微修改一下目录就会出问题，这里还是有一定的规则的，我把文件目录展示出来；
![截屏2020-03-17下午11.54.54.png-45.8kB](http://static.zybuluo.com/Tyhj/ej9axbmtzeraouqnrhaiwhv4/%E6%88%AA%E5%B1%8F2020-03-17%E4%B8%8B%E5%8D%8811.54.54.png)

首先新建一个User.aidl文件,里面的`package`是可以不和真实路径一致的，但是必须和User.java文件的包名一致，不然会报错
```java
// User.aidl
//这个包名必须和java文件的包名一致,路径和真实路径不一样也可以
package com.yorhp.interprocesscommunication.bean;
// Declare any non-default types here with import statements

parcelable User;
```

然后修改AIDL接口，需要显式引用User对象，这个对象必须是User.aidl的文件路径，不然会报错，其实引用就是这个AIDL对象，不然申明了干什么
```java
// IMyAidlInterface.aidl
package com.yorhp.interprocesscommunication;
//这个包名必须是User.aidl文件的路径
import com.yorhp.interprocesscommunication.bean.User;

// Declare any non-default types here with import statements

interface IMyAidlInterface {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    void basicTypes(int anInt, long aLong, boolean aBoolean, float aFloat,
            double aDouble, String aString);

    /**
     *
     *获取姓名
     */
     String getName(String nickName);


    /**
     *获取用户
     */
     User getUserById(int id);

}
```

同样Service实现新的接口
```java
public class AIDLService extends Service {

    IMyAidlInterface.Stub stub=new IMyAidlInterface.Stub() {
        @Override
        public void basicTypes(int anInt, long aLong, boolean aBoolean, float aFloat, double aDouble, String aString) throws RemoteException {

        }

        @Override
        public String getName(String nickName) throws RemoteException {
            return "aidl is "+nickName;
        }

        @Override
        public User getUserById(int id) throws RemoteException {
            return new User("Tyhj",1);
        }
    };


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return stub;
    }
}
```
客户端进行接口调用
```java
//绑定服务
        bindService(new Intent(MainActivity.this, AIDLService.class), mAIDLServiceConnection, BIND_AUTO_CREATE);
        //AIDL进行通信
        findViewById(R.id.btnAIDL).setOnClickListener(v -> {
            try {
                String name = null;
                User user=myAidlInterface.getUserById(0);
                name = user.getName();
                Toast.makeText(MainActivity.this, name, Toast.LENGTH_SHORT).show();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        });
```

同样的如果要在另一个APP访问服务，需要把AIDL和相关的类都拷贝过去，Parcelable类也需要放在同样的包名下
#### AIDL对象
在方法中传入一个监听接口是比较常用的方法，但是在AIDL中是不支持普通接口的，只支持AIDL接口；新建一个AIDL接口，用于监听用户的变化，当用户改变时，把最新的用户信息通知到客户端；
```java
import com.yorhp.interprocesscommunication.bean.User;
// Declare any non-default types here with import statements

interface IOnUserChangedListener {
   /**
    *用户改变监听
    */
    void onUserChanged(in User user);
}
```
#### AIDL中的in、out、inout的区别
其中AIDL中除了基本数据类型和String外，其他参数必须标上方向：in、out或者inout;定向tag是AIDL中语法的一部分，其中in、out、inout是三个定向tag。在官网上关于Android定向tag的定义是这样的：
> All non-primitive parameters require a directional tag indicating which way the data goes .
Either in , out , or inout . Primitives are in by default , and connot be otherwise .

意思就是所有非基本类型的参数都需要一个定向tag来表明数据是如何走向的，要不是in，out或者inout。基本数据类型默认是in，而且不能是其他tag。

定向 tag 表示了在跨进程通信中数据的流向，其中 in 表示数据只能由客户端流向服务端， out 表示数据只能由服务端流向客户端，而 inout 则表示数据可以在服务端与客户端之间双向流通。其中的数据流向是针对在客户端中的那个传入方法的对象而言的。

对于in，服务端将会收到客户端对象的完整数据，但是客户端对象不会因为服务端对传参的修改而发生变动。类似的行为在Java中的表现是，在Java方法中，对传进来的参数进行了深复制，传进来的参数不会受到深复制后的对象的影响。这和in的行为有点类似。

对于out，服务端将会收到客户端对象，该对象不为空，但是它里面的字段为空，但是在服务端对该对象作任何修改之后客户端的传参对象都会同步改动。类似的行为在Java中的表现是，在Java方法中，对传进来的参数进行忽略，并new一个新对象，所有的操作都是围绕着这个新对象进行的，最后将该新对象赋值给传参对象。

对于inout ，服务端将会接收到客户端传来对象的完整信息，并且客户端将会同步服务端对该对象的任何变动。类似的行为在Java中的表现是，在Java方法中，对传进来的参数进行修改并返回。

> 参考文章 [AIDL中的in、out、inout的区别](https://www.jianshu.com/p/a61da801b919)

然后继续修改`IMyAidlInterface`AIDL文件，新增两个方法，注册和取消注册
```java
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
```
然后修改`AIDLService`文件，实现新的接口，模拟了数据改变
```java
public class AIDLService extends Service {

    /**
     * 监听集合，自动进行线程同步，线程安全
     */
    private CopyOnWriteArrayList<IOnUserChangedListener> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        //开启线程模拟用户数据改变，回调
        new Thread(()->{
            while (true){
                for (IOnUserChangedListener listener:listeners){
                    try {
                        //返回用户数据
                        listener.onUserChanged(new User("Tyhj"+System.currentTimeMillis(),10));
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                SystemClock.sleep(2000);
            }
        }).start();
    }

    IMyAidlInterface.Stub stub = new IMyAidlInterface.Stub() {

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
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        }

        @Override
        public void unRegisterListener(IOnUserChangedListener listener) throws RemoteException {
            listeners.remove(listener);
        }
    };


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return stub;
    }
}
```
然后在客户端调用新的方法，返回的数据不在主线程需要做线程切换
```java
/**
     * AIDL服务连接监听
     */
    private ServiceConnection mAIDLServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            myAidlInterface = IMyAidlInterface.Stub.asInterface(service);
            //Log.i("MainActivity","service connected");
            try {
            //注册监听
                myAidlInterface.registerListener(new IOnUserChangedListener.Stub() {
                    @Override
                    public void onUserChanged(User user) throws RemoteException {
                        handler.post(()->{
                            Toast.makeText(MainActivity.this, user.getName(), Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            Toast.makeText(MainActivity.this, "service connected", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };
```
但是当取消注册监听的时候失败了，其实仔细看取消注册接口的实现`listeners.remove(listener);`，在不同的进程里面，这两个listener对象肯定不可能指向一个地址的，传过来的这个对象肯定是会被转换并生成成新对象的，因为跨进程传对象本质就是序列化和反序列化，所以是会失败的；可以使用`RemoteCallbackList`，是系统专门用于提供删除跨进程listener的接口；它的实现是一个Map，key存了传入`listener.asBinder()`，就是这个AIDL对象的Binder对象，这个对象对于同一个客户端是不变的，value就是保存了这个AIDL对象的一个封装对象；
```java
            IBinder binder = callback.asBinder();
            try {
                Callback cb = new Callback(callback, cookie);
                binder.linkToDeath(cb, 0);
                mCallbacks.put(binder, cb);
                return true;
            } catch (RemoteException e) {
                return false;
            }
```
`RemoteCallbackList`不是一个List对象，所以操作也有些不同，修改服务端代码
```java
public class AIDLService extends Service {

/**
 * 监听集合，自动进行线程同步，线程安全
 */
private RemoteCallbackList<IOnUserChangedListener> listeners = new RemoteCallbackList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        //开启线程模拟用户数据改变，回调
        new Thread(()->{
            while (true){
                final int n=listeners.beginBroadcast();
                for (int i=0;i<n;i++){
                    try {
                        IOnUserChangedListener listener=listeners.getBroadcastItem(i);
                        //返回用户数据
                        listener.onUserChanged(new User("Tyhj"+System.currentTimeMillis(),10));
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                listeners.finishBroadcast();
                SystemClock.sleep(2000);
            }
        }).start();
    }

    IMyAidlInterface.Stub stub = new IMyAidlInterface.Stub() {

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
    };


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return stub;
    }
}
```
里面需要注意的是`listeners.beginBroadcast();`和`listeners.finishBroadcast();`必须配对使用；

## 断线重连
为了程序的健壮性，我们还需要考虑服务意外死亡的情况；当服务意外停止的时候我们需要重新连接服务，第一种方法比较简单，就是在`onServiceDisconnected`方法中重连服务；第二种方法就是给Binder设置DeathRecipient监听，当Binder死亡时，我们会收到binderDied方法的回调；两种方式都可以使用，区别在于**onServiceDisconnected**是在客户端的主线程中被调用的，而**binderDied**是在客户端的Binder线程池中被回调，使用的时候需要注意一下；
```java
ServiceConnection mServiceConnection=new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            try {
                iBinder.linkToDeath(new IBinder.DeathRecipient() {
                    @Override
                    public void binderDied() {
                        //服务关闭，可以在此重连服务
                    }
                },0);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            //服务关闭，可以在此重连服务
        }
    };
```

## 权限验证
默认情况下，远程服务是任何人都可以连接的，为了保证服务的安全，我们需要在服务中加上权限验证；
第一种方法是在**onBind**方法中验证，如果验证不通过就返回null，这样验证失败的客户端就无法绑定服务；验证方式可以使用权限验证，我们在AndroidManifest文件中申明权限，随便取一个名字；
```xml
 <permission
        android:name="com.yorhp.aidl.permission.ACCESS_USER_INFO"
        android:protectionLevel="normal" />
```
然后就可以在**onBind**方法中进行验证，如果客户端连接需要在**AndroidManifest**里面申明该权限
```java
  @Override
    public IBinder onBind(Intent intent) {
        int check=checkCallingOrSelfPermission("com.yorhp.aidl.permission.ACCESS_USER_INFO");
        if(check== PackageManager.PERMISSION_DENIED){
        //权限申请失败，返回null
            return null;
        }
        return mBidner;
    }
```
第二种方式是在AIDL接口的**onTransact**方法中进行验证，如果返回了false，服务端就不会终止执行AIDL中的方法，从而达到保护服务端的目的；具体验证方法也可以使用权限验证，和上面是一样的；在这个方法里面我们通过` getCallingPid()`和`getCallingUid()`可以拿到客户端所属应用的Pid和Uid，通过这两个参数可以做一些验证操作，比如可以验证包名
```java
  @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            String packageName = null;
            String[] packages = getPackageManager().getPackagesForUid(getCallingUid());
            if (packages != null && packages.length > 0) {
                packageName = packages[0];
            }

            if (packageName == null || !packageName.startsWith("com.yorhp")) {
                //返回失败
                return false;
            }
            return super.onTransact(code, data, reply, flags);
        }
```




## 总结
讲道理，其实仔细看看还是挺简单的

## 项目地址
服务端(也包含客户端)地址：[https://github.com/tyhjh/AIDL-Service](https://github.com/tyhjh/AIDL-Service)
客户端地址：[https://github.com/tyhjh/AIDL-Client](https://github.com/tyhjh/AIDL-Client)