package com.sky.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket服务
 */
@Slf4j
@Component
@ServerEndpoint("/ws/{sid}")
public class WebSocketServer {

    /**
     * 存放会话对象。
     * 连接的建立、断开与消息推送分别在不同线程执行，必须使用 ConcurrentHashMap，
     * 否则并发增删会丢失会话，遍历时还可能抛出 ConcurrentModificationException。
     */
    private static final Map<String, Session> SESSION_MAP = new ConcurrentHashMap<>();

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        log.debug("客户端：{}建立连接", sid);
        SESSION_MAP.put(sid, session);
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        log.debug("收到来自客户端：{}的信息:{}", sid, message);
    }

    /**
     * 连接关闭调用的方法
     *
     * @param sid 客户端标识
     */
    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        log.debug("连接断开:{}", sid);
        SESSION_MAP.remove(sid);
    }

    /**
     * 群发消息
     *
     * @param message 消息内容
     */
    public void sendToAllClient(String message) {
        // ConcurrentHashMap 的 values() 是弱一致性视图，遍历期间其他线程增删不会抛异常
        for (Session session : SESSION_MAP.values()) {
            try {
                //服务器向客户端发送消息
                session.getBasicRemote().sendText(message);
            } catch (Exception e) {
                log.error("WebSocket 消息推送失败, sid={}", session.getId(), e);
            }
        }
    }

}
