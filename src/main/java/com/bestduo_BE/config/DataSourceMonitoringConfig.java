package com.bestduo_BE.config;

import com.bestduo_BE.monitoring.SqlExecutionLoggingListener;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import net.ttddyy.dsproxy.listener.logging.DefaultQueryLogEntryCreator;
import net.ttddyy.dsproxy.listener.logging.SLF4JLogLevel;
import net.ttddyy.dsproxy.listener.logging.SLF4JQueryLoggingListener;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class DataSourceMonitoringConfig {

  private final SqlExecutionLoggingListener sqlExecutionLoggingListener;

  @Bean
  public BeanPostProcessor dataSourceProxyBeanPostProcessor() {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource dataSource && !(dataSource instanceof ProxyDataSource)) {
          return instrumentedDataSource(dataSource);
        }
        return bean;
      }
    };
  }

  private DataSource instrumentedDataSource(DataSource dataSource) {
    SLF4JQueryLoggingListener loggingListener = new SLF4JQueryLoggingListener();
    loggingListener.setLogger("com.bestduo_BE.SQL");
    loggingListener.setLogLevel(SLF4JLogLevel.DEBUG);

    DefaultQueryLogEntryCreator entryCreator = new DefaultQueryLogEntryCreator();
    entryCreator.setMultiline(true);
    loggingListener.setQueryLogEntryCreator(entryCreator);

    return ProxyDataSourceBuilder.create(dataSource)
        .name("BESTDUO-DS")
        .listener(loggingListener)
        .listener(sqlExecutionLoggingListener)
        .countQuery()
        .build();
  }
}
