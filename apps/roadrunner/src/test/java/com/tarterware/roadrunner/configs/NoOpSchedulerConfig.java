package com.tarterware.roadrunner.configs;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import org.springframework.context.annotation.Primary;
import java.util.concurrent.TimeUnit;

@TestConfiguration
public class NoOpSchedulerConfig
{

    @Bean
    @Primary
    AdminClient adminClient()
    {
        AdminClient mock = Mockito.mock(AdminClient.class);
        org.apache.kafka.clients.admin.DeleteConsumerGroupsResult mockResult =
                Mockito.mock(org.apache.kafka.clients.admin.DeleteConsumerGroupsResult.class);
        @SuppressWarnings("unchecked")
        org.apache.kafka.common.KafkaFuture<Void> mockFuture =
                Mockito.mock(org.apache.kafka.common.KafkaFuture.class);

        Mockito.when(mock.deleteConsumerGroups(Mockito.anyCollection())).thenReturn(mockResult);
        Mockito.when(mockResult.all()).thenReturn(mockFuture);
        try
        {
            Mockito.when(mockFuture.get(Mockito.anyLong(), Mockito.any(TimeUnit.class))).thenReturn(null);
        }
        catch (Exception e)
        {
            // ignore
        }
        return mock;
    }

    @Bean
    TaskScheduler taskScheduler()
    {
        // Returns a no-op TaskScheduler, so scheduled tasks won't run.
        return new TaskScheduler()
        {
            @Override
            public ScheduledFuture<?> schedule(Runnable task, Trigger trigger)
            {
                return createNoOpScheduledFuture();
            }

            @Override
            public ScheduledFuture<?> schedule(Runnable task, Instant startTime)
            {
                return createNoOpScheduledFuture();
            }

            @Override
            public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period)
            {
                return createNoOpScheduledFuture();
            }

            @Override
            public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period)
            {
                return createNoOpScheduledFuture();
            }

            @Override
            public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay)
            {
                return createNoOpScheduledFuture();
            }

            @Override
            public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay)
            {
                return createNoOpScheduledFuture();
            }

            private ScheduledFuture<?> createNoOpScheduledFuture()
            {
                return new ScheduledFuture<Object>()
                {
                    @Override
                    public long getDelay(TimeUnit unit)
                    {
                        return 0;
                    }

                    @Override
                    public int compareTo(Delayed o)
                    {
                        return 0;
                    }

                    @Override
                    public boolean cancel(boolean mayInterruptIfRunning)
                    {
                        return false;
                    }

                    @Override
                    public boolean isCancelled()
                    {
                        return false;
                    }

                    @Override
                    public boolean isDone()
                    {
                        return true;
                    }

                    @Override
                    public Object get()
                    {
                        return null;
                    }

                    @Override
                    public Object get(long timeout, TimeUnit unit)
                    {
                        return null;
                    }
                };
            }
        };
    }
}
