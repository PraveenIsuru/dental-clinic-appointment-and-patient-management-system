package com.mycompany.sunrisedentalclinic.model;

import java.time.LocalDateTime;

public record SupportTicket(int id, int createdBy, String createdByName, String subject,
        String description, String priority, String status, String adminResponse, LocalDateTime createdAt) {

}
