package com.fleetops.maintenance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class AlarmBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(AlarmBroadcastService.class);

    @Value("${app.sns.insurance-topic-arn:}")
    private String insuranceTopicArn;

    @Value("${app.sns.service-topic-arn:}")
    private String serviceTopicArn;

    @Value("${app.vehicle-service.url:http://fleetops-vehicle-service:8080}")
    private String vehicleServiceUrl;

    @Value("${app.aws.region:us-east-1}")
    private String awsRegion;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;
    private SnsClient snsClient;

    public AlarmBroadcastService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    private void init() {
        this.snsClient = SnsClient.builder().region(Region.of(awsRegion)).build();
    }

    @PreDestroy
    private void destroy() {
        if (snsClient != null) snsClient.close();
    }

    public Map<String, Integer> broadcastAlarms(String bearerToken) {
        List<?> insuranceAlerts = fetchAlerts(bearerToken, "/api/vehicles/alerts/insurance");
        List<?> serviceAlerts   = fetchAlerts(bearerToken, "/api/vehicles/alerts/service");

        int insuranceCount = insuranceAlerts.size();
        int serviceCount   = serviceAlerts.size();

        if (insuranceCount > 0 && insuranceTopicArn != null && !insuranceTopicArn.isBlank()) {
            String message = buildInsuranceMessage(insuranceAlerts, insuranceCount);
            snsClient.publish(PublishRequest.builder()
                .topicArn(insuranceTopicArn)
                .subject("[FleetOps ALARM] " + insuranceCount + " vehicle(s) insurance expiring")
                .message(message)
                .build());
            log.info("Published insurance alarm to SNS: {} vehicles", insuranceCount);
        }

        if (serviceCount > 0 && serviceTopicArn != null && !serviceTopicArn.isBlank()) {
            String message = buildServiceMessage(serviceAlerts, serviceCount);
            snsClient.publish(PublishRequest.builder()
                .topicArn(serviceTopicArn)
                .subject("[FleetOps ALARM] " + serviceCount + " vehicle(s) service overdue")
                .message(message)
                .build());
            log.info("Published service alarm to SNS: {} vehicles", serviceCount);
        }

        return Map.of("insuranceAlarmsPublished", insuranceCount, "serviceAlarmsPublished", serviceCount);
    }

    private List<Map<String, Object>> fetchAlerts(String bearerToken, String path) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(vehicleServiceUrl + path))
                .header("Authorization", bearerToken)
                .GET()
                .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                return objectMapper.readValue(resp.body(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
            }
            log.warn("Vehicle service returned {} for {}", resp.statusCode(), path);
        } catch (Exception e) {
            log.error("Failed to fetch alerts from {}: {}", path, e.getMessage());
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private String buildInsuranceMessage(List<?> alerts, int count) {
        StringBuilder lines = new StringBuilder();
        for (Object a : alerts) {
            Map<String, Object> v = (Map<String, Object>) a;
            lines.append("  • ").append(v.get("vehicleNumber"))
                 .append(" (").append(v.getOrDefault("brand", "")).append(" ").append(v.getOrDefault("model", "")).append(")")
                 .append(" — expires ").append(v.getOrDefault("insuranceExpiry", "unknown")).append("\n");
        }
        return "FleetOps Daily Alarm — Insurance Expiry\n" +
               "Date: " + LocalDate.now() + "\n\n" +
               count + " vehicle(s) have insurance expiring within 30 days:\n" +
               lines +
               "\nAction required: Raise an INSURANCE_RENEWAL service request for each vehicle listed above.";
    }

    @SuppressWarnings("unchecked")
    private String buildServiceMessage(List<?> alerts, int count) {
        StringBuilder lines = new StringBuilder();
        for (Object a : alerts) {
            Map<String, Object> v = (Map<String, Object>) a;
            lines.append("  • ").append(v.get("vehicleNumber"))
                 .append(" (").append(v.getOrDefault("brand", "")).append(" ").append(v.getOrDefault("model", "")).append(")")
                 .append(" — ").append(v.getOrDefault("currentMileage", 0)).append(" km / ")
                 .append(v.getOrDefault("nextServiceMileage", 0)).append(" km threshold\n");
        }
        return "FleetOps Daily Alarm — Service Overdue\n" +
               "Date: " + LocalDate.now() + "\n\n" +
               count + " vehicle(s) have exceeded their service mileage threshold:\n" +
               lines +
               "\nAction required: Raise a ROUTINE_SERVICE service request for each vehicle listed above.";
    }
}
