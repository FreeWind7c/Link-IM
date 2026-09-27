package com.link.restapi.config.sentinel;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.link.base.template.SentinelMethodTemplate;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SentinelRuleConfig {

    @PostConstruct
    public void init() {

        List<FlowRule> rules = new ArrayList<>();

        // 查询消息：500 QPS
        FlowRule messageHistoryRule = new FlowRule();
        messageHistoryRule.setResource(SentinelMethodTemplate.PULL_MESSAGE);
        messageHistoryRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        messageHistoryRule.setCount(500);
        rules.add(messageHistoryRule);


        // 加载规则
        FlowRuleManager.loadRules(rules);
    }
}