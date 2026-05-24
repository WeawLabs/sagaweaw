package io.sagaweaw.spring.api;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class GrafanaDashboardController {

    @GetMapping("/api/grafana-dashboard")
    public ResponseEntity<byte[]> download() throws IOException {
        byte[] content = new ClassPathResource("sagaweaw-grafana-dashboard.json").getContentAsByteArray();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"sagaweaw-grafana-dashboard.json\"")
                .body(content);
    }
}
