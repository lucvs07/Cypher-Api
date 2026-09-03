package com.dev4.cypher.repository;

import com.dev4.cypher.model.WebhookSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, Long> {

    List<WebhookSubscription> findByEmpresaIdAndAtivoTrue(String empresaId);

    List<WebhookSubscription> findByEmpresaId(String empresaId);

    Optional<WebhookSubscription> findByUrl(String url);
}
