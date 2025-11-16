package com.ghostchu.btn.sparkle.config;

import jakarta.websocket.server.ServerEndpointConfig;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Custom ServerEndpointConfigurator to enable Spring dependency injection in WebSocket endpoints
 */
@Component
public class SpringWebSocketServerEndpointConfigurator extends ServerEndpointConfig.Configurator implements ApplicationContextAware {

    private static volatile BeanFactory beanFactory;

    @Override
    public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
        return beanFactory.getBean(endpointClass);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringWebSocketServerEndpointConfigurator.beanFactory = applicationContext.getAutowireCapableBeanFactory();
    }
}

