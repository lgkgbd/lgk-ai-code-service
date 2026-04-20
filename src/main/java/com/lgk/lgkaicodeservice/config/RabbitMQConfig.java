package com.lgk.lgkaicodeservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置类
 * <p>
 * 点赞消息队列拓扑（合并为单一队列）：
 * <pre>
 * thumb.exchange (Direct)
 *   └── thumb.event → thumb.event.queue
 *                        ↓ 消费失败
 *                  thumb.dlx → thumb.dead.queue
 * </pre>
 */
@Configuration
public class RabbitMQConfig {

    // ==================== Exchange ====================

    public static final String THUMB_EXCHANGE = "thumb.exchange";

    /**
     * 死信 Exchange，用于接收消费失败的消息
     */
    public static final String THUMB_DLX_EXCHANGE = "thumb.dlx.exchange";

    // ==================== Queue ====================

    public static final String THUMB_EVENT_QUEUE = "thumb.event.queue";

    /**
     * 死信队列，消费失败的消息最终进入这里
     */
    public static final String THUMB_DEAD_QUEUE = "thumb.dead.queue";

    // ==================== Routing Key ====================

    public static final String THUMB_EVENT_ROUTING_KEY = "thumb.event";
    public static final String THUMB_DEAD_ROUTING_KEY = "thumb.dead";

    // ==================== Exchange Bean ====================

    @Bean
    public DirectExchange thumbExchange() {
        return new DirectExchange(THUMB_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange thumbDlxExchange() {
        return new DirectExchange(THUMB_DLX_EXCHANGE, true, false);
    }

    // ==================== Queue Bean ====================

    /**
     * 点赞事件队列（统一队列，点赞和取消点赞都走这里）
     * 绑定死信 Exchange，消费失败时消息进入死信队列
     */
    @Bean
    public Queue thumbEventQueue() {
        return QueueBuilder.durable(THUMB_EVENT_QUEUE)
                .deadLetterExchange(THUMB_DLX_EXCHANGE)
                .deadLetterRoutingKey(THUMB_DEAD_ROUTING_KEY)
                .build();
    }

    /**
     * 死信队列
     */
    @Bean
    public Queue thumbDeadQueue() {
        return QueueBuilder.durable(THUMB_DEAD_QUEUE).build();
    }

    // ==================== Binding ====================

    @Bean
    public Binding thumbEventBinding() {
        return BindingBuilder.bind(thumbEventQueue())
                .to(thumbExchange())
                .with(THUMB_EVENT_ROUTING_KEY);
    }

    @Bean
    public Binding thumbDeadBinding() {
        return BindingBuilder.bind(thumbDeadQueue())
                .to(thumbDlxExchange())
                .with(THUMB_DEAD_ROUTING_KEY);
    }

    // ==================== JSON 序列化 ====================

    @Bean
    public MessageConverter jsonMessageConverter() {
        // 批量消费必须关闭 type header 匹配检查，否则 List 反序列化时类型信息不一致会报错
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setClassMapper(null);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    // ==================== 批量消费容器工厂 ====================

    /**
     * 点赞事件批量消费容器工厂
     * <p>
     * Spring AMQP 批量消费工作原理：
     * 1. 容器通过 Channel 的 basic.qos 预取消息（prefetchCount 控制 Channel 中未确认消息上限）
     * 2. 容器循环调用 receive() 取消息，每次循环最多取 batchSize 条
     * 3. 取到的消息攒成 List 后一次性投递给消费者的 List<ThumbEvent> 参数方法
     * 4. receiveTimeout 控制单次 receive() 的等待超时，低流量时避免消息积压
     * <p>
     * 关键参数：
     * - batchListener=true：启用批量投递模式
     * - batchSize=50：每次循环最多取 50 条消息攒批（核心参数！默认为1，不设就不攒批）
     * - prefetchCount=100：Channel 预取上限（>= batchSize）
     * - receiveTimeout=10s：单次 receive 等待超时，低流量时不会积压太久
     * - concurrency=3：3个消费线程并发处理，每个线程独立攒批
     */
    @Bean
    public SimpleRabbitListenerContainerFactory thumbBatchContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setBatchListener(true);
        factory.setBatchSize(50);               // 核心：每次循环最多取50条攒批
        factory.setPrefetchCount(100);           // Channel预取上限，>= batchSize
        factory.setReceiveTimeout(10_000L);      // 10秒超时，低流量也不会积压
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.AUTO);
        return factory;
    }
}
