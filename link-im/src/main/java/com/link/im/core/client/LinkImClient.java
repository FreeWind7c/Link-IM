package com.link.im.core.client;

import com.google.gson.Gson;
import com.link.core.client.handler.LinkClientInitializer;
import com.link.core.config.LinkCoreConfig;
import com.link.common.core.event.EventType;
import com.link.core.model.data.PackData;
import com.link.core.model.heart.LinkPing;

import com.link.im.entity.data.TextData;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.entity.message.type.MessageType;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月16日
 */
public class LinkImClient {

    private final String host;
    private final int port;

    private EventLoopGroup group;
    private Bootstrap bootstrap;
    private Channel channel;

    private LinkCoreConfig config = new LinkCoreConfig();

    public LinkImClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() {

        group = new NioEventLoopGroup();

        bootstrap = new Bootstrap();

        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new LinkClientInitializer());

        connect();
    }

    private void connect() {

        bootstrap.connect(host, port).addListener((ChannelFutureListener) future -> {

            if (future.isSuccess()) {

                System.out.println("连接成功");

                channel = future.channel();

                login(EventType.LOGIN.getAction());
                Thread.sleep(1000);
                ping(EventType.HEARTBEAT.getAction());
                Thread.sleep(1000);


            } else {

                System.out.println("连接失败，5秒后重连");

                future.channel()
                        .eventLoop()
                        .schedule(this::connect,
                                5,
                                TimeUnit.SECONDS);
            }

        });
    }



    private ByteBuf sender(PackData body) {
        ByteBuf buf = channel.alloc().buffer(
                LinkCoreConfig.PROTO_FRAME_LENGTH + body.getLength());
        buf.writeShort(body.getMagic());
        buf.writeShort(body.getAction());
        buf.writeInt(body.getLength());
        buf.writeBytes(body.getBody());
        return buf;
    }

    private void ping(short action) {
        if (channel == null || !channel.isActive()) {
            System.out.println("连接未建立");
            return;
        }

        LinkPing linkPing = new LinkPing();
        byte[] bytes = this.config.getLinkSerializer().serialize(linkPing);

        PackData body = new PackData(
                this.config.getMagic(),
                action,
                bytes.length,
                bytes);

        // 2. 按 magic(2) + action(2) + length(4) + body(n) 的顺序写入 ByteBuf
        ByteBuf buf = channel.alloc().buffer(
                LinkCoreConfig.PROTO_FRAME_LENGTH + body.getLength());
        buf.writeShort(body.getMagic());
        buf.writeShort(body.getAction());
        buf.writeInt(body.getLength());
        buf.writeBytes(body.getBody());

        // 3. 发送
        channel.writeAndFlush(buf);
    }

    public void login(short action) {

        if (channel == null || !channel.isActive()) {
            System.out.println("连接未建立");
            return;
        }


        HashMap<String, String> map = new HashMap<>();
        map.put("userId","111");
        map.put("token","sdjhfnadskjf-01221312j2eno132");
        Gson gson = new Gson();
        String json = gson.toJson(map);
        // 1. 按协议封装消息体
        byte[] bodyBytes = json.getBytes(StandardCharsets.UTF_8);

        PackData body = new PackData(
                this.config.getMagic(),
                action,
                bodyBytes.length,
                bodyBytes);

        // 2. 按 magic(2) + action(2) + length(4) + body(n) 的顺序写入 ByteBuf
        ByteBuf buf = channel.alloc().buffer(
                LinkCoreConfig.PROTO_FRAME_LENGTH + body.getLength());
        buf.writeShort(body.getMagic());
        buf.writeShort(body.getAction());
        buf.writeInt(body.getLength());
        buf.writeBytes(body.getBody());

        // 3. 发送
        channel.writeAndFlush(buf);
    }

    public void shutdown() {

        if (group != null) {
            group.shutdownGracefully();
        }
    }

    public static void main(String[] args) {

        LinkImClient client =
                new LinkImClient("127.0.0.1", 8899);

        client.start();


    }
}
