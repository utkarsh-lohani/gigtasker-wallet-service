package com.gigtasker.walletservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String TASK_EXCHANGE_NAME = "task-exchange";
    public static final String TASK_COMPLETED_KEY = "task.completed";
    public static final String WALLET_QUEUE = "wallet.task.completed.queue";
    public static final String TASK_CANCELLED_KEY = "task.cancelled";
    public static final String WALLET_REFUND_QUEUE = "wallet.task.cancelled.queue";

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public Queue walletQueue() {
        return new Queue(WALLET_QUEUE, true);
    }

    @Bean
    public TopicExchange taskExchange() {
        return new TopicExchange(TASK_EXCHANGE_NAME);
    }

    @Bean
    public Binding walletBinding() {
        return BindingBuilder.bind(walletQueue()).to(taskExchange()).with(TASK_COMPLETED_KEY);
    }

    @Bean
    public Queue refundQueue() { return new Queue(WALLET_REFUND_QUEUE, true); }

    @Bean
    public Binding refundBinding() {
        return BindingBuilder.bind(refundQueue()).to(taskExchange()).with(TASK_CANCELLED_KEY);
    }
}
