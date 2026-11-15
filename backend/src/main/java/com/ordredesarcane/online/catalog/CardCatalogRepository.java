package com.ordredesarcane.online.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardCatalogRepository extends JpaRepository<CardCatalogEntity, String> {
    List<CardCatalogEntity> findAllByOrderByCardIdAsc();
}
