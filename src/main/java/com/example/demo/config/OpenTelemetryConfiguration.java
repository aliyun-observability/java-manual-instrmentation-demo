package com.example.demo.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.extension.trace.propagation.JaegerPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.time.Duration;

/**
 * OpenTelemetry configuration class for manual instrumentation
 * 
 * This class initializes the OpenTelemetry SDK with proper configuration
 * for tracing and metrics collection, following the Alibaba Cloud ARMS documentation.
 */
@Configuration
public class OpenTelemetryConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(OpenTelemetryConfiguration.class);
    
    @Value("${spring.application.name}")
    private String serviceName;
    
    /**
     * Provides the global OpenTelemetry instance
     * 
     * @return OpenTelemetry instance
     */
    @Bean
    public OpenTelemetry openTelemetry() {
        return GlobalOpenTelemetry.get();
    }
    
    /**
     * Provides a tracer instance for the application
     * 
     * @param openTelemetry OpenTelemetry instance
     * @return Tracer instance
     */
    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(serviceName, "1.0.0");
    }
}