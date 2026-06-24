package com.link.core.client.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;

public class LinkClientInitializer
        extends ChannelInitializer {



    @Override
    protected void initChannel(Channel ch) {

        ChannelPipeline pipeline = ch.pipeline();

        pipeline.addLast(new LinkClientHandler());
    }
}