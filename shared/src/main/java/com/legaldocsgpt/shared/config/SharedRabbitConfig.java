package com.legaldocsgpt.shared.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(MessageConverter.class)
public class SharedRabbitConfig {

    public static final String QUEUE_NAME         = "document_generation_queue";
    public static final String EXCHANGE_NAME      = "document_exchange";
    public static final String ROUTING_KEY        = "document_routing_key";

    public static final String DLQ_NAME           = "document_generation_queue.dlq";
    public static final String DLX_NAME           = "document_exchange.dlx";
    public static final String DLQ_ROUTING_KEY    = "document_routing_key.dlq";


    @Bean
    public Queue documentQueue() {
        return QueueBuilder.durable(QUEUE_NAME)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public TopicExchange documentExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Binding documentBinding(Queue documentQueue, TopicExchange documentExchange) {
        return BindingBuilder.bind(documentQueue).to(documentExchange).with(ROUTING_KEY);
    }


    @Bean
    public Queue documentDlq() {
        return QueueBuilder.durable(DLQ_NAME).build();
    }

    @Bean
    public DirectExchange documentDlx() {
        return new DirectExchange(DLX_NAME);
    }

    @Bean
    public Binding dlqBinding(Queue documentDlq, DirectExchange documentDlx) {
        return BindingBuilder.bind(documentDlq).to(documentDlx).with(DLQ_ROUTING_KEY);
    }


    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}