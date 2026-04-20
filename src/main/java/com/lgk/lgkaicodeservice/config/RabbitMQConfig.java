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
 * <p>
 * 消费模式：单条消费 + 内存攒批
 * <pre>
 * RabbitMQ 逐条投递 → ThumbEventConsumer.onMessage() 单条接收入内存队列
 *                                    ↓
 *               定时任务 flushBuffer() 每秒批量取出处理（真正攒批）
 * </pre>
 * 不再使用 Spring AMQP 的 batchListener 模式，因为生产者逐条发送的消息是独立 Delivery，
 * batchSize 参数无法将多条独立 Delivery 攒成真正的批量 List，形同虚设。
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

    // ==================== 单条消费容器工厂 ====================

    /**
     * 点赞事件单条消费容器工厂
     * <p>
     * 消费端单条接收消息 → 攒入内存 ConcurrentLinkedQueue → 定时任务批量刷写数据库。
     * 这种方式比 Spring AMQP 的 batchListener 更可靠：
     * - batchListener 依赖生产者使用 BatchingRabbitTemplate 发送批量 Delivery，
     *   但我们用的是普通 RabbitTemplate 逐条发送，每条是独立 Delivery，batchSize 形同虚设。
     * - 内存攒批完全不依赖消息发送方式，无论生产者怎么发，消费端都能真正攒批。
     * <p>
     * 关键参数：
     * - prefetchCount=50：Channel 预取上限，保证消息及时送达消费端
     * - concurrency=3：3个消费线程并发拉取，快速入队
     * - acknowledgeMode=AUTO：消息拉取后自动确认，由内存队列保证不丢失
     */
    @Bean
    public SimpleRabbitListenerContainerFactory thumbSingleContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setPrefetchCount(50);
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.AUTO);
        return factory;
    }
}
