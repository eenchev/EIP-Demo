package dev.evgeni.integrationsandbox.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.aopalliance.aop.Advice;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.integration.annotation.Filter;
import org.springframework.integration.annotation.InboundChannelAdapter;
import org.springframework.integration.annotation.Poller;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.annotation.Transformer;
import org.springframework.integration.channel.NullChannel;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.handler.advice.RequestHandlerRetryAdvice;
import org.springframework.integration.http.dsl.Http;
import org.springframework.integration.http.outbound.HttpRequestExecutingMessageHandler;
import org.springframework.integration.kafka.inbound.KafkaMessageSource;
import org.springframework.integration.transformer.HeaderEnricher;
import org.springframework.integration.transformer.support.HeaderValueMessageProcessor;
import org.springframework.integration.transformer.support.StaticHeaderValueMessageProcessor;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ConsumerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.messaging.MessageChannel;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class KafkaToPersonapiConfig {

    private static final String KAFKA_BASE_URL = "http://localhost:9092";
    private static final String PERSONAPI_BASE_URL = "http://localhost:8080/";
    // I know this token will end up in Git, it is temporary
    private static final String PERSONAPI_TOKEN =
            "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJpdmFuIiwiaWF0IjoxNzA2MDI1NzQ0LCJleHAiOjE3MDYwNjE3NDR9.Bp40A4i6svXRblgrsyKvZADOXtaOYQ3IwCrj8tDnfhc";


    ObjectMapper mapper = new ObjectMapper();

    @Bean
    public MessageChannel httpResponseChannel() {
        return new NullChannel();
    }

    @Bean
    public SimpleRetryPolicy retryPolicy() {
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(5);
        return retryPolicy;
    }

    @Bean
    public FixedBackOffPolicy fixedBackOffPolicy() {
        FixedBackOffPolicy p = new FixedBackOffPolicy();
        p.setBackOffPeriod(1000);
        return p;
    }

    @Bean
    public RequestHandlerRetryAdvice retryAdvice(SimpleRetryPolicy retryPolicy,
            FixedBackOffPolicy fixedBackOffPolicy) {
        RequestHandlerRetryAdvice retryAdvice = new RequestHandlerRetryAdvice();
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setBackOffPolicy(fixedBackOffPolicy);
        retryTemplate.setRetryPolicy(retryPolicy);
        retryAdvice.setRetryTemplate(retryTemplate);
        return retryAdvice;
    }

    // @Bean
    // public Advice deadLetterAdvice() {
    // ExpressionEvaluatingRequestHandlerAdvice advice =
    // new ExpressionEvaluatingRequestHandlerAdvice();
    // advice.setFailureChannelName("deadLetter");
    // advice.setOnFailureExpressionString(
    // "payload + ' was bad, with reason: ' + #exception.cause.message");
    // advice.setTrapException(true);
    // return advice;
    // }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BASE_URL);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "0");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, String.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @InboundChannelAdapter(channel = "personsToPersonapi", poller = @Poller(fixedDelay = "5000"))
    @Bean
    public KafkaMessageSource<String, String> source(ConsumerFactory<String, String> cf) {
        ConsumerProperties consumerProperties = new ConsumerProperties("persons");
        consumerProperties.setGroupId("0");
        consumerProperties.setClientId("1");
        return new KafkaMessageSource<>(cf, consumerProperties);
    }

    @Filter(inputChannel = "personsToPersonapi", outputChannel = "validPersonsToPersonapi",
            discardChannel = "invalidPersonsToPersonapi")
    public boolean filterNonJson(String jsonString) {
        try {
            mapper.readTree(jsonString);
        } catch (JacksonException e) {
            return false;
        }
        return true;
    }

    @Bean
    @ServiceActivator(inputChannel = "validPersonsToPersonapi")
    public LoggingHandler httpResponseLogger() {
        LoggingHandler adapter = new LoggingHandler(LoggingHandler.Level.INFO);
        adapter.setLogExpressionString("payload + ' was success ' ");
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "invalidPersonsToPersonapi")
    public LoggingHandler logInvalidPersons() {
        LoggingHandler adapter = new LoggingHandler(LoggingHandler.Level.ERROR);
        // adapter.setShouldLogFullMessage(true);
        adapter.setLogExpressionString("payload + ' was failure ' ");
        return adapter;
    }

    @Bean
    @Transformer(inputChannel = "validPersonsToPersonapi", outputChannel = "toHttpRequest")
    public HeaderEnricher enrichHeaders() {
        Map<String, ? extends HeaderValueMessageProcessor<?>> headersToAdd =
                Map.of(HttpHeaders.CONTENT_TYPE,
                        new StaticHeaderValueMessageProcessor<>(MediaType.APPLICATION_JSON_VALUE),
                        HttpHeaders.AUTHORIZATION,
                        new StaticHeaderValueMessageProcessor<>(PERSONAPI_TOKEN));
        HeaderEnricher enricher = new HeaderEnricher(headersToAdd);
        return enricher;
    }

    @Bean
    @ServiceActivator(inputChannel = "toHttpRequest")
    public HttpRequestExecutingMessageHandler outbound(RequestHandlerRetryAdvice retryAdvice) {
        HttpRequestExecutingMessageHandler handler = Http
                .outboundGateway(PERSONAPI_BASE_URL + "person").httpMethod(HttpMethod.POST)
                // .messageConverters(new MappingJackson2HttpMessageConverter())
                .mappedRequestHeaders(HttpHeaders.CONTENT_TYPE, HttpHeaders.AUTHORIZATION).get();

        handler.setOutputChannel(httpResponseChannel());
        // handler.setExpectReply(false);

        // handler.setErrorHandler(null);

        List<Advice> adviceList = new ArrayList<>();
        adviceList.add(retryAdvice);
        // adviceList.add(deadLetterAdvice);
        handler.setAdviceChain(adviceList);

        return handler;

    }

}
