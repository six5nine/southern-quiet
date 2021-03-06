package test.notification;

import me.insidezhou.southernquiet.amqp.rabbit.AbstractAmqpNotificationPublisher;
import me.insidezhou.southernquiet.amqp.rabbit.DelayedMessage;
import me.insidezhou.southernquiet.debounce.Debounce;
import me.insidezhou.southernquiet.logging.SouthernQuietLogger;
import me.insidezhou.southernquiet.logging.SouthernQuietLoggerFactory;
import me.insidezhou.southernquiet.notification.AmqpNotificationAutoConfiguration;
import me.insidezhou.southernquiet.notification.NotificationListener;
import me.insidezhou.southernquiet.notification.NotificationPublisher;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static me.insidezhou.southernquiet.notification.driver.AmqpNotificationListenerManager.DeadMark;
import static me.insidezhou.southernquiet.notification.driver.AmqpNotificationListenerManager.RetryMark;

@RunWith(SpringRunner.class)
@SpringBootTest
public class NotificationTest {
    private final static SouthernQuietLogger log = SouthernQuietLoggerFactory.getLogger(NotificationTest.class);

    @SpringBootConfiguration
    @EnableAutoConfiguration
    public static class Config {
        @Bean
        public NotificationTest.Listener listener() {
            return new NotificationTest.Listener();
        }
    }

    @Autowired
    private NotificationPublisher<Serializable> notificationPublisher;

    @Resource
    private RabbitListenerEndpointRegistry rabbitListenerEndpointRegistry;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private AmqpNotificationAutoConfiguration.Properties properties;

    @Test
    public void dummy() {
        notificationPublisher.publish(new StandardNotification());
    }

    @Test
    public void delay() {
        notificationPublisher.publish(new DelayedNotification());
    }

    @Test
    public void concurrent() {
        for (int i = 0; i < 30; i++)
            notificationPublisher.publish(new ConcurrentNotification());

        long concurrent = rabbitListenerEndpointRegistry.getListenerContainers().stream()
            .filter(containers -> ((DirectMessageListenerContainer) containers).getQueueNames()[0].contains("concurrent")).count();

        Assert.assertEquals(Listener.concurrency, concurrent);

    }

    @Test
    public void queueDeclared() {
        String deadRouting = properties.getNamePrefix() + DeadMark + StandardNotification.class.getSimpleName() + "#a";
        QueueInformation deadQueue = amqpAdmin.getQueueInfo(deadRouting);
        Assert.assertNotNull(deadQueue);

        String retryRouting = properties.getNamePrefix() + RetryMark + StandardNotification.class.getSimpleName() + "#a";
        QueueInformation retryQueue = amqpAdmin.getQueueInfo(retryRouting);
        Assert.assertNotNull(retryQueue);

        String delayRouting = AbstractAmqpNotificationPublisher.getDelayedRouting(properties.getNamePrefix(), StandardNotification.class);
        QueueInformation delayQueue = amqpAdmin.getQueueInfo(delayRouting);
        Assert.assertNotNull(delayQueue);
    }

    public static class Listener {
        @NotificationListener(notification = StandardNotification.class, name = "a")
        @NotificationListener(notification = StandardNotification.class, name = "b")
        @NotificationListener(notification = StandardNotification.class, name = "e")
        @Debounce
        public void standard(StandardNotification notification, NotificationListener listener) {
            log.message("使用监听器接到通知")
                .context("listenerName", listener.name())
                .context("id", notification.getId())
                .info();
        }

        @NotificationListener(notification = StandardNotification.class, name = "e")
        public void exception(StandardNotification notification, NotificationListener listener) {
            throw new RuntimeException("在通知中抛出异常通知：listener=" + listener.name() + ", notification=" + notification.getId());
        }

        @NotificationListener(notification = DelayedNotification.class)
        public void delay(DelayedNotification notification, NotificationListener listener, DelayedMessage delayedAnnotation) {
            log.message("使用监听器接到延迟通知")
                .context("listenerName", listener.name())
                .context("delay", delayedAnnotation)
                .context("duration", Duration.between(notification.publishedAt, Instant.now()))
                .info();
        }

        public static final int concurrency = 2;

        @NotificationListener(notification = ConcurrentNotification.class, concurrency = Listener.concurrency)
        public void concurrent(ConcurrentNotification notification, NotificationListener listener) {

            log.message("使用并发监听器接到通知")
                .context("listenerName", listener.name())
                .context("listenerConcurrent", listener.concurrency())
                .context("notificationId", notification.getId())
                .context("currentThreadName", Thread.currentThread().getName())
                .info();
        }
    }

    public static class StandardNotification implements Serializable {
        private UUID id = UUID.randomUUID();

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }
    }

    @DelayedMessage(3000)
    public static class DelayedNotification implements Serializable {
        private Instant publishedAt = Instant.now();

        public Instant getPublishedAt() {
            return publishedAt;
        }

        public void setPublishedAt(Instant publishedAt) {
            this.publishedAt = publishedAt;
        }
    }

    public static class ConcurrentNotification implements Serializable {
        private UUID id = UUID.randomUUID();

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }
    }
}
