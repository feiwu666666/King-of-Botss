package com.example.backend.consumer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.backend.consumer.util.Game;
import com.example.backend.consumer.util.JwtAuthentication;
import com.example.backend.mapper.RecordMapper;
import com.example.backend.mapper.UserMapper;
import com.example.backend.pojo.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@ServerEndpoint("/websocket/{token}")  // 注意不要以'/'结尾
public class WebSocketServer {

    //每一个WebSocketServer实例都开启一个新的线程


    // ConcurrentHashMap用来映射线程安全的哈希map

    final public static ConcurrentHashMap<Integer,WebSocketServer> users = new ConcurrentHashMap<>();

    // 维护线程安全的线程池  当作匹配池

    final private static CopyOnWriteArrayList matchpool = new CopyOnWriteArrayList();

    private User user;

    private Game game = null;


    // 由于WebSocketServer不是spring中的类库（非单例模式）,所以不能直接注入
    /**
     * 非单例模式下 注入Mapper的方法
     */
    private static UserMapper userMapper;
    public static RecordMapper recordMapper;
    @Autowired
    public void setUserMapper(UserMapper userMapper){
        WebSocketServer.userMapper = userMapper;
    }
    @Autowired
    public void setRecordMapper(RecordMapper recordMapper){
        WebSocketServer.recordMapper = recordMapper;
    };
    // 用session维护前端传来的链接

    private Session session = null;


    @OnOpen
    public void onOpen(Session session, @PathParam("token") String token) throws IOException {
        // 从前端获取链接信息
        this.session = session;
        System.out.println("connected!");
        // 建立连接
        Integer userId = JwtAuthentication.getId(token);
        this.user = userMapper.selectById(userId);
        // 找到用户后 将用户id映射到map中
        if(this.user != null){
            users.put(userId,this);
        }else{
            this.session.close();  // 如果没有找到token对应的用户  就断开该连接
        }
        System.out.println(users);
    }

    @OnClose
    public void onClose() {
        // 关闭链接
        System.out.println("close");
        if(this.user != null){
            users.remove(this.user);
        }

    }

    private void startMatching(){
        System.out.println("start");
        matchpool.add(this.user);
        while(matchpool.size() >= 2){
            System.out.println("mapchpolSize:" + matchpool.size());

            Iterator<User> it = matchpool.iterator();   // 创建一个迭代器，迭代存储线程池中的用户
            User a = it.next(),b = it.next();
            matchpool.remove(a);
            matchpool.remove(b);
            // 创建地图
            Game game = new Game(13,14,20,a.getId(),b.getId());
            game.createGameMap();
            users.get(a.getId()).game = game;
            users.get(b.getId()).game = game;

            game.start();
            JSONObject respGame = new JSONObject();
            respGame.put("a_id",game.getPlayerA().getId());
            respGame.put("a_sx",game.getPlayerA().getSx());
            respGame.put("a_sy",game.getPlayerA().getSy());
            respGame.put("b_id",game.getPlayerB().getId());
            respGame.put("b_sx",game.getPlayerB().getSx());
            respGame.put("b_sy",game.getPlayerB().getSy());
            respGame.put("gamemap",game.getG());

            // 匹配成功之后 将双方信息发送给前端

            JSONObject respA = new JSONObject();
            respA.put("event","start-matching");
            respA.put("opponent_username",b.getUsername());
            respA.put("opponent_photo",b.getPhoto());
            respA.put("game",respGame);
            users.get(a.getId()).sendMessage(respA.toJSONString());

            JSONObject respB = new JSONObject();
            respB.put("event","start-matching");
            respB.put("opponent_username",a.getUsername());
            respB.put("opponent_photo",a.getPhoto());
            respB.put("game",respGame);
            users.get(b.getId()).sendMessage(respB.toJSONString());
        }
    }
    private void stopMatching(){
        System.out.println("stop");
        matchpool.remove(this.user);
    }

    private void move(int direction){  // 设置方向
        if(game.getPlayerA().getId().equals(this.user.getId())){  // 判断当前蛇是哪一方
            game.setNextStepA(direction);
        }else if(game.getPlayerB().getId().equals(this.user.getId())){
            game.setNextStepB(direction);
        }

    }
    @OnMessage
    public void onMessage(String message, Session session) {   //当作路由 用来判断前后端通信内容
        // 从Client接收消息
        System.out.println("receive message");

        JSONObject data = JSON.parseObject(message);
        String event = data.getString("event");
        if("start-matching".equals(event)){
            startMatching();
        }else if("stop-matching".equals(event)){
            stopMatching();
        }else if("move".equals(event)){
            move(data.getInteger("direction"));
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        error.printStackTrace();
    }

    public void sendMessage(String message){
        // 给session加上互斥锁，让其他线程等待， 直到事件完成 锁被释放，其他线程才能操作锁中数据
        synchronized (this.session){
            try{
                // 将当前message发送到当前绑定的session中，即向前端发送信息
                this.session.getBasicRemote().sendText(message);
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }
}