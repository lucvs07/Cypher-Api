package com.dev4.cypher.repository;

import com.dev4.cypher.model.DeliveryStatus;
import com.dev4.cypher.model.WebhookDelivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {

    List<WebhookDelivery> findByStatusAndProximaTentativaBefore(DeliveryStatus status,
                                                               OffsetDateTime limite);

    List<WebhookDelivery> findByIncidentKey(String incidentKey);

    Page<WebhookDelivery> findBySubscriptionId(Long subscriptionId, Pageable pageable);
}
