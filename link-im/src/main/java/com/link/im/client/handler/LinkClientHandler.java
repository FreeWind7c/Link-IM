package com.link.im.client.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.nio.charset.StandardCharsets;

public class LinkClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

    @Override
    public void channelActive(ChannelHandlerContext ctx) {

        System.out.println("客户端连接成功");
    }

    @Override
    protected void channelRead0(
            ChannelHandlerContext ctx,
            ByteBuf msg) {

        System.out.println("收到服务端消息：" + msg.toString(StandardCharsets.UTF_8));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {

        System.out.println("连接断开");
    }

    @Override
    public void exceptionCaught(
            ChannelHandlerContext ctx,
            Throwable cause) {

        cause.printStackTrace();

        ctx.close();
    }
}