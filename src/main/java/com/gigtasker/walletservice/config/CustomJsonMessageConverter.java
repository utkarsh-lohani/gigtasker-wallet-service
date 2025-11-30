package com.gigtasker.walletservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;

public class CustomJsonMessageConverter implements MessageConverter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Message toMessage(Object object, MessageProperties props) {
        try {
            props.setContentType("application/json");
            return new Message(
                    objectMapper.writeValueAsBytes(object),
                    props
            );
        } catch (Exception e) {
            throw new MessageConversionException("Failed to convert to JSON", e);
        }
    }

    @Override
    public Object fromMessage(Message message) {
        try {
            return objectMapper.readTree(message.getBody());
        } catch (Exception e) {
            throw new MessageConversionException("Failed to convert JSON", e);
        }
    }
}
